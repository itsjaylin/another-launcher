package com.anotherlauncher.ui

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Data holder for in-app curated wallpaper presets. */
data class WallpaperPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val topColor: Long,
    val bottomColor: Long,
    val accentColor: Long? = null,
) {
    fun generateBitmap(width: Int = 1080, height: Int = 2400): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        paint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            topColor.toInt(), bottomColor.toInt(), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        accentColor?.let { accent ->
            val radialPaint = Paint()
            radialPaint.shader = RadialGradient(
                width * 0.5f, height * 0.35f, width * 0.8f,
                accent.toInt(), 0x00000000, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(width * 0.5f, height * 0.35f, width * 0.8f, radialPaint)
        }
        return bitmap
    }
}

val BUILTIN_WALLPAPER_PRESETS = listOf(
    WallpaperPreset("midnight_oled", "Midnight OLED", "Deep black & indigo glow", 0xFF05070EL, 0xFF111827L, 0x403B82F6L),
    WallpaperPreset("emerald_dunes", "Emerald Velvet", "Dark forest & emerald green", 0xFF062C1BL, 0xFF0F172AL, 0x3510B981L),
    WallpaperPreset("solar_dusk", "Solar Dusk", "Violet & warm amber sunset", 0xFF2E1065L, 0xFF881337L, 0x45F59E0BL),
    WallpaperPreset("cobalt_wave", "Cobalt Wave", "Royal blue & cyan gradient", 0xFF0F172AL, 0xFF1E3A8AL, 0x4006B6D4L),
    WallpaperPreset("rose_quartz", "Rose Quartz", "Mauve & soft coral rose", 0xFF1E1B4BL, 0xFF4C1D95L, 0x35F43F5EL),
    WallpaperPreset("slate_graphite", "Slate Charcoal", "Minimal dark gray texture", 0xFF18181BL, 0xFF09090BL, 0x3094A3B8L),
)

/** Android's decoder honors EXIF (including mirrored orientations) before returning a bitmap. */
private fun decodeOrientedBitmap(context: Context, uri: Uri): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val (width, height) = WallpaperImageSizing.decodeSize(
            info.size.width, info.size.height,
            WallpaperImageSizing.pixelBudget(Runtime.getRuntime().maxMemory()),
        )
        decoder.setTargetSize(width, height)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}

/** Rotates a bitmap by [degrees] angle. */
private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degrees)
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

/**
 * In-App Custom Wallpaper Picker & Theme Settings Sheet.
 *
 * Allows users to set high-res custom wallpapers directly from their photo gallery or choose from
 * curated built-in themes without opening 3rd-party wallpaper choosers or system setting pages.
 */
@Composable
fun WallpaperPickerSheet(
    onDismiss: () -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedTitle by remember { mutableStateOf<String?>(null) }
    var applyTarget by remember { mutableIntStateOf(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) }
    var isSettingWallpaper by remember { mutableStateOf(false) }
    var isProcessingImage by remember { mutableStateOf(false) }
    val isBusy = isSettingWallpaper || isProcessingImage

    // Start on Main so rapid taps cannot queue multiple large allocations or overwrite a new selection.
    fun processImage(title: String, operation: suspend () -> Bitmap) {
        if (isProcessingImage || isSettingWallpaper) return
        isProcessingImage = true
        scope.launch {
            try {
                selectedBitmap = operation()
                selectedTitle = title
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OutOfMemoryError) {
                Toast.makeText(context, "Not enough memory for this photo. Try a smaller image.", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Could not process selected photo.", Toast.LENGTH_SHORT).show()
            } finally {
                isProcessingImage = false
            }
        }
    }

    fun rotateSelection(degrees: Float) {
        val source = selectedBitmap ?: return
        processImage(selectedTitle ?: "Photo from Gallery") {
            withContext(Dispatchers.Default) { rotateBitmap(source, degrees) }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            processImage("Photo from Gallery") {
                withContext(Dispatchers.IO) { decodeOrientedBitmap(context, it) }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(.92f)
                .heightIn(max = 760.dp)
                .glass(28, frosted = true),
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(Glyph.WALLPAPER, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Wallpaper Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Customize your home screen background",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        GlyphIcon(Glyph.CLOSE, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isProcessingImage) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                    // Photo Gallery Picker Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(enabled = !isBusy) { photoPickerLauncher.launch("image/*") },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                GlyphIcon(Glyph.PLUS, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Choose Photo from Gallery",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Select any picture from your device photos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            GlyphIcon(Glyph.CHEVRON_RIGHT, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Built-in Wallpaper Presets
                    Text(
                        text = "BUILT-IN PRESETS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BUILTIN_WALLPAPER_PRESETS.chunked(2).forEach { rowPresets ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowPresets.forEach { preset ->
                                    val isSelected = selectedTitle == preset.name
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(110.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable(enabled = !isBusy) {
                                                processImage(preset.name) {
                                                    withContext(Dispatchers.Default) { preset.generateBitmap() }
                                                }
                                            },
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(preset.topColor),
                                        border = androidx.compose.foundation.BorderStroke(
                                            if (isSelected) 2.5.dp else 1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f)
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                                        listOf(Color(preset.topColor), Color(preset.bottomColor))
                                                    )
                                                )
                                                .padding(12.dp)
                                        ) {
                                            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                                                Text(
                                                    text = preset.name,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = preset.subtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.75f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary)
                                                        .align(Alignment.TopEnd),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    GlyphIcon(Glyph.CHECK, Modifier.size(12.dp), tint = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Active Selected Wallpaper Preview & Apply Controls
                    selectedBitmap?.let { bitmap ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "PREVIEW: ${selectedTitle ?: "Selected Wallpaper"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(12.dp))

                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Wallpaper preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(220.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(1.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                )

                                Spacer(Modifier.height(10.dp))

                                // Manual Rotation Adjustment Controls
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(17.dp))
                                            .clickable(enabled = !isBusy) { rotateSelection(-90f) },
                                        shape = RoundedCornerShape(17.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "↺ Rotate Left",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(17.dp))
                                            .clickable(enabled = !isBusy) { rotateSelection(90f) },
                                        shape = RoundedCornerShape(17.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "↻ Rotate Right",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                run {
                                    Text(
                                        text = "APPLY TO",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val targetBoth = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                                        val targetHome = WallpaperManager.FLAG_SYSTEM
                                        val targetLock = WallpaperManager.FLAG_LOCK

                                        TargetChip(
                                            label = "Both",
                                            selected = applyTarget == targetBoth,
                                            modifier = Modifier.weight(1f),
                                            onClick = { if (!isBusy) applyTarget = targetBoth }
                                        )
                                        TargetChip(
                                            label = "Home",
                                            selected = applyTarget == targetHome,
                                            modifier = Modifier.weight(1f),
                                            onClick = { if (!isBusy) applyTarget = targetHome }
                                        )
                                        TargetChip(
                                            label = "Lock",
                                            selected = applyTarget == targetLock,
                                            modifier = Modifier.weight(1f),
                                            onClick = { if (!isBusy) applyTarget = targetLock }
                                        )
                                    }
                                    Spacer(Modifier.height(16.dp))
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .clickable(enabled = !isBusy) {
                                            isSettingWallpaper = true
                                            val target = applyTarget
                                            scope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, target)
                                                    }
                                                    Toast.makeText(context, "Wallpaper set successfully!", Toast.LENGTH_SHORT).show()
                                                    onDismiss()
                                                } catch (cancelled: CancellationException) {
                                                    throw cancelled
                                                } catch (_: OutOfMemoryError) {
                                                    Toast.makeText(context, "Not enough memory to set this wallpaper. Try a smaller image.", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Could not set wallpaper: ${e.message}", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isSettingWallpaper = false
                                                }
                                            }
                                        },
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isBusy) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text(
                                                text = "Apply Wallpaper",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // System Settings Fallback Option
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(onClick = {
                            onDismiss()
                            onOpenSystemSettings()
                        }) {
                            Text(
                                text = "Open System Wallpaper & Style",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
