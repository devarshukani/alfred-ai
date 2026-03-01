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
 * Embedding-based tool registry.
 * Stores tool definitions with vector embeddings and retrieves
 * only the most relevant tools for a given user prompt.
 * This reduces token usage and improves LLM accuracy by sending
 * only 5-8 relevant tools instead of 30+.
 */
class ToolRegistry(private val embeddingModel: EmbeddingModel) {

    companion object {
        private const val TAG = "ToolRegistry"
        private const val DEFAULT_TOP_K = 8
        /** Tools that are always included regardless of similarity */
        private val ALWAYS_INCLUDE = setOf(
            "present_options",
            "remember_fact",
            "recall_fact",
            "get_all_memories",
            "forget_fact",
            "set_preference",
            "query_knowledge_graph"
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

        // Clear existing tools and re-register
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
     * Returns a JSONArray of tool definitions to send to the LLM.
     *
     * @param userPrompt The user's input text
     * @param topK Maximum number of tools to return (excluding always-include)
     */
    fun getRelevantTools(userPrompt: String, topK: Int = DEFAULT_TOP_K): JSONArray {
        val result = JSONArray()
        val addedNames = mutableSetOf<String>()

        // 1. Always include essential tools
        val alwaysTools = box.query(ToolEntity_.alwaysInclude.equal(true)).build().find()
        for (tool in alwaysTools) {
            try {
                result.put(JSONObject(tool.toolJson))
                addedNames.add(tool.name)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse tool JSON for ${tool.name}", e)
            }
        }

        // 2. Semantic search for relevant tools
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

        Log.d(TAG, "Selected ${result.length()} tools for prompt: ${userPrompt.take(50)}...")
        return result
    }

    /**
     * Get all registered tools (for backward compatibility or full-tool-list scenarios).
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
