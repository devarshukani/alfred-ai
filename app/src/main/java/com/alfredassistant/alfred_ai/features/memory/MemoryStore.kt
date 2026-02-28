package com.alfredassistant.alfred_ai.features.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device persistent memory for Alfred.
 * Stores user preferences, facts, and context as key-value pairs.
 * Uses SharedPreferences for persistence — survives app restarts.
 */
class MemoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("alfred_memory", Context.MODE_PRIVATE)
    private val factsKey = "facts"
    private val prefsKey = "preferences"

    // ==================== FACTS ====================

    /**
     * Store a fact about the user or anything they want remembered.
     */
    fun rememberFact(key: String, value: String): String {
        val facts = getFacts()
        facts.put(key.lowercase().trim(), value)
        prefs.edit().putString(factsKey, facts.toString()).apply()
        return "Remembered: $key = $value"
    }

    /**
     * Recall a specific fact.
     */
    fun recallFact(key: String): String {
        val facts = getFacts()
        val k = key.lowercase().trim()
        return if (facts.has(k)) {
            facts.getString(k)
        } else {
            "I don't have that information stored, sir."
        }
    }

    /**
     * Get all stored facts as a readable string.
     */
    fun getAllFacts(): String {
        val facts = getFacts()
        if (facts.length() == 0) return "No facts stored yet."
        val result = JSONArray()
        facts.keys().forEach { key ->
            result.put(JSONObject().apply {
                put("key", key)
                put("value", facts.getString(key))
            })
        }
        return result.toString()
    }

    /**
     * Forget a specific fact.
     */
    fun forgetFact(key: String): String {
        val facts = getFacts()
        val k = key.lowercase().trim()
        return if (facts.has(k)) {
            facts.remove(k)
            prefs.edit().putString(factsKey, facts.toString()).apply()
            "Forgotten: $key"
        } else {
            "I don't have that stored, sir."
        }
    }

    // ==================== PREFERENCES ====================

    /**
     * Store a user preference.
     */
    fun setPreference(key: String, value: String): String {
        val preferences = getPreferences()
        preferences.put(key.lowercase().trim(), value)
        prefs.edit().putString(prefsKey, preferences.toString()).apply()
        return "Preference saved: $key = $value"
    }

    /**
     * Get a user preference.
     */
    fun getPreference(key: String): String {
        val preferences = getPreferences()
        val k = key.lowercase().trim()
        return if (preferences.has(k)) {
            preferences.getString(k)
        } else {
            "No preference set for $key."
        }
    }

    /**
     * Get all preferences.
     */
    fun getAllPreferences(): String {
        val preferences = getPreferences()
        if (preferences.length() == 0) return "No preferences stored yet."
        val result = JSONArray()
        preferences.keys().forEach { key ->
            result.put(JSONObject().apply {
                put("key", key)
                put("value", preferences.getString(key))
            })
        }
        return result.toString()
    }

    /**
     * Get all memory (facts + preferences) as context for the AI.
     * This is injected into the system prompt so Alfred always knows what it remembers.
     */
    fun getMemoryContext(): String {
        val facts = getFacts()
        val preferences = getPreferences()
        val parts = mutableListOf<String>()

        if (facts.length() > 0) {
            val factsList = mutableListOf<String>()
            facts.keys().forEach { key -> factsList.add("$key: ${facts.getString(key)}") }
            parts.add("Known facts: ${factsList.joinToString("; ")}")
        }
        if (preferences.length() > 0) {
            val prefsList = mutableListOf<String>()
            preferences.keys().forEach { key -> prefsList.add("$key: ${preferences.getString(key)}") }
            parts.add("User preferences: ${prefsList.joinToString("; ")}")
        }

        return if (parts.isEmpty()) "" else parts.joinToString("\n")
    }

    // ==================== INTERNAL ====================

    private fun getFacts(): JSONObject {
        val raw = prefs.getString(factsKey, "{}") ?: "{}"
        return try { JSONObject(raw) } catch (e: Exception) { JSONObject() }
    }

    private fun getPreferences(): JSONObject {
        val raw = prefs.getString(prefsKey, "{}") ?: "{}"
        return try { JSONObject(raw) } catch (e: Exception) { JSONObject() }
    }
}
