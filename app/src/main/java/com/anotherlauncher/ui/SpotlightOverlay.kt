package com.anotherlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.anotherlauncher.data.AppEntry
import com.anotherlauncher.data.AppCategory
import com.anotherlauncher.data.AppSearch

@Composable
fun SpotlightOverlay(
    apps: List<AppEntry>,
    repository: com.anotherlauncher.data.AppRepository,
    query: String,
    onQueryChange: (String)->Unit,
    onLaunch: (AppEntry)->Unit,
    onClose: ()->Unit,
) {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val results = remember(query, apps) {
        if (query.isBlank()) emptyList() else apps.filter { app ->
            AppSearch.rank(app.label, app.info.componentName.packageName, query) != null
        }.sortedBy { AppSearch.rank(it.label, it.info.componentName.packageName, query) }.take(8)
    }
    Surface(Modifier.fillMaxSize().testTag("spotlight").clickable { keyboard?.hide(); onClose() }.padding(12.dp),
        shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, onQueryChange, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Search apps...") }, leadingIcon = { GlyphIcon(Glyph.SEARCH) })
            if (results.isNotEmpty()) {
                LazyVerticalGrid(columns = GridCells.Adaptive(78.dp), modifier = Modifier.weight(1f)) {
                    items(results, key = { it.id }) { app ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TextButton(onClick = { onLaunch(app) }) { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}
