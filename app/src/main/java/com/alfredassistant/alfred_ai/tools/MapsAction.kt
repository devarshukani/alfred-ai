package com.alfredassistant.alfred_ai.tools

import android.Manifest
import android.content.Context
import android.content.Intent
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Maps & Places using free OSM APIs — no API key needed.
 *
 * - Overpass API: POI / category search (restaurants, shops, etc.)
 * - Nominatim: geocoding + specific place name search
 * - OSRM: routing / directions / distance
 */
class MapsAction(private val context: Context) {

    companion object {
        private const val TAG = "MapsAction"
        private const val USER_AGENT = "AlfredAI/1.0"
        private const val NOMINATIM = "https://nominatim.openstreetmap.org"
        private const val OVERPASS = "https://overpass-api.de/api/interpreter"
        private const val OSRM = "https://router.project-osrm.org"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // ── OSM category mapping for Overpass queries ──

    private val categoryTags = mapOf(
        "restaurant" to """["amenity"~"restaurant|food_court"]""",
        "restaurants" to """["amenity"~"restaurant|food_court"]""",
        "cafe" to """["amenity"="cafe"]""",
        "coffee" to """["amenity"="cafe"]""",
        "hotel" to """["tourism"~"hotel|motel|guest_house"]""",
        "hotels" to """["tourism"~"hotel|motel|guest_house"]""",
        "hospital" to """["amenity"~"hospital|clinic"]""",
        "pharmacy" to """["amenity"="pharmacy"]""",
        "gas station" to """["amenity"="fuel"]""",
        "petrol" to """["amenity"="fuel"]""",
        "fuel" to """["amenity"="fuel"]""",
        "atm" to """["amenity"="atm"]""",
        "bank" to """["amenity"="bank"]""",
        "parking" to """["amenity"="parking"]""",
        "supermarket" to """["shop"~"supermarket|convenience"]""",
        "grocery" to """["shop"~"supermarket|convenience|grocery"]""",
        "mall" to """["shop"="mall"]""",
        "shopping" to """["shop"~"mall|department_store|supermarket"]""",
        "school" to """["amenity"~"school|college"]""",
        "gym" to """["leisure"~"fitness_centre|sports_centre"]""",
        "park" to """["leisure"="park"]""",
        "temple" to """["amenity"="place_of_worship"]""",
        "church" to """["amenity"="place_of_worship"]""",
        "mosque" to """["amenity"="place_of_worship"]""",
        "police" to """["amenity"="police"]""",
        "cinema" to """["amenity"="cinema"]""",
        "bar" to """["amenity"~"bar|pub"]""",
        "pizza" to """["cuisine"~"pizza"]""",
        "bakery" to """["shop"="bakery"]""",
        "dentist" to """["amenity"~"dentist|doctors"]""",
        "doctor" to """["amenity"~"doctors|clinic"]""",
        "ev charging" to """["amenity"="charging_station"]""",
    )

    /**
     * Resolve the best Overpass tag filter for a query.
     * Tries exact match first, then substring match on keys.
     */
    private fun resolveTag(query: String): String? {
        val q = query.lowercase().trim()
        // Exact match
        categoryTags[q]?.let { return it }
        // Substring match — "best restaurants" → "restaurants"
        for ((key, tag) in categoryTags) {
            if (q.contains(key)) return tag
        }
        return null
    }

    // ── Search nearby places ──

    suspend fun searchPlaces(query: String, nearLocation: String? = null): String =
        withContext(Dispatchers.IO) {
            try {
                val (lat, lon) = if (nearLocation.isNullOrBlank()) {
                    getDeviceLocation() ?: return@withContext "Location unavailable. Please specify a city or area."
                } else {
                    geocode(nearLocation) ?: return@withContext "Could not find location: $nearLocation"
                }

                // Try Overpass first for category searches (restaurants, hotels, etc.)
                val tag = resolveTag(query)
                if (tag != null) {
                    val overpassResult = searchOverpass(tag, lat, lon, query)
                    if (overpassResult != null) return@withContext overpassResult
                }

                // Fallback: Nominatim for specific place names
                val nominatimResult = searchNominatim(query, lat, lon)
                if (nominatimResult != null) return@withContext nominatimResult

                "No results found for \"$query\". Try a simpler term like 'restaurant' or 'pharmacy'."
            } catch (e: Exception) {
                Log.e(TAG, "Place search failed", e)
                "Place search failed: ${e.message}"
            }
        }

    private fun searchOverpass(tag: String, lat: Double, lon: Double, query: String): String? {
        val radius = 10000 // 10km
        val overpassQuery = """
            [out:json][timeout:15];
            (
              node$tag(around:$radius,$lat,$lon);
              way$tag(around:$radius,$lat,$lon);
            );
            out center 10;
        """.trimIndent()

        Log.d(TAG, "Overpass query for '$query': $tag around $lat,$lon")

        val body = "data=${Uri.encode(overpassQuery)}"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val req = Request.Builder()
            .url(OVERPASS)
            .post(body)
            .addHeader("User-Agent", USER_AGENT)
            .build()

        val resp = client.newCall(req).execute()
        val respBody = resp.body?.string() ?: return null
        val json = JSONObject(respBody)
        val elements = json.optJSONArray("elements") ?: return null

        if (elements.length() == 0) return null

        val results = JSONArray()
        for (i in 0 until minOf(elements.length(), 8)) {
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: continue
            val name = tags.optString("name", "")
            if (name.isBlank()) continue

            val elLat = el.optDouble("lat", el.optJSONObject("center")?.optDouble("lat") ?: 0.0)
            val elLon = el.optDouble("lon", el.optJSONObject("center")?.optDouble("lon") ?: 0.0)

            results.put(JSONObject().apply {
                put("name", name)
                put("lat", elLat)
                put("lon", elLon)
                tags.optString("cuisine", "").ifBlank { null }?.let { put("cuisine", it) }
                tags.optString("phone", "").ifBlank { null }?.let { put("phone", it) }
                tags.optString("opening_hours", "").ifBlank { null }?.let { put("hours", it) }
                tags.optString("addr:street", "").ifBlank { null }?.let { street ->
                    val num = tags.optString("addr:housenumber", "")
                    put("address", "$num $street".trim())
                }
                tags.optString("website", "").ifBlank { null }?.let { put("website", it) }
            })
        }

        if (results.length() == 0) return null

        Log.d(TAG, "Overpass found ${results.length()} results for '$query'")

        return JSONObject().apply {
            put("query", query)
            put("results_count", results.length())
            put("results", results)
        }.toString()
    }

    private fun searchNominatim(query: String, lat: Double, lon: Double): String? {
        val delta = 0.15
        val url = "$NOMINATIM/search?" +
            "q=${Uri.encode(query)}" +
            "&format=json&addressdetails=1&limit=8" +
            "&viewbox=${lon - delta},${lat + delta},${lon + delta},${lat - delta}" +
            "&bounded=0"

        val resp = fetch(url)
        val arr = JSONArray(resp)
        if (arr.length() == 0) return null

        val results = JSONArray()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val addr = item.optJSONObject("address")
            results.put(JSONObject().apply {
                put("name", item.optString("display_name", "").split(",").first().trim())
                put("address", item.optString("display_name", ""))
                put("lat", item.optString("lat"))
                put("lon", item.optString("lon"))
                put("type", item.optString("type", ""))
                addr?.let {
                    put("city", it.optString("city", it.optString("town", it.optString("village", ""))))
                }
            })
        }

        return JSONObject().apply {
            put("query", query)
            put("results_count", results.length())
            put("results", results)
        }.toString()
    }

    // ── Get directions between two points ──

    suspend fun getDirections(from: String, to: String): String =
        withContext(Dispatchers.IO) {
            try {
                val origin = if (from.equals("here", ignoreCase = true) || from.equals("current location", ignoreCase = true)) {
                    getDeviceLocation() ?: return@withContext "Cannot determine current location."
                } else {
                    geocode(from) ?: return@withContext "Could not find origin: $from"
                }

                val dest = geocode(to) ?: return@withContext "Could not find destination: $to"

                val url = "$OSRM/route/v1/driving/${origin.second},${origin.first};${dest.second},${dest.first}" +
                    "?overview=false&steps=true"

                val resp = fetch(url)
                val json = JSONObject(resp)

                if (json.optString("code") != "Ok") {
                    return@withContext "Could not find a route from $from to $to."
                }

                val route = json.getJSONArray("routes").getJSONObject(0)
                val distanceKm = route.getDouble("distance") / 1000.0
                val durationMin = route.getDouble("duration") / 60.0

                val legs = route.getJSONArray("legs")
                val steps = JSONArray()
                for (l in 0 until legs.length()) {
                    val legSteps = legs.getJSONObject(l).getJSONArray("steps")
                    for (s in 0 until legSteps.length()) {
                        val step = legSteps.getJSONObject(s)
                        val maneuver = step.getJSONObject("maneuver")
                        val instruction = buildStepInstruction(
                            maneuver.optString("type", ""),
                            maneuver.optString("modifier", ""),
                            step.optString("name", "")
                        )
                        if (instruction.isNotBlank()) {
                            steps.put(JSONObject().apply {
                                put("instruction", instruction)
                                put("distance_km", String.format("%.1f", step.getDouble("distance") / 1000.0))
                            })
                        }
                    }
                }

                JSONObject().apply {
                    put("from", from)
                    put("to", to)
                    put("distance_km", String.format("%.1f", distanceKm))
                    put("duration_minutes", String.format("%.0f", durationMin))
                    put("steps", steps)
                }.toString()
            } catch (e: Exception) {
                "Directions failed: ${e.message}"
            }
        }

    // ── Open location in Maps app ──

    fun openInMaps(query: String): String {
        // Try Google Maps explicit intent first
        try {
            val gmUri = Uri.parse("https://www.google.com/maps/search/${Uri.encode(query)}")
            val gmIntent = Intent(Intent.ACTION_VIEW, gmUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            // Check if any app can handle it
            if (gmIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(gmIntent)
                return "Opened \"$query\" in Maps."
            }
        } catch (_: Exception) {}

        // Fallback: generic geo intent
        try {
            val geoUri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
            val geoIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(geoIntent)
            return "Opened \"$query\" in Maps."
        } catch (_: Exception) {}

        return "No maps app found on this device."
    }

    // ── Helpers ──

    private fun geocode(place: String): Pair<Double, Double>? {
        val url = "$NOMINATIM/search?q=${Uri.encode(place)}&format=json&limit=1"
        val resp = fetch(url)
        val arr = JSONArray(resp)
        if (arr.length() == 0) return null
        val obj = arr.getJSONObject(0)
        return obj.getDouble("lat") to obj.getDouble("lon")
    }

    private fun getDeviceLocation(): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: return null
        return loc.latitude to loc.longitude
    }

    private fun fetch(url: String): String {
        val req = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .build()
        val resp = client.newCall(req).execute()
        return resp.body?.string() ?: ""
    }

    private fun buildStepInstruction(type: String, modifier: String, road: String): String {
        val roadPart = if (road.isNotBlank()) " onto $road" else ""
        return when (type) {
            "depart" -> "Start$roadPart"
            "arrive" -> "Arrive at destination"
            "turn" -> "${modifier.replaceFirstChar { it.uppercase() }}$roadPart"
            "continue" -> "Continue$roadPart"
            "merge" -> "Merge$roadPart"
            "fork" -> "Take the $modifier fork$roadPart"
            "roundabout" -> "At the roundabout, take exit$roadPart"
            "new name" -> "Continue$roadPart"
            else -> ""
        }
    }

    // ── Tool definitions ──

    fun toolDefs(): List<ToolDef> = listOf(
        ToolDef(
            name = "search_places",
            description = "Search for places, businesses, restaurants, hotels, shops, or points of interest near a location. Uses OpenStreetMap Overpass API for category searches and Nominatim for specific places. Returns names, addresses, coordinates, phone numbers, and hours when available. Use simple category terms like 'restaurant', 'pharmacy', 'hotel' — not 'best restaurants'.",
            parameters = listOf(
                Param(name = "query", type = "string", description = "Category or place to search (e.g. 'restaurant', 'gas station', 'pharmacy', 'hotel'). Use simple terms."),
                Param(name = "near", type = "string", description = "City or area to search near (e.g. 'Rajkot', 'Mumbai'). Leave empty to use current GPS location.")
            ),
            required = listOf("query")
        ) { args -> searchPlaces(args.getString("query"), args.optString("near", "")) },

        ToolDef(
            name = "get_directions",
            description = "Get driving directions, distance, and estimated time between two locations. Say 'here' or 'current location' for origin to use GPS.",
            parameters = listOf(
                Param(name = "from", type = "string", description = "Starting location (or 'here' for current GPS)"),
                Param(name = "to", type = "string", description = "Destination location")
            ),
            required = listOf("from", "to")
        ) { args -> getDirections(args.getString("from"), args.getString("to")) },

        ToolDef(
            name = "open_in_maps",
            description = "Open a location, address, or place in the device's maps app (Google Maps, etc) for visual map view or turn-by-turn navigation. Use when user wants to see something on a map, navigate, or says 'open in maps'.",
            parameters = listOf(
                Param(name = "query", type = "string", description = "Place name or address to open in maps")
            ),
            required = listOf("query")
        ) { args -> openInMaps(args.getString("query")) }
    )
}
