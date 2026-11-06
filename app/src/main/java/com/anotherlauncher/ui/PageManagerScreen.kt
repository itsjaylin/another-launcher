package com.anotherlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anotherlauncher.LauncherViewModel
import com.anotherlauncher.data.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PageManagerScreen(layout: HomeLayout, selectedPage: String, apps: Map<String, AppEntry>, model: LauncherViewModel,
    reduced: Boolean, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val notice by model.pageNotice.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val list = rememberLazyListState()
    BackHandler(onBack = onBack)
    LaunchedEffect(notice?.token) {
        val value = notice ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(value.message, if (value.canUndo) "Undo" else null, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) model.undoPages() else model.dismissPageNotice(value.token)
    }
    LaunchedEffect(selectedPage) {
        val index = layout.pages.indexOfFirst { it.id == selectedPage }.coerceAtLeast(0)
        if (reduced) list.scrollToItem(index) else list.animateScrollToItem(index)
    }
    FullPageSurface("Home pages", onBack) {
        Text("Removing a page moves its items to empty spaces on other pages. You can undo this change.",
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { model.cleanEmptyPages() }, enabled = layout.pages.size > 1 && layout.pages.any { it.items.isEmpty() },
            modifier = Modifier.padding(horizontal = 12.dp)) { Text("Remove empty pages") }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyRow(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                itemsIndexed(layout.pages, key = { _, page -> page.id }) { index, page ->
                    val selected = page.id == selectedPage
                    val isDefault = page.id == layout.resolvedDefaultPageId()
                    Surface(Modifier.width(208.dp).then(if (reduced) Modifier else Modifier.animateItem()), shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Home ${index + 1}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                IconButton(onClick = { model.setDefaultPage(page.id) },
                                    modifier = Modifier.semantics {
                                        contentDescription = if (isDefault) "Home ${index + 1} is default home page" else "Set Home ${index + 1} as default home page"
                                    }) {
                                    GlyphIcon(Glyph.HOME, tint = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
                                }
                                IconButton(onClick = { model.removePage(page.id) }, enabled = layout.pages.size > 1,
                                    modifier = Modifier.semantics { contentDescription = "Remove Home ${index + 1}" }) {
                                    GlyphIcon(Glyph.REMOVE)
                                }
                            }
                            PageMiniature(page, apps, model.apps, Modifier.fillMaxWidth().aspectRatio(4f / 6f)
                                .clip(RoundedCornerShape(16.dp)).clickable { onOpen(page.id) })
                            val statusText = buildList {
                                add("${page.items.size} items")
                                if (isDefault) add("Default")
                                if (selected) add("Current")
                            }.joinToString(" · ")
                            Text(statusText, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                IconButton(onClick = { model.movePage(page.id, -1) }, enabled = index > 0,
                                    modifier = Modifier.semantics { contentDescription = "Move Home ${index + 1} earlier" }) { Text("‹") }
                                TextButton(onClick = { onOpen(page.id) }) { Text("Open") }
                                IconButton(onClick = { model.movePage(page.id, 1) }, enabled = index < layout.pages.lastIndex,
                                    modifier = Modifier.semantics { contentDescription = "Move Home ${index + 1} later" }) { Text("›") }
                            }
                        }
                    }
                }
                item("add-page") {
                    OutlinedCard(onClick = { model.addPage() }, modifier = Modifier.width(176.dp).height(260.dp).testTag("add-home-page")) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            GlyphIcon(Glyph.PLUS, Modifier.size(32.dp))
                            Text("Add page", Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp))
        }
    }
}

/** Layout-only previews: no second widget host, framebuffer capture or duplicated binding. */
@Composable
private fun PageMiniature(page: HomePage, apps: Map<String, AppEntry>, repository: AppRepository, modifier: Modifier) {
    BoxWithConstraints(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        val width = maxWidth / HomeEditor.COLUMNS
        val height = maxHeight / HomeEditor.ROWS
        page.items.forEach { item -> key(item.id) {
            Box(Modifier.absoluteOffset(width * item.column + 2.dp, height * item.row + 2.dp)
                .size(width * item.spanX - 4.dp, height * item.spanY - 4.dp), contentAlignment = Alignment.Center) {
                val app = item.appId?.let(apps::get)
                if (app != null) AppIcon(app, repository, Modifier.fillMaxSize().padding(3.dp))
                else Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        GlyphIcon(if (item.kind == HomeItemKind.STACK) Glyph.STACK else if (item.kind == HomeItemKind.FOLDER) Glyph.APPS else Glyph.WIDGETS,
                            Modifier.size(18.dp), MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        } }
    }
}
