package com.alfredassistant.alfred_ai.assistant

/**
 * Alfred's brain — orchestrates Mistral API calls.
 * Will later handle function calling for device actions.
 */
class AlfredBrain {

    private val mistral = MistralClient()

    suspend fun processInput(userSpeech: String): String {
        return mistral.chat(userSpeech)
    }

    fun resetConversation() {
        mistral.clearHistory()
    }
}
