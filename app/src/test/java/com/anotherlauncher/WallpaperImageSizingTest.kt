package com.anotherlauncher

import com.anotherlauncher.ui.WallpaperImageSizing
import org.junit.Assert.*
import org.junit.Test

class WallpaperImageSizingTest {
    @Test fun smallPhotosKeepTheirOriginalResolution() {
        assertEquals(1200 to 800, WallpaperImageSizing.decodeSize(1200, 800, 8_000_000))
    }

    @Test fun highResolutionCameraPhotoFitsBudgetWithoutDistortion() {
        val (width, height) = WallpaperImageSizing.decodeSize(16320, 12240, 8_000_000)
        assertTrue(width.toLong() * height <= 8_000_000)
        assertEquals(4.0 / 3.0, width.toDouble() / height, 0.001)
        assertTrue(width > 3000)
    }

    @Test fun portraitAndLandscapeReceiveTheSameQuality() {
        val landscape = WallpaperImageSizing.decodeSize(12000, 9000, 4_000_000)
        val portrait = WallpaperImageSizing.decodeSize(9000, 12000, 4_000_000)
        assertEquals(landscape.first to landscape.second, portrait.second to portrait.first)
    }

    @Test fun extremeAspectRatiosAndLargeDimensionsStayWithinBudget() {
        for ((w, h) in listOf(1 to Int.MAX_VALUE, Int.MAX_VALUE to 1, Int.MAX_VALUE to Int.MAX_VALUE)) {
            val (width, height) = WallpaperImageSizing.decodeSize(w, h, 1000)
            assertTrue(width > 0 && height > 0)
            assertTrue(width.toLong() * height <= 1000)
        }
    }

    @Test fun memoryBudgetScalesDownOnSmallerHeaps() {
        assertEquals(2_097_152L, WallpaperImageSizing.pixelBudget(128L * 1024 * 1024))
        assertEquals(8_000_000L, WallpaperImageSizing.pixelBudget(1024L * 1024 * 1024))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidImageDimensionsAreRejected() {
        WallpaperImageSizing.decodeSize(0, 100, 1000)
    }
}
