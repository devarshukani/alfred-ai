package com.alfredassistant.alfred_ai.assistant

import android.content.Context
import com.alfredassistant.alfred_ai.features.alarm.AlarmAction
import com.alfredassistant.alfred_ai.features.calculator.CalculatorAction
import com.alfredassistant.alfred_ai.features.calendar.CalendarAction
import com.alfredassistant.alfred_ai.features.phone.PhoneAction
import com.alfredassistant.alfred_ai.features.phone.toJsonString

/**
 * Alfred's brain — orchestrates Mistral API calls and executes tool actions.
 */
class AlfredBrain(context: Context) {

    private val mistral = MistralClient()
    private val phoneAction = PhoneAction(context)
    private val alarmAction = AlarmAction(context)
    private val calculatorAction = CalculatorAction()
    private val calendarAction = CalendarAction(context)

    suspend fun processInput(userSpeech: String): String {
        var result = mistral.chat(userSpeech)

        // Tool call loop — keep executing until we get a text response
        var maxIterations = 5
        while (result.toolCalls.isNotEmpty() && maxIterations > 0) {
            maxIterations--

            val toolResults = result.toolCalls.map { call ->
                val output = executeToolCall(call)
                Pair(call.id, output)
            }

            result = mistral.sendToolResults(toolResults)
        }

        return result.content
            ?: "I've completed the action, sir."
    }

    private fun executeToolCall(call: ToolCall): String {
        return try {
            when (call.functionName) {
                // --- Phone ---
                "search_contacts" -> {
                    val query = call.arguments.getString("query")
                    val contacts = phoneAction.searchContacts(query)
                    if (contacts.isEmpty()) "No contacts found matching \"$query\"."
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
                    val hour = call.arguments.getInt("hour")
                    val minute = call.arguments.getInt("minute")
                    val message = call.arguments.optString("message", null)
                    val vibrate = call.arguments.optBoolean("vibrate", true)
                    val daysArr = call.arguments.optJSONArray("days")
                    val days = mutableListOf<Int>()
                    if (daysArr != null) {
                        for (i in 0 until daysArr.length()) {
                            days.add(daysArr.getInt(i))
                        }
                    }
                    alarmAction.setAlarm(hour, minute, message, days, vibrate)
                    val timeStr = String.format("%02d:%02d", hour, minute)
                    if (days.isEmpty()) "Alarm set for $timeStr."
                    else "Recurring alarm set for $timeStr."
                }
                "dismiss_alarm" -> {
                    alarmAction.dismissAlarm()
                    "Alarm dismissed."
                }
                "snooze_alarm" -> {
                    val mins = if (call.arguments.has("snooze_minutes"))
                        call.arguments.getInt("snooze_minutes") else null
                    alarmAction.snoozeAlarm(mins)
                    "Alarm snoozed."
                }
                "show_alarms" -> {
                    alarmAction.showAlarms()
                    "Showing all alarms."
                }

                // --- Timer ---
                "set_timer" -> {
                    val seconds = call.arguments.getInt("seconds")
                    val message = call.arguments.optString("message", null)
                    alarmAction.setTimer(seconds, message)
                    val mins = seconds / 60
                    val secs = seconds % 60
                    val timeStr = if (mins > 0 && secs > 0) "$mins minutes and $secs seconds"
                        else if (mins > 0) "$mins minutes"
                        else "$secs seconds"
                    "Timer set for $timeStr."
                }
                "show_timers" -> {
                    alarmAction.showTimers()
                    "Showing timers."
                }

                // --- Stopwatch ---
                "start_stopwatch" -> {
                    alarmAction.startStopwatch()
                    "Stopwatch started."
                }

                // --- Calculator ---
                "evaluate_expression" -> {
                    val expr = call.arguments.getString("expression")
                    val result = calculatorAction.evaluate(expr)
                    "Result: $result"
                }
                "convert_unit" -> {
                    val value = call.arguments.getDouble("value")
                    val from = call.arguments.getString("from_unit")
                    val to = call.arguments.getString("to_unit")
                    calculatorAction.convertUnit(value, from, to)
                }

                // --- Calendar ---
                "create_calendar_event" -> {
                    val title = call.arguments.getString("title")
                    val startStr = call.arguments.getString("start_datetime")
                    val endStr = call.arguments.getString("end_datetime")
                    val desc = call.arguments.optString("description", null)
                    val location = call.arguments.optString("location", null)
                    val allDay = call.arguments.optBoolean("all_day", false)
                    val startMillis = calendarAction.parseDateTime(startStr)
                    val endMillis = calendarAction.parseDateTime(endStr)
                    calendarAction.createEvent(title, startMillis, endMillis, desc, location, allDay)
                    "Calendar event '$title' created."
                }
                "get_today_events" -> {
                    calendarAction.getTodayEvents()
                }
                "get_tomorrow_events" -> {
                    calendarAction.getTomorrowEvents()
                }
                "get_week_events" -> {
                    calendarAction.getWeekEvents()
                }
                "open_calendar" -> {
                    calendarAction.openCalendar()
                    "Calendar opened."
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
}
