package com.alfredassistant.alfred_ai.asr

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

private const val TAG = "SherpaOnnxAsr"
private const val MODEL_DIR = "asr-model"
private const val SAMPLE_RATE = 16000

/**
 * Sherpa-onnx offline ASR engine with Silero VAD.
 *
 * Captures audio from the microphone, uses VAD to detect speech segments,
 * then feeds complete segments to the Moonshine offline recognizer.
 * Calls back with partial/final results and normalised RMS audio levels.
 */
object SherpaOnnxAsr {

    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var recording = false

    private var recordingThread: Thread? = null

    /** Initialise the recognizer and VAD once. Safe to call multiple times. */
    fun create(context: Context) {
        if (recognizer != null) return
        Log.i(TAG, "Initializing sherpa-onnx offline ASR + VAD")

        try {
            val assets = context.assets

            // --- VAD setup ---
            val vadConfig = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = "$MODEL_DIR/silero_vad.onnx",
                    threshold = 0.5f,
                    minSilenceDuration = 0.25f,
                    minSpeechDuration = 0.25f,
                    windowSize = 512,
                    maxSpeechDuration = 10.0f,
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
                debug = false,
            )
            vad = Vad(assetManager = assets, config = vadConfig)
            Log.i(TAG, "VAD ready")

            // --- Offline recognizer setup (Moonshine tiny int8) ---
            val files = assets.list(MODEL_DIR) ?: error("No ASR model in assets/$MODEL_DIR")

            val preprocessor = files.firstOrNull { it.startsWith("preprocess") && it.endsWith(".onnx") }
                ?: error("No preprocess.onnx in $MODEL_DIR")
            val encoder = files.firstOrNull { it.startsWith("encode") && it.endsWith(".onnx") }
                ?: error("No encode*.onnx in $MODEL_DIR")
            val uncachedDecoder = files.firstOrNull { it.startsWith("uncached_decode") && it.endsWith(".onnx") }
                ?: error("No uncached_decode*.onnx in $MODEL_DIR")
            val cachedDecoder = files.firstOrNull { it.startsWith("cached_decode") && it.endsWith(".onnx") }
                ?: error("No cached_decode*.onnx in $MODEL_DIR")
            val tokens = files.firstOrNull { it == "tokens.txt" }
                ?: error("No tokens.txt in $MODEL_DIR")

            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    moonshine = OfflineMoonshineModelConfig(
                        preprocessor = "$MODEL_DIR/$preprocessor",
                        encoder = "$MODEL_DIR/$encoder",
                        uncachedDecoder = "$MODEL_DIR/$uncachedDecoder",
                        cachedDecoder = "$MODEL_DIR/$cachedDecoder",
                    ),
                    tokens = "$MODEL_DIR/$tokens",
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                    modelType = "moonshine",
                ),
                decodingMethod = "greedy_search",
            )

            recognizer = OfflineRecognizer(assetManager = assets, config = config)
            Log.i(TAG, "ASR ready — Moonshine tiny (offline) encoder=$encoder")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ASR: $e", e)
            vad?.release()
            vad = null
            recognizer = null
        }
    }

    /**
     * Start recognition from the microphone using VAD + offline recognizer.
     *
     * @param onReady      called once the mic is open and recording starts
     * @param onPartial    called when VAD detects ongoing speech (visual feedback)
     * @param onFinal      called with the final recognised utterance
     * @param onError      called on any error
     * @param onAudioLevel called ~30 fps with normalised [0..1] RMS level
     */
    fun startListening(
        onReady: () -> Unit,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
        onAudioLevel: (Float) -> Unit,
    ) {
        val rec = recognizer
        val v = vad
        if (rec == null || v == null) {
            onError("ASR not initialised")
            return
        }
        if (recording) return

        val bufSize = maxOf(
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ),
            SAMPLE_RATE * 2 // at least 1 second buffer
        )

        val ar = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize,
            )
        } catch (e: SecurityException) {
            onError("Microphone permission denied")
            return
        }

        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            onError("Failed to open microphone")
            ar.release()
            return
        }

        audioRecord = ar
        recording = true

        ar.startRecording()
        Log.d(TAG, "🎙️ Mic opened — recording started (VAD + offline)")
        onReady()

        recordingThread = Thread({
            val windowSize = 512 // must match VAD windowSize
            val shortBuf = ShortArray(windowSize)
            v.reset()

            while (recording) {
                val read = ar.read(shortBuf, 0, shortBuf.size)
                if (read <= 0) continue

                // Compute RMS for audio level visualisation
                var sum = 0.0
                for (i in 0 until read) {
                    val s = shortBuf[i].toDouble()
                    sum += s * s
                }
                val rms = Math.sqrt(sum / read).toFloat()
                val normalised = (rms / 6000f).coerceIn(0f, 1f)
                onAudioLevel(normalised)

                // Convert Int16 → Float32 in [-1, 1]
                val floatBuf = FloatArray(read) { shortBuf[it] / 32768.0f }

                v.acceptWaveform(floatBuf)

                // Show partial feedback while speech is detected
                if (v.isSpeechDetected()) {
                    onPartial("...")
                }

                // Process any complete speech segments
                while (!v.empty()) {
                    val segment = v.front()
                    v.pop()

                    Log.d(TAG, "🎙️ VAD segment: ${segment.samples.size} samples (${segment.samples.size / SAMPLE_RATE.toFloat()}s)")

                    val stream = rec.createStream()
                    stream.acceptWaveform(segment.samples, SAMPLE_RATE)
                    rec.decode(stream)
                    val result = rec.getResult(stream)
                    stream.release()

                    val text = result.text.trim()
                    if (text.isNotEmpty()) {
                        Log.d(TAG, "🎙️ ASR FINAL: \"$text\"")
                        onFinal(text)
                    } else {
                        Log.d(TAG, "🎙️ ASR segment decoded but empty")
                    }
                }
            }

            // Flush remaining speech in VAD buffer
            v.flush()
            while (!v.empty()) {
                val segment = v.front()
                v.pop()

                Log.d(TAG, "🎙️ VAD flush segment: ${segment.samples.size} samples")

                val stream = rec.createStream()
                stream.acceptWaveform(segment.samples, SAMPLE_RATE)
                rec.decode(stream)
                val result = rec.getResult(stream)
                stream.release()

                val text = result.text.trim()
                if (text.isNotEmpty()) {
                    Log.d(TAG, "🎙️ ASR FINAL (flush): \"$text\"")
                    onFinal(text)
                }
            }

            onAudioLevel(0f)
            ar.stop()
            ar.release()
            Log.d(TAG, "🎙️ Mic closed — recording stopped")
            audioRecord = null
            recordingThread = null
        }, "sherpa-asr-mic")
        recordingThread!!.start()
    }

    /** Stop the current recognition session and wait for mic release. */
    fun stopListening() {
        if (!recording) return
        Log.d(TAG, "🎙️ stopListening() called")
        recording = false
        val t = recordingThread
        if (t != null && t.isAlive && Thread.currentThread() != t) {
            try { t.join(500) } catch (_: InterruptedException) {}
        }
    }

    fun shutdown() {
        stopListening()
        recognizer?.release()
        recognizer = null
        vad?.release()
        vad = null
    }
}
