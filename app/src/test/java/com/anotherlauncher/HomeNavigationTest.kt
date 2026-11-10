package com.anotherlauncher

import com.anotherlauncher.ui.HomeNavigation
import com.anotherlauncher.ui.HomeSurface
import org.junit.Assert.*
import org.junit.Test

class HomeNavigationTest {
    @Test fun everySurfaceCanReturnHomeWithoutKeepingAnOverlayOrReplacement() {
        for (surface in HomeSurface.entries) {
            val navigation = HomeNavigation().open(surface, if (surface == HomeSurface.WIDGETS) "tile" else null)
            val home = navigation.open(HomeSurface.HOME)
            assertEquals(HomeNavigation(), home)
            assertFalse(home.fullPage)
        }
    }

    @Test fun wallpaperFromSettingsKeepsSettingsBehindItAndReturnsThere() {
        val wallpaper = HomeNavigation(HomeSurface.SETTINGS).open(HomeSurface.WALLPAPER)
        assertTrue(wallpaper.showsSettings)
        assertEquals(HomeSurface.WALLPAPER, wallpaper.surface)
        assertEquals(HomeNavigation(HomeSurface.SETTINGS), wallpaper.dismiss())
    }

    @Test fun wallpaperFromHomeDismissesToHome() {
        val wallpaper = HomeNavigation().open(HomeSurface.WALLPAPER)
        assertFalse(wallpaper.showsSettings)
        assertEquals(HomeNavigation(), wallpaper.dismiss())
    }

    @Test fun pagesFromSettingsReturnThereWithoutRenderingSettingsAtTheSameTime() {
        val pages = HomeNavigation(HomeSurface.SETTINGS).open(HomeSurface.PAGES)
        assertFalse(pages.showsSettings)
        assertEquals(HomeNavigation(HomeSurface.SETTINGS), pages.dismiss())
    }

    @Test fun choosingAPageCanExplicitlyExitTheSettingsReturnPath() {
        val pages = HomeNavigation(HomeSurface.SETTINGS).open(HomeSurface.PAGES)
        assertEquals(HomeNavigation(), pages.open(HomeSurface.HOME))
    }

    @Test fun widgetReplacementOnlyLivesInThePicker() {
        val picker = HomeNavigation().open(HomeSurface.WIDGETS, "missing-widget")
        assertEquals("missing-widget", picker.replacementItemId)
        assertNull(picker.dismiss().replacementItemId)
        assertNull(picker.open(HomeSurface.WEATHER).replacementItemId)
        assertNull(picker.dismiss().open(HomeSurface.WIDGETS).replacementItemId)
    }

    @Test fun openingSettingsFromEverySurfaceClearsReturnAndReplacementState() {
        for (surface in HomeSurface.entries) {
            assertEquals(HomeNavigation(HomeSurface.SETTINGS), HomeNavigation().open(surface).open(HomeSurface.SETTINGS))
        }
    }

    @Test fun onlyPagesAndWallpaperRetainASettingsReturnPath() {
        for (surface in HomeSurface.entries) {
            val destination = HomeNavigation(HomeSurface.SETTINGS).open(surface)
            assertEquals(surface == HomeSurface.PAGES || surface == HomeSurface.WALLPAPER, destination.returnToSettings)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidReturnPathIsRejected() { HomeNavigation(HomeSurface.WEATHER, returnToSettings = true) }

    @Test(expected = IllegalArgumentException::class)
    fun replacementCannotLeakIntoSettings() { HomeNavigation(HomeSurface.SETTINGS, replacementItemId = "tile") }
}
