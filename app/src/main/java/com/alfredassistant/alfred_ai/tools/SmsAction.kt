package com.alfredassistant.alfred_ai.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import com.alfredassistant.alfred_ai.skills.Param
import com.alfredassistant.alfred_ai.skills.ToolDef

/**
 * Send SMS messages and open messaging apps.
 */
class SmsAction(private val context: Context) {

    /**
     * Send an SMS directly using SmsManager.
     * Requires SEND_SMS permission.
     */
    fun sendSms(phoneNumber: String, message: String): String {
        return try {
            val smsManager = SmsManager.getDefault()
            // Split long messages into parts
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
            "Message sent to $phoneNumber."
        } catch (e: Exception) {
            "Failed to send message: ${e.message}"
        }
    }

    /**
     * Open the default messaging app with a pre-filled message.
     * Doesn't require SEND_SMS — uses intent.
     */
    fun openSmsApp(phoneNumber: String, message: String?): String {
        return try {
            val uri = if (message != null) {
                Uri.parse("smsto:$phoneNumber").buildUpon()
                    .appendQueryParameter("body", message)
                    .build()
            } else {
                Uri.parse("smsto:$phoneNumber")
            }
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Messaging app opened."
        } catch (e: Exception) {
            "Could not open messaging app: ${e.message}"
        }
    }

    fun toolDefs(): List<ToolDef> = listOf(
        ToolDef(
            name = "send_sms",
            description = "Send an SMS text message directly to a phone number. Requires confirmation via present_options first.",
            parameters = listOf(
                Param(name = "phone_number", type = "string", description = "The recipient's phone number"),
                Param(name = "message", type = "string", description = "The text message to send")
            ),
            required = listOf("phone_number", "message")
        ) { args -> sendSms(args.getString("phone_number"), args.getString("message")) },

        ToolDef(
            name = "open_sms_app",
            description = "Open the default messaging app with a phone number and optional pre-filled message.",
            parameters = listOf(
                Param(name = "phone_number", type = "string", description = "The recipient's phone number"),
                Param(name = "message", type = "string", description = "Optional pre-filled message text")
            ),
            required = listOf("phone_number")
        ) { args -> openSmsApp(args.getString("phone_number"), args.optString("message", null)) }
    )
}
