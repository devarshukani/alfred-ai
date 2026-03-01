package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.tools.LocationAction

class LocationSkill(locationAction: LocationAction) : Skill {
    override val id = "location"
    override val name = "Location"
    override val description = """Get the user's current device location or look up any place.
device_location returns city/country from GPS. device_coordinates returns lat/lon from GPS.
get_location returns city/country for a place name. get_coordinates returns lat/lon for a place name.
Use for 'where am I', before weather queries, or any location-dependent request."""
    override val tools = locationAction.toolDefs()
}
