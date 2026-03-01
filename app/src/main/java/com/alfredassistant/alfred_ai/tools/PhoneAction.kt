package com.alfredassistant.alfred_ai.tools

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import com.alfredassistant.alfred_ai.skills.Param
import com.alfredassistant.alfred_ai.skills.ToolDef
import org.json.JSONArray
import org.json.JSONObject

data class ContactResult(
    val name: String,
    val numbers: List<Pair<String, String>> // (number, label)
)

class PhoneAction(private val context: Context) {

    /**
     * Search contacts by name with fuzzy matching for voice input.
     * Handles speech-to-text variations like "sukh bava" → "Sukhmeet Bawa".
     *
     * Strategy:
     * 1. Try exact LIKE match first
     * 2. If no results, load all contacts and fuzzy-match using word-level similarity
     * 3. Rank results by match score, return best matches
     */
    fun searchContacts(query: String): List<ContactResult> {
        // Step 1: Try standard LIKE match (fast path)
        val likeResults = searchContactsLike(query)
        if (likeResults.isNotEmpty()) return likeResults

        // Step 2: Try matching each query word separately with LIKE
        val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
        if (queryWords.isNotEmpty()) {
            val wordResults = mutableMapOf<String, ContactResult>() // keyed by contactId to dedup
            for (word in queryWords) {
                val partial = searchContactsLike(word)
                for (c in partial) {
                    wordResults.putIfAbsent(c.name, c)
                }
            }
            if (wordResults.isNotEmpty()) {
                // Score and rank by how well the full query matches
                val scored = wordResults.values.map { it to fuzzyScore(query, it.name) }
                    .filter { it.second > 0.3f }
                    .sortedByDescending { it.second }
                    .map { it.first }
                if (scored.isNotEmpty()) return scored.take(5)
            }
        }

        // Step 3: Full fuzzy search — load all contacts and score them
        val allContacts = loadAllContacts()
        val scored = allContacts
            .map { it to fuzzyScore(query, it.name) }
            .filter { it.second > 0.4f }
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }

        return scored
    }

    /**
     * Standard LIKE search against the contacts database.
     */
    private fun searchContactsLike(query: String): List<ContactResult> {
        val results = mutableListOf<ContactResult>()
        val uri = ContactsContract.Contacts.CONTENT_URI
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        val selectionArgs = arrayOf("%$query%")
        val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"

        val cursor: Cursor? = context.contentResolver.query(
            uri, null, selection, selectionArgs, sortOrder
        )

        cursor?.use {
            while (it.moveToNext()) {
                val contactId = it.getString(
                    it.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                )
                val name = it.getString(
                    it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                ) ?: continue
                val hasPhone = it.getInt(
                    it.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                )

                if (hasPhone > 0) {
                    val numbers = getPhoneNumbers(contactId)
                    if (numbers.isNotEmpty()) {
                        results.add(ContactResult(name, numbers))
                    }
                }
            }
        }
        return results
    }

    /**
     * Load all contacts with phone numbers (for fuzzy fallback).
     */
    private fun loadAllContacts(): List<ContactResult> {
        val results = mutableListOf<ContactResult>()
        val uri = ContactsContract.Contacts.CONTENT_URI
        val selection = "${ContactsContract.Contacts.HAS_PHONE_NUMBER} > 0"

        val cursor: Cursor? = context.contentResolver.query(
            uri, null, selection, null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val contactId = it.getString(
                    it.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                )
                val name = it.getString(
                    it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                ) ?: continue

                val numbers = getPhoneNumbers(contactId)
                if (numbers.isNotEmpty()) {
                    results.add(ContactResult(name, numbers))
                }
            }
        }
        return results
    }

    /**
     * Fuzzy match score between a voice query and a contact name.
     * Returns 0.0 (no match) to 1.0 (perfect match).
     *
     * Uses word-level matching: each query word is matched against each name word
     * using character-level similarity (handles "bava"→"bawa", "sukh"→"sukhmeet").
     */
    private fun fuzzyScore(query: String, contactName: String): Float {
        val qWords = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val nWords = contactName.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

        if (qWords.isEmpty() || nWords.isEmpty()) return 0f

        // For each query word, find the best matching name word
        var totalScore = 0f
        for (qw in qWords) {
            var bestWordScore = 0f
            for (nw in nWords) {
                val score = wordSimilarity(qw, nw)
                if (score > bestWordScore) bestWordScore = score
            }
            totalScore += bestWordScore
        }

        // Normalize by number of query words
        return totalScore / qWords.size
    }

    /**
     * Similarity between two words. Combines:
     * - Prefix match bonus (voice often gets the start right)
     * - Phonetic similarity (consonant skeleton)
     * - Edit distance ratio
     */
    private fun wordSimilarity(a: String, b: String): Float {
        val la = a.lowercase()
        val lb = b.lowercase()

        // Exact match
        if (la == lb) return 1.0f

        // Prefix match: "sukh" matches "sukhmeet"
        if (lb.startsWith(la) || la.startsWith(lb)) {
            val shorter = minOf(la.length, lb.length)
            val longer = maxOf(la.length, lb.length)
            return 0.7f + 0.3f * (shorter.toFloat() / longer)
        }

        // Phonetic: compare consonant skeletons
        // "bava" → "bv", "bawa" → "bw" — close but not identical
        // "sukhmeet" → "skmt", "sukh" → "sk"
        val ca = consonantSkeleton(la)
        val cb = consonantSkeleton(lb)
        val phoneticScore = if (ca.isNotEmpty() && cb.isNotEmpty()) {
            if (cb.startsWith(ca) || ca.startsWith(cb)) {
                0.6f + 0.2f * (minOf(ca.length, cb.length).toFloat() / maxOf(ca.length, cb.length))
            } else {
                val editDist = editDistance(ca, cb)
                val maxLen = maxOf(ca.length, cb.length)
                if (maxLen == 0) 0f else (1f - editDist.toFloat() / maxLen) * 0.6f
            }
        } else 0f

        // Character edit distance on full words
        val editDist = editDistance(la, lb)
        val maxLen = maxOf(la.length, lb.length)
        val editScore = if (maxLen == 0) 0f else (1f - editDist.toFloat() / maxLen)

        // Weighted combination
        return maxOf(phoneticScore, editScore * 0.8f)
    }

    /**
     * Extract consonant skeleton for phonetic comparison.
     * Strips vowels and duplicate consecutive consonants.
     * Maps common voice-confusion pairs: v↔w, ph↔f, th↔t, etc.
     */
    private fun consonantSkeleton(word: String): String {
        val vowels = setOf('a', 'e', 'i', 'o', 'u')
        val normalized = word.lowercase()
            .replace("ph", "f")
            .replace("th", "t")
            .replace("ck", "k")
            .replace("gh", "g")
            .replace("wh", "w")
            .replace('v', 'w')  // v and w sound similar in many accents
            .replace('z', 's')  // z and s confusion
            .replace('c', 'k')  // c and k

        val sb = StringBuilder()
        var lastChar = ' '
        for (ch in normalized) {
            if (ch !in vowels && ch.isLetter() && ch != lastChar) {
                sb.append(ch)
                lastChar = ch
            }
        }
        return sb.toString()
    }

    /**
     * Standard Levenshtein edit distance.
     */
    private fun editDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[m][n]
    }

    private fun getPhoneNumbers(contactId: String): List<Pair<String, String>> {
        val numbers = mutableListOf<Pair<String, String>>()
        val phoneCursor: Cursor? = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )

        phoneCursor?.use {
            while (it.moveToNext()) {
                val number = it.getString(
                    it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                ) ?: continue
                val typeInt = it.getInt(
                    it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
                )
                val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                    context.resources, typeInt, "Other"
                ).toString()
                numbers.add(Pair(number, label))
            }
        }
        return numbers
    }

    /**
     * Initiate a phone call to the given number.
     */
    fun makeCall(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Open dialer with number pre-filled (doesn't auto-call).
     */
    fun dialNumber(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /** ToolDef definitions for this action — importable by any skill. */
    fun toolDefs(): List<ToolDef> = listOf(
        ToolDef(
            name = "search_contacts",
            description = "Search contacts by name with fuzzy matching. Handles voice transcription errors. If only one contact is found, use it directly without asking for confirmation.",
            parameters = listOf(Param(name = "query", type = "string", description = "The name or partial name to search for in contacts")),
            required = listOf("query")
        ) { args ->
            val q = args.getString("query")
            val contacts = searchContacts(q)
            if (contacts.isEmpty()) "No contacts found matching \"$q\"."
            else if (contacts.size == 1) "Found: ${contacts.toJsonString()}"
            else contacts.toJsonString()
        },

        ToolDef(
            name = "make_call",
            description = "Make a phone call to a specific phone number. Use this after confirming the number with the user.",
            parameters = listOf(Param(name = "phone_number", type = "string", description = "The phone number to call")),
            required = listOf("phone_number")
        ) { args -> makeCall(args.getString("phone_number")); "Call initiated." },

        ToolDef(
            name = "dial_number",
            description = "Open the phone dialer with a number pre-filled without auto-calling.",
            parameters = listOf(Param(name = "phone_number", type = "string", description = "The phone number to dial")),
            required = listOf("phone_number")
        ) { args -> dialNumber(args.getString("phone_number")); "Dialer opened." }
    )
}

/**
 * Convert search results to JSON string for Mistral function call response.
 */
fun List<ContactResult>.toJsonString(): String {
    val arr = JSONArray()
    forEach { contact ->
        val obj = JSONObject()
        obj.put("name", contact.name)
        val nums = JSONArray()
        contact.numbers.forEach { (number, label) ->
            nums.put(JSONObject().apply {
                put("number", number)
                put("label", label)
            })
        }
        obj.put("phone_numbers", nums)
        arr.put(obj)
    }
    return arr.toString()
}
