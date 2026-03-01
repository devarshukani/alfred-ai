package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.tools.NotificationAction

class NotificationSkill(notificationAction: NotificationAction) : Skill {
    override val id = "notification"
    override val name = "Notifications"
    override val description = """Read and manage device notifications. Get recent notifications, filter by app, clear notification history.
Use when user asks about notifications, messages, what's new, or wants to check alerts."""
    override val tools = notificationAction.toolDefs()
}
