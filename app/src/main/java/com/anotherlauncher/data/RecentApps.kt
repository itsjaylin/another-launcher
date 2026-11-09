package com.anotherlauncher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks recently launched apps by durable ID, persisting them across sessions.
 * The most recently used app is placed at the front of the list.
 */
object RecentApps {
    private const val PREFS = "anotherlauncher_recent_apps"
    private const val KEY_RECENTS = "recent_app_ids"
    const val MAX_RECENTS = 20

    private val _recents = MutableStateFlow<List<String>>(emptyList())
    val recents: StateFlow<List<String>> = _recents.asStateFlow()

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RECENTS, null)
        val list = raw?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        _recents.value = list
    }

    fun recordId(currentList: List<String>, appId: String, max: Int = MAX_RECENTS): List<String> {
        if (appId.isBlank()) return currentList
        val updated = currentList.toMutableList()
        updated.remove(appId)
        updated.add(0, appId)
        return if (updated.size > max) updated.take(max) else updated
    }

    fun record(context: Context, appId: String) {
        if (appId.isBlank()) return
        val updated = recordId(_recents.value, appId)
        _recents.value = updated

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENTS, updated.joinToString(","))
            .apply()
    }

    fun <T> computeRecents(allApps: List<T>, recentIds: List<String>, count: Int = 4, idSelector: (T) -> String): List<T> {
        val appMap = allApps.associateBy(idSelector)
        val recorded = recentIds.mapNotNull { appMap[it] }
        return if (recorded.size >= count) {
            recorded.take(count)
        } else {
            val used = recorded.map(idSelector).toSet()
            recorded + allApps.filter { idSelector(it) !in used }.take(count - recorded.size)
        }
    }

    fun computeRecents(allApps: List<AppEntry>, recentIds: List<String>, count: Int = 4): List<AppEntry> =
        computeRecents(allApps, recentIds, count) { it.id }

    /** Testing helper to update memory state without SharedPreferences. */
    fun setForTesting(list: List<String>) {
        _recents.value = list
    }
}
