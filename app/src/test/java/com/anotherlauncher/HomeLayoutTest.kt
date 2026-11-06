package com.anotherlauncher

import com.anotherlauncher.data.*
import org.junit.Assert.*
import org.junit.Test

class HomeLayoutTest {
    private fun app(id: String) = HomeItem(id = id, kind = HomeItemKind.APP, appId = "package/$id")

    @Test fun migrationRetainsAllPinsAndRealWidgetIdsAcrossOverflowPages() {
        val pins = (0 until 48).map { "package/App$it" }
        val widgets = listOf(31, 92, 110)
        val layout = HomeEditor.migrate(pins, widgets)
        assertEquals(pins, layout.pages.flatMap { it.items }.mapNotNull { it.appId })
        assertEquals(widgets, layout.pages.flatMap { it.items }.mapNotNull { it.widgetId })
        assertTrue(layout.pages.size >= 3)
        assertValid(layout)
    }

    @Test fun appsAndWidgetsUseTheSameOccupancyGrid() {
        val widget = HomeItem(id = "widget", kind = HomeItemKind.WIDGET, spanX = 2, spanY = 2, widgetId = 12)
        var layout = HomeEditor.add(HomeLayout(), "home", widget).layout
        layout = HomeEditor.add(layout, "home", app("a")).layout
        assertEquals(2, layout.item("a")!!.column)
        assertEquals(0, layout.item("a")!!.row)
        val reflowedMove = HomeEditor.move(layout, app("a"), "home", 1, 1)
        assertNull("Reflow makes the occupied cell placeable", reflowedMove.error)
        assertTrue(layout.item("widget")!!.intersects(HomeItem(id = "probe", kind = HomeItemKind.APP, column = 1, row = 1)))
        assertValid(layout)
    }

    @Test fun droppingIntoAnOccupiedCellSlidesNeighborsAside() {
        val widget = HomeItem(id = "widget", kind = HomeItemKind.WIDGET, spanX = 2, spanY = 2, widgetId = 12)
        var layout = HomeEditor.add(HomeLayout(), "home", widget).layout
        layout = HomeEditor.add(layout, "home", app("a")).layout
        val result = HomeEditor.move(layout, app("a"), "home", 1, 1)
        assertNull(result.error)
        val moved = result.layout.item("a")!!
        assertEquals(1, moved.column)
        assertEquals(1, moved.row)
        val widgetAfter = result.layout.item("widget")!!
        assertFalse("Neighbor must slide out of the way", widgetAfter.intersects(moved))
        assertEquals(4, widgetAfter.spanX * widgetAfter.spanY)
        assertValid(result.layout)
    }

    @Test fun reflowStaysOnTheSamePageAndPreservesEveryItem() {
        var layout = HomeEditor.add(HomeLayout(), "home", HomeItem(id = "w", kind = HomeItemKind.WIDGET, widgetId = 3, spanX = 2, spanY = 2)).layout
        repeat(6) { layout = HomeEditor.add(layout, "home", app("row-$it")).layout }
        val before = layout.pages.flatMap { it.items }.map { it.id to (it.appId ?: it.widgetId.toString()) }.sortedBy { it.first }
        val dragged = layout.item("row-0")!!
        // Dropping onto the widget's cell (not onto another app, which creates a folder) forces a reflow.
        val result = HomeEditor.move(layout, dragged, "home", 0, 0)
        assertNull(result.error)
        assertEquals(1, result.layout.pages.size)
        val after = result.layout.pages.flatMap { it.items }.map { it.id to (it.appId ?: it.widgetId.toString()) }.sortedBy { it.first }
        assertEquals(before, after)
        assertEquals(0, result.layout.item("row-0")!!.column)
        assertEquals(0, result.layout.item("row-0")!!.row)
        assertValid(result.layout)
    }

    @Test fun reflowGivesUpWhenNothingElseFitsInsteadOfOverwriting() {
        var layout = HomeLayout()
        repeat(24) { layout = HomeEditor.add(layout, "home", app("x$it")).layout }
        val widget = HomeItem(id = "w", kind = HomeItemKind.WIDGET, widgetId = 1, spanX = 2, spanY = 2)
        val blocked = HomeEditor.move(layout, widget, "home", 0, 0)
        assertNotNull(blocked.error)
        assertEquals(layout, blocked.layout)
    }

    @Test fun libraryDropRepositionsExistingShortcutInsteadOfDuplicatingIt() {
        val existing = app("a")
        val initial = HomeEditor.add(HomeLayout(), "home", existing).layout
        val incoming = existing.copy(id = "new-drag")
        val result = HomeEditor.move(initial, incoming, "home", 3, 4).layout
        assertEquals(1, result.pages.first().items.size)
        assertEquals(3, result.item("a")!!.column)
        assertEquals(4, result.item("a")!!.row)
        assertNull(result.item("new-drag"))
    }

    @Test fun droppingAppOnAppSwapsPositionsWithoutGrouping() {
        var layout = HomeEditor.add(HomeLayout(), "home", app("a")).layout
        layout = HomeEditor.add(layout, "home", app("b")).layout
        assertEquals(0, layout.item("a")!!.column)
        assertEquals(0, layout.item("a")!!.row)
        assertEquals(1, layout.item("b")!!.column)
        assertEquals(0, layout.item("b")!!.row)

        layout = HomeEditor.move(layout, layout.item("b")!!, "home", 0, 0).layout
        val movedB = layout.item("b")!!
        val swappedA = layout.item("a")!!

        assertEquals(HomeItemKind.APP, movedB.kind)
        assertEquals(HomeItemKind.APP, swappedA.kind)
        assertEquals(0, movedB.column)
        assertEquals(0, movedB.row)
        assertEquals(1, swappedA.column)
        assertEquals(0, swappedA.row)
        assertValid(layout)
    }

    @Test fun swappingAppsOnFullPagePreservesAllItems() {
        var layout = HomeLayout()
        repeat(24) { layout = HomeEditor.add(layout, "home", app("x$it")).layout }
        val beforeIds = layout.pages.first().items.map { it.id }.sorted()
        assertEquals(24, beforeIds.size)

        val first = layout.item("x0")!!
        val last = layout.item("x23")!!
        assertEquals(0, first.column); assertEquals(0, first.row)
        assertEquals(3, last.column); assertEquals(5, last.row)

        val result = HomeEditor.move(layout, last, "home", 0, 0).layout
        assertNull(HomeEditor.move(layout, last, "home", 0, 0).error)
        val afterFirst = result.item("x23")!!
        val afterLast = result.item("x0")!!

        assertEquals(0, afterFirst.column); assertEquals(0, afterFirst.row)
        assertEquals(3, afterLast.column); assertEquals(5, afterLast.row)
        assertEquals(beforeIds, result.pages.first().items.map { it.id }.sorted())
        assertValid(result)
    }

    @Test fun droppingOnFolderDoesNotDuplicateItsApps() {
        val folder = HomeItem(id = "folder", kind = HomeItemKind.FOLDER, contents = listOf("package/a", "package/b"))
        val layout = HomeEditor.add(HomeLayout(), "home", folder).layout
        val result = HomeEditor.move(layout, app("a"), "home", 0, 0)
        assertEquals(folder.contents, result.layout.item("folder")!!.contents)
        assertValid(result.layout)
    }

    @Test fun widgetCanMovePagesWithoutChangingBinding() {
        val initial = HomeLayout(listOf(HomePage("a"), HomePage("b")))
        val item = HomeItem(id = "widget", kind = HomeItemKind.WIDGET, widgetId = 47, spanX = 2, spanY = 2)
        val layout = HomeEditor.add(initial, "a", item).layout
        val moved = HomeEditor.moveToPage(layout, item.id, "b")
        assertTrue(moved.layout.pages.first().items.isEmpty())
        assertEquals(47, moved.layout.item(item.id)!!.widgetId)
        assertEquals("b", moved.pageId)
        assertValid(moved.layout)
    }

    @Test fun occupiedPagesAndLastPageCannotBeDeleted() {
        val initial = HomeEditor.add(HomeLayout(), "home", app("a")).layout
        assertNotNull(HomeEditor.deletePage(initial, "home").error)
        val two = initial.copy(pages = initial.pages + HomePage("empty"))
        assertNotNull(HomeEditor.deletePage(two, "home").error)
        assertEquals(initial, HomeEditor.deletePage(two, "empty").layout)
    }

    @Test fun expandingWidgetDoesNotOverwriteNeighborOrGoOutOfBounds() {
        val widget = HomeItem(id = "widget", kind = HomeItemKind.WIDGET, widgetId = 12, spanX = 2, spanY = 2)
        val layout = HomeEditor.add(HomeEditor.add(HomeLayout(), "home", widget).layout, "home", app("a")).layout
        assertNotNull(HomeEditor.resize(layout, "widget", 4, 2).error)
        assertNotNull(HomeEditor.resize(layout, "widget", 2, 7).error)
        assertNull(HomeEditor.resize(layout, "widget", 2, 3).error)
    }

    @Test fun fullPageAdditionCreatesPageWithoutDisplacingExistingItems() {
        var layout = HomeLayout()
        repeat(24) { layout = HomeEditor.add(layout, "home", app("$it")).layout }
        val original = layout.pages.first()
        val result = HomeEditor.add(layout, "home", app("next"))
        assertEquals(2, result.layout.pages.size)
        assertEquals(original, result.layout.pages.first())
        assertEquals(result.layout.pages.last().id, result.pageId)
        assertValid(result.layout)
    }

    @Test fun serializedLayoutRetainsPagesFoldersCellsAndWidgets() {
        var layout = HomeEditor.migrate(listOf("package/a", "package/b"), listOf(12))
        layout = HomeEditor.add(layout, "home", HomeItem(id = "folder", kind = HomeItemKind.FOLDER, title = "My tools", contents = listOf("package/x", "package/y"))).layout
        assertEquals(layout, HomeLayoutCodec.decode(HomeLayoutCodec.encode(layout)))
    }

    @Test fun serializedLayoutRetainsDefaultPage() {
        val layout = HomeLayout(listOf(HomePage("page-1"), HomePage("page-2")), defaultPageId = "page-2")
        val decoded = HomeLayoutCodec.decode(HomeLayoutCodec.encode(layout))
        assertEquals("page-2", decoded.defaultPageId)
        assertEquals(layout, decoded)
    }

    @Test fun legacySerializedLayoutDefaultsToFirstPage() {
        val legacyJson = """{"version":3,"pages":[{"id":"p1","items":[]},{"id":"p2","items":[]}]}"""
        val decoded = HomeLayoutCodec.decode(legacyJson)
        assertEquals("p1", decoded.defaultPageId)
    }

    @Test fun unknownSchemasAreRejectedInsteadOfReplaced() {
        assertThrows(IllegalArgumentException::class.java) { HomeLayoutCodec.decode("{\"version\":5,\"pages\":[]}") }
    }

    @Test fun widgetProviderIdentityRoundTripsThroughV4() {
        val widget = HomeItem(id = "w", kind = HomeItemKind.WIDGET, widgetId = 7, spanX = 2, spanY = 2,
            widgetProvider = "com.example/com.example.Provider", widgetLabel = "Example")
        val layout = HomeEditor.add(HomeLayout(), "home", widget).layout
        val decoded = HomeLayoutCodec.decode(HomeLayoutCodec.encode(layout))
        assertEquals(layout, decoded)
        assertEquals("com.example/com.example.Provider", decoded.item("w")!!.widgetProvider)
        assertEquals("Example", decoded.item("w")!!.widgetLabel)
    }

    @Test fun v3LayoutsWithoutProviderKeysStillDecode() {
        val json = """{"version":3,"defaultPage":"home","pages":[{"id":"home","items":[{"id":"w","kind":"WIDGET","x":0,"y":0,"w":2,"h":2,"app":null,"widget":7,"title":"Folder","contents":[],"stack":[]}]}]}"""
        val decoded = HomeLayoutCodec.decode(json)
        assertEquals(7, decoded.item("w")!!.widgetId)
        assertNull(decoded.item("w")!!.widgetProvider)
        assertNull(decoded.item("w")!!.widgetLabel)
    }

    @Test fun replaceWidgetBindingSwapsInPlace() {
        val widget = HomeItem(id = "w", kind = HomeItemKind.WIDGET, widgetId = 7, spanX = 2, spanY = 2)
        val layout = HomeEditor.add(HomeLayout(), "home", widget).layout
        val result = HomeEditor.replaceWidgetBinding(layout, "w", 9, "com.example/P", "Example", 2, 2)
        assertNull(result.error)
        val swapped = result.layout.item("w")!!
        assertEquals(9, swapped.widgetId)
        assertEquals("com.example/P", swapped.widgetProvider)
        assertEquals(0, swapped.column)
        assertEquals(0, swapped.row)
        assertValid(result.layout)
    }

    @Test fun replaceWidgetBindingFailsClosedWithoutRoom() {
        val blocker = (1..23).map { app("b$it").copy(column = it % 4, row = it / 4) }
        val layout = HomeLayout(listOf(HomePage("home",
            listOf(HomeItem(id = "w", kind = HomeItemKind.WIDGET, widgetId = 7, spanX = 1, spanY = 1)) + blocker)))
        val result = HomeEditor.replaceWidgetBinding(layout, "w", 9, null, null, 4, 6)
        assertNotNull(result.error)
        assertEquals(7, result.layout.item("w")!!.widgetId)
        assertValid(result.layout)
    }

    @Test fun orphanedWidgetsPartitionsByLiveness() {
        val live = HomeItem(id = "live", kind = HomeItemKind.WIDGET, widgetId = 1)
        val dead = HomeItem(id = "dead", kind = HomeItemKind.WIDGET, widgetId = 2)
        val stacked = HomeItem(id = "s", kind = HomeItemKind.STACK, spanX = 2, spanY = 2,
            stack = listOf(
                HomeItem(id = "s-live", kind = HomeItemKind.WIDGET, widgetId = 3, spanX = 2, spanY = 2),
                HomeItem(id = "s-dead", kind = HomeItemKind.WIDGET, widgetId = 4, spanX = 2, spanY = 2)))
        val layout = HomeLayout(listOf(HomePage("home", listOf(live, dead, stacked))))
        val orphans = HomeEditor.orphanedWidgets(layout) { it == 1 || it == 3 }
        assertEquals(listOf("dead", "s-dead"), orphans.map { it.id })
    }

    @Test fun pageOfResolvesStackedItems() {
        val stacked = HomeItem(id = "s", kind = HomeItemKind.STACK, spanX = 2, spanY = 2,
            stack = listOf(HomeItem(id = "child-1", kind = HomeItemKind.WIDGET, widgetId = 1)))
        val layout = HomeLayout(listOf(HomePage("page-1", listOf(stacked))))
        assertEquals("page-1", layout.pageOf("s")?.id)
        assertEquals("page-1", layout.pageOf("child-1")?.id)
        assertNull(layout.pageOf("non-existent"))
    }

    @Test fun corruptOverlappingPlacementsAreRejected() {
        val overlapping = HomeLayout(listOf(HomePage("home", listOf(app("a"), app("b")))))
        assertThrows(IllegalArgumentException::class.java) { HomeLayoutCodec.decode(HomeLayoutCodec.encode(overlapping)) }
    }

    @Test fun resizeToMinimumSpanSucceeds() {
        val widget = HomeItem(id = "w", kind = HomeItemKind.WIDGET, widgetId = 1, spanX = 2, spanY = 2)
        val layout = HomeEditor.add(HomeLayout(), "home", widget).layout
        val result = HomeEditor.resize(layout, "w", 1, 1)
        assertNull(result.error)
        assertEquals(1, result.layout.item("w")!!.spanX)
        assertEquals(1, result.layout.item("w")!!.spanY)
        assertValid(result.layout)
    }

    @Test fun resizeAtGridEdgeBlocksExpansion() {
        // Place a 1×1 widget at column 3 (rightmost column in 4-column grid)
        val widget = HomeItem(id = "w", kind = HomeItemKind.WIDGET, widgetId = 1, column = 3, row = 0, spanX = 1, spanY = 1)
        val layout = HomeLayout(listOf(HomePage("home", listOf(widget))))
        // Trying to expand horizontally past column 3 should fail (column 3 + span 2 > 4)
        val result = HomeEditor.resize(layout, "w", 2, 1)
        assertNotNull(result.error)
        assertEquals(layout, result.layout)
    }

    @Test fun resizeBlockedByNeighborInTargetCells() {
        val widget = HomeItem(id = "w", kind = HomeItemKind.WIDGET, widgetId = 1, column = 0, row = 0, spanX = 2, spanY = 2)
        val blocker = HomeItem(id = "b", kind = HomeItemKind.APP, appId = "pkg/b", column = 2, row = 0)
        val layout = HomeLayout(listOf(HomePage("home", listOf(widget, blocker))))
        // Expanding right should be blocked by the app at column 2
        val result = HomeEditor.resize(layout, "w", 3, 2)
        assertNotNull(result.error)
        assertEquals(layout, result.layout)
    }

    @Test fun resizeWithOriginShiftMovesColumnAndRow() {
        // Simulate left-handle expansion: widget at (2,2) with 1×1, expand to 2×1 starting at column 1
        val widget = HomeItem(id = "w", kind = HomeItemKind.WIDGET, widgetId = 1, column = 2, row = 2, spanX = 1, spanY = 1)
        val layout = HomeLayout(listOf(HomePage("home", listOf(widget))))
        val result = HomeEditor.resize(layout, "w", 2, 1, column = 1, row = 2)
        assertNull(result.error)
        val resized = result.layout.item("w")!!
        assertEquals(1, resized.column)
        assertEquals(2, resized.row)
        assertEquals(2, resized.spanX)
        assertEquals(1, resized.spanY)
        assertValid(result.layout)
    }

    @Test fun resizeWithTopHandleShiftsRowUp() {
        // Widget at (1,3) 2×1, expand upward to 2×2 starting at row 2
        val widget = HomeItem(id = "w", kind = HomeItemKind.WIDGET, widgetId = 1, column = 1, row = 3, spanX = 2, spanY = 1)
        val layout = HomeLayout(listOf(HomePage("home", listOf(widget))))
        val result = HomeEditor.resize(layout, "w", 2, 2, column = 1, row = 2)
        assertNull(result.error)
        val resized = result.layout.item("w")!!
        assertEquals(2, resized.row)
        assertEquals(2, resized.spanY)
        assertValid(result.layout)
    }

    @Test fun resizeWithOriginShiftBlockedAtGridOrigin() {
        // Widget at (0,0) 2×2, trying to shift column to -1 must fail
        val widget = HomeItem(id = "w", kind = HomeItemKind.WIDGET, widgetId = 1, column = 0, row = 0, spanX = 2, spanY = 2)
        val layout = HomeLayout(listOf(HomePage("home", listOf(widget))))
        val result = HomeEditor.resize(layout, "w", 3, 2, column = -1, row = 0)
        assertNotNull(result.error)
    }

    @Test fun dropTargetsComputesAccurateRegionBoundsAcrossSpans() {
        val targets = com.anotherlauncher.ui.HomeDropTargets()
        for (r in 0 until 6) {
            for (c in 0 until 4) {
                val full = androidx.compose.ui.geometry.Rect(c * 100f, r * 120f, (c + 1) * 100f, (r + 1) * 120f)
                targets.update(com.anotherlauncher.ui.CellTarget("p1", c, r, full))
            }
        }
        val region = targets.itemRegionBounds("p1", column = 1, row = 2, spanX = 2, spanY = 2, insetPx = 4f)
        assertNotNull(region)
        assertEquals(104f, region!!.left)
        assertEquals(244f, region.top)
        assertEquals(296f, region.right)
        assertEquals(476f, region.bottom)
    }

    @Test fun defaultLayoutCreatesTwoTopSquareWidgetPromptsAndAppsBelow() {
        val appIds = listOf("pkg.a/A", "pkg.b/B", "pkg.c/C", "pkg.d/D", "pkg.e/E")
        val layout = HomeEditor.defaultLayout(appIds)
        assertEquals(1, layout.pages.size)
        val items = layout.pages.single().items
        val prompts = items.filter { it.kind == HomeItemKind.WIDGET_PROMPT }
        assertEquals(2, prompts.size)
        assertEquals(0, prompts[0].column)
        assertEquals(0, prompts[0].row)
        assertEquals(2, prompts[0].spanX)
        assertEquals(2, prompts[0].spanY)

        assertEquals(2, prompts[1].column)
        assertEquals(0, prompts[1].row)
        assertEquals(2, prompts[1].spanX)
        assertEquals(2, prompts[1].spanY)

        val apps = items.filter { it.kind == HomeItemKind.APP }
        assertEquals(4, apps.size)
        apps.forEachIndexed { index, item ->
            assertEquals(index, item.column)
            assertEquals(2, item.row)
            assertEquals(appIds[index], item.appId)
        }
        assertValid(layout)
    }

    @Test fun replacingPromptWithSquareWidgetReplacesOnlyTargetPrompt() {
        val appIds = listOf("pkg.a/A", "pkg.b/B", "pkg.c/C", "pkg.d/D")
        val layout = HomeEditor.defaultLayout(appIds)
        val edit = HomeEditor.replaceWidgetBinding(layout, "widget-prompt-1", 101, "com.provider/Clock", "Clock", 2, 2)
        assertNull(edit.error)
        val items = edit.layout.pages.single().items
        val widget = items.single { it.id == "widget-prompt-1" }
        assertEquals(HomeItemKind.WIDGET, widget.kind)
        assertEquals(101, widget.widgetId)
        assertEquals(0, widget.column)
        assertEquals(0, widget.row)
        assertEquals(2, widget.spanX)
        assertEquals(2, widget.spanY)

        val siblingPrompt = items.single { it.id == "widget-prompt-2" }
        assertEquals(HomeItemKind.WIDGET_PROMPT, siblingPrompt.kind)
        assertEquals(2, siblingPrompt.column)
        assertEquals(0, siblingPrompt.row)
        assertValid(edit.layout)
    }

    @Test fun replacingPromptWithWideRectangleWidgetReplacesBothPrompts() {
        val appIds = listOf("pkg.a/A", "pkg.b/B", "pkg.c/C", "pkg.d/D")
        val layout = HomeEditor.defaultLayout(appIds)
        val edit = HomeEditor.replaceWidgetBinding(layout, "widget-prompt-1", 202, "com.provider/Calendar", "Calendar", 4, 2)
        assertNull(edit.error)
        val items = edit.layout.pages.single().items
        val widget = items.single { it.id == "widget-prompt-1" }
        assertEquals(HomeItemKind.WIDGET, widget.kind)
        assertEquals(202, widget.widgetId)
        assertEquals(0, widget.column)
        assertEquals(0, widget.row)
        assertEquals(4, widget.spanX)
        assertEquals(2, widget.spanY)

        // Sibling prompt was removed so the 4x2 widget has the entire top row
        assertNull(items.firstOrNull { it.id == "widget-prompt-2" })
        assertEquals(0, items.count { it.kind == HomeItemKind.WIDGET_PROMPT })
        assertEquals(4, items.count { it.kind == HomeItemKind.APP })
        assertValid(edit.layout)
    }

    @Test fun replacingSecondPromptWithWideRectangleWidgetReplacesBothPrompts() {
        val appIds = listOf("pkg.a/A", "pkg.b/B", "pkg.c/C", "pkg.d/D")
        val layout = HomeEditor.defaultLayout(appIds)
        val edit = HomeEditor.replaceWidgetBinding(layout, "widget-prompt-2", 303, "com.provider/Weather", "Weather", 4, 2)
        assertNull(edit.error)
        val items = edit.layout.pages.single().items
        val widget = items.single { it.id == "widget-prompt-2" }
        assertEquals(HomeItemKind.WIDGET, widget.kind)
        assertEquals(303, widget.widgetId)
        assertEquals(0, widget.column)
        assertEquals(0, widget.row)
        assertEquals(4, widget.spanX)
        assertEquals(2, widget.spanY)

        assertNull(items.firstOrNull { it.id == "widget-prompt-1" })
        assertEquals(0, items.count { it.kind == HomeItemKind.WIDGET_PROMPT })
        assertValid(edit.layout)
    }

    @Test fun codecRoundTripPreservesWidgetPrompt() {
        val layout = HomeEditor.defaultLayout(listOf("pkg.a/A"))
        val encoded = HomeLayoutCodec.encode(layout)
        val decoded = HomeLayoutCodec.decode(encoded)
        assertEquals(layout.pages.single().items.size, decoded.pages.single().items.size)
        val prompt = decoded.pages.single().items.first { it.id == "widget-prompt-1" }
        assertEquals(HomeItemKind.WIDGET_PROMPT, prompt.kind)
        assertEquals(2, prompt.spanX)
        assertEquals(2, prompt.spanY)
        assertValid(decoded)
    }

    private fun assertValid(layout: HomeLayout) {
        layout.pages.forEach { page -> page.items.forEach { assertTrue("Invalid ${it.id}", HomeEditor.fits(page, it)) } }
        val ids = layout.pages.flatMap { it.items }.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }
}

