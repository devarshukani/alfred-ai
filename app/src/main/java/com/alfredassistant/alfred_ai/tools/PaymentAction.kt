package com.alfredassistant.alfred_ai.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.alfredassistant.alfred_ai.skills.Param
import com.alfredassistant.alfred_ai.skills.ToolDef
import com.alfredassistant.alfred_ai.ui.RichBlock
import com.alfredassistant.alfred_ai.ui.RichCard
import kotlinx.coroutines.CompletableDeferred

class PaymentAction(private val context: Context) {

    /** Callback to show a card on screen — wired by the skill. */
    var onDisplayCard: ((RichCard) -> Unit)? = null

    /** Deferred that resolves when user taps a card button. */
    var pendingAction: CompletableDeferred<String>? = null

    val isAwaitingAction: Boolean
        get() = pendingAction?.isActive == true

    fun submitAction(actionId: String) {
        pendingAction?.complete(actionId)
    }

    private val paymentApps = mapOf(
        "gpay" to "com.google.android.apps.nbu.paisa.user",
        "google pay" to "com.google.android.apps.nbu.paisa.user",
        "phonepe" to "com.phonepe.app",
        "paytm" to "net.one97.paytm",
        "paypal" to "com.paypal.android.p2pmobile",
        "samsung pay" to "com.samsung.android.spay",
        "amazon pay" to "in.amazon.mShop.android.shopping",
        "cred" to "com.dreamplug.androidapp",
        "bhim" to "in.org.npci.upiapp",
        "whatsapp pay" to "com.whatsapp"
    )

    fun launchPaymentApp(appName: String): String {
        val packageName = paymentApps[appName.lowercase()]
            ?: paymentApps.entries.firstOrNull { appName.lowercase().contains(it.key) }?.value

        if (packageName != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return "Opening ${appName}."
            }
            return "$appName is not installed on this device."
        }
        return "Could not find payment app: $appName. Available: ${paymentApps.keys.joinToString(", ")}."
    }

    /**
     * Show a confirmation card with an editable UPI ID, wait for user to confirm,
     * then initiate the payment with the (possibly edited) UPI ID.
     */
    suspend fun openUpiPaymentWithConfirmation(upiId: String, name: String?, amount: String?): String {
        val displayCallback = onDisplayCard ?: return openUpiPaymentDirect(upiId, name, amount)

        // Build confirmation card with editable UPI ID
        val blocks = mutableListOf<RichBlock>(
            RichBlock.Title("Confirm UPI Payment"),
            RichBlock.Spacer(8)
        )
        if (!name.isNullOrBlank()) blocks.add(RichBlock.InfoRow("To", name))
        if (!amount.isNullOrBlank()) blocks.add(RichBlock.InfoRow("Amount", "₹$amount"))
        blocks.add(RichBlock.Spacer(8))
        blocks.add(RichBlock.Caption("Verify or edit the UPI ID below:"))
        blocks.add(RichBlock.TextField(placeholder = "UPI ID", key = "upi_id", defaultValue = upiId))
        blocks.add(RichBlock.Spacer(12))
        blocks.add(RichBlock.ButtonPrimary(label = "Pay", actionId = "confirm_upi"))
        blocks.add(RichBlock.ButtonCancel(label = "Cancel"))

        val card = RichCard(
            blocks = blocks,
            spokenSummary = "Please confirm the UPI ID before I send the payment."
        )

        val deferred = CompletableDeferred<String>()
        pendingAction = deferred
        displayCallback(card)
        val action = deferred.await()
        pendingAction = null

        if (action.startsWith("dismiss") || action.startsWith("cancel")) {
            return "Payment cancelled."
        }

        // Parse the edited UPI ID from the action string
        // Format: "confirm_upi?upi_id=edited_value"
        val finalUpiId = if (action.contains("?")) {
            val params = action.substringAfter("?")
            params.split("&").firstOrNull { it.startsWith("upi_id=") }
                ?.substringAfter("upi_id=")
                ?.takeIf { it.isNotBlank() }
                ?: upiId
        } else upiId

        return openUpiPaymentDirect(finalUpiId, name, amount)
    }

    private fun openUpiPaymentDirect(upiId: String, name: String?, amount: String?): String {
        val uriBuilder = StringBuilder("upi://pay?pa=$upiId")
        if (!name.isNullOrBlank()) uriBuilder.append("&pn=${Uri.encode(name)}")
        if (!amount.isNullOrBlank()) uriBuilder.append("&am=$amount")
        uriBuilder.append("&cu=INR")

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(uriBuilder.toString())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            "UPI payment initiated."
        } catch (e: Exception) {
            "No UPI app found to handle this payment."
        }
    }

    fun listAvailablePaymentApps(): String {
        val available = paymentApps.filter { (_, pkg) ->
            context.packageManager.getLaunchIntentForPackage(pkg) != null
        }.keys.toList()
        return if (available.isEmpty()) "No known payment apps found on this device."
        else "Available payment apps: ${available.joinToString(", ")}."
    }

    fun toolDefs(): List<ToolDef> = listOf(
        ToolDef(
            name = "launch_payment_app",
            description = "Launch a payment app. Supported: GPay, Google Pay, PhonePe, Paytm, PayPal, Samsung Pay, Amazon Pay, CRED, BHIM, WhatsApp Pay.",
            parameters = listOf(Param(name = "app_name", type = "string", description = "Name of the payment app")),
            required = listOf("app_name")
        ) { args -> launchPaymentApp(args.getString("app_name")) },
        ToolDef(
            name = "upi_payment",
            description = "Initiate a UPI payment. Shows a confirmation card with an editable UPI ID field so the user can verify/correct it before paying. Opens a UPI app to complete.",
            parameters = listOf(
                Param(name = "upi_id", type = "string", description = "Recipient's UPI ID. Use '91XXXXXXXXXX@upi' format."),
                Param(name = "name", type = "string", description = "Recipient name (optional)"),
                Param(name = "amount", type = "string", description = "Amount to pay (optional)")
            ),
            required = listOf("upi_id")
        ) { args -> openUpiPaymentWithConfirmation(args.getString("upi_id"), args.optString("name", null), args.optString("amount", null)) },
        ToolDef(name = "list_payment_apps", description = "List payment apps installed on the device.") { _ -> listAvailablePaymentApps() }
    )
}
