package com.anotherlauncher

import com.anotherlauncher.data.*
import com.anotherlauncher.widgets.WidgetCatalog
import org.junit.Assert.*
import org.junit.Test

class DashboardAndStackTest {
    @Test fun freshHomeStartsWithoutBuiltInWidgets() {
        val layout = HomeEditor.migrate(emptyList(), emptyList())
        assertTrue(layout.pages.first().items.isEmpty())
    }
    @Test fun existingDashboardUpgradePreservesAllItemsAndWidgetBindings() {
        val first = HomePage("home", listOf(HomeItem(id = "clock", kind = HomeItemKind.CLOCK, spanX = 4)) +
            (0 until 20).map { HomeItem(id = "app-$it", kind = HomeItemKind.APP, appId = "package/App$it", column = it % 4, row = 1 + it / 4) })
        val second = HomePage("other", listOf(HomeItem(id = "widget", kind = HomeItemKind.WIDGET, widgetId = 81, spanX = 2, spanY = 2)))
        val upgraded = HomeEditor.upgradeDashboard(HomeLayout(listOf(first, second)))
        assertEquals(second, upgraded.pages.first { it.id == "other" })
        assertEquals((0 until 20).map { "app-$it" }.toSet(), upgraded.pages.flatMap { it.items }.filter { it.kind == HomeItemKind.APP }.map { it.id }.toSet())
        assertEquals(listOf(81), upgraded.pages.flatMap { it.items }.flatMap { it.widgetIds() })
        assertTrue(upgraded.pages.flatMap { it.items }.none { it.kind == HomeItemKind.CLOCK })
        upgraded.pages.forEach { page -> assertTrue(page.items.all { HomeEditor.fits(page, it) }) }
    }
    @Test fun savedLayoutsDropRetiredBuiltInsAndPreserveNativeWidgets() {
        val native = HomeItem(id = "native", kind = HomeItemKind.WIDGET, widgetId = 81, spanX = 2, spanY = 2)
        val builtIn = HomeItem(id = "weather", kind = HomeItemKind.WEATHER, spanX = 2, spanY = 2, column = 2)
        val decoded = HomeLayoutCodec.decode(HomeLayoutCodec.encode(HomeLayout(listOf(HomePage("home", listOf(native, builtIn))))))
        assertEquals(listOf(native), decoded.pages.single().items)
    }
    @Test fun stacksPreserveTheirNativeIdsAndRoundTrip() {
        val a = HomeItem(id = "a", kind = HomeItemKind.WIDGET, widgetId = 41, spanX = 2, spanY = 2)
        val b = a.copy(id = "b", widgetId = 42, column = 2)
        val original = HomeLayout(listOf(HomePage("home", listOf(a, b))))
        val result = HomeEditor.createStack(original, "a", "b")
        assertNull(result.error)
        val stack = result.layout.pages.first().items.single()
        assertEquals(HomeItemKind.STACK, stack.kind)
        assertEquals(listOf(41, 42), stack.widgetIds())
        assertEquals(result.layout, HomeLayoutCodec.decode(HomeLayoutCodec.encode(result.layout)))
        val unpacked = HomeEditor.unstack(result.layout, stack.id).layout
        assertEquals(setOf("a", "b"), unpacked.pages.flatMap { it.items }.map { it.id }.toSet())
        assertEquals(listOf(41, 42), unpacked.pages.flatMap { it.items }.flatMap { it.widgetIds() })
    }
    @Test fun stacksRejectDifferentSpansAndIndependentResize() {
        val a = HomeItem(id = "a", kind = HomeItemKind.WIDGET, widgetId = 10, spanX = 2, spanY = 2)
        val b = HomeItem(id = "b", kind = HomeItemKind.APP, appId = "package/b", column = 3)
        val layout = HomeLayout(listOf(HomePage("home", listOf(a, b))))
        assertEquals(layout, HomeEditor.createStack(layout, "a", "b").layout)
        assertNotNull(HomeEditor.createStack(layout, "a", "b").error)
        val clock = a.copy(id = "clock", widgetId = 11, column = 2)
        val stacked = HomeEditor.createStack(HomeLayout(listOf(HomePage("home", listOf(a, clock)))), "a", "clock").layout
        assertNotNull(HomeEditor.resize(stacked, stacked.pages.first().items.single().id, 4, 2).error)
    }
    @Test fun appStacksKeepBothShortcutsWithoutMakingAFolder() {
        val a = HomeItem(id = "a", kind = HomeItemKind.APP, appId = "package/a")
        val b = a.copy(id = "b", appId = "package/b", column = 1)
        val result = HomeEditor.createStack(HomeLayout(listOf(HomePage("home", listOf(a, b)))), "a", "b").layout
        assertEquals(listOf("package/a", "package/b"), result.pages.first().items.single().stack.map { it.appId })
    }
    @Test fun widgetCatalogDropsExactDuplicatesButKeepsDistinctVariantsAndProfiles() {
        data class Provider(val key: String, val label: String)
        val a = Provider("personal:clock/small", "Clock")
        val b = Provider("personal:clock/large", "Clock")
        val c = Provider("work:clock/small", "Clock")
        assertEquals(listOf(a, b, c), WidgetCatalog.unique(listOf(a, a, b, c, b)) { it.key })
    }
    @Test fun weatherLocationsValidateAndPersistWithoutPermissions() {
        val location = WeatherLocation("London", 51.5074, -.1278)
        assertEquals(location, WeatherLocation.decode(location.encode()))
        assertThrows(IllegalArgumentException::class.java) { WeatherLocation("Nowhere", Double.NaN, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { WeatherLocation("Nowhere", 91.0, 0.0) }
    }
    @Test fun weatherReadingTemperatureUnitsConvertAndFormatCorrectly() {
        val location = WeatherLocation("Austin", 30.2672, -97.7431)
        val reading = WeatherReading(location, celsius = 20.0, code = 0, fetchedAt = 1000L)
        assertEquals(68 to "°F", reading.displayTemp("F", isDefaultUs = false))
        assertEquals(20 to "°C", reading.displayTemp("C", isDefaultUs = true))
        assertEquals(68 to "°F", reading.displayTemp(null, isDefaultUs = true))
        assertEquals(20 to "°C", reading.displayTemp(null, isDefaultUs = false))
    }
    @Test fun uninstallCleansOnlyTheMatchingPackageAndDissolvesOneItemStacks() {
        val a = HomeItem(id = "a", kind = HomeItemKind.APP, appId = "com.example/A")
        val b = a.copy(id = "b", appId = "com.examples/B", column = 1)
        val layout = HomeEditor.createStack(HomeLayout(listOf(HomePage("home", listOf(a, b)))), "a", "b").layout
        val cleaned = HomeEditor.removePackage(layout, "com.example")
        assertEquals(listOf("b"), cleaned.pages.first().items.map { it.id })
        assertEquals(HomeItemKind.APP, cleaned.pages.first().items.single().kind)
        assertEquals(cleaned, HomeLayoutCodec.decode(HomeLayoutCodec.encode(cleaned)))
    }
}
