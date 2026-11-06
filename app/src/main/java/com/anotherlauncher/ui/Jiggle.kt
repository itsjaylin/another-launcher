package com.anotherlauncher.ui

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

val LocalJiggle = compositionLocalOf<State<Float>?> { null }

@Composable
fun ProvideJiggle(editing: Boolean, reducedMotion: Boolean, content: @Composable () -> Unit) {
    val clock = if (editing && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "jiggle")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(280, easing = LinearEasing)), label = "jiggle phase")
    } else null
    androidx.compose.runtime.CompositionLocalProvider(LocalJiggle provides clock, content = content)
}

fun Modifier.jiggle(key: Any, amount: Float = 1f): Modifier = composed {
    val clock = LocalJiggle.current ?: return@composed this
    val hash = key.hashCode()
    val seed = (hash and 0xFF) / 255f
    val direction = if (hash and 0x100 == 0) 1f else -1f
    graphicsLayer {
        val angle = 2f * PI.toFloat() * (clock.value + seed)
        rotationZ = sin(angle) * 1.5f * amount * direction
        translationY = cos(angle) * .5f * density * amount
    }
}
