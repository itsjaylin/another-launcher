package com.anotherlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anotherlauncher.data.HomeEditor
import com.anotherlauncher.data.HomeItem
import com.anotherlauncher.data.HomeLayout
import com.anotherlauncher.widgets.WidgetController
import com.anotherlauncher.widgets.WidgetRequirements
import com.anotherlauncher.widgets.WidgetSizing
import kotlin.math.roundToInt

// ─── Handle Types ─────────────────────────────────────────────────────────────

private enum class HandleType(
    val affectsH: Boolean,
    val isRight: Boolean,
    val isLeft: Boolean,
    val affectsV: Boolean,
    val isBottom: Boolean,
    val isTop: Boolean,
) {
    RIGHT(affectsH = true, isRight = true, isLeft = false, affectsV = false, isBottom = false, isTop = false),
    LEFT(affectsH = true, isRight = false, isLeft = true, affectsV = false, isBottom = false, isTop = false),
    BOTTOM(affectsH = false, isRight = false, isLeft = false, affectsV = true, isBottom = true, isTop = false),
    TOP(affectsH = false, isRight = false, isLeft = false, affectsV = true, isBottom = false, isTop = true),
    TOP_LEFT(affectsH = true, isRight = false, isLeft = true, affectsV = true, isBottom = false, isTop = true),
    TOP_RIGHT(affectsH = true, isRight = true, isLeft = false, affectsV = true, isBottom = false, isTop = true),
    BOTTOM_LEFT(affectsH = true, isRight = false, isLeft = true, affectsV = true, isBottom = true, isTop = false),
    BOTTOM_RIGHT(affectsH = true, isRight = true, isLeft = false, affectsV = true, isBottom = true, isTop = false),
}

// ─── WidgetResizeOverlay ──────────────────────────────────────────────────────

/**
 * Full-screen overlay that displays live drag handles around a resizable widget.
 *
 * All pointer tracking is processed by the stationary root Box in root screen coordinates,
 * eliminating pointer delta cancellation caused by handle layout translation.
 * Target state (spanX, spanY, column, row) is tracked in immediate memory state for instantaneous
 * visual feedback and haptics, while committing changes asynchronously to persistence.
 */
@Composable
fun WidgetResizeOverlay(
    item: HomeItem,
    layout: HomeLayout,
    targets: HomeDropTargets,
    widgets: WidgetController,
    onDismiss: () -> Unit,
    onResize: (spanX: Int, spanY: Int, col: Int, row: Int) -> Unit,
    onDragStarted: (() -> Unit)? = null,
) {
    val info = item.widgetId?.let(widgets.manager::getAppWidgetInfo) ?: return
    val requirements = widgets.requirements(info)
    val page = layout.pageOf(item.id) ?: return

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val viewConfig = LocalViewConfiguration.current
    val insetPx = with(density) { 4.dp.toPx() }

    // Immediate local state for responsive gesture feedback
    var currentSpanX by remember(item.id) { mutableIntStateOf(item.spanX) }
    var currentSpanY by remember(item.id) { mutableIntStateOf(item.spanY) }
    var currentColumn by remember(item.id) { mutableIntStateOf(item.column) }
    var currentRow by remember(item.id) { mutableIntStateOf(item.row) }
    var activeHandle by remember { mutableStateOf<HandleType?>(null) }

    // Sync from external item state when not actively dragging
    LaunchedEffect(item.spanX, item.spanY, item.column, item.row) {
        if (activeHandle == null) {
            currentSpanX = item.spanX
            currentSpanY = item.spanY
            currentColumn = item.column
            currentRow = item.row
        }
    }

    val visualRect = targets.itemRegionBounds(
        pageId = page.id,
        column = currentColumn,
        row = currentRow,
        spanX = currentSpanX,
        spanY = currentSpanY,
        insetPx = insetPx,
    ) ?: targets.itemBounds(item.id).takeIf { it.width > 0f && it.height > 0f }
    ?: return

    val sampleCell = targets.cellTarget(page.id, currentColumn, currentRow)?.fullBounds
        ?: targets.cellTarget(page.id, 0, 0)?.fullBounds
    val cellWidthPx = sampleCell?.width ?: (visualRect.width / currentSpanX.coerceAtLeast(1))
    val cellHeightPx = sampleCell?.height ?: (visualRect.height / currentSpanY.coerceAtLeast(1))

    val cellWidthDp = with(density) { cellWidthPx.toDp().value }
    val cellHeightDp = with(density) { cellHeightPx.toDp().value }

    val latestPage by rememberUpdatedState(page)
    val latestRequirements by rememberUpdatedState(requirements)

    val canResizeTo = { sx: Int, sy: Int, col: Int, row: Int ->
        if (col < 0 || row < 0) false
        else if (col + sx > HomeEditor.COLUMNS || row + sy > HomeEditor.ROWS) false
        else if (sx < 1 || sy < 1) false
        else {
            val candidate = item.copy(spanX = sx, spanY = sy, column = col, row = row)
            HomeEditor.fits(latestPage, candidate) &&
                WidgetSizing.canResize(latestRequirements, currentSpanX, currentSpanY, sx, sy, cellWidthDp, cellHeightDp)
        }
    }

    val handles = remember(visualRect, requirements) {
        getHandlePositions(visualRect, requirements)
    }

    val currentHandles by rememberUpdatedState(handles)
    val currentVisualRect by rememberUpdatedState(visualRect)
    val currentCellWidthPx by rememberUpdatedState(cellWidthPx)
    val currentCellHeightPx by rememberUpdatedState(cellHeightPx)
    val currentCanResizeTo by rememberUpdatedState(canResizeTo)
    val currentOnResize by rememberUpdatedState(onResize)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnDragStarted by rememberUpdatedState(onDragStarted)

    BackHandler { onDismiss() }

    val touchRadiusPx = with(density) { 36.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    val hit = findHitHandle(downPos, currentHandles, touchRadiusPx)

                    if (hit == null) {
                        // Tapped outside handles — only dismiss if outside the widget area
                        val widgetBounds = currentVisualRect.inflate(with(density) { 24.dp.toPx() })
                        if (!widgetBounds.contains(downPos)) {
                            var dragged = false
                            val pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (!change.pressed) {
                                    if (!dragged) currentOnDismiss()
                                    break
                                }
                                if ((change.position - downPos).getDistance() > viewConfig.touchSlop) {
                                    dragged = true
                                }
                            }
                        }
                    } else {
                        // Handle grabbed!
                        down.consume()
                        currentOnDragStarted?.invoke()
                        activeHandle = hit

                        val startSpanX = currentSpanX
                        val startSpanY = currentSpanY
                        val startColumn = currentColumn
                        val startRow = currentRow
                        var anchorX = downPos.x
                        var anchorY = downPos.y
                        val pointerId = down.id

                        var stepX = 0
                        var stepY = 0

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) break

                            change.consume()
                            val currentPos = change.position

                            val cellW = currentCellWidthPx
                            val cellH = currentCellHeightPx
                            val deadbandX = cellW * 0.12f
                            val deadbandY = cellH * 0.12f

                            var candStepX = stepX
                            var candStepY = stepY

                            if (hit.affectsH) {
                                val totalDeltaX = currentPos.x - anchorX
                                val dragDirX = if (hit.isRight) 1 else -1
                                val effectiveDeltaX = totalDeltaX * dragDirX

                                val expandThresholdX = (stepX + 0.5f) * cellW + deadbandX
                                val shrinkThresholdX = (stepX - 0.5f) * cellW - deadbandX

                                if (effectiveDeltaX > expandThresholdX) {
                                    candStepX = stepX + 1
                                } else if (effectiveDeltaX < shrinkThresholdX) {
                                    candStepX = stepX - 1
                                }
                            }

                            if (hit.affectsV) {
                                val totalDeltaY = currentPos.y - anchorY
                                val dragDirY = if (hit.isBottom) 1 else -1
                                val effectiveDeltaY = totalDeltaY * dragDirY

                                val expandThresholdY = (stepY + 0.5f) * cellH + deadbandY
                                val shrinkThresholdY = (stepY - 0.5f) * cellH - deadbandY

                                if (effectiveDeltaY > expandThresholdY) {
                                    candStepY = stepY + 1
                                } else if (effectiveDeltaY < shrinkThresholdY) {
                                    candStepY = stepY - 1
                                }
                            }

                            val hChanged = candStepX != stepX
                            val vChanged = candStepY != stepY

                            if (hChanged || vChanged) {
                                val candSpanX = startSpanX + candStepX
                                val candCol = if (hit.isLeft) startColumn - candStepX else startColumn
                                val candSpanY = startSpanY + candStepY
                                val candRow = if (hit.isTop) startRow - candStepY else startRow

                                val bothFit = hChanged && vChanged && currentCanResizeTo(candSpanX, candSpanY, candCol, candRow)
                                val hOnlyFit = hChanged && currentCanResizeTo(candSpanX, currentSpanY, candCol, currentRow)
                                val vOnlyFit = vChanged && currentCanResizeTo(currentSpanX, candSpanY, currentColumn, candRow)

                                var applyH = false
                                var applyV = false

                                if (bothFit) {
                                    applyH = true
                                    applyV = true
                                } else if (hOnlyFit && !vOnlyFit) {
                                    applyH = true
                                } else if (vOnlyFit && !hOnlyFit) {
                                    applyV = true
                                } else if (hOnlyFit && vOnlyFit) {
                                    applyH = true
                                }

                                if (applyH) {
                                    stepX = candStepX
                                    currentSpanX = candSpanX
                                    currentColumn = candCol
                                } else if (hChanged) {
                                    val dragDirX = if (hit.isRight) 1 else -1
                                    val limitDeltaX = if (candStepX > stepX) {
                                        (stepX + 0.5f) * cellW + deadbandX
                                    } else {
                                        (stepX - 0.5f) * cellW - deadbandX
                                    }
                                    anchorX = currentPos.x - dragDirX * limitDeltaX
                                }

                                if (applyV) {
                                    stepY = candStepY
                                    currentSpanY = candSpanY
                                    currentRow = candRow
                                } else if (vChanged) {
                                    val dragDirY = if (hit.isBottom) 1 else -1
                                    val limitDeltaY = if (candStepY > stepY) {
                                        (stepY + 0.5f) * cellH + deadbandY
                                    } else {
                                        (stepY - 0.5f) * cellH - deadbandY
                                    }
                                    anchorY = currentPos.y - dragDirY * limitDeltaY
                                }

                                if (applyH || applyV) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentOnResize(currentSpanX, currentSpanY, currentColumn, currentRow)
                                }
                            }
                        }

                        activeHandle = null
                    }
                }
            }
    ) {
        // ── Selection border and tint ──
        Box(
            Modifier
                .absoluteOffset { IntOffset(visualRect.left.roundToInt(), visualRect.top.roundToInt()) }
                .size(with(density) { visualRect.width.toDp() }, with(density) { visualRect.height.toDp() })
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
        )

        // ── Floating Size Pill ──
        val pillMarginPx = with(density) { 10.dp.toPx() }
        val pillHeightPx = with(density) { 28.dp.toPx() }
        val labelY = if (visualRect.top >= pillHeightPx + pillMarginPx) {
            visualRect.top - pillHeightPx - with(density) { 6.dp.toPx() }
        } else {
            visualRect.bottom + pillMarginPx
        }
        var pillWidthPx by remember { mutableIntStateOf(0) }
        Surface(
            modifier = Modifier
                .onSizeChanged { pillWidthPx = it.width }
                .absoluteOffset {
                    IntOffset(
                        (visualRect.center.x - pillWidthPx / 2f).roundToInt(),
                        labelY.roundToInt(),
                    )
                },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 4.dp,
        ) {
            Text(
                text = "$currentSpanX × $currentSpanY",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        // ── Visual Handles ──
        handles.forEach { (type, center) ->
            val isCorner = type in setOf(HandleType.TOP_LEFT, HandleType.TOP_RIGHT, HandleType.BOTTOM_LEFT, HandleType.BOTTOM_RIGHT)
            val dotSize = if (isCorner) 8.dp else 10.dp

            Box(
                Modifier
                    .absoluteOffset {
                        IntOffset(
                            (center.x - with(density) { (dotSize / 2).toPx() }).roundToInt(),
                            (center.y - with(density) { (dotSize / 2).toPx() }).roundToInt(),
                        )
                    }
                    .size(dotSize)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

// ─── Handle Position Calculation ──────────────────────────────────────────────

private fun getHandlePositions(
    rect: Rect,
    requirements: WidgetRequirements,
): Map<HandleType, Offset> = buildMap {
    if (requirements.resizeHorizontal) {
        put(HandleType.RIGHT, Offset(rect.right, rect.center.y))
        put(HandleType.LEFT, Offset(rect.left, rect.center.y))
    }
    if (requirements.resizeVertical) {
        put(HandleType.BOTTOM, Offset(rect.center.x, rect.bottom))
        put(HandleType.TOP, Offset(rect.center.x, rect.top))
    }
    if (requirements.resizeHorizontal && requirements.resizeVertical) {
        put(HandleType.TOP_LEFT, Offset(rect.left, rect.top))
        put(HandleType.TOP_RIGHT, Offset(rect.right, rect.top))
        put(HandleType.BOTTOM_LEFT, Offset(rect.left, rect.bottom))
        put(HandleType.BOTTOM_RIGHT, Offset(rect.right, rect.bottom))
    }
}

private fun findHitHandle(
    touchPos: Offset,
    handles: Map<HandleType, Offset>,
    touchRadiusPx: Float,
): HandleType? = handles.entries
    .filter { (it.value - touchPos).getDistance() <= touchRadiusPx }
    .minByOrNull { (it.value - touchPos).getDistance() }
    ?.key
