package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.tools.SearchAction

class SearchSkill(searchAction: SearchAction) : Skill {
    override val id = "search"
    override val name = "Search, Apps & Web"
    override val description = """Device search, app management, AI-powered web search, and settings. Search installed apps, launch apps, open system settings.
Search the web for information with AI-powered answers and citations. Use when user wants to find an app, open settings, or search the web for any information."""
    override val tools = searchAction.toolDefs()
}
