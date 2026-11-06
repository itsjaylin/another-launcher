package com.anotherlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.anotherlauncher.data.PreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** Run each case with a freshly cleared isolated QA package, never the Play-installed package. */
@RunWith(AndroidJUnit4::class)
class PermissionFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val repository = PreferencesRepository(context)

    @Before fun setup() {
        assumeTrue("Permission fixtures require the isolated QA package", context.packageName.endsWith(".qa"))
        runBlocking { repository.initialize(emptyList()); repository.completeOnboarding() }
        context.startActivity(Intent(context, LauncherActivity::class.java)
            .setAction(LauncherActivity.ACTION_SHOW_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        find(By.text("Settings"))
    }

    @Test fun contactsOpensAndroidPromptDirectlyAndDenialLeavesSearchOff() {
        assumeDenied(Manifest.permission.READ_CONTACTS)
        filter("contacts")
        find(By.textContains("Include contacts in search")).click()
        androidPrompt()
        deny()
        find(By.textContains("Include contacts in search"))
        assertFalse(runBlocking { repository.preferences.first().includeContacts })
        // The unchecked row can retry directly, without toggling off first.
        find(By.textContains("Include contacts in search")).click()
        androidPrompt()
        deny()
    }

    @Test fun signalOpensAndroidPromptDirectlyAndGrantEnablesTheFeature() {
        assumeDenied(Manifest.permission.READ_PHONE_STATE)
        filter("cellular")
        find(By.textContains("Cellular signal indicator")).click()
        androidPrompt()
        find(By.res(Pattern.compile(".*:id/permission_allow_button"))).click()
        find(By.textContains("Cellular signal indicator"))
        assertEquals(PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE))
        assertTrue(runBlocking { repository.preferences.first().showCellularIndicator })
    }

    @Test fun weatherOpensAndroidPromptDirectlyAndDenialKeepsManualCitySearch() {
        assumeDenied(Manifest.permission.ACCESS_COARSE_LOCATION)
        assumeDenied(Manifest.permission.ACCESS_FINE_LOCATION)
        filter("weather")
        scrollTo("Use current location")
        find(By.text("Use current location")).click()
        androidPrompt()
        deny()
        find(By.textContains("Location permission was not granted"))
        // TextField labels are merged into semantics differently across Android versions.
        val cityField = device.findObjects(By.clazz("android.widget.EditText"))
            .firstOrNull { it.text != "weather" }
            ?: throw AssertionError("Manual city input is missing after location denial")
        cityField.text = "London"
        assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText").text("London")), 5_000))
        assertNull(runBlocking { repository.preferences.first().weatherLocation })
    }

    @Test fun onboardingNotificationActionOpensAndroidSettingsDirectly() {
        filter("tour")
        find(By.text("Welcome tour")).click()
        find(By.text("Get started")).click()
        SystemClock.sleep(400) // Wait for each onboarding pager transition before the next tap.
        repeat(2) {
            find(By.text("Next")).click()
            SystemClock.sleep(400)
        }
        find(By.text("Quick setup"))
        scrollTo("Enable dots")
        find(By.text("Enable dots")).click()
        find(By.pkg("com.android.settings"))
        assertNull(device.findObject(By.text("Agree & Open Settings")))
        device.pressBack()
        find(By.text("Quick setup"))
    }

    @Test fun privacyPolicyRemainsAvailableWhenManuallyOpened() {
        filter("privacy")
        find(By.textContains("Privacy policy")).click()
        find(By.text("Privacy Policy"))
        find(By.text("Got It")).click()
        find(By.text("Settings"))
    }

    private fun filter(query: String) {
        find(By.clazz("android.widget.EditText")).text = query
        device.waitForIdle()
    }
    private fun assumeDenied(permission: String) = assumeTrue("Reset QA permissions before this case",
        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED)
    private fun androidPrompt() {
        find(By.pkg(Pattern.compile(".*permissioncontroller")))
        assertNull("No extra app confirmation may precede Android", device.findObject(By.text("Agree & Continue")))
    }
    private fun deny() {
        find(By.res(Pattern.compile(".*:id/permission_deny(?:_and_dont_ask_again)?_button"))).click()
        assertTrue(device.wait(Until.gone(By.pkg(Pattern.compile(".*permissioncontroller"))), 5_000))
    }
    private fun scrollTo(text: String) {
        if (!device.wait(Until.hasObject(By.textContains(text)), 2_000)) {
            UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().textContains(text))
        }
    }
    private fun find(selector: BySelector) = device.wait(Until.findObject(selector), 10_000)
        ?: throw AssertionError("Missing $selector")
}
