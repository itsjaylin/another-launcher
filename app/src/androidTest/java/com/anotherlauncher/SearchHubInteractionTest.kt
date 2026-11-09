package com.anotherlauncher

import android.content.Intent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.anotherlauncher.data.PreferencesRepository
import com.anotherlauncher.data.SearchProvider
import com.anotherlauncher.data.HUB_PAGE_ID
import com.anotherlauncher.data.HomeEdit
import com.anotherlauncher.data.HomeEditor
import com.anotherlauncher.data.HomeItem
import com.anotherlauncher.data.HomeItemKind
import com.anotherlauncher.widgets.WidgetController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Run in a separately identified QA install; never change a Play-installed launcher's data. */
@RunWith(AndroidJUnit4::class)
class SearchHubInteractionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Before fun prepareQaHome() {
        assumeTrue("These tests seed settings and require the isolated .qa package", context.packageName.endsWith(".qa"))
        runBlocking {
            PreferencesRepository(context).apply {
                initialize(emptyList())
                completeOnboarding()
                searchProvider(SearchProvider.GOOGLE)
            }
        }
        home()
    }

    @Test fun searchShortcutFocusesFieldAndShowsKeyboardOnEveryTap() {
        val field = openSearch()
        assertTrue(field.isFocused)
        assertTrue("Keyboard must be visible after the transition", waitForKeyboard(true))
        text("Cancel").click()
        assertTrue(waitForKeyboard(false))
        assertTrue(openSearch().isFocused)
        assertTrue("A repeated shortcut tap must reopen the keyboard", waitForKeyboard(true))
    }

    @Test fun appMatchesAndExplicitGoogleActionCoexist() {
        openSearch().text = "Google"
        find(By.res("library-app-tile"))
        text("Apps")
        text("Search Google")
        find(By.res("web-search-action"))
        find(By.desc("Clear search")).click()
        assertEquals("", find(By.res("library-search")).text)
        assertTrue("Clearing leaves search ready for another query", waitForKeyboard(true))
        assertNull("Blank queries must not expose a web action", device.findObject(By.res("web-search-action")))
    }

    @Test fun unmatchedQueryStillProvidesWebSearchAndBackReturnsHome() {
        openSearch().text = "zzzz_no_installed_app_92741"
        text("No matching apps")
        text("Search Google")
        // Android can consume the first Back to hide the IME; the next exits search.
        device.pressBack()
        device.waitForIdle()
        if (device.findObject(By.text("Cancel")) != null) device.pressBack()
        assertTrue(waitForKeyboard(false))
        assertEquals("", find(By.res("library-search")).text)
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.res("empty-home-cell")), 5_000))
        assertNull("Offscreen library must not leave the keyboard open", device.findObject(By.text("Cancel")))
    }

    @Test fun tappingAnAppResultKeepsTheAppLaunchBehavior() {
        openSearch().text = "Another Launcher QA"
        find(By.res("library-app-tile").hasDescendant(By.text("Another Launcher QA"))).click()
        text("Settings")
        assertTrue("Launching an app result must dismiss the keyboard", waitForKeyboard(false))
    }

    @Test fun calculatorAndSystemSettingsRemainAvailableInTheLibrary() {
        val field = openSearch()
        field.text = "120 * 0.15"
        text("= 18")
        text("Calculator")
        field.text = "wifi"
        text("Wi-Fi")
        find(By.res("web-search-action"))
    }

    @Test fun selectedWebProviderOpensExternallyFromActionAndKeyboard() {
        runBlocking { PreferencesRepository(context).searchProvider(SearchProvider.DUCKDUCKGO) }
        openSearch().text = "launcher keyboard test"
        text("Search DuckDuckGo")
        find(By.res("web-search-action")).click()
        assertTrue("Web action must leave the launcher", device.wait(Until.gone(By.res("home-root")), 10_000))
        home()
        openSearch().text = "launcher ime test"
        assertTrue("IME must be ready before sending its Search key", waitForKeyboard(true))
        device.pressKeyCode(KeyEvent.KEYCODE_ENTER)
        assertTrue("Keyboard Search must launch the web provider", device.wait(Until.gone(By.res("home-root")), 10_000))
    }

    @Test fun hubHasWeatherAndWidgetManagementWithoutSearch() {
        // The narrow pager's Hub precedes Home; on a wide spread it is already the left pane.
        if (device.findObject(By.res("hub-add-widgets")) == null) {
            device.swipe(device.displayWidth / 5, device.displayHeight / 2,
                device.displayWidth * 4 / 5, device.displayHeight / 2, 30)
            find(By.desc("Hub (Current)"))
            device.waitForIdle()
            SystemClock.sleep(500) // Accessibility updates before the final pager animation frames.
        }
        find(By.res("hub-add-widgets"))
        find(By.res("hub-weather")).click()
        text("Weather apps") // Unique to the dialog; Weather also appears on the Hub itself.
        device.pressBack()
        find(By.res("hub-weather"))
        assertNull("Widget hub must not contain a search field", device.findObject(By.clazz("android.widget.EditText")))
        find(By.res("hub-add-widgets")).click()
        text("Find widgets")
        find(By.desc("Back")).click()
        find(By.res("hub-add-widgets"))
        assertNull(device.findObject(By.clazz("android.widget.EditText")))
    }

    @Test fun nativeHubWidgetCanBeAddedRenderedAndRemovedWithoutChangingWeather() {
        val manager = AppWidgetManager.getInstance(context)
        val provider = manager.installedProviders.firstOrNull {
            it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0
        }
        assertNotNull("Phone must provide at least one native home widget", provider)
        val host = AppWidgetHost(context, WidgetController.HOST_ID)
        val id = host.allocateAppWidgetId()
        val repository = PreferencesRepository(context)
        val backup = runBlocking { repository.createBackup("qa") }
        try {
            assertTrue("Grant appwidget bind access to the isolated QA package before this test",
                manager.bindAppWidgetIdIfAllowed(id, provider!!.profile, provider.provider, null))
            val widget = HomeItem(kind = HomeItemKind.WIDGET, widgetId = id,
                widgetProvider = provider.provider.flattenToString(), spanX = 2, spanY = 2)
            runBlocking {
                val edit = repository.editHome { HomeEditor.add(it, HUB_PAGE_ID, widget) }
                assertNull(edit.error)
            }
            if (device.findObject(By.res("hub-add-widgets")) == null) {
                device.swipe(device.displayWidth / 5, device.displayHeight / 2,
                    device.displayWidth * 4 / 5, device.displayHeight / 2, 30)
            }
            find(By.res("hub-widget-tile"))
            find(By.res("hub-weather"))
            runBlocking {
                repository.editHome { HomeEdit(HomeEditor.remove(it, widget.id)) }
                assertEquals(backup.weatherLocation, repository.preferences.first().weatherLocation)
            }
            assertTrue(device.wait(Until.gone(By.res("hub-widget-tile")), 5_000))
            find(By.res("hub-weather"))
        } finally {
            runBlocking { repository.restoreBackup(backup) }
            host.deleteAppWidgetId(id)
        }
    }

    private fun home() {
        val intent = Intent(context, LauncherActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
        find(By.res("home-root"))
        // The runner recreates activities between tests; a restored task can reopen its last page.
        // Deliver an actual Home request to the resumed launcher before exercising another shortcut.
        context.startActivity(intent)
        find(By.res("empty-home-cell"))
        assertTrue(device.wait(Until.gone(By.res("library-search")), 5_000))
        device.waitForIdle()
    }

    private fun openSearch(): UiObject2 {
        find(By.desc("Search")).click()
        val field = find(By.res("library-search"))
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (!field.isFocused && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        return field
    }

    private fun waitForKeyboard(visible: Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            val state = device.executeShellCommand("dumpsys input_method")
            val shown = if (state.contains("mInputShown=")) state.contains("mInputShown=true")
                else state.contains("mIsInputViewShown=true")
            if (shown == visible) return true
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        return false
    }

    private fun text(value: String) = find(By.text(value))
    private fun find(selector: androidx.test.uiautomator.BySelector): UiObject2 =
        device.wait(Until.findObject(selector), 10_000) ?: run {
            device.dumpWindowHierarchy(java.io.File(context.cacheDir, "qa-failure.xml"))
            throw AssertionError("Missing $selector")
        }
}
