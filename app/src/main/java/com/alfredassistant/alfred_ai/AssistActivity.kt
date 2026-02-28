package com.alfredassistant.alfred_ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Handles voice command / assist intents from the system.
 * When Alfred is set as the default assistant and the user triggers it
 * (long-press home, etc.), this redirects to the main activity.
 */
class AssistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("auto_listen", true)
        }
        startActivity(mainIntent)
        finish()
    }
}
