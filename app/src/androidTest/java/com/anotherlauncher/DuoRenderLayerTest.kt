package com.anotherlauncher

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anotherlauncher.duo.HingeAxis
import com.anotherlauncher.transition.DuoFrame
import com.anotherlauncher.transition.DuoRenderLayer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Run on API 31/32 for the blur fallback and API 33+ for real AGSL compilation/binding. */
@RunWith(AndroidJUnit4::class)
class DuoRenderLayerTest {
    @Test fun buildsEffectsForBothHingeAxesAndReleasesAtRest() {
        val layer = DuoRenderLayer()
        for (axis in HingeAxis.entries) {
            for (fraction in listOf(0f, .4f, 1f)) {
                assertNotNull(layer.effect(DuoFrame(.8f, .5f, -1f, fraction, axis), 1200f, 900f, 2f))
            }
        }
        assertNull(layer.effect(DuoFrame(), 1200f, 900f, 2f))
        assertNull(layer.effect(DuoFrame(strength = 1f), 0f, 900f, 2f))
    }
}
