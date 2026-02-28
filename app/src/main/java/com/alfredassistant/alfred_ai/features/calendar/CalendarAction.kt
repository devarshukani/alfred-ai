package com.alfredassistant.alfred_ai.features.calendar

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class CalendarAction(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())

    /**
     * Create a calendar event using the system calendar intent.
     * @param title Event title
     * @param startTimeMillis Start time in epoch millis
     * @param endTimeMillis End time in epoch millis
     * @param description Optional description
     * @param location Optional location
     * @param allDay Whether it's an all-day event
     */
    fun createEvent(
        title: String,
        startTimeMillis: Long,
        endTimeMillis: Long,
        description: String? = null,
        location: String? = null,
        allDay: Boolean = false
    ) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimeMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMillis)
            putExtra(CalendarContract.Events.ALL_DAY, allDay)
            if (!description.isNullOrBlank()) {
                putExtra(CalendarContract.Events.DESCRIPTION, description)
            }
            if (!location.isNullOrBlank()) {
                putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Query events within a time range.
     * @param startMillis Start of range (epoch millis)
     * @param endMillis End of range (epoch millis)
     * @return JSON string of events
     */
    fun getEvents(startMillis: Long, endMillis: Long): String {
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.ALL_DAY
        )

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startMillis.toString())
            .appendPath(endMillis.toString())
            .build()

        val events = JSONArray()
        val cursor: Cursor? = context.contentResolver.query(
            uri, projection, null, null,
            "${CalendarContract.Instances.BEGIN} ASC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val title = it.getString(1) ?: "No title"
                val begin = it.getLong(2)
                val end = it.getLong(3)
                val location = it.getString(4) ?: ""
                val description = it.getString(5) ?: ""
                val allDay = it.getInt(6) == 1

                events.put(JSONObject().apply {
                    put("title", title)
                    put("start", displayFormat.format(Date(begin)))
                    put("end", displayFormat.format(Date(end)))
                    put("location", location)
                    put("description", description)
                    put("all_day", allDay)
                })
            }
        }

        return if (events.length() == 0) "No events found in this time range."
        else events.toString()
    }

    /**
     * Get today's events.
     */
    fun getTodayEvents(): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startMillis = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, 1)
        val endMillis = cal.timeInMillis

        return getEvents(startMillis, endMillis)
    }

    /**
     * Get tomorrow's events.
     */
    fun getTomorrowEvents(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startMillis = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, 1)
        val endMillis = cal.timeInMillis

        return getEvents(startMillis, endMillis)
    }

    /**
     * Get this week's events (from now to end of week).
     */
    fun getWeekEvents(): String {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.add(Calendar.WEEK_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val endMillis = cal.timeInMillis

        return getEvents(now, endMillis)
    }

    /**
     * Open the calendar app to a specific day.
     */
    fun openCalendar(timeMillis: Long = System.currentTimeMillis()) {
        val uri = ContentUris.withAppendedId(
            CalendarContract.CONTENT_URI,
            timeMillis
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = uri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Helper: parse "YYYY-MM-DD HH:mm" to epoch millis.
     */
    fun parseDateTime(dateTimeStr: String): Long {
        return try {
            dateFormat.parse(dateTimeStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
