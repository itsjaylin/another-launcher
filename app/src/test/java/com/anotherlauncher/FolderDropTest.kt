package com.anotherlauncher

import com.anotherlauncher.data.*
import org.junit.Assert.*
import org.junit.Test

class FolderDropTest {
    private val source = HomeItem(id = "source", kind = HomeItemKind.APP, appId = "p/source", column = 0)
    private val target = HomeItem(id = "target", kind = HomeItemKind.APP, appId = "p/target", column = 1)
    private val layout = HomeLayout(listOf(HomePage("home", listOf(source, target))))

    @Test fun hoveringAnAppNeverDisplacesItsGroupingTarget() {
        repeat(10) {
            assertEquals(layout, HomeEditor.previewMove(layout, source, "home", 1, 0))
        }
        val committed = HomeEditor.move(layout, source, "home", 1, 0).layout
        val folder = committed.pages.single().items.single()
        assertEquals(HomeItemKind.FOLDER, folder.kind)
        assertEquals(listOf("p/target", "p/source"), folder.contents)
        assertEquals(1, folder.column)
    }

    @Test fun hoveringAnExistingFolderKeepsItsPositionAndContentsUntilDrop() {
        val folder = target.copy(kind = HomeItemKind.FOLDER, appId = null, contents = listOf("p/target"), title = "Tools")
        val initial = layout.copy(pages = listOf(HomePage("home", listOf(source, folder))))
        assertEquals(initial, HomeEditor.previewMove(initial, source, "home", 1, 0))
        val result = HomeEditor.move(initial, source, "home", 1, 0).layout
        assertNull(result.item(source.id))
        assertEquals(folder.copy(contents = listOf("p/target", "p/source")), result.item(folder.id))
    }

    @Test fun crossPageGroupingRemovesOnlyTheSourceAndPreservesWidgetBindings() {
        val widget = HomeItem(id = "widget", kind = HomeItemKind.WIDGET, widgetId = 42, row = 2, spanX = 2, spanY = 2)
        val initial = HomeLayout(listOf(HomePage("home", listOf(source, widget)), HomePage("other", listOf(target))))
        val result = HomeEditor.move(initial, source, "other", 1, 0)
        assertNull(result.error)
        assertEquals(listOf(widget), result.layout.pages.first().items)
        assertEquals(listOf("p/target", "p/source"), result.layout.pages.last().items.single().contents)
    }

    @Test fun anAppFromTheLibraryCanCreateAFolderWithoutMovingItsTarget() {
        val initial = layout.copy(pages = listOf(HomePage("home", listOf(target))))
        assertEquals(initial, HomeEditor.previewMove(initial, source, "home", 1, 0))
        val result = HomeEditor.move(initial, source, "home", 1, 0).layout
        assertEquals(listOf("p/target", "p/source"), result.pages.single().items.single().contents)
    }

    @Test fun droppingBackOnTheSourceDoesNotCreateAFolder() {
        assertEquals(layout, HomeEditor.move(layout, source, "home", 0, 0).layout)
    }

    @Test fun duplicateShortcutsDoNotBecomeASingleAppFolder() {
        val duplicate = target.copy(appId = source.appId)
        val initial = layout.copy(pages = listOf(HomePage("home", listOf(source, duplicate))))
        assertEquals(initial, HomeEditor.move(initial, source, "home", 1, 0).layout)
        assertEquals(initial, HomeEditor.previewMove(initial, source, "home", 1, 0))
    }

    @Test fun hoveringAWidgetStillPreviewsReflowAndNeverGroups() {
        val widget = target.copy(kind = HomeItemKind.WIDGET, appId = null, widgetId = 42, spanX = 2, spanY = 2)
        val initial = layout.copy(pages = listOf(HomePage("home", listOf(source, widget))))
        val preview = HomeEditor.previewMove(initial, source, "home", 1, 0)
        val committed = HomeEditor.move(initial, source, "home", 1, 0).layout
        assertEquals(preview, committed)
        assertEquals(42, committed.item("target")?.widgetId)
        assertTrue(committed.allItems().none { it.kind == HomeItemKind.FOLDER })
    }

    @Test fun hubStillRejectsAppDrops() {
        assertEquals(layout, HomeEditor.previewMove(layout, source, HUB_PAGE_ID, 0, 0))
        val result = HomeEditor.move(layout, source, HUB_PAGE_ID, 0, 0)
        assertNotNull(result.error)
        assertEquals(layout, result.layout)
    }

    @Test fun folderContentsSurvivePersistenceRoundTrip() {
        val result = HomeEditor.move(layout, source, "home", 1, 0).layout
        assertEquals(result, HomeLayoutCodec.decode(HomeLayoutCodec.encode(result)))
    }
}
