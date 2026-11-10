package com.anotherlauncher

import androidx.compose.ui.geometry.Offset
import com.anotherlauncher.data.HomeLayout
import com.anotherlauncher.data.HomePage
import com.anotherlauncher.ui.LauncherDragState
import com.anotherlauncher.ui.LibraryDragNavigation
import com.anotherlauncher.ui.PagerMap
import org.junit.Assert.*
import org.junit.Test

class LibraryDragTest {
    private val layout = HomeLayout(listOf(HomePage("first"), HomePage("default"), HomePage("last")), defaultPageId = "default")

    @Test fun removingTheSourceSearchResultDoesNotCancelARootTrackedDrag() {
        val drag = LauncherDragState()
        drag.begin(null, Offset(100f, 100f), null, trackedByRoot = true)
        drag.cancelFromSource()
        assertNotNull(drag.session)
        drag.moveFromRoot(Offset(200f, 250f))
        assertEquals(Offset(200f, 250f), drag.session!!.position)
    }

    @Test fun onlyTheRootCanCompleteTheHandedOffDragAndDropRunsOnce() {
        val drag = LauncherDragState()
        var drops = 0
        drag.onDrop = { drops++ }
        drag.begin(null, Offset.Zero, null, trackedByRoot = true)
        drag.moveFromRoot(Offset(200f, 200f))
        drag.finishFromSource()
        assertEquals(0, drops)
        assertNotNull(drag.session)
        drag.finish()
        drag.finishFromSource()
        assertEquals(1, drops)
        assertNull(drag.session)
    }

    @Test fun rootCoordinatesDoNotAccumulateSourceLayoutMovement() {
        val drag = LauncherDragState()
        drag.begin(null, Offset(50f, 60f), null, trackedByRoot = true)
        repeat(3) { drag.moveFromRoot(Offset(100f, 120f)) }
        assertEquals(Offset(100f, 120f), drag.session!!.position)
        assertEquals(Offset(50f, 60f), drag.session!!.origin)
    }

    @Test fun rootCancellationCannotCommitAPlacement() {
        val drag = LauncherDragState()
        drag.onDrop = { fail("Cancelled drag must not place an app") }
        drag.begin(null, Offset.Zero, null, trackedByRoot = true)
        drag.moveFromRoot(Offset(100f, 100f))
        drag.cancel()
        drag.finishFromSource()
        drag.finish()
        assertNull(drag.session)
    }

    @Test fun ordinaryHomeAndWidgetGesturesStillOwnTheirOwnCancellation() {
        val drag = LauncherDragState()
        drag.begin(null, Offset.Zero, false, "widget")
        drag.moveFromRoot(Offset(100f, 100f))
        assertEquals(Offset.Zero, drag.session!!.position)
        drag.cancelFromSource()
        assertNull(drag.session)
    }

    @Test fun aStationaryHandedOffLongPressStillOpensOptionsInsteadOfDropping() {
        val drag = LauncherDragState()
        var holds = 0
        drag.onHold = { holds++ }
        drag.onDrop = { fail("Stationary hold is not a drop") }
        drag.begin(null, Offset(100f, 100f), null, trackedByRoot = true)
        drag.moveFromRoot(Offset(101f, 100f))
        drag.finish()
        assertEquals(1, holds)
    }

    @Test fun libraryDragReturnsToTheLastHomePage() {
        assertEquals("last", LibraryDragNavigation.destination(layout, "last"))
    }

    @Test fun firstLibraryDragUsesTheConfiguredDefaultPage() {
        assertEquals("default", LibraryDragNavigation.destination(layout, null))
    }

    @Test fun deletedPagesHubAndLibraryAreNeverAppDropDestinations() {
        for (id in listOf("deleted", PagerMap.HUB_KEY, PagerMap.LIBRARY_KEY)) {
            assertEquals("default", LibraryDragNavigation.destination(layout, id))
        }
    }
}
