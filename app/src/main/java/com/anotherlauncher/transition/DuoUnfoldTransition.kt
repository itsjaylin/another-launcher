package com.anotherlauncher.transition

import android.animation.ValueAnimator
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import com.anotherlauncher.duo.FoldState
import com.anotherlauncher.duo.HingeAxis
import kotlin.math.exp

data class DuoFrame(
    val strength: Float = 0f,
    val progress: Float = 1f,
    val direction: Float = 1f,
    val hingeFraction: Float = .5f,
    val axis: HingeAxis = HingeAxis.VERTICAL,
)

/**
 * Main-thread, vsync-driven live render controller. Own in a ViewModel, pause on STOP,
 * and feed FoldState while STARTED. Retains logical continuity through Activity recreation.
 * No framebuffer capture, bitmap snapshot, MediaProjection, or window overlay permission.
 */
@MainThread
class DuoUnfoldTransition : Choreographer.FrameCallback, AutoCloseable {
    private val dynamics = TransitionDynamics()
    private val choreographer = Choreographer.getInstance()
    private var scheduled = false
    private var lastFrame = 0L
    private var fold = FoldState()
    private var interacting = false
    private var reducedMotion = false
    var frame by mutableStateOf(DuoFrame())
        private set

    fun accept(state: FoldState) {
        fold = state
        updateBlocking()
        dynamics.accept(state, SystemClock.uptimeMillis())
        if (!interacting && !reducedMotion && ValueAnimator.areAnimatorsEnabled()) schedule()
    }
    fun setInteracting(value: Boolean) { interacting = value; updateBlocking() }
    fun setReducedMotion(value: Boolean) { reducedMotion = value; updateBlocking() }
    private fun updateBlocking() {
        val blocked = interacting || reducedMotion || !ValueAnimator.areAnimatorsEnabled()
        dynamics.block(blocked)
        if (blocked) pause()
    }
    private fun schedule() {
        if (scheduled) return
        scheduled = true
        choreographer.postFrameCallback(this)
    }
    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (!ValueAnimator.areAnimatorsEnabled()) { pause(); return }
        val dt = if (lastFrame == 0L) 1f / 60f else (frameTimeNanos - lastFrame) / 1_000_000_000f
        lastFrame = frameTimeNanos
        val active = dynamics.step(dt, SystemClock.uptimeMillis())
        frame = DuoFrame(dynamics.strength, dynamics.progress, dynamics.direction, fold.hingeFraction, fold.axis)
        if (active) schedule() else lastFrame = 0L
    }
    fun pause() {
        choreographer.removeFrameCallback(this)
        scheduled = false
        lastFrame = 0L
        dynamics.resetVisuals()
        frame = DuoFrame()
    }
    override fun close() = pause()
}

/** Per-layer instance: RuntimeShader uniforms must never be shared across windows. */
class DuoRenderLayer {
    private val shader by lazy { if (Build.VERSION.SDK_INT >= 33) FlowShader() else null }

    fun effect(frame: DuoFrame, width: Float, height: Float, density: Float): RenderEffect? {
        val amount = frame.strength
        if (amount < .002f || width < 1f || height < 1f) return null
        // Blur stays a whisper; the fold reads from the geometric morph underneath.
        val radius = (amount * 10f * density).coerceIn(.1f, 28f)
        val vertical = frame.axis == HingeAxis.VERTICAL
        val blur = RenderEffect.createBlurEffect(
            if (vertical) radius else radius * .45f,
            if (vertical) radius * .45f else radius,
            Shader.TileMode.CLAMP,
        )
        if (Build.VERSION.SDK_INT >= 33) {
            val flow = shader?.effect(frame, width, height)
            if (flow != null) return RenderEffect.createChainEffect(flow, blur)
        }
        return blur
    }
}

/** The effect changes pixels only; layout, hit testing, drag coordinates and widget IDs stay real. */
@Composable
fun Modifier.duoUnfoldLayer(transition: DuoUnfoldTransition): Modifier {
    val renderer = remember { DuoRenderLayer() }
    return graphicsLayer {
        renderEffect = renderer.effect(transition.frame, size.width, size.height, density)
            ?.asComposeRenderEffect()
    }.drawWithContent {
        if (Build.VERSION.SDK_INT < 33 && transition.frame.strength > 0f) {
            val frame = transition.frame
            val contentScope = this
            val vertical = frame.axis == HingeAxis.VERTICAL
            val zoom = exp(.18f * frame.strength)
            // Canvas matrix only: unlike a View transform, this cannot move touch targets.
            withTransform({
                scale(if (vertical) zoom else 1f, if (vertical) 1f else zoom,
                    Offset(if (vertical) size.width * frame.hingeFraction else size.width / 2,
                        if (vertical) size.height / 2 else size.height * frame.hingeFraction))
            }) { contentScope.drawContent() }
        } else drawContent()
    }
}

@RequiresApi(33)
private class FlowShader {
    private val shader = RuntimeShader(SOURCE)
    fun effect(frame: DuoFrame, width: Float, height: Float): RenderEffect {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("axis", if (frame.axis == HingeAxis.VERTICAL) 1f else 0f,
            if (frame.axis == HingeAxis.HORIZONTAL) 1f else 0f)
        shader.setFloatUniform("hinge", frame.hingeFraction)
        shader.setFloatUniform("amount", frame.strength)
        shader.setFloatUniform("progress", frame.progress)
        shader.setFloatUniform("direction", frame.direction)
        return RenderEffect.createRuntimeShaderEffect(shader, "content")
    }

    companion object {
        // Original AGSL implementation. Five taps; Gaussian blur is handled by RenderEffect.
        const val SOURCE = """
            uniform shader content;
            uniform float2 size;
            uniform float2 axis;
            uniform float hinge;
            uniform float amount;
            uniform float progress;
            uniform float direction;
            half4 sampleAt(float2 p) {
                return content.eval(clamp(p, float2(0.5), size - float2(0.5)));
            }
            half4 main(float2 p) {
                float2 uv = p / size;
                float u = dot(uv, axis);
                float distance = abs(u - hinge);
                float band = mix(0.08, 0.65, progress);
                float seam = 1.0 - smoothstep(0.0, band, distance);
                float zoomed = hinge + (u - hinge) * exp(-0.18 * amount);
                float flow = direction * 0.05 * amount * (0.35 + seam);
                float2 q = p + axis * size * (zoomed - u - flow);
                float2 spread = axis * size * (0.012 * amount * seam);
                half4 diffuse = sampleAt(q) * 0.40
                    + (sampleAt(q + spread) + sampleAt(q - spread)) * 0.20
                    + (sampleAt(q + spread * 2.0) + sampleAt(q - spread * 2.0)) * 0.10;
                half4 color = mix(sampleAt(p), diffuse, half(amount * (0.35 + 0.65 * seam)));
                // Premultiplied-alpha-safe haze: transparent input never becomes an opaque veil.
                color.rgb = mix(color.rgb, half3(0.79, 0.85, 0.96) * color.a,
                    half(amount * seam * 0.09));
                return color;
            }
        """
    }
}
