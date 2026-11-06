package com.anotherlauncher.duo

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

/** WCAG relative luminance. Independent of Android so contrast decisions can be regression tested. */
object WallpaperContrast {
    fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((argb shr shift) and 255) / 255.0
            return if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
        }
        return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
    }
    fun useDarkText(colors: List<Int>, fallback: Boolean): Boolean {
        if (colors.isEmpty()) return fallback
        val average = colors.map(::luminance).average()
        return (average + .05) / .05 >= 1.05 / (average + .05)
    }
}

/** Reads public wallpaper color metadata, never wallpaper pixels or a screen capture. */
class WallpaperToneTracker(private val activity: ComponentActivity) : DefaultLifecycleObserver {
    private val manager = WallpaperManager.getInstance(activity)
    private val mutableColors = MutableStateFlow<List<Int>>(emptyList())
    val colors = mutableColors.asStateFlow()
    private var active = false
    private val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
        if (which and WallpaperManager.FLAG_SYSTEM != 0) publish(colors)
    }
    init { activity.lifecycle.addObserver(this) }
    private fun publish(colors: WallpaperColors?) {
        mutableColors.value = listOfNotNull(colors?.primaryColor, colors?.secondaryColor, colors?.tertiaryColor).map { it.toArgb() }
    }
    override fun onStart(owner: LifecycleOwner) {
        if (active) return
        active = true
        manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        try { publish(manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)) }
        catch (_: SecurityException) { publish(null) }
    }
    override fun onStop(owner: LifecycleOwner) {
        if (!active) return
        active = false
        manager.removeOnColorsChangedListener(listener)
    }
}
