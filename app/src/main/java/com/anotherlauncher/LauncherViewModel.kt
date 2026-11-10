package com.anotherlauncher

import android.app.Application
import android.content.Context
import android.appwidget.AppWidgetManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.anotherlauncher.data.AppEntry
import com.anotherlauncher.data.AppRepository
import com.anotherlauncher.data.LauncherPreferences
import com.anotherlauncher.data.PreferencesRepository
import com.anotherlauncher.data.AppCategory
import com.anotherlauncher.data.HomeEdit
import com.anotherlauncher.data.HomeEditor
import com.anotherlauncher.data.HomeItem
import com.anotherlauncher.data.HomeItemKind
import com.anotherlauncher.data.HomeLayout
import com.anotherlauncher.data.HomePage
import com.anotherlauncher.data.WeatherRepository
import com.anotherlauncher.data.WeatherState
import com.anotherlauncher.data.WeatherLocation
import com.anotherlauncher.data.detectCurrentWeatherLocation
import com.anotherlauncher.ui.DockSide
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import com.anotherlauncher.data.BackupCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException

data class PageNotice(val token: Long, val message: String, val canUndo: Boolean)
private data class PageUndo(val before: HomeLayout, val after: HomeLayout, val selectedPage: String)

class LauncherViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    val apps = AppRepository(application, viewModelScope)
    private val repository = PreferencesRepository(application)
    val preferences = repository.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherPreferences())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val _selectedPage = savedState.getStateFlow<String?>("selected_page", null)
    val selectedPage: StateFlow<String> = combine(_selectedPage, preferences) { selected, prefs ->
        val defaultId = prefs.home.resolvedDefaultPageId()
        selected?.takeIf { id ->
            id == com.anotherlauncher.ui.PagerMap.HUB_KEY ||
            id == com.anotherlauncher.ui.PagerMap.LIBRARY_KEY ||
            prefs.home.pages.any { it.id == id }
        } ?: defaultId
    }.stateIn(viewModelScope, SharingStarted.Eagerly, preferences.value.home.resolvedDefaultPageId())
    private val mutablePageNotice = MutableStateFlow<PageNotice?>(null)
    val pageNotice = mutablePageNotice.asStateFlow()
    private var pageUndo: PageUndo? = null
    private var pageNoticeToken = 0L
    val weatherRepository = WeatherRepository()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val weather = preferences.map { it.weatherLocation }.distinctUntilChanged().flatMapLatest { location -> flow {
        if (location == null) emit(WeatherState()) else while (true) {
            emit(WeatherState(location, loading = true))
            try { emit(WeatherState(location, weatherRepository.current(location))) }
            catch (_: IOException) { emit(WeatherState(location, error = "Weather unavailable")) }
            catch (_: org.json.JSONException) { emit(WeatherState(location, error = "Weather unavailable")) }
            delay(900_000)
        }
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeatherState())

    init {
        viewModelScope.launch {
            apps.removedPackages.collect { packageName ->
                try { repository.removePackage(packageName) }
                catch (_: IOException) { messages.emit("The app was removed, but its saved placement couldn't be updated.") }
            }
        }
        viewModelScope.launch {
            combine(preferences, apps.apps, apps.loaded) { prefs, entries, loaded -> Triple(prefs, entries, loaded) }
                .collect { (prefs, entries, loaded) ->
                    if (loaded) {
                        try { repository.initialize(entries.map { it.id }) }
                        catch (_: IOException) { messages.emit("Couldn't read your saved home layout.") }
                        catch (_: IllegalArgumentException) { messages.emit("Your saved layout was preserved; its version couldn't be read.") }
                    }
                }
        }
    }
    fun launch(app: AppEntry) {
        com.anotherlauncher.data.NewApps.opened(getApplication(), app.info.componentName.packageName)
        com.anotherlauncher.data.RecentApps.record(getApplication(), app.id)
        if (!apps.launch(app)) messages.tryEmit("This app is unavailable. The app list has been refreshed.")
    }
    fun pin(app: AppEntry, favorite: Boolean, index: Int = Int.MAX_VALUE) {
        if (!favorite) { addApp(app); return }
        val ids = preferences.value.favorites
        if (app.id !in ids && ids.size >= 6) {
            messages.tryEmit("Your dock has six favorites. Remove one to make room.")
            return
        }
        save { repository.pin(app.id, favorite, index) }
    }
    fun remove(app: AppEntry, favorite: Boolean) = save { repository.remove(app.id, favorite) }
    fun setSide(side: DockSide) = save { repository.side(side) }
    fun setLibraryAz(enabled: Boolean) = save { repository.libraryAz(enabled) }
    fun setInnerSideLayout(enabled: Boolean) = save { repository.innerSideLayout(enabled) }
    fun setSearchProvider(provider: com.anotherlauncher.data.SearchProvider) = save { repository.searchProvider(provider) }
    fun setMotion(reduced: Boolean) = save { repository.motion(reduced) }
    fun setHomeLabels(show: Boolean) = save { repository.labels(show) }
    fun setContacts(include: Boolean) = save { repository.contacts(include) }
    fun setStatusColor(value: Int?) = save { repository.statusColor(value) }
    fun setPageDots(always: Boolean) = save { repository.pageDots(always) }
    fun setDots(show: Boolean) = save { repository.dots(show) }
    fun setCellularIndicator(show: Boolean) = save { repository.cellularIndicator(show) }
    fun setWeather(location: WeatherLocation) = save { repository.weather(location) }
    fun setWeatherUnit(unit: String?) = save { repository.weatherUnit(unit) }
    fun autoDetectWeather(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val location = detectCurrentWeatherLocation(context.applicationContext)
                setWeather(location)
                android.util.Log.i("AnotherLauncher", "Weather location auto-detected: ${location.name}")
            }.onFailure { e ->
                android.util.Log.w("AnotherLauncher", "Failed to auto-detect weather location", e)
            }
        }
    }
    fun completeOnboarding() = save { repository.completeOnboarding() }
    fun setCategory(id: String, category: AppCategory?) = save { repository.category(id, category) }
    fun clearCategoryOverrides() = save { repository.clearCategories() }
    fun refreshCategories(clearOverrides: Boolean = false) {
        if (clearOverrides) {
            save { repository.clearCategories() }
        }
        apps.refresh()
    }
    fun selectPage(id: String) { savedState["selected_page"] = id }
    fun addApp(app: AppEntry, page: String = selectedPage.value) = edit { HomeEditor.add(it, page, HomeItem(kind = HomeItemKind.APP, appId = app.id)) }
    fun toggleHomeApp(app: AppEntry) = edit { layout ->
        val page = layout.pages.firstOrNull { it.id == selectedPage.value } ?: layout.pages.first()
        val existing = page.items.firstOrNull { it.kind == HomeItemKind.APP && it.appId == app.id }
        if (existing == null) HomeEditor.add(layout, page.id, HomeItem(kind = HomeItemKind.APP, appId = app.id))
        else HomeEdit(HomeEditor.remove(layout, existing.id))
    }
    fun toggleFavorite(app: AppEntry) = save {
        if (repository.toggleFavorite(app.id) == null) messages.emit("Your Dock is full. Remove an app to make room.")
    }
    fun addPage() = edit { layout ->
        val page = HomePage()
        HomeEdit(layout.copy(pages = layout.pages + page), page.id)
    }
    fun deletePage(id: String) = edit { HomeEditor.deletePage(it, id) }
    fun removePage(id: String) = pageEdit("Page removed · items kept", false) { HomeEditor.removePageKeepingItems(it, id) }
    fun cleanEmptyPages() = pageEdit("Empty pages removed", false, HomeEditor::removeEmptyPages)
    fun movePage(id: String, offset: Int) = pageEdit("Page moved", true) { HomeEditor.movePage(it, id, offset) }
    fun setDefaultPage(id: String) = pageEdit("Default home page set", true) { HomeEditor.setDefaultPage(it, id) }
    private fun pageEdit(message: String, focusResult: Boolean, transform: (HomeLayout) -> HomeEdit) = save {
        var before: HomeLayout? = null
        val selected = selectedPage.value
        val result = repository.editHome { layout -> before = layout; transform(layout) }
        if (result.error != null) {
            pageUndo = null
            mutablePageNotice.value = PageNotice(++pageNoticeToken, result.error, false)
        } else if (before != result.layout) {
            pageUndo = PageUndo(before!!, result.layout, selected)
            val focus = if (!focusResult && result.layout.pages.any { it.id == selected }) selected else result.pageId
            focus?.let(::selectPage)
            mutablePageNotice.value = PageNotice(++pageNoticeToken, message, true)
        }
    }
    fun undoPages() = save {
        val undo = pageUndo ?: return@save
        val result = repository.editHome { HomeEditor.undoPageEdit(it, undo.before, undo.after) }
        pageUndo = null
        if (result.error == null) selectPage(undo.selectedPage)
        mutablePageNotice.value = PageNotice(++pageNoticeToken, result.error ?: "Page edit undone", false)
    }
    fun dismissPageNotice(token: Long) {
        if (mutablePageNotice.value?.token == token) mutablePageNotice.value = null
    }
    fun addFolder() = edit { HomeEditor.add(it, selectedPage.value, HomeItem(kind = HomeItemKind.FOLDER)) }
    fun createStack(first: String, second: String) = edit { HomeEditor.createStack(it, first, second) }
    fun unstack(id: String) = edit { HomeEditor.unstack(it, id) }
    fun removeItem(id: String, onWidgetRemoved: (Int) -> Unit = {}) = save {
        var widgetIds: List<Int> = emptyList()
        val result = repository.editHome { layout ->
            widgetIds = layout.item(id)?.widgetIds().orEmpty()
            HomeEdit(HomeEditor.remove(layout, id))
        }
        if (result.error == null) widgetIds.forEach(onWidgetRemoved)
    }
    fun moveItem(id: String, page: String) = edit { HomeEditor.moveToPage(it, id, page) }
    fun drop(app: AppEntry?, itemId: String?, page: String, column: Int, row: Int) = edit { layout ->
        val item = itemId?.let(layout::item) ?: app?.let { HomeItem(kind = HomeItemKind.APP, appId = it.id) }
        if (item == null) HomeEdit(layout) else HomeEditor.move(layout, item, page, column, row)
    }
    /**
     * Grid → dock as one persisted change. The pin is written first, so a full Dock leaves the
     * home placement untouched instead of dropping the app from both surfaces.
     */
    fun moveToDock(app: AppEntry, itemId: String, index: Int) = save {
        val ids = preferences.value.favorites
        if (app.id !in ids && ids.size >= 6) {
            messages.emit("Your dock has six favorites. Remove one to make room.")
            return@save
        }
        repository.pin(app.id, true, index)
        repository.editHome { layout -> HomeEdit(HomeEditor.remove(layout, itemId)) }.error?.let { messages.emit(it) }
    }
    /**
     * Dock → grid as one persisted change. The placement is written first, so an occupied grid
     * leaves the Dock pin untouched instead of dropping the app from both surfaces.
     */
    fun moveToGrid(app: AppEntry, page: String, column: Int, row: Int) = save {
        val result = repository.editHome { layout ->
            HomeEditor.move(layout, HomeItem(kind = HomeItemKind.APP, appId = app.id), page, column, row)
        }
        val failure = result.error
        if (failure != null) { messages.emit(failure); return@save }
        repository.remove(app.id, true)
    }
    fun updateFolder(id: String, title: String, apps: List<String>) = edit { HomeEdit(HomeEditor.folder(it, id, title, apps)) }
    fun renameFolder(id: String, title: String) = edit { layout ->
        HomeEdit(HomeEditor.folder(layout, id, title, layout.item(id)?.contents ?: emptyList()))
    }
    fun sortFolderApps(id: String) = edit { layout ->
        HomeEdit(HomeEditor.sortFolder(layout, id, apps.apps.value.associate { it.id to it.label }))
    }
    fun toggleFolderApp(id: String, appId: String) = edit { layout ->
        val folder = layout.item(id)
        if (folder == null) HomeEdit(layout) else {
            val apps = if (appId in folder.contents) folder.contents - appId else folder.contents + appId
            HomeEdit(HomeEditor.folder(layout, id, folder.title, apps))
        }
    }
    fun resize(id: String, spanX: Int, spanY: Int, column: Int? = null, row: Int? = null) = edit {
        val result = HomeEditor.resize(it, id, spanX, spanY, column, row)
        android.util.Log.d("AnotherLauncher", "RESIZE id=$id sx=$spanX sy=$spanY col=$column row=$row error=${result.error}")
        result
    }
    /**
     * Swaps a dead tile's binding for [newWidgetId] in place. The fresh binding is released
     * again when there is no room, so failed repairs never leak system widget ids.
     */
    fun replaceWidget(itemId: String, newWidgetId: Int, controller: com.anotherlauncher.widgets.WidgetController) = save {
        val home = preferences.value.home
        val targetItem = home.item(itemId)
        val oldId = targetItem?.takeIf { it.kind == HomeItemKind.WIDGET }?.widgetId
        val page = home.pageOf(itemId)?.id
        if (targetItem == null || (targetItem.kind != HomeItemKind.WIDGET_PROMPT && oldId == null) || page == null) {
            controller.delete(newWidgetId)
            messages.emit("That widget is gone.")
            return@save
        }
        val manager = AppWidgetManager.getInstance(getApplication())
        val info = manager.getAppWidgetInfo(newWidgetId)
        val spans = controller.recommendedSpans(newWidgetId, page)
        val result = try {
            repository.editHome { layout ->
                HomeEditor.replaceWidgetBinding(layout, itemId, newWidgetId,
                    info?.provider?.flattenToString(),
                    info?.let { runCatching { it.loadLabel(getApplication<Application>().packageManager).toString() }.getOrNull() },
                    spans.first, spans.second)
            }
        } catch (_: IOException) {
            controller.delete(newWidgetId)
            messages.emit("Couldn't save this change. Please try again.")
            return@save
        }
        if (result.error != null) {
            controller.delete(newWidgetId)
            messages.emit(result.error)
        } else {
            if (oldId != null) controller.delete(oldId)
            result.pageId?.let(::selectPage)
        }
    }
    /** Tiles whose system binding died (host reinstall, provider removal); layout is never pruned. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val orphanedWidgets: StateFlow<List<HomeItem>> = preferences.mapLatest { prefs ->
        val manager = AppWidgetManager.getInstance(getApplication())
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            HomeEditor.orphanedWidgets(prefs.home) { id -> manager.getAppWidgetInfo(id) != null }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Session-scoped: the orphan notice returns next launch while tiles are still dead. */
    var orphanNoticeDismissed by mutableStateOf(false)
    fun addWidget(
        id: Int,
        page: String,
        controller: com.anotherlauncher.widgets.WidgetController,
        initialSpans: Pair<Int, Int>? = null,
    ) = edit(onError = { controller.delete(id) }) { layout ->
        val info = AppWidgetManager.getInstance(getApplication()).getAppWidgetInfo(id)
        val density = getApplication<Application>().resources.displayMetrics.density
        val spanX = initialSpans?.first ?: (info?.targetCellWidth?.takeIf { it > 0 }
            ?: kotlin.math.ceil((info?.minWidth ?: 160) / density / 72f).toInt()).coerceIn(1, 4)
        val spanY = initialSpans?.second ?: (info?.targetCellHeight?.takeIf { it > 0 }
            ?: kotlin.math.ceil((info?.minHeight ?: 160) / density / 88f).toInt()).coerceIn(1, 6)
        if (layout.allItems().any { id in it.widgetIds() }) HomeEdit(layout)
        else HomeEditor.add(layout, page, HomeItem(id = "widget-$id", kind = HomeItemKind.WIDGET, widgetId = id, spanX = spanX, spanY = spanY,
            widgetProvider = info?.provider?.flattenToString(),
            widgetLabel = info?.let { runCatching { it.loadLabel(getApplication<Application>().packageManager).toString() }.getOrNull() }))
    }
    private fun edit(onError: (() -> Unit)? = null, transform: (HomeLayout) -> HomeEdit) = save {
        val result = try {
            repository.editHome(transform)
        } catch (error: IOException) {
            onError?.invoke()
            throw error
        }
        result.error?.let { onError?.invoke(); messages.emit(it) }
        result.pageId?.let(::selectPage)
    }
    private fun save(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (_: IOException) { messages.emit("Couldn't save this change. Please try again.") }
    }
    fun exportBackup(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val resolver = getApplication<Application>().contentResolver
            val stream = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
                ?: resolver.openOutputStream(uri)
                ?: throw IOException("Unable to open output stream")
            stream.use { writeBackupToStream(it) }
            messages.emit("Backup exported successfully.")
        } catch (e: Exception) {
            Log.e("LauncherViewModel", "Failed to export backup", e)
            messages.emit("Failed to export backup.")
        }
    }

    fun exportBackup(stream: java.io.OutputStream) = viewModelScope.launch(Dispatchers.IO) {
        try {
            stream.use { writeBackupToStream(it) }
            messages.emit("Backup exported successfully.")
        } catch (e: Exception) {
            Log.e("LauncherViewModel", "Failed to export backup", e)
            messages.emit("Failed to export backup.")
        }
    }

    private suspend fun writeBackupToStream(stream: java.io.OutputStream) {
        val pInfo = runCatching {
            getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0)
        }.getOrNull()
        val versionName = pInfo?.versionName ?: "0.7.0-alpha"
        val backup = repository.createBackup(versionName)
        val json = BackupCodec.encode(backup)
        stream.bufferedWriter().use { writer ->
            writer.write(json)
            writer.flush()
        }
    }

    fun importBackup(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val resolver = getApplication<Application>().contentResolver
            val stream = resolver.openInputStream(uri)
                ?: throw IOException("Unable to open input stream")
            val backup = stream.use { readBackupFromStream(it) }
            repository.restoreBackup(backup)
            selectPage(backup.home.resolvedDefaultPageId())
            messages.emit("Backup restored successfully.")
        } catch (e: IllegalArgumentException) {
            Log.e("LauncherViewModel", "Invalid backup", e)
            messages.emit("Invalid backup: ${e.message}")
        } catch (e: org.json.JSONException) {
            Log.e("LauncherViewModel", "Corrupted backup file", e)
            messages.emit("Corrupted backup file.")
        } catch (e: Exception) {
            Log.e("LauncherViewModel", "Couldn't restore backup", e)
            messages.emit("Couldn't restore backup. Current layout was preserved.")
        }
    }

    fun importBackup(stream: java.io.InputStream) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val backup = stream.use { readBackupFromStream(it) }
            repository.restoreBackup(backup)
            selectPage(backup.home.resolvedDefaultPageId())
            messages.emit("Backup restored successfully.")
        } catch (e: IllegalArgumentException) {
            Log.e("LauncherViewModel", "Invalid backup", e)
            messages.emit("Invalid backup: ${e.message}")
        } catch (e: org.json.JSONException) {
            Log.e("LauncherViewModel", "Corrupted backup file", e)
            messages.emit("Corrupted backup file.")
        } catch (e: Exception) {
            Log.e("LauncherViewModel", "Couldn't restore backup", e)
            messages.emit("Couldn't restore backup. Current layout was preserved.")
        }
    }

    private fun readBackupFromStream(stream: java.io.InputStream): com.anotherlauncher.data.LauncherBackup {
        val json = stream.bufferedReader().use { it.readText() }
        return BackupCodec.decode(json)
    }

    override fun onCleared() { apps.close() }
}
