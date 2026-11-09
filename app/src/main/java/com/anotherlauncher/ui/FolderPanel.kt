package com.anotherlauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.anotherlauncher.LauncherViewModel
import com.anotherlauncher.data.AppEntry
import com.anotherlauncher.data.HomeItem
import kotlinx.coroutines.delay

@Composable
fun FolderPanel(folder: HomeItem, apps: List<AppEntry>, model: LauncherViewModel, reduced: Boolean, onDismiss: () -> Unit) {
    var opened by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    var managing by rememberSaveable(folder.id) { mutableStateOf(folder.contents.isEmpty()) }
    var title by rememberSaveable(folder.id) { mutableStateOf(folder.title) }
    var query by rememberSaveable(folder.id) { mutableStateOf("") }
    LaunchedEffect(Unit) { opened = true; entered = true }
    LaunchedEffect(opened) { if (entered && !opened) { delay(if (reduced) 0 else 170); onDismiss() } }
    val progress by animateFloatAsState(if (opened) 1f else 0f, tween(if (reduced) 0 else 170), label = "folder opening")
    val visibleApps = remember(apps, folder.contents, managing, query) {
        if (managing) apps.filter { it.label.contains(query.trim(), true) }
        else folder.contents.mapNotNull { id -> apps.firstOrNull { it.id == id } }
    }
    Dialog(onDismissRequest = { opened = false }) {
        val isDark = isSystemInDarkTheme()
        val contentColor = if (isDark) Color(0xFFF8FAFF) else MaterialTheme.colorScheme.onSurface
        Surface(
            Modifier.fillMaxWidth().heightIn(max = 600.dp).graphicsLayer {
                alpha = progress; scaleX = .92f + .08f * progress; scaleY = .92f + .08f * progress
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            contentColor = contentColor
        ) {
            CompositionLocalProvider(
                LocalContentColor provides contentColor,
                LocalHomeColors provides HomeColors(darkText = !isDark)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (managing) OutlinedTextField(title, { title = it.take(40) }, label = { Text("Folder name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    else Text(folder.title, style = MaterialTheme.typography.headlineSmall, color = contentColor)
                    if (managing) OutlinedTextField(query, { query = it }, placeholder = { Text("Find apps for this folder") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (visibleApps.isEmpty()) Text("Add apps to make this folder yours.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyVerticalGrid(GridCells.Adaptive(76.dp), modifier = Modifier.weight(1f, fill = false).heightIn(min = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(visibleApps, key = { it.id }) { app ->
                            Column(Modifier.clickable {
                                if (managing) model.toggleFolderApp(folder.id, app.id) else model.launch(app)
                            }.padding(vertical = 6.dp).semantics { contentDescription = "${app.label}${if (managing && app.id in folder.contents) ", selected" else ""}" },
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                AppIcon(app, model.apps)
                                HomeLabel(app.label, onWallpaper = false)
                                if (managing) Checkbox(app.id in folder.contents, onCheckedChange = { model.toggleFolderApp(folder.id, app.id) })
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = {
                            if (managing) model.renameFolder(folder.id, title)
                            managing = !managing
                        }) { Text(if (managing) "Save folder" else "Edit folder") }
                        TextButton(onClick = { if (managing) model.renameFolder(folder.id, title); opened = false }) { Text("Done") }
                    }
                }
            }
        }
    }
}
