package com.alfredassistant.alfred_ai.features.mail

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Mail actions using Android intents.
 * Compose emails via standard intents — works with Gmail, Outlook, etc.
 * Reading emails requires OAuth/Gmail API which is out of scope for intent-based actions,
 * so we provide open_mail to let the user check their inbox.
 */
class MailAction(private val context: Context) {

    /**
     * Compose and send an email.
     * @param to Recipient email address(es), comma-separated
     * @param subject Email subject
     * @param body Email body text
     * @param cc Optional CC recipients, comma-separated
     * @param bcc Optional BCC recipients, comma-separated
     */
    fun composeEmail(
        to: String,
        subject: String,
        body: String,
        cc: String? = null,
        bcc: String? = null
    ) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, to.split(",").map { it.trim() }.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            if (!cc.isNullOrBlank()) {
                putExtra(Intent.EXTRA_CC, cc.split(",").map { it.trim() }.toTypedArray())
            }
            if (!bcc.isNullOrBlank()) {
                putExtra(Intent.EXTRA_BCC, bcc.split(",").map { it.trim() }.toTypedArray())
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Open the default email app.
     */
    fun openMail() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_EMAIL)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: try opening Gmail directly
            val gmailIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
            if (gmailIntent != null) {
                gmailIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(gmailIntent)
            }
        }
    }

    /**
     * Share text via email (useful for forwarding content).
     */
    fun shareViaEmail(subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(Intent.createChooser(intent, "Send via").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
}
