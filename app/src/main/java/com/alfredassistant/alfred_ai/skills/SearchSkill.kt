package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.tools.SearchAction

class SearchSkill(searchAction: SearchAction) : Skill {
    override val id = "search"
    override val name = "Search & Files"
    override val description = """Comprehensive device and web search. Search contacts by name, search files (photos, videos, audio, documents, downloads), find recent files, get device info (model, storage, app count), and AI-powered web search with citations. Use when user wants to find anything — contacts, files, device info — or search the web."""
    override val tools = searchAction.toolDefs()
}
