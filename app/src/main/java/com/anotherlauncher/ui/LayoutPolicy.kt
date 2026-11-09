package com.anotherlauncher.ui

import com.anotherlauncher.duo.DisplaySurface
import com.anotherlauncher.duo.FoldPosture
import com.anotherlauncher.duo.FoldState

enum class DockSide { LEFT, RIGHT }
enum class DockPlacement { LEFT, RIGHT, BOTTOM }

object LayoutPolicy {
    fun dock(state: FoldState, preference: DockSide, innerSideLayout: Boolean = false): DockPlacement {
        if (state.surface == DisplaySurface.INNER && state.posture != FoldPosture.CLOSED &&
            state.window.height > state.window.width && !state.inMultiWindow && !innerSideLayout) return DockPlacement.BOTTOM
        return when (preference) {
            DockSide.LEFT -> DockPlacement.LEFT
            DockSide.RIGHT -> DockPlacement.RIGHT
        }
    }
}
