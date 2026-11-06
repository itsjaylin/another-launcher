package com.anotherlauncher

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Tests navigation and popup geometry without changing pins, widgets, or launcher settings. */
@RunWith(AndroidJUnit4::class)
class HomeInteractionTest {
    @Test fun wallpaperButtonOffersCustomPickerAndSamsungLibrary() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val context = instrumentation.targetContext
        context.startActivity(Intent(context, LauncherActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        device.waitForIdle(2_000)
        assertTrue(device.wait(Until.hasObject(By.res("home-root")), 10_000))
        fun homeOptions(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.actionList.any { it.label?.toString() == "Home options" }) return node
            for (index in 0 until node.childCount) homeOptions(node.getChild(index))?.let { return it }
            return null
        }
        val target = homeOptions(instrumentation.uiAutomation.rootInActiveWindow)
        assertNotNull("Home options action missing", target)
        val action = target!!.actionList.first { it.label?.toString() == "Home options" }
        assertTrue(target.performAction(action.id))
        text(device, "Wallpaper").click()
        text(device, "Wallpaper Settings")
        if (device.findObject(By.text("Open System Wallpaper & Style")) == null) {
            UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView("Open System Wallpaper & Style")
        }
        text(device, "Open System Wallpaper & Style").click()
        assertTrue("Dressroom must open from the launcher button",
            device.wait(Until.hasObject(By.pkg("com.samsung.android.app.dressroom")), 10_000))
        assertTrue("Samsung wallpaper UI must be visible",
            device.wait(Until.hasObject(By.text(java.util.regex.Pattern.compile("Wallpapers|Wallpaper and style"))), 5_000))
    }

    @Test fun emptySpaceDockFullPageSettingsAndItemPopupUseDistinctSurfaces() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext, LauncherActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        device.waitForIdle(2_000)
        // A restored task may initially reopen Settings; a fresh Home intent must return Home.
        instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext, LauncherActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        assertTrue(device.wait(Until.hasObject(By.res("home-root")), 10_000))
        openHomeOptions(device)
        val dock = device.wait(Until.findObject(By.desc("Home options")), 5_000) ?: throw AssertionError("Home dock missing")
        assertTrue("Home options should be a small bottom dock", dock.visibleBounds.height() < device.displayHeight / 3)
        text(device, "Settings").click()
        text(device, "Settings")
        text(device, "Show app names")
        device.wait(Until.findObject(By.desc("Back")), 5_000)!!.click()
        assertTrue(device.wait(Until.hasObject(By.res("home-root")), 5_000))

        val app = device.wait(Until.findObject(By.res("home-app-tile")), 5_000)
        if (app != null) {
            val anchor = app.visibleBounds
            val downTime = SystemClock.uptimeMillis()
            fun touch(action: Int, x: Float, y: Float) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                try { instrumentation.uiAutomation.injectInputEvent(event, true) } finally { event.recycle() }
            }
            val x = anchor.exactCenterX()
            val y = anchor.exactCenterY()
            try {
                touch(MotionEvent.ACTION_DOWN, x, y)
                SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 150L)
                touch(MotionEvent.ACTION_MOVE, x + 90, y + 60)
                assertTrue(device.wait(Until.hasObject(By.res("drag-follower")), 2_000))
                assertTrue("Original icon must remain while dragging", device.wait(Until.hasObject(By.res("drag-source-placeholder")), 2_000))
            } finally { touch(MotionEvent.ACTION_CANCEL, x + 90, y + 60) }
            assertTrue(device.wait(Until.gone(By.res("drag-follower")), 2_000))
            device.wait(Until.findObject(By.res("home-app-tile")), 2_000)!!.longClick()
            val popup = device.wait(Until.findObject(By.res("item-quick-popup")), 5_000) ?: throw AssertionError("Item popup missing")
            text(device, "Remove from homescreen")
            text(device, "Uninstall")
            assertTrue(popup.visibleBounds.width() < device.displayWidth)
            assertTrue(popup.visibleBounds.height() < device.displayHeight / 2)
            assertTrue("Popup must be near its source", kotlin.math.abs(popup.visibleBounds.centerX() - anchor.centerX()) < device.displayWidth / 2)
            device.pressBack()
        }
        openHomeOptions(device)
        text(device, "Widgets").click()
        text(device, "Find widgets")
        assertTrue(device.findObjects(By.res("builtin-weather-choice")).isEmpty())
        assertTrue(device.findObjects(By.res("builtin-analog-choice")).isEmpty())
        device.findObject(By.desc("Back")).click()
        assertTrue(device.wait(Until.hasObject(By.res("home-root")), 5_000))
        openHomeOptions(device)
        text(device, "Pages").click()
        text(device, "Home pages")
        text(device, "Remove empty pages")
        device.findObject(By.desc("Back")).click()
        val appLibrary = device.wait(Until.findObject(By.desc("Search")), 5_000)
            ?: device.wait(Until.findObject(By.desc("Search")), 5_000)!!
        appLibrary.click()
        val search = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)!!
        search.click()
        search.text = "a"
        val result = device.wait(Until.findObject(By.res("library-app-tile")), 5_000) ?: throw AssertionError("Search result missing")
        assertTrue("Keyboard must not squash results into a strip", result.visibleBounds.height() >= 48 * instrumentation.targetContext.resources.displayMetrics.density)
        text(device, "Cancel").click()
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.res("empty-home-cell")), 5_000))
    }

    @Test fun settingsAppOpensFullHomescreenOptions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val context = instrumentation.targetContext

        val settingsIntent = Intent(context, SettingsActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(settingsIntent)
        device.waitForIdle(2_000)

        assertTrue("Launcher settings surface must be open",
            device.wait(Until.hasObject(By.text("Settings")), 10_000))
        assertTrue("Home options section must be visible",
            device.wait(Until.hasObject(By.text("Show app names")), 5_000))

        val backButton = device.wait(Until.findObject(By.desc("Back")), 5_000)
        assertNotNull("Back button should be present on settings surface", backButton)
        backButton!!.click()
        assertTrue(device.wait(Until.hasObject(By.res("home-root")), 5_000))
    }
    private fun text(device: UiDevice, value: String): UiObject2 =
        device.wait(Until.findObject(By.text(value)), 5_000) ?: throw AssertionError("Missing text: $value")

    private fun openHomeOptions(device: UiDevice) {
        val empty = device.findObject(By.res("empty-home-cell"))
        if (empty != null) empty.longClick()
        else {
            // A user's full page still exposes Home options to accessibility; don't add/delete
            // their items merely to make a blank cell for the test.
            val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByViewId("home-root")?.firstOrNull()
            assertNotNull("Home options accessibility target missing", root)
            assertTrue(root!!.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))
        }
    }
}
