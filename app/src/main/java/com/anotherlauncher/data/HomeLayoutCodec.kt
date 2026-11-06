package com.anotherlauncher.data

import org.json.JSONArray
import org.json.JSONObject

/** Strict, versioned storage. Old layouts are readable; unsupported/corrupt data is never replaced. */
object HomeLayoutCodec {
    fun encode(layout: HomeLayout): String = JSONObject().put("version", 5)
        .put("defaultPage", layout.resolvedDefaultPageId())
        .put("hub", JSONArray().apply { layout.hubPage.items.forEach { put(encodeItem(it)) } })
        .put("pages", JSONArray().apply {
            layout.pages.forEach { page -> put(JSONObject().put("id", page.id)
                .put("items", JSONArray().apply { page.items.forEach { put(encodeItem(it)) } })) }
        }).toString()

    private fun encodeItem(item: HomeItem): JSONObject = JSONObject().apply {
        put("id", item.id); put("kind", item.kind.name)
        put("x", item.column); put("y", item.row); put("w", item.spanX); put("h", item.spanY)
        put("app", item.appId); put("widget", item.widgetId); put("title", item.title)
        item.widgetProvider?.let { put("wprov", it) }
        item.widgetLabel?.let { put("wlabel", it) }
        put("contents", JSONArray(item.contents))
        put("stack", JSONArray().apply { item.stack.forEach { put(encodeItem(it)) } })
    }

    fun decode(json: String): HomeLayout {
        val root = JSONObject(json)
        val version = root.getInt("version")
        require(version in 2..5) { "Unsupported home layout version" }
        val ids = mutableSetOf<String>()
        val widgets = mutableSetOf<Int>()
        fun withoutRetiredBuiltIns(item: HomeItem): HomeItem? {
            if (item.kind.isRetiredBuiltIn()) return null
            if (item.kind != HomeItemKind.STACK) return item
            val children = item.stack.filterNot { it.kind.isRetiredBuiltIn() }
            return when (children.size) {
                0 -> null
                1 -> children.single().copy(column = item.column, row = item.row)
                else -> item.copy(stack = children)
            }
        }
        fun item(value: JSONObject, depth: Int): HomeItem {
            require(depth <= 1) { "Nested stacks aren't supported" }
            val result = HomeItem(
                id = value.getString("id"), kind = HomeItemKind.valueOf(value.getString("kind")),
                column = value.getInt("x"), row = value.getInt("y"), spanX = value.getInt("w"), spanY = value.getInt("h"),
                appId = value.optString("app").takeIf(String::isNotBlank),
                widgetId = if (value.has("widget") && !value.isNull("widget")) value.getInt("widget") else null,
                widgetProvider = value.optString("wprov").takeIf(String::isNotBlank),
                widgetLabel = value.optString("wlabel").takeIf(String::isNotBlank),
                title = value.optString("title", "Folder"),
                contents = value.getJSONArray("contents").let { array -> (0 until array.length()).map(array::getString).distinct() },
                stack = value.optJSONArray("stack")?.let { array -> (0 until array.length()).map { item(array.getJSONObject(it), depth + 1) } } ?: emptyList(),
            )
            require(result.id.isNotBlank() && ids.add(result.id))
            require(result.spanX in 1..4 && result.spanY in 1..6)
            if (result.kind != HomeItemKind.STACK) require(result.stack.isEmpty())
            when (result.kind) {
                HomeItemKind.APP -> require(!result.appId.isNullOrBlank() && result.spanX == 1 && result.spanY == 1)
                HomeItemKind.WIDGET -> require(result.widgetId != null && result.widgetId >= 0 && widgets.add(result.widgetId))
                HomeItemKind.FOLDER -> require(result.spanX == 1 && result.spanY == 1)
                HomeItemKind.STACK -> require(result.stack.size >= 2 && result.stack.all {
                    it.kind != HomeItemKind.STACK && it.spanX == result.spanX && it.spanY == result.spanY
                })
                HomeItemKind.WIDGET_PROMPT -> require(result.spanX in 1..4 && result.spanY in 1..6)
                else -> Unit
            }
            return result
        }
        val pagesArray = root.getJSONArray("pages")
        require(pagesArray.length() > 0)
        val pages = (0 until pagesArray.length()).map { index ->
            val page = pagesArray.getJSONObject(index)
            val id = page.getString("id")
            require(id.isNotBlank() && id != HUB_PAGE_ID && ids.add(id))
            val items = page.getJSONArray("items")
            val parsed = (0 until items.length()).mapNotNull { withoutRetiredBuiltIns(item(items.getJSONObject(it), 0)) }
            HomePage(id, parsed).also { result -> require(parsed.all { HomeEditor.fits(result, it) }) { "Overlapping or out-of-bounds items" } }
        }
        val hubItems = if (version >= 5) root.optJSONArray("hub")?.let { array ->
            (0 until array.length()).map { item(array.getJSONObject(it), 0) }
        }.orEmpty() else emptyList()
        require(hubItems.all { it.kind == HomeItemKind.WIDGET }) { "Only widgets can be stored on the Hub" }
        val hubPage = HomePage(HUB_PAGE_ID, hubItems).also { result ->
            require(hubItems.all { HomeEditor.fits(result, it) }) { "Overlapping or out-of-bounds Hub widgets" }
        }
        val defaultPage = root.optString("defaultPage").takeIf { id -> pages.any { it.id == id } } ?: pages.first().id
        return HomeLayout(pages = pages, defaultPageId = defaultPage, hubPage = hubPage)
    }
}
