package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.tools.CalendarAction

class CalendarSkill(calendarAction: CalendarAction) : Skill {
    override val id = "calendar"
    override val name = "Calendar & Events"
    override val description = """Calendar management. Create events, check today's/tomorrow's/week's schedule, open calendar app.
Use when user mentions meetings, events, schedule, appointments, calendar, what's on today/tomorrow."""
    override val tools = calendarAction.toolDefs()
}
