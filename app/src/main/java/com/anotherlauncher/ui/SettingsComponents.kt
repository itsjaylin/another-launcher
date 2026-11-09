package com.anotherlauncher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Curated preset swatches for status bar & pill tint.
 */
data class StatusColorPreset(val name: String, val colorInt: Int?)

object ColorPickerDefaults {
    val presets = listOf(
        StatusColorPreset("Auto", null),
        StatusColorPreset("Pure White", 0xFFFFFFFF.toInt()),
        StatusColorPreset("Chalk", 0xFFF1F5F9.toInt()),
        StatusColorPreset("Slate", 0xFF94A3B8.toInt()),
        StatusColorPreset("Mint", 0xFF34C759.toInt()),
        StatusColorPreset("Cyan", 0xFF38BDF8.toInt()),
        StatusColorPreset("Sky Blue", 0xFF3B82F6.toInt()),
        StatusColorPreset("Indigo", 0xFF6366F1.toInt()),
        StatusColorPreset("Lavender", 0xFFA855F7.toInt()),
        StatusColorPreset("Rose", 0xFFFB7185.toInt()),
        StatusColorPreset("Amber", 0xFFF59E0B.toInt()),
        StatusColorPreset("Lime", 0xFF84CC16.toInt()),
    )

    fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
        val h = (hue % 360f + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(r1 + m, g1 + m, b1 + m, 1f)
    }

    fun colorToHsv(colorInt: Int): FloatArray {
        val r = ((colorInt ushr 16) and 0xFF) / 255f
        val g = ((colorInt ushr 8) and 0xFF) / 255f
        val b = (colorInt and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val h = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val s = if (max == 0f) 0f else delta / max
        val v = max
        return floatArrayOf(h, s, v)
    }

    fun parseHex(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        if (hex.length != 6 && hex.length != 8) return null
        if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return try {
            (if (hex.length == 6) "FF$hex" else hex).toULong(16).toInt()
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun formatHex(colorInt: Int?): String {
        if (colorInt == null) return ""
        val alpha = (colorInt ushr 24) and 0xFF
        val rgb = colorInt and 0x00FFFFFF
        return if (alpha == 0xFF) "#%06X".format(rgb) else "#%08X".format(colorInt)
    }
}

/**
 * Standardized Section Group Container with frosted glass card & uniform spacing.
 */
@Composable
fun SettingGroup(
    title: String,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title.uppercase(),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glass(24, frosted = true)
                .clip(RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
        if (!footer.isNullOrBlank()) {
            Text(
                text = footer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Inset Divider for use between setting rows inside a SettingGroup.
 */
@Composable
fun SettingDivider(modifier: Modifier = Modifier, startIndent: Boolean = true) {
    HorizontalDivider(
        modifier = modifier.padding(start = if (startIndent) 66.dp else 16.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

/**
 * Standardized Toggle / Switch Row.
 */
@Composable
fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: Glyph? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = iconTint.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(icon, Modifier.size(20.dp), tint = iconTint)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled
        )
    }
}

/**
 * Standardized Navigation / Link Row.
 */
@Composable
fun SettingNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    valueText: String? = null,
    icon: Glyph? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = iconTint.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(icon, Modifier.size(20.dp), tint = iconTint)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!valueText.isNullOrBlank()) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        GlyphIcon(
            glyph = Glyph.CHEVRON_RIGHT,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

/**
 * Standardized Segmented Control Row for discrete choices.
 */
@Composable
fun <T> SettingSegmentedRow(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: Glyph? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = iconTint.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(icon, Modifier.size(20.dp), tint = iconTint)
                }
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // Segmented pill container
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { option ->
                    val isSelected = option == selected
                    val backgroundColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        animationSpec = tween(150),
                        label = "segmentedBg"
                    )
                    val contentColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(150),
                        label = "segmentedText"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 36.dp)
                            .background(backgroundColor, RoundedCornerShape(13.dp))
                            .clip(RoundedCornerShape(13.dp))
                            .clickable { onSelect(option) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = labelProvider(option),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standardized Action Row (e.g. Set as default home, Cellular signal permission).
 */
@Composable
fun SettingActionRow(
    title: String,
    buttonText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    statusText: String? = null,
    isStatusPositive: Boolean = false,
    icon: Glyph? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    buttonEnabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = iconTint.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(icon, Modifier.size(20.dp), tint = iconTint)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (!statusText.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isStatusPositive) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        contentColor = if (isStatusPositive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        FilledTonalButton(
            onClick = onAction,
            enabled = buttonEnabled,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(buttonText, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Color Setting Row that opens the interactive Color Picker Modal.
 */
@Composable
fun SettingColorPickerRow(
    title: String,
    currentColor: Int?,
    onColorSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: Glyph = Glyph.PALETTE,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { pickerOpen = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = iconTint.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(icon, Modifier.size(20.dp), tint = iconTint)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Color Swatch Badge
        ColorSwatchPreview(
            colorInt = currentColor,
            size = 36.dp,
            onClick = { pickerOpen = true }
        )
    }

    if (pickerOpen) {
        ColorPickerModal(
            currentColor = currentColor,
            onDismiss = { pickerOpen = false },
            onApply = { selectedColor ->
                onColorSelected(selectedColor)
            }
        )
    }
}

/**
 * Circular Swatch Preview with Auto/Dynamic multi-tone gradient support.
 */
@Composable
fun ColorSwatchPreview(
    colorInt: Int?,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    if (colorInt == null) {
        // Dynamic / Auto preview: two-tone split or dynamic gradient
        Box(
            modifier = modifier
                .size(size)
                .then(clickModifier)
                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFF8FAFF),
                            Color(0xFF64748B),
                            Color(0xFF0F172A)
                        )
                    ),
                    shape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "A",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        val color = Color(colorInt)
        Box(
            modifier = modifier
                .size(size)
                .then(clickModifier)
                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .background(color, shape)
        )
    }
}

/**
 * Interactive Color Picker Modal with Live Status Preview, Presets, Hue Slider, and Hex Input.
 */
@Composable
fun ColorPickerModal(
    currentColor: Int?,
    onDismiss: () -> Unit,
    onApply: (Int?) -> Unit,
) {
    var selectedColor by remember(currentColor) { mutableStateOf(currentColor) }
    val initialHsv = remember(currentColor) {
        if (currentColor != null) ColorPickerDefaults.colorToHsv(currentColor)
        else floatArrayOf(210f, 0.7f, 0.95f)
    }
    var hue by rememberSaveable { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(initialHsv[1]) }
    var value by rememberSaveable { mutableFloatStateOf(initialHsv[2]) }

    var hexText by rememberSaveable(selectedColor) {
        mutableStateOf(ColorPickerDefaults.formatHex(selectedColor))
    }
    var hexError by remember { mutableStateOf<String?>(null) }
    val isDarkTheme = isSystemInDarkTheme()
    val focusManager = LocalFocusManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                GlyphIcon(
                                    Glyph.PALETTE,
                                    Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "Status Color Tint",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            GlyphIcon(Glyph.CLOSE, Modifier.size(18.dp))
                        }
                    }

                    // Live Status Pill Preview
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Live Status Preview",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        LiveStatusPreviewCard(selectedColor = selectedColor, isDark = isDarkTheme)
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // Curated Presets
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Presets",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (selectedColor != null) {
                                TextButton(
                                    onClick = {
                                        selectedColor = null
                                        hexText = ""
                                        hexError = null
                                        onApply(null)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Reset to auto", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        // Presets Grid / Flow
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ColorPickerDefaults.presets.take(6).forEach { preset ->
                                val pColor = preset.colorInt
                                PresetItem(
                                    preset = preset,
                                    isSelected = (pColor == null && selectedColor == null) ||
                                            (pColor != null && selectedColor == pColor),
                                    onClick = {
                                        selectedColor = pColor
                                        if (pColor != null) {
                                            val hsv = ColorPickerDefaults.colorToHsv(pColor)
                                            hue = hsv[0]
                                            saturation = hsv[1]
                                            value = hsv[2]
                                            hexText = ColorPickerDefaults.formatHex(pColor)
                                        } else {
                                            hexText = ""
                                        }
                                        hexError = null
                                        onApply(pColor)
                                    }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ColorPickerDefaults.presets.drop(6).take(6).forEach { preset ->
                                val pColor = preset.colorInt
                                PresetItem(
                                    preset = preset,
                                    isSelected = (pColor != null && selectedColor == pColor),
                                    onClick = {
                                        selectedColor = pColor
                                        if (pColor != null) {
                                            val hsv = ColorPickerDefaults.colorToHsv(pColor)
                                            hue = hsv[0]
                                            saturation = hsv[1]
                                            value = hsv[2]
                                            hexText = ColorPickerDefaults.formatHex(pColor)
                                        }
                                        hexError = null
                                        onApply(pColor)
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // Continuous Custom Hue & Saturation/Value Controls
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Custom Color Tuner",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Rainbow Hue Slider
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Hue", style = MaterialTheme.typography.bodySmall)
                                Text("${hue.toInt()}°", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                            RainbowHueSlider(
                                hue = hue,
                                onHueChange = { newHue ->
                                    hue = newHue
                                    val newColor = ColorPickerDefaults.hsvToColor(hue, saturation.coerceAtLeast(0.1f), value.coerceAtLeast(0.2f)).toArgb()
                                    selectedColor = newColor
                                    hexText = ColorPickerDefaults.formatHex(newColor)
                                    hexError = null
                                    onApply(newColor)
                                }
                            )
                        }

                        // Saturation Slider
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Saturation", style = MaterialTheme.typography.bodySmall)
                                Text("${(saturation * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                            SaturationSlider(
                                hue = hue,
                                saturation = saturation,
                                value = value,
                                onSaturationChange = { newSat ->
                                    saturation = newSat
                                    val newColor = ColorPickerDefaults.hsvToColor(hue, saturation, value).toArgb()
                                    selectedColor = newColor
                                    hexText = ColorPickerDefaults.formatHex(newColor)
                                    hexError = null
                                    onApply(newColor)
                                }
                            )
                        }

                        // Lightness / Value Slider
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Brightness", style = MaterialTheme.typography.bodySmall)
                                Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                            BrightnessSlider(
                                hue = hue,
                                saturation = saturation,
                                value = value,
                                onValueChange = { newVal ->
                                    value = newVal
                                    val newColor = ColorPickerDefaults.hsvToColor(hue, saturation, value).toArgb()
                                    selectedColor = newColor
                                    hexText = ColorPickerDefaults.formatHex(newColor)
                                    hexError = null
                                    onApply(newColor)
                                }
                            )
                        }

                        // Direct Hex Code Field
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = hexText,
                                onValueChange = { input ->
                                    hexText = input
                                    hexError = null
                                    if (input.isNotBlank()) {
                                        val parsed = ColorPickerDefaults.parseHex(input)
                                        if (parsed != null) {
                                            selectedColor = parsed
                                            val hsv = ColorPickerDefaults.colorToHsv(parsed)
                                            hue = hsv[0]
                                            saturation = hsv[1]
                                            value = hsv[2]
                                            onApply(parsed)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text("Hex code") },
                                placeholder = { Text("#FFFFFF") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    if (hexText.isNotBlank()) {
                                        val parsed = ColorPickerDefaults.parseHex(hexText)
                                        if (parsed == null) hexError = "Invalid hex code. Use #RRGGBB."
                                    }
                                }),
                                isError = hexError != null
                            )
                            FilledTonalButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    if (hexText.isBlank()) {
                                        selectedColor = null
                                        onApply(null)
                                    } else {
                                        val parsed = ColorPickerDefaults.parseHex(hexText)
                                        if (parsed != null) {
                                            selectedColor = parsed
                                            onApply(parsed)
                                        } else {
                                            hexError = "Invalid hex code. Use #RRGGBB."
                                        }
                                    }
                                }
                            ) {
                                Text("Apply")
                            }
                        }
                        if (hexError != null) {
                            Text(hexError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    // Footer Done Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Done", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Realistic Live Preview Card demonstrating status pill appearance.
 */
@Composable
private fun LiveStatusPreviewCard(selectedColor: Int?, isDark: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) Color(0xFF0F172A) else Color(0xFFE2E8F0),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mock Status Pill
            val effectiveTint = if (selectedColor != null) {
                Color(selectedColor)
            } else {
                if (isDark) Color(0xFFF8FAFF) else Color(0xFF15202C)
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f)
                ),
                contentColor = effectiveTint
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "9:41",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = effectiveTint
                    )
                    Text("•", color = effectiveTint.copy(alpha = 0.5f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        GlyphIcon(Glyph.SIGNAL, Modifier.size(16.dp), tint = effectiveTint)
                        Text(
                            text = "5G",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = effectiveTint
                        )
                    }
                    Text("•", color = effectiveTint.copy(alpha = 0.5f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "88%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = effectiveTint
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 18.dp, height = 10.dp)
                                .border(1.dp, effectiveTint, RoundedCornerShape(2.dp))
                                .padding(1.5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.88f)
                                    .background(effectiveTint, RoundedCornerShape(1.dp))
                            )
                        }
                    }
                }
            }

            Text(
                text = if (selectedColor == null) "Dynamic Auto tint adapts automatically to light/dark wallpaper luminance."
                else "Custom tint applied directly across clock, battery, and duo pill indicators.",
                style = MaterialTheme.typography.labelSmall,
                color = effectiveTint.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Individual Preset Swatch Item with active ring and checkmark.
 */
@Composable
private fun PresetItem(
    preset: StatusColorPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (preset.colorInt == null) {
                // Auto swatch
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            if (isSelected) 2.5.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        )
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFF8FAFF), Color(0xFF64748B), Color(0xFF0F172A))
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        GlyphIcon(Glyph.CHECK, Modifier.size(16.dp), tint = Color.White)
                    } else {
                        Text("A", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                val colorVal = preset.colorInt
                val swatchColor = Color(colorVal)
                val checkColor = if (com.anotherlauncher.duo.WallpaperContrast.luminance(colorVal) > 0.4) Color.Black else Color.White
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            if (isSelected) 2.5.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        )
                        .background(swatchColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        GlyphIcon(Glyph.CHECK, Modifier.size(16.dp), tint = checkColor)
                    }
                }
            }
        }
        Text(
            text = preset.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Rainbow Hue Slider Track.
 */
@Composable
private fun RainbowHueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val rainbowColors = remember {
        listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
            Color.Red
        )
    }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val thumbOffset = ((hue / 360f) * widthPx).coerceIn(0f, widthPx)

        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(rainbowColors))
                .pointerInput(widthPx) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / widthPx).coerceIn(0f, 1f)
                        onHueChange(ratio * 360f)
                    }
                }
                .pointerInput(widthPx) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val ratio = (change.position.x / widthPx).coerceIn(0f, 1f)
                        onHueChange(ratio * 360f)
                    }
                }
        )

        // Thumb indicator
        val thumbColor = ColorPickerDefaults.hsvToColor(hue, 1f, 1f)
        val thumbOffsetDp = with(density) { (thumbOffset - 14.dp.toPx()).toDp() }
        Box(
            modifier = Modifier
                .offset(x = thumbOffsetDp.coerceAtLeast(0.dp))
                .size(28.dp)
                .border(2.5.dp, Color.White, CircleShape)
                .background(thumbColor, CircleShape)
        )
    }
}

/**
 * Saturation Slider Track.
 */
@Composable
private fun SaturationSlider(
    hue: Float,
    saturation: Float,
    value: Float,
    onSaturationChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val startColor = ColorPickerDefaults.hsvToColor(hue, 0f, value)
    val endColor = ColorPickerDefaults.hsvToColor(hue, 1f, value)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val thumbOffset = (saturation * widthPx).coerceIn(0f, widthPx)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(listOf(startColor, endColor)))
                .pointerInput(widthPx) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / widthPx).coerceIn(0f, 1f)
                        onSaturationChange(ratio)
                    }
                }
                .pointerInput(widthPx) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val ratio = (change.position.x / widthPx).coerceIn(0f, 1f)
                        onSaturationChange(ratio)
                    }
                }
        )

        val thumbColor = ColorPickerDefaults.hsvToColor(hue, saturation, value)
        val thumbOffsetDp = with(density) { (thumbOffset - 14.dp.toPx()).toDp() }
        Box(
            modifier = Modifier
                .offset(x = thumbOffsetDp.coerceAtLeast(0.dp))
                .size(28.dp)
                .border(2.5.dp, Color.White, CircleShape)
                .background(thumbColor, CircleShape)
        )
    }
}

/**
 * Brightness / Value Slider Track.
 */
@Composable
private fun BrightnessSlider(
    hue: Float,
    saturation: Float,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val startColor = ColorPickerDefaults.hsvToColor(hue, saturation, 0f)
    val endColor = ColorPickerDefaults.hsvToColor(hue, saturation, 1f)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val thumbOffset = (value * widthPx).coerceIn(0f, widthPx)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(listOf(startColor, endColor)))
                .pointerInput(widthPx) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / widthPx).coerceIn(0f, 1f)
                        onValueChange(ratio)
                    }
                }
                .pointerInput(widthPx) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val ratio = (change.position.x / widthPx).coerceIn(0f, 1f)
                        onValueChange(ratio)
                    }
                }
        )

        val thumbColor = ColorPickerDefaults.hsvToColor(hue, saturation, value)
        val thumbOffsetDp = with(density) { (thumbOffset - 14.dp.toPx()).toDp() }
        Box(
            modifier = Modifier
                .offset(x = thumbOffsetDp.coerceAtLeast(0.dp))
                .size(28.dp)
                .border(2.5.dp, Color.White, CircleShape)
                .background(thumbColor, CircleShape)
        )
    }
}

/**
 * Clean Stat Tile for Hardware & Fold Diagnostics Grid.
 */
@Composable
fun SettingStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: Glyph? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (icon != null) {
                    GlyphIcon(icon, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
