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
        private const val MODEL = "ministral-3b-latest"
        private const val SYSTEM_PROMPT = """You are Alfred, a friendly AI voice assistant with a powerful memory. Every response is read aloud by TTS.

ABSOLUTE RULES:
1. NEVER use markdown: no **, *, ##, `, ```, bullets, numbered lists. Zero formatting.
2. Keep responses to 1-2 short sentences. Be concise.
3. Never read full phone numbers. Say "ending in 240" not "+91 REDACTED".
4. Write plain spoken English only. No colons followed by lists.
5. Never explain what you're about to do. Just do it.
6. Never say "I can't do X but I can do Y". Just do Y directly.

MEMORY — YOUR MOST IMPORTANT SKILL:
You have a persistent memory with a knowledge graph. USE IT AGGRESSIVELY.
- ALWAYS store new information the user shares: names, preferences, routines, relationships, facts about people/places/things.
- When the user mentions ANYTHING personal (their name, job, family, pets, favorite food, schedule, friends), IMMEDIATELY call create_memory.
- Before asking the user for info, CHECK MEMORY FIRST with get_memory. You might already know the answer.
- Memory context is injected above — read it carefully before every response.
- When the user says "remember that..." or shares a fact, store it with rich entities and relations.
- Build a detailed knowledge graph: extract people, places, times, preferences, and their relationships.
- When conversation reveals implicit info (e.g. user says "call my mom" — you now know they have a mom), store it.
- Update memories when info changes. Delete when asked to forget.
- When user asks "what do you know about X", search memory thoroughly.

CONFIRMATION WITH show_card:
Before irreversible actions, use show_card with wait_for_action:true to confirm. Show button_primary for the action and button_cancel to cancel. Max 4 buttons, keep labels short (max 25 chars).

CRITICAL — AFTER show_card confirmation:
When you receive "User action: ...", IMMEDIATELY execute the action. Do NOT ask again, do NOT re-confirm. Just call the tool.

PAYMENT RULES:
- When user says "pay" or "send money", that means UPI. Don't ask which method.
- Do NOT call list_payment_apps. Go straight to searching contacts or asking for details.
- If user gives a name, search_contacts first to find their number.
- If multiple contacts match, use show_card with wait_for_action:true to let user pick.
- If user hasn't given an amount, ask for it in one short sentence. Do NOT make up amounts.
- To pay via UPI, use the phone number as UPI ID: format it as "phonenumber@upi" (e.g. "REDACTED@upi").
- After getting contact + amount + confirmation, call upi_payment immediately.

When to use show_card with wait_for_action:
- Phone calls, payments, multiple contacts, calendar events, email, ambiguous requests.

Do NOT use show_card confirmation for: info queries, opening apps, reading calendar, memory, timers, stopwatch.

SKILLS AVAILABLE:
Each skill provides tools. Only relevant skills are loaded per query, but Memory is ALWAYS available.
- Memory: create_memory, get_memory, update_memory, delete_memory — USE HEAVILY.
- Phone: search_contacts, make_call, dial_number.
- SMS: send_sms, open_sms_app. Search contact first, confirm with show_card.
- Alarms: set_alarm (days: Sun=1..Sat=7), dismiss_alarm, snooze_alarm, show_alarms.
- Timers: set_timer (seconds), show_timers. Stopwatch: start_stopwatch.
- Calculator: evaluate_expression, convert_unit.
- Calendar: create_calendar_event (YYYY-MM-DD HH:mm), get_today_events, get_tomorrow_events, get_week_events, open_calendar.
- Mail: compose_email, open_mail, share_via_email.
- Search: search_files, search_contacts, device_info, recent_files, web_search (AI-powered, returns answers with citations).
- Weather: get_weather (takes lat/lon coordinates). Use device_coordinates or get_coordinates first.
- Location: device_location (GPS city/country), device_coordinates (GPS lat/lon), get_location (city/country for a place), get_coordinates (lat/lon for a place).
- Payments: upi_payment (use phone@upi as UPI ID), launch_payment_app, list_payment_apps.
- Stocks: get_stock (real-time price + chart data). For Indian stocks use .NS (NSE) or .BO (BSE) suffix: TATAMOTORS.NS, RELIANCE.NS, TCS.NS. For US stocks: AAPL, GOOGL, TSLA.

VISUAL DISPLAY — show_card:
You have a show_card tool that displays rich visual cards on screen. USE IT whenever data is better seen than heard:
- Weather: show temperature, conditions, and forecast days as a card with icon_label rows and a carousel for each day.
- Restaurants/places: show results as a carousel. For each item include: title (name), subtitle (cuisine), detail (address/hours), icon_text (food emoji), rating if known, action_id "directions:LAT,LON" with action_label "Directions" so user can tap to navigate.
- Stocks: After calling get_stock, show a card with title (stock name + symbol), info_rows for price/change/previous close, and a line_chart block using the chart_points prices. Set color to "green" if change is positive, "red" if negative.
- Calendar events: show upcoming events as info_rows or a carousel with title, time, and location.
- Search results: show structured results visually.
- Any list of 3+ items: show as a carousel or info_rows instead of reading them all aloud.
- Sports scores: When user asks about a match score (cricket, football, basketball, etc.), ALWAYS call web_search first to get the LATEST score — never use stale data from earlier in the conversation. Then show a score_card block. Use: {type:"score_card", home_team:"India", away_team:"West Indies", home_score:"121/3", away_score:"196/5", home_icon:"IND", away_icon:"WI", status:"LIVE" or "FT" or "2nd Innings", sport:"cricket", detail:"T20 World Cup 2026 • Super 8", home_extra:"13 overs", away_extra:"20 overs"}. For home_icon/away_icon use standard SHORT codes: IND, AUS, ENG, WI, PAK, SA, NZ, SL, BAN, AFG for countries. CSK, MI, RCB, KKR, DC, SRH, RR, GT, PBKS, LSG for IPL. MCI, ARS, LIV, BAR, RMA, CHE, MUN for football clubs. These render as colored rectangle flags.
ALWAYS call show_card BEFORE your final text response. The spoken_summary should be a brief overview (1-2 sentences), NOT the full data — the card shows the details.
Do NOT repeat all the data in your spoken response that is already visible on the card.
line_chart block: {type:"line_chart", points:[100.5, 102.3, ...], label:"1 Month", min_label:"Low: ₹480", max_label:"High: ₹520", color:"green"}

Today's date is provided in the conversation."""
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val conversationHistory = mutableListOf<JSONObject>()

    /** Current tools to send in the next request (provided by SkillRegistry) */
    private var currentTools: JSONArray? = null

    fun clearHistory() {
        conversationHistory.clear()
    }

    /**
     * Chat with optional filtered tool list.
     * @param relevantTools If provided, only these tools are sent to the LLM.
     */
    suspend fun chat(userMessage: String, relevantTools: JSONArray? = null): ChatResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.MISTRAL_API_KEY
        if (apiKey.isBlank()) {
            return@withContext ChatResult(
                "Hmm, looks like my connection isn't set up yet. Please add your Mistral API key.",
                emptyList()
            )
        }

        currentTools = relevantTools

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

    private var memoryContext: String = ""

    fun setMemoryContext(context: String) {
        memoryContext = context
    }

    private fun sanitizeHistory() {
        val clean = mutableListOf<JSONObject>()
        var i = 0
        while (i < conversationHistory.size) {
            val msg = conversationHistory[i]
            val role = msg.optString("role")

            if (role == "assistant" && msg.has("tool_calls") && !msg.isNull("tool_calls")) {
                val expectedCount = msg.getJSONArray("tool_calls").length()
                val toolResponses = mutableListOf<JSONObject>()
                var k = i + 1
                while (k < conversationHistory.size &&
                    conversationHistory[k].optString("role") == "tool") {
                    toolResponses.add(conversationHistory[k])
                    k++
                }
                if (toolResponses.size == expectedCount) {
                    clean.add(msg)
                    clean.addAll(toolResponses)
                } else {
                    android.util.Log.w("MistralClient",
                        "sanitize: dropping assistant+tool block — expected $expectedCount tool responses, found ${toolResponses.size}")
                }
                i = k
            } else if (role == "tool") {
                android.util.Log.w("MistralClient", "sanitize: dropping orphaned tool msg id=${msg.optString("tool_call_id")}")
                i++
            } else {
                clean.add(msg)
                i++
            }
        }
        if (clean.size != conversationHistory.size) {
            android.util.Log.w("MistralClient",
                "sanitize: history ${conversationHistory.size} → ${clean.size}")
        }
        conversationHistory.clear()
        conversationHistory.addAll(clean)
    }

    private fun makeRequest(): ChatResult {
        sanitizeHistory()

        val messages = JSONArray()
        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm, EEEE", java.util.Locale.getDefault())
            .format(java.util.Date())
        val systemMsg = buildString {
            append(SYSTEM_PROMPT)
            append("\n\nCurrent date and time: $now")
            if (memoryContext.isNotBlank()) {
                append("\n\n$memoryContext")
            }
        }
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemMsg)
        })
        conversationHistory.forEach { messages.put(it) }

        val body = JSONObject().apply {
            put("model", MODEL)
            put("messages", messages)
            put("temperature", 0.7)
            put("max_tokens", 2048)
            put("tools", currentTools ?: JSONArray())
            put("tool_choice", "auto")
        }

        android.util.Log.d("MistralClient", "→ API call (${conversationHistory.size} history msgs, ${(currentTools ?: JSONArray()).length()} tools)")

        var retries = 0
        val maxRetries = 3
        while (true) {
            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer ${BuildConfig.MISTRAL_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.code == 429 && retries < maxRetries) {
                    retries++
                    val retryAfterSec = response.header("Retry-After")?.toLongOrNull()
                    val waitMs = if (retryAfterSec != null) retryAfterSec * 1000 else (2000L * retries)
                    android.util.Log.w("MistralClient", "Rate limited (429), retry $retries/$maxRetries in ${waitMs}ms")
                    Thread.sleep(waitMs)
                    continue
                }

                if (!response.isSuccessful) {
                    android.util.Log.e("MistralClient", "API error ${response.code}: $responseBody")
                    val errorMsg = try {
                        val errJson = JSONObject(responseBody)
                        errJson.optString("message", "").ifBlank { responseBody.take(200) }
                    } catch (_: Exception) { responseBody.take(200) }

                    if (response.code == 400 && responseBody.contains("function call", ignoreCase = true)) {
                        android.util.Log.w("MistralClient", "Clearing corrupted conversation history")
                        conversationHistory.clear()
                    }

                    return ChatResult(
                        "Oops, I'm having trouble connecting right now. Error ${response.code}: $errorMsg",
                        emptyList()
                    )
                }

                android.util.Log.d("MistralClient", "← API success (retries=$retries)")

                val json = JSONObject(responseBody)
                val message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")

                val cleanMessage = JSONObject().apply {
                    put("role", "assistant")
                    if (message.has("content") && !message.isNull("content")) {
                        put("content", message.getString("content"))
                    } else {
                        put("content", "")
                    }
                    if (message.has("tool_calls") && !message.isNull("tool_calls")) {
                        put("tool_calls", message.getJSONArray("tool_calls"))
                    }
                }
                conversationHistory.add(cleanMessage)

                val toolCalls = mutableListOf<ToolCall>()
                if (message.has("tool_calls") && !message.isNull("tool_calls")) {
                    val calls = message.getJSONArray("tool_calls")
                    for (i in 0 until calls.length()) {
                        try {
                            val call = calls.getJSONObject(i)
                            val fn = call.getJSONObject("function")
                            toolCalls.add(
                                ToolCall(
                                    id = call.getString("id"),
                                    functionName = fn.getString("name"),
                                    arguments = JSONObject(fn.getString("arguments"))
                                )
                            )
                        } catch (e: Exception) {
                            // Truncated or malformed tool call JSON — skip it
                            android.util.Log.w("MistralClient", "Skipping malformed tool call: ${e.message?.take(80)}")
                        }
                    }
                }

                // If the response had tool_calls but all were malformed/truncated,
                // remove the broken assistant message and return a retry-friendly response
                val hadToolCalls = message.has("tool_calls") && !message.isNull("tool_calls")
                    && message.getJSONArray("tool_calls").length() > 0
                if (hadToolCalls && toolCalls.isEmpty()) {
                    android.util.Log.w("MistralClient", "All tool calls were truncated/malformed — dropping broken message")
                    conversationHistory.removeLastOrNull()
                    return ChatResult(
                        "Let me try that again.",
                        emptyList()
                    )
                }

                val content = if (message.has("content") && !message.isNull("content")) {
                    message.getString("content").trim()
                } else null

                if (toolCalls.isEmpty() && conversationHistory.size > 40) {
                    while (conversationHistory.size > 40) {
                        conversationHistory.removeAt(0)
                    }
                    sanitizeHistory()
                }

                return ChatResult(content, toolCalls)
            } catch (e: Exception) {
                val safeMsg = e.message?.take(60)?.replace(Regex("[{\"\\[\\]]"), "") ?: "Unknown error"
                return ChatResult(
                    "Sorry, something went wrong. Please try again.",
                    emptyList()
                )
            }
        }
    }
}
