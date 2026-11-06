package com.anotherlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.unit.dp

val Ink = Color(0xFF101625)
val Frost = Color(0xFFB8D3FF)
val Muted = Color(0xFFB5BDCE)

data class HomeColors(val darkText: Boolean) {
    val text get() = if (darkText) Color(0xFF15202C) else Color(0xFFF8FAFF)
    val secondary get() = text.copy(alpha = .76f)
}
val LocalHomeColors = staticCompositionLocalOf { HomeColors(false) }
/** Packages with live notifications; provided at the home root, empty until access is granted. */
val LocalNotificationDots = staticCompositionLocalOf { emptySet<String>() }

@Composable
fun AnotherLauncherTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(LocalContext.current)
        else dynamicLightColorScheme(LocalContext.current), content = content,
)

@Composable
fun Modifier.glass(radius: Int = 28, emphasized: Boolean = false, frosted: Boolean = false): Modifier {
    val colors = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(radius.dp)
    if (!frosted) return background(colors.surfaceContainer.copy(alpha = .94f), shape)
        .border(1.dp, (if (emphasized) colors.primary else colors.outlineVariant).copy(alpha = .45f), shape)
    // Layered liquid glass: a translucent density gradient carries the wallpaper,
    // while broad reflections and a tight inner rim add curved, glossy depth.
    val fill = Brush.verticalGradient(listOf(
        colors.surfaceContainerHigh.copy(alpha = when {
            emphasized && isDark -> .76f
            emphasized -> .80f
            isDark -> .30f
            else -> .36f
        }),
        colors.surfaceContainer.copy(alpha = when {
            emphasized && isDark -> .68f
            emphasized -> .72f
            isDark -> .16f
            else -> .22f
        }),
        colors.surfaceContainerHighest.copy(alpha = when {
            emphasized && isDark -> .72f
            emphasized -> .76f
            isDark -> .26f
            else -> .32f
        }),
    ))
    return background(fill, shape)
        .drawWithCache {
            val corner = radius.dp.toPx()
            val rimInset = .8.dp.toPx()
            val specular = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isDark) .32f else .42f),
                    Color.White.copy(alpha = if (isDark) .04f else .07f),
                    Color.Transparent,
                ),
                center = Offset(size.width * .22f, -size.height * .18f),
                radius = maxOf(size.width, size.height) * .72f,
            )
            val lowerReflection = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = if (isDark) .12f else .18f)),
                startY = size.height * .80f,
                endY = size.height,
            )
            val innerRim = Brush.verticalGradient(listOf(
                Color.White.copy(alpha = if (isDark) .25f else .50f),
                Color.White.copy(alpha = if (isDark) .03f else .12f),
            ))
            onDrawBehind {
                val corners = CornerRadius(corner, corner)
                drawRoundRect(specular, cornerRadius = corners)
                drawRoundRect(lowerReflection, cornerRadius = corners)
                drawRoundRect(
                    brush = innerRim,
                    topLeft = Offset(rimInset, rimInset),
                    size = Size(size.width - rimInset * 2, size.height - rimInset * 2),
                    cornerRadius = CornerRadius(
                        (corner - rimInset).coerceAtLeast(0f),
                        (corner - rimInset).coerceAtLeast(0f),
                    ),
                    style = Stroke(.8.dp.toPx()),
                )
            }
        }
        .border(1.dp, Brush.verticalGradient(listOf(
            Color.White.copy(alpha = if (isDark) .50f else .78f),
            Color.White.copy(alpha = if (isDark) .10f else .24f),
            colors.outlineVariant.copy(alpha = if (isDark) .44f else .34f),
        )), shape)
}

enum class Glyph { APPS, SETTINGS, CLOSE, PLUS, SEARCH, BACK, FOLD, EDIT, WIDGETS, WALLPAPER, PAGES, STACK, UNSTACK, REMOVE, UNINSTALL, CONTACT, HOME, PALETTE, CHEVRON_RIGHT, CHECK, BELL, SIGNAL, LOCATION }

@Composable
fun GlyphIcon(glyph: Glyph, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier.size(22.dp)) {
        val u = size.minDimension / 24f
        val stroke = 1.7f * u
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
            tint, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), stroke, StrokeCap.Round,
        )
        when (glyph) {
            Glyph.APPS -> for (x in listOf(5f, 12f, 19f)) for (y in listOf(5f, 12f, 19f)) {
                drawCircle(tint, 1.7f * u, Offset(x * u, y * u))
            }
            Glyph.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            Glyph.PLUS -> { line(5f, 12f, 19f, 12f); line(12f, 5f, 12f, 19f) }
            Glyph.BACK -> { line(15f, 5f, 8f, 12f); line(8f, 12f, 15f, 19f) }
            Glyph.CHEVRON_RIGHT -> { line(9f, 5f, 16f, 12f); line(16f, 12f, 9f, 19f) }
            Glyph.CHECK -> { line(4.5f, 12f, 9.5f, 17f); line(9.5f, 17f, 19.5f, 7f) }
            Glyph.SEARCH -> {
                drawCircle(tint, 6f * u, Offset(10f * u, 10f * u), style = Stroke(stroke))
                line(15f, 15f, 20f, 20f)
            }
            Glyph.SETTINGS -> {
                drawCircle(tint, 6.5f * u, Offset(12f * u, 12f * u), style = Stroke(stroke))
                drawCircle(tint, 2.4f * u, Offset(12f * u, 12f * u), style = Stroke(stroke))
                repeat(8) { i ->
                    val angle = Math.toRadians(i * 45.0)
                    val dx = kotlin.math.cos(angle).toFloat(); val dy = kotlin.math.sin(angle).toFloat()
                    line(12 + dx * 7, 12 + dy * 7, 12 + dx * 9.5f, 12 + dy * 9.5f)
                }
            }
            Glyph.EDIT -> {
                line(5f, 16f, 16f, 5f); line(8f, 19f, 19f, 8f); line(16f, 5f, 19f, 8f)
                line(5f, 16f, 4f, 20f); line(4f, 20f, 8f, 19f); line(13f, 8f, 16f, 11f)
            }
            Glyph.WIDGETS -> for (x in listOf(3f, 14f)) for (y in listOf(3f, 14f)) {
                drawRoundRect(tint, Offset(x * u, y * u), Size(7f * u, 7f * u), CornerRadius(2f * u), style = Stroke(stroke))
            }
            Glyph.WALLPAPER -> {
                drawRoundRect(tint, Offset(3f * u, 4f * u), Size(18f * u, 16f * u), CornerRadius(3f * u), style = Stroke(stroke))
                drawCircle(tint, 1.8f * u, Offset(8.5f * u, 9.5f * u))
                val ridge = Path().apply {
                    moveTo(3f * u, 17f * u); lineTo(9.5f * u, 11.5f * u); lineTo(13f * u, 15f * u)
                    lineTo(15.5f * u, 12.5f * u); lineTo(21f * u, 17.5f * u)
                }
                drawPath(ridge, tint, style = Stroke(stroke))
            }
            Glyph.PAGES -> {
                drawRoundRect(tint, Offset(7f * u, 3f * u), Size(13f * u, 15f * u), CornerRadius(2f * u), style = Stroke(stroke))
                line(3f, 7f, 3f, 21f); line(3f, 21f, 16f, 21f)
            }
            Glyph.STACK, Glyph.UNSTACK -> {
                val path = Path().apply { moveTo(3f * u, 8f * u); lineTo(12f * u, 3f * u); lineTo(21f * u, 8f * u); lineTo(12f * u, 13f * u); close() }
                drawPath(path, tint, style = Stroke(stroke))
                line(3f, 13f, 12f, 18f); line(12f, 18f, 21f, 13f)
                if (glyph == Glyph.STACK) { line(3f, 17f, 12f, 22f); line(12f, 22f, 21f, 17f) }
            }
            Glyph.REMOVE -> {
                drawCircle(tint, 8.5f * u, Offset(12f * u, 12f * u), style = Stroke(stroke))
                line(8f, 12f, 16f, 12f)
            }
            Glyph.UNINSTALL -> {
                line(4f, 6f, 20f, 6f); line(9f, 3f, 15f, 3f)
                line(6f, 9f, 7f, 21f); line(7f, 21f, 17f, 21f); line(17f, 21f, 18f, 9f)
                line(10f, 10f, 10f, 17f); line(14f, 10f, 14f, 17f)
            }
            Glyph.FOLD -> {
                line(3f, 4f, 10f, 7f); line(10f, 7f, 10f, 21f); line(10f, 21f, 3f, 18f); line(3f, 18f, 3f, 4f)
                line(14f, 7f, 21f, 4f); line(21f, 4f, 21f, 18f); line(21f, 18f, 14f, 21f); line(14f, 21f, 14f, 7f)
            }
            Glyph.CONTACT -> {
                drawCircle(tint, 3.2f * u, Offset(12f * u, 8f * u), style = Stroke(stroke))
                drawArc(tint, 180f, 180f, false, Offset(4.5f * u, 12.5f * u), Size(15f * u, 13f * u), style = Stroke(stroke))
            }
            Glyph.HOME -> {
                line(3f, 11.5f, 12f, 4f)
                line(12f, 4f, 21f, 11.5f)
                line(5.5f, 11f, 5.5f, 20f)
                line(18.5f, 11f, 18.5f, 20f)
                line(5.5f, 20f, 18.5f, 20f)
                line(9.5f, 20f, 9.5f, 14f)
                line(9.5f, 14f, 14.5f, 14f)
                line(14.5f, 14f, 14.5f, 20f)
            }
            Glyph.PALETTE -> {
                val path = Path().apply {
                    moveTo(12f * u, 3f * u)
                    cubicTo(17.5f * u, 3f * u, 21f * u, 6.5f * u, 21f * u, 11.5f * u)
                    cubicTo(21f * u, 15f * u, 18.5f * u, 17f * u, 16f * u, 17f * u)
                    cubicTo(14.8f * u, 17f * u, 14f * u, 17.8f * u, 14f * u, 19f * u)
                    cubicTo(14f * u, 19.8f * u, 14.4f * u, 20.3f * u, 14f * u, 21f * u)
                    cubicTo(13.2f * u, 22f * u, 10.5f * u, 21f * u, 7.5f * u, 19f * u)
                    cubicTo(4.5f * u, 16.8f * u, 3f * u, 14f * u, 3f * u, 11.5f * u)
                    cubicTo(3f * u, 6.5f * u, 7f * u, 3f * u, 12f * u, 3f * u)
                    close()
                }
                drawPath(path, tint, style = Stroke(stroke))
                drawCircle(tint, 1.2f * u, Offset(8f * u, 8.5f * u))
                drawCircle(tint, 1.2f * u, Offset(13f * u, 7.5f * u))
                drawCircle(tint, 1.2f * u, Offset(17f * u, 10.5f * u))
            }
            Glyph.BELL -> {
                val bell = Path().apply {
                    moveTo(12f * u, 4f * u)
                    cubicTo(14.5f * u, 4f * u, 16.5f * u, 6.2f * u, 16.5f * u, 9.5f * u)
                    lineTo(16.5f * u, 14f * u)
                    lineTo(18.5f * u, 17f * u)
                    lineTo(5.5f * u, 17f * u)
                    lineTo(7.5f * u, 14f * u)
                    lineTo(7.5f * u, 9.5f * u)
                    cubicTo(7.5f * u, 6.2f * u, 9.5f * u, 4f * u, 12f * u, 4f * u)
                }
                drawPath(bell, tint, style = Stroke(stroke))
                line(10.5f, 19.5f, 13.5f, 19.5f)
            }
            Glyph.SIGNAL -> {
                for (i in 0..3) {
                    val h = 3.5f + i * 3.5f
                    val x = 5.5f + i * 4.3f
                    drawLine(tint, Offset(x * u, 18.5f * u), Offset(x * u, (18.5f - h) * u), stroke, StrokeCap.Round)
                }
            }
            Glyph.LOCATION -> {
                val pin = Path().apply {
                    moveTo(12f * u, 21f * u)
                    cubicTo(8.5f * u, 15f * u, 6f * u, 12f * u, 6f * u, 9f * u)
                    cubicTo(6f * u, 5.5f * u, 8.7f * u, 3.5f * u, 12f * u, 3.5f * u)
                    cubicTo(15.3f * u, 3.5f * u, 18f * u, 5.5f * u, 18f * u, 9f * u)
                    cubicTo(18f * u, 12f * u, 15.5f * u, 15f * u, 12f * u, 21f * u)
                    close()
                }
                drawPath(pin, tint, style = Stroke(stroke))
                drawCircle(tint, 2.2f * u, Offset(12f * u, 9f * u))
            }
        }
    }
}
