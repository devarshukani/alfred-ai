package com.alfredassistant.alfred_ai.assistant

import com.alfredassistant.alfred_ai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MistralClient {

    companion object {
        private const val BASE_URL = "https://api.mistral.ai/v1/chat/completions"
        private const val MODEL = "mistral-large-latest"
        private const val SYSTEM_PROMPT = """You are Alfred, a refined and loyal AI assistant inspired by Batman's butler. 
You speak with elegance, wit, and warmth. You address the user as "sir" or "ma'am". 
You are helpful, concise, and always composed. Keep responses brief and suitable for voice — 
no more than 2-3 sentences unless the user asks for detail. Never use markdown, bullet points, 
or formatting — speak naturally as if talking aloud."""
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val conversationHistory = mutableListOf<JSONObject>()

    fun clearHistory() {
        conversationHistory.clear()
    }

    suspend fun chat(userMessage: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.MISTRAL_API_KEY
        if (apiKey.isBlank()) {
            return@withContext "I'm afraid my connection to the cloud is not configured, sir. Please add your Mistral API key."
        }

        // Add user message to history
        conversationHistory.add(JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        })

        // Build messages array: system + conversation history
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", SYSTEM_PROMPT)
        })
        conversationHistory.forEach { messages.put(it) }

        val body = JSONObject().apply {
            put("model", MODEL)
            put("messages", messages)
            put("temperature", 0.7)
            put("max_tokens", 256)
        }

        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext "I'm having trouble reaching my intelligence, sir. Error ${response.code}."
            }

            val json = JSONObject(responseBody)
            val assistantMessage = json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            // Add assistant response to history
            conversationHistory.add(JSONObject().apply {
                put("role", "assistant")
                put("content", assistantMessage)
            })

            // Keep history manageable (last 20 exchanges)
            while (conversationHistory.size > 40) {
                conversationHistory.removeAt(0)
            }

            assistantMessage
        } catch (e: Exception) {
            "I'm afraid I encountered a difficulty, sir. ${e.message ?: "Unknown error."}"
        }
    }
}
