package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.tools.MapsAction

class MapsSkill(mapsAction: MapsAction) : Skill {
    override val id = "maps"
    override val name = "Maps & Navigation"
    override val description = """Maps, places, directions, navigation, and location search. Search for nearby restaurants, shops, gas stations, hotels, or any point of interest.
Get driving directions and distance between two locations. Open places in Google Maps or any maps app for navigation.
Use when user asks about places nearby, directions, distance, how to get somewhere, wants to find a business, open maps, navigate, or anything related to locations and maps."""
    override val tools = mapsAction.toolDefs()
}
