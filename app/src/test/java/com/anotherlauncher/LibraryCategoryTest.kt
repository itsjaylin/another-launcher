package com.anotherlauncher

import android.content.pm.ApplicationInfo
import com.anotherlauncher.ui.LibraryCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCategoryTest {

    @Test
    fun packageHintsCategorizeBrowsersAndUtilities() {
        assertEquals(LibraryCategory.UTILITIES, LibraryCategory.of("com.android.chrome"))
        assertEquals(LibraryCategory.UTILITIES, LibraryCategory.of("org.mozilla.firefox"))
        assertEquals(LibraryCategory.UTILITIES, LibraryCategory.of("com.sec.android.app.myfiles"))
        assertEquals(LibraryCategory.UTILITIES, LibraryCategory.of("com.google.android.calculator"))
    }

    @Test
    fun socialAndMessagingPackagesCategorizedCorrectly() {
        assertEquals(LibraryCategory.SOCIAL, LibraryCategory.of("com.discord"))
        assertEquals(LibraryCategory.SOCIAL, LibraryCategory.of("com.instagram.android"))
        assertEquals(LibraryCategory.SOCIAL, LibraryCategory.of("com.twitter.android"))
        assertEquals(LibraryCategory.SOCIAL, LibraryCategory.of("org.telegram.messenger"))
    }

    @Test
    fun productivityAndFinancePackagesCategorizedCorrectly() {
        assertEquals(LibraryCategory.PRODUCTIVITY, LibraryCategory.of("com.google.android.gm"))
        assertEquals(LibraryCategory.PRODUCTIVITY, LibraryCategory.of("com.google.android.calendar"))
        assertEquals(LibraryCategory.PRODUCTIVITY, LibraryCategory.of("com.squareup.cash"))
    }

    @Test
    fun entertainmentAndStreamingPackagesCategorizedCorrectly() {
        assertEquals(LibraryCategory.ENTERTAINMENT, LibraryCategory.of("com.google.android.youtube"))
        assertEquals(LibraryCategory.ENTERTAINMENT, LibraryCategory.of("com.spotify.music"))
        assertEquals(LibraryCategory.ENTERTAINMENT, LibraryCategory.of("com.netflix.mediaclient"))
    }

    @Test
    fun fallbackUsesDeclaredApplicationInfoCategory() {
        assertEquals(LibraryCategory.GAMES, LibraryCategory.of("com.random.unrecognized.app", platformCategory = ApplicationInfo.CATEGORY_GAME))
        assertEquals(LibraryCategory.SOCIAL, LibraryCategory.of("com.random.unrecognized.app", platformCategory = ApplicationInfo.CATEGORY_SOCIAL))
        assertEquals(LibraryCategory.PRODUCTIVITY, LibraryCategory.of("com.random.unrecognized.app", platformCategory = ApplicationInfo.CATEGORY_PRODUCTIVITY))
        assertEquals(LibraryCategory.TRAVEL, LibraryCategory.of("com.random.unrecognized.app", platformCategory = ApplicationInfo.CATEGORY_MAPS))
        assertEquals(LibraryCategory.INFO, LibraryCategory.of("com.random.unrecognized.app", platformCategory = ApplicationInfo.CATEGORY_NEWS))
    }

    @Test
    fun popularGamesCategorizedCorrectly() {
        assertEquals(LibraryCategory.GAMES, LibraryCategory.of("com.supercell.clashroyale"))
        assertEquals(LibraryCategory.GAMES, LibraryCategory.of("com.nianticlabs.pokemongo"))
        assertEquals(LibraryCategory.GAMES, LibraryCategory.of("com.valvesoftware.android.steam.community"))
        assertEquals(LibraryCategory.GAMES, LibraryCategory.of("com.activision.callofduty.shooter"))
        assertEquals(LibraryCategory.GAMES, LibraryCategory.of("com.mojang.minecraftpe"))
        assertEquals(LibraryCategory.GAMES, LibraryCategory.of("com.king.candycrushsaga"))
        assertEquals(LibraryCategory.GAMES, LibraryCategory.of("com.tencent.ig"))
    }

    @Test
    fun creativityAndPhotoVideoCategorizedCorrectly() {
        assertEquals(LibraryCategory.CREATIVITY, LibraryCategory.of("com.lemon.lvoverseas", appLabel = "CapCut"))
        assertEquals(LibraryCategory.CREATIVITY, LibraryCategory.of("com.niksoftware.snapseed"))
        assertEquals(LibraryCategory.CREATIVITY, LibraryCategory.of("com.adobe.lrmobile"))
        assertEquals(LibraryCategory.CREATIVITY, LibraryCategory.of("com.canva.editor"))
        assertEquals(LibraryCategory.CREATIVITY, LibraryCategory.of("com.sec.android.app.camera"))
    }

    @Test
    fun shoppingAndFoodDeliveryCategorizedCorrectly() {
        assertEquals(LibraryCategory.SHOPPING, LibraryCategory.of("com.dd.doordash"))
        assertEquals(LibraryCategory.SHOPPING, LibraryCategory.of("com.ubercab.eats"))
        assertEquals(LibraryCategory.SHOPPING, LibraryCategory.of("com.instacart.client"))
        assertEquals(LibraryCategory.SHOPPING, LibraryCategory.of("com.starbucks.mobilecard"))
        assertEquals(LibraryCategory.SHOPPING, LibraryCategory.of("com.mcdonalds.app"))
        assertEquals(LibraryCategory.SHOPPING, LibraryCategory.of("com.amazon.mShop.android.shopping"))
        assertEquals(LibraryCategory.SHOPPING, LibraryCategory.of("com.einnovation.temu"))
    }

    @Test
    fun travelAndMapsCategorizedCorrectly() {
        assertEquals(LibraryCategory.TRAVEL, LibraryCategory.of("com.google.android.apps.maps"))
        assertEquals(LibraryCategory.TRAVEL, LibraryCategory.of("com.ubercab"))
        assertEquals(LibraryCategory.TRAVEL, LibraryCategory.of("me.lyft.android"))
        assertEquals(LibraryCategory.TRAVEL, LibraryCategory.of("com.booking"))
        assertEquals(LibraryCategory.TRAVEL, LibraryCategory.of("com.airbnb.android"))
        assertEquals(LibraryCategory.TRAVEL, LibraryCategory.of("com.delta.mobile.android"))
    }

    @Test
    fun infoAndReadingCategorizedCorrectly() {
        assertEquals(LibraryCategory.INFO, LibraryCategory.of("com.amazon.kindle"))
        assertEquals(LibraryCategory.INFO, LibraryCategory.of("com.duolingo"))
        assertEquals(LibraryCategory.INFO, LibraryCategory.of("com.strava"))
        assertEquals(LibraryCategory.INFO, LibraryCategory.of("com.weather.Weather"))
        assertEquals(LibraryCategory.INFO, LibraryCategory.of("org.wikipedia"))
    }

    @Test
    fun aiAndModernFinanceCategorizedCorrectly() {
        assertEquals(LibraryCategory.PRODUCTIVITY, LibraryCategory.of("com.openai.chatgpt"))
        assertEquals(LibraryCategory.PRODUCTIVITY, LibraryCategory.of("com.anthropic.claude"))
        assertEquals(LibraryCategory.PRODUCTIVITY, LibraryCategory.of("ai.perplexity.app.android"))
        assertEquals(LibraryCategory.PRODUCTIVITY, LibraryCategory.of("com.samsung.android.app.notes"))
        assertEquals(LibraryCategory.PRODUCTIVITY, LibraryCategory.of("com.paypal.android.p2pmobile"))
        assertEquals(LibraryCategory.PRODUCTIVITY, LibraryCategory.of("com.chase.sig.android"))
        assertEquals(LibraryCategory.PRODUCTIVITY, LibraryCategory.of("com.robinhood.android"))
    }

    @Test
    fun labelFallbackCategorizesNonDescriptivePackages() {
        assertEquals(LibraryCategory.GAMES, LibraryCategory.of("com.unknown.pkg1", appLabel = "Solitaire Classic"))
        assertEquals(LibraryCategory.TRAVEL, LibraryCategory.of("com.unknown.pkg2", appLabel = "Flight Tracker"))
        assertEquals(LibraryCategory.ENTERTAINMENT, LibraryCategory.of("com.unknown.pkg3", appLabel = "Podcast Studio"))
        assertEquals(LibraryCategory.CREATIVITY, LibraryCategory.of("com.unknown.pkg4", appLabel = "Digital Paint Art"))
    }

    @Test
    fun appCategoryMappingCoversAllEntries() {
        assertEquals(LibraryCategory.SOCIAL, LibraryCategory.fromAppCategory(com.anotherlauncher.data.AppCategory.SOCIAL))
        assertEquals(LibraryCategory.UTILITIES, LibraryCategory.fromAppCategory(com.anotherlauncher.data.AppCategory.BROWSERS))
        assertEquals(LibraryCategory.ENTERTAINMENT, LibraryCategory.fromAppCategory(com.anotherlauncher.data.AppCategory.MEDIA))
        assertEquals(LibraryCategory.CREATIVITY, LibraryCategory.fromAppCategory(com.anotherlauncher.data.AppCategory.CREATIVITY))
        assertEquals(LibraryCategory.ENTERTAINMENT, LibraryCategory.fromAppCategory(com.anotherlauncher.data.AppCategory.ENTERTAINMENT))
        assertEquals(LibraryCategory.GAMES, LibraryCategory.fromAppCategory(com.anotherlauncher.data.AppCategory.GAMES))
        assertEquals(LibraryCategory.PRODUCTIVITY, LibraryCategory.fromAppCategory(com.anotherlauncher.data.AppCategory.PRODUCTIVITY))
        assertEquals(LibraryCategory.INFO, LibraryCategory.fromAppCategory(com.anotherlauncher.data.AppCategory.NEWS))
        assertEquals(LibraryCategory.TRAVEL, LibraryCategory.fromAppCategory(com.anotherlauncher.data.AppCategory.MAPS))
        assertEquals(LibraryCategory.SHOPPING, LibraryCategory.fromAppCategory(com.anotherlauncher.data.AppCategory.SHOPPING))
        assertEquals(LibraryCategory.UTILITIES, LibraryCategory.fromAppCategory(com.anotherlauncher.data.AppCategory.TOOLS))
        assertEquals(LibraryCategory.OTHER, LibraryCategory.fromAppCategory(com.anotherlauncher.data.AppCategory.OTHER))
    }
}
