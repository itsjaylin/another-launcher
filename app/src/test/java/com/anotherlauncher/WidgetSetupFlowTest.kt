package com.anotherlauncher

import com.anotherlauncher.widgets.PendingWidget
import com.anotherlauncher.widgets.WidgetSetupFlow
import com.anotherlauncher.widgets.WidgetSetupState
import org.junit.Assert.*
import org.junit.Test

class WidgetSetupFlowTest {
    private val widget = PendingWidget(12, "home", "broken-tile")

    @Test fun successfulConsentAndConfigurationPreserveThePlacementAndReplacement() {
        val flow = WidgetSetupFlow()
        assertTrue(flow.begin(widget))
        assertTrue(flow.configure())
        assertEquals(widget, flow.finish())
        assertTrue(flow.idle)
        assertNull(flow.finish())
        assertNull(flow.cancel())
    }

    @Test fun widgetsWithoutMandatoryConfigurationCanFinishAfterBinding() {
        val flow = WidgetSetupFlow()
        flow.begin(widget)
        assertEquals(widget, flow.finish())
        assertTrue(flow.idle)
    }

    @Test fun denyingConsentReleasesOnlyTheNewIdExactlyOnce() {
        val flow = WidgetSetupFlow()
        flow.begin(widget)
        assertEquals(widget, flow.cancel())
        assertNull(flow.cancel())
        assertFalse(flow.configure())
    }

    @Test fun cancellingProviderConfigurationReleasesOnlyTheNewIdExactlyOnce() {
        val flow = WidgetSetupFlow()
        flow.begin(widget)
        flow.configure()
        assertEquals(widget, flow.cancel())
        assertTrue(flow.idle)
        assertNull(flow.finish())
    }

    @Test fun anExistingWidgetIsNeverReleasedByCancellingItsSettings() {
        val flow = WidgetSetupFlow()
        assertTrue(flow.reconfigure(99))
        assertNull(flow.pending)
        assertNull(flow.cancel())
        assertTrue(flow.idle)
    }

    @Test fun completingExistingSettingsNeverAddsANewWidget() {
        val flow = WidgetSetupFlow()
        flow.reconfigure(99)
        assertNull(flow.finish())
        assertTrue(flow.idle)
    }

    @Test fun everyBusyPhaseRejectsANewBindingOrExistingSettings() {
        val states = listOf(WidgetSetupState.Binding(widget), WidgetSetupState.Configuring(widget), WidgetSetupState.Reconfiguring(99))
        for (state in states) {
            val flow = WidgetSetupFlow(state)
            assertFalse(flow.begin(PendingWidget(44, "another-page")))
            assertFalse(flow.reconfigure(77))
            assertEquals(state, flow.state)
        }
    }

    @Test fun restoredConfigurationRetainsItsTargetAndCanBeCancelled() {
        val original = WidgetSetupFlow()
        original.begin(widget)
        original.configure()
        val restored = WidgetSetupFlow(original.state)
        assertEquals(widget, restored.cancel())
    }

    @Test fun restoredExistingConfigurationNeverOwnsTheExistingBinding() {
        val restored = WidgetSetupFlow(WidgetSetupState.Reconfiguring(99))
        assertNull(restored.cancel())
        assertTrue(restored.begin(widget))
    }

    @Test fun finishingBeforeCallbackAllowsASubsequentTransaction() {
        val flow = WidgetSetupFlow()
        flow.begin(widget)
        val completed = flow.finish()
        assertTrue(flow.begin(PendingWidget(44, "new-page")))
        assertEquals(widget, completed)
        assertEquals(44, flow.pending?.id)
    }

    @Test fun configurationCannotStartFromIdleOrExistingSettings() {
        val flow = WidgetSetupFlow()
        assertFalse(flow.configure())
        flow.reconfigure(99)
        assertFalse(flow.configure())
        assertEquals(WidgetSetupState.Reconfiguring(99), flow.state)
    }
}
