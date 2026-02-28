package com.alfredassistant.alfred_ai.features.weather

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Weather using Open-Meteo API — free, no API key needed.
 * Uses geocoding to resolve city names to coordinates.
 */
class WeatherAction {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getWeather(location: String): String = withContext(Dispatchers.IO) {
        try {
            // Step 1: Geocode the location
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(location)}&count=1"
            val geoReq = Request.Builder().url(geoUrl).build()
            val geoResp = client.newCall(geoReq).execute()
            val geoBody = geoResp.body?.string() ?: ""
            val geoJson = JSONObject(geoBody)

            val results = geoJson.optJSONArray("results")
            if (results == null || results.length() == 0) {
                return@withContext "Could not find location: $location"
            }

            val place = results.getJSONObject(0)
            val lat = place.getDouble("latitude")
            val lon = place.getDouble("longitude")
            val cityName = place.getString("name")
            val country = place.optString("country", "")

            // Step 2: Get weather
            val weatherUrl = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max" +
                "&timezone=auto&forecast_days=3"

            val weatherReq = Request.Builder().url(weatherUrl).build()
            val weatherResp = client.newCall(weatherReq).execute()
            val weatherBody = weatherResp.body?.string() ?: ""
            val weatherJson = JSONObject(weatherBody)

            val current = weatherJson.getJSONObject("current")
            val daily = weatherJson.getJSONObject("daily")

            val temp = current.getDouble("temperature_2m")
            val feelsLike = current.getDouble("apparent_temperature")
            val humidity = current.getInt("relative_humidity_2m")
            val windSpeed = current.getDouble("wind_speed_10m")
            val weatherCode = current.getInt("weather_code")
            val condition = weatherCodeToText(weatherCode)

            val result = JSONObject().apply {
                put("location", "$cityName, $country")
                put("current", JSONObject().apply {
                    put("temperature_c", temp)
                    put("feels_like_c", feelsLike)
                    put("condition", condition)
                    put("humidity_percent", humidity)
                    put("wind_speed_kmh", windSpeed)
                })

                val forecastArr = org.json.JSONArray()
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

            result.toString()
        } catch (e: Exception) {
            "Weather lookup failed: ${e.message}"
        }
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
}
