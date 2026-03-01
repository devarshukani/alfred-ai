package com.alfredassistant.alfred_ai.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.alfredassistant.alfred_ai.skills.Param
import com.alfredassistant.alfred_ai.skills.ToolDef
import org.json.JSONArray
import org.json.JSONObject

data class ContactResult(
    val name: String,
    val numbers: List<Pair<String, String>> // (number, label)
)

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

class PhoneAction(private val context: Context) {

    fun makeCall(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun dialNumber(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun toolDefs(): List<ToolDef> = listOf(
        ToolDef(
            name = "make_call",
            description = "Make a phone call to a specific phone number. Use search_contacts first to find the number, then confirm with the user before calling.",
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
