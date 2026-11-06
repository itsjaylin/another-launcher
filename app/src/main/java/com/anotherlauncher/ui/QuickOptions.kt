package com.anotherlauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

data class QuickAction(val label: String, val icon: Glyph? = null, val destructive: Boolean = false, val action: () -> Unit)

class AttachedPopupPosition(private val anchor: Rect, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchor.center.x.toInt() - popupContentSize.width / 2).coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val above = anchor.top.toInt() - popupContentSize.height - margin
        val y = (if (above >= margin) above else anchor.bottom.toInt() + margin)
            .coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin))
        return IntOffset(x, y)
    }
}

@Composable
fun ItemQuickPopup(anchor: Rect, title: String, actions: List<QuickAction>, onDismiss: () -> Unit, onSettings: (() -> Unit)? = null,
    hint: String? = null, reducedMotion: Boolean = false, headerIcon: (@Composable () -> Unit)? = null, focusable: Boolean = true) {
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val reveal by animateFloatAsState(if (shown) 1f else 0f, tween(if (reducedMotion) 0 else 150), label = "quick menu reveal")
    Popup(popupPositionProvider = remember(anchor, gap) { AttachedPopupPosition(anchor, gap) }, onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = focusable)) {
        Surface(Modifier.widthIn(min = 224.dp, max = 280.dp).heightIn(max = 420.dp).testTag("item-quick-popup").semantics { testTagsAsResourceId = true }
            .graphicsLayer { alpha = reveal; scaleX = .96f + .04f * reveal; scaleY = .96f + .04f * reveal }, shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 6.dp, tonalElevation = 3.dp) {
            Column(Modifier.width(IntrinsicSize.Max).verticalScroll(rememberScrollState()).padding(8.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 8.dp, end = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (headerIcon != null) Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { headerIcon() }
                    Text(title, Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    if (onSettings != null) IconButton(onClick = onSettings, Modifier.semantics { contentDescription = "$title settings" }) {
                        GlyphIcon(Glyph.SETTINGS, Modifier.size(19.dp))
                    }
                }
                hint?.let { Text(it, Modifier.padding(horizontal = 8.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                actions.forEachIndexed { index, action ->
                    if (action.destructive && index > 0 && !actions[index - 1].destructive) HorizontalDivider(Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                    val tint = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = action.action)
                        .padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        action.icon?.let { GlyphIcon(it, Modifier.size(22.dp), tint) }
                        Text(action.label, style = MaterialTheme.typography.bodyMedium, color = tint)
                    }
                }
            }
        }
    }
}
