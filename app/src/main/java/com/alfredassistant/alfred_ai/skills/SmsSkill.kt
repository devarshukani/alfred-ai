package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.tools.SearchAction
import com.alfredassistant.alfred_ai.tools.SmsAction

class SmsSkill(smsAction: SmsAction, searchAction: SearchAction) : Skill {
    override val id = "sms"
    override val name = "SMS & Messaging"
    override val description = """Send text messages and open messaging apps. Send SMS directly or open the messaging app with a pre-filled message.
Use when user wants to text, message, or SMS someone. Search contacts first to find the number."""
    override val tools = listOf(searchAction.contactSearchToolDef()) + smsAction.toolDefs()
}
