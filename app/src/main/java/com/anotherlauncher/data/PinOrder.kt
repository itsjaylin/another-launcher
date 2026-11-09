package com.anotherlauncher.data

object PinOrder {
    /** Index is an insertion boundary in the original list, before removal of a dragged item. */
    fun insert(ids: List<String>, id: String, index: Int, capacity: Int): List<String> {
        val existing = ids.indexOf(id)
        if (existing < 0 && ids.size >= capacity) return ids
        val boundary = index.coerceIn(0, ids.size)
        val target = boundary - if (existing in 0 until boundary) 1 else 0
        return ids.filterNot { it == id }.toMutableList().apply { add(target.coerceIn(0, size), id) }
    }
}
