package com.anotherlauncher

import com.anotherlauncher.data.*
import com.anotherlauncher.ui.DockSide
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {

    @Test
    fun roundTripEncodesAndDecodesFullBackup() {
        val app = HomeItem(id = "app-1", kind = HomeItemKind.APP, appId = "com.example/MainActivity", column = 0, row = 0)
        val folder = HomeItem(id = "f-1", kind = HomeItemKind.FOLDER, title = "Tools", contents = listOf("app-a", "app-b"), column = 1, row = 0)
        val widget = HomeItem(
            id = "w-1", kind = HomeItemKind.WIDGET, widgetId = 42, spanX = 2, spanY = 2,
            widgetProvider = "com.sec.android.daemonapp/WeatherWidget", widgetLabel = "Weather",
            column = 2, row = 0
        )
        val stack = HomeItem(
            id = "s-1", kind = HomeItemKind.STACK, spanX = 2, spanY = 2, column = 0, row = 2,
            stack = listOf(
                HomeItem(id = "s-w", kind = HomeItemKind.WIDGET, widgetId = 43, spanX = 2, spanY = 2, widgetProvider = "com.example/Widget"),
                HomeItem(id = "s-widget", kind = HomeItemKind.WIDGET, widgetId = 45, spanX = 2, spanY = 2)
            )
        )
        val page1 = HomePage(id = "home", items = listOf(app, folder, widget, stack))
        val page2 = HomePage(id = "page-2", items = listOf(HomeItem(id = "app-2", kind = HomeItemKind.APP, appId = "com.test/App")))
        val hubWidget = HomeItem(
            id = "hub-w-1", kind = HomeItemKind.WIDGET, widgetId = 44, spanX = 2, spanY = 2,
            widgetProvider = "com.example/HubWidget", widgetLabel = "Hub Widget",
        )
        val layout = HomeLayout(
            pages = listOf(page1, page2),
            defaultPageId = "page-2",
            hubPage = HomePage(HUB_PAGE_ID, listOf(hubWidget)),
        )

        val backup = LauncherBackup(
            backupVersion = 1,
            appVersion = "0.7.0-alpha",
            timestamp = 1726380000000L,
            home = layout,
            favorites = listOf("com.example/MainActivity", "com.test/App"),
            categoryOverrides = mapOf("com.example/MainActivity" to AppCategory.TOOLS),
            dockSide = DockSide.LEFT,
            libraryAz = true,
            innerSideLayout = true,
            searchProvider = SearchProvider.BRAVE,
            reducedMotion = true,
            showHomeLabels = true,
            includeContacts = true,
            statusColor = 0xFF00FF00.toInt(),
            alwaysShowPageDots = true,
            showDots = false,
            showCellularIndicator = false,
            weatherLocation = WeatherLocation("Seoul", 37.5665, 126.9780),
            weatherUnit = "F"
        )

        val encoded = BackupCodec.encode(backup)
        val decoded = BackupCodec.decode(encoded)

        assertEquals(backup.backupVersion, decoded.backupVersion)
        assertEquals(backup.appVersion, decoded.appVersion)
        assertEquals(backup.timestamp, decoded.timestamp)
        assertEquals(backup.favorites, decoded.favorites)
        assertEquals(backup.categoryOverrides, decoded.categoryOverrides)
        assertEquals(backup.dockSide, decoded.dockSide)
        assertEquals(backup.libraryAz, decoded.libraryAz)
        assertEquals(backup.innerSideLayout, decoded.innerSideLayout)
        assertEquals(backup.searchProvider, decoded.searchProvider)
        assertEquals(backup.reducedMotion, decoded.reducedMotion)
        assertEquals(backup.showHomeLabels, decoded.showHomeLabels)
        assertEquals(backup.includeContacts, decoded.includeContacts)
        assertEquals(backup.statusColor, decoded.statusColor)
        assertEquals(backup.alwaysShowPageDots, decoded.alwaysShowPageDots)
        assertEquals(backup.showDots, decoded.showDots)
        assertEquals(backup.showCellularIndicator, decoded.showCellularIndicator)
        assertEquals(backup.weatherLocation?.name, decoded.weatherLocation?.name)
        assertEquals(backup.weatherLocation?.latitude, decoded.weatherLocation?.latitude)
        assertEquals(backup.weatherLocation?.longitude, decoded.weatherLocation?.longitude)
        assertEquals(backup.weatherUnit, decoded.weatherUnit)

        // Home layout checks
        assertEquals("page-2", decoded.home.resolvedDefaultPageId())
        assertEquals(2, decoded.home.pages.size)
        val decodedWidget = decoded.home.item("w-1")
        assertNotNull(decodedWidget)
        assertEquals(42, decodedWidget!!.widgetId)
        assertEquals("com.sec.android.daemonapp/WeatherWidget", decodedWidget.widgetProvider)
        assertEquals("Weather", decodedWidget.widgetLabel)

        val decodedHubWidget = decoded.home.item("hub-w-1")
        assertNotNull(decodedHubWidget)
        assertEquals(44, decodedHubWidget!!.widgetId)
        assertEquals(HUB_PAGE_ID, decoded.home.pageOf(decodedHubWidget.id)?.id)

        val decodedStack = decoded.home.item("s-1")
        assertNotNull(decodedStack)
        assertEquals(2, decodedStack!!.stack.size)
        assertEquals("s-w", decodedStack.stack[0].id)
    }

    @Test
    fun unsupportedVersionThrowsIllegalArgumentException() {
        val invalidJson = """{"backupVersion": 999, "home": {"version": 4, "pages": []}}"""
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(invalidJson)
        }
    }

    @Test
    fun corruptJsonThrowsException() {
        assertThrows(Exception::class.java) {
            BackupCodec.decode("not valid json")
        }
    }

    @Test
    fun backupWithLegacyV3LayoutDecodesCleanly() {
        val v3Json = """
        {
            "backupVersion": 1,
            "appVersion": "0.6.0",
            "home": {
                "version": 3,
                "defaultPage": "home",
                "pages": [
                    {
                        "id": "home",
                        "items": [
                            {"id": "a", "kind": "APP", "x": 0, "y": 0, "w": 1, "h": 1, "app": "pkg/Act", "widget": null, "title": "Folder", "contents": [], "stack": []}
                        ]
                    }
                ]
            }
        }
        """.trimIndent()

        val decoded = BackupCodec.decode(v3Json)
        assertEquals(1, decoded.backupVersion)
        assertEquals("home", decoded.home.resolvedDefaultPageId())
        val item = decoded.home.item("a")
        assertNotNull(item)
        assertEquals("pkg/Act", item!!.appId)
    }

    @Test
    fun legacyBackupWithWallpaperFieldDecodesCleanly() {
        val legacyJson = """
        {
            "backupVersion": 1,
            "appVersion": "0.6.0",
            "home": {
                "version": 4,
                "defaultPage": "home",
                "pages": [{"id": "home", "items": []}]
            },
            "settings": {
                "wallpaper": "FLOW",
                "reducedMotion": false
            }
        }
        """.trimIndent()

        val decoded = BackupCodec.decode(legacyJson)
        assertEquals(1, decoded.backupVersion)
        assertFalse(decoded.reducedMotion)
        assertEquals(DockSide.RIGHT, decoded.dockSide)
        assertTrue(decoded.showCellularIndicator)
    }

    @Test
    fun legacyBackupWithAutoDockSideFallsBackToRight() {
        val legacyJson = """
        {
            "backupVersion": 1,
            "appVersion": "0.7.0",
            "home": {
                "version": 4,
                "defaultPage": "home",
                "pages": [{"id": "home", "items": []}]
            },
            "settings": {
                "dockSide": "AUTO"
            }
        }
        """.trimIndent()

        val decoded = BackupCodec.decode(legacyJson)
        assertEquals(DockSide.RIGHT, decoded.dockSide)
    }
}
