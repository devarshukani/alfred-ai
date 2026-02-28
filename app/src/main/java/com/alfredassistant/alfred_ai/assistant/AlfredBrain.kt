package com.alfredassistant.alfred_ai.assistant

/**
 * Simple local response generator for Alfred.
 * Replace this with an actual AI API (OpenAI, Gemini, etc.) for real intelligence.
 */
object AlfredBrain {

    fun generateResponse(input: String): String {
        val lower = input.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                "Hello! I'm Alfred, your AI assistant. How can I help you?"

            lower.contains("your name") || lower.contains("who are you") ->
                "I'm Alfred, your personal AI assistant. I'm here to help with whatever you need."

            lower.contains("time") ->
                "The current time is ${java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())}."

            lower.contains("date") || lower.contains("today") ->
                "Today is ${java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date())}."

            lower.contains("weather") ->
                "I don't have access to weather data yet, but that feature is coming soon."

            lower.contains("thank") ->
                "You're welcome! Anything else I can help with?"

            lower.contains("bye") || lower.contains("goodbye") ->
                "Goodbye! Have a great day."

            lower.contains("joke") ->
                "Why do programmers prefer dark mode? Because light attracts bugs."

            else ->
                "I heard you say: \"$input\". I'm still learning, but I'll get smarter over time."
        }
    }
}
