package com.anotherlauncher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WidgetProvider(val key: String, val appName: String, val label: String, val info: AppWidgetProviderInfo)

object WidgetCatalog {
    /** Labels are not identity: distinct providers and user profiles must retain their variants. */
    fun <T> unique(items: List<T>, identity: (T) -> String): List<T> = items.distinctBy(identity)
}

/** Our catalog deduplicates entries; Android owns binding consent and provider configuration. */
class WidgetController(
    private val activity: ComponentActivity,
    savedState: Bundle?,
    private val onAdded: (newWidgetId: Int, pageId: String, replaceItemId: String?) -> Unit,
    private val onError: (String) -> Unit,
) : DefaultLifecycleObserver {
    val host = object : AppWidgetHost(activity, HOST_ID) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo): AppWidgetHostView = LauncherWidgetHostView(context)
    }
    val manager = AppWidgetManager.getInstance(activity)
    val displayedSizes = mutableMapOf<Int, android.util.SizeF>()
    private val grids = mutableMapOf<String, android.util.SizeF>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var listening = false
    private val startListening = Runnable {
        if (activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) && !listening) {
            host.startListening()
            listening = true
        }
    }
    fun reportGrid(page: String, cellWidth: Float, cellHeight: Float) { grids[page] = android.util.SizeF(cellWidth, cellHeight) }

    fun requirements(info: AppWidgetProviderInfo): WidgetRequirements {
        val density = activity.resources.displayMetrics.density
        val horizontal = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
        val vertical = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        val width = info.minWidth / density
        val height = info.minHeight / density
        return WidgetRequirements(width, height,
            if (horizontal && info.minResizeWidth > 0) info.minResizeWidth / density else width,
            if (vertical && info.minResizeHeight > 0) info.minResizeHeight / density else height,
            horizontal, vertical, info.maxResizeWidth / density, info.maxResizeHeight / density)
    }
    fun recommendedSpans(id: Int, page: String): Pair<Int, Int> {
        val info = manager.getAppWidgetInfo(id) ?: return 2 to 2
        val grid = grids[page] ?: android.util.SizeF(72f, 88f)
        return WidgetSizing.initialSpans(requirements(info), info.targetCellWidth, info.targetCellHeight, grid.width, grid.height)
    }
    fun createView(context: Context, id: Int, info: AppWidgetProviderInfo, spanX: Int, spanY: Int): FittingWidgetFrame {
        val view = host.createView(context, id, info) as LauncherWidgetHostView
        view.spanX = spanX; view.spanY = spanY
        return FittingWidgetFrame(context, view).also { it.requirements = requirements(info) }
    }
    private val restoredLegacySetup = savedState != null && !savedState.containsKey("pending_widget_configuring")
    private val setup = WidgetSetupFlow(restoreSetup(savedState))
    private val pendingId: Int get() = setup.pending?.id ?: AppWidgetManager.INVALID_APPWIDGET_ID
    private val bindResult = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // A late bind result must never finish an unrelated configuration transaction.
        if (setup.state is WidgetSetupState.Binding) {
            if (result.resultCode == Activity.RESULT_OK && manager.getAppWidgetInfo(pendingId) != null) configurePending()
            else cancelPending()
        }
    }
    init { activity.lifecycle.addObserver(this) }

    suspend fun catalog(): List<WidgetProvider> = withContext(Dispatchers.IO) {
        val providers = manager.getInstalledProvidersForProfile(Process.myUserHandle())
            .filter { WidgetSizing.isHomeProvider(it.widgetCategory) }.map { info ->
            val app = info.provider.packageName
            val name = try { activity.packageManager.getApplicationLabel(activity.packageManager.getApplicationInfo(app, 0)).toString() }
                catch (_: android.content.pm.PackageManager.NameNotFoundException) { app }
            WidgetProvider("${info.profile}:${info.provider.flattenToString()}", name, info.loadLabel(activity.packageManager), info)
        }
        WidgetCatalog.unique(providers) { it.key }.sortedWith(compareBy<WidgetProvider> { it.appName.lowercase() }.thenBy { it.label.lowercase() }.thenBy { it.key })
    }

    /** Finds an installed provider by flattened component for automatic dead-tile repair. */
    suspend fun findProvider(flattened: String): AppWidgetProviderInfo? = withContext(Dispatchers.IO) {
        runCatching {
            manager.getInstalledProvidersForProfile(Process.myUserHandle())
                .firstOrNull { WidgetSizing.isHomeProvider(it.widgetCategory) && it.provider.flattenToString() == flattened }
        }.getOrNull()
    }

    fun pick(info: AppWidgetProviderInfo, pageId: String) {
        if (!setup.idle) return
        if (!WidgetSizing.isHomeProvider(info.widgetCategory)) { onError("This provider is not a home-screen widget."); return }
        beginBind(info, pageId)
    }

    /** Rebinds a dead tile in place; the ViewModel swaps the fresh id onto the existing item. */
    fun pickForReplace(info: AppWidgetProviderInfo, pageId: String, itemId: String) {
        if (!setup.idle) return
        if (!WidgetSizing.isHomeProvider(info.widgetCategory)) { onError("This provider is not a home-screen widget."); return }
        beginBind(info, pageId, itemId)
    }

    private fun beginBind(info: AppWidgetProviderInfo, pageId: String, replaceItemId: String? = null) {
        if (!setup.idle) return
        setup.begin(PendingWidget(host.allocateAppWidgetId(), pageId, replaceItemId))
        try {
            val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) }
            if (manager.bindAppWidgetIdIfAllowed(pendingId, info.profile, info.provider, options)) configurePending()
            else bindResult.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options))
        } catch (_: ActivityNotFoundException) { cancelPending(); onError("Android couldn't open this widget's setup.") }
        catch (_: SecurityException) { cancelPending(); onError("This widget couldn't be bound.") }
    }

    private fun configurePending() {
        if (setup.state !is WidgetSetupState.Binding) return
        val info = manager.getAppWidgetInfo(pendingId)
        if (info == null) { cancelPending(); return }
        val optional = info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (info.configure == null || optional) { finishPending(); return }
        setup.configure()
        try { host.startAppWidgetConfigureActivityForResult(activity, pendingId, 0, CONFIGURE_REQUEST, null) }
        catch (_: ActivityNotFoundException) { cancelPending(); onError("The widget's setup screen is unavailable.") }
        catch (_: SecurityException) { cancelPending(); onError("The widget's setup screen couldn't be opened.") }
    }
    fun canConfigure(id: Int): Boolean = manager.getAppWidgetInfo(id)?.let {
        it.configure != null && it.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
    } == true
    fun configureExisting(id: Int) {
        if (!setup.idle || !canConfigure(id)) return
        setup.reconfigure(id)
        try { host.startAppWidgetConfigureActivityForResult(activity, id, 0, CONFIGURE_REQUEST, null) }
        catch (_: RuntimeException) { setup.cancel(); onError("Widget settings are unavailable.") }
    }
    fun configurationResult(result: Int) {
        when (setup.state) {
            is WidgetSetupState.Reconfiguring -> setup.finish()
            is WidgetSetupState.Configuring -> if (result == Activity.RESULT_OK) finishPending() else cancelPending()
            is WidgetSetupState.Binding -> {
                // Compatibility with saved instances from before setup phases were recorded.
                if (restoredLegacySetup && manager.getAppWidgetInfo(pendingId) != null) {
                    if (result == Activity.RESULT_OK) finishPending() else cancelPending()
                }
            }
            else -> Unit // Ignore results when no configuration was launched.
        }
    }
    private fun finishPending() {
        val widget = setup.pending ?: return
        if (manager.getAppWidgetInfo(widget.id) == null) { cancelPending(); return }
        setup.finish()
        onAdded(widget.id, widget.pageId, widget.replaceItemId)
    }
    private fun cancelPending() {
        setup.cancel()?.let { host.deleteAppWidgetId(it.id) }
    }
    fun save(bundle: Bundle) {
        val pending = setup.pending
        bundle.putInt("pending_widget", pending?.id ?: AppWidgetManager.INVALID_APPWIDGET_ID)
        bundle.putString("pending_widget_page", pending?.pageId)
        bundle.putString("pending_replace_item", pending?.replaceItemId)
        bundle.putBoolean("pending_widget_configuring", setup.state is WidgetSetupState.Configuring)
        bundle.putInt("existing_widget_config", (setup.state as? WidgetSetupState.Reconfiguring)?.id
            ?: AppWidgetManager.INVALID_APPWIDGET_ID)
    }
    fun delete(id: Int) { displayedSizes.remove(id); host.deleteAppWidgetId(id) }
    override fun onStart(owner: LifecycleOwner) {
        // RemoteViews delivery can do substantial binder/inflate work. Let the launcher draw its
        // return-to-Home frame before asking every provider for updates.
        mainHandler.removeCallbacks(startListening)
        mainHandler.postDelayed(startListening, WIDGET_RESUME_DELAY_MS)
    }
    override fun onStop(owner: LifecycleOwner) {
        mainHandler.removeCallbacks(startListening)
        if (listening) {
            host.stopListening()
            listening = false
        }
    }
    companion object {
        private fun restoreSetup(bundle: Bundle?): WidgetSetupState {
            val existing = bundle?.getInt("existing_widget_config", AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
            val pending = bundle?.getInt("pending_widget", AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
            if (pending != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val widget = PendingWidget(pending, bundle?.getString("pending_widget_page") ?: "home",
                    bundle?.getString("pending_replace_item"))
                // Older saved instances did not record the phase. A resolved provider means
                // binding already succeeded; the controller resolves this legacy case below.
                return if (bundle?.getBoolean("pending_widget_configuring") == true) WidgetSetupState.Configuring(widget)
                else WidgetSetupState.Binding(widget)
            }
            return if (existing != AppWidgetManager.INVALID_APPWIDGET_ID) WidgetSetupState.Reconfiguring(existing)
            else WidgetSetupState.Idle
        }
        const val HOST_ID = 8128
        const val CONFIGURE_REQUEST = 8129
        private const val WIDGET_RESUME_DELAY_MS = 180L
    }
}
