package com.anotherlauncher.ui

import android.widget.Toast
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anotherlauncher.data.*
import com.anotherlauncher.widgets.WidgetSizing

enum class DrawerBrowseMode {
    CATEGORIES,
    LIST
}

@Composable
fun AllAppsDrawer(
    apps: List<AppEntry>, loaded: Boolean, repository: AppRepository, drag: LauncherDragState,
    query: String, onQueryChange: (String) -> Unit, gridState: LazyGridState,
    overrides: Map<String, AppCategory>, adding: Boolean, pinned: Set<String>, reducedMotion: Boolean,
    addingToDock: Boolean, homeNumber: Int, onDestination: (Boolean) -> Unit, focusSearch: Boolean,
    onAdd: (AppEntry) -> Unit, onClose: () -> Unit, onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit, onSettings: () -> Unit,
    onAppBounds: (String, Rect) -> Unit, contacts: List<ContactMatch> = emptyList(),
    onContact: (ContactMatch) -> Unit = {},
    isPage: Boolean = false,
    homeRequest: Int = 0,
    libraryAz: Boolean = false,
    onLibraryAzChange: (Boolean) -> Unit = {},
    searchProvider: SearchProvider = SearchProvider.GOOGLE,
    searchRequest: Int = 0,
    onSearchRequestHandled: (Int) -> Unit = {},
    active: Boolean = true,
) {
    val context = LocalContext.current
    val playStoreIntent = remember(query) { PlayStoreSearch.intent(context, query) }
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val browseMode = if (isPage && libraryAz) DrawerBrowseMode.LIST else DrawerBrowseMode.CATEGORIES
    var openCategory by remember { mutableStateOf<LibraryCategory?>(null) }

    val category = AppCategory.entries.firstOrNull { it.name == categoryName }
    val categorized = remember(apps, overrides) { apps.groupBy { overrides[it.id] ?: it.category } }

    val recentAppIds by com.anotherlauncher.data.RecentApps.recents.collectAsState()
    val recentApps = remember(apps, recentAppIds) {
        com.anotherlauncher.data.RecentApps.computeRecents(apps, recentAppIds)
    }

    val libraryCategoryGroups: List<Pair<LibraryCategory, List<AppEntry>>> = remember(apps, overrides, recentAppIds) {
        val grouped = apps.groupBy { app ->
            overrides[app.id]?.let { LibraryCategory.fromAppCategory(it) }
                ?: LibraryCategory.of(
                    packageName = app.info.componentName.packageName,
                    platformCategory = app.info.applicationInfo.category,
                    appLabel = app.label,
                    isGame = AppEntry.isGameApp(app.info.applicationInfo)
                )
        }

        grouped.entries
            .filter { it.value.isNotEmpty() }
            .map { (cat, catApps) ->
                val sortedApps = catApps.sortedWith(
                    compareBy<AppEntry>(
                        { app ->
                            val recIndex = recentAppIds.indexOf(app.id)
                            if (recIndex >= 0) recIndex else Int.MAX_VALUE
                        },
                        { it.label.lowercase() }
                    )
                )
                cat to sortedApps
            }
            .sortedWith(
                compareBy<Pair<LibraryCategory, List<AppEntry>>>(
                    { (cat, _) -> cat == LibraryCategory.OTHER },
                    { (_, catApps) ->
                        val recentsCount = catApps.count { it.id in recentAppIds }
                        -(recentsCount * 10 + catApps.size)
                    }
                ).thenBy { (cat, _) -> cat.title }
            )
    }

    val mathResult = remember(query) { AppSearch.evaluateMath(query) }
    val settingMatches = remember(query) { AppSearch.findSettings(query) }

    val matches = remember(apps, query, category, overrides) {
        if (query.isNotBlank()) apps.mapNotNull { app ->
            AppSearch.rank(app.label, app.info.componentName.packageName, query)?.let { app to it }
        }.sortedBy { it.second }.map { it.first }
        else apps.filter { category == null || (overrides[it.id] ?: it.category) == category }
    }

    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val close = {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        onClose()
    }
    val compact = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isUnfolded = minOf(screenWidth, configuration.screenHeightDp) >= 600
    val portraitPageMaxWidth = WidgetSizing.MAX_CELL_DP.dp * HomeEditor.COLUMNS + 32.dp
    val pageMaxWidth = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
        portraitPageMaxWidth
    } else {
        680.dp
    }
    var searchFocused by remember { mutableStateOf(false) }
    val isSearchActive = (searchFocused || query.isNotBlank()) && !adding

    val launchWithDismiss: (AppEntry) -> Unit = { app ->
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        onLaunch(app)
        if (!isPage) close()
    }

    BackHandler(enabled = active && openCategory != null) {
        openCategory = null
    }

    BackHandler(enabled = active && isPage && isSearchActive) {
        onQueryChange("")
        searchFocused = false
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    LaunchedEffect(homeRequest) {
        if (homeRequest > 0) {
            onQueryChange("")
            searchFocused = false
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    LaunchedEffect(active, focusSearch, searchRequest) {
        if (active && (focusSearch || searchRequest > 0)) {
            openCategory = null
            withFrameNanos { }
            focus.requestFocus()
            withFrameNanos { }
            keyboard?.show()
            if (searchRequest > 0) onSearchRequestHandled(searchRequest)
        }
    }
    LaunchedEffect(active) {
        if (!active) {
            openCategory = null
            if (searchFocused) {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
            }
        }
    }
    fun searchWeb() {
        val intent = WebSearch.intent(query, searchProvider) ?: return
        try {
            context.startActivity(intent)
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, "No app is available to open web search.", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "Android couldn't open web search.", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(category, query, browseMode) { gridState.scrollToItem(0) }

    // A library drag hides the drawer to reveal the grid; dismiss the keyboard with it.
    LaunchedEffect(drag.session?.let { Triple(it.app?.id, it.itemId, it.favorite) }) {
        val session = drag.session
        if (session?.app != null && session.itemId == null && session.favorite == null) keyboard?.hide()
    }

    val content = @Composable {
        val topPadding = if (isPage && isUnfolded && isLandscape) 34.dp else if (isPage) 8.dp else if (compact) 10.dp else 14.dp
        val bottomPadding = if (isPage) 8.dp else if (compact) 10.dp else 14.dp
        Column(
            Modifier
                .then(
                    if (isPage) Modifier.widthIn(max = pageMaxWidth).fillMaxSize().padding(start = 16.dp, end = 16.dp, top = topPadding, bottom = bottomPadding)
                    else Modifier.fillMaxSize().padding(if (compact) 10.dp else 14.dp)
                ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
        ) {
            if (!compact && !isPage) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (adding) "Add apps" else "App library",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge
                )
                if (!adding) IconButton(onClick = onSettings, Modifier.semantics { contentDescription = "Launcher settings" }) {
                    GlyphIcon(Glyph.SETTINGS)
                }
                IconButton(onClick = close, Modifier.semantics { contentDescription = if (adding) "Close" else "Close app library" }) {
                    GlyphIcon(Glyph.CLOSE)
                }
            }

            if (adding) Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(!addingToDock, { onDestination(false) }, label = { Text("Home $homeNumber") })
                FilterChip(addingToDock, { onDestination(true) }, label = { Text("Dock") })
                Spacer(Modifier.weight(1f))
                Text(
                    "${pinned.size}${if (addingToDock) " / 6" else ""} added",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isPage) {
                val isDark = isSystemInDarkTheme()
                val drawerContentColor = if (isDark) Color(0xFFF8FAFF) else MaterialTheme.colorScheme.onSurface

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Search Capsule
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(25.dp))
                                .glass(25, emphasized = true, frosted = true)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    focus.requestFocus()
                                    keyboard?.show()
                                }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlyphIcon(Glyph.SEARCH, Modifier.size(20.dp), tint = if (isPage) drawerContentColor.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                            Spacer(Modifier.width(10.dp))
                            BasicTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focus)
                                    .testTag("library-search")
                                    .semantics { contentDescription = "Search apps and web" }
                                    .onFocusChanged { searchFocused = it.isFocused },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = if (isPage) drawerContentColor else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                keyboardOptions = KeyboardOptions(
                                    imeAction = if (adding) ImeAction.Done else ImeAction.Search,
                                    autoCorrectEnabled = false,
                                    capitalization = KeyboardCapitalization.None
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = { searchWeb() },
                                    onDone = {
                                        focusManager.clearFocus(force = true)
                                        keyboard?.hide()
                                    }
                                ),
                                decorationBox = { innerTextField ->
                                    if (query.isEmpty()) {
                                        Text(
                                            "Search apps and web",
                                            color = if (isPage) drawerContentColor.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        onQueryChange("")
                                        focus.requestFocus()
                                        keyboard?.show()
                                    },
                                    modifier = Modifier.size(24.dp).semantics { contentDescription = "Clear search" }
                                ) {
                                    GlyphIcon(Glyph.CLOSE, Modifier.size(16.dp), tint = if (isPage) drawerContentColor.copy(alpha = 0.80f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                                }
                            }
                        }

                        // Right Action: Cancel when search is active, or A-Z square toggle button when idle
                        if (!adding) {
                            if (isSearchActive) {
                                TextButton(
                                    onClick = {
                                        onQueryChange("")
                                        searchFocused = false
                                        focusManager.clearFocus(force = true)
                                        keyboard?.hide()
                                    },
                                    modifier = Modifier.glass(16, emphasized = true, frosted = true), contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = "Cancel",
                                        style = TextStyle(
                                            color = if (isPage) drawerContentColor else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        )
                                    )
                                }
                            } else {
                                val isAZActive = browseMode == DrawerBrowseMode.LIST
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .glass(16, emphasized = true, frosted = true)
                                        .clickable {
                                            onLibraryAzChange(!isAZActive)
                                        }
                                        .semantics {
                                            contentDescription = if (isAZActive) "Show category cards" else "Show alphabetical list"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(
                                                if (isAZActive) (if (isPage) drawerContentColor else MaterialTheme.colorScheme.onSurface)
                                                else (if (isPage) drawerContentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                                            )
                                            .then(
                                                if (isAZActive) Modifier
                                                else Modifier.border(
                                                    width = 1.dp,
                                                    color = if (isPage) drawerContentColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                                    shape = RoundedCornerShape(9.dp)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "A-Z",
                                            style = TextStyle(
                                                fontFamily = FontFamily.SansSerif,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 11.5.sp,
                                                letterSpacing = (-0.5).sp,
                                                color = if (isAZActive) (if (isDark) Color.Black else MaterialTheme.colorScheme.surface) else (if (isPage) drawerContentColor else MaterialTheme.colorScheme.onSurface)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Dropdown Modal for Recent Apps (visible only when search is tapped / active with empty query)
                    AnimatedVisibility(
                        visible = isSearchActive && query.isBlank() && recentApps.isNotEmpty(),
                        enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .glass(20, emphasized = true, frosted = true),
                            color = Color.Transparent
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Recent apps",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.4.sp
                                    ),
                                    color = if (isPage) drawerContentColor.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    recentApps.forEach { app ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    launchWithDismiss(app)
                                                }
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            AppIcon(app, repository, Modifier.size(46.dp))
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = app.label,
                                                style = TextStyle(
                                                    color = if (isPage) drawerContentColor else MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        query, onQueryChange, Modifier.weight(1f).focusRequester(focus), singleLine = true,
                        placeholder = { Text("Search") }, leadingIcon = { GlyphIcon(Glyph.SEARCH) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(
                                onClick = { onQueryChange("") },
                                Modifier.semantics { contentDescription = "Clear search" }
                            ) {
                                GlyphIcon(Glyph.CLOSE)
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = if (adding) ImeAction.Done else ImeAction.Search,
                            autoCorrectEnabled = false,
                            capitalization = KeyboardCapitalization.None
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus(force = true)
                                keyboard?.hide()
                                if (query.isNotBlank()) matches.firstOrNull()?.let(launchWithDismiss)
                            },
                            onDone = {
                                focusManager.clearFocus(force = true)
                                keyboard?.hide()
                            }
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    if (compact) IconButton(onClick = close, Modifier.semantics { contentDescription = "Close app library" }) {
                        GlyphIcon(Glyph.CLOSE)
                    }
                }
            }

                // Explicit web action stays reachable above the scrolling local results.
                if (query.isNotBlank() && !adding) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .glass(18, emphasized = true, frosted = true)
                            .testTag("web-search-action")
                            .clickable { searchWeb() }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlyphIcon(Glyph.SEARCH, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Search ${searchProvider.label}", style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("“${query.trim()}” · Web", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                if (query.isNotBlank() && adding) {
                    Text(
                        "${matches.size} ${if (matches.size == 1) "result" else "results"} · all apps",
                        color = if (isPage) LocalHomeColors.current.text.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (query.isBlank() && matches.isEmpty() && (browseMode == DrawerBrowseMode.LIST || adding)) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(if (!loaded) "Loading your apps…" else "No matching apps", color = if (isPage) LocalHomeColors.current.text.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (query.isBlank() && !adding && browseMode == DrawerBrowseMode.CATEGORIES) {
                    // iOS App Library 2x2 Category Cards Grid
                    val columns = when {
                        screenWidth < 600 -> 2
                        isPage && configuration.orientation == Configuration.ORIENTATION_PORTRAIT -> 3
                        screenWidth >= 600 -> 4
                        else -> 2
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(libraryCategoryGroups, key = { it.first.name }) { (cat, catApps) ->
                            CategoryCard(
                                title = cat.title,
                                apps = catApps,
                                repository = repository,
                                onLaunch = { launchWithDismiss(it) },
                                onOpen = { openCategory = cat },
                                onWallpaper = isPage
                            )
                        }
                    }
                } else {
                    // Search results share a contrast-preserving glass surface.
                    val isAZMode = (query.isBlank() && !adding && browseMode == DrawerBrowseMode.LIST) || adding
                    val aZColumns = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 6 else 4
                    val aZVerticalSpacing = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 24.dp else if (adding) 16.dp else 48.dp
                    LazyVerticalGrid(
                        columns = if (isAZMode) GridCells.Fixed(aZColumns) else GridCells.Adaptive(78.dp),
                        state = gridState,
                        modifier = Modifier.weight(1f).then(
                            if (query.isNotBlank() && !adding) Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = .45f), RoundedCornerShape(24.dp)).glass(24, emphasized = true, frosted = true)
                                .padding(horizontal = 12.dp)
                            else Modifier
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(
                            if (isAZMode) aZVerticalSpacing else 10.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (query.isNotBlank() && !adding) item(key = "search-heading", span = { GridItemSpan(maxLineSpan) }) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Apps", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.weight(1f))
                                Text("${matches.size} found", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val groups: Map<String?, List<AppEntry>> = when {
                            query.isNotBlank() -> mapOf(null to matches)
                            else -> {
                                // Single continuous alphabetical list of apps
                                mapOf(null to matches.sortedBy { it.label.lowercase() })
                            }
                        }

                        groups.forEach { (group, entries) ->
                            if (group != null && category == null && query.isBlank()) item(
                                key = "group-$group",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                Text(
                                    group,
                                    Modifier.padding(top = 10.dp, bottom = 2.dp),
                                    color = if (isPage) LocalHomeColors.current.text.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            items(entries, key = { it.id }) { app ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = if (reducedMotion) Modifier else Modifier.animateItem()
                                ) {
                                    CompositionLocalProvider(LocalContentColor provides (if (isPage) Color.White else MaterialTheme.colorScheme.onSurface)) {
                                    AppTile(
                                        app, repository, drag, { if (adding) onAdd(app) else launchWithDismiss(app) }, { onMenu(app) },
                                        Modifier.testTag("library-app-tile").then(
                                            if (query.isNotBlank() && !adding && app.id == matches.firstOrNull()?.id)
                                                Modifier.background(if (isPage) Color.White.copy(alpha = .15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), RoundedCornerShape(18.dp))
                                            else Modifier
                                        ),
                                        onWallpaper = isPage,
                                        reducedMotion = reducedMotion, membership = if (adding) app.id in pinned else null,
                                        onBounds = { onAppBounds(app.id, it) },
                                        handoffDragToRoot = true,
                                    )
                                    }
                                    if (adding) Text(
                                        if (app.id in pinned) "✓ Remove" else "+ Add",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        if (query.isNotBlank() && !adding && (mathResult != null || settingMatches.isNotEmpty())) {
                            item(key = "search-utilities", span = { GridItemSpan(maxLineSpan) }) {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (mathResult != null && !adding) {
                                        Surface(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ) {
                                            Row(
                                                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "= $mathResult",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(Modifier.weight(1f))
                                                Text(
                                                    "Calculator",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    // Settings Shortcuts Chips
                                    if (settingMatches.isNotEmpty() && !adding) {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(settingMatches) { setting ->
                                                AssistChip(
                                                    onClick = {
                                                        try {
                                                            context.startActivity(setting.intent())
                                                            close()
                                                        } catch (_: Exception) {}
                                                    },
                                                    label = { Text(setting.title) },
                                                    leadingIcon = { GlyphIcon(Glyph.SETTINGS, Modifier.size(16.dp)) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (query.isNotBlank() && !adding) {
                            item(key = "search-actions", span = { GridItemSpan(maxLineSpan) }) {
                                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .10f))
                                    if (matches.isEmpty()) Text(
                                        if (loaded) "No matching apps" else "Loading your apps…",
                                        Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (contacts.isNotEmpty()) Text("Contacts",
                                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    contacts.forEach { contact ->
                                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { focusManager.clearFocus(force = true); keyboard?.hide(); onContact(contact) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            GlyphIcon(Glyph.CONTACT, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(contact.name, Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    if (matches.isEmpty()) playStoreIntent?.let { intent ->
                                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                            runCatching {
                                                context.startActivity(intent)
                                                focusManager.clearFocus(force = true)
                                                keyboard?.hide()
                                            }
                                        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            GlyphIcon(Glyph.APPS, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Search Google Play for “$query”", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (adding) Text(

                    "Tap to add or remove · ${if (addingToDock) "Dock" else "Home $homeNumber"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(14.dp)
                )
            }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = if (isPage) Alignment.TopCenter else Alignment.TopStart) {
        if (isPage) {
            content()
        } else {
            Surface(
                Modifier
                    .fillMaxSize()
                    .glass(28, frosted = true)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 4.dp
            ) {
                content()
            }
        }

        // Category Folder Overlay Sheet
        openCategory?.let { cat ->
            val catApps = remember(cat, libraryCategoryGroups) {
                libraryCategoryGroups.firstOrNull { it.first == cat }?.second ?: emptyList()
            }
            CategoryFolderOverlay(
                category = cat,
                apps = catApps,
                repository = repository,
                drag = drag,
                onLaunch = { onLaunch(it); if (!isPage) close() },
                onMenu = onMenu,
                onDismiss = { openCategory = null }
            )
        }
    }
}
