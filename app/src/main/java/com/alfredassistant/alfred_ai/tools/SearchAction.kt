package com.alfredassistant.alfred_ai.tools

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.ContactsContract
import android.provider.MediaStore
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    // ──────────────────────────────────────────────
    // Contact search with fuzzy matching for voice
    // ──────────────────────────────────────────────

    fun searchContacts(query: String): List<ContactResult> {
        val likeResults = searchContactsLike(query)
        if (likeResults.isNotEmpty()) return likeResults

        val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
        if (queryWords.isNotEmpty()) {
            val wordResults = mutableMapOf<String, ContactResult>()
            for (word in queryWords) {
                for (c in searchContactsLike(word)) {
                    wordResults.putIfAbsent(c.name, c)
                }
            }
            if (wordResults.isNotEmpty()) {
                val scored = wordResults.values.map { it to fuzzyScore(query, it.name) }
                    .filter { it.second > 0.3f }
                    .sortedByDescending { it.second }
                    .map { it.first }
                if (scored.isNotEmpty()) return scored.take(5)
            }
        }

        val allContacts = loadAllContacts()
        return allContacts
            .map { it to fuzzyScore(query, it.name) }
            .filter { it.second > 0.4f }
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
    }

    private fun searchContactsLike(query: String): List<ContactResult> {
        val results = mutableListOf<ContactResult>()
        val cursor: Cursor? = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?",
            arrayOf("%$query%"),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        )
        cursor?.use {
            while (it.moveToNext()) {
                val contactId = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)) ?: continue
                val hasPhone = it.getInt(it.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER))
                if (hasPhone > 0) {
                    val numbers = getPhoneNumbers(contactId)
                    if (numbers.isNotEmpty()) results.add(ContactResult(name, numbers))
                }
            }
        }
        return results
    }

    private fun loadAllContacts(): List<ContactResult> {
        val results = mutableListOf<ContactResult>()
        val cursor: Cursor? = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, null,
            "${ContactsContract.Contacts.HAS_PHONE_NUMBER} > 0", null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        )
        cursor?.use {
            while (it.moveToNext()) {
                val contactId = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)) ?: continue
                val numbers = getPhoneNumbers(contactId)
                if (numbers.isNotEmpty()) results.add(ContactResult(name, numbers))
            }
        }
        return results
    }

    private fun getPhoneNumbers(contactId: String): List<Pair<String, String>> {
        val numbers = mutableListOf<Pair<String, String>>()
        val phoneCursor: Cursor? = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId), null
        )
        phoneCursor?.use {
            while (it.moveToNext()) {
                val number = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: continue
                val typeInt = it.getInt(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE))
                val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, typeInt, "Other").toString()
                numbers.add(Pair(number, label))
            }
        }
        return numbers
    }

    private fun fuzzyScore(query: String, contactName: String): Float {
        val qWords = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val nWords = contactName.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (qWords.isEmpty() || nWords.isEmpty()) return 0f
        var totalScore = 0f
        for (qw in qWords) {
            var best = 0f
            for (nw in nWords) { val s = wordSimilarity(qw, nw); if (s > best) best = s }
            totalScore += best
        }
        return totalScore / qWords.size
    }

    private fun wordSimilarity(a: String, b: String): Float {
        val la = a.lowercase(); val lb = b.lowercase()
        if (la == lb) return 1.0f
        if (lb.startsWith(la) || la.startsWith(lb)) {
            val shorter = minOf(la.length, lb.length); val longer = maxOf(la.length, lb.length)
            return 0.7f + 0.3f * (shorter.toFloat() / longer)
        }
        val ca = consonantSkeleton(la); val cb = consonantSkeleton(lb)
        val phoneticScore = if (ca.isNotEmpty() && cb.isNotEmpty()) {
            if (cb.startsWith(ca) || ca.startsWith(cb)) 0.6f + 0.2f * (minOf(ca.length, cb.length).toFloat() / maxOf(ca.length, cb.length))
            else { val d = editDistance(ca, cb); val m = maxOf(ca.length, cb.length); if (m == 0) 0f else (1f - d.toFloat() / m) * 0.6f }
        } else 0f
        val editDist = editDistance(la, lb); val maxLen = maxOf(la.length, lb.length)
        val editScore = if (maxLen == 0) 0f else (1f - editDist.toFloat() / maxLen)
        return maxOf(phoneticScore, editScore * 0.8f)
    }

    private fun consonantSkeleton(word: String): String {
        val vowels = setOf('a', 'e', 'i', 'o', 'u')
        val normalized = word.lowercase()
            .replace("ph", "f").replace("th", "t").replace("ck", "k")
            .replace("gh", "g").replace("wh", "w")
            .replace('v', 'w').replace('z', 's').replace('c', 'k')
        val sb = StringBuilder(); var last = ' '
        for (ch in normalized) { if (ch !in vowels && ch.isLetter() && ch != last) { sb.append(ch); last = ch } }
        return sb.toString()
    }

    private fun editDistance(a: String, b: String): Int {
        val m = a.length; val n = b.length
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                curr[j] = if (a[i - 1] == b[j - 1]) prev[j - 1]
                else 1 + minOf(prev[j], curr[j - 1], prev[j - 1])
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[n]
    }

    // ──────────────────────────────────────────────
    // File search via MediaStore
    // ──────────────────────────────────────────────

    fun searchFiles(query: String, fileType: String? = null): String {
        val results = mutableListOf<JSONObject>()
        val q = "%${query.lowercase()}%"
        val collections = mutableListOf<Pair<android.net.Uri, Array<String>>>()

        when (fileType?.lowercase()) {
            "image", "images", "photo", "photos" ->
                collections.add(MediaStore.Images.Media.EXTERNAL_CONTENT_URI to fileColumns())
            "video", "videos" ->
                collections.add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI to fileColumns())
            "audio", "music", "song", "songs" ->
                collections.add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to fileColumns())
            "document", "documents", "file", "files", "pdf", "doc" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    collections.add(MediaStore.Downloads.EXTERNAL_CONTENT_URI to fileColumns())
                collections.add(MediaStore.Files.getContentUri("external") to fileColumns())
            }
            else -> {
                collections.add(MediaStore.Images.Media.EXTERNAL_CONTENT_URI to fileColumns())
                collections.add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI to fileColumns())
                collections.add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to fileColumns())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    collections.add(MediaStore.Downloads.EXTERNAL_CONTENT_URI to fileColumns())
            }
        }

        for ((uri, columns) in collections) {
            if (results.size >= 20) break
            try {
                val cursor: Cursor? = context.contentResolver.query(
                    uri, columns,
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf(q),
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )
                cursor?.use { while (it.moveToNext() && results.size < 20) results.add(cursorToJson(it)) }
            } catch (e: Exception) { Log.w(TAG, "Error searching $uri: ${e.message}") }
        }

        return if (results.isEmpty()) "No files found matching \"$query\"."
        else JSONObject().apply { put("count", results.size); put("files", JSONArray(results)) }.toString()
    }

    fun getRecentFiles(fileType: String? = null, limit: Int = 15): String {
        val results = mutableListOf<JSONObject>()
        val uri = when (fileType?.lowercase()) {
            "image", "images", "photo", "photos" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            "video", "videos" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            "audio", "music" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            "document", "download", "downloads" ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.EXTERNAL_CONTENT_URI
                else MediaStore.Files.getContentUri("external")
            else -> MediaStore.Files.getContentUri("external")
        }
        try {
            val cursor: Cursor? = context.contentResolver.query(uri, fileColumns(), null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")
            cursor?.use { while (it.moveToNext() && results.size < limit) results.add(cursorToJson(it)) }
        } catch (e: Exception) { Log.w(TAG, "Error getting recent files: ${e.message}") }

        return if (results.isEmpty()) "No recent files found."
        else JSONObject().apply { put("count", results.size); put("files", JSONArray(results)) }.toString()
    }

    // ──────────────────────────────────────────────
    // Device info
    // ──────────────────────────────────────────────

    fun getDeviceInfo(): String {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val allApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        else pm.getInstalledApplications(0)
        val userApps = allApps.count { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.blockSizeLong * stat.blockCountLong
        val freeBytes = stat.blockSizeLong * stat.availableBlocksLong
        return JSONObject().apply {
            put("device", Build.MODEL); put("manufacturer", Build.MANUFACTURER)
            put("android_version", Build.VERSION.RELEASE); put("sdk_level", Build.VERSION.SDK_INT)
            put("total_apps", allApps.size); put("user_installed_apps", userApps)
            put("system_apps", allApps.size - userApps)
            put("storage_total_gb", String.format("%.1f", totalBytes / 1_073_741_824.0))
            put("storage_used_gb", String.format("%.1f", (totalBytes - freeBytes) / 1_073_741_824.0))
            put("storage_free_gb", String.format("%.1f", freeBytes / 1_073_741_824.0))
        }.toString()
    }

    // ──────────────────────────────────────────────
    // Web search via Mistral agent
    // ──────────────────────────────────────────────

    suspend fun webSearch(query: String): String = withContext(Dispatchers.IO) {
        try {
            val agentId = BuildConfig.MISTRAL_AGENT_ID
            if (agentId.isBlank()) return@withContext "Web search agent not configured."

            val body = JSONObject().apply {
                put("agent_id", agentId); put("inputs", query)
                put("stream", false); put("store", false)
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
                    "text" -> { val t = chunk.optString("text", ""); if (t.isNotBlank()) textParts.add(t) }
                    "tool_reference" -> {
                        val title = chunk.optString("title", ""); val url = chunk.optString("url", "")
                        if (title.isNotBlank() || url.isNotBlank()) sources.add("$title: $url")
                    }
                }
            }
        }
        return buildString {
            append(textParts.joinToString("").trim())
            if (sources.isNotEmpty()) { append("\n\nSources:"); sources.forEach { append("\n- $it") } }
        }
    }

    // ──────────────────────────────────────────────
    // MediaStore helpers
    // ──────────────────────────────────────────────

    private fun fileColumns() = arrayOf(
        MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.RELATIVE_PATH
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    private fun cursorToJson(cursor: Cursor): JSONObject {
        val name = cursor.getStringOrNull(MediaStore.MediaColumns.DISPLAY_NAME) ?: "unknown"
        val size = cursor.getLongOrNull(MediaStore.MediaColumns.SIZE) ?: 0L
        val modified = cursor.getLongOrNull(MediaStore.MediaColumns.DATE_MODIFIED) ?: 0L
        val path = cursor.getStringOrNull(MediaStore.MediaColumns.RELATIVE_PATH) ?: ""
        return JSONObject().apply {
            put("name", name); put("path", path); put("size_kb", size / 1024)
            if (modified > 0) put("modified", dateFormat.format(Date(modified * 1000)))
            put("type", name.substringAfterLast('.', "file"))
        }
    }

    private fun Cursor.getStringOrNull(column: String): String? {
        val idx = getColumnIndex(column); return if (idx >= 0) getString(idx) else null
    }
    private fun Cursor.getLongOrNull(column: String): Long? {
        val idx = getColumnIndex(column); return if (idx >= 0) getLong(idx) else null
    }

    // ──────────────────────────────────────────────
    // Tool definitions
    // ──────────────────────────────────────────────

    /** Contact search tool def — also used by other skills (Phone, SMS). */
    fun contactSearchToolDef(): ToolDef = ToolDef(
        name = "search_contacts",
        description = "Search contacts by name with fuzzy matching. Handles voice transcription errors. Returns names and phone numbers. If only one contact is found, use it directly without asking for confirmation.",
        parameters = listOf(Param(name = "query", type = "string", description = "The name or partial name to search for in contacts")),
        required = listOf("query")
    ) { args ->
        val q = args.getString("query")
        val contacts = searchContacts(q)
        if (contacts.isEmpty()) "No contacts found matching \"$q\"."
        else if (contacts.size == 1) "Found: ${contacts.toJsonString()}"
        else contacts.toJsonString()
    }

    fun toolDefs(): List<ToolDef> = listOf(
        contactSearchToolDef(),

        ToolDef(
            name = "search_files",
            description = "Search files on the device by name. Searches images, videos, audio, documents, and downloads. Can filter by file type.",
            parameters = listOf(
                Param(name = "query", type = "string", description = "File name or keyword to search for"),
                Param(name = "file_type", type = "string", description = "Optional filter: image, video, audio, document. Omit to search all types.")
            ),
            required = listOf("query")
        ) { args -> searchFiles(args.getString("query"), args.optString("file_type", null)) },

        ToolDef(
            name = "get_recent_files",
            description = "Get recently modified files on the device. Can filter by type. Use when user asks about recent downloads, recent photos, etc.",
            parameters = listOf(
                Param(name = "file_type", type = "string", description = "Optional filter: image, video, audio, document, download. Omit for all types."),
                Param(name = "limit", type = "string", description = "Max number of results (default 15)")
            ),
            required = emptyList()
        ) { args ->
            val type = args.optString("file_type", null)
            val limit = args.optString("limit", "15").toIntOrNull() ?: 15
            getRecentFiles(type, limit)
        },

        ToolDef(
            name = "get_device_info",
            description = "Get device information: model, Android version, storage usage, number of installed apps.",
            parameters = emptyList(),
            required = emptyList()
        ) { _ -> getDeviceInfo() },

        ToolDef(
            name = "web_search",
            description = "Search the web for information using AI-powered search. Returns a detailed answer with source citations. Use for factual questions, current events, news, definitions, how-to questions.",
            parameters = listOf(Param(name = "query", type = "string", description = "The search query")),
            required = listOf("query")
        ) { args -> webSearch(args.getString("query")) }
    )
}
