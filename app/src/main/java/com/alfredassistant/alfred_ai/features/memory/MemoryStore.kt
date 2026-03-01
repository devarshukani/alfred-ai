package com.alfredassistant.alfred_ai.features.memory

import android.content.Context
import android.util.Log
import com.alfredassistant.alfred_ai.db.MemoryEntity
import com.alfredassistant.alfred_ai.db.MemoryEntity_
import com.alfredassistant.alfred_ai.db.ObjectBoxStore
import com.alfredassistant.alfred_ai.embedding.EmbeddingModel
import io.objectbox.Box
import io.objectbox.query.QueryBuilder.StringOrder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Unified memory layer for Alfred — inspired by mem0's architecture.
 *
 * Combines flat memory (MemoryEntity) with knowledge graph (KnowledgeGraph)
 * into a single facade. Every memory operation writes to both stores:
 *
 * 1. MemoryEntity (vector store) — for fast semantic search via embeddings
 * 2. KnowledgeGraph (graph store) — for entity/relationship traversal
 *
 * The LLM sees only 4 tools:
 *   create_memory  — stores a fact/preference AND extracts entities into the graph
 *   get_memory     — semantic search over memories + graph context enrichment
 *   update_memory  — updates existing memory + graph entities
 *   delete_memory  — removes memory + cleans up graph edges
 *
 * Like mem0, retrieval combines vector similarity (find relevant memories)
 * with graph traversal (enrich with related entities and relationships).
 */
class MemoryStore(
    private val context: Context,
    private val embeddingModel: EmbeddingModel,
    private val knowledgeGraph: KnowledgeGraph
) {

    companion object {
        private const val TAG = "MemoryStore"
        private const val LEGACY_PREFS = "alfred_memory"
        private const val MIGRATION_DONE_KEY = "objectbox_migration_done_v2"
    }

    private val box: Box<MemoryEntity> by lazy {
        ObjectBoxStore.store.boxFor(MemoryEntity::class.java)
    }

    init {
        migrateFromSharedPreferences()
    }

    // ==================== CREATE ====================

    /**
     * Create a memory. Writes to both vector store and knowledge graph.
     * The LLM extracts entities and relations — we just store them.
     *
     * @param content Natural language memory, e.g. "My friend John lives in NYC"
     * @param type "fact" or "preference"
     * @param entities LLM-extracted entities as JSONArray of {name, type, attributes?}
     * @param relations LLM-extracted relations as JSONArray of {source, relation, target}
     */
    fun createMemory(
        content: String,
        type: String = "fact",
        entities: JSONArray? = null,
        relations: JSONArray? = null
    ): String {
        val normalized = content.trim()
        val embedding = embeddingModel.embed(normalized)

        // Check for duplicate/similar existing memory
        val similar = findSimilarMemory(normalized)
        if (similar != null && similar.value.equals(normalized, ignoreCase = true)) {
            return "I already know that."
        }

        // If similar memory exists, update it instead (mem0-style consolidation)
        if (similar != null) {
            similar.value = normalized
            similar.embedding = embedding
            similar.updatedAt = System.currentTimeMillis()
            box.put(similar)
            knowledgeGraph.storeStructured(entities, relations)
            return "Updated memory: $normalized"
        }

        // Create new memory
        val key = generateKey(normalized)
        box.put(MemoryEntity(
            key = key,
            value = normalized,
            type = type,
            embedding = embedding
        ))

        // Store LLM-extracted entities and relationships into knowledge graph
        knowledgeGraph.storeStructured(entities, relations)

        Log.d(TAG, "Created memory [$type]: $normalized")
        return "Remembered: $normalized"
    }

    // ==================== GET (SEARCH) ====================

    /**
     * Search memories by natural language query.
     * Combines vector similarity search with graph context enrichment.
     * Returns a rich context string for the LLM.
     */
    fun getMemory(query: String): String {
        val parts = mutableListOf<String>()

        // 1. Vector search — find semantically similar memories
        val memories = semanticSearch(query, maxResults = 8)
        if (memories.isNotEmpty()) {
            val memList = memories.joinToString("; ") { it.value }
            parts.add("Memories: $memList")
        }

        // 2. Graph search — find related entities and traverse relationships
        val graphContext = knowledgeGraph.getGraphContext(query, maxNodes = 5)
        if (graphContext.isNotBlank()) {
            parts.add(graphContext)
        }

        return if (parts.isEmpty()) {
            "No memories found for '$query'."
        } else {
            parts.joinToString("\n")
        }
    }

    // ==================== UPDATE ====================

    /**
     * Update an existing memory. Finds the closest match and updates it.
     * Also rebuilds the knowledge graph from LLM-extracted entities/relations.
     *
     * @param oldContent The memory to find (semantic match)
     * @param newContent The updated content
     * @param entities LLM-extracted entities for the new content
     * @param relations LLM-extracted relations for the new content
     */
    fun updateMemory(
        oldContent: String,
        newContent: String,
        entities: JSONArray? = null,
        relations: JSONArray? = null
    ): String {
        val target = findSimilarMemory(oldContent)
            ?: return "No matching memory found to update."

        val oldValue = target.value
        target.value = newContent.trim()
        target.embedding = embeddingModel.embed(newContent.trim())
        target.updatedAt = System.currentTimeMillis()
        box.put(target)

        // Rebuild graph from LLM-extracted structure
        knowledgeGraph.storeStructured(entities, relations)

        Log.d(TAG, "Updated memory: '$oldValue' → '${newContent.trim()}'")
        return "Updated: $newContent"
    }

    // ==================== DELETE ====================

    /**
     * Delete a memory by semantic match.
     * Also removes associated graph nodes and edges.
     */
    fun deleteMemory(content: String): String {
        val normalized = content.trim().lowercase()

        // "all" / "everything" → wipe both stores
        if (normalized in listOf("all", "everything", "all memories", "everything you know")) {
            val count = box.count()
            box.removeAll()
            knowledgeGraph.clearAll()
            Log.d(TAG, "Deleted all $count memories and cleared graph")
            return "All memories and knowledge graph cleared."
        }

        val target = findSimilarMemory(content)
            ?: return "No matching memory found to delete."

        val deleted = target.value
        box.remove(target)

        Log.d(TAG, "Deleted memory: $deleted")
        return "Forgotten: $deleted"
    }

    // ==================== CONTEXT FOR LLM ====================

    /**
     * Get enriched memory context for the current user query.
     * This is injected into the system prompt before each LLM call.
     *
     * Combines:
     * - Top-K semantically relevant memories (vector search)
     * - Related graph entities and relationships (graph traversal)
     */
    fun getRelevantContext(userQuery: String): String {
        val parts = mutableListOf<String>()

        // Vector search for relevant memories
        val relevant = semanticSearch(userQuery, maxResults = 8)
        val facts = relevant.filter { it.type == "fact" }
        val prefs = relevant.filter { it.type == "preference" }

        if (facts.isNotEmpty()) {
            parts.add("Relevant memories: ${facts.joinToString("; ") { it.value }}")
        }
        if (prefs.isNotEmpty()) {
            parts.add("User preferences: ${prefs.joinToString("; ") { it.value }}")
        }

        // Graph context — entity relationships
        val graphContext = knowledgeGraph.getGraphContext(userQuery, maxNodes = 5)
        if (graphContext.isNotBlank()) {
            parts.add(graphContext)
        }

        return if (parts.isEmpty()) "" else parts.joinToString("\n")
    }

    // ==================== INTERNAL ====================

    private fun semanticSearch(query: String, maxResults: Int = 5, type: String? = null): List<MemoryEntity> {
        val queryEmbedding = embeddingModel.embed(query)
        val condition = if (type != null) {
            MemoryEntity_.embedding.nearestNeighbors(queryEmbedding, maxResults)
                .and(MemoryEntity_.type.equal(type, StringOrder.CASE_SENSITIVE))
        } else {
            MemoryEntity_.embedding.nearestNeighbors(queryEmbedding, maxResults)
        }
        return box.query(condition).build().findWithScores().map { it.get() }
    }

    private fun findSimilarMemory(content: String): MemoryEntity? {
        val results = semanticSearch(content, maxResults = 1)
        return results.firstOrNull()
    }

    /**
     * Generate a short key from content for backward compat with MemoryEntity.
     * Takes first few meaningful words.
     */
    private fun generateKey(content: String): String {
        return content.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .split("\\s+".toRegex())
            .filter { it.length > 2 }
            .take(4)
            .joinToString("_")
            .ifBlank { "memory_${System.currentTimeMillis()}" }
    }

    // ==================== DEBUG ====================

    fun getDebugEntries(): List<Pair<String, String>> {
        return box.all.map { entry ->
            val prefix = if (entry.type == "fact") "📝" else "⚙️"
            "$prefix ${entry.key}" to entry.value
        }
    }

    // ==================== MIGRATION ====================

    private fun migrateFromSharedPreferences() {
        val migrationPrefs = context.getSharedPreferences("alfred_migration", Context.MODE_PRIVATE)
        if (migrationPrefs.getBoolean(MIGRATION_DONE_KEY, false)) return

        try {
            val oldPrefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            val factsRaw = oldPrefs.getString("facts", "{}") ?: "{}"
            val prefsRaw = oldPrefs.getString("preferences", "{}") ?: "{}"

            val factsJson = JSONObject(factsRaw)
            factsJson.keys().forEach { key ->
                createMemory("$key: ${factsJson.getString(key)}", "fact")
            }

            val prefsJson = JSONObject(prefsRaw)
            prefsJson.keys().forEach { key ->
                createMemory("preference $key: ${prefsJson.getString(key)}", "preference")
            }

            migrationPrefs.edit().putBoolean(MIGRATION_DONE_KEY, true).apply()
            Log.i(TAG, "Migrated ${factsJson.length()} facts and ${prefsJson.length()} preferences")
        } catch (e: Exception) {
            Log.w(TAG, "Migration from SharedPreferences failed", e)
        }
    }
}
