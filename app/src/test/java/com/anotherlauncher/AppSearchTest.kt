package com.anotherlauncher

import com.anotherlauncher.data.AppSearch
import com.anotherlauncher.data.AppCategory
import org.junit.Assert.*
import org.junit.Test

class AppSearchTest {
    @Test fun exactMatchesRankBeforePrefixesAndSubstrings() {
        assertEquals(0, AppSearch.rank("Maps", "example.maps", "maps"))
        assertEquals(1, AppSearch.rank("Maps Go", "example.go", "maps"))
        assertEquals(3, AppSearch.rank("Citymaps", "example.city", "maps"))
    }
    @Test fun accentsCaseAndWhitespaceDoNotBlockMatches() {
        assertEquals(0, AppSearch.rank("Café", "example.cafe", "  CAFE  "))
        assertEquals(2, AppSearch.rank("Google Play Store", "com.store", "play st"))
    }
    @Test fun packageSearchIsLowerPriorityThanAppName() {
        assertEquals(6, AppSearch.rank("Photos", "com.google.android.apps.photos", "google"))
        assertNull(AppSearch.rank("Photos", "com.example.other", "xx"))
    }
    @Test fun initialsAndSubsequenceMatching() {
        assertEquals(4, AppSearch.rank("YouTube", "com.google.android.youtube", "yt"))
        assertEquals(4, AppSearch.rank("Google Play Store", "com.android.vending", "gps"))
        assertEquals(5, AppSearch.rank("Spotify", "com.spotify.music", "spfy"))
    }
    @Test fun mathCalculatorEvaluation() {
        assertEquals("14", AppSearch.evaluateMath("2 + 3 * 4"))
        assertEquals("20", AppSearch.evaluateMath("(2 + 3) * 4"))
        assertEquals("42", AppSearch.evaluateMath("12 * (3 + 4) / 2"))
        assertEquals("54", AppSearch.evaluateMath("50 * 1.08"))
        assertEquals("256", AppSearch.evaluateMath("2 ^ 8"))
        assertNull(AppSearch.evaluateMath("555-1234"))
        assertNull(AppSearch.evaluateMath("2026-09-15"))
        assertNull(AppSearch.evaluateMath("hello"))
        assertNull(AppSearch.evaluateMath("12+"))
    }
    @Test fun settingShortcutsMatching() {
        val wifi = AppSearch.findSettings("wifi")
        assertTrue(wifi.any { it.title == "Wi-Fi" })
        val display = AppSearch.findSettings("dark mode")
        assertTrue(display.any { it.title == "Display & brightness" })
        val battery = AppSearch.findSettings("power")
        assertTrue(battery.any { it.title == "Battery" })
    }
    @Test fun unknownTermsDoNotProduceUnrelatedResults() {
        assertNull(AppSearch.rank("Calendar", "com.example.calendar", "music"))
        assertEquals(0, AppSearch.rank("Calendar", "com.example.calendar", ""))
    }
    @Test fun knownBrowsersAreSeparatedFromCommunicationApps() {
        assertEquals(AppCategory.BROWSERS, AppCategory.classify(4, "com.chrome.beta"))
        assertEquals(AppCategory.BROWSERS, AppCategory.classify(4, "org.mozilla.firefox"))
        assertEquals(AppCategory.SOCIAL, AppCategory.classify(4, "com.example.chat"))
        assertEquals(AppCategory.OTHER, AppCategory.classify(-1, "com.chromeimitation"))
    }
}
