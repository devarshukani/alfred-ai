package com.alfredassistant.alfred_ai.tools

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import com.alfredassistant.alfred_ai.skills.Param
import com.alfredassistant.alfred_ai.skills.ToolDef
import java.util.concurrent.TimeUnit

/**
 * Weather using Open-Meteo API — free, no API key needed.
 * Takes coordinates (from get_location) or a city name (geocoded).
 */
class WeatherAction {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Get weather by coordinates.
     */
    suspend fun getWeatherByCoords(lat: Double, lon: Double, label: String = "Location"): String =
        withContext(Dispatchers.IO) {
            try {
                fetchWeather(lat, lon, label)
            } catch (e: Exception) {
                "Weather lookup failed: ${e.message}"
            }
        }

    /**
     * Get weather by city name (geocodes first).
     */
    suspend fun getWeatherByCity(city: String): String = withContext(Dispatchers.IO) {
        try {
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(city)}&count=1"
            val geoResp = client.newCall(Request.Builder().url(geoUrl).build()).execute()
            val geoJson = JSONObject(geoResp.body?.string() ?: "")

            val results = geoJson.optJSONArray("results")
            if (results == null || results.length() == 0) {
                return@withContext "Could not find location: $city"
            }

            val place = results.getJSONObject(0)
            val lat = place.getDouble("latitude")
            val lon = place.getDouble("longitude")
            val name = place.getString("name")
            val country = place.optString("country", "")
            val label = if (country.isNotBlank()) "$name, $country" else name

            fetchWeather(lat, lon, label)
        } catch (e: Exception) {
            "Weather lookup failed: ${e.message}"
        }
    }

    private fun fetchWeather(lat: Double, lon: Double, label: String): String {
        val weatherUrl = "https://api.open-meteo.com/v1/forecast?" +
            "latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
            "&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max" +
            "&timezone=auto&forecast_days=3"

        val resp = client.newCall(Request.Builder().url(weatherUrl).build()).execute()
        val json = JSONObject(resp.body?.string() ?: "")

        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")

        val result = JSONObject().apply {
            put("location", label)
            put("current", JSONObject().apply {
                put("temperature_c", current.getDouble("temperature_2m"))
                put("feels_like_c", current.getDouble("apparent_temperature"))
                put("condition", weatherCodeToText(current.getInt("weather_code")))
                put("humidity_percent", current.getInt("relative_humidity_2m"))
                put("wind_speed_kmh", current.getDouble("wind_speed_10m"))
            })
            val forecastArr = JSONArray()
            val dates = daily.getJSONArray("time")
            val maxTemps = daily.getJSONArray("temperature_2m_max")
            val minTemps = daily.getJSONArray("temperature_2m_min")
            val codes = daily.getJSONArray("weather_code")
            val rainChance = daily.getJSONArray("precipitation_probability_max")
            for (i in 0 until dates.length()) {
                forecastArr.put(JSONObject().apply {
                    put("date", dates.getString(i))
                    put("high_c", maxTemps.getDouble(i))
                    put("low_c", minTemps.getDouble(i))
                    put("condition", weatherCodeToText(codes.getInt(i)))
                    put("rain_chance_percent", rainChance.optInt(i, 0))
                })
            }
            put("forecast", forecastArr)
        }
        return result.toString()
    }

    private fun weatherCodeToText(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75 -> "Snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Unknown"
    }

    fun toolDefs(): List<ToolDef> = listOf(
        ToolDef(
            name = "get_weather",
            description = "Get current weather and 3-day forecast by coordinates. Use device_coordinates or get_coordinates first to get lat/lon.",
            parameters = listOf(
                Param(name = "latitude", type = "number", description = "Latitude coordinate"),
                Param(name = "longitude", type = "number", description = "Longitude coordinate"),
                Param(name = "label", type = "string", description = "Display name for the location (e.g. 'Mumbai, India')")
            ),
            required = listOf("latitude", "longitude")
        ) { args ->
            getWeatherByCoords(
                args.getDouble("latitude"),
                args.getDouble("longitude"),
                args.optString("label", "Location")
            )
        }
    )
}
