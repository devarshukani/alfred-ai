package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.ui.ConfirmationRequest
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject

/**
 * Confirmation Skill — ALWAYS included.
 * Presents clickable options to the user for confirmation before irreversible actions.
 */
class ConfirmationSkill : Skill {

    override val id = "confirmation"
    override val name = "User Confirmation"
    override val alwaysInclude = true

    override val description = """Present options and confirmations to the user before irreversible actions.
Show clickable buttons for the user to choose from. Always include a Cancel option."""

    var onConfirmationNeeded: ((ConfirmationRequest) -> Unit)? = null
    var pendingSelection: CompletableDeferred<String>? = null

    override val tools = listOf(
        ToolDef(
            name = "present_options",
            description = "Present clickable options to the user when a choice or confirmation is needed BEFORE executing an action. Max 4 options. Always include Cancel last.",
            parameters = listOf(
                Param(name = "prompt", type = "string", description = "The question or prompt to display above the options"),
                Param(name = "options", type = "array", description = "List of option labels (max 4). Short — max 25 chars each.", maxItems = 4,
                    items = Param(name = "option", type = "string", description = "Option label")),
                Param(name = "button_styles", type = "array", description = "Style for each button: 'primary', 'secondary', or 'cancel'. Must match options length.",
                    items = Param(name = "style", type = "string", description = "Button style",
                        enum = listOf("primary", "secondary", "cancel")))
            ),
            required = listOf("prompt", "options", "button_styles")
        ) { args -> executeConfirmation(args) }
    )

    private suspend fun executeConfirmation(arguments: JSONObject): String {
        val prompt = arguments.getString("prompt")
        val optionsArr = arguments.getJSONArray("options")
        val stylesArr = arguments.optJSONArray("button_styles")
        val options = mutableListOf<String>()
        val styles = mutableListOf<String>()
        for (i in 0 until minOf(optionsArr.length(), 4)) {
            options.add(optionsArr.getString(i))
            styles.add(stylesArr?.optString(i, "primary") ?: "primary")
        }

        val spokenPrompt = abbreviateForSpeech(prompt)
        val deferred = CompletableDeferred<String>()
        pendingSelection = deferred
        onConfirmationNeeded?.invoke(ConfirmationRequest(prompt, options, styles, spokenPrompt))
        val selection = deferred.await()
        pendingSelection = null

        return if (selection.equals("Cancel", ignoreCase = true)) {
            "User cancelled the action."
        } else {
            "User selected: $selection"
        }
    }

    private fun abbreviateForSpeech(text: String): String {
        var result = text
        result = result
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("\\*(.+?)\\*"), "$1")
            .replace(Regex("`(.+?)`"), "$1")
            .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("^[\\-*]\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
        val phoneRegex = Regex("""[\+]?[\d\s\-\(\)]{7,}""")
        result = phoneRegex.replace(result) { match ->
            val digits = match.value.filter { it.isDigit() }
            if (digits.length >= 4) "ending in ${digits.takeLast(3)}" else match.value
        }
        return result.trim()
    }
}
