package com.anotherlauncher.duo

/** Immutable pixels, in window coordinates. A zero-width folding line is valid. */
data class PixelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

enum class FoldPosture { UNKNOWN, CLOSED, HALF_OPENED, FLAT }
enum class HingeAxis { VERTICAL, HORIZONTAL }
enum class DisplaySurface { COVER, INNER, UNKNOWN }
enum class SurfaceEvidence { FOLDING_FEATURE, MAXIMUM_METRICS, COMPACT_METRICS }

data class FoldState(
    val window: PixelBounds = PixelBounds(0, 0, 0, 0),
    val density: Float = 1f,
    val maximumSmallestWidthDp: Float = 0f,
    val displayId: Int = -1,
    val rotation: Int = 0,
    val inMultiWindow: Boolean = false,
    val hinge: PixelBounds? = null,
    val axis: HingeAxis = HingeAxis.VERTICAL,
    val separating: Boolean = false,
    val featurePosture: FoldPosture = FoldPosture.UNKNOWN,
    val hingeAngle: Float? = null,
    val sensorAvailable: Boolean = false,
    val sampleNanos: Long = 0L,
) {
    val ready get() = window.width > 0 && window.height > 0
    val widthDp get() = window.width / density
    val heightDp get() = window.height / density
    val surface: DisplaySurface
        get() = when {
            !ready -> DisplaySurface.UNKNOWN
            hinge != null || maximumSmallestWidthDp >= 600f -> DisplaySurface.INNER
            else -> DisplaySurface.COVER
        }
    val evidence: SurfaceEvidence
        get() = when {
            hinge != null -> SurfaceEvidence.FOLDING_FEATURE
            maximumSmallestWidthDp >= 600f -> SurfaceEvidence.MAXIMUM_METRICS
            else -> SurfaceEvidence.COMPACT_METRICS
        }
    val posture: FoldPosture
        get() = when {
            hingeAngle != null && hingeAngle <= 8f -> FoldPosture.CLOSED
            featurePosture != FoldPosture.UNKNOWN -> featurePosture
            hingeAngle == null -> FoldPosture.UNKNOWN
            hingeAngle >= 172f -> FoldPosture.FLAT
            else -> FoldPosture.HALF_OPENED
        }
    val progress: Float
        get() = hingeAngle?.let(FoldMath::progress) ?: when (posture) {
            FoldPosture.CLOSED -> 0f
            FoldPosture.HALF_OPENED -> .5f
            else -> if (surface == DisplaySurface.INNER) 1f else 0f
        }

    /** Do not use Rect.isEmpty: an Android folding line can have zero thickness. */
    val hingeFraction: Float
        get() {
            val bounds = hinge ?: return .5f
            return if (axis == HingeAxis.VERTICAL) {
                ((bounds.left + bounds.right) * .5f / window.width.coerceAtLeast(1)).coerceIn(0f, 1f)
            } else {
                ((bounds.top + bounds.bottom) * .5f / window.height.coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        }
}

object FoldMath {
    fun validAngle(value: Float): Float? = value.takeIf { it.isFinite() && it in 0f..180f }
    fun progress(angle: Float): Float = ((angle - 8f) / 164f).coerceIn(0f, 1f)
    fun smoothstep(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
}
