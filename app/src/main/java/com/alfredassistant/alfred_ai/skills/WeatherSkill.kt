package com.alfredassistant.alfred_ai.skills

import com.alfredassistant.alfred_ai.tools.WeatherAction

class WeatherSkill(weatherAction: WeatherAction) : Skill {
    override val id = "weather"
    override val name = "Weather"
    override val description = """Weather information and forecasts. Get current weather and 3-day forecast for any city or current GPS location.
Returns temperature, conditions, humidity, wind speed, and daily forecast.
Use when user asks about weather, temperature, forecast, rain, or climate conditions."""
    override val tools = weatherAction.toolDefs()
}
