package com.anotherlauncher.duo

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Activity-scoped posture source. Construction attaches to START/STOP automatically.
 * No permissions are needed for TYPE_HINGE_ANGLE. OEMs may omit it or reject registration.
 * WindowManager and maximum-window metrics remain useful in that case.
 *
 * All writes are on main. No Activity, listener, or sensor survives onDestroy/close.
 */
@MainThread
class FoldStateTracker(private val activity: ComponentActivity) : DefaultLifecycleObserver, AutoCloseable {
    private val sensors = activity.getSystemService(SensorManager::class.java)
    private val displays = activity.getSystemService(DisplayManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(FoldState())
    val state = mutableState.asStateFlow()
    private var layoutJob: Job? = null
    private var feature: FoldingFeature? = null
    private var started = false
    private var sensorRegistered = false
    private var lastSensorTimestamp = 0L
    private var lastUnfoldedTimestamp = 0L
    private var lastClosingTimestamp = 0L

    var onFoldClosing: (() -> Unit)? = null
    var onInnerToCoverHandoff: (() -> Unit)? = null

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!started || event.timestamp < lastSensorTimestamp) return
            val angle = event.values.firstOrNull()?.let(FoldMath::validAngle) ?: return
            lastSensorTimestamp = event.timestamp
            val wasUnfolded = mutableState.value.surface == DisplaySurface.INNER ||
                    (SystemClock.elapsedRealtime() - lastUnfoldedTimestamp < 3000L)
            if (wasUnfolded && angle <= 75f) {
                lastClosingTimestamp = SystemClock.elapsedRealtime()
                onFoldClosing?.invoke()
            }
            mutableState.value = mutableState.value.copy(
                hingeAngle = angle, sampleNanos = event.timestamp,
            )
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    private val layoutListener = View.OnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
        if (l != ol || t != ot || r != or || b != ob) refreshWindowMetrics()
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refreshWindowMetrics()
        override fun onDisplayRemoved(displayId: Int) = refreshWindowMetrics()
        override fun onDisplayChanged(displayId: Int) {
            val prevSurface = mutableState.value.surface
            refreshWindowMetrics()
            val newSurface = mutableState.value.surface
            if (prevSurface == DisplaySurface.INNER && newSurface == DisplaySurface.COVER) {
                lastClosingTimestamp = SystemClock.elapsedRealtime()
                onInnerToCoverHandoff?.invoke()
            }
        }
    }

    init {
        refreshWindowMetrics()
        activity.lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        if (started) return
        started = true
        feature = null
        lastSensorTimestamp = 0L
        mutableState.value = mutableState.value.copy(hingeAngle = null, sampleNanos = 0L)
        refreshWindowMetrics()
        activity.window.decorView.addOnLayoutChangeListener(layoutListener)
        displays?.registerDisplayListener(displayListener, main)
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        sensorRegistered = try {
            sensor != null && sensors.registerListener(
                sensorListener, sensor, SensorManager.SENSOR_DELAY_GAME, main,
            ) == true
        } catch (error: SecurityException) {
            Log.i(TAG, "Hinge sensor unavailable to this application", error)
            false
        }
        mutableState.value = mutableState.value.copy(sensorAvailable = sensorRegistered)
        layoutJob = activity.lifecycleScope.launch {
            try {
                WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
                    feature = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                    refreshWindowMetrics()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: RuntimeException) {
                // Some OEM WindowExtensions implementations fail. Retain sensor/metrics support.
                Log.w(TAG, "WindowExtensions unavailable; using sensor and metrics", error)
                feature = null
                refreshWindowMetrics()
            }
        }
    }

    /** Also call from onConfigurationChanged and onMultiWindowModeChanged. */
    fun refreshWindowMetrics() {
        val calculator = WindowMetricsCalculator.getOrCreate()
        val current = try { calculator.computeCurrentWindowMetrics(activity).bounds }
        catch (_: Exception) { return }
        // During a panel handoff the window can report a transient zero size; keep the last
        // good metrics instead of publishing an empty layout that blanks the grid mid-fold.
        if ((current.width() <= 0 || current.height() <= 0) && mutableState.value.ready) return
        val maximum = try { calculator.computeMaximumWindowMetrics(activity).bounds }
        catch (_: Exception) { current }
        val density = activity.resources.displayMetrics.density.coerceAtLeast(.1f)
        val bounds = feature?.bounds
        val validFeature = feature?.takeIf {
            bounds != null && bounds.left >= 0 && bounds.top >= 0 &&
                bounds.right <= current.width() && bounds.bottom <= current.height()
        }
        val horizontal = validFeature?.orientation == FoldingFeature.Orientation.HORIZONTAL
        val wasInner = mutableState.value.surface == DisplaySurface.INNER
        mutableState.value = mutableState.value.copy(
            window = PixelBounds(0, 0, current.width(), current.height()),
            density = density,
            maximumSmallestWidthDp = minOf(maximum.width(), maximum.height()) / density,
            displayId = activity.display?.displayId ?: -1,
            rotation = activity.display?.rotation ?: 0,
            inMultiWindow = activity.isInMultiWindowMode,
            hinge = validFeature?.bounds?.let { PixelBounds(it.left, it.top, it.right, it.bottom) },
            axis = if (horizontal) HingeAxis.HORIZONTAL else HingeAxis.VERTICAL,
            separating = validFeature?.isSeparating == true,
            featurePosture = when (validFeature?.state) {
                FoldingFeature.State.FLAT -> FoldPosture.FLAT
                FoldingFeature.State.HALF_OPENED -> FoldPosture.HALF_OPENED
                else -> FoldPosture.UNKNOWN
            },
        )
        val newSurface = mutableState.value.surface
        if (newSurface == DisplaySurface.INNER) {
            lastUnfoldedTimestamp = SystemClock.elapsedRealtime()
        } else if (wasInner && newSurface == DisplaySurface.COVER) {
            lastClosingTimestamp = SystemClock.elapsedRealtime()
            onInnerToCoverHandoff?.invoke()
        }
    }

    fun wasRecentlyClosingFromUnfolded(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val recentlyClosing = lastClosingTimestamp > 0L && (now - lastClosingTimestamp < 4000L)
        val recentlyUnfoldedAndNarrow = (now - lastUnfoldedTimestamp < 3500L) &&
                (mutableState.value.surface == DisplaySurface.COVER || (mutableState.value.hingeAngle ?: 180f) <= 60f)
        return recentlyClosing || recentlyUnfoldedAndNarrow
    }

    fun clearFoldClosing() {
        lastClosingTimestamp = 0L
    }

    override fun onStop(owner: LifecycleOwner) = stop()
    override fun onDestroy(owner: LifecycleOwner) = close()
    private fun stop() {
        if (!started) return
        started = false
        layoutJob?.cancel()
        layoutJob = null
        if (sensorRegistered) sensors?.unregisterListener(sensorListener)
        sensorRegistered = false
        displays?.unregisterDisplayListener(displayListener)
        activity.window.decorView.removeOnLayoutChangeListener(layoutListener)
    }
    override fun close() {
        stop()
        activity.lifecycle.removeObserver(this)
    }
    private companion object { const val TAG = "FoldStateTracker" }
}
