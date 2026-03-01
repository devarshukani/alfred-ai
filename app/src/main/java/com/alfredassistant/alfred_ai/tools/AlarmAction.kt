package com.alfredassistant.alfred_ai.tools

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.alfredassistant.alfred_ai.skills.Param
import com.alfredassistant.alfred_ai.skills.ToolDef

/**
 * Alarm, Timer, and Stopwatch actions using Android's native AlarmClock intents.
 * These launch the system Clock app to perform the action.
 */
class AlarmAction(private val context: Context) {

    /**
     * Set an alarm.
     * @param hour 0-23
     * @param minute 0-59
     * @param message Optional label
     * @param days List of days (Calendar.MONDAY=2 .. Calendar.SUNDAY=1). Empty = one-time alarm.
     * @param vibrate Whether to vibrate
     * @param skipUi If true, sets alarm silently without opening clock app
     */
    fun setAlarm(
        hour: Int,
        minute: Int,
        message: String? = null,
        days: List<Int> = emptyList(),
        vibrate: Boolean = true,
        skipUi: Boolean = true
    ) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_VIBRATE, vibrate)
            putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
            if (!message.isNullOrBlank()) {
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
            }
            if (days.isNotEmpty()) {
                putExtra(AlarmClock.EXTRA_DAYS, ArrayList(days))
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Dismiss all currently ringing alarms.
     */
    fun dismissAlarm() {
        val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Snooze the currently ringing alarm.
     * @param snoozeDurationMinutes How long to snooze (optional).
     */
    fun snoozeAlarm(snoozeDurationMinutes: Int? = null) {
        val intent = Intent(AlarmClock.ACTION_SNOOZE_ALARM).apply {
            if (snoozeDurationMinutes != null) {
                putExtra(AlarmClock.EXTRA_ALARM_SNOOZE_DURATION, snoozeDurationMinutes)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Show all alarms in the clock app.
     */
    fun showAlarms() {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Set a countdown timer.
     * @param seconds Duration in seconds
     * @param message Optional label
     * @param skipUi If true, starts timer silently
     */
    fun setTimer(
        seconds: Int,
        message: String? = null,
        skipUi: Boolean = true
    ) {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
            if (!message.isNullOrBlank()) {
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Show existing timers in the clock app.
     */
    fun showTimers() {
        val intent = Intent(AlarmClock.ACTION_SHOW_TIMERS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Start a stopwatch.
     */
    fun startStopwatch() {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            val stopwatchIntent = Intent("android.intent.action.SHOW_STOPWATCH").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(stopwatchIntent)
        } catch (e: Exception) {
            showTimers()
        }
    }

    fun toolDefs(): List<ToolDef> = listOf(
        ToolDef(
            name = "set_alarm",
            description = "Set an alarm on the device. Can be one-time or recurring on specific days.",
            parameters = listOf(
                Param(name = "hour", type = "integer", description = "Hour in 24-hour format (0-23)"),
                Param(name = "minute", type = "integer", description = "Minute (0-59)"),
                Param(name = "message", type = "string", description = "Optional label for the alarm"),
                Param(name = "days", type = "array", description = "Days to repeat: Sunday=1..Saturday=7. Empty for one-time.",
                    items = Param(name = "day", type = "integer", description = "Day number")),
                Param(name = "vibrate", type = "boolean", description = "Whether to vibrate. Default true.")
            ),
            required = listOf("hour", "minute")
        ) { args ->
            val h = args.getInt("hour"); val m = args.getInt("minute")
            val msg = args.optString("message", null)
            val vib = args.optBoolean("vibrate", true)
            val daysArr = args.optJSONArray("days")
            val days = mutableListOf<Int>()
            if (daysArr != null) for (i in 0 until daysArr.length()) days.add(daysArr.getInt(i))
            setAlarm(h, m, msg, days, vib)
            val t = String.format("%02d:%02d", h, m)
            if (days.isEmpty()) "Alarm set for $t." else "Recurring alarm set for $t."
        },
        ToolDef(name = "dismiss_alarm", description = "Dismiss all currently ringing alarms.") { _ -> dismissAlarm(); "Alarm dismissed." },
        ToolDef(name = "snooze_alarm", description = "Snooze the currently ringing alarm.",
            parameters = listOf(Param(name = "snooze_minutes", type = "integer", description = "Minutes to snooze. Optional."))
        ) { args -> snoozeAlarm(if (args.has("snooze_minutes")) args.getInt("snooze_minutes") else null); "Alarm snoozed." },
        ToolDef(name = "show_alarms", description = "Open the clock app to show all existing alarms.") { _ -> showAlarms(); "Showing alarms." },
        ToolDef(
            name = "set_timer",
            description = "Set a countdown timer. Duration must be in seconds.",
            parameters = listOf(
                Param(name = "seconds", type = "integer", description = "Timer duration in seconds (e.g. 300 for 5 minutes)"),
                Param(name = "message", type = "string", description = "Optional label for the timer")
            ),
            required = listOf("seconds")
        ) { args ->
            val sec = args.getInt("seconds")
            setTimer(sec, args.optString("message", null))
            val mins = sec / 60; val secs = sec % 60
            val t = if (mins > 0 && secs > 0) "$mins min $secs sec" else if (mins > 0) "$mins min" else "$secs sec"
            "Timer set for $t."
        },
        ToolDef(name = "show_timers", description = "Open the clock app to show existing timers.") { _ -> showTimers(); "Showing timers." },
        ToolDef(name = "start_stopwatch", description = "Start the stopwatch in the clock app.") { _ -> startStopwatch(); "Stopwatch started." }
    )
}
