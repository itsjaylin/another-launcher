package com.anotherlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anotherlauncher.data.AppEntry
import com.anotherlauncher.data.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DragSession(val app: AppEntry?, val origin: Offset, val position: Offset,
    val favorite: Boolean?, val itemId: String? = null, val label: String = app?.label ?: "Item", val sourceBounds: Rect = Rect.Zero,
    val trackedByRoot: Boolean = false) {
    val previewTopLeft: Offset get() = position - (origin - sourceBounds.topLeft)
}

class LauncherDragState {
    var session by mutableStateOf<DragSession?>(null)
        private set
    var onDrop: (DragSession) -> Unit = {}
    var onHold: (DragSession) -> Unit = {}
    var holdSlop = 20f
    var onPickup: () -> Unit = {}
    fun begin(app: AppEntry?, position: Offset, favorite: Boolean?, itemId: String? = null, label: String = app?.label ?: "Item", bounds: Rect = Rect.Zero,
        trackedByRoot: Boolean = false) {
        session = DragSession(app, position, position, favorite, itemId, label, bounds, trackedByRoot)
        onPickup()
    }
    fun move(delta: Offset) { session = session?.let { it.copy(position = it.position + delta) } }
    /** Root coordinates remain stable when the source grid scrolls or leaves composition. */
    fun moveFromRoot(position: Offset) {
        session = session?.takeIf { it.trackedByRoot }?.copy(position = position) ?: session
    }
    fun finishFromSource() { if (session?.trackedByRoot != true) finish() }
    fun cancelFromSource() { if (session?.trackedByRoot != true) cancel() }
    fun updateSourceBounds(bounds: Rect) {
        if (bounds.width > 0 && bounds.height > 0) session = session?.copy(sourceBounds = bounds)
    }
    fun cancel() { session = null }
    fun finish() {
        val completed = session ?: return
        // Consume before invoking effects: a callback may start another drag or throw.
        session = null
        if ((completed.position - completed.origin).getDistance() < holdSlop) onHold(completed) else onDrop(completed)
    }
}

@Composable
fun AppIcon(
    app: AppEntry,
    repository: AppRepository,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp? = null,
) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, app.info) {
        value = withContext(Dispatchers.IO) { repository.icon(app) }
    }
    val targetSize = size ?: if (modifier == Modifier) 62.dp else null
    val boxModifier = if (targetSize != null) modifier.size(targetSize) else modifier
    Box(boxModifier, contentAlignment = Alignment.Center) {
        val icon = bitmap
        if (icon != null) Image(icon.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), filterQuality = FilterQuality.High)
        else Box(Modifier.fillMaxSize(0.9f).background(MaterialTheme.colorScheme.primary.copy(alpha = .18f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Text(app.label.take(1), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun AppTile(
    app: AppEntry,
    repository: AppRepository,
    drag: LauncherDragState,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    favorite: Boolean? = null,
    itemId: String? = null,
    onWallpaper: Boolean = false,
    reducedMotion: Boolean = false,
    membership: Boolean? = null,
    onBounds: (Rect) -> Unit = {},
    hasDot: Boolean = LocalNotificationDots.current.contains(app.info.componentName.packageName),
    iconSize: androidx.compose.ui.unit.Dp? = null,
    handoffDragToRoot: Boolean = false,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !reducedMotion) .94f else 1f, tween(if (reducedMotion) 0 else 130), label = "icon press")
    val isDock = favorite == true
    val actualIconSize = iconSize ?: if (isDock) 48.dp else 62.dp
    val padH = if (isDock) 2.dp else 2.dp
    val padV = if (isDock) 2.dp else 2.dp
    Column(
        modifier.onGloballyPositioned { bounds = it.boundsInRoot(); onBounds(bounds) }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .semantics {
                if (!showLabel) contentDescription = app.label
                customActions = listOf(CustomAccessibilityAction("App options", { onMenu(); true }))
                if (membership != null) {
                    selected = membership
                    stateDescription = if (membership) "Added; tap to remove" else "Not added; tap to add"
                }
            }
            .clickable(interactionSource = interactions, indication = null,
                onClickLabel = if (membership == null) "Open ${app.label}" else if (membership) "Remove ${app.label}" else "Add ${app.label}", onClick = onClick)
            .pointerInput(app.id, favorite, itemId, handoffDragToRoot) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { drag.begin(app, bounds.topLeft + it, favorite, itemId, bounds = bounds, trackedByRoot = handoffDragToRoot) },
                    onDrag = { change, amount ->
                        change.consume()
                        if (drag.session?.trackedByRoot != true) drag.move(amount)
                    },
                    onDragCancel = drag::cancelFromSource,
                    onDragEnd = drag::finishFromSource,
                )
            }.padding(horizontal = padH, vertical = padV),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (showLabel) 4.dp else 0.dp, Alignment.CenterVertically),
    ) {
        val isSource = drag.session?.let { if (itemId != null) it.itemId == itemId else it.itemId == null && it.app?.id == app.id && it.favorite == favorite } == true
        Box(contentAlignment = Alignment.TopEnd) {
            AppIcon(app, repository, Modifier.alpha(if (isSource) .4f else 1f)
                .then(if (isSource) Modifier.testTag("drag-source-placeholder").semantics { contentDescription = "${app.label} drag placeholder" } else Modifier),
                size = actualIconSize)
            if (hasDot && !isSource) Box(Modifier.size(10.dp)
                .background(Color.Red, RoundedCornerShape(50))) {}
            val installProgress = com.anotherlauncher.data.LocalInstallProgress.current[app.info.componentName.packageName]
            if (installProgress != null && installProgress < 1f) {
                com.anotherlauncher.data.InstallRing(installProgress, Modifier.size(actualIconSize).clip(RoundedCornerShape(16.dp)))
            }
        }
        if (showLabel) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                com.anotherlauncher.data.NewAppDot(app.info.componentName.packageName)
                HomeLabel(app.label, onWallpaper = onWallpaper)
            }
        }
    }
}

@Composable
fun HomeLabel(text: String, modifier: Modifier = Modifier, onWallpaper: Boolean = true) {
    val ink = if (onWallpaper) Color(0xFFF8FAFF) else LocalContentColor.current
    Text(text, modifier, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = ink,
        style = TextStyle(shadow = if (onWallpaper) Shadow(
            Color.Black.copy(alpha = .8f),
            Offset(0f, 1f), 4f,
        ) else null))
}

@Composable
fun RemoveAppButton(label: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onRemove, modifier = modifier.size(28.dp).semantics { contentDescription = "Remove $label" }) {
        Box(Modifier.size(22.dp).background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            Text("−", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 18.sp)
        }
    }
}
