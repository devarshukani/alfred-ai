package com.alfredassistant.alfred_ai.tools

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.alfredassistant.alfred_ai.BuildConfig
import com.alfredassistant.alfred_ai.skills.Param
import com.alfredassistant.alfred_ai.skills.ToolDef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SearchAction(private val context: Context) {

    companion object {
        private const val TAG = "SearchAction"
        private const val CONVERSATIONS_URL = "https://api.mistral.ai/v1/conversations"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json".toMediaType()

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
        val intent = Intent(action).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
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
     * Web search via Mistral's pre-created web_search agent.
     * Calls POST /v1/conversations with the agent ID from BuildConfig.
     */
    suspend fun webSearch(query: String): String = withContext(Dispatchers.IO) {
        try {
            val agentId = BuildConfig.MISTRAL_AGENT_ID
            if (agentId.isBlank()) {
                return@withContext "Web search agent not configured. Add MISTRAL_AGENT_ID to local.properties."
            }

            val body = JSONObject().apply {
                put("agent_id", agentId)
                put("inputs", query)
                put("stream", false)
                put("store", false)
            }

            val request = Request.Builder()
                .url(CONVERSATIONS_URL)
                .addHeader("Authorization", "Bearer ${BuildConfig.MISTRAL_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(jsonType))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Search failed: ${response.code} $responseBody")
                return@withContext "Web search failed: HTTP ${response.code}"
            }

            parseSearchResponse(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Web search error", e)
            "Web search failed: ${e.message}"
        }
    }

    private fun parseSearchResponse(responseBody: String): String {
        val json = JSONObject(responseBody)
        val outputs = json.optJSONArray("outputs") ?: return "No results."

        val textParts = mutableListOf<String>()
        val sources = mutableListOf<String>()

        for (i in 0 until outputs.length()) {
            val output = outputs.getJSONObject(i)
            if (output.optString("type") != "message.output") continue

            val content = output.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val chunk = content.getJSONObject(j)
                when (chunk.optString("type")) {
                    "text" -> {
                        val text = chunk.optString("text", "")
                        if (text.isNotBlank()) textParts.add(text)
                    }
                    "tool_reference" -> {
                        val title = chunk.optString("title", "")
                        val url = chunk.optString("url", "")
                        if (title.isNotBlank() || url.isNotBlank()) {
                            sources.add("$title: $url")
                        }
                    }
                }
            }
        }

        val answer = textParts.joinToString("").trim()
        Log.d(TAG, "Parsed: ${answer.take(100)}... (${sources.size} sources)")

        return buildString {
            append(answer)
            if (sources.isNotEmpty()) {
                append("\n\nSources:")
                sources.forEach { append("\n- $it") }
            }
        }
    }

    fun toolDefs(): List<ToolDef> = listOf(
        ToolDef(
            name = "search_apps",
            description = "Search installed apps by name. Returns app names and package names.",
            parameters = listOf(Param(name = "query", type = "string", description = "App name to search for")),
            required = listOf("query")
        ) { args -> searchApps(args.getString("query")) },
        ToolDef(
            name = "launch_app",
            description = "Launch an app by its package name. Use search_apps first to find the package name.",
            parameters = listOf(Param(name = "package_name", type = "string", description = "The package name of the app")),
            required = listOf("package_name")
        ) { args -> launchApp(args.getString("package_name")) },
        ToolDef(
            name = "open_settings",
            description = "Open a specific system settings page. Supported: wifi, bluetooth, display, sound, battery, storage, apps, location, security, accessibility, date, language, developer, nfc, notifications.",
            parameters = listOf(Param(name = "settings_type", type = "string", description = "The type of settings to open")),
            required = listOf("settings_type")
        ) { args -> openSettings(args.getString("settings_type")) },
        ToolDef(
            name = "web_search",
            description = "Search the web for information using AI-powered search. Returns a detailed answer with source citations. Use for factual questions, current events, news, definitions, how-to questions.",
            parameters = listOf(Param(name = "query", type = "string", description = "The search query")),
            required = listOf("query")
        ) { args -> webSearch(args.getString("query")) }
    )
}
