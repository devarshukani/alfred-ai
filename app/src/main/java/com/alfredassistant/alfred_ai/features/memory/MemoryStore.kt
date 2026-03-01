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
 * On-device persistent memory for Alfred.
 * Uses ObjectBox with HNSW vector index for semantic search.
 * Facts and preferences are embedded and stored as vectors,
 * enabling semantic recall instead of exact key matching.
 */
class MemoryStore(private val context: Context, private val embeddingModel: EmbeddingModel) {

    companion object {
        private const val TAG = "MemoryStore"
        private const val LEGACY_PREFS = "alfred_memory"
        private const val MIGRATION_DONE_KEY = "objectbox_migration_done"
    }

    private val box: Box<MemoryEntity> by lazy {
        ObjectBoxStore.store.boxFor(MemoryEntity::class.java)
    }

    init {
        migrateFromSharedPreferences()
    }

    // ==================== FACTS ====================

    fun rememberFact(key: String, value: String): String {
        val k = key.lowercase().trim()
        val v = value.trim()
        val text = "$k: $v"
        val embedding = embeddingModel.embed(text)

        // Upsert: update if key exists, insert otherwise
        val existing = box.query(
            MemoryEntity_.key.equal(k, StringOrder.CASE_SENSITIVE)
                .and(MemoryEntity_.type.equal("fact", StringOrder.CASE_SENSITIVE))
        ).build().findFirst()

        if (existing != null) {
            existing.value = v
            existing.embedding = embedding
            existing.updatedAt = System.currentTimeMillis()
            box.put(existing)
        } else {
            box.put(MemoryEntity(
                key = k, value = v, type = "fact",
                embedding = embedding
            ))
        }
        return "Remembered: $key = $value"
    }

    fun recallFact(key: String): String {
        val k = key.lowercase().trim()
        // Try exact match first
        val exact = box.query(
            MemoryEntity_.key.equal(k, StringOrder.CASE_SENSITIVE)
                .and(MemoryEntity_.type.equal("fact", StringOrder.CASE_SENSITIVE))
        ).build().findFirst()
        if (exact != null) return exact.value

        // Fall back to semantic search
        val results = semanticSearch(k, maxResults = 1, type = "fact")
        return if (results.isNotEmpty()) {
            results.first().value
        } else {
            "I don't have that information stored, sir."
        }
    }

    fun getAllFacts(): String {
        val facts = box.query(
            MemoryEntity_.type.equal("fact", StringOrder.CASE_SENSITIVE)
        ).build().find()
        if (facts.isEmpty()) return "No facts stored yet."
        val result = JSONArray()
        facts.forEach { f ->
            result.put(JSONObject().apply {
                put("key", f.key)
                put("value", f.value)
            })
        }
        return result.toString()
    }

    fun forgetFact(key: String): String {
        val k = key.lowercase().trim()
        val existing = box.query(
            MemoryEntity_.key.equal(k, StringOrder.CASE_SENSITIVE)
                .and(MemoryEntity_.type.equal("fact", StringOrder.CASE_SENSITIVE))
        ).build().find()
        return if (existing.isNotEmpty()) {
            box.remove(existing as Collection<MemoryEntity>)
            "Forgotten: $key"
        } else {
            "I don't have that stored, sir."
        }
    }

    // ==================== PREFERENCES ====================

    fun setPreference(key: String, value: String): String {
        val k = key.lowercase().trim()
        val v = value.trim()
        val text = "preference $k: $v"
        val embedding = embeddingModel.embed(text)

        val existing = box.query(
            MemoryEntity_.key.equal(k, StringOrder.CASE_SENSITIVE)
                .and(MemoryEntity_.type.equal("preference", StringOrder.CASE_SENSITIVE))
        ).build().findFirst()

        if (existing != null) {
            existing.value = v
            existing.embedding = embedding
            existing.updatedAt = System.currentTimeMillis()
            box.put(existing)
        } else {
            box.put(MemoryEntity(
                key = k, value = v, type = "preference",
                embedding = embedding
            ))
        }
        return "Preference saved: $key = $value"
    }

    fun getAllPreferences(): String {
        val prefs = box.query(
            MemoryEntity_.type.equal("preference", StringOrder.CASE_SENSITIVE)
        ).build().find()
        if (prefs.isEmpty()) return "No preferences stored yet."
        val result = JSONArray()
        prefs.forEach { p ->
            result.put(JSONObject().apply {
                put("key", p.key)
                put("value", p.value)
            })
        }
        return result.toString()
    }

    // ==================== SEMANTIC SEARCH ====================

    /**
     * Search memories by semantic similarity to the query.
     * Returns top-K most relevant memories.
     */
    fun semanticSearch(query: String, maxResults: Int = 5, type: String? = null): List<MemoryEntity> {
        val queryEmbedding = embeddingModel.embed(query)
        val condition = if (type != null) {
            MemoryEntity_.embedding.nearestNeighbors(queryEmbedding, maxResults)
                .and(MemoryEntity_.type.equal(type, StringOrder.CASE_SENSITIVE))
        } else {
            MemoryEntity_.embedding.nearestNeighbors(queryEmbedding, maxResults)
        }
        val results = box.query(condition).build().findWithScores()
        return results.map { it.get() }
    }

    // ==================== CONTEXT FOR AI ====================

    /**
     * Get memory context relevant to the current user query.
     * Uses semantic search to find the most relevant memories.
     */
    fun getRelevantMemoryContext(userQuery: String): String {
        val parts = mutableListOf<String>()

        // Get semantically relevant memories (top 10)
        val relevant = semanticSearch(userQuery, maxResults = 10)
        val facts = relevant.filter { it.type == "fact" }
        val prefs = relevant.filter { it.type == "preference" }

        if (facts.isNotEmpty()) {
            val factsList = facts.joinToString("; ") { "${it.key}: ${it.value}" }
            parts.add("Relevant facts: $factsList")
        }
        if (prefs.isNotEmpty()) {
            val prefsList = prefs.joinToString("; ") { "${it.key}: ${it.value}" }
            parts.add("User preferences: $prefsList")
        }

        return if (parts.isEmpty()) "" else parts.joinToString("\n")
    }

    /**
     * Legacy method — returns all memory context (for backward compat).
     */
    fun getMemoryContext(): String {
        val facts = box.query(
            MemoryEntity_.type.equal("fact", StringOrder.CASE_SENSITIVE)
        ).build().find()
        val prefs = box.query(
            MemoryEntity_.type.equal("preference", StringOrder.CASE_SENSITIVE)
        ).build().find()
        val parts = mutableListOf<String>()

        if (facts.isNotEmpty()) {
            val factsList = facts.joinToString("; ") { "${it.key}: ${it.value}" }
            parts.add("Known facts: $factsList")
        }
        if (prefs.isNotEmpty()) {
            val prefsList = prefs.joinToString("; ") { "${it.key}: ${it.value}" }
            parts.add("User preferences: $prefsList")
        }

        return if (parts.isEmpty()) "" else parts.joinToString("\n")
    }

    // ==================== DEBUG ====================

    /** Return all entries as key→value pairs with type prefix for debug UI */
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
                val value = factsJson.getString(key)
                rememberFact(key, value)
            }

            val prefsJson = JSONObject(prefsRaw)
            prefsJson.keys().forEach { key ->
                val value = prefsJson.getString(key)
                setPreference(key, value)
            }

            migrationPrefs.edit().putBoolean(MIGRATION_DONE_KEY, true).apply()
            Log.i(TAG, "Migrated ${factsJson.length()} facts and ${prefsJson.length()} preferences to ObjectBox")
        } catch (e: Exception) {
            Log.w(TAG, "Migration from SharedPreferences failed", e)
        }
    }
}
