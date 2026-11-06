package com.anotherlauncher.ui

/** Outer pager: hub + home spreads + library.
 *
 * Folded/Narrow (perSpread == 1):
 *   Page 0: Hub
 *   Pages 1..spreadCount: Home spreads (1 page each)
 *   Page spreadCount + 1: App Library
 *   Total pages = spreadCount + 2
 *
 * Unfolded/Wide (perSpread == 2):
 *   Spread 0: Hub (left pane) + Home Page 1 (right pane)
 *   Spreads 1..spreadCount - 1: Subsequent home pages paired 2 per spread (Page 2+3, Page 4+5, etc.)
 *   Spread spreadCount: App Library
 *   Total pages = spreadCount + 1
 */
object PagerMap {
    const val HUB_KEY = "hub"
    const val LIBRARY_KEY = "library"

    fun spreads(pageCount: Int, perSpread: Int = 1): Int = when (perSpread) {
        1 -> maxOf(1, pageCount)
        else -> 1 + (maxOf(0, pageCount - 1) + 1) / 2
    }

    fun totalPages(spreadCount: Int, perSpread: Int = 1): Int = when (perSpread) {
        1 -> spreadCount + 2
        else -> spreadCount + 1
    }

    fun isHub(i: Int, perSpread: Int = 1): Boolean = when (perSpread) {
        1 -> i == 0
        else -> false // On wide, Hub is the left pane of Spread 0
    }

    fun isHomeSpread(i: Int, sc: Int, perSpread: Int = 1): Boolean = when (perSpread) {
        1 -> i in 1..sc
        else -> i in 0 until sc
    }

    fun isLibrary(i: Int, sc: Int, perSpread: Int = 1): Boolean = when (perSpread) {
        1 -> i == sc + 1
        else -> i == sc
    }

    fun spreadOfPage(ids: List<String>, id: String, ps: Int = 1): Int = when (ps) {
        1 -> ids.indexOf(id).coerceAtLeast(0)
        else -> {
            val idx = ids.indexOf(id)
            if (idx <= 0) 0
            else 1 + (idx - 1) / 2
        }
    }

    fun pageIndex(ids: List<String>, selected: String, sc: Int, ps: Int = 1): Int = when (ps) {
        1 -> when (selected) {
            HUB_KEY -> 0
            LIBRARY_KEY -> sc + 1
            else -> (ids.indexOf(selected).coerceAtLeast(0)).coerceIn(0, sc - 1) + 1
        }
        else -> when (selected) {
            HUB_KEY -> 0
            LIBRARY_KEY -> sc
            else -> {
                val idx = ids.indexOf(selected)
                if (idx <= 0) 0
                else (1 + (idx - 1) / 2).coerceIn(0, sc - 1)
            }
        }
    }

    fun pageKey(ids: List<String>, i: Int, sc: Int, ps: Int = 1): String = when (ps) {
        1 -> when {
            i == 0 -> HUB_KEY
            i == sc + 1 -> LIBRARY_KEY
            else -> ids.getOrNull(i - 1) ?: "spread-${i - 1}"
        }
        else -> when {
            i == 0 -> "spread-hub-${ids.firstOrNull() ?: "0"}"
            i == sc -> LIBRARY_KEY
            else -> {
                val leftIdx = 1 + (i - 1) * 2
                ids.getOrNull(leftIdx) ?: "spread-$i"
            }
        }
    }
}
