package com.anotherlauncher

import com.anotherlauncher.widgets.*
import org.junit.Assert.*
import org.junit.Test

class WidgetSizingTest {
    @Test fun oversizedProviderFitsCompletelyAndKeepsAspectRatio() {
        val fit = WidgetSizing.fit(180, 140, 300, 200)!!
        assertEquals(300, fit.width)
        assertEquals(200, fit.height)
        assertEquals(.6f, fit.scale, .001f)
        assertTrue(fit.left >= 0f && fit.top >= 0f)
        assertTrue(fit.left + fit.visibleWidth <= 180.001f)
        assertTrue(fit.top + fit.visibleHeight <= 140.001f)
    }
    @Test fun fittingDoesNotEnlargeOrInsetAProviderThatAlreadyFits() {
        val fit = WidgetSizing.fit(240, 160, 100, 90)!!
        assertEquals(240, fit.width)
        assertEquals(160, fit.height)
        assertEquals(1f, fit.scale, 0f)
        assertEquals(0f, fit.left, 0f)
        assertEquals(0f, fit.top, 0f)
    }
    @Test fun emptyAndTransientBoundsDoNotProduceInvalidTransforms() {
        assertNull(WidgetSizing.fit(0, 100, 100, 100))
        assertNull(WidgetSizing.fit(100, 0, 100, 100))
        assertEquals(1f, WidgetSizing.fit(100, 100, -1, 0)!!.scale, 0f)
    }
    @Test fun initialSpansUseMeasuredGridAndMinimumNotOnlyAdvertisedCellCount() {
        val requirements = WidgetRequirements(200f, 160f, 120f, 80f, true, true)
        assertEquals(3 to 2, WidgetSizing.initialSpans(requirements, 2, 2, 72f, 88f))
        assertEquals(2 to 2, WidgetSizing.initialSpans(requirements, 2, 2, 112f, 100f))
    }
    @Test fun resizeRulesRespectAxesContentGapAndProviderLimits() {
        val requirements = WidgetRequirements(150f, 100f, 140f, 80f, true, false, maxWidth = 250f)
        assertFalse(WidgetSizing.canResize(requirements, 2, 2, 2, 2, 72f, 88f)) // only 136dp content
        assertTrue(WidgetSizing.canResize(requirements, 2, 2, 3, 2, 72f, 88f))
        assertFalse(WidgetSizing.canResize(requirements, 2, 2, 4, 2, 72f, 88f))
        assertFalse(WidgetSizing.canResize(requirements, 2, 2, 3, 3, 72f, 88f))
    }
    @Test fun pickerExcludesLockScreenAndSpecialSurfaceOnlyProviders() {
        assertTrue(WidgetSizing.isHomeProvider(1))
        assertTrue(WidgetSizing.isHomeProvider(3))
        assertFalse(WidgetSizing.isHomeProvider(2))
        assertFalse(WidgetSizing.isHomeProvider(8192))
        assertFalse(WidgetSizing.isHomeProvider(0))
    }
    @Test fun gridCellCapsWideDisplaysForBreathingRoom() {
        // Unfolded inner display: both fits exceed the cap, so the grid stays compact and centered.
        assertEquals(WidgetSizing.MAX_CELL_DP, WidgetSizing.gridCell(172f, 135f), 0f)
        assertEquals(135f, WidgetSizing.gridCell(172f, 135f, capDp = 140f), 0f)
    }
    @Test fun gridCellFollowsWidthOnNarrowCoverDisplays() {
        assertEquals(86f, WidgetSizing.gridCell(86f, 140f), 0f)
    }
    @Test fun gridCellFollowsHeightInShortWindows() {
        assertEquals(72f, WidgetSizing.gridCell(120f, 72f), 0f)
    }
    @Test fun resizeToMinimumOneByOneAllowedWhenContentFits() {
        val requirements = WidgetRequirements(50f, 50f, 40f, 40f, true, true)
        assertTrue(WidgetSizing.canResize(requirements, 2, 2, 1, 1, 72f, 88f))
    }
    @Test fun resizeRefusedWhenBothAxesAreLocked() {
        val requirements = WidgetRequirements(100f, 100f, 80f, 80f, false, false)
        assertFalse(WidgetSizing.canResize(requirements, 2, 2, 3, 2, 72f, 88f))
        assertFalse(WidgetSizing.canResize(requirements, 2, 2, 2, 3, 72f, 88f))
    }
    @Test fun resizeBlockedByMaxConstraintInBothDirections() {
        val requirements = WidgetRequirements(100f, 100f, 80f, 80f, true, true, maxWidth = 160f, maxHeight = 100f)
        assertFalse(WidgetSizing.canResize(requirements, 2, 1, 3, 1, 72f, 88f)) // 3*72-8=208 > 160
        assertFalse(WidgetSizing.canResize(requirements, 2, 1, 2, 2, 72f, 88f)) // 2*88-8=168 > 100
    }
    @Test fun resizeAllowsVerticalOnlyWhenHorizontalIsLocked() {
        val requirements = WidgetRequirements(100f, 100f, 80f, 80f, false, true)
        assertFalse(WidgetSizing.canResize(requirements, 2, 2, 3, 2, 72f, 88f))
        assertTrue(WidgetSizing.canResize(requirements, 2, 2, 2, 3, 72f, 88f))
    }
    @Test fun spanBoundsRejectZeroAndNegativeValues() {
        val requirements = WidgetRequirements(50f, 50f, 40f, 40f, true, true)
        assertFalse(WidgetSizing.canResize(requirements, 2, 2, 0, 2, 72f, 88f))
        assertFalse(WidgetSizing.canResize(requirements, 2, 2, 2, 0, 72f, 88f))
        assertFalse(WidgetSizing.canResize(requirements, 2, 2, 5, 2, 72f, 88f))
        assertFalse(WidgetSizing.canResize(requirements, 2, 2, 2, 7, 72f, 88f))
    }
}
