package com.anotherlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeOptionsDock(visible: Boolean, reduced: Boolean, onDismiss: () -> Unit,
    onWidgets: () -> Unit, onWallpaper: () -> Unit, onPages: () -> Unit, onEdit: () -> Unit,
    onSettings: () -> Unit) {
    BackHandler(visible, onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        if (visible) Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
        AnimatedVisibility(visible, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            enter = slideInVertically(tween(if (reduced) 0 else 240)) { it } + fadeIn(tween(if (reduced) 0 else 160)),
            exit = slideOutVertically(tween(if (reduced) 0 else 180)) { it } + fadeOut(tween(if (reduced) 0 else 120))) {
            Surface(shape = RoundedCornerShape(18.dp), shadowElevation = 6.dp, tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 560.dp).semantics { contentDescription = "Home options" }) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DockOption("Widgets", Glyph.WIDGETS, onWidgets)
                    DockOption("Wallpaper", Glyph.WALLPAPER, onWallpaper)
                    DockOption("Pages", Glyph.PAGES, onPages)
                    DockOption("Edit", Glyph.EDIT, onEdit)
                    DockOption("Settings", Glyph.SETTINGS, onSettings)
                }
            }
        }
    }
}

@Composable
private fun DockOption(label: String, icon: Glyph, onClick: () -> Unit) {
    Column(Modifier.width(66.dp).heightIn(min = 78.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            GlyphIcon(icon, Modifier.size(22.dp), MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp, letterSpacing = 0.sp), maxLines = 1, textAlign = TextAlign.Center, softWrap = false)
    }
}
