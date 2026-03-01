package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.tools.SearchAction

class SearchSkill(searchAction: SearchAction) : Skill {
    override val id = "search"
    override val name = "Search, Apps & Web"
    override val description = """Device search, app management, web search, and settings. Search installed apps, launch apps, open system settings.
Search the web for information, open URLs in browser. Use when user wants to find an app, open settings, search the web, or open a website."""
    override val tools = searchAction.toolDefs()
}
