package com.alfredassistant.alfred_ai.features.search

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SearchAction(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // ==================== DEVICE SEARCH ====================

    /**
     * Search installed apps by name. Returns matching app names + package names.
     */
    fun searchApps(query: String): String {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
        val q = query.lowercase()

        val matches = apps.filter {
            it.loadLabel(pm).toString().lowercase().contains(q)
        }.take(10).map { app ->
            JSONObject().apply {
                put("name", app.loadLabel(pm).toString())
                put("package", app.activityInfo.packageName)
            }
        }

        return if (matches.isEmpty()) "No apps found matching \"$query\"."
        else JSONArray(matches).toString()
    }

    /**
     * Launch an app by package name.
     */
    fun launchApp(packageName: String): String {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            "App launched."
        } else {
            "Could not find app to launch."
        }
    }

    /**
     * Open a specific system settings page.
     */
    fun openSettings(settingsType: String): String {
        val action = when (settingsType.lowercase()) {
            "wifi", "network" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "apps", "applications" -> Settings.ACTION_APPLICATION_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "security" -> Settings.ACTION_SECURITY_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "date", "time" -> Settings.ACTION_DATE_SETTINGS
            "language" -> Settings.ACTION_LOCALE_SETTINGS
            "developer" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            "nfc" -> Settings.ACTION_NFC_SETTINGS
            "notification", "notifications" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            "Settings opened."
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            "Opened general settings."
        }
    }

    /**
     * Search contacts by name (reuses phone contacts query but returns email + phone).
     */
    fun searchContacts(query: String): String {
        val results = JSONArray()
        val cursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?",
            arrayOf("%$query%"),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        )
        cursor?.use {
            var count = 0
            while (it.moveToNext() && count < 10) {
                val name = it.getString(
                    it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                ) ?: continue
                results.put(JSONObject().apply { put("name", name) })
                count++
            }
        }
        return if (results.length() == 0) "No contacts found matching \"$query\"."
        else results.toString()
    }

    // ==================== WEB SEARCH ====================

    /**
     * Perform a web search and return summarizable snippets.
     * Uses DuckDuckGo Instant Answer API (no API key needed).
     */
    suspend fun webSearch(query: String): String = withContext(Dispatchers.IO) {
        try {
            // DuckDuckGo Instant Answer API
            val url = "https://api.duckduckgo.com/?q=${Uri.encode(query)}&format=json&no_html=1&skip_disambig=1"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)

            val results = mutableListOf<String>()

            // Abstract (main answer)
            val abstract = json.optString("AbstractText", "")
            if (abstract.isNotBlank()) {
                results.add(abstract)
            }

            // Answer (instant answer)
            val answer = json.optString("Answer", "")
            if (answer.isNotBlank()) {
                results.add(answer)
            }

            // Related topics
            val relatedTopics = json.optJSONArray("RelatedTopics")
            if (relatedTopics != null) {
                for (i in 0 until minOf(relatedTopics.length(), 3)) {
                    val topic = relatedTopics.optJSONObject(i)
                    val text = topic?.optString("Text", "") ?: ""
                    if (text.isNotBlank()) results.add(text)
                }
            }

            if (results.isEmpty()) {
                "No instant results found. Opening web search."
            } else {
                results.joinToString("\n\n")
            }
        } catch (e: Exception) {
            "Web search failed: ${e.message}"
        }
    }

    /**
     * Open a web search in the browser.
     */
    fun openWebSearch(query: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to browser
            val browserIntent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        }
    }

    /**
     * Open a URL in the browser.
     */
    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
