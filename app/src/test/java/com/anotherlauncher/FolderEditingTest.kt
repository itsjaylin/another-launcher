package com.anotherlauncher

import com.anotherlauncher.data.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class FolderEditingTest {
    private val folder = HomeItem(id = "group", kind = HomeItemKind.FOLDER, column = 2, row = 3,
        title = "Tools", contents = listOf("z", "b", "a"))
    private val widget = HomeItem(id = "widget", kind = HomeItemKind.WIDGET, widgetId = 42, spanX = 2, spanY = 2)
    private val layout = HomeLayout(listOf(HomePage("home", listOf(folder, widget))))
    private val labels = mapOf("z" to "Zebra", "b" to "beta", "a" to "Alpha")

    @Test fun alphabeticalSortUsesLabelsWithoutCaseBiasAndPersists() {
        val sorted = HomeEditor.sortFolder(layout, folder.id, labels, Locale.US)
        assertEquals(listOf("a", "b", "z"), sorted.item(folder.id)!!.contents)
        assertEquals(sorted, HomeLayoutCodec.decode(HomeLayoutCodec.encode(sorted)))
    }

    @Test fun sortKeepsFolderIdentityNamePositionAndEveryOtherTile() {
        val sorted = HomeEditor.sortFolder(layout, folder.id, labels, Locale.US)
        assertEquals(folder.copy(contents = listOf("a", "b", "z")), sorted.item(folder.id))
        assertEquals(widget, sorted.item(widget.id))
        assertEquals(layout.pages.map { it.id }, sorted.pages.map { it.id })
        assertEquals(layout.defaultPageId, sorted.defaultPageId)
    }

    @Test fun temporarilyUnavailableAppsAreKeptAtTheEndInTheirOriginalOrder() {
        val initial = layout.copy(pages = listOf(HomePage("home", listOf(folder.copy(contents = listOf("missing2", "z", "missing1", "a"))))))
        val sorted = HomeEditor.sortFolder(initial, folder.id, labels, Locale.US)
        assertEquals(listOf("a", "z", "missing2", "missing1"), sorted.item(folder.id)!!.contents)
    }

    @Test fun equivalentLabelsKeepTheirOriginalOrder() {
        val sorted = HomeEditor.sortFolder(layout, folder.id, mapOf("z" to "mail", "b" to "MAIL", "a" to "Mail"), Locale.US)
        assertEquals(folder.contents, sorted.item(folder.id)!!.contents)
    }

    @Test fun collationHonorsTheRequestedLocale() {
        val sorted = HomeEditor.sortFolder(layout, folder.id, mapOf("z" to "Zebra", "b" to "Örebro", "a" to "Alpha"), Locale.forLanguageTag("sv-SE"))
        assertEquals(listOf("a", "z", "b"), sorted.item(folder.id)!!.contents)
    }

    @Test fun sortingAnAlreadySortedOrEmptyFolderIsIdempotent() {
        val sorted = HomeEditor.sortFolder(layout, folder.id, labels, Locale.US)
        assertEquals(sorted, HomeEditor.sortFolder(sorted, folder.id, labels, Locale.US))
        val empty = layout.copy(pages = listOf(HomePage("home", listOf(folder.copy(contents = emptyList())))))
        assertEquals(empty, HomeEditor.sortFolder(empty, folder.id, labels, Locale.US))
    }

    @Test fun sortingAMissingOrNonFolderItemDoesNothing() {
        assertEquals(layout, HomeEditor.sortFolder(layout, "deleted", labels, Locale.US))
        assertEquals(layout, HomeEditor.sortFolder(layout, widget.id, labels, Locale.US))
    }

    @Test fun renamingAfterSortingKeepsMembershipAndOrderWithExistingNameValidation() {
        val sorted = HomeEditor.sortFolder(layout, folder.id, labels, Locale.US)
        val renamed = HomeEditor.folder(sorted, folder.id, "  Work  ", sorted.item(folder.id)!!.contents)
        assertEquals("Work", renamed.item(folder.id)!!.title)
        assertEquals(listOf("a", "b", "z"), renamed.item(folder.id)!!.contents)
        assertEquals(widget, renamed.item(widget.id))
        val blank = HomeEditor.folder(renamed, folder.id, "   ", renamed.item(folder.id)!!.contents)
        assertEquals("Folder", blank.item(folder.id)!!.title)
        val long = HomeEditor.folder(renamed, folder.id, "x".repeat(80), renamed.item(folder.id)!!.contents)
        assertEquals(40, long.item(folder.id)!!.title.length)
    }
}
