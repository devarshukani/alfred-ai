package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.tools.PhoneAction
import com.alfredassistant.alfred_ai.tools.SearchAction

class PhoneSkill(phoneAction: PhoneAction, searchAction: SearchAction) : Skill {
    override val id = "phone"
    override val name = "Phone & Contacts"
    override val description = """Phone calls and contact lookup. Search contacts by name with fuzzy matching for voice input.
Make phone calls, dial numbers. Handles voice transcription errors like 'sukh bava' finding 'Sukhmeet Bawa'.
Use when user wants to call someone, find a contact, or dial a number."""
    override val tools = listOf(searchAction.contactSearchToolDef()) + phoneAction.toolDefs()
}
