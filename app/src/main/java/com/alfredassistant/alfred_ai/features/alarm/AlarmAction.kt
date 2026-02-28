package com.alfredassistant.alfred_ai.features.alarm

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

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
            // There's no official stopwatch intent, but we can open timers
            // Some clock apps support ACTION_STOPWATCH
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        // Try the stopwatch intent first (supported on many devices)
        try {
            val stopwatchIntent = Intent("android.intent.action.SHOW_STOPWATCH").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(stopwatchIntent)
        } catch (e: Exception) {
            // Fallback: open timers
            showTimers()
        }
    }
}
