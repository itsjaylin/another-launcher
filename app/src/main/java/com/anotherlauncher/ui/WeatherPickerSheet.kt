package com.anotherlauncher.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anotherlauncher.data.AppEntry
import com.anotherlauncher.data.AppRepository
import com.anotherlauncher.data.WeatherState
import kotlinx.coroutines.delay
import java.util.Calendar

/** Data holder for weather app launch targets (both launcher entries and system packages). */
data class DiscoveredWeatherApp(
    val label: String,
    val packageName: String,
    val appEntry: AppEntry? = null,
    val directIntent: Intent? = null,
    val iconDrawable: Drawable? = null,
)

/**
 * Material You Weather App Picker & Temperature Options Sheet.
 * - Dynamic condition header with current reading & location.
 * - Interactive Celsius / Fahrenheit / Auto temperature unit selector.
 * - Installed weather apps list with one-tap launch.
 * - Fallback to Google Play / Google Weather if no weather apps installed.
 * - Frosted glass surface matching project contrast invariants.
 */
@Composable
fun WeatherPickerSheet(
    weather: WeatherState,
    weatherUnit: String?,
    apps: List<AppEntry>,
    appRepository: AppRepository,
    onLaunchApp: (AppEntry) -> Unit,
    onOpenLocationSettings: () -> Unit,
    reduced: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val isDefaultUs = locale.country == "US"
    val isDark = isSystemInDarkTheme()
    val contentColor = if (isDark) Color(0xFFF8FAFF) else MaterialTheme.colorScheme.onSurface
    val isNight = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        hour < 6 || hour >= 20
    }

    var opened by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { opened = true; entered = true }
    LaunchedEffect(opened) {
        if (entered && !opened) {
            delay(if (reduced) 0 else 180)
            onDismiss()
        }
    }
    val progress by animateFloatAsState(
        targetValue = if (opened) 1f else 0f,
        animationSpec = tween(if (reduced) 0 else 180),
        label = "weather sheet animation"
    )

    // Discover installed weather applications
    val weatherApps = remember(apps, context) {
        findWeatherApps(context, apps)
    }

    Dialog(
        onDismissRequest = { opened = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { opened = false }
                )
                .padding(horizontal = 20.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = progress
                        scaleX = 0.92f + 0.08f * progress
                        scaleY = 0.92f + 0.08f * progress
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume inner touches
                    )
                    .glass(28, frosted = true),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                contentColor = contentColor,
                tonalElevation = 6.dp
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides contentColor,
                    LocalHomeColors provides HomeColors(darkText = !isDark)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Header: Condition icon, City, Current reading, Close button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                val reading = weather.reading
                                if (reading != null) {
                                    WeatherConditionArt(
                                        code = reading.code,
                                        isNight = isNight,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Column {
                                        val (tempVal, tempUnitLabel) = reading.displayTemp(weatherUnit, isDefaultUs)
                                        Text(
                                            text = "$tempVal$tempUnitLabel • ${reading.description}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = contentColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val locationName = weather.location?.name ?: "Weather"
                                        Text(
                                            text = locationName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = contentColor.copy(alpha = 0.75f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    Column {
                                        Text(
                                            text = "Weather",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = contentColor
                                        )
                                        Text(
                                            text = weather.location?.name ?: "Select location",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = contentColor.copy(alpha = 0.75f)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { opened = false },
                                modifier = Modifier.size(36.dp)
                            ) {
                                GlyphIcon(Glyph.CLOSE, tint = contentColor)
                            }
                        }

                        HorizontalDivider(
                            color = contentColor.copy(alpha = 0.12f),
                            thickness = 1.dp
                        )

                        // Installed Weather Apps Picker
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Weather apps",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = contentColor.copy(alpha = 0.85f)
                                )
                                if (weatherApps.isNotEmpty()) {
                                    Text(
                                        text = "${weatherApps.size} available",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = contentColor.copy(alpha = 0.60f)
                                    )
                                }
                            }

                            if (weatherApps.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    weatherApps.forEach { target ->
                                        WeatherAppRow(
                                            target = target,
                                            appRepository = appRepository,
                                            contentColor = contentColor,
                                            isDark = isDark,
                                            onLaunch = {
                                                if (target.appEntry != null) {
                                                    onLaunchApp(target.appEntry)
                                                } else if (target.directIntent != null) {
                                                    runCatching { context.startActivity(target.directIntent) }
                                                }
                                                opened = false
                                            }
                                        )
                                    }
                                }
                            } else {
                                // Empty state when no weather apps are installed
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "No dedicated weather apps found",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = contentColor.copy(alpha = 0.85f)
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            TextButton(
                                                onClick = {
                                                    val playIntent = Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse("market://search?q=weather&c=apps")
                                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    runCatching { context.startActivity(playIntent) }
                                                    opened = false
                                                }
                                            ) {
                                                Text("Get on Google Play")
                                            }

                                            TextButton(
                                                onClick = {
                                                    val webIntent = Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse("https://www.google.com/search?q=weather")
                                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    runCatching { context.startActivity(webIntent) }
                                                    opened = false
                                                }
                                            ) {
                                                Text("Google Weather")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            color = contentColor.copy(alpha = 0.12f),
                            thickness = 1.dp
                        )

                        // Bottom Actions: Change Location & Done
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    opened = false
                                    onOpenLocationSettings()
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    GlyphIcon(Glyph.SETTINGS, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text("Weather settings")
                                }
                            }

                            TextButton(onClick = { opened = false }) {
                                Text("Done", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Row item for each discovered weather app target. */
@Composable
private fun WeatherAppRow(
    target: DiscoveredWeatherApp,
    appRepository: AppRepository,
    contentColor: Color,
    isDark: Boolean,
    onLaunch: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color(0xFFFFFFFF).copy(alpha = 0.06f) else Color(0x00000000).copy(alpha = 0.04f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onLaunch)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // App Icon
            if (target.appEntry != null) {
                AppIcon(
                    app = target.appEntry,
                    repository = appRepository,
                    modifier = Modifier.size(42.dp),
                    size = 42.dp
                )
            } else if (target.iconDrawable != null) {
                val bitmap = remember(target.iconDrawable) {
                    target.iconDrawable.toSafeBitmap()
                }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = target.label,
                    modifier = Modifier.size(42.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = target.label.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // App Label and Package
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = target.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tap to open",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.65f)
                )
            }

            // Open Pill Indicator
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Open",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** Finds all weather apps installed on device (including Samsung Weather). */
private fun findWeatherApps(context: Context, apps: List<AppEntry>): List<DiscoveredWeatherApp> {
    val result = mutableListOf<DiscoveredWeatherApp>()
    val seenPackages = mutableSetOf<String>()

    // 1. Scan user launchable apps
    for (app in apps) {
        val pkg = app.info.componentName.packageName
        val label = app.label.lowercase()
        val pkgLower = pkg.lowercase()

        val isWeather = label.contains("weather") ||
            pkgLower.contains("weather") ||
            pkg == "com.sec.android.daemonapp" ||
            pkg == "com.google.android.apps.weather" ||
            pkg == "com.weather.Weather" ||
            pkg == "com.accuweather.android" ||
            pkg == "com.handmark.expressweather" ||
            pkg == "widget.dd.com.overdrop.flutter" ||
            pkg == "com.grailr.carrotweather" ||
            pkg == "com.yahoo.mobile.client.android.weather"

        if (isWeather && seenPackages.add(pkg)) {
            result.add(
                DiscoveredWeatherApp(
                    label = app.label,
                    packageName = pkg,
                    appEntry = app
                )
            )
        }
    }

    // 2. Scan for Samsung Weather if not in the default launcher app list
    if ("com.sec.android.daemonapp" !in seenPackages) {
        runCatching {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo("com.sec.android.daemonapp", 0)
            val intent = Intent().apply {
                component = ComponentName("com.sec.android.daemonapp", "com.sec.android.daemonapp.app.MainActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(pm) != null) {
                val label = pm.getApplicationLabel(appInfo).toString().ifBlank { "Samsung Weather" }
                val icon = pm.getApplicationIcon(appInfo)
                seenPackages.add("com.sec.android.daemonapp")
                result.add(
                    0,
                    DiscoveredWeatherApp(
                        label = label,
                        packageName = "com.sec.android.daemonapp",
                        directIntent = intent,
                        iconDrawable = icon
                    )
                )
            }
        }
    }

    // 3. Scan for Google Weather standalone if present
    if ("com.google.android.apps.weather" !in seenPackages) {
        runCatching {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage("com.google.android.apps.weather")
            if (launchIntent != null) {
                val appInfo = pm.getApplicationInfo("com.google.android.apps.weather", 0)
                val label = pm.getApplicationLabel(appInfo).toString().ifBlank { "Google Weather" }
                val icon = pm.getApplicationIcon(appInfo)
                seenPackages.add("com.google.android.apps.weather")
                result.add(
                    DiscoveredWeatherApp(
                        label = label,
                        packageName = "com.google.android.apps.weather",
                        directIntent = launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        iconDrawable = icon
                    )
                )
            }
        }
    }

    return result
}

/** Safely rasterize a Drawable to Bitmap for Compose ImageBitmap. */
private fun Drawable.toSafeBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val width = intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = intrinsicHeight.takeIf { it > 0 } ?: 96
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}
