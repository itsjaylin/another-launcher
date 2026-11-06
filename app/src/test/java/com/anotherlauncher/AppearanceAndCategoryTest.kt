package com.anotherlauncher

import com.anotherlauncher.data.AppCategory
import com.anotherlauncher.duo.WallpaperContrast
import org.junit.Assert.*
import org.junit.Test

class AppearanceAndCategoryTest {
    @Test fun lightAndDarkWallpapersChooseOppositeForegrounds() {
        assertTrue(WallpaperContrast.useDarkText(listOf(0xFFFFFFFF.toInt(), 0xFFECE3D2.toInt()), false))
        assertFalse(WallpaperContrast.useDarkText(listOf(0xFF101625.toInt(), 0xFF263A4D.toInt()), true))
    }
    @Test fun metadataUnavailableUsesExplicitFallback() {
        assertTrue(WallpaperContrast.useDarkText(emptyList(), true))
        assertFalse(WallpaperContrast.useDarkText(emptyList(), false))
    }
    @Test fun luminanceUsesLinearSrgb() {
        assertEquals(0.0, WallpaperContrast.luminance(0xFF000000.toInt()), .0001)
        assertEquals(1.0, WallpaperContrast.luminance(0xFFFFFFFF.toInt()), .0001)
        assertEquals(.21586, WallpaperContrast.luminance(0xFF808080.toInt()), .0001)
    }
    @Test fun appMetadataTakesPrecedenceOverPackageTokens() {
        assertEquals(AppCategory.GAMES, AppCategory.classify(0, "example.camera.game"))
        assertEquals(AppCategory.PRODUCTIVITY, AppCategory.classify(7, "example.messages"))
    }
    @Test fun unclassifiedAppsUseConservativeTokensAndUnknownFallback() {
        assertEquals(AppCategory.SOCIAL, AppCategory.classify(-1, "com.samsung.android.messaging"))
        assertEquals(AppCategory.MEDIA, AppCategory.classify(-1, "com.example.camera"))
        assertEquals(AppCategory.OTHER, AppCategory.classify(-1, "com.example.camerashop"))
        assertEquals(AppCategory.OTHER, AppCategory.classify(-1, "com.example.unknown"))
    }
    @Test fun socialCoversCommonMessagingAndNetworks() {
        assertEquals("Social", AppCategory.SOCIAL.title)
        assertEquals(AppCategory.SOCIAL, AppCategory.classify(-1, "com.instagram.android"))
        assertEquals(AppCategory.SOCIAL, AppCategory.classify(-1, "org.telegram.messenger"))
        assertEquals(AppCategory.SOCIAL, AppCategory.classify(-1, "com.whatsapp"))
    }
    @Test fun gameTokensStayConservative() {
        assertEquals(AppCategory.GAMES, AppCategory.classify(-1, "com.example.game"))
        assertEquals(AppCategory.OTHER, AppCategory.classify(-1, "com.example.gamershub"))
    }
    @Test fun anotherLauncherPackageClassifiesAsTools() {
        assertEquals(AppCategory.TOOLS, AppCategory.classify(-1, "com.anotherlauncher"))
        assertEquals("Utilities", AppCategory.TOOLS.title)
    }
    @Test fun appCategoryClassificationSupportsLabelsAndGameFlag() {
        assertEquals(AppCategory.GAMES, AppCategory.classify(-1, "com.unknown.game", isGame = true))
        assertEquals(AppCategory.GAMES, AppCategory.classify(-1, "com.example.noname", label = "Speed Racing"))
        assertEquals(AppCategory.SHOPPING, AppCategory.classify(-1, "com.example.noname", label = "Quick Food Delivery"))
        assertEquals(AppCategory.PRODUCTIVITY, AppCategory.classify(-1, "com.example.noname", label = "My Calendar Notes"))
    }
    @Test fun newCategoryTitlesAreValid() {
        assertEquals("Photo & Video", AppCategory.CREATIVITY.title)
        assertEquals("Entertainment", AppCategory.ENTERTAINMENT.title)
        assertEquals("Shopping & Food", AppCategory.SHOPPING.title)
    }
}
