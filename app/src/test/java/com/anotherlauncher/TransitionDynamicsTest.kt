package com.anotherlauncher

import com.anotherlauncher.duo.FoldPosture
import com.anotherlauncher.duo.FoldState
import com.anotherlauncher.duo.PixelBounds
import com.anotherlauncher.transition.TransitionDynamics
import org.junit.Assert.*
import org.junit.Test

class TransitionDynamicsTest {
    private val cover = FoldState(window = PixelBounds(0, 0, 400, 900), maximumSmallestWidthDp = 400f)
    private val inner = cover.copy(window = PixelBounds(0, 0, 900, 760), maximumSmallestWidthDp = 760f)

    @Test fun initialStartupAndDelayedFeatureDoNotPlayAnUnfold() {
        val dynamics = TransitionDynamics()
        dynamics.accept(inner, 0)
        dynamics.accept(inner.copy(featurePosture = FoldPosture.FLAT, hinge = PixelBounds(450, 0, 450, 760)), 20)
        assertFalse(dynamics.step(.016f, 30))
        assertEquals(0f, dynamics.strength, 0f)
    }
    @Test fun sensorlessHandoffBridgesThenFullyRemovesEffect() {
        val dynamics = TransitionDynamics()
        dynamics.accept(cover, 0)
        dynamics.accept(inner, 30)
        assertTrue(dynamics.strength > .5f)
        repeat(120) { dynamics.step(1f / 60, 30 + it * 17L) }
        assertEquals(0f, dynamics.strength, 0f)
        assertFalse(dynamics.step(.016f, 2500))
    }
    @Test fun rotationAndMultitaskingNeverTriggerHandoff() {
        for (state in listOf(inner.copy(rotation = 1), inner.copy(inMultiWindow = true))) {
            val dynamics = TransitionDynamics()
            dynamics.accept(cover, 0)
            dynamics.accept(state, 20)
            assertFalse(dynamics.step(.016f, 30))
        }
    }
    @Test fun aStationaryHalfOpenScreenBecomesSharpWithoutMoreSensorEvents() {
        val dynamics = TransitionDynamics()
        dynamics.accept(inner.copy(hingeAngle = 80f), 0)
        dynamics.accept(inner.copy(hingeAngle = 90f), 20)
        repeat(6) { dynamics.step(.016f, 30 + it * 16L) }
        assertTrue(dynamics.strength > .5f)
        repeat(120) { dynamics.step(.016f, 200 + it * 16L) }
        assertEquals(0f, dynamics.strength, 0f)
    }
    @Test fun closingReversesFlowAndTouchCancelsImmediately() {
        val dynamics = TransitionDynamics()
        dynamics.accept(inner.copy(hingeAngle = 100f), 0)
        dynamics.accept(inner.copy(hingeAngle = 90f), 20)
        dynamics.step(.016f, 30)
        assertEquals(-1f, dynamics.direction, 0f)
        assertTrue(dynamics.strength > 0f)
        dynamics.block(true)
        assertEquals(0f, dynamics.strength, 0f)
        dynamics.accept(cover, 40)
        dynamics.block(false)
        assertFalse(dynamics.step(.016f, 50))
    }
    @Test fun resumedLauncherDoesNotReplayOldFold() {
        val dynamics = TransitionDynamics()
        dynamics.accept(cover, 0)
        dynamics.accept(inner, 20_000)
        assertFalse(dynamics.step(.016f, 20_020))
    }
    @Test fun smoothingIsIndependentOfDisplayRefreshRate() {
        fun run(hz: Int): Float {
            val dynamics = TransitionDynamics()
            dynamics.accept(inner.copy(hingeAngle = 80f), 0)
            dynamics.accept(inner.copy(hingeAngle = 90f), 1)
            repeat(hz / 10) { dynamics.step(1f / hz, (it + 1) * 1000L / hz) }
            return dynamics.strength
        }
        assertEquals(run(60), run(120), .001f)
    }
}
