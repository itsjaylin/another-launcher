package com.anotherlauncher.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anotherlauncher.data.HUB_PAGE_ID
import com.anotherlauncher.data.HomeEditor
import com.anotherlauncher.data.HomeItem
import com.anotherlauncher.data.HomePage
import com.anotherlauncher.widgets.WidgetController
import com.anotherlauncher.widgets.WidgetSizing

/** Adaptive native-widget canvas embedded beneath the Hub snapshot. */
@Composable
internal fun HubWidgetSection(
    page: HomePage,
    widgets: WidgetController,
    editing: Boolean,
    reducedMotion: Boolean,
    drag: LauncherDragState,
    targets: HomeDropTargets,
    onWidgetMenu: (HomeItem) -> Unit,
    onAddWidgets: () -> Unit,
) {
    DisposableEffect(page.id) { onDispose { targets.clear(HUB_PAGE_ID) } }

    @Composable
    fun AddWidgetCircularButton(modifier: Modifier = Modifier) {
        IconButton(
            onClick = onAddWidgets,
            modifier = modifier
                .size(54.dp)
                .glass(27, frosted = true)
                .testTag("hub-add-widgets")
                .semantics { contentDescription = "Add Widget" }
        ) {
            Box(contentAlignment = Alignment.Center) {
                GlyphIcon(
                    Glyph.PLUS,
                    Modifier
                        .size(26.dp)
                        .offset(y = 1.dp)
                        .blur(1.dp, BlurredEdgeTreatment.Unbounded),
                    tint = Color.Black.copy(alpha = 0.10f)
                )
                GlyphIcon(
                    Glyph.PLUS,
                    Modifier.size(26.dp),
                    tint = Color.White
                )
            }
        }
    }

    if (page.items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            AddWidgetCircularButton()
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = minOf(maxWidth / HomeEditor.COLUMNS, WidgetSizing.MAX_CELL_DP.dp)
            val gridWidth = cell * HomeEditor.COLUMNS
            val occupiedRows = page.items.maxOfOrNull { it.row + it.spanY }?.coerceIn(1, HomeEditor.ROWS) ?: 1
            val visibleRows = if (editing || drag.session != null) HomeEditor.ROWS else occupiedRows
            val gridHeight = cell * visibleRows
            val left = (maxWidth - gridWidth) / 2
            SideEffect { widgets.reportGrid(HUB_PAGE_ID, cell.value, cell.value) }

            Box(Modifier.fillMaxWidth().height(gridHeight)) {
                repeat(visibleRows) { row ->
                    repeat(HomeEditor.COLUMNS) { column ->
                        key(HUB_PAGE_ID, column, row) {
                            Box(
                                Modifier
                                    .absoluteOffset(left + cell * column, cell * row)
                                    .size(cell)
                                    .onGloballyPositioned {
                                        val full = Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
                                        targets.update(CellTarget(HUB_PAGE_ID, column, row, full, full))
                                    }
                                    .then(
                                        if (editing) Modifier.padding(4.dp).border(
                                            1.dp,
                                            LocalHomeColors.current.text.copy(alpha = .18f),
                                            RoundedCornerShape(18.dp),
                                        ) else Modifier
                                    ),
                            )
                        }
                    }
                }

                page.items.forEach { item ->
                    key(item.id) {
                        val itemLeft by animateDpAsState(left + cell * item.column + 4.dp, tween(if (reducedMotion) 0 else 220), label = "hub widget x")
                        val itemTop by animateDpAsState(cell * item.row + 4.dp, tween(if (reducedMotion) 0 else 220), label = "hub widget y")
                        Box(
                            Modifier
                                .absoluteOffset { IntOffset(itemLeft.roundToPx(), itemTop.roundToPx()) }
                                .size(cell * item.spanX - 8.dp, cell * item.spanY - 8.dp)
                                .testTag("hub-widget-tile")
                                .onGloballyPositioned {
                                    targets.updateItem(
                                        HUB_PAGE_ID,
                                        item.id,
                                        Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat())),
                                    )
                                },
                        ) {
                            WidgetTile(item, widgets, editing, drag, onWidgetMenu)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            AddWidgetCircularButton()
        }
    }
}

