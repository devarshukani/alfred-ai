package com.alfredassistant.alfred_ai.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.alfredassistant.alfred_ai.skills.Param
import com.alfredassistant.alfred_ai.skills.ToolDef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LocationAction(private val context: Context) {

    companion object {
        private const val TAG = "LocationAction"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun getDeviceGps(): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: return null
        return Pair(loc.latitude, loc.longitude)
    }

    private fun reverseGeocode(lat: Double, lon: Double): Pair<String, String> {
        return try {
            val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json"
            val req = Request.Builder().url(url).addHeader("User-Agent", "AlfredAI/1.0").build()
            val body = client.newCall(req).execute().body?.string() ?: ""
            val address = JSONObject(body).optJSONObject("address")
            val city = address?.optString("city", "")
                ?.ifBlank { address.optString("town", "") }
                ?.ifBlank { address.optString("village", "") }
                ?.ifBlank { "Unknown" } ?: "Unknown"
            val country = address?.optString("country", "") ?: ""
            Pair(city, country)
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocode failed: ${e.message}")
            Pair("Unknown", "")
        }
    }

    private fun forwardGeocode(place: String): JSONObject? {
        return try {
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(place)}&count=1"
            val body = client.newCall(Request.Builder().url(url).build()).execute().body?.string() ?: ""
            val results = JSONObject(body).optJSONArray("results")
            if (results != null && results.length() > 0) results.getJSONObject(0) else null
        } catch (e: Exception) {
            Log.w(TAG, "Forward geocode failed: ${e.message}")
            null
        }
    }

    /** Device GPS → city, country */
    suspend fun deviceLocation(): String = withContext(Dispatchers.IO) {
        val gps = getDeviceGps()
            ?: return@withContext "Location unavailable. GPS may be off or permission not granted."
        val (city, country) = reverseGeocode(gps.first, gps.second)
        JSONObject().apply {
            put("city", city)
            put("country", country)
        }.toString()
    }

    /** Device GPS → lat, lon */
    suspend fun deviceCoordinates(): String = withContext(Dispatchers.IO) {
        val gps = getDeviceGps()
            ?: return@withContext "Location unavailable. GPS may be off or permission not granted."
        JSONObject().apply {
            put("latitude", gps.first)
            put("longitude", gps.second)
        }.toString()
    }

    /** Place name → city, country */
    suspend fun getLocation(place: String): String = withContext(Dispatchers.IO) {
        val geo = forwardGeocode(place)
            ?: return@withContext "Could not find location: $place"
        JSONObject().apply {
            put("city", geo.getString("name"))
            put("country", geo.optString("country", ""))
        }.toString()
    }

    /** Place name → lat, lon */
    suspend fun getCoordinates(place: String): String = withContext(Dispatchers.IO) {
        val geo = forwardGeocode(place)
            ?: return@withContext "Could not find location: $place"
        JSONObject().apply {
            put("latitude", geo.getDouble("latitude"))
            put("longitude", geo.getDouble("longitude"))
            put("name", geo.getString("name"))
        }.toString()
    }

    fun toolDefs(): List<ToolDef> = listOf(
        ToolDef(
            name = "device_location",
            description = "Get the device's current location as city and country from GPS."
        ) { _ -> deviceLocation() },
        ToolDef(
            name = "device_coordinates",
            description = "Get the device's current GPS coordinates (latitude, longitude). Use before get_weather for 'weather here'."
        ) { _ -> deviceCoordinates() },
        ToolDef(
            name = "get_location",
            description = "Get city and country for a place name.",
            parameters = listOf(Param(name = "place", type = "string", description = "Place name to look up")),
            required = listOf("place")
        ) { args -> getLocation(args.getString("place")) },
        ToolDef(
            name = "get_coordinates",
            description = "Get latitude and longitude for a place name. Use before get_weather for a named location.",
            parameters = listOf(Param(name = "place", type = "string", description = "Place name to look up")),
            required = listOf("place")
        ) { args -> getCoordinates(args.getString("place")) }
    )
}
