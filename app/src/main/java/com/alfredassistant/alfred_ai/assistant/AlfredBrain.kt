package com.alfredassistant.alfred_ai.assistant

import android.content.Context
import com.alfredassistant.alfred_ai.features.alarm.AlarmAction
import com.alfredassistant.alfred_ai.features.calculator.CalculatorAction
import com.alfredassistant.alfred_ai.features.calendar.CalendarAction
import com.alfredassistant.alfred_ai.features.mail.MailAction
import com.alfredassistant.alfred_ai.features.memory.MemoryStore
import com.alfredassistant.alfred_ai.features.notifications.NotificationAction
import com.alfredassistant.alfred_ai.features.payments.PaymentAction
import com.alfredassistant.alfred_ai.features.phone.PhoneAction
import com.alfredassistant.alfred_ai.features.phone.toJsonString
import com.alfredassistant.alfred_ai.features.search.SearchAction
import com.alfredassistant.alfred_ai.features.weather.WeatherAction

class AlfredBrain(context: Context) {

    private val mistral = MistralClient()
    private val phoneAction = PhoneAction(context)
    private val alarmAction = AlarmAction(context)
    private val calculatorAction = CalculatorAction()
    private val calendarAction = CalendarAction(context)
    private val mailAction = MailAction(context)
    private val searchAction = SearchAction(context)
    private val weatherAction = WeatherAction()
    private val paymentAction = PaymentAction(context)
    private val notificationAction = NotificationAction(context)
    private val memoryStore = MemoryStore(context)

    suspend fun processInput(userSpeech: String): String {
        // Inject memory context into system prompt
        mistral.setMemoryContext(memoryStore.getMemoryContext())

        var result = mistral.chat(userSpeech)

        var maxIterations = 8
        while (result.toolCalls.isNotEmpty() && maxIterations > 0) {
            maxIterations--
            val toolResults = result.toolCalls.map { call ->
                Pair(call.id, executeToolCall(call))
            }
            result = mistral.sendToolResults(toolResults)
        }

        return result.content ?: "I've completed the action, sir."
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

                // --- Memory ---
                "remember_fact" -> memoryStore.rememberFact(call.arguments.getString("key"), call.arguments.getString("value"))
                "recall_fact" -> memoryStore.recallFact(call.arguments.getString("key"))
                "get_all_memories" -> memoryStore.getAllFacts() + "\n" + memoryStore.getAllPreferences()
                "forget_fact" -> memoryStore.forgetFact(call.arguments.getString("key"))
                "set_preference" -> memoryStore.setPreference(call.arguments.getString("key"), call.arguments.getString("value"))

                else -> "Unknown function: ${call.functionName}"
            }
        } catch (e: Exception) {
            "Error executing ${call.functionName}: ${e.message}"
        }
    }

    fun resetConversation() {
        mistral.clearHistory()
    }
}
