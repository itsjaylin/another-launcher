package com.anotherlauncher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import com.anotherlauncher.widgets.FittingWidgetFrame

/** Observe native input without consuming scrolling; take ownership only after a stationary hold. */
suspend fun PointerInputScope.detectWidgetPickup(
    itemId: String,
    label: () -> String,
    bounds: () -> Rect,
    frame: () -> FittingWidgetFrame?,
    drag: LauncherDragState,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var last = down.position
        val endedBeforeHold = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed || event.changes.count { it.pressed } > 1 ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                last = change.position
            }
            true
        }
        if (endedBeforeHold == null) {
            val native = frame()
            var finished = false
            try {
                native?.claimGestureForLauncher()
                drag.begin(null, bounds().topLeft + last, false, itemId, label(), bounds())
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || event.changes.count { it.pressed } > 1 || drag.session?.itemId != itemId) {
                        event.changes.forEach { it.consume() }
                        break
                    }
                    // Both positions use the current coordinate transform. A page sliding under
                    // the held widget must not be mistaken for finger movement.
                    drag.move(change.position - change.previousPosition)
                    change.consume()
                    if (!change.pressed) {
                        drag.finish()
                        finished = true
                        break
                    }
                }
            } finally {
                if (!finished) drag.cancel()
                native?.finishLauncherGesture()
            }
        }
    }
}
