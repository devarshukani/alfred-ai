package com.alfredassistant.alfred_ai.features.sms

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager

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
}
