package com.alfredassistant.alfred_ai.embedding

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.alfredassistant.alfred_ai.models.ModelDownloader
import java.io.File
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * On-device text embedding using all-MiniLM-L6-v2 ONNX model.
 * Produces 384-dimensional embeddings for semantic similarity search.
 *
 * Model files are downloaded during onboarding to:
 *   getExternalFilesDir/models/embedding/model.onnx
 *   getExternalFilesDir/models/embedding/vocab.txt
 */
class EmbeddingModel(private val context: Context) {

    companion object {
        private const val TAG = "EmbeddingModel"
        const val DIMENSIONS = 384
        private const val MAX_SEQ_LENGTH = 128
    }

    private lateinit var ortEnv: OrtEnvironment
    private lateinit var ortSession: OrtSession
    private lateinit var vocab: Map<String, Int>
    private var initialized = false

    /**
     * Initialize the embedding model. Call once, ideally on a background thread.
     * Throws if the ONNX model or vocab file is missing.
     */
    @Synchronized
    fun initialize() {
        if (initialized) return
        val embDir = ModelDownloader.getEmbeddingDir(context)
        val modelFile = File(embDir, "model.onnx")
        val vocabFile = File(embDir, "vocab.txt")

        if (!modelFile.exists() || !vocabFile.exists()) {
            throw IllegalStateException("Embedding model files not found in ${embDir.absolutePath}. Run model download first.")
        }

        val modelBytes = modelFile.readBytes()
        ortEnv = OrtEnvironment.getEnvironment()
        ortSession = ortEnv.createSession(modelBytes)
        val vocabLines = vocabFile.readLines()
        vocab = vocabLines.withIndex().associate { (i, token) -> token to i }
        initialized = true
        Log.i(TAG, "ONNX embedding model loaded (${vocabLines.size} vocab tokens)")
    }

    /**
     * Generate a 384-dimensional embedding for the given text.
     */
    fun embed(text: String): FloatArray {
        if (!initialized) initialize()

        val tokens = tokenize(text, vocab)
        val inputIds = LongArray(tokens.size) { tokens[it].toLong() }
        val attentionMask = LongArray(tokens.size) { 1L }
        val tokenTypeIds = LongArray(tokens.size) { 0L }

        val shape = longArrayOf(1, tokens.size.toLong())

        val inputIdsTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(inputIds), shape)
        val attentionMaskTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(attentionMask), shape)
        val tokenTypeIdsTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(tokenTypeIds), shape)

        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor,
            "token_type_ids" to tokenTypeIdsTensor
        )

        return try {
            val results = ortSession.run(inputs)
            // Output shape: [1, seq_len, 384] — mean pooling
            @Suppress("UNCHECKED_CAST")
            val output = results[0].value as Array<Array<FloatArray>>
            val seqLen = output[0].size
            val embedding = FloatArray(DIMENSIONS)
            for (i in 0 until seqLen) {
                for (j in 0 until DIMENSIONS) {
                    embedding[j] += output[0][i][j]
                }
            }
            for (j in 0 until DIMENSIONS) {
                embedding[j] /= seqLen.toFloat()
            }
            normalize(embedding)
        } finally {
            inputIdsTensor.close()
            attentionMaskTensor.close()
            tokenTypeIdsTensor.close()
        }
    }

    /**
     * Simple WordPiece tokenizer for BERT-style models.
     */
    private fun tokenize(text: String, vocab: Map<String, Int>): List<Int> {
        val clsId = vocab["[CLS]"] ?: 101
        val sepId = vocab["[SEP]"] ?: 102
        val unkId = vocab["[UNK]"] ?: 100

        val tokens = mutableListOf(clsId)
        val words = text.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

        for (word in words) {
            if (tokens.size >= MAX_SEQ_LENGTH - 1) break
            var remaining = word
            var isFirst = true
            while (remaining.isNotEmpty() && tokens.size < MAX_SEQ_LENGTH - 1) {
                var matched = false
                for (end in remaining.length downTo 1) {
                    val sub = if (isFirst) remaining.substring(0, end) else "##${remaining.substring(0, end)}"
                    val id = vocab[sub]
                    if (id != null) {
                        tokens.add(id)
                        remaining = remaining.substring(end)
                        isFirst = false
                        matched = true
                        break
                    }
                }
                if (!matched) {
                    tokens.add(unkId)
                    break
                }
            }
        }
        tokens.add(sepId)
        return tokens
    }

    private fun normalize(vec: FloatArray): FloatArray {
        var norm = 0.0f
        for (v in vec) norm += v * v
        norm = sqrt(norm)
        if (norm > 0f) {
            for (i in vec.indices) vec[i] /= norm
        }
        return vec
    }

    fun close() {
        if (initialized) {
            ortSession.close()
            ortEnv.close()
            initialized = false
        }
    }
}
