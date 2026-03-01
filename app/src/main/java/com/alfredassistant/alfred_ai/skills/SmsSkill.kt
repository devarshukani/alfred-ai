package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.tools.PhoneAction
import com.alfredassistant.alfred_ai.tools.SmsAction

class SmsSkill(smsAction: SmsAction, phoneAction: PhoneAction) : Skill {
    override val id = "sms"
    override val name = "SMS & Messaging"
    override val description = """Send text messages and open messaging apps. Send SMS directly or open the messaging app with a pre-filled message.
Use when user wants to text, message, or SMS someone. Search contacts first to find the number."""
    // search_contacts comes from PhoneAction, SMS tools from SmsAction
    override val tools = phoneAction.toolDefs().filter { it.name == "search_contacts" } + smsAction.toolDefs()
}
