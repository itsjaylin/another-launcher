# Architecture and integration

## State ownership and transitions

[STATE_MACHINES.md](STATE_MACHINES.md) maps workflow owners and transition contracts. `HomeNavigation` owns the foreground surface and Settings return paths. `WidgetSetupFlow` separates new binding/configuration from existing-widget settings. `LauncherDragState` consumes completion before callbacks. Layout schemas and widget host identity remain unchanged; OS/provider behavior still needs device checks.

## Build and platform configuration

The project uses AGP **9.4.0**, Kotlin/Compose compiler **2.4.20**, and Gradle **9.7.1**. AGP 9 includes Kotlin support; do not add `org.jetbrains.kotlin.android`.

`app/build.gradle.kts` sets compile/target SDK 37, minimum SDK 31, Java/Kotlin 17 bytecode, Compose, and release shrinking. Versions are pinned in the catalog:

| Dependency | Version | Use |
| --- | --- | --- |
| `androidx.window:window` | 1.5.1 | Fold features and window metrics |
| `androidx.activity:activity-compose` | 1.13.0 | Activity and result APIs |
| `androidx.core:core-ktx` | 1.19.0 | Insets and compatibility |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0 | Lifecycle observation |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 | ViewModel integration |
| `androidx.datastore:datastore-preferences` | 1.2.1 | Settings and layouts |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | Main/IO work and state streams |
| `androidx.compose:compose-bom` | 2026.09.00 | Compose UI and Material 3 |

### Manifest

- `LauncherActivity` handles `MAIN` + `HOME` + `DEFAULT`. Its `singleTask` mode delivers subsequent Home requests to `onNewIntent`.
- `SettingsActivity` has a `MAIN` + `LAUNCHER` entry and routes through `ACTION_SHOW_SETTINGS` to the full Settings screen.
- The activity is resizable and handles orientation, screen-size, layout, and density changes. It refreshes metrics and system bars after configuration changes.
- The window is hardware-accelerated and edge-to-edge. Interactive controls respect cutout/navigation insets; status bars hide on Home and can be revealed by a system swipe.
- `INTERNET`, network/Wi-Fi state, and `WAKE_LOCK` support weather, status, and fold handoff. Optional location, contacts, phone-state, and notification access are requested from user actions with inline explanations.
- `REQUEST_DELETE_PACKAGES` opens Android's uninstall confirmation. Package visibility is limited to launchable activities; `LauncherApps` lists the current user's activities.
- Hinge, Wi-Fi, and telephony hardware are optional. There is no root, accessibility service, screen capture, application-overlay permission, or `WRITE_SECURE_SETTINGS`.
- Samsung Wallpaper & style uses `com.samsung.intent.action.WALLPAPER_SETTING`, with a system Settings fallback.
- Local widget IDs are excluded from Android backup/device transfer in `res/xml/data_extraction_rules.xml`.

Full-page overlays blur the workspace by 28dp and, on Android 12+, request 36dp wallpaper blur with `FLAG_BLUR_BEHIND`. This is separate from the removed fold animation. Tour text and scrims follow the system theme. Privacy and credits have dedicated dialogs.

## Data flow and exact hooks

```text
LauncherActivity.onCreate
  ├─ FoldStateTracker ── lifecycle START/STOP
  │    ├─ WindowInfoTracker.windowLayoutInfo
  │    ├─ Sensor.TYPE_HINGE_ANGLE
  │    ├─ DisplayManager.DisplayListener
  │    └─ layout/configuration/multi-window changes
  │              ↓ StateFlow<FoldState>
  ├─ StatusMonitor → StateFlow<DeviceStatus>
  └─ setContent → HomeScreen
         ├─ workspace / native widget hosts
         ├─ favorites dock
         └─ status module
```

Folding changes the layout without a blur transition. `FoldStateTracker` removes sensor, display, and layout listeners and cancels WindowManager collection at STOP; coroutine cancellation propagates.

Search, Library scroll, and selected-page identity survive configuration changes. Favorites use ordered component IDs; home items use durable IDs and page/cell/span coordinates. Widget geometry changes preserve Android widget IDs.

## Home model and editing

`HomeLayout` stores home pages, a separate `hubPage`, and `defaultPageId`. Each page has a 4×6 grid. Apps and folders occupy one cell; widgets have rectangular spans. Stacks hold same-size leaf items and keep all child widget bindings.

`HomeEditor` handles placement, collisions, folders, stacks, cross-page moves, and page edits:

- Occupied drops reflow neighbors to the nearest free cells using Manhattan distance, then row/column order. If nothing fits, the change fails without overwriting items.
- App-on-app drops create folders on the same page, across pages, and from Library/Dock, including full grids. Existing folders accept distinct app IDs; duplicate shortcuts are a no-op.
- `folderDropTarget` is shared by hover/commit. `previewMove` keeps grouping targets stationary until release; other collisions reflow. Edit-mode folder creation remains available.
- Removing a page can repack its items elsewhere, large items first. Page edits support undo, and deleting the default page falls back to the first remaining page.

`HomeLayoutCodec` writes schema 5 under `home_layout_v3` and reads schemas 2–4. The old `home_layout_v2` key is retained. Schema 4 introduced provider identity (`wprov`/`wlabel`); schema 5 adds the separate Hub widget array. Legacy built-in clock/weather tiles are removed during decoding while apps, folders, and native widgets remain. Corrupt or unsupported data is rejected without overwriting storage. Stack child IDs stay globally unique; nested stacks are flattened. Binding deletion follows an explicitly persisted removal.

`Workspace` pairs pages on wide side-dock canvases and shows single pages otherwise. Default cells cap at 100dp; the optional inner portrait side layout caps at 140dp and aligns toward the dock. Cover grids sit 16dp above the bottom indicator; other grids center when space permits. Home pages remain continuous 4×6 grids without an inserted hinge row. Short windows scroll rather than shrink touch targets.

Wallpaper long-press yields to controls, occupied cells, and child gestures, and cancels beyond touch slop. Home options contains Widgets, Wallpaper, Pages, Edit, and Settings. Full-page settings/pickers use `FullPageSurface`; item menus use root-coordinate anchors clamped to the window.

Dragging creates a follower and dims the source without changing storage until release. Root-coordinate registries track targets; a same-cell drop is a no-op. Native widget scrolling stays with the provider until pickup sends `ACTION_CANCEL` through `claimGestureForLauncher`. Package removal cleans app shortcuts, folder/stack entries, and dock pins.

Live widget resize uses `WidgetResizeOverlay`: axis-appropriate handles, `positionChange()`, signed offsets, and commits at 60% of a cell with haptics and a 12% deadband. `expandParams`/`shrinkParams` adjust both span and origin for top/left changes. A size pill shows the current dimensions.

## Built-in tiles and widget catalog

Weather uses Open-Meteo forecasts/geocoding and a 15-minute in-memory cache. Users choose a city or allow a location lookup; startup can reuse a recent location when permission is already granted. Refresh follows the visible lifecycle, and errors leave the launcher usable. Units default to Fahrenheit for a US locale and Celsius otherwise. The legacy analog tile uses the shared minute tick rather than a frame loop.

The catalog filters `WIDGET_CATEGORY_HOME_SCREEN`, deduplicates by component and profile, and groups provider variants. Selection allocates an ID, tries `bindAppWidgetIdIfAllowed`, then opens consent/configuration if needed. Pending new bindings and existing-widget reconfiguration stay separate, so canceling reconfiguration cannot delete a working widget.

Bindings belong to Android's `AppWidgetService`. Broken bindings leave layout tiles intact: `orphanedWidgets` reports them, a delayed notice offers repair, and `replaceWidgetBinding` replaces the binding in place.

### First-run layout

The launcher does not import an OEM launcher's private layout or widget bindings. `HomeEditor.defaultLayout` starts Page 1 with two removable 2×2 widget prompts at row 0 and four apps at row 2. A square widget keeps the neighboring prompt; a wider widget clears it to make room. Edit mode can remove either prompt.

## Backup, restore and migration

`BackupCodec` exports layouts, favorites, categories, and settings as JSON through the Storage Access Framework (`CreateDocument`/`OpenDocument`), without broad storage access.

- Validate schema before writing; corrupt or unsupported files leave the active layout unchanged.
- Pass SAF URIs to ViewModel IO work, which owns and closes streams. Do not pass streams already closed by a caller's `.use` block.
- Keep widget positions and provider metadata; dead or foreign IDs become repairable tiles.
- Missing apps render an Unavailable placeholder without displacing neighbors.

### Native widget sizing

The grid owns an 8dp total gap. `LauncherWidgetHostView` removes framework padding and publishes actual content dimensions, min/max bounds, and `OPTION_APPWIDGET_SIZES` after layout. Standard `updateAppWidgetSize` would subtract padding again. Updates are coalesced, identical sizes are skipped, and measurement performs no provider IPC.

`FittingWidgetFrame` measures at least the provider minimum, uniformly scales oversized content to fit, and preserves native touch coordinates. Neither it nor Compose adds rounded clipping. Span selection and resizing use measured cells, provider constraints, and the same gap rules. Stack navigation has its own 36dp footer.

Samsung Weather receives optional `semAppWidgetColumnSpan`/`semAppWidgetRowSpan` hints from the stored span. Public sizing and fit remain primary; no hidden methods or stock-launcher identity are used.

## Appearance, library and status

`AnotherLauncherTheme` follows dynamic Material You light/dark colors. Menus use themed surfaces, 52dp minimum rows, separate destructive actions, and reduced-motion-aware reveals. Home labels default to hidden while accessibility names remain.

`Modifier.glass(..., frosted = true)` combines translucent fill, upper/lower reflections, and inner/outer rims. Decorative fills use 16–36% opacity; emphasized text panels retain 68–80% tint. Wallpaper controls use `HomeColors`; Hub/Library panels use `onSurface`. Shadows belong on wallpaper, not text inside sheets.

The transparent `FLAG_SHOW_WALLPAPER` window exposes Android's wallpaper. `WallpaperToneTracker` observes `WallpaperColors`, and `WallpaperContrast` compares foreground contrast in linear sRGB.

`PagerMap` maps Hub/Home/Library pages, including wide Hub + Home 1 pairing, without ghost pages. The Hub has weather and a schema-5 widget canvas: occupied-row height normally, full 4×6 in Edit. Its add button selects `HUB_KEY` before opening the picker. The independent Search shortcut uses a magnifier and the exact accessibility label Search.

The Library has a saved A–Z/category preference, adaptive category sheets, and a recent-app dropdown shown only for focused empty search. Hub and Library use capped portrait widths and respect landscape cutouts.

### Gesture navigation and returning Home

- `GestureNavContract.fromIntent` reads `gesture_nav_contract_v1` in `onCreate` and `onNewIntent`, then replies with `sendEndPosition()`.
- Avoid package re-queries and icon-cache flushes during `onResume`; `LauncherApps.Callback` keeps the app list current.
- A monotonic `homeRequest` triggers a snap to the resolved default page, even when that page is already selected in state.
- `adjustResize|stateAlwaysHidden`, focus clearing, and keyboard dismissal prevent the IME reopening after Home, app launch, or paging away.
- Read pager animation values inside `graphicsLayer` to avoid recomposing widget-heavy pages each frame. Defer `AppWidgetHost.startListening()` briefly after STARTED while keeping IDs and views intact.

Inline Library/search drag transfers pointer ownership to the root, hides search/IME, and reveals the last valid home page without canceling the gesture. Category-folder dialogs retain their separate-window path.

Add-apps mode remains an app-only A–Z modal with Home/Dock destinations and add/remove badges. During drag it stays composed but transparent so the gesture survives. Outside taps dismiss it; internal taps remain inside the surface.

### App grouping and device status

Grouping preserves other tiles and widget bindings. Tap a group title to edit a separate rename draft; Done, check, focus loss, or dismissal saves without replacing membership. +/check toggles app selection independently. `sortFolderApps` persists locale-aware A–Z order, keeps stable ties, and places unavailable apps last; later additions append until sorted again.

Notification dots are borderless red 10dp circles. `StatusMonitor` supplies time, battery, Wi-Fi/mobile type, and cellular strength. It handles display-info overrides and multi-cell readings for 5G NSA. Charging uses a solid `#34C759` pulse without a shadow.

Tapping the status emblem opens `DeviceStatusDialog`. Wi-Fi, mobile data, power saving, and airplane controls open system panels rather than changing protected settings directly. Broadcasts/lifecycle refresh the displayed state. Sheet contrast follows the system theme, independently of wallpaper controls.

## Fold-state policy

`FoldState` uses immutable window-coordinate `PixelBounds`. Zero-thickness fold lines are valid; do not discard them with `Rect.isEmpty`.

1. `FoldingFeature` supplies geometry, separation, and FLAT/HALF_OPENED posture.
2. Accept finite hinge angles in 0–180°; otherwise keep the angle nullable. Do not guess vendor sensor units.
3. Normalize motion with `p = clamp((angle − 8) / 164, 0, 1)`; 172° counts as flat.
4. A closed sensor reading overrides stale extension posture. Otherwise prefer WindowManager, then sensor fallback.
5. A folding feature or maximum smallest width ≥600dp identifies an inner canvas. Maximum metrics avoid misclassifying narrow split-screen windows.
6. Closing detection (`angle <= 75f` or INNER→COVER) triggers a timed bright wake lock and turn-screen-on/show-when-locked calls. A recent closing event can restart Home on the cover with `FLAG_ACTIVITY_SINGLE_TOP` during `onStop`.

These are heuristics, not fixed device identities. Collect actual diagnostics when tuning; display IDs do not identify panels reliably.

| Canvas | Dock |
| --- | --- |
| Cover, either orientation | Selected left/right side |
| Inner landscape or square | Selected left/right side |
| Inner portrait, full window | Bottom by default; selected side with wider-layout option |
| Multi-window | Selected left/right side |

There is no Auto option. Wide-pane hinge coordinates are converted from window to workspace space, with 12dp clearance on each side; individual home grids remain continuous.

## Transition math and rendering (experimental reference)

`TransitionDynamics`, `DuoUnfoldTransition`, and `DuoRenderLayer` remain as reference code. Their fold blur/shader is disconnected from the active Home/Activity path.

- Strength target: `sin(πp)`, zero at both endpoints.
- Smoothing: `x += (target − x) × (1 − exp(−dt/τ))`; τ is 45ms for progress/rising strength and 100ms for falling strength.
- Metrics-only handoffs start at 0.72; rotation/multi-window changes do not trigger them.
- A 160ms freshness deadline settles stationary postures; input older than ten seconds is a fresh resume.
- Interaction, menus, Settings, reduced motion, and disabled system animators clear the effect while still updating posture.

API 33+ uses Gaussian blur (up to 18dp/48px), hinge-relative AGSL zoom `exp(−0.12a)`, directional parallax, and five clamped seam samples. The seam grows from 0.08 to 0.65 of the axis; settled output sets `renderEffect = null`. Each instance owns its uniforms. API 31/32 uses drawing-only Canvas scaling and blur, preserving hit-test geometry.

This code cannot control system panel blanking or replay content from an inactive display. Shader tests validate construction, not current app performance.

## Settings architecture and components

`LauncherSettings` uses shared components in `SettingsComponents.kt`:

| Component | Purpose |
| --- | --- |
| `SettingGroup` | Frosted sections and aligned dividers |
| `SettingSwitchRow` | Icon, title, explanation, and boolean switch |
| `SettingSegmentedRow<T>` | Discrete choices with animated selection and haptics |
| `SettingNavigationRow` | Destination and current value |
| `SettingActionRow` | Action button and live status |
| `SettingStatTile` | Display, density, posture, and window diagnostics |

### Color picker and search

`SettingColorPickerRow`/`ColorPickerModal` provide a live status-pill preview, Auto plus 11 swatches, hue/saturation/value sliders, and validated six-digit hex input. Pure Kotlin RGB↔HSV math keeps unit tests independent of Android graphics APIs. Search matches section titles, row titles, and descriptions, expands matching groups, and restores the full view when cleared.

## App categories and Library folders

`LibraryFolders.kt` and `AppCategory.kt` combine user overrides, platform metadata, game flags, known packages, and label/package tokens. Overrides take precedence. Collision guards distinguish Perplexity from Plex, Uber Eats from transit, and Kindle/Amazon Music from shopping.

Active categories lead the grid; Other stays last. Category cards use recent launches for their three direct-launch slots, with remaining apps alphabetical in the sheet. Settings offers category refresh and override reset.

## Onboarding and first run

`onboarding_completed` defaults to false; restored backups mark it complete. Completion uses `LauncherViewModel.completeOnboarding()`. Help & Tour replays with `isTourMode = true` without resetting layouts or preferences.

The five steps cover welcome, Hub/Home/Library structure, widget/page gestures, optional integrations, and completion. Integration actions open the Home role prompt, notification settings, or location/contact/phone permission prompts, with current access status shown inline.

## Extension points

- Tune fold thresholds from device evidence, not model-specific resolutions.
- Keep interactions in the existing hierarchy and preserve widget IDs during geometry edits.
- Reuse collision and provider-size rules for new layout controls.
- Treat transition/shader tuning as experimental; profile release builds on hardware before reconnecting it.

## Unified Library search and focus

`HomeScreen` owns the saveable query. Contacts permission refreshes on resume; queries run on IO and launches use NEW_TASK. `Workspace` passes a request token to `AllAppsDrawer`, consumed after selection, programmatic navigation, and pager settlement. Focus is requested on one frame and the keyboard on the next. Each shortcut tap gets a new token.

Offscreen drawers cannot handle Back or retain focus/overlays. Clear keeps focus; Cancel/Back exits search. Home clears query and pending requests, then waits for the ViewModel's default-page selection before jumping. Observe settled pager positions through `snapshotFlow` outside programmatic navigation and pending Home requests, never synchronous `SideEffect`.

Ranked apps, calculator, Settings, and optional contacts remain local. Nonblank input also shows a provider-labeled Web action; IME Search uses the same `WebSearch.intent`. Add-apps mode stays app-only. Discover requirements are in [DISCOVER.md](DISCOVER.md).

## Wallpaper edits and disclosures

`WallpaperImageSizing` bounds decoding by available heap, preserves aspect ratio, and never upscales. `ImageDecoder` handles orientation. A Main-owned busy state serializes photo/preset/rotation work on IO/Default; failures retain selection and re-enable controls. Home/Lock/Both application stays off-thread. Widget preview keys include density for display changes.

Permission requests come only from onboarding/Settings actions. `PermissionExplanation` shows complete inline text before the OS prompt; no extra Agree dialog is used. Denied contacts stay off and retryable; denied location leaves manual city search usable. Navigation/dismissal never requests access. The manually opened privacy policy scrolls while keeping Close reachable. Location disclosures cover approximate/precise access, saved coordinates/backups, Open-Meteo requests, and Android geocoding.
