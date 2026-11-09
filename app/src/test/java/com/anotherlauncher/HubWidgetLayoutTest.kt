package com.anotherlauncher

import com.anotherlauncher.data.HUB_PAGE_ID
import com.anotherlauncher.data.HomeEditor
import com.anotherlauncher.data.HomeItem
import com.anotherlauncher.data.HomeItemKind
import com.anotherlauncher.data.HomeLayout
import com.anotherlauncher.data.HomeLayoutCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HubWidgetLayoutTest {
    private fun widget(id: Int) = HomeItem(
        id = "widget-$id",
        kind = HomeItemKind.WIDGET,
        widgetId = id,
        spanX = 2,
        spanY = 2,
    )

    @Test
    fun hubWidgetUsesDedicatedCanvasWithoutAddingAHomePage() {
        val original = HomeLayout()
        val result = HomeEditor.add(original, HUB_PAGE_ID, widget(41))

        assertNull(result.error)
        assertEquals(HUB_PAGE_ID, result.pageId)
        assertEquals(original.pages, result.layout.pages)
        assertEquals(41, result.layout.hubPage.items.single().widgetId)
        assertEquals(HUB_PAGE_ID, result.layout.pageOf("widget-41")?.id)
    }

    @Test
    fun hubRejectsNonWidgetsAndDoesNotSpillWhenFull() {
        val app = HomeItem(id = "app", kind = HomeItemKind.APP, appId = "pkg/Activity")
        assertNotNull(HomeEditor.add(HomeLayout(), HUB_PAGE_ID, app).error)

        var layout = HomeLayout()
        repeat(6) { index -> layout = HomeEditor.add(layout, HUB_PAGE_ID, widget(index)).layout }
        val full = HomeEditor.add(layout, HUB_PAGE_ID, widget(99))

        assertNotNull(full.error)
        assertEquals(layout, full.layout)
        assertEquals(1, full.layout.pages.size)
        assertEquals(6, full.layout.hubPage.items.size)
    }

    @Test
    fun hubWidgetCanResizeMoveAndRemoveWithoutChangingItsBinding() {
        var layout = HomeEditor.add(HomeLayout(), HUB_PAGE_ID, widget(7)).layout
        layout = HomeEditor.move(layout, layout.item("widget-7")!!, HUB_PAGE_ID, 2, 1).layout
        layout = HomeEditor.resize(layout, "widget-7", 2, 3).layout

        val moved = layout.item("widget-7")!!
        assertEquals(7, moved.widgetId)
        assertEquals(2, moved.column)
        assertEquals(1, moved.row)
        assertEquals(3, moved.spanY)

        val removed = HomeEditor.remove(layout, moved.id)
        assertTrue(removed.hubPage.items.isEmpty())
        assertEquals(1, removed.pages.size)
    }

    @Test
    fun schemaFiveRoundTripRetainsHubWidgetsAndLegacySchemaStartsEmpty() {
        val layout = HomeEditor.add(HomeLayout(), HUB_PAGE_ID, widget(22)).layout
        val encoded = HomeLayoutCodec.encode(layout)
        val decoded = HomeLayoutCodec.decode(encoded)

        assertEquals(layout, decoded)
        assertEquals(22, decoded.hubPage.items.single().widgetId)

        val legacy = HomeLayoutCodec.decode(
            """{"version":4,"defaultPage":"home","pages":[{"id":"home","items":[]}]}"""
        )
        assertTrue(legacy.hubPage.items.isEmpty())
    }
}
