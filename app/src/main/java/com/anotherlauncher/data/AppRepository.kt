package com.anotherlauncher.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.os.Process
import android.os.UserHandle
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.content.pm.ApplicationInfo
import java.text.Collator

data class AppEntry(val id: String, val label: String, val info: LauncherActivityInfo) {
    val category: AppCategory = AppCategory.classify(
        platformCategory = info.applicationInfo.category,
        packageName = info.componentName.packageName,
        label = label,
        isGame = isGameApp(info.applicationInfo)
    )

    companion object {
        @Suppress("DEPRECATION")
        fun isGameApp(appInfo: ApplicationInfo): Boolean =
            appInfo.category == ApplicationInfo.CATEGORY_GAME ||
            (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
    }
}

/** Current user's launchable activities; package visibility is scoped by the manifest query. */
class AppRepository(private val context: Context, private val scope: CoroutineScope) : AutoCloseable {
    private val launcher = context.getSystemService(LauncherApps::class.java)
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val mutableApps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps = mutableApps.asStateFlow()
    val loaded = MutableStateFlow(false)
    val removedPackages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private var refreshJob: Job? = null
    private val icons = LruCache<String, Bitmap>(96)
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(name: String, user: UserHandle) = refresh()
        override fun onPackageRemoved(name: String, user: UserHandle) {
            if (user == Process.myUserHandle()) removedPackages.tryEmit(name)
            refresh()
        }
        override fun onPackageChanged(name: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }
    init { launcher.registerCallback(callback); refresh() }
    fun refresh() {
        refreshJob?.cancel()
        icons.evictAll()
        refreshJob = scope.launch(Dispatchers.IO) {
            val collator = Collator.getInstance()
            val entries = launcher.getActivityList(null, Process.myUserHandle())
                .filterNot {
                    it.componentName.packageName == context.packageName &&
                        (it.componentName.className == "com.anotherlauncher.LauncherActivity" || it.componentName.className.endsWith(".LauncherActivity"))
                }
                .map { AppEntry(it.componentName.flattenToString(), it.label.toString(), it) }
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
            mutableApps.value = entries
            loaded.value = true
        }
    }
    fun icon(app: AppEntry): Bitmap {
        icons.get(app.id)?.let { return it }
        val density = activityManager?.launcherLargeIconDensity?.takeIf { it > 0 }
            ?: android.util.DisplayMetrics.DENSITY_XXXHIGH
        val targetSize = maxOf(activityManager?.launcherLargeIconSize ?: 0, (72 * context.resources.displayMetrics.density).toInt())
        val pixels = targetSize.coerceIn(96, 384)
        val drawable = try { app.info.getBadgedIcon(density) }
        catch (_: RuntimeException) { context.packageManager.defaultActivityIcon }
        return drawable.toBitmap(pixels, pixels).also { icons.put(app.id, it) }
    }
    fun launch(app: AppEntry): Boolean = try {
        launcher.startMainActivity(app.info.componentName, app.info.user, null, null)
        true
    } catch (_: SecurityException) { false }
    catch (_: android.content.ActivityNotFoundException) { refresh(); false }
    /** Pinned, manifest and dynamic shortcuts for long-press menus; an empty list is always valid. */
    fun shortcuts(app: AppEntry, max: Int = 4): List<ShortcutInfo> = try {
        val query = LauncherApps.ShortcutQuery()
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            .setActivity(app.info.componentName)
        launcher.getShortcuts(query, Process.myUserHandle())?.filter { it.isEnabled }?.take(max)
            .orEmpty()
    } catch (_: SecurityException) { emptyList() }
    catch (_: IllegalStateException) { emptyList() }
    fun startShortcut(app: AppEntry, shortcutId: String): Boolean = try {
        launcher.startShortcut(app.info.componentName.packageName, shortcutId, null, null, app.info.user)
        true
    } catch (_: SecurityException) { false }
    catch (_: android.content.ActivityNotFoundException) { false }
    override fun close() { launcher.unregisterCallback(callback); refreshJob?.cancel(); icons.evictAll() }
}
