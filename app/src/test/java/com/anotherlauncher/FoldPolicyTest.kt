package com.anotherlauncher

import com.anotherlauncher.duo.DisplaySurface
import com.anotherlauncher.duo.FoldMath
import com.anotherlauncher.duo.FoldPosture
import com.anotherlauncher.duo.FoldState
import com.anotherlauncher.duo.HingeAxis
import com.anotherlauncher.duo.PixelBounds
import com.anotherlauncher.ui.DockPlacement
import com.anotherlauncher.ui.DockSide
import com.anotherlauncher.ui.LayoutPolicy
import org.junit.Assert.*
import org.junit.Test

class FoldPolicyTest {
    private val cover = FoldState(window = PixelBounds(0, 0, 400, 900), maximumSmallestWidthDp = 400f)
    private val inner = cover.copy(window = PixelBounds(0, 0, 900, 760), maximumSmallestWidthDp = 760f)

    @Test fun bottomDockIsExclusiveToInnerPortrait() {
        assertEquals(DockPlacement.RIGHT, LayoutPolicy.dock(cover, DockSide.RIGHT))
        assertEquals(DockPlacement.LEFT, LayoutPolicy.dock(inner, DockSide.LEFT))
        val portrait = inner.copy(window = PixelBounds(0, 0, 760, 900))
        assertEquals(DockPlacement.BOTTOM, LayoutPolicy.dock(portrait, DockSide.LEFT))
        assertEquals(DockPlacement.LEFT, LayoutPolicy.dock(portrait, DockSide.LEFT, innerSideLayout = true))
        assertEquals(DockPlacement.RIGHT, LayoutPolicy.dock(portrait, DockSide.RIGHT, innerSideLayout = true))
        assertEquals(DockPlacement.RIGHT, LayoutPolicy.dock(portrait.copy(hingeAngle = 0f), DockSide.RIGHT))
    }
    @Test fun splitScreenRetainsPhysicalSurfaceButUsesSideDock() {
        val split = inner.copy(window = PixelBounds(0, 0, 360, 760), inMultiWindow = true)
        assertEquals(DisplaySurface.INNER, split.surface)
        assertEquals(DockPlacement.RIGHT, LayoutPolicy.dock(split, DockSide.RIGHT))
    }
    @Test fun dockSidePlacementStaysConsistentAcrossRotation() {
        for (rotation in 0..3) {
            assertEquals(DockPlacement.RIGHT, LayoutPolicy.dock(inner.copy(rotation = rotation), DockSide.RIGHT))
            assertEquals(DockPlacement.LEFT, LayoutPolicy.dock(inner.copy(rotation = rotation), DockSide.LEFT))
        }
    }
    @Test fun zeroThicknessHingeRetainsItsOffCenterPositionOnBothAxes() {
        val vertical = inner.copy(hinge = PixelBounds(360, 0, 360, 760))
        assertEquals(.4f, vertical.hingeFraction, .0001f)
        val horizontal = inner.copy(hinge = PixelBounds(0, 190, 900, 190), axis = HingeAxis.HORIZONTAL)
        assertEquals(.25f, horizontal.hingeFraction, .0001f)
    }
    @Test fun invalidSensorValuesCannotEnterState() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 181f)) assertNull(FoldMath.validAngle(value))
        assertEquals(0f, FoldMath.progress(0f), 0f)
        assertEquals(1f, FoldMath.progress(180f), 0f)
        assertEquals(1f, FoldMath.progress(172f), 0f)
    }
    @Test fun openingDoesNotGetStuckInClosedPosture() {
        val closed = inner.copy(hingeAngle = 0f)
        assertEquals(FoldPosture.CLOSED, closed.posture)
        assertEquals(FoldPosture.HALF_OPENED, closed.copy(hingeAngle = 90f).posture)
        assertEquals(FoldPosture.FLAT, closed.copy(hingeAngle = 175f).posture)
    }
    @Test fun absentSensorUsesWindowManagerPosture() {
        assertEquals(.5f, inner.copy(featurePosture = FoldPosture.HALF_OPENED).progress, 0f)
        assertEquals(1f, inner.copy(featurePosture = FoldPosture.FLAT).progress, 0f)
        assertEquals(0f, cover.progress, 0f)
    }
}
