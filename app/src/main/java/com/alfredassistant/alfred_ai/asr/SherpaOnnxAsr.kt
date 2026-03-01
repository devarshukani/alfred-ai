package com.alfredassistant.alfred_ai.asr

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

private const val TAG = "SherpaOnnxAsr"
private const val MODEL_DIR = "asr-model"
private const val SAMPLE_RATE = 16000

/**
 * Sherpa-onnx streaming ASR engine.
 *
 * Captures audio from the microphone and feeds it to the on-device
 * recognizer in real time. Calls back with partial/final results and
 * normalised RMS audio levels.
 */
object SherpaOnnxAsr {

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var recording = false

    private var recordingThread: Thread? = null

    /** Initialise the recognizer once. Safe to call multiple times. */
    fun create(context: Context) {
        if (recognizer != null) return
        Log.i(TAG, "Initializing sherpa-onnx ASR")

        val assets = context.assets

        // Auto-detect model files inside assets/asr-model/
        val files = assets.list(MODEL_DIR) ?: error("No ASR model in assets/$MODEL_DIR")
        val encoder = files.firstOrNull { it.contains("encoder") && it.endsWith(".onnx") }
            ?: error("No encoder .onnx in $MODEL_DIR")
        val decoder = files.firstOrNull { it.contains("decoder") && it.endsWith(".onnx") }
            ?: error("No decoder .onnx in $MODEL_DIR")
        val joiner = files.firstOrNull { it.contains("joiner") && it.endsWith(".onnx") }
            ?: error("No joiner .onnx in $MODEL_DIR")
        val tokens = files.firstOrNull { it == "tokens.txt" }
            ?: error("No tokens.txt in $MODEL_DIR")

        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$MODEL_DIR/$encoder",
                    decoder = "$MODEL_DIR/$decoder",
                    joiner = "$MODEL_DIR/$joiner",
                ),
                tokens = "$MODEL_DIR/$tokens",
                numThreads = 2,
                debug = false,
                provider = "cpu",
                modelType = "zipformer",
            ),
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 2.0f, 0.0f),
                rule2 = EndpointRule(true, 1.2f, 0.0f),
                rule3 = EndpointRule(false, 0.0f, 20.0f),
            ),
            enableEndpoint = true,
            decodingMethod = "greedy_search",
        )

        recognizer = OnlineRecognizer(assetManager = assets, config = config)
        Log.i(TAG, "ASR ready — encoder=$encoder")
    }

    /**
     * Start streaming recognition from the microphone.
     *
     * @param onReady      called once the mic is open and recording starts
     * @param onPartial    called with partial (intermediate) text
     * @param onFinal      called with the final recognised utterance
     * @param onError      called on any error
     * @param onAudioLevel called ~20 fps with normalised [0..1] RMS level
     */
    fun startListening(
        onReady: () -> Unit,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
        onAudioLevel: (Float) -> Unit,
    ) {
        val rec = recognizer
        if (rec == null) {
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
        stream = rec.createStream()
        recording = true

        ar.startRecording()
        onReady()

        // Read audio in a background thread
        recordingThread = Thread(Runnable {
            val shortBuf = ShortArray(SAMPLE_RATE / 10) // 100 ms chunks
            var lastText = ""

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

                stream?.acceptWaveform(floatBuf, SAMPLE_RATE)

                while (rec.isReady(stream!!)) {
                    rec.decode(stream!!)
                }

                val result = rec.getResult(stream!!)
                val text = result.text.trim()

                if (rec.isEndpoint(stream!!)) {
                    if (text.isNotEmpty()) {
                        onFinal(text)
                    }
                    rec.reset(stream!!)
                    lastText = ""
                } else if (text.isNotEmpty() && text != lastText) {
                    lastText = text
                    onPartial(text)
                }
            }

            // Drain any remaining text
            stream?.inputFinished()
            while (rec.isReady(stream!!)) {
                rec.decode(stream!!)
            }
            val tail = rec.getResult(stream!!).text.trim()
            if (tail.isNotEmpty()) {
                onFinal(tail)
            }

            onAudioLevel(0f)
            ar.stop()
            ar.release()
            stream?.release()
            stream = null
            audioRecord = null
            recordingThread = null
        }, "sherpa-asr-mic")
        recordingThread!!.start()
    }

    /** Stop the current recognition session and wait for mic release. */
    fun stopListening() {
        if (!recording) return
        recording = false
        // Wait for the recording thread to finish so the mic is fully released
        // before TTS starts playing (prevents self-hearing)
        val t = recordingThread
        if (t != null && t.isAlive && Thread.currentThread() != t) {
            try { t.join(500) } catch (_: InterruptedException) {}
        }
    }

    fun shutdown() {
        stopListening()
        recognizer?.release()
        recognizer = null
    }
}
