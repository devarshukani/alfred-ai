package com.alfredassistant.alfred_ai.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.alfredassistant.alfred_ai.skills.Param
import com.alfredassistant.alfred_ai.skills.ToolDef

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

    fun toolDefs(): List<ToolDef> = listOf(
        ToolDef(
            name = "compose_email",
            description = "Compose and open an email in the user's mail app. The user will review and send it manually.",
            parameters = listOf(
                Param(name = "to", type = "string", description = "Recipient email(s), comma-separated"),
                Param(name = "subject", type = "string", description = "Email subject line"),
                Param(name = "body", type = "string", description = "Email body text"),
                Param(name = "cc", type = "string", description = "Optional CC recipients"),
                Param(name = "bcc", type = "string", description = "Optional BCC recipients")
            ),
            required = listOf("to", "subject", "body")
        ) { args ->
            composeEmail(args.getString("to"), args.getString("subject"), args.getString("body"),
                args.optString("cc", null), args.optString("bcc", null))
            "Email composed. Please review and send."
        },
        ToolDef(name = "open_mail", description = "Open the user's default email app to check inbox.") { _ -> openMail(); "Mail opened." },
        ToolDef(
            name = "share_via_email",
            description = "Share content via email using the system share sheet.",
            parameters = listOf(
                Param(name = "subject", type = "string", description = "Email subject"),
                Param(name = "body", type = "string", description = "Content to share")
            ),
            required = listOf("subject", "body")
        ) { args -> shareViaEmail(args.getString("subject"), args.getString("body")); "Share sheet opened." }
    )
}
