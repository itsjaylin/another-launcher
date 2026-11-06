package com.anotherlauncher.widgets

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class WidgetRequirements(
    val defaultWidth: Float,
    val defaultHeight: Float,
    val minWidth: Float,
    val minHeight: Float,
    val resizeHorizontal: Boolean,
    val resizeVertical: Boolean,
    val maxWidth: Float = 0f,
    val maxHeight: Float = 0f,
)

data class WidgetFit(val width: Int, val height: Int, val scale: Float, val left: Float, val top: Float) {
    val visibleWidth get() = width * scale
    val visibleHeight get() = height * scale
}

/** Pixel inputs for rendering, dp inputs for grid calculations. No OEM constants or screen sizes. */
object WidgetSizing {
    const val GRID_GAP_DP = 8f
    const val STACK_CONTROLS_DP = 36f
    /** Caps the home cell so wide (unfolded) displays get surrounding breathing room
     * instead of a grid stretched edge to edge. Narrow cover displays never reach it. */
    const val MAX_CELL_DP = 100f
    fun isHomeProvider(category: Int): Boolean = category and 1 != 0

    /** Square home cell: the tightest of the width fit, the height fit, and the breathing-room cap. */
    fun gridCell(maxCellWidthDp: Float, maxCellHeightDp: Float, capDp: Float = MAX_CELL_DP): Float =
        min(maxCellWidthDp, maxCellHeightDp).coerceAtMost(capDp).coerceAtLeast(1f)

    fun fit(slotWidth: Int, slotHeight: Int, minWidth: Int, minHeight: Int): WidgetFit? {
        if (slotWidth <= 0 || slotHeight <= 0) return null
        val width = max(slotWidth, minWidth.coerceAtLeast(1))
        val height = max(slotHeight, minHeight.coerceAtLeast(1))
        val scale = min(1f, min(slotWidth.toFloat() / width, slotHeight.toFloat() / height))
        return WidgetFit(width, height, scale, (slotWidth - width * scale) / 2, (slotHeight - height * scale) / 2)
    }

    fun initialSpans(requirements: WidgetRequirements, targetX: Int, targetY: Int, cellWidth: Float, cellHeight: Float): Pair<Int, Int> {
        val x = ceil((max(requirements.defaultWidth, requirements.minWidth) + GRID_GAP_DP) / cellWidth.coerceAtLeast(1f)).toInt()
        val y = ceil((max(requirements.defaultHeight, requirements.minHeight) + GRID_GAP_DP) / cellHeight.coerceAtLeast(1f)).toInt()
        return max(x, targetX).coerceIn(1, 4) to max(y, targetY).coerceIn(1, 6)
    }

    fun canResize(requirements: WidgetRequirements, oldX: Int, oldY: Int, x: Int, y: Int, cellWidth: Float, cellHeight: Float): Boolean {
        if (x !in 1..4 || y !in 1..6) return false
        if (x != oldX && !requirements.resizeHorizontal || y != oldY && !requirements.resizeVertical) return false
        val width = x * cellWidth - GRID_GAP_DP
        val height = y * cellHeight - GRID_GAP_DP
        return width >= requirements.minWidth && height >= requirements.minHeight &&
            (requirements.maxWidth <= 0 || width <= requirements.maxWidth) &&
            (requirements.maxHeight <= 0 || height <= requirements.maxHeight)
    }

}
