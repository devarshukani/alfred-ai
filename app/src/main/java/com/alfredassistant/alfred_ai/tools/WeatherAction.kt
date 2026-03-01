package com.alfredassistant.alfred_ai.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.alfredassistant.alfred_ai.skills.Param
import com.alfredassistant.alfred_ai.skills.ToolDef
import java.util.concurrent.TimeUnit

/**
 * Weather using Open-Meteo API — free, no API key needed.
 * Supports city name lookup and GPS-based current location.
 */
class WeatherAction(private val context: Context) {

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

    /**
     * Get weather for the user's current GPS location.
     */
    suspend fun getWeatherHere(): String = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext "Location permission not granted. Please enable location access."
        }

        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location == null) {
                return@withContext "Could not determine current location. Please try specifying a city name."
            }

            val lat = location.latitude
            val lon = location.longitude

            // Reverse geocode to get city name
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=&latitude=$lat&longitude=$lon&count=1"
            var cityName = "Current location"

            // Try reverse geocoding via nominatim
            try {
                val revUrl = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json"
                val revReq = Request.Builder().url(revUrl)
                    .addHeader("User-Agent", "AlfredAI/1.0")
                    .build()
                val revResp = client.newCall(revReq).execute()
                val revBody = revResp.body?.string() ?: ""
                val revJson = JSONObject(revBody)
                val address = revJson.optJSONObject("address")
                cityName = address?.optString("city", "")
                    ?.ifBlank { address.optString("town", "") }
                    ?.ifBlank { address.optString("village", "") }
                    ?.ifBlank { "Current location" } ?: "Current location"
            } catch (_: Exception) {}

            getWeatherByCoords(lat, lon, cityName)
        } catch (e: Exception) {
            "Weather lookup failed: ${e.message}"
        }
    }

    private suspend fun getWeatherByCoords(lat: Double, lon: Double, cityName: String): String =
        withContext(Dispatchers.IO) {
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

            val result = JSONObject().apply {
                put("location", cityName)
                put("current", JSONObject().apply {
                    put("temperature_c", current.getDouble("temperature_2m"))
                    put("feels_like_c", current.getDouble("apparent_temperature"))
                    put("condition", weatherCodeToText(current.getInt("weather_code")))
                    put("humidity_percent", current.getInt("relative_humidity_2m"))
                    put("wind_speed_kmh", current.getDouble("wind_speed_10m"))
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
        ToolDef(name = "get_weather", description = "Get current weather and 3-day forecast for a location.",
            parameters = listOf(Param(name = "location", type = "string", description = "City name (e.g. 'London', 'Mumbai')")),
            required = listOf("location")
        ) { args -> getWeather(args.getString("location")) },
        ToolDef(name = "get_weather_here",
            description = "Get weather for the user's current GPS location. Use when user says 'weather here' or doesn't specify a city."
        ) { _ -> getWeatherHere() }
    )
}
