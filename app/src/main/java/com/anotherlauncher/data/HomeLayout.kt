package com.anotherlauncher.data

import java.util.UUID

const val HUB_PAGE_ID = "hub"

enum class HomeItemKind { APP, WIDGET, FOLDER, CLOCK, WEATHER, ANALOG_CLOCK, STACK, WIDGET_PROMPT }

data class HomeItem(
    val id: String = UUID.randomUUID().toString(),
    val kind: HomeItemKind,
    val column: Int = 0,
    val row: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val appId: String? = null,
    val widgetId: Int? = null,
    /** Flattened provider component + label at bind time; survives binding death for repair. */
    val widgetProvider: String? = null,
    val widgetLabel: String? = null,
    val title: String = "Folder",
    val contents: List<String> = emptyList(),
    val stack: List<HomeItem> = emptyList(),
) {
    fun widgetIds(): List<Int> = listOfNotNull(widgetId) + stack.flatMap { it.widgetIds() }
    fun intersects(other: HomeItem): Boolean = column < other.column + other.spanX &&
        column + spanX > other.column && row < other.row + other.spanY && row + spanY > other.row
}

data class HomePage(val id: String = UUID.randomUUID().toString(), val items: List<HomeItem> = emptyList())
data class HomeLayout(
    val pages: List<HomePage> = listOf(HomePage(id = "home")),
    val defaultPageId: String = pages.firstOrNull()?.id ?: "home",
    val hubPage: HomePage = HomePage(id = HUB_PAGE_ID),
) {
    fun surfaces(): List<HomePage> = listOf(hubPage) + pages
    fun surface(id: String): HomePage? = if (id == HUB_PAGE_ID) hubPage else pages.firstOrNull { it.id == id }
    fun allItems(): List<HomeItem> = surfaces().flatMap { it.items }
    fun item(id: String): HomeItem? = surfaces().firstNotNullOfOrNull { page -> page.items.firstOrNull { it.id == id } }
    fun pageOf(id: String): HomePage? = surfaces().firstOrNull { page -> page.items.any { it.id == id || it.stack.any { child -> child.id == id } } }
    fun resolvedDefaultPageId(): String = pages.firstOrNull { it.id == defaultPageId }?.id ?: pages.firstOrNull()?.id ?: "home"
}
data class HomeEdit(val layout: HomeLayout, val pageId: String? = null, val error: String? = null)

/** Pure placement rules. Display rotation never rewrites persisted cells or widget IDs. */
object HomeEditor {
    const val COLUMNS = 4
    const val ROWS = 6

    fun fits(page: HomePage, item: HomeItem): Boolean = item.column >= 0 && item.row >= 0 &&
        item.spanX in 1..COLUMNS && item.spanY in 1..ROWS &&
        item.column + item.spanX <= COLUMNS && item.row + item.spanY <= ROWS &&
        page.items.none { it.id != item.id && it.intersects(item) }

    fun firstSpace(page: HomePage, item: HomeItem): HomeItem? {
        for (row in 0 until ROWS) for (column in 0 until COLUMNS) {
            val placed = item.copy(column = column, row = row)
            if (fits(page, placed)) return placed
        }
        return null
    }

    fun add(layout: HomeLayout, pageId: String, item: HomeItem): HomeEdit {
        val page = layout.surface(pageId) ?: layout.pages.last()
        if (page.id == HUB_PAGE_ID && item.kind != HomeItemKind.WIDGET) {
            return HomeEdit(layout, error = "Only Android widgets can be added to the Hub.")
        }
        // Re-adding from the drawer is idempotent on this page, but other pages may have a copy.
        if (item.kind == HomeItemKind.APP && page.items.any { it.appId == item.appId }) return HomeEdit(layout, page.id)
        val positioned = firstSpace(page, item)
        if (positioned != null) return HomeEdit(replacePage(layout, page.copy(items = page.items + positioned)), page.id)
        if (page.id == HUB_PAGE_ID) return HomeEdit(layout, error = "The Hub widget area is full.")
        val next = HomePage()
        val placed = firstSpace(next, item) ?: return HomeEdit(layout, error = "This item is larger than a home page.")
        return HomeEdit(layout.copy(pages = layout.pages + next.copy(items = listOf(placed))), next.id)
    }

    fun move(layout: HomeLayout, requested: HomeItem, pageId: String, column: Int, row: Int): HomeEdit {
        val page = layout.surface(pageId) ?: return HomeEdit(layout)
        if (page.id == HUB_PAGE_ID && requested.kind != HomeItemKind.WIDGET) {
            return HomeEdit(layout, error = "Only Android widgets can be moved to the Hub.")
        }
        val item = if (requested.kind == HomeItemKind.APP && layout.item(requested.id) == null) {
            page.items.firstOrNull { it.kind == HomeItemKind.APP && it.appId == requested.appId } ?: requested
        } else requested
        val originPage = layout.pageOf(item.id)
        if (originPage?.id == pageId && item.column == column && item.row == row) return HomeEdit(layout, pageId)
        val positioned = item.copy(column = column, row = row)
        val target = page.items.firstOrNull { it.id != item.id && it.intersects(positioned) }

        // Dropping an app on an existing folder adds it to the folder's contents.
        if (item.kind == HomeItemKind.APP && target != null &&
            target.column == column && target.row == row && target.kind == HomeItemKind.FOLDER) {
            val apps = target.contents + listOfNotNull(item.appId)
            val folder = target.copy(contents = apps.distinct())
            val removed = remove(layout, item.id)
            return HomeEdit(replaceItem(removed, folder), pageId)
        }

        // Dropping an app on another 1x1 app on the same page swaps their positions cleanly without grouping.
        if (item.kind == HomeItemKind.APP && target != null &&
            target.kind == HomeItemKind.APP && target.spanX == 1 && target.spanY == 1 &&
            originPage?.id == pageId && item.spanX == 1 && item.spanY == 1) {
            val swappedTarget = target.copy(column = item.column, row = item.row)
            val swappedItem = item.copy(column = column, row = row)
            val updatedItems = page.items.filterNot { it.id == item.id || it.id == target.id } + listOf(swappedItem, swappedTarget)
            return HomeEdit(replacePage(layout, page.copy(items = updatedItems)), pageId)
        }

        if (!fits(page, positioned)) {
            // Drag target wins: whatever sits there slides to the nearest free cells.
            val plan = reflowed(page, positioned) ?: return HomeEdit(layout, error = "That space is occupied. Drop on an empty grid cell.")
            val removed = remove(layout, item.id)
            val destination = removed.surface(pageId) ?: return HomeEdit(layout)
            return HomeEdit(replacePage(removed, destination.copy(items = plan.items)), pageId)
        }
        val removed = remove(layout, item.id)
        val destination = removed.surface(pageId) ?: return HomeEdit(layout)
        return HomeEdit(replacePage(removed, destination.copy(items = destination.items + positioned)), pageId)
    }

    /**
     * Live drag preview: the dragged item takes [item]'s cells and every intersecting item
     * slides to the closest free position on the same page, or null when nothing fits.
     */
    fun reflowed(page: HomePage, item: HomeItem): HomePage? {
        val originalItem = page.items.firstOrNull { it.id == item.id }
        val base = page.items.filterNot { it.id == item.id }
        val moving = base.filter { it.intersects(item) }
        var result = page.copy(items = base.filterNot { moving.any { blocked -> blocked.id == it.id } } + item)
        val preferredCell = originalItem?.let { it.column to it.row }
        for (blocked in moving.sortedWith(compareBy({ it.row }, { it.column }))) {
            val moved = slideCandidates(blocked, preferredCell).firstOrNull { fits(result, it) } ?: return null
            result = result.copy(items = result.items + moved)
        }
        return result
    }

    /** Free placements for one item, nearest to its original cell first. */
    private fun slideCandidates(item: HomeItem, preferredCell: Pair<Int, Int>? = null): List<HomeItem> {
        val candidates = (0..COLUMNS - item.spanX).flatMap { column -> (0..ROWS - item.spanY).map { row -> item.copy(column = column, row = row) } }
            .filterNot { it.column == item.column && it.row == item.row }
            .sortedWith(compareBy(
                { kotlin.math.abs(it.column - item.column) + kotlin.math.abs(it.row - item.row) },
                { kotlin.math.abs(it.row - item.row) }, { it.column }))
        if (preferredCell == null) return candidates
        val preferred = item.copy(column = preferredCell.first, row = preferredCell.second)
        return if (preferred in candidates) {
            listOf(preferred) + candidates.filterNot { it.column == preferredCell.first && it.row == preferredCell.second }
        } else {
            candidates
        }
    }

    fun moveToPage(layout: HomeLayout, itemId: String, pageId: String): HomeEdit {
        val item = layout.item(itemId) ?: return HomeEdit(layout)
        val page = layout.surface(pageId) ?: return HomeEdit(layout)
        val place = firstSpace(page, item) ?: return HomeEdit(layout, error = "This page has no room for that item.")
        return move(layout, item, pageId, place.column, place.row)
    }

    fun remove(layout: HomeLayout, itemId: String): HomeLayout = layout.copy(
        pages = layout.pages.map { it.copy(items = it.items.filterNot { item -> item.id == itemId }) },
        hubPage = layout.hubPage.copy(items = layout.hubPage.items.filterNot { it.id == itemId }),
    )

    fun removePackage(layout: HomeLayout, packageName: String): HomeLayout {
        fun belongs(id: String) = id.startsWith("$packageName/")
        fun clean(item: HomeItem): HomeItem? = when (item.kind) {
            HomeItemKind.APP -> item.takeUnless { it.appId?.let(::belongs) == true }
            HomeItemKind.FOLDER -> item.copy(contents = item.contents.filterNot(::belongs))
            HomeItemKind.STACK -> {
                val children = item.stack.mapNotNull(::clean)
                when (children.size) {
                    0 -> null
                    1 -> children.single().copy(column = item.column, row = item.row)
                    else -> item.copy(stack = children)
                }
            }
            else -> item
        }
        return layout.copy(
            pages = layout.pages.map { it.copy(items = it.items.mapNotNull(::clean)) },
            hubPage = layout.hubPage.copy(items = layout.hubPage.items.mapNotNull(::clean)),
        )
    }

    /**
     * Swaps a dead widget binding for a fresh one in place: same tile, same page and cell
     * when the new spans still fit, otherwise the nearest free cells. Never deletes.
     */
    fun replaceWidgetBinding(
        layout: HomeLayout, itemId: String, widgetId: Int,
        provider: String?, label: String?, spanX: Int, spanY: Int,
    ): HomeEdit {
        val page = layout.pageOf(itemId) ?: return HomeEdit(layout, error = "That widget is gone.")
        val item = layout.item(itemId)?.takeIf { it.kind == HomeItemKind.WIDGET || it.kind == HomeItemKind.WIDGET_PROMPT }
            ?: return HomeEdit(layout, error = "That widget is gone.")
        var currentPage = page
        if (item.kind == HomeItemKind.WIDGET_PROMPT && spanX > 2) {
            val cleanedItems = currentPage.items.filterNot { it.id != item.id && it.kind == HomeItemKind.WIDGET_PROMPT && it.row == item.row }
            currentPage = currentPage.copy(items = cleanedItems)
        }
        val col = if (item.kind == HomeItemKind.WIDGET_PROMPT && spanX > 2) 0 else item.column
        val swapped = item.copy(
            kind = HomeItemKind.WIDGET,
            widgetId = widgetId,
            widgetProvider = provider,
            widgetLabel = label,
            column = col,
            row = item.row,
            spanX = spanX,
            spanY = spanY
        )
        val tempLayout = replacePage(layout, currentPage)
        if (fits(currentPage, swapped)) return HomeEdit(replaceItem(tempLayout, swapped), page.id)
        val placed = firstSpace(currentPage, swapped.copy(column = 0, row = 0))
            ?: return HomeEdit(layout, error = "There's no room for that widget here.")
        return HomeEdit(replaceItem(tempLayout, placed), page.id)
    }

    /** Widget tiles whose system binding is gone; layout untouched so they can be repaired. */
    fun orphanedWidgets(layout: HomeLayout, isAlive: (Int) -> Boolean): List<HomeItem> =
        layout.surfaces().flatMap { page -> page.items.flatMap { listOf(it) + it.stack } }
            .filter { it.kind == HomeItemKind.WIDGET && it.widgetId != null && !isAlive(it.widgetId) }

    fun resize(layout: HomeLayout, id: String, spanX: Int, spanY: Int, column: Int? = null, row: Int? = null): HomeEdit {
        val page = layout.pageOf(id) ?: return HomeEdit(layout)
        if (layout.item(id)?.kind == HomeItemKind.STACK) return HomeEdit(layout, error = "Unstack these items before resizing them.")
        val item = layout.item(id)!!.copy(
            spanX = spanX, spanY = spanY,
            column = column ?: layout.item(id)!!.column, row = row ?: layout.item(id)!!.row
        )
        if (!fits(page, item)) return HomeEdit(layout, error = "There isn't enough free space for that size.")
        return HomeEdit(replaceItem(layout, item), page.id)
    }

    fun folder(layout: HomeLayout, id: String, title: String, contents: List<String>): HomeLayout {
        val item = layout.item(id)?.takeIf { it.kind == HomeItemKind.FOLDER } ?: return layout
        return replaceItem(layout, item.copy(title = title.trim().take(40).ifEmpty { "Folder" }, contents = contents.distinct()))
    }

    fun stackCandidates(layout: HomeLayout, id: String): List<HomeItem> {
        val item = layout.item(id) ?: return emptyList()
        if (layout.pageOf(id)?.id == HUB_PAGE_ID) return emptyList()
        return layout.pages.flatMap { it.items }.filter { candidate ->
            candidate.id != id && candidate.spanX == item.spanX && candidate.spanY == item.spanY &&
                candidate.kind != HomeItemKind.FOLDER && item.kind != HomeItemKind.FOLDER &&
                candidate.kind != HomeItemKind.WIDGET_PROMPT && item.kind != HomeItemKind.WIDGET_PROMPT
        }
    }

    fun createStack(layout: HomeLayout, firstId: String, secondId: String): HomeEdit {
        val first = layout.item(firstId) ?: return HomeEdit(layout)
        val second = stackCandidates(layout, firstId).firstOrNull { it.id == secondId }
            ?: return HomeEdit(layout, error = "Choose another item with the same grid size.")
        val page = layout.pageOf(firstId)!!
        fun leaves(item: HomeItem) = if (item.kind == HomeItemKind.STACK) item.stack else listOf(item)
        val stack = HomeItem(kind = HomeItemKind.STACK, column = first.column, row = first.row,
            spanX = first.spanX, spanY = first.spanY, title = "Stack",
            stack = (leaves(first) + leaves(second)).map { it.copy(column = 0, row = 0) })
        val removed = remove(remove(layout, firstId), secondId)
        return HomeEdit(replacePage(removed, removed.pages.first { it.id == page.id }.let { it.copy(items = it.items + stack) }), page.id)
    }

    fun unstack(layout: HomeLayout, id: String): HomeEdit {
        val item = layout.item(id)?.takeIf { it.kind == HomeItemKind.STACK } ?: return HomeEdit(layout)
        val pageId = layout.pageOf(id)!!.id
        var result = remove(layout, id)
        item.stack.forEachIndexed { index, child ->
            result = if (index == 0) move(result, child, pageId, item.column, item.row).layout
                else add(result, pageId, child).layout
        }
        return HomeEdit(result, pageId)
    }

    /** Legacy upgrade: remove retired built-in tiles while preserving apps and native bindings. */
    fun upgradeDashboard(layout: HomeLayout): HomeLayout {
        return layout.copy(pages = layout.pages.map { page ->
            page.copy(items = page.items.filterNot { it.kind.isRetiredBuiltIn() })
        })
    }

    fun deletePage(layout: HomeLayout, id: String): HomeEdit {
        if (layout.pages.size == 1) return HomeEdit(layout, error = "Keep at least one home page.")
        if (layout.pages.firstOrNull { it.id == id }?.items?.isNotEmpty() == true) {
            return HomeEdit(layout, error = "Move or remove this page's items first.")
        }
        val pages = layout.pages.filterNot { it.id == id }
        val newDefault = if (layout.defaultPageId == id) pages.first().id else layout.defaultPageId
        return HomeEdit(layout.copy(pages = pages, defaultPageId = newDefault), pages.first().id)
    }

    /** Remove a page in one transaction, keeping every placement and native binding. */
    fun removePageKeepingItems(layout: HomeLayout, id: String): HomeEdit {
        val index = layout.pages.indexOfFirst { it.id == id }
        if (index < 0) return HomeEdit(layout)
        if (layout.pages.size == 1) return HomeEdit(layout, error = "Keep at least one home page.")
        val removed = layout.pages[index]
        val pages = layout.pages.filterNot { it.id == id }.toMutableList()
        val preferred = pages.indices.sortedBy { kotlin.math.abs(it - index.coerceAtMost(pages.lastIndex)) }
        // Pack large rectangles first; app shortcuts then fill the remaining single cells.
        val items = removed.items.sortedWith(compareByDescending<HomeItem> { it.spanX * it.spanY }.thenBy { it.row }.thenBy { it.column })
        for (item in items) {
            var placed = false
            for (destination in preferred) {
                val position = firstSpace(pages[destination], item) ?: continue
                // Deliberately preserve duplicate app shortcuts from different pages by identity.
                pages[destination] = pages[destination].copy(items = pages[destination].items + position)
                placed = true
                break
            }
            if (!placed) return HomeEdit(layout, error = "Other pages don't have enough free space. This page and all its items were kept.")
        }
        val targetPageId = pages[index.coerceAtMost(pages.lastIndex)].id
        val newDefault = if (layout.defaultPageId == id) targetPageId else layout.defaultPageId
        return HomeEdit(layout.copy(pages = pages, defaultPageId = newDefault), targetPageId)
    }

    fun removeEmptyPages(layout: HomeLayout): HomeEdit {
        val pages = layout.pages.filter { it.items.isNotEmpty() }.ifEmpty { listOf(layout.pages.first()) }
        val newDefault = if (pages.any { it.id == layout.defaultPageId }) layout.defaultPageId else pages.first().id
        return HomeEdit(layout.copy(pages = pages, defaultPageId = newDefault), pages.first().id)
    }

    fun movePage(layout: HomeLayout, id: String, offset: Int): HomeEdit {
        val from = layout.pages.indexOfFirst { it.id == id }
        if (from < 0) return HomeEdit(layout)
        val to = (from + offset).coerceIn(0, layout.pages.lastIndex)
        if (from == to) return HomeEdit(layout, id)
        val pages = layout.pages.toMutableList().apply { add(to, removeAt(from)) }
        return HomeEdit(layout.copy(pages = pages), id)
    }

    fun setDefaultPage(layout: HomeLayout, id: String): HomeEdit {
        if (layout.pages.none { it.id == id }) return HomeEdit(layout, error = "Page not found.")
        if (layout.defaultPageId == id) return HomeEdit(layout, id)
        return HomeEdit(layout.copy(defaultPageId = id), id)
    }

    fun undoPageEdit(current: HomeLayout, before: HomeLayout, after: HomeLayout): HomeEdit =
        if (current == after) HomeEdit(before)
        else HomeEdit(current, error = "Home changed since that page edit, so it couldn't be undone.")

    fun defaultLayout(apps: List<String>): HomeLayout {
        val prompt1 = HomeItem(
            id = "widget-prompt-1",
            kind = HomeItemKind.WIDGET_PROMPT,
            column = 0,
            row = 0,
            spanX = 2,
            spanY = 2,
            title = "Add widget"
        )
        val prompt2 = HomeItem(
            id = "widget-prompt-2",
            kind = HomeItemKind.WIDGET_PROMPT,
            column = 2,
            row = 0,
            spanX = 2,
            spanY = 2,
            title = "Add widget"
        )
        val initialApps = apps.distinct().take(4).mapIndexed { index, appId ->
            HomeItem(
                id = "app-$index",
                kind = HomeItemKind.APP,
                column = index,
                row = 2,
                spanX = 1,
                spanY = 1,
                appId = appId
            )
        }
        val page = HomePage(id = "home", items = listOf(prompt1, prompt2) + initialApps)
        return HomeLayout(pages = listOf(page), defaultPageId = "home")
    }

    fun migrate(apps: List<String>, widgets: List<Int>): HomeLayout {
        var layout = HomeLayout()
        apps.distinct().forEachIndexed { index, app ->
            layout = add(layout, layout.pages.last().id, HomeItem(id = "legacy-app-$index", kind = HomeItemKind.APP, appId = app)).layout
        }
        widgets.distinct().forEach { id ->
            layout = add(layout, layout.pages.last().id,
                HomeItem(id = "widget-$id", kind = HomeItemKind.WIDGET, widgetId = id, spanX = 2, spanY = 2)).layout
        }
        return layout
    }

    fun replacePage(layout: HomeLayout, page: HomePage) = if (page.id == HUB_PAGE_ID) {
        layout.copy(hubPage = page)
    } else layout.copy(pages = layout.pages.map { if (it.id == page.id) page else it })

    private fun replaceItem(layout: HomeLayout, item: HomeItem) = layout.copy(
        pages = layout.pages.map { page -> page.copy(items = page.items.map { if (it.id == item.id) item else it }) },
        hubPage = layout.hubPage.copy(items = layout.hubPage.items.map { if (it.id == item.id) item else it }),
    )
}

fun HomeItemKind.isRetiredBuiltIn(): Boolean =
    this == HomeItemKind.CLOCK || this == HomeItemKind.WEATHER || this == HomeItemKind.ANALOG_CLOCK
