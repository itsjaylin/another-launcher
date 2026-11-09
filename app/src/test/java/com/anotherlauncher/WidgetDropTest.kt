package com.anotherlauncher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.anotherlauncher.data.*
import com.anotherlauncher.ui.*
import org.junit.Assert.*
import org.junit.Test

class WidgetDropTest {
    private fun targets() = HomeDropTargets().apply {
        repeat(6) { row -> repeat(4) { col -> update(CellTarget("home", col, row,
            Rect(col * 100f, row * 100f, (col + 1) * 100f, (row + 1) * 100f))) } }
    }
    @Test fun aWidgetGrabbedAtItsCenterDropsByItsFootprintNotTheFingerCell() {
        val item = HomeItem(id = "w", kind = HomeItemKind.WIDGET, widgetId = 9, spanX = 2, spanY = 2)
        val session = DragSession(null, Offset(100f, 100f), Offset(200f, 200f), false, item.id, "Widget", Rect(4f, 4f, 196f, 196f))
        val target = targets().forDrop(session, item, 4f)!!
        assertEquals(1, target.column)
        assertEquals(1, target.row)
    }
    @Test fun widgetSnapsToNearestLegalFootprintAtBottomRight() {
        val item = HomeItem(id = "w", kind = HomeItemKind.WIDGET, widgetId = 9, spanX = 2, spanY = 2)
        val session = DragSession(null, Offset(50f, 50f), Offset(390f, 590f), false, item.id, "Widget", Rect(4f, 4f, 196f, 196f))
        val target = targets().forDrop(session, item, 4f)!!
        assertEquals(2, target.column)
        assertEquals(4, target.row)
    }
    @Test fun droppingBackIntoTheOriginalSlotDoesNotRewriteOrReorderItems() {
        val item = HomeItem(id = "w", kind = HomeItemKind.WIDGET, widgetId = 9, spanX = 2, spanY = 2)
        val other = HomeItem(id = "a", kind = HomeItemKind.APP, appId = "package/a", column = 2)
        val layout = HomeLayout(listOf(HomePage("home", listOf(item, other))))
        assertEquals(layout, HomeEditor.move(layout, item, "home", 0, 0).layout)
    }
    @Test fun outOfWorkspaceDropHasNoTarget() {
        val item = HomeItem(kind = HomeItemKind.WIDGET, widgetId = 9, spanX = 2, spanY = 2)
        val session = DragSession(null, Offset(50f, 50f), Offset(800f, 900f), false, item.id, "Widget", Rect(4f, 4f, 196f, 196f))
        assertNull(targets().forDrop(session, item, 4f))
    }
}
