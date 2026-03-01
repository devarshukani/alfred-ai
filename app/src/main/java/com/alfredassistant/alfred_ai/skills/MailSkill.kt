package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.tools.MailAction

class MailSkill(mailAction: MailAction) : Skill {
    override val id = "mail"
    override val name = "Email"
    override val description = """Email composition and management. Compose emails, open mail app, share content via email.
Use when user wants to send email, check inbox, compose a message, or share something via email."""
    override val tools = mailAction.toolDefs()
}
