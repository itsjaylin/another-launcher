package com.anotherlauncher

import androidx.compose.ui.geometry.Offset
import com.anotherlauncher.ui.LauncherDragState
import org.junit.Assert.*
import org.junit.Test

class DragLifecycleTest {
    @Test fun aDropIsConsumedBeforeItsCallbackAndCannotRunTwice() {
        val drag = LauncherDragState()
        var drops = 0
        drag.onDrop = {
            assertNull(drag.session)
            drops++
            drag.finish()
        }
        drag.begin(null, Offset.Zero, false, "widget")
        drag.move(Offset(100f, 0f))
        drag.finish()
        drag.finish()
        assertEquals(1, drops)
    }

    @Test fun aHoldCanStartANewSessionWithoutTheOldFinishClearingIt() {
        val drag = LauncherDragState()
        drag.onHold = { drag.begin(null, Offset.Zero, false, "next") }
        drag.begin(null, Offset.Zero, false, "first")
        drag.finish()
        assertEquals("next", drag.session?.itemId)
    }

    @Test fun cancelledDragHasNoDropOrHoldEffects() {
        val drag = LauncherDragState()
        drag.onDrop = { fail("Cancelled drag was dropped") }
        drag.onHold = { fail("Cancelled drag was held") }
        drag.begin(null, Offset.Zero, false, "widget")
        drag.cancel()
        drag.finish()
        assertNull(drag.session)
    }

    @Test fun failedDropCannotLeaveTheSessionActive() {
        val drag = LauncherDragState()
        drag.onDrop = { throw IllegalStateException("Failed effect") }
        drag.begin(null, Offset.Zero, false, "widget")
        drag.move(Offset(100f, 0f))
        try { drag.finish(); fail("Expected callback failure") }
        catch (_: IllegalStateException) { assertNull(drag.session) }
        drag.finish()
    }
}
