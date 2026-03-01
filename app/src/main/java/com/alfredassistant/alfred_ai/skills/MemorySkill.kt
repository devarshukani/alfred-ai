package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.tools.MemoryStore

class MemorySkill(memoryStore: MemoryStore) : Skill {
    override val id = "memory"
    override val name = "Memory & Knowledge Graph"
    override val alwaysInclude = true
    override val description = """Memory management and knowledge graph. Use this skill to remember EVERYTHING the user tells you.
Store personal info (name, age, location, job, family, pets, preferences, routines, schedules).
Store facts about people they mention, places they go, things they like or dislike.
Store context from conversations — what was discussed, decisions made, plans.
ALWAYS check memory before asking the user for info you might already know.
When the user shares ANY personal detail, immediately store it. Be proactive about remembering.
When the user asks "what do you know about me" or similar, search the knowledge graph.
The knowledge graph connects entities (people, places, things) with relationships."""
    override val tools = memoryStore.toolDefs()
}
