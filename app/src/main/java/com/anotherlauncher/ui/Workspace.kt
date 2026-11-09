package com.anotherlauncher.ui

import android.util.SizeF
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import com.anotherlauncher.data.*
import com.anotherlauncher.duo.FoldState
import com.anotherlauncher.duo.DisplaySurface
import com.anotherlauncher.duo.HingeAxis
import com.anotherlauncher.widgets.WidgetController
import com.anotherlauncher.widgets.WidgetSizing
import com.anotherlauncher.widgets.FittingWidgetFrame
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.abs

data class CellTarget(val pageId: String, val column: Int, val row: Int, val bounds: Rect, val fullBounds: Rect = bounds)

class HomeDropTargets {
    private val cells = mutableMapOf<String, CellTarget>()
    private val items = mutableMapOf<String, Pair<String, Rect>>()
    fun updateItem(pageId: String, id: String, bounds: Rect) { items[id] = pageId to bounds }
    fun itemBounds(id: String): Rect = items[id]?.second ?: Rect.Zero
    fun update(target: CellTarget) { cells["${target.pageId}:${target.column}:${target.row}"] = target }
    fun clear(pageId: String) { cells.entries.removeAll { it.value.pageId == pageId }; items.entries.removeAll { it.value.first == pageId } }
    fun at(position: androidx.compose.ui.geometry.Offset): CellTarget? = cells.values.firstOrNull { it.bounds.contains(position) }
    fun cellTarget(pageId: String, column: Int, row: Int): CellTarget? =
        cells["$pageId:$column:$row"]

    fun itemRegionBounds(pageId: String, column: Int, row: Int, spanX: Int, spanY: Int, insetPx: Float = 0f): Rect? {
        val start = cells["$pageId:$column:$row"]?.fullBounds ?: return null
        val endCol = (column + spanX - 1).coerceIn(0, HomeEditor.COLUMNS - 1)
        val endRow = (row + spanY - 1).coerceIn(0, HomeEditor.ROWS - 1)
        val end = cells["$pageId:$endCol:$endRow"]?.fullBounds ?: return null
        return Rect(
            left = start.left + insetPx,
            top = start.top + insetPx,
            right = end.right - insetPx,
            bottom = end.bottom - insetPx,
        )
    }
    fun forDrop(session: DragSession, item: HomeItem?, inset: Float): CellTarget? {
        if (item == null || session.sourceBounds.width <= 0f) return at(session.position)
        val origin = session.previewTopLeft
        val pageId = at(session.position)?.pageId ?: at(origin + Offset(inset, inset))?.pageId ?: return null
        return cells.values.filter { it.pageId == pageId && it.bounds.width > 0 && it.bounds.height > 0 &&
            it.column + item.spanX <= HomeEditor.COLUMNS && it.row + item.spanY <= HomeEditor.ROWS }
            .minByOrNull { (it.fullBounds.topLeft + Offset(inset, inset) - origin).getDistanceSquared() }
    }
}

@Composable
fun Workspace(
    layout: HomeLayout, selectedPage: String, fold: FoldState, wide: Boolean, timeMillis: Long,
    apps: Map<String, AppEntry>, repository: AppRepository, drag: LauncherDragState,
    targets: HomeDropTargets, widgets: WidgetController, editing: Boolean, reducedMotion: Boolean,
    onSelectPage: (String) -> Unit, onHomeMenu: (String) -> Unit, onItemMenu: (HomeItem) -> Unit,
    onLaunch: (AppEntry) -> Unit, onFolder: (String) -> Unit, onAddPage: () -> Unit,
    onRemoveItem: (HomeItem) -> Unit,
    showLabels: Boolean, weather: WeatherState, onWeatherSettings: () -> Unit,
    alwaysShowPageDots: Boolean = false,
    allApps: List<AppEntry> = emptyList(),
    appsLoaded: Boolean = true,
    categoryOverrides: Map<String, AppCategory> = emptyMap(),
    onAppMenu: ((AppEntry) -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onAppBounds: ((String, Rect) -> Unit)? = null,
    contactMatches: List<ContactMatch> = emptyList(),
    onContact: ((ContactMatch) -> Unit)? = null,
    onHubAddWidgets: () -> Unit = {},
    weatherUnit: String? = null,
    homeRequest: Int = 0,
    libraryAz: Boolean = false,
    onLibraryAzChange: (Boolean) -> Unit = {},
    innerSideLayout: Boolean = false,
    dockSide: DockSide = DockSide.RIGHT,
    searchProvider: SearchProvider = SearchProvider.GOOGLE,
    libraryQuery: String = "",
    onLibraryQueryChange: (String) -> Unit = {},
    librarySearchRequest: Int = 0,
    onLibrarySearchHandled: (Int) -> Unit = {},
    onAddPromptWidget: ((HomeItem) -> Unit)? = null,
) {
    val count = if (wide) 2 else 1
    val sidePortrait = innerSideLayout && fold.surface == DisplaySurface.INNER && fold.window.height > fold.window.width
    val coverGrid = fold.surface == DisplaySurface.COVER
    val pageIds = remember(layout.pages) { layout.pages.map { it.id } }
    val spreads = PagerMap.spreads(layout.pages.size, count)
    val totalPages = PagerMap.totalPages(spreads, count)
    val currentSelection by rememberUpdatedState(selectedPage)
    val currentHomeRequest by rememberUpdatedState(homeRequest)
    val currentLayout by rememberUpdatedState(layout)
    val currentSelect by rememberUpdatedState(onSelectPage)
    val initial = PagerMap.pageIndex(pageIds, selectedPage, spreads, count)
    val pager = rememberPagerState(initialPage = initial) { totalPages }
    var navigating by remember { mutableStateOf(false) }
    var userPaging by remember { mutableStateOf(false) }
    var previousCount by remember { mutableIntStateOf(count) }
    var handledHomeRequest by remember { mutableIntStateOf(homeRequest) }
    var pagerBounds by remember { mutableStateOf(Rect.Zero) }
    val libraryGridState = rememberLazyGridState()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val hinge = fold.hinge?.takeIf { wide && fold.axis == HingeAxis.VERTICAL }
    val leftWidth = hinge?.let { with(density) { (it.left - pagerBounds.left).toDp() } - 12.dp }
        ?.takeIf { it > 120.dp && it < with(density) { pagerBounds.width.toDp() } - 144.dp }
    val pageGap = if (wide) 32.dp + with(density) { (hinge?.width ?: 0).toDp() } else 0.dp
    LaunchedEffect(selectedPage, count, pageIds, homeRequest) {
        val returningHome = homeRequest != handledHomeRequest
        val targetPage = if (returningHome) layout.resolvedDefaultPageId() else selectedPage
        if (returningHome) {
            onLibraryQueryChange("")
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            // HomeScreen updates selection through the ViewModel's flow. Do not consume the
            // request while that flow still holds Hub/Library: its next emission would cancel
            // this jump and navigate back to the old page.
            if (selectedPage != targetPage) return@LaunchedEffect
            handledHomeRequest = homeRequest
        }
        val targetIndex = PagerMap.pageIndex(pageIds, targetPage, spreads, count)
        userPaging = false
        navigating = true
        try {
            if (pager.currentPage != targetIndex || pager.currentPageOffsetFraction != 0f) {
                if (returningHome || reducedMotion || previousCount != count) pager.scrollToPage(targetIndex)
                else pager.animateScrollToPage(targetIndex, animationSpec = tween(320))
            }
        } finally { previousCount = count; navigating = false }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling) {
                    if (!navigating) userPaging = true
                    focusManager.clearFocus(force = true)
                    keyboard?.hide()
                }
            }
    }
    LaunchedEffect(pager, spreads, count, pageIds) {
        snapshotFlow {
            if (pager.isScrollInProgress || navigating || !userPaging || currentHomeRequest != handledHomeRequest) null
            else pager.settledPage
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { pageIndex ->
                if (!navigating) {
                    // Only a user's swipe changes selection from pager position. Initial layout,
                    // folding and programmatic jumps must keep the requested page identity.
                    userPaging = false
                    when {
                        PagerMap.isLibrary(pageIndex, spreads, count) -> {
                            if (currentSelection != PagerMap.LIBRARY_KEY) {
                                currentSelect(PagerMap.LIBRARY_KEY)
                            }
                        }
                        wide -> {
                            if (pageIndex == 0) {
                                val firstPageId = currentLayout.pages.firstOrNull()?.id
                                if (currentSelection != PagerMap.HUB_KEY && currentSelection != firstPageId) {
                                    currentSelect(firstPageId ?: PagerMap.HUB_KEY)
                                }
                            } else {
                                val extraIndex = pageIndex - 1
                                val leftPage = currentLayout.pages.getOrNull(1 + extraIndex * 2)
                                val rightPage = currentLayout.pages.getOrNull(1 + extraIndex * 2 + 1)
                                if (currentSelection != leftPage?.id && currentSelection != rightPage?.id) {
                                    leftPage?.id?.let(currentSelect)
                                }
                            }
                        }
                        else -> {
                            when {
                                PagerMap.isHub(pageIndex, count) -> {
                                    if (currentSelection != PagerMap.HUB_KEY) {
                                        currentSelect(PagerMap.HUB_KEY)
                                    }
                                }
                                PagerMap.isHomeSpread(pageIndex, spreads, count) -> {
                                    val spreadIndex = pageIndex - 1
                                    val page = currentLayout.pages.getOrNull(spreadIndex)
                                    if (page != null && page.id != currentSelection) {
                                        currentSelect(page.id)
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }
    Column(Modifier.fillMaxSize()) {
        // On wide spreads two pages sit side by side, so each page is already narrow:
        // the breathing room has to come from padding around the whole spread instead.
        val spreadSidePadding = if (wide) 32.dp else 0.dp
        HorizontalPager(
            state = pager, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = spreadSidePadding).onGloballyPositioned { pagerBounds = it.boundsInRoot() }, beyondViewportPageCount = 1,
            userScrollEnabled = drag.session == null, key = { pageIndex ->
                PagerMap.pageKey(pageIds, pageIndex, spreads, count)
            },
        ) { pageIndex ->
            val pageMotion = if (reducedMotion) Modifier.fillMaxSize() else Modifier.fillMaxSize().graphicsLayer {
                // Read pager motion in the layer phase so widget-heavy content is not recomposed every frame.
                val distance = abs(pager.currentPage - pageIndex + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                alpha = 1f - distance * .18f
                scaleY = 1f - distance * .018f
            }

            when {
                PagerMap.isLibrary(pageIndex, spreads, count) -> {
                    AllAppsDrawer(
                        apps = allApps,
                        loaded = appsLoaded,
                        repository = repository,
                        drag = drag,
                        query = libraryQuery,
                        onQueryChange = onLibraryQueryChange,
                        gridState = libraryGridState,
                        overrides = categoryOverrides,
                        adding = false,
                        pinned = emptySet(),
                        reducedMotion = reducedMotion,
                        addingToDock = false,
                        homeNumber = 1,
                        onDestination = {},
                        focusSearch = false,
                        searchRequest = librarySearchRequest,
                        onSearchRequestHandled = onLibrarySearchHandled,
                        active = selectedPage == PagerMap.LIBRARY_KEY &&
                            !navigating && !pager.isScrollInProgress && pager.settledPage == pageIndex,
                        searchProvider = searchProvider,
                        onAdd = {},
                        onClose = { onSelectPage(layout.resolvedDefaultPageId()) },
                        onLaunch = onLaunch,
                        onMenu = { onAppMenu?.invoke(it) },
                        onSettings = { onSettings?.invoke() },
                        onAppBounds = { id, bounds -> onAppBounds?.invoke(id, bounds) },
                        contacts = contactMatches,
                        onContact = { onContact?.invoke(it) },
                        isPage = true,
                        homeRequest = homeRequest,
                        libraryAz = libraryAz,
                        onLibraryAzChange = onLibraryAzChange,
                    )
                }
                wide -> {
                    if (pageIndex == 0) {
                        Row(pageMotion, horizontalArrangement = Arrangement.spacedBy(pageGap)) {
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                HubPage(
                                    weather = weather,
                                    weatherUnit = weatherUnit,
                                    onWeatherSettings = onWeatherSettings,
                                    hubPage = layout.hubPage,
                                    widgets = widgets,
                                    editing = editing,
                                    reducedMotion = reducedMotion,
                                    drag = drag,
                                    targets = targets,
                                    onWidgetMenu = onItemMenu,
                                    onAddWidgets = onHubAddWidgets,
                                )
                            }
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                val page = layout.pages.firstOrNull()
                                if (page != null) {
                                    HomeGrid(page, timeMillis, apps, repository, drag, targets, widgets,
                                        editing, reducedMotion, { onSelectPage(page.id) }, { onHomeMenu(page.id) }, onItemMenu, onLaunch, onFolder, onRemoveItem,
                                        showLabels, weather, onWeatherSettings, weatherUnit, onAddPromptWidget,
                                        maxCellDp = if (sidePortrait) 140f else WidgetSizing.MAX_CELL_DP,
                                        edgeAlign = if (sidePortrait) dockSide else null, bottomAlign = coverGrid)
                                }
                            }
                        }
                    } else {
                        val extraIndex = pageIndex - 1
                        val leftPage = layout.pages.getOrNull(1 + extraIndex * 2)
                        val rightPage = layout.pages.getOrNull(1 + extraIndex * 2 + 1)
                        Row(pageMotion, horizontalArrangement = Arrangement.spacedBy(pageGap)) {
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                if (leftPage != null) {
                                    HomeGrid(leftPage, timeMillis, apps, repository, drag, targets, widgets,
                                        editing, reducedMotion, { onSelectPage(leftPage.id) }, { onHomeMenu(leftPage.id) }, onItemMenu, onLaunch, onFolder, onRemoveItem,
                                        showLabels, weather, onWeatherSettings, weatherUnit, onAddPromptWidget,
                                        maxCellDp = if (sidePortrait) 140f else WidgetSizing.MAX_CELL_DP,
                                        edgeAlign = if (sidePortrait) dockSide else null, bottomAlign = coverGrid)
                                }
                            }
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                if (rightPage != null) {
                                    HomeGrid(rightPage, timeMillis, apps, repository, drag, targets, widgets,
                                        editing, reducedMotion, { onSelectPage(rightPage.id) }, { onHomeMenu(rightPage.id) }, onItemMenu, onLaunch, onFolder, onRemoveItem,
                                        showLabels, weather, onWeatherSettings, weatherUnit, onAddPromptWidget,
                                        maxCellDp = if (sidePortrait) 140f else WidgetSizing.MAX_CELL_DP,
                                        edgeAlign = if (sidePortrait) dockSide else null, bottomAlign = coverGrid)
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (PagerMap.isHub(pageIndex, count)) {
                        HubPage(
                            weather = weather,
                            weatherUnit = weatherUnit,
                            onWeatherSettings = onWeatherSettings,
                            hubPage = layout.hubPage,
                            widgets = widgets,
                            editing = editing,
                            reducedMotion = reducedMotion,
                            drag = drag,
                            targets = targets,
                            onWidgetMenu = onItemMenu,
                            onAddWidgets = onHubAddWidgets,
                        )
                    } else {
                        val spread = pageIndex - 1
                        val page = layout.pages.getOrNull(spread)
                        Box(pageMotion) {
                            if (page != null) {
                                HomeGrid(page, timeMillis, apps, repository, drag, targets, widgets,
                                    editing, reducedMotion, { onSelectPage(page.id) }, { onHomeMenu(page.id) }, onItemMenu, onLaunch, onFolder, onRemoveItem,
                                    showLabels, weather, onWeatherSettings, weatherUnit, onAddPromptWidget,
                                        maxCellDp = if (sidePortrait) 140f else WidgetSizing.MAX_CELL_DP,
                                        edgeAlign = if (sidePortrait) dockSide else null, bottomAlign = coverGrid)
                            }
                        }
                    }
                }
            }
        }
        // Page indicators: Hub widget icon on the left (narrow only), dots for home spreads in center, App Library icon on right.
        if (alwaysShowPageDots || layout.pages.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (!wide) {
                    // Hub widget icon as a filled pill with a dark icon (only when narrow/folded)
                    val isHubActive = PagerMap.isHub(pager.settledPage, count)
                    TextButton(
                        onClick = { onSelectPage(PagerMap.HUB_KEY) },
                        modifier = Modifier.size(32.dp, 40.dp).semantics {
                            contentDescription = "Hub${if (isHubActive) " (Current)" else ""}"
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            // 90° drop shadow per AGENTS.md invariant
                            Box(
                                Modifier
                                    .offset(y = 1.dp)
                                    .size(width = 28.dp, height = 16.dp)
                                    .blur(1.dp, BlurredEdgeTreatment.Unbounded)
                                    .background(Color.Black.copy(alpha = 0.30f), RoundedCornerShape(50))
                            )
                            // Filled pill with high-contrast dark icon
                            Box(
                                Modifier
                                    .size(width = 28.dp, height = 16.dp)
                                    .background(
                                        Color.White.copy(alpha = if (isHubActive) 1f else 0.75f),
                                        RoundedCornerShape(50)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                GlyphIcon(
                                    Glyph.WIDGETS,
                                    Modifier.size(11.dp),
                                    tint = Color(0xFF1A1A1A).copy(alpha = if (isHubActive) 1f else 0.80f)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                }
                if (spreads <= 9) repeat(spreads) { spread ->
                    val isActive = if (wide) pager.settledPage == spread else pager.settledPage == (spread + 1)
                    val targetPageId = if (wide) {
                        if (spread == 0) layout.pages.firstOrNull()?.id ?: PagerMap.HUB_KEY
                        else layout.pages.getOrNull(1 + (spread - 1) * 2)?.id ?: ""
                    } else {
                        val index = spread * count
                        layout.pages.getOrNull(index)?.id ?: ""
                    }
                    val isDefault = if (wide) {
                        if (spread == 0) layout.pages.firstOrNull()?.id == layout.resolvedDefaultPageId()
                        else {
                            val p1 = layout.pages.getOrNull(1 + (spread - 1) * 2)?.id
                            val p2 = layout.pages.getOrNull(1 + (spread - 1) * 2 + 1)?.id
                            layout.resolvedDefaultPageId().let { it == p1 || it == p2 }
                        }
                    } else {
                        val index = spread * count
                        layout.pages.drop(index).take(count).any { it.id == layout.resolvedDefaultPageId() }
                    }
                    val desc = if (wide) {
                        if (spread == 0) "Hub and Home 1${if (isDefault) " (Default)" else ""}"
                        else {
                            val p1 = 2 + (spread - 1) * 2
                            val p2 = p1 + 1
                            if (p2 <= layout.pages.size) "Home $p1 and $p2${if (isDefault) " (Default)" else ""}"
                            else "Home $p1${if (isDefault) " (Default)" else ""}"
                        }
                    } else {
                        val index = spread * count
                        "Home ${index + 1}${if (wide && index + 1 < layout.pages.size) " and ${index + 2}" else ""}${if (isDefault) " (Default)" else ""}"
                    }
                    TextButton(onClick = { if (targetPageId.isNotEmpty()) onSelectPage(targetPageId) }, modifier = Modifier.size(20.dp, 40.dp).semantics {
                        contentDescription = desc
                    }, contentPadding = PaddingValues(0.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                Modifier
                                    .offset(y = 1.dp)
                                    .size(if (isActive) 8.dp else 6.dp)
                                    .blur(1.dp, BlurredEdgeTreatment.Unbounded)
                                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
                            )
                            Box(
                                Modifier
                                    .size(if (isActive) 8.dp else 6.dp)
                                    .background(Color.White.copy(alpha = if (isActive) 1f else .80f), RoundedCornerShape(50))
                            )
                        }
                    }
                } else Text(
                    when {
                        wide && pager.settledPage == spreads -> "Library"
                        wide -> "${pager.settledPage + 1} / $spreads"
                        pager.settledPage == 0 -> "Hub"
                        pager.settledPage == spreads + 1 -> "Library"
                        else -> "${pager.settledPage} / $spreads"
                    },
                    color = Color.White,
                    fontSize = 12.sp
                )
                Spacer(Modifier.width(6.dp))
                // App Library pill icon on the right
                val isLibraryActive = PagerMap.isLibrary(pager.settledPage, spreads, count)
                TextButton(
                    onClick = { onSelectPage(PagerMap.LIBRARY_KEY) },
                    modifier = Modifier.size(32.dp, 40.dp).semantics {
                        contentDescription = "App Library${if (isLibraryActive) " (Current)" else ""}"
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // 90° drop shadow per AGENTS.md invariant
                        Box(
                            Modifier
                                .offset(y = 1.dp)
                                .size(width = 28.dp, height = 16.dp)
                                .blur(1.dp, BlurredEdgeTreatment.Unbounded)
                                .background(Color.Black.copy(alpha = 0.30f), RoundedCornerShape(50))
                        )
                        // Filled pill with high-contrast dark icon
                        Box(
                            Modifier
                                .size(width = 28.dp, height = 16.dp)
                                .background(
                                    Color.White.copy(alpha = if (isLibraryActive) 1f else 0.75f),
                                    RoundedCornerShape(50)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(
                                Glyph.APPS,
                                Modifier.size(11.dp),
                                tint = Color(0xFF1A1A1A).copy(alpha = if (isLibraryActive) 1f else 0.80f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeGrid(
    page: HomePage, time: Long, apps: Map<String, AppEntry>, repository: AppRepository,
    drag: LauncherDragState, targets: HomeDropTargets, widgets: WidgetController, editing: Boolean,
    reducedMotion: Boolean, onFocus: () -> Unit, onMenu: () -> Unit, onItemMenu: (HomeItem) -> Unit,
    onLaunch: (AppEntry) -> Unit, onFolder: (String) -> Unit,
    onRemoveItem: (HomeItem) -> Unit,
    showLabels: Boolean, weather: WeatherState, onWeatherSettings: () -> Unit,
    weatherUnit: String? = null,
    onAddPromptWidget: ((HomeItem) -> Unit)? = null,
    maxCellDp: Float = WidgetSizing.MAX_CELL_DP,
    edgeAlign: DockSide? = null,
    bottomAlign: Boolean = false,
) {
    DisposableEffect(page.id) { onDispose { targets.clear(page.id) } }
    val scroll = rememberScrollState()
    val ink = LocalHomeColors.current.text
    var viewport by remember { mutableStateOf(Rect.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { viewport = it.boundsInRoot() }) {
        val totalWidth = maxWidth
        val totalHeight = maxHeight
        val maxCellWidth = totalWidth / HomeEditor.COLUMNS
        val maxCellHeight = if (totalHeight > 180.dp) (totalHeight - 16.dp) / HomeEditor.ROWS else maxCellWidth
        val cellSize = WidgetSizing.gridCell(maxCellWidth.value, maxCellHeight.value, maxCellDp).dp
        val cellWidth = cellSize
        val cellHeight = cellSize
        SideEffect { widgets.reportGrid(page.id, cellWidth.value, cellHeight.value) }
        // Home pages deliberately use one continuous grid across the unfolded display.
        val gridWidth = cellWidth * HomeEditor.COLUMNS
        val gridHeight = cellHeight * HomeEditor.ROWS
        val extraWidth = (totalWidth - gridWidth).coerceAtLeast(0.dp)
        val horizontalOffset = when (edgeAlign) {
            DockSide.LEFT -> minOf(16.dp, extraWidth)
            DockSide.RIGHT -> (extraWidth - 16.dp).coerceAtLeast(0.dp)
            null -> extraWidth / 2
        }
        val extraHeight = (totalHeight - gridHeight).coerceAtLeast(0.dp)
        val verticalOffset = if (bottomAlign) (extraHeight - 16.dp).coerceAtLeast(0.dp) else extraHeight / 2
        fun x(col: Int) = horizontalOffset + cellWidth * col
        fun y(row: Int) = verticalOffset + cellHeight * row
        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(maxOf(totalHeight, gridHeight))) {
                repeat(HomeEditor.ROWS) { row -> repeat(HomeEditor.COLUMNS) { col ->
                    key(page.id, col, row) {
                        var bounds by remember { mutableStateOf(Rect.Zero) }
                        Box(Modifier.absoluteOffset(x(col), y(row)).size(cellWidth, cellHeight)
                            .testTag(if (page.items.none { col in it.column until it.column + it.spanX && row in it.row until it.row + it.spanY }) "empty-home-cell" else "occupied-home-cell")
                            .onGloballyPositioned {
                                val full = Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
                                bounds = full.intersect(viewport)
                                targets.update(CellTarget(page.id, col, row, bounds, full))
                            }
                            .combinedClickable(onClick = onFocus, onLongClick = onMenu, onLongClickLabel = "Home options")
                            .padding(4.dp)
                            .then(if (editing || drag.session?.position?.let(bounds::contains) == true)
                                Modifier.border(1.dp, ink.copy(alpha = .18f), RoundedCornerShape(18.dp)) else Modifier))
                    }
                } }
                ProvideJiggle(editing = editing, reducedMotion = reducedMotion) {
                    page.items.forEach { item -> key(item.id) {
                        val x by animateDpAsState(x(item.column) + 4.dp, tween(if (reducedMotion) 0 else 220), label = "grid x")
                        val top by animateDpAsState(y(item.row) + 4.dp, tween(if (reducedMotion) 0 else 220), label = "grid y")
                        val itemHeight = cellHeight * item.spanY - 8.dp
                        Box(Modifier.absoluteOffset { IntOffset(x.roundToPx(), top.roundToPx()) }.size(cellWidth * item.spanX - 8.dp, itemHeight)
                            .testTag("home-${item.kind.name.lowercase()}-tile")
                            .then(if (editing && !reducedMotion) Modifier.jiggle(item.id) else Modifier)
                            .onGloballyPositioned { targets.updateItem(page.id, item.id,
                                Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat()))) }) {
                            when (item.kind) {
                            HomeItemKind.APP -> {
                                val app = item.appId?.let(apps::get)
                                val appIconSize = if (showLabels) (cellHeight - 26.dp).coerceIn(46.dp, 56.dp) else (cellHeight - 22.dp).coerceIn(52.dp, 62.dp)
                                if (app != null) AppTile(app, repository, drag,
                                    { onFocus(); if (editing) onItemMenu(item) else onLaunch(app) }, { onItemMenu(item) },
                                    Modifier.fillMaxSize(), favorite = false, itemId = item.id, onWallpaper = true, reducedMotion = reducedMotion, showLabel = showLabels,
                                    iconSize = appIconSize)
                                else MissingItem(item, onItemMenu)
                            }
                            HomeItemKind.FOLDER -> FolderTile(item, apps, repository, drag,
                                { onFocus(); if (editing) onItemMenu(item) else onFolder(item.id) }, { onItemMenu(item) }, showLabels)
                            HomeItemKind.CLOCK, HomeItemKind.ANALOG_CLOCK, HomeItemKind.WEATHER -> Unit
                            HomeItemKind.WIDGET -> WidgetTile(item, widgets, editing, drag, onItemMenu)
                            HomeItemKind.STACK -> StackTile(item, apps, repository, widgets, drag, time, weather, showLabels, reducedMotion,
                                onLaunch, onWeatherSettings, { onItemMenu(item) }, weatherUnit)
                            HomeItemKind.WIDGET_PROMPT -> WidgetPromptTile(
                                item = item,
                                editing = editing,
                                onAdd = { onAddPromptWidget?.invoke(item) }
                            )
                        }
                        if (editing && item.kind != HomeItemKind.WIDGET) RemoveAppButton(
                            item.appId?.let(apps::get)?.label ?: if (item.kind == HomeItemKind.FOLDER) item.title else if (item.kind == HomeItemKind.WIDGET_PROMPT) "Widget slot" else "item",
                            { onRemoveItem(item) }, Modifier.align(Alignment.TopEnd))
                    }
                } } }
            }
        }
    }
}

@Composable
fun WidgetPromptTile(
    item: HomeItem,
    editing: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onAdd,
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = modifier
            .fillMaxSize()
            .glass(24, frosted = true)
            .semantics { contentDescription = "Add widget" }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .glass(22, frosted = true),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(
                    Glyph.PLUS,
                    Modifier
                        .size(22.dp)
                        .offset(y = 1.dp)
                        .blur(1.dp, BlurredEdgeTreatment.Unbounded),
                    tint = Color.Black.copy(alpha = 0.15f)
                )
                GlyphIcon(
                    Glyph.PLUS,
                    Modifier.size(22.dp),
                    tint = Color.White
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Add widget",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.25f),
                        offset = Offset(0f, 1.dp.value),
                        blurRadius = 1.5f
                    )
                ),
                color = Color.White
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (item.spanX >= 4) "4×2 wide" else "2×2 or 4×2",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.20f),
                        offset = Offset(0f, 1.dp.value),
                        blurRadius = 1.5f
                    )
                ),
                color = Color.White.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun MissingItem(item: HomeItem, menu: (HomeItem) -> Unit) {
    Column(Modifier.fillMaxSize().combinedClickable(onClick = { menu(item) }, onLongClick = { menu(item) }),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        GlyphIcon(Glyph.APPS, tint = LocalHomeColors.current.secondary)
        HomeLabel("Unavailable")
    }
}

@Composable
internal fun WidgetTile(item: HomeItem, controller: WidgetController, editing: Boolean, drag: LauncherDragState, menu: (HomeItem) -> Unit) {
    val id = item.widgetId ?: return
    val info = controller.manager.getAppWidgetInfo(id)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var nativeFrame by remember { mutableStateOf<FittingWidgetFrame?>(null) }
    val isSource = drag.session?.itemId == item.id
    Box(Modifier.fillMaxSize().onGloballyPositioned {
        bounds = Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
    }.pointerInput(item.id) {
        detectWidgetPickup(item.id, { "Widget" }, { bounds }, { nativeFrame }, drag)
    }) {
        if (info == null) MissingItem(item, menu)
        else BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = maxWidth.value
            val height = maxHeight.value
            AndroidView(
                factory = { context -> controller.createView(context, id, info, item.spanX, item.spanY).also { nativeFrame = it } },
                modifier = Modifier.fillMaxSize().alpha(if (isSource) .55f else 1f),
                update = { view ->
                    controller.displayedSizes[id] = SizeF(width, height)
                    view.requirements = controller.requirements(info)
                    view.widget.spanX = item.spanX; view.widget.spanY = item.spanY
                    view.widget.publishAfterLayout()
                },
            )
        }
        if (editing) {
            Surface(Modifier.align(Alignment.TopEnd).size(48.dp)
                .clickable(onClick = { menu(item) })
                .semantics { contentDescription = "Widget options; hold and drag to move" },
                shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Box(contentAlignment = Alignment.Center) { GlyphIcon(Glyph.APPS) }
            }
        }
    }
}

@Composable
fun FolderTile(item: HomeItem, apps: Map<String, AppEntry>, repository: AppRepository, drag: LauncherDragState, onOpen: () -> Unit, onMenu: () -> Unit, showLabel: Boolean = false) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Column(Modifier.fillMaxSize().onGloballyPositioned { bounds = it.boundsInRoot() }
        .clickable(onClick = onOpen)
        .semantics { contentDescription = item.title; customActions = listOf(androidx.compose.ui.semantics.CustomAccessibilityAction("Folder options", { onMenu(); true })) }
        .pointerInput(item.id) {
            detectDragGesturesAfterLongPress(onDragStart = { drag.begin(null, bounds.topLeft + it, false, item.id, item.title, bounds) },
                onDrag = { change, delta -> change.consume(); drag.move(delta) }, onDragEnd = drag::finish, onDragCancel = drag::cancel)
        }.padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (showLabel) 4.dp else 0.dp, Alignment.CenterVertically)) {
        Box(Modifier.size(62.dp).glass(18)) {
            item.contents.take(4).forEachIndexed { index, id ->
                apps[id]?.let { app ->
                    Box(Modifier.absoluteOffset((index % 2 * 28 + 4).dp, (index / 2 * 28 + 4).dp).size(26.dp)) {
                        AppIcon(app, repository, Modifier.fillMaxSize(), size = 26.dp)
                    }
                }
            }
        }
        if (showLabel) HomeLabel(item.title)
    }
}

@Composable
private fun StackTile(item: HomeItem, apps: Map<String, AppEntry>, repository: AppRepository, controller: WidgetController,
    drag: LauncherDragState, time: Long, weather: WeatherState, showLabels: Boolean, reduced: Boolean,
    onLaunch: (AppEntry) -> Unit, onWeatherSettings: () -> Unit, onOptions: () -> Unit,
    weatherUnit: String? = null) {
    var index by rememberSaveable(item.id) { mutableIntStateOf(0) }
    val child = item.stack.getOrNull(index.coerceIn(0, item.stack.lastIndex)) ?: return
    val rendered = child.copy(id = item.id)
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
        key(child.id) {
            when (child.kind) {
                HomeItemKind.APP -> apps[child.appId]?.let { app ->
                    AppTile(app, repository, drag, { onLaunch(app) }, onOptions, Modifier.fillMaxSize(),
                        itemId = item.id, favorite = false, showLabel = showLabels, onWallpaper = true, reducedMotion = reduced)
                }
                HomeItemKind.WIDGET -> WidgetTile(rendered, controller, false, drag) { onOptions() }
                HomeItemKind.WEATHER, HomeItemKind.ANALOG_CLOCK, HomeItemKind.CLOCK -> Unit
                else -> Unit
            }
        }
        }
        Surface(Modifier.align(Alignment.CenterHorizontally).height(WidgetSizing.STACK_CONTROLS_DP.dp), shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface) {
            if (item.spanX == 1) Box(Modifier.widthIn(min = 40.dp).clickable { index = (index + 1) % item.stack.size }
                .semantics { contentDescription = "Next in stack, ${index + 1} of ${item.stack.size}" }, contentAlignment = Alignment.Center) {
                Text("${index + 1}/${item.stack.size}", fontSize = 10.sp)
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { index = (index - 1 + item.stack.size) % item.stack.size }, Modifier.size(32.dp).semantics { contentDescription = "Previous in stack" }) {
                    Text("‹", fontSize = 20.sp)
                }
                Text("${index.coerceIn(0, item.stack.lastIndex) + 1}/${item.stack.size}", fontSize = 10.sp,
                    modifier = Modifier.clickable(onClick = onOptions))
                IconButton(onClick = { index = (index + 1) % item.stack.size }, Modifier.size(32.dp).semantics { contentDescription = "Next in stack" }) {
                    Text("›", fontSize = 20.sp)
                }
            }
        }
    }
}
