package com.anotherlauncher.widgets

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup

object RoundedCornerEnforcement {
    fun findBackground(appWidget: View): View? {
        val backgrounds = mutableListOf<View>()
        accumulateViewsWithId(appWidget, android.R.id.background, backgrounds)
        if (backgrounds.size == 1) return backgrounds[0]
        if (appWidget is ViewGroup && appWidget.childCount > 0) {
            return findUndefinedBackground(appWidget.getChildAt(0))
        }
        return appWidget
    }

    fun hasAppWidgetOptedOut(background: View): Boolean {
        return background.id == android.R.id.background && background.clipToOutline
    }

    fun computeRoundedRectangle(appWidget: View, background: View, outRect: Rect) {
        outRect.set(0, 0, background.width, background.height)
        var current: View? = background
        while (current != null && current !== appWidget) {
            outRect.offset(current.left, current.top)
            current = current.parent as? View
        }
    }

    fun computeEnforcedRadius(context: Context): Float {
        val res = context.resources
        val systemRadius = res.getDimension(android.R.dimen.system_app_widget_background_radius)
        // AOSP Launcher3 uses Math.min(16dp, systemRadius) by default, or just systemRadius.
        // One UI's squircle makes systemRadius look good. Let's use systemRadius directly up to 28dp,
        // matching the behavior where `useSystemRadiusForAppWidgets` is true (the current Android direction).
        return systemRadius.coerceAtMost(28f * res.displayMetrics.density)
    }

    private fun accumulateViewsWithId(view: View, viewId: Int, output: MutableList<View>) {
        if (view.id == viewId) {
            output.add(view)
            return
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                accumulateViewsWithId(view.getChildAt(i), viewId, output)
            }
        }
    }

    private fun isViewVisible(view: View): Boolean {
        if (view.visibility != View.VISIBLE) return false
        return !view.willNotDraw() || view.foreground != null || view.background != null
    }

    private fun findUndefinedBackground(current: View): View? {
        if (current.visibility != View.VISIBLE) return null
        if (isViewVisible(current)) return current
        var lastVisibleView: View? = null
        if (current is ViewGroup) {
            for (i in 0 until current.childCount) {
                val visibleView = findUndefinedBackground(current.getChildAt(i))
                if (visibleView != null) {
                    if (lastVisibleView != null) return current // At least two visible children
                    lastVisibleView = visibleView
                }
            }
        }
        return lastVisibleView
    }
}
