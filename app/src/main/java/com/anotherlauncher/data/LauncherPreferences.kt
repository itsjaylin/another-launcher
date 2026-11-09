package com.anotherlauncher.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anotherlauncher.ui.DockSide
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.launcherDataStore by preferencesDataStore("launcher")

data class LauncherPreferences(
    val favorites: List<String> = emptyList(),
    val home: HomeLayout = HomeLayout(),
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
    val initialized: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val layoutError: Boolean = false,
)

class PreferencesRepository(context: Context) {
    private val store = context.applicationContext.launcherDataStore
    val preferences = store.data.catch { error ->
        if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
    }.map { data ->
        val home = runCatching { readHome(data) }
        LauncherPreferences(
            favorites = decode(data[FAVORITES]), home = home.getOrDefault(HomeLayout()), layoutError = home.isFailure,
            categoryOverrides = decodeCategories(data[CATEGORIES]),
            libraryAz = data[LIBRARY_AZ] ?: false,
            innerSideLayout = data[INNER_SIDE_LAYOUT] ?: false,
            searchProvider = SearchProvider.entries.firstOrNull { it.name == data[SEARCH_PROVIDER] } ?: SearchProvider.GOOGLE,
            dockSide = DockSide.entries.firstOrNull { it.name == data[SIDE] } ?: DockSide.RIGHT,
            reducedMotion = data[MOTION] ?: false, initialized = data[INITIALIZED] ?: false,
            onboardingCompleted = data[ONBOARDING_COMPLETED] ?: false,
            showHomeLabels = data[LABELS] ?: false,
            includeContacts = data[CONTACTS] ?: false,
            statusColor = data[STATUS_COLOR],
            alwaysShowPageDots = data[PAGE_DOTS] ?: false,
            showDots = data[DOTS] ?: true,
            showCellularIndicator = data[CELLULAR_INDICATOR] ?: true,
            weatherLocation = data[WEATHER]?.let { runCatching { WeatherLocation.decode(it) }.getOrNull() },
            weatherUnit = data[WEATHER_UNIT],
        )
    }
    suspend fun initialize(ids: List<String>) = store.edit { data ->
        if (data[INITIALIZED] != true) {
            data[FAVORITES] = encode(ids.take(4))
            val initialLayout = HomeEditor.defaultLayout(ids.drop(4))
            data[HOME] = HomeLayoutCodec.encode(initialLayout)
            data[INITIALIZED] = true
        }
        if (data[HOME] == null) data[HOME] = HomeLayoutCodec.encode(readHome(data))
    }
    suspend fun pin(id: String, favorite: Boolean, index: Int = Int.MAX_VALUE) = store.edit { data ->
        check(favorite) { "Home pins must use explicit page placement" }
        data[FAVORITES] = encode(PinOrder.insert(decode(data[FAVORITES]), id, index, 6))
    }
    suspend fun remove(id: String, favorite: Boolean) = store.edit { data ->
        check(favorite)
        data[FAVORITES] = encode(decode(data[FAVORITES]).filterNot { it == id })
    }
    suspend fun toggleFavorite(id: String): Boolean? {
        var added: Boolean? = null
        store.edit { data ->
            val ids = decode(data[FAVORITES])
            when {
                id in ids -> { data[FAVORITES] = encode(ids - id); added = false }
                ids.size < 6 -> { data[FAVORITES] = encode(ids + id); added = true }
            }
        }
        return added
    }
    suspend fun editHome(transform: (HomeLayout) -> HomeEdit): HomeEdit {
        var result: HomeEdit? = null
        store.edit { data ->
            val current = try { readHome(data) } catch (error: Exception) {
                throw IOException("Saved layout couldn't be read; it was preserved.", error)
            }
            val edited = transform(current)
            result = edited
            if (edited.error == null) data[HOME] = HomeLayoutCodec.encode(edited.layout)
        }
        return result!!
    }
    suspend fun category(id: String, category: AppCategory?) = store.edit { data ->
        val entries = decodeCategories(data[CATEGORIES]).toMutableMap()
        if (category == null) entries.remove(id) else entries[id] = category
        data[CATEGORIES] = JSONObject(entries.mapValues { it.value.name }).toString()
    }
    suspend fun clearCategories() = store.edit { it.remove(CATEGORIES) }
    suspend fun libraryAz(value: Boolean) = store.edit { it[LIBRARY_AZ] = value }
    suspend fun innerSideLayout(value: Boolean) = store.edit { it[INNER_SIDE_LAYOUT] = value }
    suspend fun searchProvider(value: SearchProvider) = store.edit { it[SEARCH_PROVIDER] = value.name }
    suspend fun side(value: DockSide) = store.edit { it[SIDE] = value.name }
    suspend fun motion(value: Boolean) = store.edit { it[MOTION] = value }
    suspend fun labels(value: Boolean) = store.edit { it[LABELS] = value }
    suspend fun contacts(value: Boolean) = store.edit { it[CONTACTS] = value }
    suspend fun statusColor(value: Int?) = store.edit { data ->
        if (value == null) data.remove(STATUS_COLOR) else data[STATUS_COLOR] = value
    }
    suspend fun pageDots(value: Boolean) = store.edit { it[PAGE_DOTS] = value }
    suspend fun dots(value: Boolean) = store.edit { it[DOTS] = value }
    suspend fun cellularIndicator(value: Boolean) = store.edit { it[CELLULAR_INDICATOR] = value }
    suspend fun removePackage(packageName: String) = store.edit { data ->
        val current = try { readHome(data) } catch (error: Exception) { throw IOException("Saved layout was preserved", error) }
        data[HOME] = HomeLayoutCodec.encode(HomeEditor.removePackage(current, packageName))
        data[FAVORITES] = encode(decode(data[FAVORITES]).filterNot { it.startsWith("$packageName/") })
    }
    suspend fun weather(location: WeatherLocation) = store.edit { it[WEATHER] = location.encode() }
    suspend fun weatherUnit(unit: String?) = store.edit { data ->
        if (unit == null) data.remove(WEATHER_UNIT) else data[WEATHER_UNIT] = unit
    }
    suspend fun completeOnboarding() = store.edit { it[ONBOARDING_COMPLETED] = true }

    suspend fun createBackup(appVersion: String): LauncherBackup {
        val data = store.data.first()
        val home = readHome(data)
        return LauncherBackup(
            backupVersion = LauncherBackup.CURRENT_VERSION,
            appVersion = appVersion,
            timestamp = System.currentTimeMillis(),
            home = home,
            favorites = decode(data[FAVORITES]),
            categoryOverrides = decodeCategories(data[CATEGORIES]),
            libraryAz = data[LIBRARY_AZ] ?: false,
            innerSideLayout = data[INNER_SIDE_LAYOUT] ?: false,
            searchProvider = SearchProvider.entries.firstOrNull { it.name == data[SEARCH_PROVIDER] } ?: SearchProvider.GOOGLE,
            dockSide = DockSide.entries.firstOrNull { it.name == data[SIDE] } ?: DockSide.RIGHT,
            reducedMotion = data[MOTION] ?: false,
            showHomeLabels = data[LABELS] ?: false,
            includeContacts = data[CONTACTS] ?: false,
            statusColor = data[STATUS_COLOR],
            alwaysShowPageDots = data[PAGE_DOTS] ?: false,
            showDots = data[DOTS] ?: true,
            showCellularIndicator = data[CELLULAR_INDICATOR] ?: true,
            weatherLocation = data[WEATHER]?.let { runCatching { WeatherLocation.decode(it) }.getOrNull() },
            weatherUnit = data[WEATHER_UNIT],
        )
    }

    suspend fun restoreBackup(backup: LauncherBackup) = store.edit { data ->
        data[HOME] = HomeLayoutCodec.encode(backup.home)
        data[FAVORITES] = encode(backup.favorites)
        data[CATEGORIES] = JSONObject(backup.categoryOverrides.mapValues { it.value.name }).toString()
        data[LIBRARY_AZ] = backup.libraryAz
        data[INNER_SIDE_LAYOUT] = backup.innerSideLayout
        data[SEARCH_PROVIDER] = backup.searchProvider.name
        data[SIDE] = backup.dockSide.name
        data[MOTION] = backup.reducedMotion
        data[LABELS] = backup.showHomeLabels
        data[CONTACTS] = backup.includeContacts
        if (backup.statusColor != null) data[STATUS_COLOR] = backup.statusColor else data.remove(STATUS_COLOR)
        data[PAGE_DOTS] = backup.alwaysShowPageDots
        data[DOTS] = backup.showDots
        data[CELLULAR_INDICATOR] = backup.showCellularIndicator
        if (backup.weatherLocation != null) data[WEATHER] = backup.weatherLocation.encode() else data.remove(WEATHER)
        if (backup.weatherUnit != null) data[WEATHER_UNIT] = backup.weatherUnit else data.remove(WEATHER_UNIT)
        data[INITIALIZED] = true
        data[ONBOARDING_COMPLETED] = true
    }

    private fun readHome(data: androidx.datastore.preferences.core.Preferences): HomeLayout = data[HOME]?.let(HomeLayoutCodec::decode)
        ?: data[LEGACY_HOME]?.let { HomeEditor.upgradeDashboard(HomeLayoutCodec.decode(it)) }
        ?: HomeEditor.migrate(decode(data[WORKSPACE]), decode(data[WIDGETS]).mapNotNull(String::toIntOrNull))

    private fun decodeCategories(json: String?): Map<String, AppCategory> = try {
        val root = JSONObject(json ?: "{}")
        root.keys().asSequence().mapNotNull { key ->
            AppCategory.entries.firstOrNull { it.name == root.optString(key) }?.let { key to it }
        }.toMap()
    } catch (_: org.json.JSONException) { emptyMap() }

    private fun encode(ids: List<String>): String = JSONArray(ids).toString()
    private fun decode(json: String?): List<String> = try {
        val array = JSONArray(json ?: "[]")
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.distinct()
    } catch (_: org.json.JSONException) { emptyList() }
    private companion object {
        val FAVORITES = stringPreferencesKey("favorites")
        val WORKSPACE = stringPreferencesKey("workspace")
        val WIDGETS = stringPreferencesKey("widgets")
        val SIDE = stringPreferencesKey("dock_side")
        val MOTION = booleanPreferencesKey("reduced_motion")
        val INITIALIZED = booleanPreferencesKey("initialized")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val HOME = stringPreferencesKey("home_layout_v3")
        val LEGACY_HOME = stringPreferencesKey("home_layout_v2")
        val LABELS = booleanPreferencesKey("show_home_labels")
        val CONTACTS = booleanPreferencesKey("search_contacts")
        val STATUS_COLOR = intPreferencesKey("status_tint")
        val PAGE_DOTS = booleanPreferencesKey("always_show_page_dots")
        val DOTS = booleanPreferencesKey("notification_dots")
        val CELLULAR_INDICATOR = booleanPreferencesKey("show_cellular_indicator")
        val WEATHER = stringPreferencesKey("weather_location")
        val WEATHER_UNIT = stringPreferencesKey("weather_unit")
        val CATEGORIES = stringPreferencesKey("category_overrides")
        val LIBRARY_AZ = booleanPreferencesKey("library_az")
        val INNER_SIDE_LAYOUT = booleanPreferencesKey("inner_side_layout")
        val SEARCH_PROVIDER = stringPreferencesKey("search_provider")
    }
}
