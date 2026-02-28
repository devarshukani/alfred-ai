package com.alfredassistant.alfred_ai.assistant

import android.content.Context
import com.alfredassistant.alfred_ai.features.phone.PhoneAction
import com.alfredassistant.alfred_ai.features.phone.toJsonString

/**
 * Alfred's brain — orchestrates Mistral API calls and executes tool actions.
 */
class AlfredBrain(context: Context) {

    private val mistral = MistralClient()
    private val phoneAction = PhoneAction(context)

    suspend fun processInput(userSpeech: String): String {
        var result = mistral.chat(userSpeech)

        // Tool call loop — keep executing until we get a text response
        var maxIterations = 5
        while (result.toolCalls.isNotEmpty() && maxIterations > 0) {
            maxIterations--

            val toolResults = result.toolCalls.map { call ->
                val output = executeToolCall(call)
                Pair(call.id, output)
            }

            result = mistral.sendToolResults(toolResults)
        }

        return result.content
            ?: "I've completed the action, sir."
    }

    private fun executeToolCall(call: ToolCall): String {
        return try {
            when (call.functionName) {
                "search_contacts" -> {
                    val query = call.arguments.getString("query")
                    val contacts = phoneAction.searchContacts(query)
                    if (contacts.isEmpty()) {
                        "No contacts found matching \"$query\"."
                    } else {
                        contacts.toJsonString()
                    }
                }

                "make_call" -> {
                    val number = call.arguments.getString("phone_number")
                    phoneAction.makeCall(number)
                    "Call initiated to $number."
                }

                "dial_number" -> {
                    val number = call.arguments.getString("phone_number")
                    phoneAction.dialNumber(number)
                    "Dialer opened with $number."
                }

                else -> "Unknown function: ${call.functionName}"
            }
        } catch (e: Exception) {
            "Error executing ${call.functionName}: ${e.message}"
        }
    }

    fun resetConversation() {
        mistral.clearHistory()
    }
}
