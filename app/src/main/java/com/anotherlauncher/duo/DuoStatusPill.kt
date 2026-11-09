package com.anotherlauncher.duo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings

import android.text.format.DateFormat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anotherlauncher.ui.Glyph
import com.anotherlauncher.ui.GlyphIcon
import com.anotherlauncher.ui.glass
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin

/** Time + battery arc / Wi-Fi center / cellular dots, matching the compact edge-rail reference. */
@Composable
fun DuoStatusPill(
    status: DeviceStatus,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    tint: Color = Color.White,
    showCellular: Boolean = true,
    onSignalPermission: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val time = SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale).format(Date(status.timeMillis))
    val fullTime = DateFormat.getTimeFormat(context).format(Date(status.timeMillis))
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val shadowOffsetDp = 1.dp
    val shadowOffsetPx = with(density) { shadowOffsetDp.toPx() }
    val shadowBlurRadiusPx = with(density) { 3.dp.toPx() }
    val shadowBlurDp = 3.dp
    // Keep the frame clock completely idle unless the charging dot is visible.
    val pulseRadius = if (status.charging) {
        val infiniteTransition = rememberInfiniteTransition(label = "chargingPulse")
        val pulseProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "chargingPulse"
        )
        (1.7f + 0.6f * pulseProgress).dp
    } else 2.dp
    // Ultra-sheer drop shadow keeps the module legible over bright wallpapers without dark smudges:
    val textStyle = TextStyle(color = tint, fontSize = 16.sp, fontWeight = FontWeight.Bold,
        shadow = Shadow(Color.Black.copy(alpha = .08f), Offset(0f, shadowOffsetPx), shadowBlurRadiusPx))
    var details by remember { mutableStateOf(false) }
    val description = buildString {
        append(fullTime)
        append(", battery ${status.battery?.let { "$it percent" } ?: "unavailable"}")
        if (status.charging) append(", charging")
        append(if (status.wifiConnected) ", Wi-Fi connected" else status.networkType?.let { ", $it connected" } ?: ", Wi-Fi not active")
        if (showCellular) {
            append(if (status.airplaneMode) ", airplane mode" else status.cellularLevel?.let { ", cellular $it of 4" } ?: ", cellular unavailable")
        }
        append(". Tap for status details.")
    }
    val emblem: @Composable (Color, Modifier) -> Unit = { ink, emblemModifier ->
        Canvas(emblemModifier.size(48.dp, 51.dp)) {
            val center = Offset(size.width / 2, size.height * .44f)
            val radius = size.width * .43f
            val topLeft = center - Offset(radius, radius)
            val diameter = Size(radius * 2, radius * 2)
            val stroke = Stroke(3.2.dp.toPx(), cap = StrokeCap.Round)
            val trackAlpha = ink.alpha * .23f
            // The gap at the bottom is reserved for cellular dots, not another status row.
            drawArc(ink.copy(alpha = trackAlpha), 135f, 270f, false, topLeft, diameter, style = stroke)
            status.battery?.let { percent ->
                if (percent > 0) drawArc(ink, 135f, 270f * percent / 100f, false, topLeft, diameter, style = stroke)
            }
            if (status.airplaneMode) {
                drawLine(ink, center - Offset(0f, 6.dp.toPx()), center + Offset(0f, 6.dp.toPx()), 1.8.dp.toPx(), StrokeCap.Round)
                drawLine(ink, center - Offset(6.dp.toPx(), 0f), center + Offset(6.dp.toPx(), 0f), 1.8.dp.toPx(), StrokeCap.Round)
            } else if (status.wifiConnected) {
                // Scaled-up prominent Wi-Fi icon, optically centered
                val wifiOrigin = center + Offset(0f, 6.8.dp.toPx())
                for (band in 1..3) {
                    val r = band * 4.3.dp.toPx()
                    val color = if ((status.wifiLevel ?: 0) >= band) ink else ink.copy(alpha = trackAlpha)
                    drawArc(color, 224f, 92f, false, wifiOrigin - Offset(r, r), Size(r * 2, r * 2),
                        style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                }
                drawCircle(ink, 1.7.dp.toPx(), wifiOrigin)
            } else {
                // When Wi-Fi is disconnected, show active cellular network (5G+, 5G, 4G, etc.)
                val net = status.networkType ?: if (!status.airplaneMode && (status.cellularConnected || (status.cellularLevel ?: 0) > 0)) "5G" else null
                if (net != null) {
                    val layout = textMeasurer.measure(
                        text = net,
                        style = TextStyle(
                            color = ink,
                            fontSize = if (net.length > 2) 11.sp else 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    )
                    drawText(
                        layout,
                        topLeft = Offset(
                            center.x - layout.size.width / 2f,
                            center.y - layout.size.height / 2f
                        )
                    )
                }
            }
            if (showCellular) {
                repeat(4) { index ->
                    val angle = Math.toRadians(121.0 - index * 20.5)
                    val position = center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius)
                    val lit = !status.airplaneMode && (status.cellularLevel ?: 0) > index
                    drawCircle(if (lit) ink else ink.copy(alpha = trackAlpha), 2.dp.toPx(), position)
                }
            }
            if (status.charging && ink == tint) {
                // Iconic Apple systemGreen charging indicator (#34C759), 100% opaque, gentle size pulse only
                drawCircle(Color(0xFF34C759), pulseRadius.toPx(), center + Offset(0f, radius + 6.dp.toPx()))
            }
        }
    }
    val base = modifier.clickable(onClickLabel = "Status details") { details = true }
        .semantics(mergeDescendants = true) { contentDescription = description }
    val emblemWithShadow: @Composable () -> Unit = {
        Box {
            // Ultra-sheer blurred shadow pass matching the time label shadow above
            emblem(
                Color.Black.copy(alpha = .06f),
                Modifier
                    .offset(y = shadowOffsetDp)
                    .blur(shadowBlurDp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            )
            emblem(tint, Modifier)
        }
    }
    if (vertical) Column(base.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(time, style = textStyle, maxLines = 1)
        emblemWithShadow()
    } else Row(base.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        Text(time, style = textStyle)
        Spacer(Modifier.width(8.dp)); emblemWithShadow()
    }
    if (details) DeviceStatusDialog(status, fullTime, onDismiss = { details = false }, onSignalPermission = onSignalPermission)
}


@Composable
private fun DeviceStatusDialog(
    status: DeviceStatus,
    fullTime: String,
    onDismiss: () -> Unit,
    onSignalPermission: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val titleColor = if (isDark) Color(0xFFF8FAFF) else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (isDark) Color(0xFFF8FAFF).copy(alpha = 0.70f) else MaterialTheme.colorScheme.onSurfaceVariant
    val cardBackground = if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.46f)
    val innerCardBackground = if (isDark) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.38f)

    fun openSetting(primary: String, fallback: String = Settings.ACTION_SETTINGS) {
        try {
            context.startActivity(Intent(primary).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(fallback).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Transparent, contentColor = titleColor) {
            Column(
                Modifier.padding(horizontal = 20.dp).widthIn(max = 420.dp).fillMaxWidth()
                    .glass(28, emphasized = true, frosted = true).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Device controls", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = titleColor)
                        Text(fullTime, style = MaterialTheme.typography.bodyMedium, color = subtitleColor)
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp).background(if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f), CircleShape)
                    ) {
                        GlyphIcon(Glyph.CLOSE, tint = titleColor)
                    }
                }

                // Battery Status Card
                Row(
                    Modifier.fillMaxWidth().background(cardBackground, RoundedCornerShape(22.dp)).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Battery", style = MaterialTheme.typography.labelLarge, color = subtitleColor)
                        Text(status.battery?.let { "$it%" } ?: "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = titleColor)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (status.charging) {
                            Box(
                                Modifier.size(10.dp).background(Color(0xFF34C759), CircleShape)
                            )
                        }
                        Text(
                            if (status.charging) "Charging" else if (status.powerSaveMode) "Power saving" else "On battery",
                            color = when {
                                status.charging -> Color(0xFF34C759)
                                status.powerSaveMode -> MaterialTheme.colorScheme.primary
                                else -> subtitleColor
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Connectivity & Controls Card
                Column(Modifier.fillMaxWidth().background(innerCardBackground, RoundedCornerShape(22.dp))) {
                    // Wi-Fi Row
                    StatusControlRow(
                        title = "Wi-Fi",
                        summary = if (status.wifiConnected) "Connected"
                        else if (status.wifiEnabled) "Not connected" else "Off",
                        checked = status.wifiEnabled,
                        accent = Color(0xFF4285F4),
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        icon = { tint -> WifiControlIcon(tint) }
                    ) { openSetting(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIFI_SETTINGS) }

                    StatusDivider()

                    // Mobile Data / Cellular Network Row
                    val cellularSummary = when {
                        status.airplaneMode -> "Off"
                        status.networkType != null -> {
                            if (status.wifiConnected) status.networkType else "Connected · ${status.networkType}"
                        }
                        status.cellularConnected -> "Connected"
                        else -> "Off"
                    }
                    StatusControlRow(
                        title = "Mobile data",
                        summary = cellularSummary,
                        checked = !status.airplaneMode && (status.cellularConnected || status.networkType != null),
                        accent = Color(0xFF34C759),
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        icon = { tint ->
                            GlyphIcon(Glyph.SIGNAL, modifier = Modifier.size(20.dp), tint = tint)
                        },
                        showSwitch = false,
                        trailing = {
                            GlyphIcon(Glyph.CHEVRON_RIGHT, modifier = Modifier.size(16.dp), tint = subtitleColor)
                        }
                    ) {
                        openSetting(Settings.ACTION_NETWORK_OPERATOR_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
                    }

                    StatusDivider()

                    // Power saving Row
                    StatusControlRow(
                        title = "Power saving",
                        summary = if (status.powerSaveMode) "On" else "Off",
                        checked = status.powerSaveMode,
                        accent = Color(0xFFFF9500),
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        icon = { tint -> PowerSaveControlIcon(tint) },
                        showSwitch = false,
                        trailing = {
                            GlyphIcon(Glyph.CHEVRON_RIGHT, modifier = Modifier.size(16.dp), tint = subtitleColor)
                        }
                    ) {
                        openSetting(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                    }

                    StatusDivider()

                    // Airplane mode Row
                    StatusControlRow(
                        title = "Airplane mode",
                        summary = if (status.airplaneMode) "On" else "Off",
                        checked = status.airplaneMode,
                        accent = Color(0xFF8E8E93),
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        icon = { tint -> AirplaneControlIcon(tint) }
                    ) { openSetting(Settings.ACTION_AIRPLANE_MODE_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS) }
                }
            }
        }
    }
}

@Composable
private fun StatusDivider() = HorizontalDivider(
    Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f)
)

@Composable
private fun StatusControlRow(
    title: String,
    summary: String,
    checked: Boolean,
    accent: Color,
    titleColor: Color,
    subtitleColor: Color,
    icon: @Composable (Color) -> Unit,
    showSwitch: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Open $title controls", onClick = onClick)
            .semantics { role = Role.Switch }.padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconTint = if (checked) Color.White else accent
        Box(
            Modifier.size(38.dp).background(accent.copy(alpha = if (checked) 1f else .20f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            icon(iconTint)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = titleColor)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
        }
        if (trailing != null) {
            trailing()
        } else if (showSwitch) {
            Switch(
                checked = checked,
                onCheckedChange = { onClick() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent,
                )
            )
        }
    }
}

@Composable
private fun WifiControlIcon(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val origin = Offset(w / 2f, h * 0.85f)
        val stroke = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
        for (band in 1..3) {
            val r = band * 3.2.dp.toPx()
            drawArc(
                tint,
                224f,
                92f,
                false,
                origin - Offset(r, r),
                Size(r * 2, r * 2),
                style = stroke
            )
        }
        drawCircle(tint, 1.4.dp.toPx(), origin)
    }
}

@Composable
private fun PowerSaveControlIcon(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        // Battery body outline
        val stroke = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(
            tint,
            topLeft = Offset(w * 0.15f, h * 0.25f),
            size = Size(w * 0.70f, h * 0.50f),
            cornerRadius = CornerRadius(2.5.dp.toPx()),
            style = stroke
        )
        // Battery terminal tip
        drawRoundRect(
            tint,
            topLeft = Offset(w * 0.87f, h * 0.38f),
            size = Size(w * 0.08f, h * 0.24f),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
        // Inner lightning bolt
        val bolt = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.52f, h * 0.32f)
            lineTo(w * 0.36f, h * 0.52f)
            lineTo(w * 0.48f, h * 0.52f)
            lineTo(w * 0.44f, h * 0.68f)
            lineTo(w * 0.62f, h * 0.48f)
            lineTo(w * 0.50f, h * 0.48f)
            close()
        }
        drawPath(bolt, tint)
    }
}

@Composable
private fun AirplaneControlIcon(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        // Fuselage and wings
        val plane = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.50f, h * 0.12f)
            lineTo(w * 0.58f, h * 0.38f)
            lineTo(w * 0.92f, h * 0.54f)
            lineTo(w * 0.92f, h * 0.64f)
            lineTo(w * 0.58f, h * 0.58f)
            lineTo(w * 0.58f, h * 0.80f)
            lineTo(w * 0.72f, h * 0.90f)
            lineTo(w * 0.72f, h * 0.96f)
            lineTo(w * 0.50f, h * 0.90f)
            lineTo(w * 0.28f, h * 0.96f)
            lineTo(w * 0.28f, h * 0.90f)
            lineTo(w * 0.42f, h * 0.80f)
            lineTo(w * 0.42f, h * 0.58f)
            lineTo(w * 0.08f, h * 0.64f)
            lineTo(w * 0.08f, h * 0.54f)
            lineTo(w * 0.42f, h * 0.38f)
            close()
        }
        drawPath(plane, tint)
    }
}
