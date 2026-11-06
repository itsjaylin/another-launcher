package com.anotherlauncher

import android.Manifest
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import com.anotherlauncher.duo.DisplaySurface
import androidx.activity.compose.setContent
import androidx.compose.animation.togetherWith
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.anotherlauncher.duo.FoldStateTracker
import com.anotherlauncher.duo.StatusMonitor
import com.anotherlauncher.duo.WallpaperToneTracker
import com.anotherlauncher.duo.WallpaperContrast
import com.anotherlauncher.ui.AnotherLauncherTheme
import com.anotherlauncher.ui.HomeScreen
import com.anotherlauncher.widgets.WidgetController
import kotlinx.coroutines.launch

class LauncherActivity : ComponentActivity() {
    private val model: LauncherViewModel by viewModels()
    private lateinit var foldTracker: FoldStateTracker
    private lateinit var statusMonitor: StatusMonitor
    private lateinit var widgets: WidgetController
    private lateinit var wallpaperTracker: WallpaperToneTracker
    private var transitionWakeLock: PowerManager.WakeLock? = null
    private val foldWakeHandler = Handler(Looper.getMainLooper())
    private var darkNavigationIcons = false
    private var fullPageShown by mutableStateOf(false)
    private var homeRequest by mutableIntStateOf(0)
    private var showSettingsRequest by mutableIntStateOf(0)
    private val homeRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private val uninstall = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { model.apps.refresh() }
    private val signalPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        statusMonitor.refreshPhonePermission()
        if (!granted) message("Cellular signal will stay unavailable. The launcher still works normally.")
    }
    private val contactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.setContacts(granted)
        if (!granted) message("Contacts stay out of search. The launcher still works normally.")
    }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) {
            model.autoDetectWeather(applicationContext)
        }
    }

    private fun checkAndAutoPullWeatherLocation(currentLocation: com.anotherlauncher.data.WeatherLocation?) {
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPermission && currentLocation == null) {
            model.autoDetectWeather(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
        }
        if (intent?.action == ACTION_SHOW_SETTINGS || intent?.getBooleanExtra(EXTRA_SHOW_SETTINGS, false) == true) {
            showSettingsRequest++
        }
        intent?.let { GestureNavContract.fromIntent(it)?.sendEndPosition() }
        enableEdgeToEdge()
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        configureSystemBars()
        foldTracker = FoldStateTracker(this).apply {
            onFoldClosing = { wakeCoverScreen("foldClosing") }
            onInnerToCoverHandoff = { wakeCoverScreen("innerToCoverHandoff") }
        }
        statusMonitor = StatusMonitor(this)
        wallpaperTracker = WallpaperToneTracker(this)
        com.anotherlauncher.data.Installs.start(this)
        com.anotherlauncher.data.NewApps.load(this)
        com.anotherlauncher.data.RecentApps.load(this)
        widgets = WidgetController(this, savedInstanceState,
            { id, page, replace ->
                if (replace != null) model.replaceWidget(replace, id, widgets)
                else model.addWidget(id, page, widgets, widgets.recommendedSpans(id, page))
            }, ::message)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.messages.collect(::message) }
            }
        }
        setContent {
            val fold by foldTracker.state.collectAsStateWithLifecycle()
            val status by statusMonitor.state.collectAsStateWithLifecycle()
            val colors by wallpaperTracker.colors.collectAsStateWithLifecycle()
            val prefs by model.preferences.collectAsStateWithLifecycle()
            val dark = isSystemInDarkTheme()
            val homeDarkText = WallpaperContrast.useDarkText(colors, !dark)
            val installSessions by com.anotherlauncher.data.Installs.active.collectAsStateWithLifecycle()
            val installProgressMap = remember(installSessions) { installSessions.values.associate { it.packageName to it.progress } }
            val newAppsSet by com.anotherlauncher.data.NewApps.packages.collectAsStateWithLifecycle()
            
            val lightIcons = if (!prefs.onboardingCompleted || fullPageShown) !dark else homeDarkText
            LaunchedEffect(lightIcons) {
                darkNavigationIcons = lightIcons
                configureSystemBars()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                LaunchedEffect(fullPageShown) {
                    if (fullPageShown) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        window.attributes = window.attributes.apply {
                            blurBehindRadius = (36 * resources.displayMetrics.density).toInt()
                        }
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        window.attributes = window.attributes.apply {
                            blurBehindRadius = 0
                        }
                    }
                }
            }
            LaunchedEffect(prefs.initialized) {
                if (prefs.initialized) {
                    checkAndAutoPullWeatherLocation(prefs.weatherLocation)
                }
            }

            AnotherLauncherTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.anotherlauncher.data.LocalInstallProgress provides installProgressMap,
                    com.anotherlauncher.data.LocalNewApps provides newAppsSet
                ) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = prefs.onboardingCompleted,
                        transitionSpec = {
                            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(400)) togetherWith
                                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(400))
                        },
                        label = "onboarding_transition"
                    ) { completed ->
                        if (!completed) {
                            com.anotherlauncher.ui.OnboardingScreen(
                                model = model,
                                fold = fold,
                                onComplete = { model.completeOnboarding() }
                            )
                        } else {
                            HomeScreen(model, fold, status, widgets, homeRequest, homeDarkText,
                                showSettingsRequest = showSettingsRequest,
                                onFullPageChanged = { fullPageShown = it },
                                onDefaultHome = ::requestHomeRole,
                                onSignalPermission = { signalPermission.launch(Manifest.permission.READ_PHONE_STATE) },
                                onContactsPermission = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) },
                                onNotificationAccess = {
                                    try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                    catch (_: android.content.ActivityNotFoundException) { message("Open system Settings to allow notification access.") }
                                },
                                onWallpaperSettings = {
                        // Samsung Dressroom entrypoints and standard system wallpaper picker actions:
                        // 1. Samsung "Wallpaper and style" public action (unscoped)
                        // 2. Standard Android SET_WALLPAPER action (opens Dressroom on Samsung, Wallpaper picker on Pixel & standard OEMs)
                        // 3. Samsung SET_AS_WALLPAPER action
                        // 4. Samsung Dressroom explicit package action
                        // 5. Samsung Dressroom explicit WallpaperSettingActivity component
                        // 6. Samsung Dressroom WALLPAPER_LIBRARY action
                        // 7. System live wallpaper chooser
                        val wallpaperIntents = listOf(
                            Intent().setComponent(
                                ComponentName(
                                    "com.samsung.android.app.dressroom",
                                    "com.samsung.android.app.dressroom.presentation.settings.WallpaperSettingActivity"
                                )
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            Intent("com.samsung.intent.action.WALLPAPER_SETTING")
                                .setPackage("com.samsung.android.app.dressroom")
                                .addCategory(Intent.CATEGORY_DEFAULT)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            Intent("com.samsung.intent.action.WALLPAPER_SETTING")
                                .addCategory(Intent.CATEGORY_DEFAULT)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            Intent(Intent.ACTION_SET_WALLPAPER)
                                .addCategory(Intent.CATEGORY_DEFAULT)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            Intent("com.samsung.android.intent.action.SET_AS_WALLPAPER")
                                .addCategory(Intent.CATEGORY_DEFAULT)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            Intent("com.samsung.dressroom.intent.action.WALLPAPER_LIBRARY")
                                .setPackage("com.samsung.android.app.dressroom")
                                .addCategory(Intent.CATEGORY_DEFAULT)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        var opened = false
                        for (intent in wallpaperIntents) {
                            try {
                                startActivity(intent)
                                opened = true
                                break
                            } catch (_: android.content.ActivityNotFoundException) {
                            } catch (_: SecurityException) {
                            }
                        }
                        if (!opened) {
                            // Direct user to system Settings to access Wallpaper & style if no wallpaper picker handled the intent.
                            try {
                                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                message("Open Wallpaper and style in system Settings.")
                            } catch (_: Exception) {
                                message("Open Wallpaper and style in system Settings.")
                            }
                        }
                    },
                    onUninstall = { app ->
                        try { uninstall.launch(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.info.componentName.packageName, null))) }
                        catch (_: android.content.ActivityNotFoundException) { message("Android couldn't open uninstall for this app.") }
                        catch (_: SecurityException) { message("This app can't be uninstalled here.") }
                    })
                        }
                    }
                }
            }
        }
    }
    private fun configureSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = darkNavigationIcons
            isAppearanceLightNavigationBars = darkNavigationIcons
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeCoverScreen(reason: String) {
        Log.i("AnotherLauncher", "wakeCoverScreen: $reason")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
            val power = getSystemService(PowerManager::class.java)
            if (transitionWakeLock == null) {
                transitionWakeLock = power.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                            PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            PowerManager.ON_AFTER_RELEASE,
                    "anotherlauncher:cover_handoff"
                ).apply { setReferenceCounted(false) }
            }
            transitionWakeLock?.acquire(3500L)
        } catch (e: Exception) {
            Log.w("AnotherLauncher", "Failed to acquire wake lock for cover transition", e)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i("AnotherLauncher", "onResume")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
            setShowWhenLocked(false)
        }
        val hasLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasLocationPermission && model.preferences.value.weatherLocation == null) {
            model.autoDetectWeather(applicationContext)
        }
        foldTracker.clearFoldClosing()
        foldWakeHandler.postDelayed({
            try {
                if (transitionWakeLock?.isHeld == true) {
                    transitionWakeLock?.release()
                }
            } catch (_: Exception) {}
        }, 600L)
        configureSystemBars()
        dismissKeyboard()
    }

    override fun onPause() {
        Log.i("AnotherLauncher", "onPause")
        dismissKeyboard()
        if (foldTracker.wasRecentlyClosingFromUnfolded()) {
            wakeCoverScreen("onPauseFoldClose")
        }
        super.onPause()
    }

    override fun onStop() {
        Log.i("AnotherLauncher", "onStop")
        if (foldTracker.wasRecentlyClosingFromUnfolded()) {
            wakeCoverScreen("onStopFoldClose")
            foldWakeHandler.postDelayed({
                if (foldTracker.wasRecentlyClosingFromUnfolded()) {
                    wakeCoverScreen("onStopRetry")
                    try {
                        val homeIntent = Intent(this, LauncherActivity::class.java).apply {
                            action = Intent.ACTION_MAIN
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(homeIntent)
                    } catch (e: Exception) {
                        Log.w("AnotherLauncher", "Failed to restart home on cover", e)
                    }
                }
            }, 150L)
        }
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        Log.i("AnotherLauncher", "onConfigurationChanged")
        val wasInner = foldTracker.state.value.surface == DisplaySurface.INNER
        super.onConfigurationChanged(newConfig)
        foldTracker.refreshWindowMetrics()
        val newSurface = foldTracker.state.value.surface
        if (wasInner && newSurface == DisplaySurface.COVER) {
            wakeCoverScreen("configChangedHandoff")
        }
        configureSystemBars()
    }

    override fun onDestroy() {
        foldWakeHandler.removeCallbacksAndMessages(null)
        try {
            if (transitionWakeLock?.isHeld == true) {
                transitionWakeLock?.release()
            }
        } catch (_: Exception) {}
        transitionWakeLock = null
        super.onDestroy()
    }
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        Log.i("AnotherLauncher", "onMultiWindowModeChanged");
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        foldTracker.refreshWindowMetrics()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dismissKeyboard()
        GestureNavContract.fromIntent(intent)?.sendEndPosition()
        if (intent.action == ACTION_SHOW_SETTINGS || intent.getBooleanExtra(EXTRA_SHOW_SETTINGS, false)) {
            showSettingsRequest++
        } else if (intent.action == Intent.ACTION_MAIN || intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeRequest++
        }
    }

    private fun dismissKeyboard() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
            currentFocus?.clearFocus()
        } catch (_: Exception) {}
    }
    override fun onSaveInstanceState(outState: Bundle) {
        widgets.save(outState)
        super.onSaveInstanceState(outState)
    }
    @Deprecated("AppWidgetHost configuration uses the platform result callback")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == WidgetController.CONFIGURE_REQUEST) widgets.configurationResult(resultCode)
    }
    private fun requestHomeRole() {
        val roles = getSystemService(RoleManager::class.java)
        if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            homeRole.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
        } else if (roles.isRoleHeld(RoleManager.ROLE_HOME)) message("Another Launcher is already your home.")
        else startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun message(text: String) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }

    companion object {
        const val ACTION_SHOW_SETTINGS = "com.anotherlauncher.action.SHOW_SETTINGS"
        const val EXTRA_SHOW_SETTINGS = "com.anotherlauncher.extra.SHOW_SETTINGS"
    }
}
