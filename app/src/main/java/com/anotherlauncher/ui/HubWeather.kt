package com.anotherlauncher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anotherlauncher.data.WeatherState
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Modern Material You Weather Card for the Hub:
 * - Dynamic condition artwork (Sun, Moon, Clouds, Rain, Thunderstorm, Snow, Fog)
 * - Material 3 tonal typography with locale-aware unit display (°F in US, °C elsewhere)
 * - Fluid frosted glass finish with subtle ambient condition tinting
 * - One-tap navigation to weather location settings
 */
@Composable
fun HubWeatherCard(
    weather: WeatherState,
    weatherUnit: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reading = weather.reading
    val location = weather.location
    val locale = LocalConfiguration.current.locales[0]
    val isUs = locale.country == "US"
    val isNight = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        hour < 6 || hour >= 20
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val weatherTextColor = if (isDark) Color(0xFFF8FAFF) else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .glass(26, emphasized = true, frosted = true)
            .clickable(onClickLabel = "Open weather apps and options", onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        if (reading != null && location != null) {
            val parts = location.name.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val cityName = parts.firstOrNull() ?: "Weather"
            val region = if (parts.size >= 2) parts[1] else null
            val (tempDegrees, tempUnit) = reading.displayTemp(weatherUnit, isUs)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: City, Region, Condition Pill
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column {
                        Text(
                            text = cityName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = weatherTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (region != null) {
                            Text(
                                text = region,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Material You Condition Pill
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isDark) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.60f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = reading.description,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Right Column: Graphic Artwork + Expressive Temperature
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WeatherConditionArt(
                        code = reading.code,
                        isNight = isNight,
                        modifier = Modifier.size(48.dp)
                    )

                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "$tempDegrees",
                            fontSize = 44.sp,
                            lineHeight = 44.sp,
                            fontWeight = FontWeight.Medium,
                            color = weatherTextColor
                        )
                        Text(
                            text = tempUnit,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color.White.copy(alpha = 0.80f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                        )
                    }
                }
            }
        } else {
            // Idle / Unset / Loading State
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f)),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(
                        glyph = Glyph.WIDGETS,
                        modifier = Modifier.size(22.dp),
                        tint = if (isDark) Color.White else MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (location != null) location.name.substringBefore(',') else "Weather",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = weatherTextColor
                    )
                    Text(
                        text = when {
                            weather.loading -> "Updating weather…"
                            location == null -> "Tap to select your city"
                            else -> weather.error ?: "Weather currently unavailable"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

/**
 * Procedural Material You Weather Artwork:
 * Draws expressive stylized vector graphics for various meteorological condition codes.
 */
@Composable
internal fun WeatherConditionArt(
    code: Int,
    isNight: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "weather shimmer")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Canvas(modifier = modifier) {
        when {
            // Clear skies
            code == 0 -> {
                if (isNight) {
                    drawCrescentMoon()
                } else {
                    drawGoldenSun(pulse)
                }
            }
            // Partly cloudy
            code in 1..2 -> {
                if (isNight) {
                    drawCrescentMoon(scale = 0.75f, offset = Offset(-size.width * 0.12f, -size.height * 0.12f))
                } else {
                    drawGoldenSun(pulse, scale = 0.75f, offset = Offset(-size.width * 0.12f, -size.height * 0.12f))
                }
                drawStylizedCloud(offset = Offset(size.width * 0.08f, size.height * 0.12f), scale = 0.85f)
            }
            // Cloudy / Overcast
            code == 3 -> {
                drawStylizedCloud(offset = Offset(-size.width * 0.08f, -size.height * 0.06f), scale = 0.82f, dark = true)
                drawStylizedCloud(offset = Offset(size.width * 0.08f, size.height * 0.10f), scale = 0.88f)
            }
            // Fog
            code in 45..48 -> {
                drawFogBars()
            }
            // Rain / Drizzle
            code in 51..67 || code in 80..82 -> {
                drawStylizedCloud(offset = Offset(0f, -size.height * 0.10f), scale = 0.90f)
                drawRaindrops()
            }
            // Snow
            code in 71..77 || code in 85..86 -> {
                drawStylizedCloud(offset = Offset(0f, -size.height * 0.10f), scale = 0.90f)
                drawSnowflakes()
            }
            // Thunderstorms
            code in 95..99 -> {
                drawStylizedCloud(offset = Offset(0f, -size.height * 0.12f), scale = 0.90f, dark = true)
                drawLightningBolt()
            }
            else -> {
                drawGoldenSun(pulse)
            }
        }
    }
}

private fun DrawScope.drawGoldenSun(pulse: Float, scale: Float = 1f, offset: Offset = Offset.Zero) {
    val center = Offset(size.width / 2 + offset.x, size.height / 2 + offset.y)
    val baseRadius = (size.minDimension / 2.5f) * scale
    val glowRadius = baseRadius * 1.25f * pulse

    // Radiant outer ambient glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFB300).copy(alpha = 0.35f), Color.Transparent),
            center = center,
            radius = glowRadius
        ),
        radius = glowRadius,
        center = center
    )

    // Solid core sun with warm sunset gradient
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFFD54F), Color(0xFFFF8F00)),
            start = Offset(center.x - baseRadius, center.y - baseRadius),
            end = Offset(center.x + baseRadius, center.y + baseRadius)
        ),
        radius = baseRadius,
        center = center
    )
}

private fun DrawScope.drawCrescentMoon(scale: Float = 1f, offset: Offset = Offset.Zero) {
    val center = Offset(size.width / 2 + offset.x, size.height / 2 + offset.y)
    val r = (size.minDimension / 2.6f) * scale

    val moonPath = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(center.x - r, center.y - r, center.x + r, center.y + r))
    }
    val cutoutPath = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(center.x - r * 0.45f, center.y - r * 1.05f, center.x + r * 1.25f, center.y + r * 0.65f))
    }
    val resultPath = Path()
    resultPath.op(moonPath, cutoutPath, androidx.compose.ui.graphics.PathOperation.Difference)

    drawPath(
        path = resultPath,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFFF176), Color(0xFFFFB74D)),
            start = Offset(center.x - r, center.y - r),
            end = Offset(center.x + r, center.y + r)
        )
    )

    // Small star accent
    val starCenter = Offset(center.x + r * 0.55f, center.y - r * 0.35f)
    drawCircle(Color(0xFFFFF9C4), 2.dp.toPx(), starCenter)
}

private fun DrawScope.drawStylizedCloud(
    offset: Offset = Offset.Zero,
    scale: Float = 1f,
    dark: Boolean = false
) {
    val cx = size.width / 2 + offset.x
    val cy = size.height / 2 + offset.y
    val w = size.width * 0.68f * scale
    val h = size.height * 0.42f * scale

    val baseBrush = if (dark) {
        Brush.linearGradient(
            colors = listOf(Color(0xFF90A4AE), Color(0xFF546E7A)),
            start = Offset(cx - w / 2, cy - h / 2),
            end = Offset(cx + w / 2, cy + h / 2)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFFECEFF1), Color(0xFFCFD8DC)),
            start = Offset(cx - w / 2, cy - h / 2),
            end = Offset(cx + w / 2, cy + h / 2)
        )
    }

    val path = Path().apply {
        val left = cx - w / 2
        val top = cy - h / 2
        val bottom = cy + h / 2
        val right = cx + w / 2

        // Cloud base pill
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left, bottom - h * 0.55f, right, bottom,
                CornerRadius(h * 0.28f, h * 0.28f)
            )
        )
        // Main puffy lobe
        addOval(androidx.compose.ui.geometry.Rect(cx - w * 0.25f, top, cx + w * 0.28f, bottom))
        // Secondary puffy lobe
        addOval(androidx.compose.ui.geometry.Rect(left + w * 0.05f, top + h * 0.16f, cx + w * 0.05f, bottom))
    }

    drawPath(path, baseBrush)
}

private fun DrawScope.drawRaindrops() {
    val dropColor = Color(0xFF4FC3F7)
    val strokeWidth = 2.5.dp.toPx()
    val yStart = size.height * 0.68f
    val length = size.height * 0.18f

    val xs = listOf(size.width * 0.32f, size.width * 0.50f, size.width * 0.68f)
    xs.forEachIndexed { index, x ->
        val y = yStart + (index % 2) * 4.dp.toPx()
        drawLine(
            color = dropColor,
            start = Offset(x, y),
            end = Offset(x - 3.dp.toPx(), y + length),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawSnowflakes() {
    val flakeColor = Color(0xFFE1F5FE)
    val r = 2.2.dp.toPx()
    val yStart = size.height * 0.70f

    val positions = listOf(
        Offset(size.width * 0.34f, yStart),
        Offset(size.width * 0.50f, yStart + 6.dp.toPx()),
        Offset(size.width * 0.66f, yStart + 2.dp.toPx())
    )
    positions.forEach { pt ->
        drawCircle(flakeColor, r, pt)
    }
}

private fun DrawScope.drawLightningBolt() {
    val boltColor = Color(0xFFFFD54F)
    val cx = size.width * 0.50f
    val cy = size.height * 0.65f

    val path = Path().apply {
        moveTo(cx + 4.dp.toPx(), cy - 6.dp.toPx())
        lineTo(cx - 3.dp.toPx(), cy + 2.dp.toPx())
        lineTo(cx + 2.dp.toPx(), cy + 2.dp.toPx())
        lineTo(cx - 4.dp.toPx(), cy + 12.dp.toPx())
        lineTo(cx + 6.dp.toPx(), cy + 3.dp.toPx())
        lineTo(cx, cy + 3.dp.toPx())
        close()
    }
    drawPath(path, boltColor, style = Fill)
}

private fun DrawScope.drawFogBars() {
    val fogColor = Color(0xFFB0BEC5).copy(alpha = 0.85f)
    val barHeight = 3.dp.toPx()
    val yCenter = size.height / 2

    val bars = listOf(
        Pair(size.width * 0.20f, size.width * 0.80f) to yCenter - 7.dp.toPx(),
        Pair(size.width * 0.15f, size.width * 0.85f) to yCenter,
        Pair(size.width * 0.25f, size.width * 0.75f) to yCenter + 7.dp.toPx()
    )

    bars.forEach { (range, y) ->
        drawLine(
            color = fogColor,
            start = Offset(range.first, y),
            end = Offset(range.second, y),
            strokeWidth = barHeight,
            cap = StrokeCap.Round
        )
    }
}
