package com.anotherlauncher.data

import com.anotherlauncher.ui.DockSide
import org.json.JSONArray
import org.json.JSONObject

data class LauncherBackup(
    val backupVersion: Int = CURRENT_VERSION,
    val appVersion: String,
    val timestamp: Long = System.currentTimeMillis(),
    val home: HomeLayout,
    val favorites: List<String> = emptyList(),
    val categoryOverrides: Map<String, AppCategory> = emptyMap(),
    val libraryAz: Boolean = false,
    val innerSideLayout: Boolean = false,
    val searchProvider: SearchProvider = SearchProvider.GOOGLE,
    val dockSide: DockSide = DockSide.RIGHT,
    val reducedMotion: Boolean = false,
    val showHomeLabels: Boolean = false,
    val includeContacts: Boolean = false,
    val statusColor: Int? = null,
    val alwaysShowPageDots: Boolean = false,
    val showDots: Boolean = true,
    val showCellularIndicator: Boolean = true,
    val weatherLocation: WeatherLocation? = null,
    val weatherUnit: String? = null,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/** Strict, versioned backup serialization and deserialization. */
object BackupCodec {
    fun encode(backup: LauncherBackup): String {
        return JSONObject().apply {
            put("backupVersion", backup.backupVersion)
            put("appVersion", backup.appVersion)
            put("timestamp", backup.timestamp)
            put("home", JSONObject(HomeLayoutCodec.encode(backup.home)))
            put("favorites", JSONArray(backup.favorites))
            put("categories", JSONObject(backup.categoryOverrides.mapValues { it.value.name }))
            put("settings", JSONObject().apply {
                put("dockSide", backup.dockSide.name)
                put("libraryAz", backup.libraryAz)
                put("innerSideLayout", backup.innerSideLayout)
                put("searchProvider", backup.searchProvider.name)
                put("reducedMotion", backup.reducedMotion)
                put("showHomeLabels", backup.showHomeLabels)
                put("includeContacts", backup.includeContacts)
                backup.statusColor?.let { put("statusColor", it) }
                put("alwaysShowPageDots", backup.alwaysShowPageDots)
                put("showDots", backup.showDots)
                put("showCellularIndicator", backup.showCellularIndicator)
                backup.weatherLocation?.let { put("weatherLocation", it.encode()) }
                backup.weatherUnit?.let { put("weatherUnit", it) }
            })
        }.toString(2)
    }

    fun decode(json: String): LauncherBackup {
        val root = JSONObject(json)
        val version = root.getInt("backupVersion")
        require(version == 1) { "Unsupported backup version: $version" }
        val appVersion = root.optString("appVersion", "unknown")
        val timestamp = root.optLong("timestamp", System.currentTimeMillis())

        val homeObj = root.optJSONObject("home")
        val homeStr = homeObj?.toString() ?: root.getString("home")
        val home = HomeLayoutCodec.decode(homeStr)

        val favArray = root.optJSONArray("favorites")
        val favorites = if (favArray != null) {
            (0 until favArray.length()).mapNotNull { favArray.optString(it).takeIf(String::isNotBlank) }.distinct()
        } else emptyList()

        val catObj = root.optJSONObject("categories")
        val categories = if (catObj != null) {
            catObj.keys().asSequence().mapNotNull { key ->
                AppCategory.entries.firstOrNull { it.name == catObj.optString(key) }?.let { key to it }
            }.toMap()
        } else emptyMap()

        val settings = root.optJSONObject("settings") ?: JSONObject()
        val dockSide = DockSide.entries.firstOrNull { it.name == settings.optString("dockSide") } ?: DockSide.RIGHT
        val reducedMotion = settings.optBoolean("reducedMotion", false)
        val libraryAz = settings.optBoolean("libraryAz", false)
        val innerSideLayout = settings.optBoolean("innerSideLayout", false)
        val searchProvider = SearchProvider.entries.firstOrNull { it.name == settings.optString("searchProvider") } ?: SearchProvider.GOOGLE
        val showHomeLabels = settings.optBoolean("showHomeLabels", false)
        val includeContacts = settings.optBoolean("includeContacts", false)
        val statusColor = if (settings.has("statusColor")) settings.getInt("statusColor") else null
        val alwaysShowPageDots = settings.optBoolean("alwaysShowPageDots", false)
        val showDots = settings.optBoolean("showDots", true)
        val showCellularIndicator = settings.optBoolean("showCellularIndicator", true)
        val weatherLocation = settings.optString("weatherLocation").takeIf(String::isNotBlank)?.let {
            runCatching { WeatherLocation.decode(it) }.getOrNull()
        }
        val weatherUnit = settings.optString("weatherUnit").takeIf(String::isNotBlank)

        return LauncherBackup(
            backupVersion = version,
            appVersion = appVersion,
            timestamp = timestamp,
            home = home,
            favorites = favorites,
            categoryOverrides = categories,
            libraryAz = libraryAz,
            innerSideLayout = innerSideLayout,
            searchProvider = searchProvider,
            dockSide = dockSide,
            reducedMotion = reducedMotion,
            showHomeLabels = showHomeLabels,
            includeContacts = includeContacts,
            statusColor = statusColor,
            alwaysShowPageDots = alwaysShowPageDots,
            showDots = showDots,
            showCellularIndicator = showCellularIndicator,
            weatherLocation = weatherLocation,
            weatherUnit = weatherUnit,
        )
    }
}
