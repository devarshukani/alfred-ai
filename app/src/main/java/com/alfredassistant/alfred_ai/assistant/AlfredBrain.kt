package com.alfredassistant.alfred_ai.assistant

/**
 * Alfred's brain — responds like Batman's loyal butler.
 * Replace with a real AI API for actual intelligence.
 */
object AlfredBrain {

    fun generateResponse(input: String): String {
        val lower = input.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                "Good day, sir. Alfred at your service. How may I assist you?"

            lower.contains("your name") || lower.contains("who are you") ->
                "I am Alfred, sir. Your personal assistant, always at the ready."

            lower.contains("time") ->
                "The time is ${java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())}, sir."

            lower.contains("date") || lower.contains("today") ->
                "Today is ${java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date())}, sir."

            lower.contains("weather") ->
                "I'm afraid I don't have access to weather data at the moment, sir. That capability is forthcoming."

            lower.contains("thank") ->
                "It is my pleasure, sir. Will there be anything else?"

            lower.contains("bye") || lower.contains("goodbye") ->
                "Very well, sir. I shall be here when you need me."

            lower.contains("joke") ->
                "Why do programmers prefer dark mode? Because light attracts bugs, sir. My sincerest apologies for the humor."

            lower.contains("batman") ->
                "I'm sure I don't know what you're referring to, sir. I am merely a humble assistant."

            lower.contains("help") ->
                "Of course, sir. I can assist with the time, date, and general inquiries. Simply speak your command."

            else ->
                "I heard: \"$input\". I shall endeavor to be more helpful as my capabilities expand, sir."
        }
    }
}
