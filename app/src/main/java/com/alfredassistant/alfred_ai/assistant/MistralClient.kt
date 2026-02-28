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

You have access to tools for:
- Phone: search contacts, make calls, dial numbers. When multiple numbers exist, ask which one.
- Alarms: set alarms (one-time or recurring with days), dismiss, snooze, show all alarms.
  For days parameter use: Sunday=1, Monday=2, Tuesday=3, Wednesday=4, Thursday=5, Friday=6, Saturday=7.
  For recurring alarms like "weekdays", pass [2,3,4,5,6]. For "weekends", pass [1,7].
- Timers: set countdown timers (specify duration in seconds), show timers.
- Stopwatch: start the stopwatch.
- Calculator: evaluate math expressions and convert units.
- Calendar: create events, check today/tomorrow/week schedule, open calendar.
  For dates, use format "YYYY-MM-DD HH:mm". Always confirm event details before creating.
- Mail: compose emails (with to, subject, body, optional cc/bcc), open mail app.
  When composing, confirm the recipient and content with the user before sending.
  If the user doesn't provide an email address, search their contacts first to find it.
- Device Search: find and launch apps, search contacts, open specific system settings.
- Web Search: search the web for information and summarize results, open URLs, open browser search.
  Use web_search to get information, then summarize it naturally for voice. If results are insufficient, use open_web_search to open the browser.
- Weather: get current weather and 3-day forecast for any city. Summarize naturally.
- Payments: launch payment apps (GPay, PhonePe, Paytm, PayPal, etc.), initiate UPI payments, list available payment apps.
- Notifications: read recent notifications, filter by app, clear notifications. If listener not enabled, guide user to enable it.
- Memory: remember facts and preferences the user tells you (e.g. "my name is...", "I prefer..."). 
  Use remember_fact for things the user wants you to remember. Use recall_fact to look up stored info.
  Use set_preference for user preferences. Always check memory when it might be relevant.

When setting alarms, use 24-hour format internally. Confirm the time with the user naturally.
When setting timers, convert the user's request to seconds (e.g. "5 minutes" = 300 seconds).
For calendar events, infer reasonable end times if not specified (e.g. 1 hour after start).
Today's date is provided in the conversation — use it to calculate correct dates for "tomorrow", "next Monday", etc."""
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

    private var memoryContext: String = ""

    fun setMemoryContext(context: String) {
        memoryContext = context
    }

    private fun makeRequest(): ChatResult {
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
                put("description", "Initiate a UPI payment to a specific UPI ID.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("upi_id", JSONObject().apply {
                            put("type", "string")
                            put("description", "The recipient's UPI ID (e.g. name@upi)")
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

        return tools
    }
}
