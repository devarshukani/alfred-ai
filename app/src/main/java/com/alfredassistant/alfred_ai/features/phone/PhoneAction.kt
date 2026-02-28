package com.alfredassistant.alfred_ai.features.phone

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import org.json.JSONArray
import org.json.JSONObject

data class ContactResult(
    val name: String,
    val numbers: List<Pair<String, String>> // (number, label)
)

class PhoneAction(private val context: Context) {

    /**
     * Search contacts by name. Returns matching contacts with all their phone numbers.
     */
    fun searchContacts(query: String): List<ContactResult> {
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
