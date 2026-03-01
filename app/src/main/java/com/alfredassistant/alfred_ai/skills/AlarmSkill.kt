package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.tools.AlarmAction

class AlarmSkill(alarmAction: AlarmAction) : Skill {
    override val id = "alarm"
    override val name = "Alarms, Timers & Stopwatch"
    override val description = """Set alarms, timers, and stopwatch. Create one-time or recurring alarms, countdown timers, and start stopwatch.
Dismiss or snooze ringing alarms. Show existing alarms and timers.
Use when user mentions alarm, timer, stopwatch, wake up, remind me at a time, countdown."""
    override val tools = alarmAction.toolDefs()
}
