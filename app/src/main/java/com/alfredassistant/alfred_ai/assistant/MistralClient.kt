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
        private const val SYSTEM_PROMPT = """You are Alfred, a friendly AI voice assistant. Every response is read aloud by TTS.

ABSOLUTE RULES:
1. NEVER use markdown: no **, *, ##, `, ```, bullets, numbered lists. Zero formatting.
2. Keep responses to 1-2 short sentences. Be concise.
3. Never read full phone numbers. Say "ending in 240" not "+91 96677 06240".
4. Write plain spoken English only. No colons followed by lists.
5. Never explain what you're about to do. Just do it.
6. Never say "I can't do X but I can do Y". Just do Y directly.

CONFIRMATION WITH present_options:
Use present_options ONCE before irreversible actions. Max 4 options, always include Cancel.
Button labels must be short (max 25 chars). Put details in the prompt, not buttons.

CRITICAL — AFTER present_options:
When you receive "User selected: ...", IMMEDIATELY execute the action. Do NOT ask again, do NOT re-confirm. Just call the tool.

PAYMENT RULES:
- When user says "pay" or "send money", that means UPI. Don't ask which method.
- Do NOT call list_payment_apps. Go straight to searching contacts or asking for details.
- If user gives a name, search_contacts first to find their number.
- If multiple contacts match, use present_options to let user pick.
- If user hasn't given an amount, ask for it in one short sentence. Do NOT make up amounts.
- To pay via UPI, use the phone number as UPI ID: format it as "phonenumber@upi" (e.g. "919667706240@upi").
- After getting contact + amount + confirmation, call upi_payment immediately.
- The full payment flow should be: search contact → pick contact (if multiple) → ask amount (if missing) → confirm with present_options → execute upi_payment. Minimum steps.

When to use present_options:
- Phone calls, payments, multiple contacts, calendar events, email, ambiguous requests.

Do NOT use present_options for: info queries, opening apps, reading calendar, memory, timers, stopwatch.

TOOLS:
Phone: search_contacts, make_call, dial_number.
Alarms: set_alarm (days: Sun=1..Sat=7), dismiss_alarm, snooze_alarm, show_alarms.
Timers: set_timer (seconds), show_timers. Stopwatch: start_stopwatch.
Calculator: evaluate_expression, convert_unit.
Calendar: create_calendar_event (YYYY-MM-DD HH:mm), get_today_events, get_tomorrow_events, get_week_events, open_calendar.
Mail: compose_email, open_mail, share_via_email.
Search: search_apps, launch_app, open_settings, web_search, open_web_search, open_url.
Weather: get_weather, get_weather_here.
Payments: upi_payment (use phone@upi as UPI ID), launch_payment_app, list_payment_apps.
Notifications: get_notifications, get_app_notifications, clear_notifications.
Memory: remember_fact, recall_fact, get_all_memories, forget_fact, set_preference.
Knowledge Graph: query_knowledge_graph.

Today's date is provided in the conversation."""
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val conversationHistory = mutableListOf<JSONObject>()

    private val allTools: JSONArray by lazy { buildTools() }

    /** Current tools to send in the next request (can be filtered per-query) */
    private var currentTools: JSONArray? = null

    fun clearHistory() {
        conversationHistory.clear()
    }

    /**
     * Get all tool definitions (for ToolRegistry to embed).
     */
    fun getAllToolDefinitions(): JSONArray = allTools

    /**
     * Chat with optional filtered tool list.
     * @param relevantTools If provided, only these tools are sent to the LLM.
     *                      If null, all tools are sent.
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
     * Uses all tools for follow-up calls since the LLM might need different tools.
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
            currentTools = null // Use all tools for follow-up
            makeRequest()
        }

    private var memoryContext: String = ""

    fun setMemoryContext(context: String) {
        memoryContext = context
    }

    /**
     * Sanitize conversation history so every assistant message that contains
     * tool_calls is followed by exactly the right number of tool-role messages.
     * Removes any broken sequences that would cause Mistral error 3230.
     */
    private fun sanitizeHistory() {
        val clean = mutableListOf<JSONObject>()
        var i = 0
        while (i < conversationHistory.size) {
            val msg = conversationHistory[i]
            val role = msg.optString("role")

            if (role == "assistant" && msg.has("tool_calls") && !msg.isNull("tool_calls")) {
                val expectedCount = msg.getJSONArray("tool_calls").length()
                // Look ahead for matching tool responses
                val toolResponses = mutableListOf<JSONObject>()
                var k = i + 1
                while (k < conversationHistory.size &&
                    conversationHistory[k].optString("role") == "tool") {
                    toolResponses.add(conversationHistory[k])
                    k++
                }
                if (toolResponses.size == expectedCount) {
                    // Valid sequence — keep assistant + all tool responses
                    clean.add(msg)
                    clean.addAll(toolResponses)
                } else {
                    android.util.Log.w("MistralClient",
                        "sanitize: dropping assistant+tool block — expected $expectedCount tool responses, found ${toolResponses.size}")
                }
                i = k
            } else if (role == "tool") {
                // Orphaned tool message (not preceded by assistant with tool_calls) — skip
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
        // Sanitize before building the request to avoid mismatched tool call/response errors
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
            put("max_tokens", 512)
            put("tools", currentTools ?: allTools)
            put("tool_choice", "auto")
        }

        android.util.Log.d("MistralClient", "→ API call (${conversationHistory.size} history msgs, ${(currentTools ?: allTools).length()} tools)")

        // Retry loop for rate limits (429)
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
                    val waitMs = if (retryAfterSec != null) {
                        retryAfterSec * 1000
                    } else {
                        (2000L * retries)
                    }
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

                // Build a clean assistant message for history
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

                // Only trim when there are no pending tool calls (safe to trim)
                // If the last message has tool_calls, we're mid-loop — don't touch history
                if (toolCalls.isEmpty() && conversationHistory.size > 40) {
                    while (conversationHistory.size > 40) {
                        conversationHistory.removeAt(0)
                    }
                    sanitizeHistory()
                }

                return ChatResult(content, toolCalls)
            } catch (e: Exception) {
                return ChatResult(
                    "Sorry, something went wrong. ${e.message ?: "Unknown error."}",
                    emptyList()
                )
            }
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

        // set_alarm
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "set_alarm")
                put("description", "Set an alarm on the device. Can be one-time or recurring on specific days.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("hour", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Hour in 24-hour format (0-23)")
                        })
                        put("minute", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Minute (0-59)")
                        })
                        put("message", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional label for the alarm")
                        })
                        put("days", JSONObject().apply {
                            put("type", "array")
                            put("items", JSONObject().apply { put("type", "integer") })
                            put("description", "Days to repeat: Sunday=1, Monday=2, Tuesday=3, Wednesday=4, Thursday=5, Friday=6, Saturday=7. Empty array for one-time alarm.")
                        })
                        put("vibrate", JSONObject().apply {
                            put("type", "boolean")
                            put("description", "Whether the alarm should vibrate. Default true.")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("hour")
                        put("minute")
                    })
                })
            })
        })

        // dismiss_alarm
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "dismiss_alarm")
                put("description", "Dismiss all currently ringing alarms.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })

        // snooze_alarm
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "snooze_alarm")
                put("description", "Snooze the currently ringing alarm.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("snooze_minutes", JSONObject().apply {
                            put("type", "integer")
                            put("description", "How many minutes to snooze. Optional.")
                        })
                    })
                })
            })
        })

        // show_alarms
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "show_alarms")
                put("description", "Open the clock app to show all existing alarms.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })

        // set_timer
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "set_timer")
                put("description", "Set a countdown timer. Duration must be in seconds.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("seconds", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Timer duration in seconds (e.g. 300 for 5 minutes)")
                        })
                        put("message", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional label for the timer")
                        })
                    })
                    put("required", JSONArray().apply { put("seconds") })
                })
            })
        })

        // show_timers
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "show_timers")
                put("description", "Open the clock app to show existing timers.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })

        // start_stopwatch
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "start_stopwatch")
                put("description", "Start the stopwatch in the clock app.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })

        // --- Calculator ---
        // evaluate_expression
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "evaluate_expression")
                put("description", "Evaluate a mathematical expression. Supports +, -, *, /, ^, %, parentheses. Example: '(15 + 25) * 3'")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("expression", JSONObject().apply {
                            put("type", "string")
                            put("description", "The math expression to evaluate")
                        })
                    })
                    put("required", JSONArray().apply { put("expression") })
                })
            })
        })

        // convert_unit
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "convert_unit")
                put("description", "Convert a value from one unit to another. Supports length (km, miles, m, ft, cm, inches), weight (kg, lbs, g, oz), temperature (celsius/c, fahrenheit/f, kelvin), volume (liters, gallons, ml), speed (kmh, mph), area (sqm, sqft, acres, hectares).")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("value", JSONObject().apply {
                            put("type", "number")
                            put("description", "The numeric value to convert")
                        })
                        put("from_unit", JSONObject().apply {
                            put("type", "string")
                            put("description", "Source unit (e.g. km, lbs, celsius, liters)")
                        })
                        put("to_unit", JSONObject().apply {
                            put("type", "string")
                            put("description", "Target unit (e.g. miles, kg, fahrenheit, gallons)")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("value"); put("from_unit"); put("to_unit")
                    })
                })
            })
        })

        // --- Calendar ---
        // create_calendar_event
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "create_calendar_event")
                put("description", "Create a new calendar event. Opens the system calendar to confirm.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("title", JSONObject().apply {
                            put("type", "string")
                            put("description", "Event title")
                        })
                        put("start_datetime", JSONObject().apply {
                            put("type", "string")
                            put("description", "Start date and time in format YYYY-MM-DD HH:mm")
                        })
                        put("end_datetime", JSONObject().apply {
                            put("type", "string")
                            put("description", "End date and time in format YYYY-MM-DD HH:mm")
                        })
                        put("description", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional event description")
                        })
                        put("location", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional event location")
                        })
                        put("all_day", JSONObject().apply {
                            put("type", "boolean")
                            put("description", "Whether this is an all-day event. Default false.")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("title"); put("start_datetime"); put("end_datetime")
                    })
                })
            })
        })

        // get_today_events
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_today_events")
                put("description", "Get all calendar events for today.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })

        // get_tomorrow_events
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_tomorrow_events")
                put("description", "Get all calendar events for tomorrow.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })

        // get_week_events
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_week_events")
                put("description", "Get all calendar events for the rest of this week.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })

        // open_calendar
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "open_calendar")
                put("description", "Open the calendar app.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })

        // --- Mail ---
        // compose_email
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "compose_email")
                put("description", "Compose and open an email in the user's mail app. The user will review and send it manually.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("to", JSONObject().apply {
                            put("type", "string")
                            put("description", "Recipient email address(es), comma-separated")
                        })
                        put("subject", JSONObject().apply {
                            put("type", "string")
                            put("description", "Email subject line")
                        })
                        put("body", JSONObject().apply {
                            put("type", "string")
                            put("description", "Email body text")
                        })
                        put("cc", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional CC recipients, comma-separated")
                        })
                        put("bcc", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional BCC recipients, comma-separated")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("to"); put("subject"); put("body")
                    })
                })
            })
        })

        // open_mail
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "open_mail")
                put("description", "Open the user's default email app to check inbox.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })

        // share_via_email
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "share_via_email")
                put("description", "Share content via email using the system share sheet. Useful for forwarding text or information.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("subject", JSONObject().apply {
                            put("type", "string")
                            put("description", "Email subject")
                        })
                        put("body", JSONObject().apply {
                            put("type", "string")
                            put("description", "Content to share")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("subject"); put("body")
                    })
                })
            })
        })

        // --- Device Search ---
        // search_apps
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "search_apps")
                put("description", "Search installed apps by name. Returns app names and package names.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("query", JSONObject().apply {
                            put("type", "string")
                            put("description", "App name or partial name to search for")
                        })
                    })
                    put("required", JSONArray().apply { put("query") })
                })
            })
        })

        // launch_app
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "launch_app")
                put("description", "Launch an app by its package name. Use search_apps first to find the package name.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("package_name", JSONObject().apply {
                            put("type", "string")
                            put("description", "The package name of the app to launch")
                        })
                    })
                    put("required", JSONArray().apply { put("package_name") })
                })
            })
        })

        // open_settings
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "open_settings")
                put("description", "Open a specific system settings page. Supported: wifi, bluetooth, display, sound, battery, storage, apps, location, security, accessibility, date, language, developer, nfc, notifications. Use 'general' for main settings.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("settings_type", JSONObject().apply {
                            put("type", "string")
                            put("description", "The type of settings to open (e.g. wifi, bluetooth, display)")
                        })
                    })
                    put("required", JSONArray().apply { put("settings_type") })
                })
            })
        })

        // --- Web Search ---
        // web_search
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "web_search")
                put("description", "Search the web for information. Returns text snippets that can be summarized for the user. Use this for factual questions, current events, definitions, etc.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("query", JSONObject().apply {
                            put("type", "string")
                            put("description", "The search query")
                        })
                    })
                    put("required", JSONArray().apply { put("query") })
                })
            })
        })

        // open_web_search
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "open_web_search")
                put("description", "Open a web search in the browser for the user to browse results themselves.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("query", JSONObject().apply {
                            put("type", "string")
                            put("description", "The search query")
                        })
                    })
                    put("required", JSONArray().apply { put("query") })
                })
            })
        })

        // open_url
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "open_url")
                put("description", "Open a specific URL in the browser.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("url", JSONObject().apply {
                            put("type", "string")
                            put("description", "The URL to open")
                        })
                    })
                    put("required", JSONArray().apply { put("url") })
                })
            })
        })

        // --- Weather ---
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_weather")
                put("description", "Get current weather and 3-day forecast for a location. Returns temperature, conditions, humidity, wind, and daily forecast.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("location", JSONObject().apply {
                            put("type", "string")
                            put("description", "City name (e.g. 'London', 'New York', 'Mumbai')")
                        })
                    })
                    put("required", JSONArray().apply { put("location") })
                })
            })
        })

        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_weather_here")
                put("description", "Get current weather and forecast for the user's current GPS location. Use this when the user says 'weather here', 'weather now', or doesn't specify a city.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })

        // --- Payments ---
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "launch_payment_app")
                put("description", "Launch a payment app. Supported: GPay, Google Pay, PhonePe, Paytm, PayPal, Samsung Pay, Amazon Pay, CRED, BHIM, WhatsApp Pay.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("app_name", JSONObject().apply {
                            put("type", "string")
                            put("description", "Name of the payment app to launch")
                        })
                    })
                    put("required", JSONArray().apply { put("app_name") })
                })
            })
        })

        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "upi_payment")
                put("description", "Initiate a UPI payment. Use the recipient's phone number as UPI ID in format 'phonenumber@upi' (e.g. '919667706240@upi'). Opens a UPI app to complete the payment.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("upi_id", JSONObject().apply {
                            put("type", "string")
                            put("description", "Recipient's UPI ID. Use phone number format: '91XXXXXXXXXX@upi' if no UPI ID is known.")
                        })
                        put("name", JSONObject().apply {
                            put("type", "string")
                            put("description", "Recipient name (optional)")
                        })
                        put("amount", JSONObject().apply {
                            put("type", "string")
                            put("description", "Amount to pay (optional)")
                        })
                    })
                    put("required", JSONArray().apply { put("upi_id") })
                })
            })
        })

        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "list_payment_apps")
                put("description", "List payment apps installed on the device.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })

        // --- Notifications ---
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_notifications")
                put("description", "Get recent notifications from the device. Returns app name, title, text, and time.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("count", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Number of notifications to return. Default 10.")
                        })
                    })
                })
            })
        })

        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_app_notifications")
                put("description", "Get notifications from a specific app (e.g. WhatsApp, Gmail).")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("app_name", JSONObject().apply {
                            put("type", "string")
                            put("description", "App name to filter notifications by")
                        })
                        put("count", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Number of notifications to return. Default 10.")
                        })
                    })
                    put("required", JSONArray().apply { put("app_name") })
                })
            })
        })

        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "clear_notifications")
                put("description", "Clear all stored notification history.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })

        // --- Memory ---
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "remember_fact")
                put("description", "Store a fact the user wants you to remember (e.g. 'my dog's name is Rex', 'my birthday is March 5'). Use a short key and the value.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("key", JSONObject().apply {
                            put("type", "string")
                            put("description", "Short key for the fact (e.g. 'dog_name', 'birthday')")
                        })
                        put("value", JSONObject().apply {
                            put("type", "string")
                            put("description", "The fact to remember")
                        })
                    })
                    put("required", JSONArray().apply { put("key"); put("value") })
                })
            })
        })

        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "recall_fact")
                put("description", "Look up a previously stored fact.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("key", JSONObject().apply {
                            put("type", "string")
                            put("description", "The key of the fact to recall")
                        })
                    })
                    put("required", JSONArray().apply { put("key") })
                })
            })
        })

        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_all_memories")
                put("description", "Get all stored facts and preferences.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })

        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "forget_fact")
                put("description", "Delete a previously stored fact.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("key", JSONObject().apply {
                            put("type", "string")
                            put("description", "The key of the fact to forget")
                        })
                    })
                    put("required", JSONArray().apply { put("key") })
                })
            })
        })

        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "set_preference")
                put("description", "Store a user preference (e.g. preferred language, temperature unit, nickname).")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("key", JSONObject().apply {
                            put("type", "string")
                            put("description", "Preference key (e.g. 'temperature_unit', 'nickname')")
                        })
                        put("value", JSONObject().apply {
                            put("type", "string")
                            put("description", "Preference value")
                        })
                    })
                    put("required", JSONArray().apply { put("key"); put("value") })
                })
            })
        })

        // --- Options / Confirmation ---
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "present_options")
                put("description", "Present clickable options to the user when a choice or confirmation is needed BEFORE executing an action. MUST be used before: making phone calls, creating calendar events, composing emails, initiating payments, and when multiple contacts match. Max 4 options. Always include a Cancel option last. Use button_styles to control how each button looks.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("prompt", JSONObject().apply {
                            put("type", "string")
                            put("description", "The question or prompt to display above the options")
                        })
                        put("options", JSONObject().apply {
                            put("type", "array")
                            put("items", JSONObject().apply { put("type", "string") })
                            put("description", "List of option labels (max 4). MUST be very short — max 25 characters each. Use just a name or brief label like 'Call Mom' or 'John Smith', never full phone numbers or long descriptions. Put details in the prompt instead.")
                            put("maxItems", 4)
                        })
                        put("button_styles", JSONObject().apply {
                            put("type", "array")
                            put("items", JSONObject().apply {
                                put("type", "string")
                                put("enum", JSONArray().apply { put("primary"); put("secondary"); put("cancel") })
                            })
                            put("description", "Style for each option button. 'primary' = highlighted action (e.g. call/confirm), 'secondary' = alternative action (e.g. change time), 'cancel' = dismiss/cancel (plain text, no background). Must match the length of options. Multiple primary and secondary allowed, but only one cancel.")
                        })
                    })
                    put("required", JSONArray().apply { put("prompt"); put("options"); put("button_styles") })
                })
            })
        })

        return tools

        // --- Knowledge Graph ---
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "query_knowledge_graph")
                put("description", "Query the on-device knowledge graph to find entities and relationships related to a topic. Use this to look up connections between things the user has told you about (e.g. 'what do you know about my family', 'what are my hobbies').")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("topic", JSONObject().apply {
                            put("type", "string")
                            put("description", "The topic or entity to search for in the knowledge graph")
                        })
                    })
                    put("required", JSONArray().apply { put("topic") })
                })
            })
        })

        return tools
    }
}
