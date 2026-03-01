package com.alfredassistant.alfred_ai.features.memory

import android.util.Log
import com.alfredassistant.alfred_ai.db.ObjectBoxStore
import com.alfredassistant.alfred_ai.db.ToolEntity
import com.alfredassistant.alfred_ai.db.ToolEntity_
import com.alfredassistant.alfred_ai.embedding.EmbeddingModel
import io.objectbox.Box
import org.json.JSONArray
import org.json.JSONObject

/**
 * Embedding-based tool registry with smart routing.
 *
 * Strategy:
 * - Memory & knowledge tools are ALWAYS sent (the LLM needs them for every interaction)
 * - present_options is ALWAYS sent (confirmation flow)
 * - Other tools are selected via semantic similarity to the user's prompt
 * - Default top-K for non-essential tools is 4 (so total ≈ 8-12 tools instead of 40+)
 */
class ToolRegistry(private val embeddingModel: EmbeddingModel) {

    companion object {
        private const val TAG = "ToolRegistry"
        /** Max non-essential tools to include via semantic search */
        private const val DEFAULT_TOP_K = 4

        /** Tools that are ALWAYS included — unified memory (4) + confirmation (1) */
        private val ALWAYS_INCLUDE = setOf(
            "present_options",
            "create_memory",
            "get_memory",
            "update_memory",
            "delete_memory"
        )
    }

    private val box: Box<ToolEntity> by lazy {
        ObjectBoxStore.store.boxFor(ToolEntity::class.java)
    }

    private var initialized = false

    /**
     * Register all tools with their embeddings.
     * Call once at startup with the full tool definitions.
     */
    fun registerTools(tools: JSONArray) {
        if (initialized && box.count() > 0) return

        box.removeAll()

        for (i in 0 until tools.length()) {
            val tool = tools.getJSONObject(i)
            val fn = tool.getJSONObject("function")
            val name = fn.getString("name")
            val description = fn.getString("description")
            val embeddingText = "$name: $description"

            box.put(ToolEntity(
                name = name,
                description = description,
                toolJson = tool.toString(),
                alwaysInclude = name in ALWAYS_INCLUDE,
                embedding = embeddingModel.embed(embeddingText)
            ))
        }

        initialized = true
        Log.i(TAG, "Registered ${tools.length()} tools with embeddings")
    }

    /**
     * Get the most relevant tools for a user prompt.
     *
     * Always includes: memory tools + present_options (~7 tools)
     * Adds: top-K semantically similar non-essential tools (default 4)
     * Total sent to LLM: ~8-11 tools instead of 40+
     */
    fun getRelevantTools(userPrompt: String, topK: Int = DEFAULT_TOP_K): JSONArray {
        val result = JSONArray()
        val addedNames = mutableSetOf<String>()

        // 1. Always include essential tools (memory + confirmation)
        val alwaysTools = box.query(ToolEntity_.alwaysInclude.equal(true)).build().find()
        for (tool in alwaysTools) {
            try {
                result.put(JSONObject(tool.toolJson))
                addedNames.add(tool.name)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse tool JSON for ${tool.name}", e)
            }
        }

        // 2. Semantic search for relevant non-essential tools
        val queryEmbedding = embeddingModel.embed(userPrompt)
        val similar = box.query(
            ToolEntity_.embedding.nearestNeighbors(queryEmbedding, topK + ALWAYS_INCLUDE.size)
        ).build().findWithScores()

        var added = 0
        for (scored in similar) {
            if (added >= topK) break
            val tool = scored.get()
            if (tool.name in addedNames) continue
            try {
                result.put(JSONObject(tool.toolJson))
                addedNames.add(tool.name)
                added++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse tool JSON for ${tool.name}", e)
            }
        }

        Log.d(TAG, "Selected ${result.length()} tools (${alwaysTools.size} always + $added semantic) for: ${userPrompt.take(50)}...")
        return result
    }

    /**
     * Get all registered tools (for backward compatibility).
     */
    fun getAllTools(): JSONArray {
        val result = JSONArray()
        box.all.forEach { tool ->
            try {
                result.put(JSONObject(tool.toolJson))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse tool JSON for ${tool.name}", e)
            }
        }
        return result
    }
}
