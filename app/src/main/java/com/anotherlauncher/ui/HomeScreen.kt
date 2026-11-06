package com.anotherlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anotherlauncher.LauncherViewModel
import com.anotherlauncher.data.*
import com.anotherlauncher.duo.DeviceStatus
import com.anotherlauncher.duo.DuoStatusPill
import com.anotherlauncher.duo.FoldState
import com.anotherlauncher.duo.NotificationDotsService
import com.anotherlauncher.widgets.WidgetController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private enum class QuickOrigin { HOME, DOCK, LIBRARY }
private data class QuickSelection(val anchor: Rect, val origin: QuickOrigin, val itemId: String? = null, val app: AppEntry? = null)

@Composable
fun HomeScreen(model: LauncherViewModel, fold: FoldState, status: DeviceStatus, widgets: WidgetController,
    homeRequest: Int, homeDarkText: Boolean, showSettingsRequest: Int = 0, onDefaultHome: () -> Unit, onSignalPermission: () -> Unit,
    onContactsPermission: () -> Unit = {}, onNotificationAccess: () -> Unit = {}, onWallpaperSettings: () -> Unit, onUninstall: (AppEntry) -> Unit, onFullPageChanged: (Boolean) -> Unit = {}) {
    val prefs by model.preferences.collectAsStateWithLifecycle()
    val apps by model.apps.apps.collectAsStateWithLifecycle()
    val loaded by model.apps.loaded.collectAsStateWithLifecycle()
    val selectedPage by model.selectedPage.collectAsStateWithLifecycle()
    val weather by model.weather.collectAsStateWithLifecycle()
    val appById = remember(apps) { apps.associateBy { it.id } }
    val favorites = prefs.favorites.mapNotNull(appById::get)
    val placement = LayoutPolicy.dock(fold, prefs.dockSide, prefs.innerSideLayout)
    val sidePortrait = prefs.innerSideLayout && fold.surface == com.anotherlauncher.duo.DisplaySurface.INNER && fold.window.height > fold.window.width
    val wide = fold.widthDp >= 700f && placement != DockPlacement.BOTTOM && !sidePortrait
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isUnfolded = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600
    val statusTopPadding = if (isUnfolded && isLandscape) 36.dp else 0.dp
    // Status module color: white by default in every mode; a custom hex color wins when set;
    // otherwise fall back to a dark translucent tint only when the background demands dark text.
    val statusTint = prefs.statusColor?.let { androidx.compose.ui.graphics.Color(it) }
        ?: androidx.compose.ui.graphics.Color.White
    val drag = remember { LauncherDragState() }
    val targets = remember { HomeDropTargets() }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var addingApps by rememberSaveable { mutableStateOf(false) }
    var addingToDock by rememberSaveable { mutableStateOf(false) }
    var focusSearch by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchSequence by rememberSaveable { mutableIntStateOf(0) }
    var librarySearchRequest by rememberSaveable { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var homeMenu by remember { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var widgetPicker by rememberSaveable { mutableStateOf(false) }
    var pageManager by remember { mutableStateOf(false) }
    var weatherPickerOpen by rememberSaveable { mutableStateOf(false) }
    var wallpaperPickerOpen by rememberSaveable { mutableStateOf(false) }
    var pagesFromSettings by remember { mutableStateOf(false) }
    var quick by remember { mutableStateOf<QuickSelection?>(null) }
    var choosingStack by remember { mutableStateOf(false) }
    var folderId by rememberSaveable { mutableStateOf<String?>(null) }
    var resizingId by remember { mutableStateOf<String?>(null) }
    var replaceTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var touching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var dockBounds by remember { mutableStateOf(Rect.Zero) }
    var removeBounds by remember { mutableStateOf(Rect.Zero) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val launchApp: (AppEntry) -> Unit = { app ->
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        if (app.info.componentName.packageName == context.packageName) {
            drawerOpen = false
            editing = false
            homeMenu = false
            widgetPicker = false
            weatherPickerOpen = false
            quick = null
            choosingStack = false
            folderId = null
            pageManager = false
            pagesFromSettings = false
            resizingId = null
            settingsOpen = true
        } else {
            model.launch(app)
        }
    }
    var hoverTarget by remember { mutableStateOf<CellTarget?>(null) }
    val dockSlots = remember { mutableMapOf<String, Rect>() }
    val librarySlots = remember { mutableMapOf<String, Rect>() }
    val drawerState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val reduced = prefs.reducedMotion || !android.animation.ValueAnimator.areAnimatorsEnabled()
    val fullPage = settingsOpen || widgetPicker || pageManager || weatherPickerOpen || wallpaperPickerOpen
    val selectedHome = prefs.home.pages.firstOrNull { it.id == selectedPage } ?: prefs.home.pages.first()
    val homeNumber = prefs.home.pages.indexOf(selectedHome) + 1
    fun safeAnchor(bounds: Rect) = if (bounds.width > 0 && bounds.height > 0) bounds else Rect(16f, 32f, 72f, 88f)
    val closeQuick = { quick = null; choosingStack = false }
    // A drag that started on a library tile (itemId null, no favorite role) reflows the grid live.
    val libraryDrag = drag.session?.let { it.app != null && it.itemId == null && it.favorite == null } == true
    val itemMenu: (HomeItem) -> Unit = { item ->
        homeMenu = false; choosingStack = false
        quick = QuickSelection(safeAnchor(targets.itemBounds(item.id)), QuickOrigin.HOME, item.id, item.appId?.let(appById::get))
    }
    val dockMenu: (AppEntry) -> Unit = { app ->
        homeMenu = false; choosingStack = false
        quick = QuickSelection(safeAnchor(dockSlots[app.id] ?: Rect.Zero), QuickOrigin.DOCK, app = app)
    }
    val openHomeMenu: (String) -> Unit = { page ->
        model.selectPage(page); closeQuick(); haptics.performHapticFeedback(HapticFeedbackType.LongPress); homeMenu = true
    }
    val openDrawer: (Boolean) -> Unit = { query = ""; addingApps = it; addingToDock = false; focusSearch = false; drawerOpen = true }
    val addDockApps = { openDrawer(true); addingToDock = true }
    SideEffect {
        onFullPageChanged(fullPage)
        drag.holdSlop = with(density) { 12.dp.toPx() }
        drag.onPickup = {
            drag.session?.itemId?.let { drag.updateSourceBounds(targets.itemBounds(it)) }
            if (drag.session?.let { it.app != null && it.itemId == null && it.favorite == null } == true) drawerOpen = false
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        drag.onHold = { session ->
            val item = session.itemId?.let(prefs.home::item)
            if (item != null) itemMenu(item) else session.app?.let { app ->
                homeMenu = false; choosingStack = false
                quick = QuickSelection(safeAnchor(session.sourceBounds), if (session.favorite == true) QuickOrigin.DOCK else QuickOrigin.LIBRARY,
                    selectedHome.items.firstOrNull { it.appId == app.id }?.id, app)
            }
        }
        drag.onDrop = { drop ->
            when {
                removeBounds.contains(drop.position) && (drop.itemId != null || drop.favorite == true) -> {
                    if (drop.favorite == true && drop.app != null) model.remove(drop.app, true)
                    else drop.itemId?.let { model.removeItem(it, widgets::delete) }
                }
                dockBounds.contains(drop.position) && drop.app != null &&
                    (drop.itemId == null || prefs.home.item(drop.itemId)?.kind == HomeItemKind.APP) -> {
                    val coordinate = if (placement == DockPlacement.BOTTOM) drop.position.x else drop.position.y
                    val index = favorites.indexOfFirst { app -> dockSlots[app.id]?.let {
                        coordinate < if (placement == DockPlacement.BOTTOM) it.center.x else it.center.y
                    } == true }.let { if (it < 0) favorites.size else it }
                    // A grid item leaves the grid only once the dock accepted the pin.
                    if (drop.itemId != null) model.moveToDock(drop.app, drop.itemId, index)
                    else model.pin(drop.app, true, index)
                }
                !drawerOpen && !dockBounds.contains(drop.position) -> targets.forDrop(drop, drop.itemId?.let(prefs.home::item), with(density) { 4.dp.toPx() })
                    ?.let { cell ->
                        val app = drop.app
                        // A dock item leaves the dock only once the grid accepted the placement.
                        if (drop.favorite == true && drop.itemId == null && app != null) model.moveToGrid(app, cell.pageId, cell.column, cell.row)
                        else model.drop(drop.app, drop.itemId, cell.pageId, cell.column, cell.row)
                    }
            }
        }
    }
    LaunchedEffect(fold.window, fold.rotation, homeRequest) { drag.cancel(); touching = false; closeQuick() }
    val edgeDirection = drag.session?.takeIf { (it.position - it.origin).getDistance() > drag.holdSlop }?.let { session ->
        val edge = with(density) { 24.dp.toPx() }
        when { session.position.x < edge -> -1; session.position.x > fold.window.width - edge -> 1; else -> 0 }
    } ?: 0
    LaunchedEffect(edgeDirection, selectedPage, drawerOpen) {
        if (edgeDirection != 0 && drag.session != null && !drawerOpen) {
            delay(650)
            val next = prefs.home.pages.indexOfFirst { it.id == selectedPage } + edgeDirection * if (wide) 2 else 1
            prefs.home.pages.getOrNull(next)?.let { model.selectPage(it.id) }
        }
    }
    LaunchedEffect(homeRequest) {
        if (homeRequest > 0) {
            query = ""
            librarySearchRequest = 0
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            drawerOpen = false; editing = false; homeMenu = false; settingsOpen = false; widgetPicker = false
            weatherPickerOpen = false
            closeQuick(); folderId = null; pageManager = false; pagesFromSettings = false; resizingId = null
            model.selectPage(prefs.home.resolvedDefaultPageId())
        }
    }
    LaunchedEffect(showSettingsRequest) {
        if (showSettingsRequest > 0) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            drawerOpen = false; editing = false; homeMenu = false; widgetPicker = false
            weatherPickerOpen = false
            closeQuick(); folderId = null; pageManager = false; pagesFromSettings = false; resizingId = null
            settingsOpen = true
        }
    }
    // While dragging over the grid, compute the same reflow plan the drop will commit.
    LaunchedEffect(prefs.home, selectedPage) {
        snapshotFlow { drag.session?.position }.distinctUntilChanged().collect { position ->
            val session = drag.session
            hoverTarget = if (session == null || position == null) null else {
                val item = session.itemId?.let(prefs.home::item)
                    ?: session.app?.let { app -> HomeItem(kind = HomeItemKind.APP, appId = app.id) }
                item?.let { targets.forDrop(session, it, with(density) { 4.dp.toPx() }) }
            }
        }
    }
    val draggedItem = drag.session?.let { session ->
        session.itemId?.let(prefs.home::item) ?: session.app?.let { app -> HomeItem(kind = HomeItemKind.APP, appId = app.id) }
    }
    val displayHome = run {
        val target = hoverTarget
        if (draggedItem == null || target == null) prefs.home
        else {
            val page = prefs.home.surface(target.pageId) ?: return@run prefs.home
            val planned = HomeEditor.reflowed(page, draggedItem.copy(column = target.column, row = target.row))
            if (planned == null) prefs.home
            else HomeEditor.replacePage(prefs.home, planned)
        }
    }
    val defaultPage = prefs.home.resolvedDefaultPageId()
    val isModalDrawerOpen = drawerOpen && addingApps
    val dismissModalDrawer = {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        drawerOpen = false
    }
    val openLibrarySearch = {
        drawerOpen = false
        addingApps = false
        editing = false
        homeMenu = false
        searchSequence += 1
        librarySearchRequest = searchSequence
        model.selectPage(PagerMap.LIBRARY_KEY)
    }
    BackHandler(isModalDrawerOpen || editing || selectedPage != defaultPage) {
        when {
            isModalDrawerOpen -> dismissModalDrawer()
            editing -> editing = false
            else -> model.selectPage(defaultPage)
        }
    }
    Box(Modifier.fillMaxSize()) {
        val dots by NotificationDotsService.active.collectAsStateWithLifecycle()
        CompositionLocalProvider(LocalHomeColors provides HomeColors(homeDarkText),
            LocalNotificationDots provides if (prefs.showDots) dots else emptySet()) {
            val homeBlurRadius by animateDpAsState(
                targetValue = if (fullPage) 28.dp else 0.dp,
                animationSpec = tween(250),
                label = "home_blur"
            )
            Box(Modifier.fillMaxSize().testTag("home-root")
                .then(if (homeBlurRadius > 0.dp) Modifier.blur(homeBlurRadius, edgeTreatment = BlurredEdgeTreatment.Rectangle) else Modifier)
                .then(if (fullPage) Modifier.clearAndSetSemantics { } else Modifier.semantics {
                testTagsAsResourceId = true
                customActions = listOf(CustomAccessibilityAction("Home options", { openHomeMenu(selectedPage); true }))
                onLongClick("Home options") { openHomeMenu(selectedPage); true }
            }).pointerInput(Unit) {
                try { awaitPointerEventScope { while (true) touching = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed } } }
                finally { touching = false }
            }.pointerInput(fullPage, selectedPage, dockBounds, viewConfiguration, isModalDrawerOpen) {
                if (fullPage || isModalDrawerOpen) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                    if (down.isConsumed || dockBounds.contains(down.position) || targets.at(down.position) != null) return@awaitEachGesture
                    var cancelled = false
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed || change.isConsumed ||
                                (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                cancelled = true
                                break
                            }
                        }
                    }
                    if (!cancelled) {
                        openHomeMenu(selectedPage)
                        do { val stillPressed = awaitPointerEvent(PointerEventPass.Final).changes.any { it.pressed } } while (stillPressed)
                    }
                }
            }) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp).then(
                        if (isModalDrawerOpen) Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = dismissModalDrawer
                        ) else Modifier
                    ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (placement == DockPlacement.BOTTOM) DuoStatusPill(status, modifier = Modifier.padding(top = if (isUnfolded && isLandscape) 26.dp else 0.dp), tint = statusTint, showCellular = prefs.showCellularIndicator, onSignalPermission = onSignalPermission)
                        if (prefs.layoutError) Text("Your saved layout couldn't be read. It has been preserved.", color = MaterialTheme.colorScheme.error)
                        if (editing && !drawerOpen) Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                                if (selectedPage != PagerMap.HUB_KEY) {
                                    TextButton(onClick = { openDrawer(true) }) { Text("Add apps") }
                                }
                                TextButton(onClick = { widgetPicker = true }) { Text(if (selectedPage == PagerMap.HUB_KEY) "Add widget" else "Widgets") }
                                if (selectedPage != PagerMap.HUB_KEY) {
                                    TextButton(onClick = { model.addFolder() }) { Text("Folder") }
                                    TextButton(onClick = { pageManager = true }) { Text("Pages") }
                                }
                                if (selectedPage != defaultPage && selectedPage != PagerMap.HUB_KEY) {
                                    TextButton(onClick = { model.setDefaultPage(selectedPage) }) { Text("Set as home") }
                                }
                                TextButton(onClick = { editing = false }) { Text("Done") }
                            }
                        }
    val pageContent: @Composable () -> Unit = {
        val contactsOn = prefs.includeContacts
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        fun canReadContacts() = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CONTACTS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        var contactsAllowed by remember { mutableStateOf(canReadContacts()) }
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) contactsAllowed = canReadContacts()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        val contactMatches by produceState(emptyList<ContactMatch>(), query, contactsOn, contactsAllowed) {
            value = emptyList()
            if (contactsOn && contactsAllowed && query.isNotBlank())
                value = withContext(Dispatchers.IO) { ContactSearch.query(context, query) }
        }
        fun openContact(contact: ContactMatch) {
            try {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, contact.uri())
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            catch (_: Exception) { model.messages.tryEmit("Couldn't open contact.") }
        }
        Box(Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxSize().then(if (isModalDrawerOpen) Modifier.clearAndSetSemantics { } else Modifier)) {
                                    Workspace(displayHome, selectedPage, fold, wide, status.timeMillis, appById, model.apps, drag, targets,
                                        widgets, editing, reduced, model::selectPage, openHomeMenu, itemMenu, launchApp,
                                        { folderId = it }, { model.addPage() }, { model.removeItem(it.id, widgets::delete) },
                                        prefs.showHomeLabels, weather, { weatherPickerOpen = true }, prefs.alwaysShowPageDots,
                                        allApps = apps,
                                        appsLoaded = loaded,
                                        categoryOverrides = prefs.categoryOverrides,
                                        onAppMenu = { app ->
                                            choosingStack = false
                                            quick = QuickSelection(safeAnchor(librarySlots[app.id] ?: Rect.Zero), QuickOrigin.LIBRARY, selectedHome.items.firstOrNull { it.appId == app.id }?.id, app)
                                        },
                                        onSettings = { settingsOpen = true },
                                        onAppBounds = { id, bounds -> librarySlots[id] = bounds },
                                        contactMatches = contactMatches,
                                        onContact = ::openContact,
                                        onHubAddWidgets = { model.selectPage(PagerMap.HUB_KEY); widgetPicker = true },
                                        weatherUnit = prefs.weatherUnit,
                                        homeRequest = homeRequest,
                                        libraryAz = prefs.libraryAz,
                                        onLibraryAzChange = model::setLibraryAz,
                                        innerSideLayout = prefs.innerSideLayout,
                                        dockSide = prefs.dockSide,
                                        searchProvider = prefs.searchProvider,
                                        libraryQuery = query,
                                        onLibraryQueryChange = { query = it },
                                        librarySearchRequest = librarySearchRequest,
                                        onLibrarySearchHandled = { request ->
                                            if (librarySearchRequest == request) librarySearchRequest = 0
                                        },
                                        onAddPromptWidget = { item -> replaceTargetId = item.id; widgetPicker = true })
                                }
                                androidx.compose.animation.AnimatedVisibility(isModalDrawerOpen || libraryDrag,
                                    enter = fadeIn(tween(if (reduced) 0 else 180)) + slideInVertically(tween(if (reduced) 0 else 260)) { it / 8 },
                                    exit = fadeOut(tween(if (reduced) 0 else 150))) {
                                    // Fade out on drag pickup so the grid is revealed; animating keeps the
                                    // drawer invisible through the post-drop exit instead of flashing back.
                                    val drawerAlpha by androidx.compose.animation.core.animateFloatAsState(
                                        if (isModalDrawerOpen) 1f else 0f, tween(if (reduced) 0 else 180), label = "drawer hide")
                                    Box(Modifier.graphicsLayer { alpha = drawerAlpha }) {
                                    AllAppsDrawer(apps, loaded, model.apps, drag, query, { query = it }, drawerState, prefs.categoryOverrides,
                                        addingApps, if (addingToDock) prefs.favorites.toSet() else selectedHome.items.mapNotNull { it.appId }.toSet(),
                                        reduced, addingToDock, homeNumber, { addingToDock = it }, focusSearch,
                                        { if (addingToDock) model.toggleFavorite(it) else model.toggleHomeApp(it) }, { drawerOpen = false }, launchApp,
                                        { app -> choosingStack = false; quick = QuickSelection(safeAnchor(librarySlots[app.id] ?: Rect.Zero), QuickOrigin.LIBRARY,
                                            selectedHome.items.firstOrNull { it.appId == app.id }?.id, app) },
                                        { settingsOpen = true }, { id, bounds -> librarySlots[id] = bounds },
                                        contactMatches, ::openContact,
                                        homeRequest = homeRequest)
                                    }
                                }
                            }
                        }
                        val rail: @Composable () -> Unit = {
                            Column(Modifier.width(66.dp).fillMaxHeight().then(
                                if (isModalDrawerOpen) Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = dismissModalDrawer
                                ) else Modifier
                            ), horizontalAlignment = Alignment.CenterHorizontally) {
                                DuoStatusPill(status, modifier = Modifier.padding(top = statusTopPadding), vertical = true, tint = statusTint, showCellular = prefs.showCellularIndicator, onSignalPermission = onSignalPermission)
                                Box(Modifier.weight(1f).fillMaxWidth().then(
                                    if (isModalDrawerOpen) Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = dismissModalDrawer
                                    ) else Modifier
                                ), contentAlignment = Alignment.Center) {
                                    FavoritesDock(favorites, model, drag, placement, reduced, dockMenu,
                                        { dockBounds = it }, { id, bounds -> dockSlots[id] = bounds }, editing, addDockApps)
                                }
                                IconButton(onClick = {
                                    openLibrarySearch()
                                },
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).size(54.dp).glass(16, frosted = true).semantics {
                                        contentDescription = "Search"
                                    }) {
                                        val glyph = Glyph.SEARCH
                                        Box(contentAlignment = Alignment.Center) {
                                            GlyphIcon(glyph, Modifier.size(26.dp).offset(y = 1.dp).blur(1.dp, BlurredEdgeTreatment.Unbounded), tint = Color.Black.copy(alpha = 0.10f))
                                            GlyphIcon(glyph, Modifier.size(26.dp), tint = Color.White)
                                        }
                                    }
                            }
                        }
                        if (placement == DockPlacement.BOTTOM) {
                            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) { pageContent() }
                            Box(Modifier.fillMaxWidth().height(68.dp).then(
                                if (isModalDrawerOpen) Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = dismissModalDrawer
                                ) else Modifier
                            )) {
                                IconButton(
                                    onClick = {
                                        openLibrarySearch()
                                    },
                                    modifier = Modifier.align(Alignment.BottomStart).size(68.dp).glass(16, frosted = true).semantics {
                                        contentDescription = "Search"
                                    },
                                ) { GlyphIcon(Glyph.SEARCH, Modifier.size(26.dp), tint = LocalHomeColors.current.text) }
                                Box(Modifier.align(Alignment.BottomCenter)) {
                                    FavoritesDock(favorites, model, drag, placement, reduced, dockMenu,
                                        { dockBounds = it }, { id, bounds -> dockSlots[id] = bounds }, editing, addDockApps)
                                }
                            }
                        } else Row(Modifier.weight(1f).fillMaxWidth().then(
                            if (isModalDrawerOpen) Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = dismissModalDrawer
                            ) else Modifier
                        ), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (placement == DockPlacement.LEFT) rail()
                            Box(Modifier.weight(1f).fillMaxHeight().padding(start = if (placement == DockPlacement.LEFT) 0.dp else 4.dp, end = if (placement == DockPlacement.RIGHT) 0.dp else 4.dp, top = 2.dp, bottom = 2.dp)) { pageContent() }
                            if (placement == DockPlacement.RIGHT) rail()
                        }
                    }
                }
                drag.session?.let { session ->
                    if (session.itemId != null || session.favorite == true) Surface(
                        Modifier.align(Alignment.TopCenter).padding(WindowInsets.safeDrawing.asPaddingValues()).padding(top = 8.dp)
                            .width(220.dp).height(48.dp).onGloballyPositioned { removeBounds = it.boundsInRoot() },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) { Box(contentAlignment = Alignment.Center) { Text(if (session.favorite == true) "Remove from Dock" else "Remove from homescreen") } }
                    DisposableEffect(session.itemId, session.favorite) { onDispose { removeBounds = Rect.Zero } }
                    val widgetPreview = session.app == null && session.sourceBounds.width > 0f
                    val previewWidth = if (widgetPreview) with(density) { session.sourceBounds.width.toDp() } else 56.dp
                    val previewHeight = if (widgetPreview) with(density) { session.sourceBounds.height.toDp() } else 56.dp
                    Box(Modifier.size(previewWidth, previewHeight).testTag("drag-follower").graphicsLayer {
                        translationX = if (widgetPreview) session.previewTopLeft.x else session.position.x - 28.dp.toPx()
                        translationY = if (widgetPreview) session.previewTopLeft.y else session.position.y - 28.dp.toPx()
                        scaleX = if (widgetPreview) 1f else 1.12f; scaleY = scaleX; alpha = .96f
                    }, contentAlignment = Alignment.Center) {
                        if (session.app != null) AppIcon(session.app, model.apps)
                        else Surface(Modifier.fillMaxSize().border(2.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .65f)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                GlyphIcon(Glyph.WIDGETS, Modifier.size(28.dp))
                                Text(session.label, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
        HomeOptionsDock(homeMenu, reduced, { homeMenu = false },
            { homeMenu = false; widgetPicker = true }, { homeMenu = false; wallpaperPickerOpen = true }, { homeMenu = false; pageManager = true },
            { homeMenu = false; editing = true }, { homeMenu = false; settingsOpen = true })
        if (settingsOpen) LauncherSettings(prefs, fold, model, apps, { settingsOpen = false }, onDefaultHome, onSignalPermission,
            onWallpaperSettings = { wallpaperPickerOpen = true },
            onPages = { settingsOpen = false; pagesFromSettings = true; pageManager = true }, onContactsPermission = onContactsPermission,
            onNotificationAccess = onNotificationAccess)
        if (wallpaperPickerOpen) WallpaperPickerSheet(
            onDismiss = { wallpaperPickerOpen = false },
            onOpenSystemSettings = {
                wallpaperPickerOpen = false
                onWallpaperSettings()
            }
        )
        if (weatherPickerOpen) WeatherPickerSheet(
            weather = weather,
            weatherUnit = prefs.weatherUnit,
            apps = apps,
            appRepository = model.apps,
            onLaunchApp = launchApp,
            onOpenLocationSettings = {
                weatherPickerOpen = false
                settingsOpen = true
            },
            reduced = reduced,
            onDismiss = { weatherPickerOpen = false }
        )
        if (widgetPicker) WidgetPickerScreen(widgets,
            { widgetPicker = false; replaceTargetId = null },
            { provider ->
                widgetPicker = false
                val target = replaceTargetId?.let(prefs.home::item)?.takeIf { it.kind == HomeItemKind.WIDGET || it.kind == HomeItemKind.WIDGET_PROMPT }?.id
                replaceTargetId = null
                if (target != null) widgets.pickForReplace(provider.info, prefs.home.pageOf(target)?.id ?: selectedPage, target)
                else widgets.pick(provider.info, selectedPage)
            })
        if (pageManager) PageManagerScreen(prefs.home, selectedPage, appById, model, reduced, {
            pageManager = false; if (pagesFromSettings) settingsOpen = true; pagesFromSettings = false
        }, { model.selectPage(it); pageManager = false; pagesFromSettings = false })
        val pageSnackbar = remember { SnackbarHostState() }
        val pageNotice by model.pageNotice.collectAsStateWithLifecycle()
        LaunchedEffect(pageNotice?.token, fullPage) {
            val notice = pageNotice ?: return@LaunchedEffect
            if (fullPage) return@LaunchedEffect
            val result = pageSnackbar.showSnackbar(notice.message, if (notice.canUndo) "Undo" else null, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) model.undoPages() else model.dismissPageNotice(notice.token)
        }
        SnackbarHost(pageSnackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 8.dp))
        // Dead bindings after a host reinstall or provider removal: grace period lets
        // transient unpublishes heal on their own; the layout itself is never pruned.
        val orphans by model.orphanedWidgets.collectAsStateWithLifecycle()
        var orphanGracePassed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { delay(30_000); orphanGracePassed = true }
        // Auto-dismiss the notice after 8 seconds so it fades away promptly without lingering on screen
        LaunchedEffect(orphanGracePassed, orphans.isNotEmpty()) {
            if (orphanGracePassed && orphans.isNotEmpty() && !model.orphanNoticeDismissed) {
                delay(8_000)
                model.orphanNoticeDismissed = true
            }
        }
        val showOrphanNotice = orphanGracePassed && orphans.isNotEmpty() && !model.orphanNoticeDismissed && !fullPage
        AnimatedVisibility(
            visible = showOrphanNotice,
            enter = fadeIn(tween(if (reduced) 0 else 200)) + slideInVertically(tween(if (reduced) 0 else 200)) { it / 3 },
            exit = fadeOut(tween(if (reduced) 0 else 200)) + slideOutVertically(tween(if (reduced) 0 else 200)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 64.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        if (orphans.size == 1) "A widget lost its connection after an app update. Your layout is saved. Tap Review to reconnect it."
                        else "${orphans.size} widgets lost their connection after an app update. Your layout is saved. Tap Review to reconnect them.",
                        style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton({ model.orphanNoticeDismissed = true }) { Text("Dismiss") }
                        TextButton({
                            model.orphanNoticeDismissed = true
                            orphans.firstNotNullOfOrNull { prefs.home.pageOf(it.id)?.id }?.let(model::selectPage)
                        }) { Text("Review") }
                    }
                }
            }
        }
    }
    quick?.let { selection ->
        val item = selection.itemId?.let(prefs.home::item)
        val app = selection.app
        val title = app?.label ?: item?.let { itemTitle(it, appById) } ?: "Item"
        // Published app shortcuts behave like Pixel long-press actions: top of the menu, tap to run.
        val published by produceState(emptyList<android.content.pm.ShortcutInfo>(), app?.id) {
            value = app?.let { withContext(Dispatchers.IO) { model.apps.shortcuts(it) } }.orEmpty()
        }
        if (choosingStack && item != null) {
            val candidates = HomeEditor.stackCandidates(prefs.home, item.id)
            ItemQuickPopup(selection.anchor, "Create stack", candidates.map { other -> QuickAction(itemTitle(other, appById), Glyph.STACK) {
                model.createStack(item.id, other.id); closeQuick()
            } }, closeQuick, hint = if (candidates.isEmpty()) "Add another ${item.spanX} × ${item.spanY} item to your home screen to stack them." else "Choose an item of the same size.", reducedMotion = reduced)
        } else {
            val actions = buildList {
                if (app != null) published.forEach { shortcut ->
                    add(QuickAction(shortcut.shortLabel?.toString()?.takeIf(String::isNotBlank) ?: shortcut.longLabel?.toString() ?: "Open") {
                        closeQuick()
                        if (!model.apps.startShortcut(app, shortcut.id)) model.messages.tryEmit("That shortcut isn't available.")
                    })
                }
                if (item != null && selection.origin == QuickOrigin.HOME && item.kind != HomeItemKind.FOLDER && item.kind != HomeItemKind.WIDGET_PROMPT) {
                    val widgetAlive = item.widgetId?.let { widgets.manager.getAppWidgetInfo(it) != null } ?: true
                    if (item.kind == HomeItemKind.WIDGET && !widgetAlive) {
                        add(QuickAction("Replace widget", Glyph.WIDGETS) {
                            val targetId = item.id
                            closeQuick()
                            scope.launch {
                                val target = model.preferences.value.home.item(targetId)
                                    ?.takeIf { it.kind == HomeItemKind.WIDGET }
                                val info = target?.widgetProvider?.let { widgets.findProvider(it) }
                                val page = model.preferences.value.home.pageOf(targetId)?.id ?: selectedPage
                                if (target != null && info != null) widgets.pickForReplace(info, page, targetId)
                                else {
                                    if (target?.widgetProvider != null) model.messages.tryEmit(
                                        "That widget isn't installed. Choose a replacement.")
                                    replaceTargetId = targetId
                                    widgetPicker = true
                                }
                            }
                        })
                    }
                    val isResizable = item.widgetId != null && (widgets.manager.getAppWidgetInfo(item.widgetId)?.resizeMode ?: 0) != 0
                    if (isResizable) {
                        add(QuickAction("Resize", Glyph.WIDGETS) { closeQuick(); resizingId = item.id })
                    }
                    if (prefs.home.pageOf(item.id)?.id != HUB_PAGE_ID) {
                        add(QuickAction("Create stack", Glyph.STACK) { choosingStack = true })
                    }
                    if (item.kind == HomeItemKind.STACK) add(QuickAction("Unstack", Glyph.UNSTACK) { model.unstack(item.id); closeQuick() })
                }
                if (selection.origin == QuickOrigin.DOCK && app != null) add(QuickAction("Remove from dock", Glyph.REMOVE) { model.remove(app, true); closeQuick() })
                else if (item != null) add(QuickAction(if (prefs.home.pageOf(item.id)?.id == HUB_PAGE_ID) "Remove from Hub" else "Remove from home screen", Glyph.REMOVE) { model.removeItem(item.id, widgets::delete); closeQuick() })
                if (app != null) add(QuickAction("Uninstall", Glyph.UNINSTALL, destructive = true) { closeQuick(); onUninstall(app) })
            }
            val configure: (() -> Unit)? = when {
                item?.kind == HomeItemKind.WEATHER -> ({ closeQuick(); settingsOpen = true })
                item?.kind == HomeItemKind.FOLDER -> ({ closeQuick(); folderId = item.id })
                item?.widgetId != null && widgets.canConfigure(item.widgetId) -> ({ closeQuick(); widgets.configureExisting(item.widgetId) })
                else -> null
            }
            val isResizableWidget = item?.widgetId != null && (widgets.manager.getAppWidgetInfo(item.widgetId)?.resizeMode ?: 0) != 0
            ItemQuickPopup(selection.anchor, title, actions, closeQuick, configure, reducedMotion = reduced,
                headerIcon = if (app != null) ({ AppIcon(app, model.apps, Modifier.size(32.dp)) }) else null,
                focusable = !isResizableWidget)
        }
    }
    val resizableQuickItem = quick?.takeIf { it.origin == QuickOrigin.HOME }?.itemId?.let(prefs.home::item)
        ?.takeIf { item -> item.widgetId != null && (widgets.manager.getAppWidgetInfo(item.widgetId)?.resizeMode ?: 0) != 0 }

    val activeResizeItem = resizingId?.let(prefs.home::item) ?: resizableQuickItem

    if (activeResizeItem != null) {
        WidgetResizeOverlay(
            item = activeResizeItem,
            layout = prefs.home,
            targets = targets,
            widgets = widgets,
            onDismiss = {
                resizingId = null
                closeQuick()
            },
            onResize = { w, h, col, row ->
                model.resize(activeResizeItem.id, w, h, col, row)
            },
            onDragStarted = {
                closeQuick()
                resizingId = activeResizeItem.id
            }
        )
    }
    folderId?.let(prefs.home::item)?.let { folder -> FolderPanel(folder, apps, model, reduced, { folderId = null }) }
}

private fun itemTitle(item: HomeItem, apps: Map<String, AppEntry>): String = when (item.kind) {
    HomeItemKind.APP -> apps[item.appId]?.label ?: "App"
    HomeItemKind.WEATHER -> "Weather"
    HomeItemKind.CLOCK, HomeItemKind.ANALOG_CLOCK -> "Analog clock"
    HomeItemKind.STACK -> "Stack · ${item.stack.size} items"
    HomeItemKind.FOLDER -> item.title
    HomeItemKind.WIDGET -> item.widgetLabel?.takeIf { it.isNotBlank() } ?: "Android widget"
    HomeItemKind.WIDGET_PROMPT -> "Add widget"
}

@Composable
private fun FavoritesDock(apps: List<AppEntry>, model: LauncherViewModel, drag: LauncherDragState, placement: DockPlacement,
    reduced: Boolean, onMenu: (AppEntry) -> Unit, onBounds: (Rect) -> Unit, onSlot: (String, Rect) -> Unit,
    editing: Boolean, onAdd: () -> Unit) {
    val content: @Composable () -> Unit = {
        apps.forEach { app -> key(app.id) {
            Box(Modifier.size(54.dp, 56.dp).onGloballyPositioned { onSlot(app.id, it.boundsInRoot()) }) {
                AppTile(app, model.apps, drag, { if (editing) onMenu(app) else model.launch(app) }, { onMenu(app) },
                    Modifier.fillMaxSize(), showLabel = false, favorite = true, reducedMotion = reduced)
                if (editing) RemoveAppButton("${app.label} from Dock", { model.remove(app, true) }, Modifier.align(Alignment.TopEnd))
            }
        } }
        if (apps.isEmpty() || editing && apps.size < 6) IconButton(onClick = onAdd, Modifier.size(54.dp).semantics { contentDescription = "Add apps to Dock" }) {
            GlyphIcon(Glyph.PLUS, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
    val base = Modifier.onGloballyPositioned { onBounds(it.boundsInRoot()) }.glass(16, drag.session != null, frosted = true).padding(horizontal = 4.dp, vertical = 6.dp)
    if (placement == DockPlacement.BOTTOM) Row(base.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) { content() }
    else Column(base.width(62.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) { content() }
}
