package com.anotherlauncher.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.CancellationSignal
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.anotherlauncher.LauncherViewModel
import com.anotherlauncher.data.*
import com.anotherlauncher.duo.FoldState
import com.anotherlauncher.duo.notificationDotsGranted
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.IOException
import kotlin.coroutines.resume

@Composable
fun LauncherSettings(
    prefs: LauncherPreferences,
    fold: FoldState,
    model: LauncherViewModel,
    apps: List<AppEntry>,
    onDismiss: () -> Unit,
    onDefaultHome: () -> Unit,
    onSignalPermission: () -> Unit,
    onWallpaperSettings: () -> Unit,
    onPages: () -> Unit,
    onContactsPermission: () -> Unit = {},
    onNotificationAccess: () -> Unit = {}
) {
    var settingQuery by rememberSaveable { mutableStateOf("") }
    fun matches(words: String) = settingQuery.trim().split(' ').all { words.contains(it, ignoreCase = true) }
    val context = LocalContext.current

    var resumeTrigger by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val dotsGranted = remember(prefs.showDots, apps, resumeTrigger) { context.notificationDotsGranted() }
    val signalGranted = remember(resumeTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }
    val contactsGranted = remember(resumeTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
    var showTour by rememberSaveable { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    if (showTour) {
        OnboardingScreen(
            model = model,
            fold = fold,
            onComplete = { showTour = false },
            isTourMode = true,
            onCloseTour = { showTour = false }
        )
    } else {
        BackHandler(onBack = {
            if (showPrivacyPolicy) {
                showPrivacyPolicy = false
            } else if (showAboutDialog) {
                showAboutDialog = false
            } else {
                onDismiss()
            }
        })
        FullPageSurface("Settings", onDismiss) {
        // Frosted Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .glass(24, frosted = true)
        ) {
            TextField(
                value = settingQuery,
                onValueChange = { settingQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Find a setting") },
                singleLine = true,
                leadingIcon = { GlyphIcon(Glyph.SEARCH, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (settingQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { settingQuery = "" },
                            Modifier.semantics { contentDescription = "Clear settings search" }
                        ) { GlyphIcon(Glyph.CLOSE) }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                )
            )
        }

        val showHome = matches("home pages icons labels show app names page indicator dots notifications badges")
        val showAppearance = matches("appearance android system theme wallpaper light dark reduce motion animations")
        val showDock = matches("dock edge favorites side left right bottom placement wider inner screen layout")
        val showStatus = matches("status time battery color signal indicator tint bar duo pill")
        val showWeather = matches("weather forecast location city temperature celsius fahrenheit unit")
        val showLibrary = matches("app library alphabetical az categories organize apps contacts people")
        val showSearch = matches("search web provider engine google duckduckgo brave startpage")
        val showDevice = matches("default home launcher permissions cellular signal device system role")
        val showBackup = matches("backup restore export import transfer layout save reset data")
        val showHelp = matches("help tour onboarding welcome guide gestures tutorial tips")
        val showAbout = matches("about privacy policy legal terms data permissions safety play store credits open source license version")

        val hasMatches = showHome || showAppearance || showDock || showStatus || showWeather || showLibrary || showSearch || showDevice || showBackup || showHelp || showAbout

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!hasMatches) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No settings found for \"$settingQuery\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1. HOME SCREEN SECTION
            if (showHome) {
                SettingGroup(
                    title = "Home screen",
                    footer = "Customize icon labels, page indicators, and notification dots."
                ) {
                    SettingSwitchRow(
                        title = "Show app names",
                        summary = "Display text labels below home screen icons.",
                        checked = prefs.showHomeLabels,
                        onCheckedChange = model::setHomeLabels,
                        icon = Glyph.APPS
                    )
                    SettingDivider()
                    SettingSwitchRow(
                        title = "Always show page dots",
                        summary = "Keep the page indicator visible even with a single home page.",
                        checked = prefs.alwaysShowPageDots,
                        onCheckedChange = model::setPageDots,
                        icon = Glyph.PAGES
                    )
                    SettingDivider()
                    SettingSwitchRow(
                        title = "Notification dots",
                        summary = PermissionExplanation.NOTIFICATIONS,
                        checked = prefs.showDots,
                        onCheckedChange = { enabled ->
                            model.setDots(enabled)
                            if (enabled && !dotsGranted) {
                                onNotificationAccess()
                            }
                        },
                        icon = Glyph.BELL
                    )
                    if (prefs.showDots && !dotsGranted) {
                        SettingDivider()
                        SettingActionRow(
                            title = "Notification access needed",
                            buttonText = "Grant",
                            onAction = { onNotificationAccess() },
                            summary = PermissionExplanation.NOTIFICATIONS,
                            statusText = "Action needed",
                            isStatusPositive = false,
                            icon = Glyph.BELL,
                            iconTint = MaterialTheme.colorScheme.error
                        )
                    }
                    SettingDivider()
                    SettingNavigationRow(
                        title = "Home pages",
                        summary = "Manage, reorder, or set your default home page.",
                        valueText = "${prefs.home.pages.size} pages",
                        onClick = onPages,
                        icon = Glyph.PAGES
                    )
                }
            }

            // 2. APPEARANCE SECTION
            if (showAppearance) {
                SettingGroup(
                    title = "Appearance",
                    footer = "Visual style follows your Android system theme and wallpaper palette."
                ) {
                    SettingNavigationRow(
                        title = "Wallpaper and style",
                        summary = "Open system wallpaper, colors, and themes.",
                        onClick = onWallpaperSettings,
                        icon = Glyph.WALLPAPER
                    )
                    SettingDivider()
                    SettingSwitchRow(
                        title = "Reduce motion",
                        summary = "Use simpler transitions and softer animations.",
                        checked = prefs.reducedMotion,
                        onCheckedChange = model::setMotion,
                        icon = Glyph.EDIT
                    )
                }
            }

            // 3. DOCK EDGE SECTION
            if (showDock) {
                SettingGroup(
                    title = "Dock",
                    footer = "Positions the dock along the left or right edge. Sits at the bottom when unfolded in portrait."
                ) {
                    SettingSwitchRow(
                        title = "Wider inner-screen layout",
                        summary = "Use a side dock and a wider, edge-aligned home grid when unfolded in portrait.",
                        checked = prefs.innerSideLayout,
                        onCheckedChange = model::setInnerSideLayout,
                        icon = Glyph.PAGES
                    )
                    SettingDivider()
                    SettingSegmentedRow(
                        title = "Dock position",
                        summary = "Choose the screen edge for favorite pinned apps.",
                        options = DockSide.entries,
                        selected = prefs.dockSide,
                        onSelect = model::setSide,
                        labelProvider = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                        icon = Glyph.APPS
                    )
                }
            }

            // 4. STATUS BAR & DUO PILL TINT SECTION (Interactive Color Picker)
            if (showStatus) {
                SettingGroup(
                    title = "Status bar and pill",
                    footer = "Dynamic color adapts to your wallpaper, or choose a custom tint for the time and battery pill."
                ) {
                    SettingColorPickerRow(
                        title = "Status color tint",
                        summary = if (prefs.statusColor == null) "Dynamic (adapts to wallpaper)" else "Custom color ${ColorPickerDefaults.formatHex(prefs.statusColor)}",
                        currentColor = prefs.statusColor,
                        onColorSelected = model::setStatusColor,
                        icon = Glyph.PALETTE
                    )
                }
            }

            // 5. WEATHER SECTION
            if (showWeather) {
                SettingGroup(
                    title = "Weather",
                    footer = "Forecasts and city geocoding powered by Open-Meteo."
                ) {
                    WeatherSettings(
                        location = prefs.weatherLocation,
                        weatherUnit = prefs.weatherUnit,
                        model = model
                    )
                }
            }

            // 6. APP LIBRARY SECTION
            if (showLibrary) {
                var refreshNotice by remember { mutableStateOf<String?>(null) }
                val coroutineScope = rememberCoroutineScope()

                SettingGroup(
                    title = "App library",
                    footer = "Include contacts in search and customize app category folders."
                ) {
                    SettingSwitchRow(
                        title = "Alphabetical App Library",
                        summary = "Keep the A–Z view when folding, reopening the library, or restarting.",
                        checked = prefs.libraryAz,
                        onCheckedChange = model::setLibraryAz,
                        icon = Glyph.APPS
                    )
                    SettingDivider()
                    SettingSwitchRow(
                        title = "Include contacts in search",
                        summary = PermissionExplanation.CONTACTS,
                        checked = prefs.includeContacts && contactsGranted,
                        onCheckedChange = { enabled ->
                            if (enabled && !contactsGranted) onContactsPermission()
                            else model.setContacts(enabled)
                        },
                        icon = Glyph.CONTACT
                    )
                    SettingDivider()
                    SettingActionRow(
                        title = "Refresh categories",
                        summary = "Re-sort installed apps using smart category rules.",
                        buttonText = "Refresh",
                        statusText = refreshNotice,
                        isStatusPositive = true,
                        onAction = {
                            model.refreshCategories()
                            refreshNotice = "Categories refreshed"
                            coroutineScope.launch {
                                delay(2500)
                                refreshNotice = null
                            }
                        },
                        icon = Glyph.APPS
                    )
                    SettingDivider()
                    CategorySettings(apps, prefs.categoryOverrides, model)
                }
            }

            // 7. WEB SEARCH SECTION
            if (showSearch) {
                SettingGroup(
                    title = "Web search",
                    footer = "App, contact, and Settings results remain local to the launcher."
                ) {
                    SettingSegmentedRow(
                        title = "Search provider: ${prefs.searchProvider.label}",
                        summary = "Used for web searches from the App Library.",
                        options = SearchProvider.entries,
                        selected = prefs.searchProvider,
                        onSelect = model::setSearchProvider,
                        labelProvider = { provider -> when (provider) {
                            SearchProvider.DUCKDUCKGO -> "DDG"
                            SearchProvider.STARTPAGE -> "Start"
                            else -> provider.label
                        } },
                        icon = Glyph.SEARCH
                    )
                }
            }

            // 8. DEVICE INTEGRATION SECTION
            if (showDevice) {
                SettingGroup(
                    title = "Device integration",
                    footer = "System home role and cellular telemetry."
                ) {
                    SettingActionRow(
                        title = "Default home app",
                        summary = "Set Another Launcher as your primary home screen.",
                        buttonText = "Configure",
                        onAction = onDefaultHome,
                        icon = Glyph.HOME
                    )
                    SettingDivider()
                    SettingSwitchRow(
                        title = "Cellular signal indicator",
                        summary = when {
                            !signalGranted -> "Permission needed. ${PermissionExplanation.PHONE_STATE}"
                            !prefs.showCellularIndicator -> "Disabled · Mobile signal hidden from the status pill."
                            else -> PermissionExplanation.PHONE_STATE
                        },
                        checked = signalGranted && prefs.showCellularIndicator,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                model.setCellularIndicator(true)
                                if (!signalGranted) {
                                    onSignalPermission()
                                }
                            } else {
                                model.setCellularIndicator(false)
                            }
                        },
                        icon = Glyph.SIGNAL
                    )
                }
            }

            // 8. BACKUP & RESTORE SECTION
            if (showBackup) {
                SettingGroup(
                    title = "Backup and restore",
                    footer = "Save your layout, pinned favorites, and settings to a JSON file, or restore from a previous backup."
                ) {
                    BackupSettings(model)
                }
            }

            // 9. HELP & WELCOME TOUR SECTION
            if (showHelp) {
                SettingGroup(
                    title = "Help and tour",
                    footer = "Revisit the welcome guide, shortcuts, and quick setup anytime."
                ) {
                    SettingNavigationRow(
                        title = "Welcome tour",
                        summary = "Explore features, shortcuts, and setup options.",
                        icon = Glyph.APPS,
                        onClick = { showTour = true }
                    )
                }
            }

            // 10. ABOUT & PRIVACY SECTION
            if (showAbout) {
                SettingGroup(
                    title = "About and privacy",
                    footer = "Another Launcher is private, local-first, and built for foldables."
                ) {
                    SettingNavigationRow(
                        title = "Privacy policy",
                        summary = "Transparent disclosure of our zero-tracking policy.",
                        icon = Glyph.WIDGETS,
                        onClick = { showPrivacyPolicy = true }
                    )
                    SettingDivider()
                    SettingNavigationRow(
                        title = "Another Launcher",
                        summary = "Version 0.7.0-beta.3",
                        icon = Glyph.APPS,
                        onClick = { showAboutDialog = true }
                    )
                }
            }
        }
    }

    if (showPrivacyPolicy) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicy = false })
    }

    if (showAboutDialog) {
        AboutLauncherDialog(onDismiss = { showAboutDialog = false })
    }
}
}

@Composable
private fun WeatherSettings(
    location: WeatherLocation?,
    weatherUnit: String?,
    model: LauncherViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherLocation>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var detecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun detectLocation() {
        scope.launch {
            detecting = true
            error = null
            try {
                model.setWeather(detectCurrentWeatherLocation(context))
            } catch (_: SecurityException) {
                error = "Location permission is needed to detect your weather location."
            } catch (_: IOException) {
                error = "Couldn't detect your location. Check Location Services and try again."
            } finally {
                detecting = false
            }
        }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) detectLocation()
        else error = "Location permission was not granted. You can still search for a city."
    }

    LaunchedEffect(query) {
        results = emptyList()
        error = null
        searching = false
        if (query.trim().length >= 2) {
            delay(400)
            searching = true
            try {
                results = model.weatherRepository.search(query)
            } catch (_: IOException) {
                error = "City search is unavailable. Try again when connected."
            } catch (_: org.json.JSONException) {
                error = "City search is unavailable."
            } finally {
                searching = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Explain location handling before the single action that opens Android's prompt.
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        GlyphIcon(Glyph.LOCATION, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Column {
                        Text(location?.name ?: "No location set", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text(if (location != null) "%.2f°, %.2f°".format(location.latitude, location.longitude)
                            else "Search for a city or use your location", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(PermissionExplanation.LOCATION, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(
                    enabled = !detecting,
                    modifier = Modifier.align(Alignment.End),
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (granted) detectLocation()
                        else locationPermission.launch(arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ))
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (detecting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Use current location", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // City Search field
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search for a city") },
            placeholder = { Text("e.g. New York, London, Tokyo") },
            singleLine = true,
            leadingIcon = { GlyphIcon(Glyph.SEARCH, Modifier.size(18.dp)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; results = emptyList() }) {
                        GlyphIcon(Glyph.CLOSE, Modifier.size(16.dp))
                    }
                }
            }
        )
        if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())

        // Search results
        if (results.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    results.forEachIndexed { index, result ->
                        ListItem(
                            headlineContent = { Text(result.name, fontWeight = FontWeight.Medium) },
                            leadingContent = { GlyphIcon(Glyph.LOCATION, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable {
                                model.setWeather(result)
                                query = ""
                                results = emptyList()
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        if (index < results.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        // Temperature Unit Segmented Control
        SettingSegmentedRow(
            title = "Temperature unit",
            options = listOf(null, "F", "C"),
            selected = weatherUnit?.uppercase()?.takeIf { it == "F" || it == "C" },
            onSelect = model::setWeatherUnit,
            labelProvider = { unit ->
                when (unit) {
                    "F" -> "Fahrenheit (°F)"
                    "C" -> "Celsius (°C)"
                    else -> "Automatic"
                }
            }
        )


    }
}

@Composable
private fun CategorySettings(
    apps: List<AppEntry>,
    overrides: Map<String, AppCategory>,
    model: LauncherViewModel
) {
    var categoriesOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Category organization",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Change the category folder for any installed app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (overrides.isNotEmpty()) {
                    TextButton(onClick = { model.clearCategoryOverrides() }) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = { categoriesOpen = !categoriesOpen }) {
                    Text(if (categoriesOpen) "Hide" else "Organize")
                }
            }
        }

        if (categoriesOpen) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search installed apps") },
                singleLine = true,
                leadingIcon = { GlyphIcon(Glyph.SEARCH, Modifier.size(18.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { GlyphIcon(Glyph.CLOSE, Modifier.size(16.dp)) }
                    }
                }
            )

            val matching = apps.filter { it.label.contains(query, ignoreCase = true) }.take(25)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    matching.forEachIndexed { index, app ->
                        var menuOpen by remember(app.id) { mutableStateOf(false) }
                        val activeCategory = overrides[app.id] ?: app.category
                        val isOverridden = overrides.containsKey(app.id)

                        Box {
                            ListItem(
                                headlineContent = { Text(app.label, fontWeight = FontWeight.Medium) },
                                supportingContent = {
                                    Text(
                                        if (isOverridden) "${activeCategory.title} (custom)" else "${activeCategory.title} (auto)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isOverridden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = {
                                    AppIcon(app, model.apps, size = 36.dp)
                                },
                                trailingContent = {
                                    FilledTonalButton(
                                        onClick = { menuOpen = true },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(activeCategory.title, style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Auto (${app.category.title})") },
                                    onClick = {
                                        model.setCategory(app.id, null)
                                        menuOpen = false
                                    }
                                )
                                AppCategory.entries.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.title) },
                                        onClick = {
                                            model.setCategory(app.id, cat)
                                            menuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        if (index < matching.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupSettings(model: LauncherViewModel) {
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            model.exportBackup(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
        }
    }

    if (pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Restore backup?") },
            text = { Text("This will replace your current home screen layout, pages, and preferences with the contents of the backup file.") },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingRestoreUri
                    pendingRestoreUri = null
                    if (uri != null) {
                        model.importBackup(uri)
                    }
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    exportLauncher.launch("anotherlauncher-backup-$timestamp.json")
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                GlyphIcon(Glyph.SETTINGS, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export backup")
            }
            OutlinedButton(
                onClick = {
                    importLauncher.launch(arrayOf("application/json", "*/*"))
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                GlyphIcon(Glyph.PAGES, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Restore backup")
            }
        }
        Text(
            text = "Save your layout, pinned favorites, category overrides, and settings to a JSON file, or restore from a previous backup. Widgets keep their positions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
