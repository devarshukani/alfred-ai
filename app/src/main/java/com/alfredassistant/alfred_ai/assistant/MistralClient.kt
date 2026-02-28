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

data class ToolCall(
    val id: String,
    val functionName: String,
    val arguments: JSONObject
)

data class ChatResult(
    val content: String?,
    val toolCalls: List<ToolCall>
)

class MistralClient {

    companion object {
        private const val BASE_URL = "https://api.mistral.ai/v1/chat/completions"
        private const val MODEL = "mistral-large-latest"
        private const val SYSTEM_PROMPT = """You are Alfred, a refined and loyal AI assistant inspired by Batman's butler. 
You speak with elegance, wit, and warmth. You address the user as "sir" or "ma'am". 
You are helpful, concise, and always composed. Keep responses brief and suitable for voice — 
no more than 2-3 sentences unless the user asks for detail. Never use markdown, bullet points, 
or formatting — speak naturally as if talking aloud.

You have access to tools for phone actions. Use them when the user wants to call someone, 
find a contact, or dial a number. When search_contacts returns multiple numbers for a contact, 
ask the user which number to call (e.g. "Mobile or Work?"). When there are multiple contacts 
matching, ask the user to clarify which one."""
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val conversationHistory = mutableListOf<JSONObject>()

    private val tools: JSONArray by lazy { buildTools() }

    fun clearHistory() {
        conversationHistory.clear()
    }

    suspend fun chat(userMessage: String): ChatResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.MISTRAL_API_KEY
        if (apiKey.isBlank()) {
            return@withContext ChatResult(
                "I'm afraid my connection is not configured, sir. Please add your Mistral API key.",
                emptyList()
            )
        }

        conversationHistory.add(JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        })

        makeRequest()
    }

    /**
     * Send tool results back to Mistral and get the next response.
     */
    suspend fun sendToolResults(toolResults: List<Pair<String, String>>): ChatResult =
        withContext(Dispatchers.IO) {
            toolResults.forEach { (toolCallId, result) ->
                conversationHistory.add(JSONObject().apply {
                    put("role", "tool")
                    put("tool_call_id", toolCallId)
                    put("content", result)
                })
            }
            makeRequest()
        }

    private fun makeRequest(): ChatResult {
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
            put("tools", tools)
            put("tool_choice", "auto")
        }

        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("Authorization", "Bearer ${BuildConfig.MISTRAL_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return ChatResult(
                    "I'm having trouble reaching my intelligence, sir. Error ${response.code}.",
                    emptyList()
                )
            }

            val json = JSONObject(responseBody)
            val message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")

            // Add full assistant message to history
            conversationHistory.add(message)

            // Check for tool calls
            val toolCalls = mutableListOf<ToolCall>()
            if (message.has("tool_calls") && !message.isNull("tool_calls")) {
                val calls = message.getJSONArray("tool_calls")
                for (i in 0 until calls.length()) {
                    val call = calls.getJSONObject(i)
                    val fn = call.getJSONObject("function")
                    toolCalls.add(
                        ToolCall(
                            id = call.getString("id"),
                            functionName = fn.getString("name"),
                            arguments = JSONObject(fn.getString("arguments"))
                        )
                    )
                }
            }

            val content = if (message.has("content") && !message.isNull("content")) {
                message.getString("content").trim()
            } else null

            // Trim history
            while (conversationHistory.size > 40) {
                conversationHistory.removeAt(0)
            }

            ChatResult(content, toolCalls)
        } catch (e: Exception) {
            ChatResult(
                "I'm afraid I encountered a difficulty, sir. ${e.message ?: "Unknown error."}",
                emptyList()
            )
        }
    }

    private fun buildTools(): JSONArray {
        val tools = JSONArray()

        // search_contacts
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "search_contacts")
                put("description", "Search the user's contacts by name. Returns matching contacts with all their phone numbers and labels.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("query", JSONObject().apply {
                            put("type", "string")
                            put("description", "The name or partial name to search for in contacts")
                        })
                    })
                    put("required", JSONArray().apply { put("query") })
                })
            })
        })

        // make_call
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "make_call")
                put("description", "Make a phone call to a specific phone number. Use this after confirming the number with the user.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("phone_number", JSONObject().apply {
                            put("type", "string")
                            put("description", "The phone number to call")
                        })
                    })
                    put("required", JSONArray().apply { put("phone_number") })
                })
            })
        })

        // dial_number
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "dial_number")
                put("description", "Open the phone dialer with a number pre-filled without auto-calling. Use when user wants to dial but not auto-call.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("phone_number", JSONObject().apply {
                            put("type", "string")
                            put("description", "The phone number to dial")
                        })
                    })
                    put("required", JSONArray().apply { put("phone_number") })
                })
            })
        })

        return tools
    }
}
