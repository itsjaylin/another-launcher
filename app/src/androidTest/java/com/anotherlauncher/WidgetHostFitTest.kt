package com.anotherlauncher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.InputDevice
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.anotherlauncher.data.HomeLayout
import com.anotherlauncher.widgets.FittingWidgetFrame
import com.anotherlauncher.widgets.LauncherWidgetHostView
import com.anotherlauncher.widgets.WidgetController
import com.anotherlauncher.widgets.WidgetRequirements
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetHostFitTest {
    @Test fun launcherPickupCancelsNativeTouchUntilTheNextDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val host = LauncherWidgetHostView(context).apply { setAppWidget(-1, null) }
            var ups = 0; var cancels = 0
            host.addView(object : View(context) {
                override fun onTouchEvent(event: MotionEvent): Boolean {
                    if (event.actionMasked == MotionEvent.ACTION_UP) ups++
                    if (event.actionMasked == MotionEvent.ACTION_CANCEL) cancels++
                    return true
                }
            }, FrameLayout.LayoutParams(-1, -1))
            val frame = FittingWidgetFrame(context, host)
            frame.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY))
            frame.layout(0, 0, 200, 200)
            fun send(action: Int) {
                val now = SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(now, now, action, 100f, 100f, 0)
                try { frame.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            send(MotionEvent.ACTION_DOWN)
            frame.claimGestureForLauncher()
            send(MotionEvent.ACTION_UP)
            frame.finishLauncherGesture()
            assertEquals(1, cancels)
            assertEquals(0, ups)
            send(MotionEvent.ACTION_DOWN); send(MotionEvent.ACTION_UP)
            assertEquals(1, ups)
        }
    }
    @Test fun completeNativeContentAndTouchTargetsFitASmallerSlot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            val widget = LauncherWidgetHostView(context).apply { setAppWidget(-1, null) }
            var touched = false
            val marker = object : View(context) {
                override fun onTouchEvent(event: MotionEvent): Boolean {
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        assertEquals(10f, event.x, 1f)
                        assertEquals(10f, event.y, 1f)
                        touched = true
                    }
                    return true
                }
            }
            val body = FrameLayout(context).apply { addView(marker, FrameLayout.LayoutParams(20, 20, Gravity.BOTTOM or Gravity.RIGHT)) }
            widget.addView(body, FrameLayout.LayoutParams(-1, -1))
            val frame = FittingWidgetFrame(context, widget).apply {
                requirements = WidgetRequirements(300f / density, 220f / density, 300f / density, 220f / density, false, false)
            }
            frame.measure(View.MeasureSpec.makeMeasureSpec(180, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(110, View.MeasureSpec.EXACTLY))
            frame.layout(0, 0, 180, 110)
            val fit = frame.geometry!!
            assertEquals(0, widget.paddingLeft + widget.paddingRight + widget.paddingTop + widget.paddingBottom)
            assertTrue(widget.width >= 300 && widget.height >= 220)
            assertEquals(widget.width, body.width)
            assertEquals(widget.height, marker.bottom)
            assertTrue(fit.left + fit.visibleWidth <= frame.width + .01f)
            assertTrue(fit.top + fit.visibleHeight <= frame.height + .01f)
            val x = fit.left + (marker.left + 10) * fit.scale
            val y = fit.top + (marker.top + 10) * fit.scale
            val time = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, x, y, 0)
                try { assertTrue(frame.dispatchTouchEvent(event)) } finally { event.recycle() }
            }
            assertTrue("Scaled native controls must still receive correctly mapped touches", touched)
        }
    }

    @Test fun existingSamsungWeatherBindingPublishesItsActualUnpaddedSize() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = AppWidgetManager.getInstance(context)
        val id = AppWidgetHost(context, WidgetController.HOST_ID).appWidgetIds.firstOrNull {
            manager.getAppWidgetInfo(it)?.provider?.packageName == "com.sec.android.daemonapp"
        }
        assumeTrue("Install a Samsung Weather widget to run this device-specific check", id != null)
        val originalProvider = manager.getAppWidgetInfo(id!!).provider
        context.startActivity(android.content.Intent(context, LauncherActivity::class.java)
            .setAction(android.content.Intent.ACTION_MAIN).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP))
        // Home is system-owned and may immediately relaunch when finished. Observe its lifecycle
        // instead of asking ActivityScenario.close() to destroy the default launcher.
        fun onActivity(block: (LauncherActivity) -> Unit) = instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<LauncherActivity>().firstOrNull() ?: throw AssertionError("Launcher isn't resumed")
            block(activity)
        }
        run {
            assertTrue(UiDevice.getInstance(instrumentation).wait(Until.hasObject(By.res("home-root")), 5_000))
            var view: FittingWidgetFrame? = null
            var originalPage: String? = null
            fun locate(root: View): FittingWidgetFrame? {
                if (root is FittingWidgetFrame && root.widget.appWidgetId == id) return root
                if (root is ViewGroup) for (index in 0 until root.childCount) locate(root.getChildAt(index))?.let { return it }
                return null
            }
            val deadline = SystemClock.uptimeMillis() + 8_000
            while (view == null && SystemClock.uptimeMillis() < deadline) {
                onActivity { activity ->
                    val model = ViewModelProvider(activity)[LauncherViewModel::class.java]
                    val page = model.preferences.value.home.pages.firstOrNull { page -> page.items.any { id in it.widgetIds() } }
                    if (page != null) {
                        if (originalPage == null) originalPage = model.selectedPage.value
                        model.selectPage(page.id)
                    }
                    view = locate(activity.window.decorView)?.takeIf { it.width > 0 && it.widget.childCount > 0 }
                }
                if (view == null) SystemClock.sleep(100)
            }
            assertNotNull("Bound Samsung Weather must have a live fitting host", view)
            instrumentation.waitForIdleSync()
            var beforeDrag: HomeLayout? = null
            val rect = android.graphics.Rect()
            onActivity { activity ->
                val frame = view!!
                val host = frame.widget
                val fit = frame.geometry!!
                val density = host.resources.displayMetrics.density
                assertEquals(0, host.paddingLeft + host.paddingRight + host.paddingTop + host.paddingBottom)
                assertFalse(frame.clipChildren)
                assertTrue(fit.visibleWidth <= frame.width + .01f && fit.visibleHeight <= frame.height + .01f)
                val options = manager.getAppWidgetOptions(id!!)
                assertEquals((host.width / density).toInt(), options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH))
                assertEquals((host.height / density).toInt(), options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT))
                assertEquals(originalProvider, manager.getAppWidgetInfo(id)?.provider)
                android.util.Log.i("WidgetFitCheck", "${host.appWidgetInfo.provider.flattenToShortString()}: host=${host.width / density}x${host.height / density}dp, scale=${fit.scale}, padding=0")
                beforeDrag = ViewModelProvider(activity)[LauncherViewModel::class.java].preferences.value.home
                frame.getGlobalVisibleRect(rect)
            }
            assertTrue("Weather widget must be visible for direct-drag verification", rect.width() > 0 && rect.height() > 0)
            val device = UiDevice.getInstance(instrumentation)
            val downTime = SystemClock.uptimeMillis()
            fun touch(action: Int, x: Float, y: Float) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                try { instrumentation.uiAutomation.injectInputEvent(event, true) } finally { event.recycle() }
            }
            val x = rect.exactCenterX(); val y = rect.exactCenterY()
            val delta = 16 * context.resources.displayMetrics.density
            try {
                touch(MotionEvent.ACTION_DOWN, x, y)
                SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 150L)
                touch(MotionEvent.ACTION_MOVE, x + delta, y)
                assertTrue("Long-press must pick up the widget, not open a popup", device.wait(Until.hasObject(By.res("drag-follower")), 3_000))
                assertFalse(device.hasObject(By.res("item-quick-popup")))
                touch(MotionEvent.ACTION_UP, x + delta, y)
                assertTrue(device.wait(Until.gone(By.res("drag-follower")), 3_000))
                instrumentation.waitForIdleSync()
                onActivity { activity ->
                    assertEquals("A drop inside the original cell must preserve the exact layout", beforeDrag, ViewModelProvider(activity)[LauncherViewModel::class.java].preferences.value.home)
                }
                assertEquals(originalProvider, manager.getAppWidgetInfo(id)?.provider)
            } finally {
                touch(MotionEvent.ACTION_CANCEL, x + delta, y)
                onActivity { activity -> originalPage?.let { ViewModelProvider(activity)[LauncherViewModel::class.java].selectPage(it) } }
            }
        }
    }
}
