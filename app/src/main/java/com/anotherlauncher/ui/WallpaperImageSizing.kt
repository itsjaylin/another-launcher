package com.anotherlauncher.ui

import kotlin.math.floor
import kotlin.math.sqrt

/** Bounds decoded pixels before allocation, preserving aspect ratio and never upscaling. */
object WallpaperImageSizing {
    // Reserve room for a source and rotated copy, the Compose preview, and launcher/widget state.
    // Eight bytes per pixel also covers wide-gamut RGBA_F16 photos.
    fun pixelBudget(heapBytes: Long): Long = (heapBytes / 8 / 8).coerceIn(1, 8_000_000)

    fun decodeSize(width: Int, height: Int, maxPixels: Long): Pair<Int, Int> {
        require(width > 0 && height > 0 && maxPixels > 0)
        val pixels = width.toLong() * height
        if (pixels <= maxPixels) return width to height
        val scale = sqrt(maxPixels.toDouble() / pixels)
        val targetWidth = minOf(floor(width * scale).toLong().coerceAtLeast(1), maxPixels).toInt()
        // For extremely thin images the one-pixel minimum must still respect the budget.
        val targetHeight = minOf(
            floor(height * scale).toLong().coerceAtLeast(1),
            maxPixels / targetWidth,
        ).toInt().coerceAtLeast(1)
        return targetWidth to targetHeight
    }
}
