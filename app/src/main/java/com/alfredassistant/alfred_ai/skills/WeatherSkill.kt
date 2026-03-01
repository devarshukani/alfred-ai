package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.tools.WeatherAction

class WeatherSkill(weatherAction: WeatherAction) : Skill {
    override val id = "weather"
    override val name = "Weather"
    override val description = """Weather information and forecasts. Get current weather and 3-day forecast by coordinates.
Returns temperature, conditions, humidity, wind speed, and daily forecast.
Use device_coordinates or get_coordinates first to get lat/lon, then call get_weather.
Use when user asks about weather, temperature, forecast, rain, or climate conditions."""
    override val tools = weatherAction.toolDefs()
}
