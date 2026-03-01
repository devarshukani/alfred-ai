package com.alfredassistant.alfred_ai.tools

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import com.alfredassistant.alfred_ai.skills.Param
import com.alfredassistant.alfred_ai.skills.ToolDef
import java.text.SimpleDateFormat
import java.util.*

/**
 * Notification listener that captures and provides access to device notifications.
 */
class AlfredNotificationListener : NotificationListenerService() {

    companion object {
        val recentNotifications = mutableListOf<NotificationData>()
        private const val MAX_STORED = 50
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val extras = sbn.notification.extras
        val data = NotificationData(
            appName = sbn.packageName,
            title = extras.getCharSequence("android.title")?.toString() ?: "",
            text = extras.getCharSequence("android.text")?.toString() ?: "",
            timestamp = sbn.postTime,
            key = sbn.key
        )
        synchronized(recentNotifications) {
            recentNotifications.add(0, data)
            while (recentNotifications.size > MAX_STORED) {
                recentNotifications.removeAt(recentNotifications.size - 1)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        synchronized(recentNotifications) {
            recentNotifications.removeAll { it.key == sbn.key }
        }
    }
}

data class NotificationData(
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val key: String
)

class NotificationAction(private val context: Context) {

    private val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    /**
     * Get recent notifications as JSON.
     */
    fun getRecentNotifications(count: Int = 10): String {
        val notifications = synchronized(AlfredNotificationListener.recentNotifications) {
            AlfredNotificationListener.recentNotifications.take(count)
        }

        if (notifications.isEmpty()) {
            return "No recent notifications."
        }

        val arr = JSONArray()
        notifications.forEach { n ->
            arr.put(JSONObject().apply {
                put("app", getAppName(n.appName))
                put("title", n.title)
                put("text", n.text)
                put("time", dateFormat.format(Date(n.timestamp)))
            })
        }
        return arr.toString()
    }

    /**
     * Get notifications from a specific app.
     */
    fun getNotificationsFromApp(appQuery: String, count: Int = 10): String {
        val q = appQuery.lowercase()
        val notifications = synchronized(AlfredNotificationListener.recentNotifications) {
            AlfredNotificationListener.recentNotifications.filter {
                it.appName.lowercase().contains(q) ||
                    getAppName(it.appName).lowercase().contains(q)
            }.take(count)
        }

        if (notifications.isEmpty()) {
            return "No notifications from $appQuery."
        }

        val arr = JSONArray()
        notifications.forEach { n ->
            arr.put(JSONObject().apply {
                put("app", getAppName(n.appName))
                put("title", n.title)
                put("text", n.text)
                put("time", dateFormat.format(Date(n.timestamp)))
            })
        }
        return arr.toString()
    }

    /**
     * Clear all stored notifications.
     */
    fun clearNotifications(): String {
        synchronized(AlfredNotificationListener.recentNotifications) {
            AlfredNotificationListener.recentNotifications.clear()
        }
        return "Notifications cleared."
    }

    /**
     * Check if notification listener permission is granted.
     */
    fun isListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return flat?.contains(context.packageName) == true
    }

    /**
     * Open notification listener settings so user can grant permission.
     */
    fun openListenerSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast(".")
        }
    }

    fun toolDefs(): List<ToolDef> = listOf(
        ToolDef(name = "get_notifications", description = "Get recent notifications from the device. Returns app name, title, text, and time.",
            parameters = listOf(Param(name = "count", type = "integer", description = "Number of notifications. Default 10."))
        ) { args ->
            if (!isListenerEnabled()) { openListenerSettings(); "Notification access is required. I've opened the settings — please enable Alfred." }
            else getRecentNotifications(args.optInt("count", 10))
        },
        ToolDef(name = "get_app_notifications", description = "Get notifications from a specific app (e.g. WhatsApp, Gmail).",
            parameters = listOf(
                Param(name = "app_name", type = "string", description = "App name to filter by"),
                Param(name = "count", type = "integer", description = "Number of notifications. Default 10.")
            ),
            required = listOf("app_name")
        ) { args ->
            if (!isListenerEnabled()) { openListenerSettings(); "Notification access is required. I've opened the settings — please enable Alfred." }
            else getNotificationsFromApp(args.getString("app_name"), args.optInt("count", 10))
        },
        ToolDef(name = "clear_notifications", description = "Clear all stored notification history.") { _ -> clearNotifications() }
    )
}
