package com.alfredassistant.alfred_ai.assistant

import android.content.Context
import com.alfredassistant.alfred_ai.db.ObjectBoxStore
import com.alfredassistant.alfred_ai.embedding.EmbeddingModel
import com.alfredassistant.alfred_ai.features.alarm.AlarmAction
import com.alfredassistant.alfred_ai.features.calculator.CalculatorAction
import com.alfredassistant.alfred_ai.features.calendar.CalendarAction
import com.alfredassistant.alfred_ai.features.mail.MailAction
import com.alfredassistant.alfred_ai.features.memory.KnowledgeGraph
import com.alfredassistant.alfred_ai.features.memory.MemoryStore
import com.alfredassistant.alfred_ai.features.memory.ToolRegistry
import com.alfredassistant.alfred_ai.features.notifications.NotificationAction
import com.alfredassistant.alfred_ai.features.payments.PaymentAction
import com.alfredassistant.alfred_ai.features.phone.PhoneAction
import com.alfredassistant.alfred_ai.features.phone.toJsonString
import com.alfredassistant.alfred_ai.features.search.SearchAction
import com.alfredassistant.alfred_ai.features.weather.WeatherAction
import com.alfredassistant.alfred_ai.ui.ConfirmationRequest
import kotlinx.coroutines.CompletableDeferred

class AlfredBrain(context: Context) {

    private val mistral = MistralClient()
    private val phoneAction = PhoneAction(context)
    private val alarmAction = AlarmAction(context)
    private val calculatorAction = CalculatorAction()
    private val calendarAction = CalendarAction(context)
    private val mailAction = MailAction(context)
    private val searchAction = SearchAction(context)
    private val weatherAction = WeatherAction(context)
    private val paymentAction = PaymentAction(context)
    private val notificationAction = NotificationAction(context)

    // Vector DB + Embedding infrastructure
    private val embeddingModel = EmbeddingModel(context)
    private val memoryStore = MemoryStore(context, embeddingModel)
    private val knowledgeGraph = KnowledgeGraph(embeddingModel)
    private val toolRegistry = ToolRegistry(embeddingModel)

    // Callback for when AI wants to show options to the user
    var onConfirmationNeeded: ((ConfirmationRequest) -> Unit)? = null

    // Callback for when a redirecting action was performed (call, open app, etc.)
    var onRedirectingAction: (() -> Unit)? = null

    // Deferred that gets completed when user picks an option
    private var pendingSelection: CompletableDeferred<String>? = null

    // Set to true when a tool call redirects to another app/screen
    @Volatile
    var didRedirect: Boolean = false
        private set

    /** Last set of tool names selected for a query (for debug UI) */
    var lastSelectedTools: List<String> = emptyList()
        private set
    /** Last set of tool calls executed (for debug UI) */
    var lastExecutedTools: List<String> = emptyList()
        private set

    init {
        // Initialize ObjectBox if not already done
        ObjectBoxStore.init(context)
        // Initialize embedding model in background
        embeddingModel.initialize()
        // Register all tools with embeddings for smart routing
        toolRegistry.registerTools(mistral.getAllToolDefinitions())
    }

    /**
     * Called from the UI when user taps or speaks an option.
     */
    fun submitOptionSelection(selectedOption: String) {
        pendingSelection?.complete(selectedOption)
    }

    /** True when the tool loop is waiting for the user to pick an option. */
    val isAwaitingSelection: Boolean
        get() = pendingSelection?.isActive == true

    companion object {
        /** Tool calls that redirect the user to another app/screen */
        private val REDIRECTING_TOOLS = setOf(
            "make_call", "dial_number", "launch_app", "open_url", "open_settings",
            "launch_payment_app", "upi_payment", "open_mail", "open_calendar",
            "compose_email", "share_via_email", "open_web_search"
        )
    }

    suspend fun processInput(userSpeech: String): String {
        didRedirect = false
        // Inject relevant memory context (semantic search based on user query)
        val memoryContext = memoryStore.getRelevantMemoryContext(userSpeech)
        val graphContext = knowledgeGraph.getGraphContext(userSpeech)
        val fullContext = listOf(memoryContext, graphContext)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        mistral.setMemoryContext(fullContext)

        // Get only relevant tools for this query (smart routing)
        val relevantTools = toolRegistry.getRelevantTools(userSpeech)

        // Track selected tools for debug UI
        val toolNames = mutableListOf<String>()
        for (i in 0 until relevantTools.length()) {
            try {
                toolNames.add(relevantTools.getJSONObject(i).getJSONObject("function").getString("name"))
            } catch (_: Exception) {}
        }
        lastSelectedTools = toolNames

        val executedNames = mutableListOf<String>()

        var result = mistral.chat(userSpeech, relevantTools)

        var maxIterations = 5
        while (result.toolCalls.isNotEmpty() && maxIterations > 0) {
            maxIterations--
            val callNames = result.toolCalls.joinToString(", ") { it.functionName }
            android.util.Log.d("AlfredBrain", "Tool loop iteration ${5 - maxIterations}: [$callNames]")
            val toolResults = result.toolCalls.map { call ->
                executedNames.add(call.functionName)
                val res = executeToolCall(call)
                android.util.Log.d("AlfredBrain", "  ${call.functionName} → ${res.take(100)}")
                if (call.functionName in REDIRECTING_TOOLS) {
                    didRedirect = true
                }
                Pair(call.id, res)
            }
            result = mistral.sendToolResults(toolResults)
        }

        if (maxIterations == 0 && result.toolCalls.isNotEmpty()) {
            android.util.Log.w("AlfredBrain", "Tool loop hit max iterations, forcing stop")
        }

        lastExecutedTools = executedNames

        return result.content ?: "All done!"
    }

    private suspend fun executeToolCall(call: ToolCall): String {
        return try {
            when (call.functionName) {
                // --- Phone ---
                "search_contacts" -> {
                    val q = call.arguments.getString("query")
                    val contacts = phoneAction.searchContacts(q)
                    if (contacts.isEmpty()) "No contacts found matching \"$q\"."
                    else contacts.toJsonString()
                }
                "make_call" -> {
                    phoneAction.makeCall(call.arguments.getString("phone_number"))
                    "Call initiated."
                }
                "dial_number" -> {
                    phoneAction.dialNumber(call.arguments.getString("phone_number"))
                    "Dialer opened."
                }

                // --- Alarm ---
                "set_alarm" -> {
                    val h = call.arguments.getInt("hour")
                    val m = call.arguments.getInt("minute")
                    val msg = call.arguments.optString("message", null)
                    val vib = call.arguments.optBoolean("vibrate", true)
                    val daysArr = call.arguments.optJSONArray("days")
                    val days = mutableListOf<Int>()
                    if (daysArr != null) for (i in 0 until daysArr.length()) days.add(daysArr.getInt(i))
                    alarmAction.setAlarm(h, m, msg, days, vib)
                    val t = String.format("%02d:%02d", h, m)
                    if (days.isEmpty()) "Alarm set for $t." else "Recurring alarm set for $t."
                }
                "dismiss_alarm" -> { alarmAction.dismissAlarm(); "Alarm dismissed." }
                "snooze_alarm" -> {
                    val mins = if (call.arguments.has("snooze_minutes")) call.arguments.getInt("snooze_minutes") else null
                    alarmAction.snoozeAlarm(mins); "Alarm snoozed."
                }
                "show_alarms" -> { alarmAction.showAlarms(); "Showing alarms." }
                "set_timer" -> {
                    val sec = call.arguments.getInt("seconds")
                    alarmAction.setTimer(sec, call.arguments.optString("message", null))
                    val mins = sec / 60; val secs = sec % 60
                    val t = if (mins > 0 && secs > 0) "$mins min $secs sec" else if (mins > 0) "$mins min" else "$secs sec"
                    "Timer set for $t."
                }
                "show_timers" -> { alarmAction.showTimers(); "Showing timers." }
                "start_stopwatch" -> { alarmAction.startStopwatch(); "Stopwatch started." }

                // --- Calculator ---
                "evaluate_expression" -> "Result: ${calculatorAction.evaluate(call.arguments.getString("expression"))}"
                "convert_unit" -> calculatorAction.convertUnit(
                    call.arguments.getDouble("value"),
                    call.arguments.getString("from_unit"),
                    call.arguments.getString("to_unit")
                )

                // --- Calendar ---
                "create_calendar_event" -> {
                    val title = call.arguments.getString("title")
                    val start = calendarAction.parseDateTime(call.arguments.getString("start_datetime"))
                    val end = calendarAction.parseDateTime(call.arguments.getString("end_datetime"))
                    calendarAction.createEvent(title, start, end,
                        call.arguments.optString("description", null),
                        call.arguments.optString("location", null),
                        call.arguments.optBoolean("all_day", false))
                    "Event '$title' created."
                }
                "get_today_events" -> calendarAction.getTodayEvents()
                "get_tomorrow_events" -> calendarAction.getTomorrowEvents()
                "get_week_events" -> calendarAction.getWeekEvents()
                "open_calendar" -> { calendarAction.openCalendar(); "Calendar opened." }

                // --- Mail ---
                "compose_email" -> {
                    mailAction.composeEmail(
                        call.arguments.getString("to"),
                        call.arguments.getString("subject"),
                        call.arguments.getString("body"),
                        call.arguments.optString("cc", null),
                        call.arguments.optString("bcc", null))
                    "Email composed. Please review and send."
                }
                "open_mail" -> { mailAction.openMail(); "Mail opened." }
                "share_via_email" -> {
                    mailAction.shareViaEmail(call.arguments.getString("subject"), call.arguments.getString("body"))
                    "Share sheet opened."
                }

                // --- Device Search ---
                "search_apps" -> searchAction.searchApps(call.arguments.getString("query"))
                "launch_app" -> searchAction.launchApp(call.arguments.getString("package_name"))
                "open_settings" -> searchAction.openSettings(call.arguments.getString("settings_type"))

                // --- Web Search ---
                "web_search" -> searchAction.webSearch(call.arguments.getString("query"))
                "open_web_search" -> { searchAction.openWebSearch(call.arguments.getString("query")); "Browser opened." }
                "open_url" -> { searchAction.openUrl(call.arguments.getString("url")); "URL opened." }

                // --- Weather ---
                "get_weather" -> weatherAction.getWeather(call.arguments.getString("location"))
                "get_weather_here" -> weatherAction.getWeatherHere()

                // --- Payments ---
                "launch_payment_app" -> paymentAction.launchPaymentApp(call.arguments.getString("app_name"))
                "upi_payment" -> paymentAction.openUpiPayment(
                    call.arguments.getString("upi_id"),
                    call.arguments.optString("name", null),
                    call.arguments.optString("amount", null))
                "list_payment_apps" -> paymentAction.listAvailablePaymentApps()

                // --- Notifications ---
                "get_notifications" -> {
                    if (!notificationAction.isListenerEnabled()) {
                        notificationAction.openListenerSettings()
                        "Notification access is required. I've opened the settings — please enable Alfred."
                    } else {
                        notificationAction.getRecentNotifications(call.arguments.optInt("count", 10))
                    }
                }
                "get_app_notifications" -> {
                    if (!notificationAction.isListenerEnabled()) {
                        notificationAction.openListenerSettings()
                        "Notification access is required. I've opened the settings — please enable Alfred."
                    } else {
                        notificationAction.getNotificationsFromApp(
                            call.arguments.getString("app_name"),
                            call.arguments.optInt("count", 10))
                    }
                }
                "clear_notifications" -> notificationAction.clearNotifications()

                // --- Memory (now backed by ObjectBox + vector search) ---
                "remember_fact" -> {
                    val key = call.arguments.getString("key")
                    val value = call.arguments.getString("value")
                    // Also extract into knowledge graph
                    knowledgeGraph.extractAndStore(key, value)
                    memoryStore.rememberFact(key, value)
                }
                "recall_fact" -> memoryStore.recallFact(call.arguments.getString("key"))
                "get_all_memories" -> memoryStore.getAllFacts() + "\n" + memoryStore.getAllPreferences()
                "forget_fact" -> memoryStore.forgetFact(call.arguments.getString("key"))
                "set_preference" -> memoryStore.setPreference(call.arguments.getString("key"), call.arguments.getString("value"))

                // --- Knowledge Graph ---
                "query_knowledge_graph" -> knowledgeGraph.queryGraph(call.arguments.getString("topic"))

                // --- Options / Confirmation ---
                "present_options" -> {
                    val prompt = call.arguments.getString("prompt")
                    val optionsArr = call.arguments.getJSONArray("options")
                    val stylesArr = call.arguments.optJSONArray("button_styles")
                    val options = mutableListOf<String>()
                    val styles = mutableListOf<String>()
                    for (i in 0 until minOf(optionsArr.length(), 4)) {
                        options.add(optionsArr.getString(i))
                        styles.add(stylesArr?.optString(i, "primary") ?: "primary")
                    }
                    // Build a shorter spoken version — abbreviate phone numbers
                    val spokenPrompt = abbreviateForSpeech(prompt)
                    // Show options to user and suspend until they pick one
                    val deferred = CompletableDeferred<String>()
                    pendingSelection = deferred
                    onConfirmationNeeded?.invoke(
                        ConfirmationRequest(prompt, options, styles, spokenPrompt)
                    )
                    val selection = deferred.await()
                    pendingSelection = null
                    if (selection.equals("Cancel", ignoreCase = true)) {
                        "User cancelled the action."
                    } else {
                        "User selected: $selection"
                    }
                }

                else -> "Unknown function: ${call.functionName}"
            }
        } catch (e: Exception) {
            "Error executing ${call.functionName}: ${e.message}"
        }
    }

    fun resetConversation() {
        mistral.clearHistory()
    }

    /**
     * Shorten text for TTS — replaces full phone numbers with "ending in XXX"
     * and trims other verbose details that sound bad when spoken aloud.
     */
    private fun abbreviateForSpeech(text: String): String {
        // Replace phone numbers (7+ digits, with optional +, spaces, dashes) with last 3 digits
        val phoneRegex = Regex("""[\+]?[\d\s\-\(\)]{7,}""")
        return phoneRegex.replace(text) { match ->
            val digits = match.value.filter { it.isDigit() }
            if (digits.length >= 4) {
                "ending in ${digits.takeLast(3)}"
            } else {
                match.value
            }
        }
    }

    // ==================== DEBUG DATA ====================

    /** Get all stored memories for debug display */
    fun getDebugMemories(): List<Pair<String, String>> {
        return memoryStore.getDebugEntries()
    }

    /** Get all knowledge graph nodes for debug display */
    fun getDebugGraphNodes(): List<Triple<Long, String, String>> {
        return knowledgeGraph.getDebugNodes()
    }

    /** Get all knowledge graph edges for debug display */
    fun getDebugGraphEdges(): List<Triple<String, String, String>> {
        return knowledgeGraph.getDebugEdges()
    }
}
