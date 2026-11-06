package com.anotherlauncher.transition

import com.anotherlauncher.duo.DisplaySurface
import com.anotherlauncher.duo.FoldState
import com.anotherlauncher.duo.FoldPosture
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/** Pure, clock-injected state machine, independent of frame rate and Android sensors. */
class TransitionDynamics {
    var strength: Float = 0f
        private set
    var progress: Float = 1f
        private set
    var direction: Float = 1f
        private set
    private var target = 0f
    private var targetProgress = 1f
    private var previous: FoldState? = null
    private var lastInputMillis = 0L
    private var motionDeadline = 0L
    private var blocked = false

    fun accept(state: FoldState, nowMillis: Long) {
        if (!state.ready) return
        val old = previous
        previous = state
        val recent = old != null && nowMillis - lastInputMillis in 0..10_000
        lastInputMillis = nowMillis
        targetProgress = state.progress
        if (!recent || blocked) { progress = targetProgress; return }
        // Rotation / split-screen resize is not a physical display handoff.
        val handoff = old.surface != state.surface && !old.inMultiWindow && !state.inMultiWindow &&
            old.rotation == state.rotation && old.surface != DisplaySurface.UNKNOWN
        val delta = if (old.hingeAngle != null && state.hingeAngle != null) {
            state.hingeAngle - old.hingeAngle
        } else 0f
        val postureChange = old.featurePosture != FoldPosture.UNKNOWN &&
            state.featurePosture != FoldPosture.UNKNOWN &&
            old.featurePosture != state.featurePosture && state.hinge != null
        if (abs(delta) >= .15f || handoff || postureChange) {
            direction = if (delta < 0f || (handoff && state.surface == DisplaySurface.COVER)) -1f else 1f
            target = envelope(targetProgress)
            if (handoff || (postureChange && state.hingeAngle == null)) {
                // A brief live-content bridge when the OEM only reports endpoints.
                strength = maxOf(strength, .72f)
            }
            motionDeadline = nowMillis + 160L
        }
    }

    fun block(value: Boolean) {
        blocked = value
        if (value) resetVisuals()
    }
    fun resetVisuals() { strength = 0f; target = 0f; motionDeadline = 0L }

    /** Returns true only while another frame is needed. Half-open rest always settles. */
    fun step(dtSeconds: Float, nowMillis: Long): Boolean {
        if (blocked) return false
        if (nowMillis >= motionDeadline) target = 0f
        progress += (targetProgress - progress) * (1f - exp(-dtSeconds.coerceIn(0f, .1f) / .045f))
        val tau = if (target > strength) .045f else .10f
        strength += (target - strength) * (1f - exp(-dtSeconds.coerceIn(0f, .1f) / tau))
        if (abs(strength - target) < .002f) strength = target
        return strength != 0f || target != 0f
    }

    companion object {
        fun envelope(progress: Float): Float =
            sin(PI.toFloat() * progress.coerceIn(0f, 1f)).coerceAtLeast(0f)
    }
}
