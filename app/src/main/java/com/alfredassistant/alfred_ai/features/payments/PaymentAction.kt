package com.alfredassistant.alfred_ai.features.payments

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Launch payment and financial apps via intents.
 */
class PaymentAction(private val context: Context) {

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

    /**
     * Launch a payment app by name.
     */
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

        // Try as a generic app search
        val intent = context.packageManager.getLaunchIntentForPackage(appName)
        if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            return "App launched."
        }

        return "Could not find payment app: $appName. Available: ${paymentApps.keys.joinToString(", ")}."
    }

    /**
     * Open UPI payment link (for direct payments).
     */
    fun openUpiPayment(upiId: String, name: String?, amount: String?): String {
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

    /**
     * List available payment apps on the device.
     */
    fun listAvailablePaymentApps(): String {
        val available = paymentApps.filter { (_, pkg) ->
            context.packageManager.getLaunchIntentForPackage(pkg) != null
        }.keys.toList()

        return if (available.isEmpty()) "No known payment apps found on this device."
        else "Available payment apps: ${available.joinToString(", ")}."
    }
}
