package com.anotherlauncher

import com.anotherlauncher.data.*
import org.junit.Assert.*
import org.junit.Test

class PageManagementTest {
    private fun app(id: String, col: Int = 0, row: Int = 0) = HomeItem(id = id, kind = HomeItemKind.APP, appId = "package/$id", column = col, row = row)

    @Test fun populatedPageCanBeRemovedWithoutLosingItemsOrBindings() {
        val widget = HomeItem(id = "weather", kind = HomeItemKind.WIDGET, widgetId = 93, spanX = 2, spanY = 2)
        val source = HomePage("source", listOf(widget, app("a", 2)))
        val destination = HomePage("destination", listOf(app("b")))
        val before = HomeLayout(listOf(source, destination))
        val result = HomeEditor.removePageKeepingItems(before, "source")
        assertNull(result.error)
        assertEquals(listOf("destination"), result.layout.pages.map { it.id })
        assertEquals(setOf("weather", "a", "b"), result.layout.pages.flatMap { it.items }.map { it.id }.toSet())
        assertEquals(listOf(93), result.layout.pages.flatMap { it.items }.flatMap { it.widgetIds() })
        assertEquals(destination.items.single(), result.layout.item("b"))
        assertValid(result.layout)
    }
    @Test fun removalIsAtomicWhenOtherPagesHaveNoRoom() {
        val full = HomePage("full", (0 until 24).map { app("app-$it", it % 4, it / 4) })
        val source = HomePage("source", listOf(app("extra")))
        val before = HomeLayout(listOf(source, full))
        val result = HomeEditor.removePageKeepingItems(before, source.id)
        assertNotNull(result.error)
        assertEquals(before, result.layout)
    }
    @Test fun packingLargeWidgetsFirstAvoidsFragmentingFreeSpace() {
        val widget = HomeItem(id = "large", kind = HomeItemKind.WIDGET, widgetId = 94, spanX = 4, spanY = 5)
        val source = HomePage("source", listOf(app("small", 0, 5), widget))
        val before = HomeLayout(listOf(source, HomePage("empty")))
        val result = HomeEditor.removePageKeepingItems(before, source.id)
        assertNull(result.error)
        assertEquals(0, result.layout.item("large")!!.row)
        assertEquals(5, result.layout.item("small")!!.row)
        assertValid(result.layout)
    }
    @Test fun copiesOfTheSameAppKeepTheirDistinctPlacementIdentities() {
        val a = app("a")
        val b = a.copy(id = "b")
        val before = HomeLayout(listOf(HomePage("one", listOf(a)), HomePage("two", listOf(b))))
        val result = HomeEditor.removePageKeepingItems(before, "one")
        assertEquals(setOf("a", "b"), result.layout.pages.single().items.map { it.id }.toSet())
    }
    @Test fun undoRestoresExactGeometryButNeverOverwritesSubsequentEdits() {
        val before = HomeLayout(listOf(HomePage("one", listOf(app("a"))), HomePage("two")))
        val after = HomeEditor.removePageKeepingItems(before, "one").layout
        assertEquals(before, HomeEditor.undoPageEdit(after, before, after).layout)
        val edited = HomeEditor.add(after, "two", app("later")).layout
        assertNotNull(HomeEditor.undoPageEdit(edited, before, after).error)
        assertEquals(edited, HomeEditor.undoPageEdit(edited, before, after).layout)
    }
    @Test fun emptyPageCleanupRetainsAtLeastOnePage() {
        val before = HomeLayout(listOf(HomePage("one"), HomePage("two"), HomePage("three")))
        assertEquals(listOf("one"), HomeEditor.removeEmptyPages(before).layout.pages.map { it.id })
        assertNotNull(HomeEditor.removePageKeepingItems(HomeLayout(), "home").error)
    }
    @Test fun pageReorderingDoesNotChangeGridCoordinates() {
        val before = HomeLayout(listOf(HomePage("one", listOf(app("a", 2, 4))), HomePage("two"), HomePage("three")))
        val result = HomeEditor.movePage(before, "one", 1)
        assertEquals(listOf("two", "one", "three"), result.layout.pages.map { it.id })
        assertEquals(before.item("a"), result.layout.item("a"))
    }
    @Test fun settingDefaultPageUpdatesDefaultAndPersistsAcrossReordering() {
        val before = HomeLayout(listOf(HomePage("one"), HomePage("two"), HomePage("three")))
        assertEquals("one", before.defaultPageId)
        val updated = HomeEditor.setDefaultPage(before, "two")
        assertNull(updated.error)
        assertEquals("two", updated.layout.defaultPageId)
        assertEquals("two", updated.layout.resolvedDefaultPageId())

        // Moving pages preserves the designated default page id
        val moved = HomeEditor.movePage(updated.layout, "two", 1)
        assertEquals("two", moved.layout.defaultPageId)
        assertEquals(listOf("one", "three", "two"), moved.layout.pages.map { it.id })

        // Setting nonexistent page returns an error
        val invalid = HomeEditor.setDefaultPage(before, "nonexistent")
        assertNotNull(invalid.error)
    }
    @Test fun removingDefaultPageReassignsDefaultToRemainingPage() {
        val page1 = HomePage("one", listOf(app("a")))
        val page2 = HomePage("two", listOf(app("b")))
        val before = HomeLayout(listOf(page1, page2), defaultPageId = "two")
        val result = HomeEditor.removePageKeepingItems(before, "two")
        assertNull(result.error)
        assertEquals("one", result.layout.defaultPageId)
        assertEquals("one", result.layout.resolvedDefaultPageId())
    }
    @Test fun deletingDefaultEmptyPageReassignsDefault() {
        val before = HomeLayout(listOf(HomePage("one"), HomePage("two")), defaultPageId = "two")
        val result = HomeEditor.deletePage(before, "two")
        assertNull(result.error)
        assertEquals("one", result.layout.defaultPageId)
    }
    @Test fun undoingDefaultPageRestoresPreviousDefault() {
        val before = HomeLayout(listOf(HomePage("one"), HomePage("two")), defaultPageId = "one")
        val after = HomeEditor.setDefaultPage(before, "two").layout
        assertEquals("two", after.defaultPageId)
        val undone = HomeEditor.undoPageEdit(after, before, after)
        assertNull(undone.error)
        assertEquals("one", undone.layout.defaultPageId)
    }
    private fun assertValid(layout: HomeLayout) { layout.pages.forEach { page -> assertTrue(page.items.all { HomeEditor.fits(page, it) }) } }
}
