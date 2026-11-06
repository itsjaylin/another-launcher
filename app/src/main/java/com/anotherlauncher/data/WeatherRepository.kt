package com.anotherlauncher.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

data class WeatherLocation(val name: String, val latitude: Double, val longitude: Double) {
    init { require(name.isNotBlank() && latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0) }
    fun encode() = JSONObject().put("name", name).put("lat", latitude).put("lon", longitude).toString()
    companion object {
        fun decode(json: String): WeatherLocation = JSONObject(json).let { WeatherLocation(it.getString("name"), it.getDouble("lat"), it.getDouble("lon")) }
    }
}

data class WeatherReading(val location: WeatherLocation, val celsius: Double, val code: Int, val fetchedAt: Long) {
    val description get() = when (code) {
        0 -> "Clear skies"; 1, 2 -> "Partly cloudy"; 3 -> "Cloudy"; 45, 48 -> "Fog"
        in 51..57 -> "Drizzle"; in 61..67, in 80..82 -> "Rain"; in 71..77, 85, 86 -> "Snow"
        95, 96, 99 -> "Thunderstorms"; else -> "Current weather"
    }

    val fahrenheit: Double get() = celsius * 9.0 / 5.0 + 32.0

    fun displayTemp(unit: String?, isDefaultUs: Boolean = true): Pair<Int, String> {
        val isF = when (unit?.uppercase()) {
            "F" -> true
            "C" -> false
            else -> isDefaultUs
        }
        val degrees = if (isF) Math.round(fahrenheit).toInt() else Math.round(celsius).toInt()
        val unitLabel = if (isF) "°F" else "°C"
        return degrees to unitLabel
    }
}


data class WeatherState(val location: WeatherLocation? = null, val reading: WeatherReading? = null, val loading: Boolean = false, val error: String? = null)

/** Explicit city selection, HTTPS and no location permission/API key. Data: Open-Meteo. */
class WeatherRepository {
    private var cached: WeatherReading? = null
    suspend fun search(query: String): List<WeatherLocation> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val values = request("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=5&language=en&format=json").optJSONArray("results")
            ?: return@withContext emptyList()
        (0 until values.length()).mapNotNull { index -> runCatching { values.getJSONObject(index).let {
            val name = listOf(it.getString("name"), it.optString("admin1"), it.optString("country")).filter(String::isNotBlank).distinct().joinToString(", ")
            WeatherLocation(name, it.getDouble("latitude"), it.getDouble("longitude"))
        } }.getOrNull() }.distinctBy { it.latitude to it.longitude }
    }
    suspend fun current(location: WeatherLocation): WeatherReading = withContext(Dispatchers.IO) {
        cached?.takeIf { it.location == location && System.currentTimeMillis() - it.fetchedAt in 0..900_000 }?.let { return@withContext it }
        val current = request("https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}&current=temperature_2m,weather_code").getJSONObject("current")
        val temperature = current.getDouble("temperature_2m")
        if (!temperature.isFinite()) throw IOException("Temperature unavailable")
        WeatherReading(location, temperature, current.getInt("weather_code"), System.currentTimeMillis()).also { cached = it }
    }
    private fun request(url: String): JSONObject {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000; connection.readTimeout = 8_000
        return try {
            if (connection.responseCode !in 200..299) throw IOException("Weather service unavailable")
            val body = connection.inputStream.bufferedReader().use { reader ->
                val text = reader.readText()
                if (text.length > 256_000) throw IOException("Unexpected weather response")
                text
            }
            try { JSONObject(body) } catch (error: org.json.JSONException) { throw IOException("Invalid weather response", error) }
        } finally { connection.disconnect() }
    }
}

@SuppressLint("MissingPermission")
suspend fun detectCurrentWeatherLocation(context: Context): WeatherLocation {
    val manager = context.getSystemService(LocationManager::class.java)
        ?: throw IOException("LocationManager unavailable")
    val providers = listOf(
        LocationManager.FUSED_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER
    ).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    if (providers.isEmpty()) throw IOException("Location Services are disabled")

    // Check recent cached location first for instantaneous response
    val lastKnown = providers.firstNotNullOfOrNull { p ->
        runCatching { manager.getLastKnownLocation(p) }.getOrNull()
    }?.takeIf { System.currentTimeMillis() - it.time < 4 * 3600 * 1000L }

    val location = lastKnown ?: run {
        val provider = providers.first()
        suspendCancellableCoroutine<Location?> { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                manager.getCurrentLocation(provider, signal, context.mainExecutor) {
                    if (continuation.isActive) continuation.resume(it)
                }
            } catch (error: SecurityException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            }
        } ?: providers.firstNotNullOfOrNull { p -> runCatching { manager.getLastKnownLocation(p) }.getOrNull() }
        ?: throw IOException("No current location")
    }
    val name = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        runCatching { Geocoder(context, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1) }
            .getOrNull()?.firstOrNull()?.let { address ->
                listOfNotNull(address.locality ?: address.subAdminArea, address.adminArea, address.countryName).distinct().joinToString(", ")
            }?.takeIf(String::isNotBlank)
    } ?: "%.2f, %.2f".format(location.latitude, location.longitude)
    return WeatherLocation(name, location.latitude, location.longitude)
}
