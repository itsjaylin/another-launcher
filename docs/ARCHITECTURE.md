# Architecture and integration

## State ownership and transitions

The source audit and transition contracts are in [STATE_MACHINES.md](STATE_MACHINES.md). `HomeNavigation` replaces independent full-page flags with one foreground surface and explicit Settings return paths. `WidgetSetupFlow` separates new binding/configuration ownership from existing-widget settings. `LauncherDragState` consumes completed gestures before dispatching effects. These changes preserve the current UI, layout schema and widget host identity; OS/provider behavior still requires device checks.

## Build and platform configuration

The project uses AGP **9.4.0**, Kotlin/Compose compiler **2.4.20**, and Gradle **9.7.1**. AGP 9 supplies built-in Kotlin support; do not add the legacy `org.jetbrains.kotlin.android` plugin alongside it.

`app/build.gradle.kts` sets `compileSdk = 37`, `targetSdk = 37`, `minSdk = 31`, Compose, Java/Kotlin 17 bytecode, and release shrinking. The locally installed base SDK identifies itself as Android 17, API 37.0, non-preview. The version catalog pins:

| Dependency | Version | Use |
| --- | --- | --- |
| `androidx.window:window` | 1.5.1 | FoldingFeature and current/maximum window metrics |
| `androidx.activity:activity-compose` | 1.13.0 | Activity, Compose and result APIs |
| `androidx.core:core-ktx` | 1.19.0 | Insets and platform compatibility |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0 | Lifecycle-aware observation |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 | ViewModel integration |
| `androidx.datastore:datastore-preferences` | 1.2.1 | Ordered pins, dock preference, motion setting, widget IDs |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | Main/IO work and state streams |
| `androidx.compose:compose-bom` | 2026.09.00 | UI, graphics, Foundation and Material 3 |

`android.graphics.RenderEffect` (API 31) and `android.graphics.RuntimeShader` (API 33) are **framework APIs**, not separate graphics dependencies. AGSL is embedded in the render-layer source and instantiated only behind the API 33 check. Android 12 uses Gaussian blur and a drawing-only Canvas matrix.

### Manifest

- `MAIN` + `HOME` + `DEFAULT` on `LauncherActivity` declares a selectable Android home app. `SettingsActivity` declares a dedicated `MAIN` + `LAUNCHER` filter, making "Another Launcher Settings" appear in the App Library and system launcher list and routing to `LauncherActivity` via `ACTION_SHOW_SETTINGS` to open the full homescreen options surface.
- `singleTask` delivers subsequent Home requests to `onNewIntent`, which closes transient UI.
- `resizeableActivity` and configuration handling for orientation, screen size, smallest screen size, screen layout and density support display transitions. The Activity refreshes metrics and system bars on configuration changes.
- Hardware acceleration enables RenderEffect. The app draws edge-to-edge and reserves safe drawing/cutout/navigation insets for interactive UI. Status bars are hidden while home is visible and can be revealed with a system swipe. The navigation bar remains system-owned.
- `ACCESS_NETWORK_STATE` and `ACCESS_WIFI_STATE` are normal permissions. Optional runtime `READ_PHONE_STATE` enables the signal-strength callback.
- `INTERNET` supports weather/forecast requests. Optional coarse/fine location permissions are requested only when the user taps **Use current location** in Weather settings; manual city search does not require them. `REQUEST_DELETE_PACKAGES` enables the Uninstall quick action; Android supplies the confirmation UI.
- `android.hardware.sensor.hinge_angle`, Wi-Fi, and telephony are optional features.
- Package visibility is restricted to launchable activities through an intent query. The app lists the current user's activities using `LauncherApps`.
- The hinge sensor requires no permission. There is no projection, privileged framebuffer access, accessibility service, root module, or application-overlay permission.
- `WAKE_LOCK` permission and dynamic `setTurnScreenOn(true)` / `setShowWhenLocked(true)` prevent foldable devices (such as Samsung Galaxy Z Fold) from entering deep sleep when folded shut from the launcher, ensuring the cover display turns on and maintains home continuity.
- Wallpaper integration links directly to Samsung "Wallpaper and style" via public action `com.samsung.intent.action.WALLPAPER_SETTING` with graceful fallback to system Settings. In compliance with Google Play Developer Program Policies, no privileged signature permissions (such as `WRITE_SECURE_SETTINGS`) are requested.
- Optional location, contacts, phone-state and notification-access features show in-context explanations (`PermissionExplanation`) before the action that opens the Android permission prompt or access settings. A complete in-app privacy policy viewer (`PrivacyPolicyDialog`) and standalone `PRIVACY_POLICY.md` document transparent on-device data handling, while `CreditsDialog` attributes key open-source repositories (`jakesgoodapps/DuoLauncher`, `berkaycatak/adaptive_platform_ui`) with direct links and usage descriptions.
- Full-page overlay transitions (`HomeScreen.kt`, `LauncherActivity.kt`): when full-page overlays (`fullPage`: Settings, Welcome Tour, Widget Picker, Page Manager) are active, `HomeScreen` applies an animated 28dp Gaussian blur to the underlying workspace (`home-root`), while `LauncherActivity` dynamically activates Android 12+ `FLAG_BLUR_BEHIND` (`blurBehindRadius = 36.dp`) to softly diffuse the system wallpaper behind the window.
- Walkthrough and onboarding contrast (`OnboardingScreen.kt`): strictly respects the project's Sheet and Overlay Contrast Invariant by using dark theme tokens (`MaterialTheme.colorScheme.onSurface`, `onSurfaceVariant`) in light mode and `#F8FAFF` in dark mode, coupled with contrast-tuned scrims (82% white in light mode, 72% black in dark mode).
- Local widget IDs are excluded from backup/device transfer in `res/xml/data_extraction_rules.xml`.

## Data flow and exact hooks

```text
LauncherActivity.onCreate
  ├─ FoldStateTracker(this) ── lifecycle START/STOP
  │    ├─ WindowInfoTracker.windowLayoutInfo
  │    ├─ SensorManager / Sensor.TYPE_HINGE_ANGLE
  │    ├─ DisplayManager.DisplayListener
  │    └─ decor layout + onConfigurationChanged/onMultiWindowModeChanged
  │              ↓ StateFlow<FoldState>
  ├─ repeatOnLifecycle(STARTED)
  ├─ StatusMonitor(this) → StateFlow<DeviceStatus>
  └─ setContent → HomeScreen
         └─ Box
               ├─ workspace / native widget host views
               ├─ adaptive favorites dock
               └─ status module
```

The home screen renders directly without an overlay blur transition on fold/unfold. `HomeScreen` provides immediate, crisp transitions across cover and inner displays. `FoldStateTracker` unregisters every sensor/display/layout listener and cancels WindowManager collection at STOP. Coroutine cancellation is propagated rather than swallowed.


Search query, library scroll state and the selected page identity survive configuration changes. Dock pins are ordered component IDs. Home items have durable IDs and explicit page/cell/span coordinates. Native widget IDs are stable; `LauncherWidgetHostView` publishes its measured content dimensions to each provider after layout. Reconfiguration never creates duplicate persistent widget IDs.

## Home model and editing (0.7 alpha)

`HomeLayout` contains persistent home pages plus a dedicated `hubPage`, each with a 4-column × 6-row grid, and an explicit `defaultPageId` (`resolvedDefaultPageId()` falls back to the first available page). Apps/folders take one cell; third-party Android widgets have rectangular spans. A stack contains same-size leaf items and renders one active child while preserving all native widget IDs. `HomeEditor` handles collision checks, first-fit insertion, folders, stacks/unstacking, cross-page movement, default page designation (`setDefaultPage`), and safe deletion. Full-page insertion creates another page instead of displacing content. App-on-app drops create folders at the target cell, and app-on-folder drops add a distinct app entry. `HomeEditor.folderDropTarget` is shared by commit and hover policy. `HomeEditor.previewMove` keeps grouping targets stationary until release; for other collisions it delegates to `reflowed`, which slides intersecting items to free positions on the same page and fails closed when nothing fits. `HomeScreen` uses this preview policy so grouping targets do not move away from the pointer.

`HomeLayoutCodec` writes schema 5 JSON inside DataStore with `defaultPage`, normal `pages`, and a separate `hub` widget array; it reads schemas 2–4 for upgrade. The `home_layout_v2` key is preserved while the new layout is stored under `home_layout_v3`. Legacy built-in clock and weather tiles are removed during decoding while all apps, folders, and native widget identities remain intact. Schema 4 adds per-widget provider identity (`wprov`/`wlabel`) so dead bindings stay repairable. Other existing pages retain their layouts. Unsupported or corrupt data is rejected rather than overwritten. Stack children have globally unique identities and nested stacks are flattened; all child bindings are deleted only after an explicit removal is persisted.

`Workspace` presents pairs of pages on wide side-dock canvases and single pages otherwise. To maintain comfortable breathing room on expansive inner displays, `WidgetSizing.gridCell` caps cells at `MAX_CELL_DP = 100dp` in the default layout. The optional inner portrait side layout uses a 140dp cap and aligns the grid toward the selected dock edge. Cover grids sit above the bottom indicator with a 16dp margin; other grids remain vertically centered when room permits. Home grids deliberately ignore the physical hinge line and remain a continuous 4×6 canvas in every unfolded orientation. Short windows scroll the grid rather than reduce icon touch targets. A passive root gesture observer extends Home-options long-press handling to unobstructed wallpaper across the display; it yields to grid cells, the Dock, controls, and consumed child gestures, and cancels once movement exceeds touch slop so pager swipes remain native. High-density icon loading in `AppRepository` uses `launcherLargeIconDensity` and `launcherLargeIconSize` (target size up to 384px) with `FilterQuality.High` to keep app icons sharp on high-DPI displays.

Long-pressing a free cell opens `HomeOptionsDock`, an animated in-window bottom control dock streamlined to 5 core actions: **Widgets**, **Wallpaper**, **Pages**, **Edit**, and **Settings** (app additions are handled via the dedicated library button, drawer swipe, and '+' controls). Tapping the "Another Launcher Settings" app from the App Library or home grid directly launches the full `LauncherSettings` surface. Launcher settings and widget selection use `FullPageSurface`, not Dialog windows. Item options use `ItemQuickPopup` with a measured root-coordinate anchor and a clamped position provider. App popups expose removal/uninstall without an app-settings page; optional widget/folder setup is exposed through a small gear only when applicable.

Pickup creates a visual follower without changing persistence. The source icon remains dimmed at its original position, and only release commits a placement. Root-coordinate grid/drop registries and a top Remove target handle drag destinations. Widget long-press observes pointer events without consuming native scrolling, then claims the gesture for the launcher (`claimGestureForLauncher` sends `ACTION_CANCEL`) so pickup never opens a popup; a same-cell drop is a no-op. Package removal callbacks clean the corresponding app shortcuts, folder/stack contents and Dock pins. Widget resizing uses live drag handles (`WidgetResizeOverlay` in `HomeMenus.kt`): edge handles for single-axis widgets and corner handles for dual-axis widgets. The pointer loop uses `positionChange()` (not raw deltas) so that widget-bound shifts mid-drag don't cause offset jumps. Each drag accumulates a signed pixel offset per axis; crossing 60% of a cell threshold triggers a resize commit with haptic feedback (`TextHandleMove`) guarded by a 12% hysteresis deadband to prevent flutter. A floating "$W \times H$" label indicates live dimensions above the widget during resize. The overlay highlight uses `primaryContainer` tint with a border. `expandParams`/`shrinkParams` helpers compute the new span/origin for any axis/direction combination, supporting left and top handle expansion that shifts the widget's column or row. Page removal keeps items by repacking them large-first into free space elsewhere (duplicates preserved), with `removeEmptyPages`, `movePage`, `setDefaultPage`, and undo around every page edit.

## Built-in tiles and widget catalog

Weather uses a selected `WeatherLocation`, Open-Meteo's geocoding/current-weather endpoints and a 15-minute in-memory cache. The location can come from manual city search or a user-initiated `LocationManager` lookup; reverse geocoding supplies a readable label when available. A lifecycle-aware subscription refreshes while the UI is active; request timeouts and offline/error states leave the launcher usable. An unconfigured tile prompts for a city instead of inventing a forecast. Temperatures are displayed in Fahrenheit for a US locale and Celsius otherwise. The analog clock draws hour/minute hands from the shared minute-tick status source without a permanent per-frame timer.

The provider catalog filters for `WIDGET_CATEGORY_HOME_SCREEN`, deduplicates by component **and profile**, and groups legitimate variants by provider package. Selection allocates an ID, attempts `bindAppWidgetIdIfAllowed`, and invokes Android's consent/configuration activities as needed. Pending new bindings and existing-widget configuration are tracked separately through recreation: canceling reconfiguration cannot delete an existing widget. Stack cycling keeps bindings intact, and unstacking restores children into grid slots.

Bindings belong to the system `AppWidgetService`, not to app storage: reinstalling the host or removing a provider orphans the ids while the layout keeps them (observed on One UI after an `install -r` replace cycle). The launcher therefore never prunes widget tiles on its own — `HomeEditor.orphanedWidgets` only reports them, the home screen shows a repair notice after a grace period, and `replaceWidgetBinding` swaps a fresh binding onto the same tile in place via the picker's replace flow.

### First-Run Layout & Top Widget Call-to-Action Architecture
Because Android application sandboxing strictly forbids third-party launchers from accessing OEM home screen databases (e.g. Samsung One UI Home's signature-protected provider) and `AppWidgetManager` binds widget IDs strictly to host process IDs, cross-launcher automated layout import is impossible on modern Android (API 31+). 

Another Launcher solves initial setup with an intentional, zero-clutter first-run layout (`HomeEditor.defaultLayout`):
- **Top Widget Call-to-Action Prompts (`HomeItemKind.WIDGET_PROMPT`)**: Rather than dumping arbitrary alphabetical apps across the top rows, Page 1 initializes with two 2×2 liquid frosted glass prompt cards (`Add widget · 2×2 or 4×2`) at row 0 (columns 0 and 2).
- **In-Place Widget Binding & Sibling Reflow (`replaceWidgetBinding`)**: Tapping either prompt launches the native widget picker. If the user selects a square widget (span $\le$ 2), it binds in place while preserving the sibling prompt for a second square widget. If the user chooses a wide rectangle widget (spanX $>$ 2), the sibling prompt on that row is automatically cleared, allowing the 4×2 or 4×1 widget to seamlessly span across the top of the grid.
- **Edit-Mode Dismissal**: Each prompt card renders a top-right remove button in edit mode, allowing users who prefer an app-only grid to dismiss the widget slots cleanly.
- **Row 2 App Flow**: The first four apps from the user's library are placed on Row 2 (columns 0..3), leaving Rows 3–5 open and spacious.

## Backup, restore and migration

`BackupCodec` writes and reads standalone portable JSON backup files containing the complete home layout, pinned favorites, category overrides, and user settings. It operates via the Android Storage Access Framework (`CreateDocument` / `OpenDocument`), allowing users to save and restore backups to Documents, Downloads, or cloud storage without requiring broad external storage permissions.

Restoring a backup is atomic and safe:
- **Schema Validation & Rollback Protection**: The JSON schema is validated before writing to DataStore. If the file is corrupted or carries an unsupported version, the active layout is left completely intact.
- **Asynchronous Safe Streaming**: Streams opened by Storage Access Framework (`contentResolver.openOutputStream` / `openInputStream`) are managed entirely inside the ViewModel's background coroutine (`Dispatchers.IO`). SAF URIs are passed directly from Compose activity launchers to the ViewModel rather than passing streams closed synchronously via `.use`, preventing premature stream closure and truncated/empty files.
- **Widget Binding Continuity**: Restored widgets maintain their precise grid coordinates, spans, and Schema 4 provider metadata (`wprov`/`wlabel`). Because native widget IDs are host-specific, the restored IDs are evaluated against the current system `AppWidgetService`: dead or foreign bindings are cleanly marked as orphaned tiles, allowing in-place rebinding via the quick menu or review banner without collapsing the grid.
- **Missing App Resilience**: If a backed-up app is not currently installed on the target device, it renders the "Unavailable" placeholder (`MissingItem`) rather than displacing adjacent tiles.

### Native widget sizing

The grid applies its own 8dp total cell gap. `LauncherWidgetHostView.setAppWidget` removes the framework's additional padding so provider content aligns with those grid bounds. Calling the standard `updateAppWidgetSize` after doing that would still subtract the framework padding internally. Instead, a coalesced post-layout callback publishes the actual content width/height, min/max bounds and `OPTION_APPWIDGET_SIZES` directly. Identical sizes are not republished. No provider IPC is performed inside measurement.

`FittingWidgetFrame` measures the real host at least as large as the provider's supported minimum. If its grid slot is smaller, one uniform native View scale fits the whole widget into the slot and centers it. This retains aspect ratio and correct native touch transformation. Neither this frame nor Compose imposes a rounded clipping mask on provider content. The provider remains responsible for its own internal scrolling and design.

Initial span selection uses the reported grid cell dimensions, provider default/minimum size and advertised target cells. Resize options use the same content-gap rules and provider axis/max-size constraints. Existing stored placements do not need to be deleted or rebound. Stack navigation consumes a separate 36dp footer rather than overlaying weather text or controls.

For Samsung Weather, optional `semAppWidgetColumnSpan`/`semAppWidgetRowSpan` bundle hints reflect the real stored span; these names were observed on the connected One UI device. No hidden methods or stock-launcher identity spoofing are used. The public size contract and fit behavior remain the primary mechanism.

## Appearance, library and status

`AnotherLauncherTheme` uses the system's dynamic Material You light/dark color schemes. Native day/night resources follow system UI mode. Popup menus use tonal surfaces, action icons, 52dp minimum rows, optional app headers, separated destructive actions and a reduced-motion-aware reveal. Home controls use compact tonal icon buttons. Wallpaper-aware home text remains independent of menu colors.

Docks and interactive surfaces support a layered liquid-glass finish via `Modifier.glass(radius, emphasized, frosted = true)`. Frosted surfaces combine a translucent three-stop Material density gradient with a broad top-left radial reflection, a faint lower reflection, a tight inner rim, and a top-lit outer refraction edge. Dark mode uses lower-density fills and restrained highlights to preserve wallpaper depth; light mode uses stronger white reflections and rim separation so glass remains legible without becoming opaque.

The launcher window operates with `FLAG_SHOW_WALLPAPER` and a transparent background, directly exposing the native Android/Samsung system wallpaper; `WallpaperToneTracker` listens to public `WallpaperColors` updates. `WallpaperContrast` compares light/dark foreground contrast using linear sRGB luminance, with system appearance as fallback. Home labels have a contrast shadow; library/menu surfaces use opaque theme colors. Directly integrates with Samsung Dressroom (`com.samsung.android.app.dressroom`) and Android system wallpaper settings.

Home labels default to hidden independently of the app library; accessibility names remain. `PagerMap` retains narrow Hub/Home/Library pages and wide Hub + Home 1 pairing, with the final native App Library spread and no ghost pages. Hub contains the existing weather card and schema-5 native-widget canvas only. It compacts to occupied rows, expands to 4×6 in Edit, and keeps bindings, configuration, resizing, removal and orphan repair. The Hub add button selects `HUB_KEY` before opening the picker. App Library keeps its square A–Z/category toggle, DataStore-backed browsing preference, category sheets and recent-app dropdown. The Hub indicator uses `Glyph.WIDGETS`; the independent Search shortcut uses `Glyph.SEARCH` and exact accessibility label “Search”, retaining existing dock/rail placement.

In portrait, Hub weather and widgets share the capped four-column grid width; narrower displays fill available width. The Library retains the same portrait cap, responsive category columns, alphabetical grid and unfolded-landscape cutout padding. Category folder overlays retain adaptive sizing and Back/outside dismissal.

### Gesture Navigation Contract & System Return-to-Home Handshake

Returning to the home screen from within third-party apps via system gesture navigation (swiping up from the bottom navigation bar on AOSP, Pixel Launcher, or Samsung One UI HoneySpace) relies on a two-way handshake protocol:
- **`GestureNavContract` Handshake**: When the system navigation bar initiates a return-to-home gesture, it delivers an intent with `gesture_nav_contract_v1` extras containing `EXTRA_REMOTE_CALLBACK` (a `Message` object with a `replyTo` messenger). `LauncherActivity` extracts this via `GestureNavContract.fromIntent(intent)` in both `onCreate` and `onNewIntent` and immediately replies (`sendEndPosition()`). Acknowledging this handshake informs the system gesture controller that the launcher is ready, preventing gesture timeouts, flicker, and animation cancellations (`onAnimationCancelled`).
- **Zero-Stall `onResume` Transition**: To stay well within the system gesture controller's frame deadline (~400ms), `onResume` avoids synchronous heavy work such as package re-querying or icon cache flushes (`icons.evictAll()`). `AppRepository` maintains continuous state through live `LauncherApps.Callback` events, ensuring smooth 60/120fps entry without garbage-collection hitching that could abort the gesture back into the foreground app.
- **`homeRequest` Pager Synchronization**: Returning home dispatches a monotonic `homeRequest` counter from `LauncherActivity.onNewIntent` through `HomeScreen` into `Workspace`. Observing `homeRequest` as a key in `Workspace`'s pager effect guarantees an immediate scroll back to the default home spread, even if `selectedPage` was already set to the default page.
- **Soft Keyboard & Search Focus Suppression (`stateAlwaysHidden`)**: To prevent the on-screen keyboard from automatically popping up and shrinking the homescreen when returning home from external apps, `AndroidManifest.xml` enforces `windowSoftInputMode="adjustResize|stateAlwaysHidden"`. Furthermore, returning home (`homeRequest`), swiping the workspace pager away from search surfaces, and launching apps from App Library actively invokes `focusManager.clearFocus(force = true)` and `keyboardController.hide()`, resetting search queries and ensuring the homescreen rests cleanly in full-height unconstrained state.

The modal drawer overlay remains scoped exclusively to "Add apps" / Add-to-home mode, styled with a frosted glass container (`Modifier.glass(28, frosted = true)`). It presents a single unified A–Z list/grid of all apps without category splits or category chips, with instant destination switching (Home N / Dock) and "+ Add" / "✓ Remove" badges. Inline library/result tiles hand their drag to the persistent Home root. Its Initial-pass pointer loop follows the original pointer in root coordinates; source cancellation/end no longer terminates that session. After movement exceeds hold slop, HomeScreen clears search/IME and selects the last valid home page (otherwise the configured default); Workspace snaps to that page while a root-tracked drag is active. This permits source removal and edge paging without losing the gesture. Stationary holds still open app options; release commits once, while root teardown, Home, geometry change or multi-touch cancel. The older Add apps modal can fade out during the same root-owned handoff instead of keeping an invisible source alive. Category-folder dialogs retain their separate-window gesture path; this handoff covers inline search/list results. Touching anywhere outside the modal drawer surface — specifically the empty areas of the side rail (between the top-right status module and dock, or between the dock and bottom app button), the bottom dock bar padding, or the app library button itself — triggers `dismissModalDrawer()`, clearing focus, hiding the keyboard, and closing the drawer immediately without requiring the device back button. The modal drawer's `Surface` consumes internal taps, ensuring browsing, searching, and adding apps within the drawer never triggers accidental dismissals.

### Homescreen App Swapping & Reflow Engine

Moving apps onto one another creates a folder:

- **App-on-app grouping:** releasing a 1×1 app on another 1×1 app replaces the target with a folder containing both app IDs. This works on the same page, across pages, and from the library or Dock. Other tiles and widget bindings are preserved, including on full pages.
- **Stable hover:** grouping targets stay in place until the drop; folders are not persisted or allocated during hover.
- **Existing folders:** drops append distinct app IDs while preserving the folder ID, name and position. Dropping two shortcuts of the same app onto one another is a no-op.
- **Other placement:** empty cells move normally, and non-grouping collisions retain reflow. The Edit-mode Folder action remains available. Inside an open group, tapping its title starts a separate rename draft with selected text and keyboard focus. IME Done, the check button, focus loss or dismissal saves through `renameFolder`, preserving current membership. The +/check toggle controls app selection without changing the name. A–Z calls `sortFolderApps` on the current persisted layout; locale collation ignores case/accents at primary strength, stable ties keep their order, and unavailable apps remain at the end. Sorting is a one-time persisted action, so later additions append normally.

Apps with live notifications (via `NotificationListenerService`) render a clean, borderless solid red alert dot (`Color.Red`, 10dp) at the top-right of their icon.

The edge status module uses a 16sp bold `h:mm`/`HH:mm` time above a circular emblem. The outer arc indicates battery percentage, four lower dots show cellular signal strength, and the center dynamically presents either Wi-Fi connectivity or the active cellular network type (**5G+**, **5G**, **4G**, **3G**, **2G**, or **1x**). `StatusMonitor` listens to `TelephonyCallback.DisplayInfoListener` decoupled from phone state permissions (taking advantage of the Android 12+ API 31 permission waiver) and formats `TelephonyDisplayInfo` override types. Cellular signal strength is extracted across multi-cell measurements (`cellSignalStrengths`) to support 5G NSA topologies where `mNr` is idle/invalid while the primary LTE carrier operates at full bars. When charging, an iconic Apple systemGreen (`#34C759`) indicator pulses gently below the emblem. The module includes an ultra-sheer blurred drop shadow pass matching the time label for maximum legibility over light and dark wallpapers. Tap opens a responsive frosted device-controls dialog (`DeviceStatusDialog`) adhering to the Sheet & Overlay Contrast Invariant (`#F8FAFF` in dark mode) with high-fidelity vector icons for Wi-Fi, Mobile Data (with signal dots/bars and quick permission enablement), Power Saving, and Airplane Mode. Because Android protects these radios and global power settings from ordinary apps, the rows route to the corresponding system panel/settings screen and `StatusMonitor` refreshes from Wi-Fi, power-save, airplane, and telephony broadcasts when state changes. Full-page settings uses the system-theme navigation icon contrast, independently of the Home wallpaper's foreground choice.

Pager motion reads `PagerState` only inside `graphicsLayer`, invalidating the render layer without recomposing widget-heavy page content on every frame. A new Home request snaps the pager to the resolved default before the system gesture completes instead of stacking an internal page animation over Android’s Home transition. `WidgetController` defers `AppWidgetHost.startListening()` briefly after `STARTED`, keeping provider binder/RemoteViews inflation work off the first return-to-Home frame while preserving widget IDs and views.

## Fold-state policy

`FoldState` contains immutable `PixelBounds`, rather than publishing a mutable Android `Rect`. Bounds are in window coordinates. Zero-thickness fold lines are valid; the hinge center calculation intentionally does not test `Rect.isEmpty`.

1. A `FoldingFeature` provides geometry, orientation, separation and FLAT/HALF_OPENED posture.
2. Standard hinge samples are accepted only if finite and within 0–180°. Unavailable or rejected sensors leave a nullable angle. Vendor sensors with unknown units are not guessed by name.
3. For continuous motion, `p = clamp((angle − 8) / 164, 0, 1)`. The flat threshold of 172° tolerates hinges which stop short of 180°.
4. A closed sensor reading overrides stale extension posture. Otherwise WindowManager posture takes precedence, then the sensor supplies a discrete fallback.
5. A folding feature or **maximum** smallest width ≥600 dp classifies an inner canvas. Using maximum rather than current window bounds prevents a narrow split-screen window from being misclassified as the cover. The reported evidence is visible in Settings.
6. **Hardware Fold Continuity:** When a foldable device is closed while the launcher is active, `FoldStateTracker` flags a closing transition via hinge sensor (`angle <= 75f`) or `DisplayListener` handoff (`INNER` to `COVER`). `LauncherActivity` acquires a temporary `PowerManager.SCREEN_BRIGHT_WAKE_LOCK` and invokes `setTurnScreenOn(true)` / `setShowWhenLocked(true)`. On `onStop`, if a fold closing was detected within the handoff window, the launcher wakes the cover screen and restarts Home on the cover display with `FLAG_ACTIVITY_SINGLE_TOP`, preventing devices like the Samsung Galaxy Z Fold from dropping into deep sleep or failing to illuminate the cover panel.

This classification is a capability heuristic: public window metrics alone cannot distinguish every large tablet, external display, or OEM cover-screen policy. No display ID is assumed to be a particular panel. Device-specific tuning should use measured diagnostics from the actual device.

Dock policy:

| Canvas | Position |
| --- | --- |
| Cover, portrait or landscape | Side |
| Inner landscape or square | Side |
| Inner portrait, not closed, full window | Bottom |
| Multi-window | Side |

Auto always uses the right edge regardless of rotation. Explicit Left/Right preferences take precedence, so the Dock moves left only when Left is selected. Physical hinge placement is converted from window coordinates to workspace coordinates before splitting panes; a 12 dp gutter is reserved on each side of the seam.

## Transition math and rendering (Experimental Reference)

The classes in `com.anotherlauncher.transition` (`TransitionDynamics`, `DuoUnfoldTransition`, `DuoRenderLayer`) contain the mathematical models and AGSL shaders developed for experimental fold-motion bridges. The runtime blur and shader overlay have been removed from the active `HomeScreen` and Activity lifecycle to keep fold and unfold handoffs instant, sharp, and lightweight. The reference implementations remain preserved for future motion exploration.

`TransitionDynamics` is pure Kotlin with an injected timestamp for reproducible tests. It distinguishes initial layout, angle movement, discrete posture changes, surface handoffs, rotation, multi-window resize, and stale resumes.

- Motion target: `a = sin(πp)`; it is zero at closed/flat endpoints and peaks mid-unfold.
- Vsync smoothing: `x += (target − x) × (1 − exp(−dt/τ))`. Progress and rising strength use τ=45 ms; falling strength uses τ=100 ms. This makes smoothing independent of refresh rate.
- A metrics-only cover/inner handoff starts with strength 0.72 and settles on the new live layout. A rotation or multi-window change does not produce this trigger.
- Motion has a 160 ms freshness deadline. Without another movement event, the target decays to zero, including at HALF_OPENED. This prevents persistent blur while reading in tabletop posture.
- Input older than ten seconds is treated as a fresh resume rather than replaying a stale transition.
- Touch, app menus, Settings, reduced motion, and disabled system animators clear the effect immediately. Suppressed samples still update logical posture so ending a drag cannot replay an old handoff.

Rendering on API 33+:

1. `RenderEffect.createBlurEffect` applies anisotropic Gaussian blur, up to 18 dp / 48 physical pixels, stronger perpendicular to the seam.
2. AGSL samples hinge-relative coordinates using exponential zoom `exp(−0.12a)` and a directional parallax offset.
3. A smooth seam mask grows from 0.08 to 0.65 of the relevant screen axis with progress. Five clamped samples make a short directional smear within this band.
4. The mask mixes the displaced signal with the original live layer and adds a small premultiplied-alpha-safe frost tint.
5. Once settled, `renderEffect = null`; no continuously running animation or forced offscreen layer remains.

Every render-layer instance owns its shader uniforms. Both vertical and horizontal hinges are supported. API 31/32 applies Canvas scaling inside `drawWithContent`, then Gaussian blur; it never scales the View's layout or hit-test coordinate system.

The physical panels are separate system-managed surfaces. This implementation renders an optical bridge over the launcher's current scene and preserves its logical content; it cannot keep an OEM-disabled display illuminated or eliminate a compositor blanking interval. It does not capture or replay a cover-screen bitmap.

## Settings Architecture & Component Design System

`LauncherSettings` provides a unified, full-page homescreen preferences surface using a standardized component library (`SettingsComponents.kt`) built on Another Launcher's frosted glass design language:

### Component Design System (`SettingsComponents.kt`)

1. **`SettingGroup`**:
   - Structured frosted container (`Modifier.glass(24, frosted = true)`) grouping logically related settings under an uppercase tracking section header.
   - Automatically renders hairline frosted dividers with 56dp start insets between adjacent rows, maintaining visual alignment with leading icon badges.
2. **`SettingSwitchRow`**:
   - Tonal frosted icon badge (40dp with rounded 12dp corners) using semantic icons (`Glyph.BELL`, `Glyph.SIGNAL`, `Glyph.LOCATION`, `Glyph.SETTINGS`).
   - Title, descriptive subtitle, and standard Material You `Switch`.
   - Used for boolean features including Notification Dots, Search Contacts, Page Indicator Dots, and the Cellular Signal Indicator (which binds to live `READ_PHONE_STATE` status and `prefs.showCellularIndicator` with dynamic lifecycle resume observation).
3. **`SettingSegmentedRow<T>`**:
   - Replaces dropdowns or multi-tap toggles for discrete selections (e.g. Dock Placement: Left / Right; Temperature Unit: Auto / °C / °F).
   - Frosted pill container with animated thumb background (`Modifier.background(MaterialTheme.colorScheme.primaryContainer)`) and haptic feedback.
4. **`SettingNavigationRow` & `SettingActionRow`**:
   - Navigation row renders a leading icon badge, title, trailing value pill, and `Glyph.CHEVRON_RIGHT` indicator.
   - Action row provides high-visibility primary buttons (e.g. "Set Default Home", "Manage Notification Access") paired with live status tags ("Active", "Setup").
5. **`SettingStatTile`**:
   - Compact 2-column or 4-column metric tiles displaying real-time hardware telemetry (display resolution, density, posture, hinge angle, and window size classes).

### Interactive Color Picker & Live Preview (`SettingColorPickerRow`, `ColorPickerModal`)

For UI element tinting (Status Bar & Duo Status Pill), Another Launcher introduces a specialized interactive color picker:
- **Live Preview Anchor**: The top of the modal hosts a live `DuoStatusPill` replica (`9:41 • 5G • 88%` with battery emblem and Wi-Fi/cellular indicators), updating in real time as the user selects presets or adjusts sliders.
- **12 Curated Swatches**:
  - `Auto`: Dynamic wallpaper-adaptive mode with a vibrant multi-color indicator.
  - 11 hand-picked, accessibility-tested swatches: Pure White (`#FFFFFF`), Chalk (`#E2E8F0`), Slate (`#94A3B8`), Mint (`#A7F3D0`), Cyan (`#67E8F9`), Sky Blue (`#7DD3FC`), Indigo (`#A5B4FC`), Lavender (`#C4B5FD`), Rose (`#FDA4AF`), Amber (`#FCD34D`), and Lime (`#BEF264`).
- **Precision HSV Gradient Controls**:
  - Rainbow Hue slider (0°..360°) with custom multi-stop gradient track (`Red -> Yellow -> Green -> Cyan -> Blue -> Magenta -> Red`).
  - Saturation and Value/Brightness sliders with dynamic color tracks derived from current hue.
- **Validated Hex Input**:
  - Live 6-character hex input field with `#` prefix, instant preview update on valid 6-hex entry, and graceful fallback.
- **JVM & Robolectric Decoupling**:
  - Implements pure Kotlin RGB-to-HSV and HSV-to-RGB conversion math in `ColorPickerDefaults`, avoiding `android.graphics.Color` calls in unit tests and preventing unmocked framework crashes.

### Instant Search & Filter Engine

`LauncherSettings` integrates an instant search capsule at the top of the screen:
- Matches user queries against group titles, row titles, and descriptive subtitles.
- Real-time filtering displays matching rows while hiding un-matched groups.
- Matching groups expand automatically, and clearing the search instantly restores full view with zero layout flutter.

## Smart App Categorization Engine & Library Folders (`LibraryFolders.kt`, `AppCategory.kt`)

Another Launcher employs an intelligent, multi-tier app categorization system engineered to maximize classification accuracy and minimize unorganized apps falling into "Other":

### Multi-Tier Classification Hierarchy
1. **User Overrides (`LauncherPreferences.categoryOverrides`)**:
   - Explicit user assignments configured in Settings always take immediate precedence over automated classification.
2. **Android Platform Categories (`ApplicationInfo.CATEGORY_*`)**:
   - Reads framework-level metadata declared in app manifests:
     - `CATEGORY_GAME` $\rightarrow$ Games
     - `CATEGORY_AUDIO`, `CATEGORY_VIDEO` $\rightarrow$ Entertainment
     - `CATEGORY_IMAGE` $\rightarrow$ Creativity
     - `CATEGORY_SOCIAL` $\rightarrow$ Social
     - `CATEGORY_NEWS` $\rightarrow$ Information & Reading
     - `CATEGORY_MAPS` $\rightarrow$ Travel & Navigation
     - `CATEGORY_PRODUCTIVITY` $\rightarrow$ Productivity & Finance
3. **Application Flags & Game Signatures**:
   - Inspects `ApplicationInfo.FLAG_IS_GAME` (safely encapsulated with `@Suppress("DEPRECATION")` for API 37 compatibility) and known game engines (`unity3d`, `unrealengine`).
4. **Publisher & Package Signatures**:
   - Curated mappings for top global apps and services across banking/finance, streaming, social media, shopping, delivery, utilities, and developer tools.
5. **Semantic Keyword & Token Matching**:
   - Tokenizes and analyzes both reverse-domain package names and user-visible display labels for high-confidence domain keywords (e.g., "bank", "crypto", "editor", "vpn", "delivery", "transit").
6. **Substring Collision Guards**:
   - Prevents common substring false positives through strict regex and ordering checks:
     - `ai.perplexity.app.android` (contains `plex`) is correctly assigned to Productivity rather than Entertainment (Plex).
     - `com.ubercab.eats` (contains `uber`) is classified into Shopping & Delivery before transit heuristics run.
     - `com.amazon.kindle` and `com.amazon.mp3` are routed to Reading and Entertainment rather than Shopping.

### Dynamic Folder Organization & Launch Prioritization
- **Category Ordering**: Active categories containing frequently used and larger collections of apps bubble to the top of the 2×2 folder grid. The "Other" category is strictly guaranteed to render as the final card.
- **Card Tile Ranking**: The 3 prominent direct-launch slots in each 2×2 category card are populated by the user's most recently launched apps in that category, with remaining apps ordered alphabetically in the expanded frosted folder overlay.
- **In-Settings Management & Refresh**: Launcher Settings provides a dedicated "Refresh categories" action row with animated status confirmation, allowing users to re-evaluate categories dynamically after app updates, paired with an instant "Reset" button to clear custom overrides.

## Onboarding & First-Run Experience (`OnboardingScreen.kt`)

Another Launcher delivers a guided, foldable-aware onboarding flow for first-time installations:

### Architecture & Lifecycle
- **Preference Persistence**: Tracked via `LauncherPreferences.onboardingCompleted` (DataStore key `onboarding_completed`). On fresh installs with no stored preference, it evaluates to `false`. When restoring existing backups (`BackupCodec`), it automatically evaluates to `true` to avoid re-prompting existing users.
- **Root Screen Selection**: In `LauncherActivity`, `prefs.onboardingCompleted` governs whether `OnboardingScreen` or `HomeScreen` is rendered. Completing the tour invokes `LauncherViewModel.completeOnboarding()`, smoothly transitioning into the homescreen via `AnimatedContent`.
- **Tour Mode Replayability**: Launcher Settings exposes a "Welcome Tour" action row in the "Help & Tour" section. Activating it renders `OnboardingScreen(..., isTourMode = true)`, allowing users to revisit the visual guides and check permission statuses without altering launcher layout or resetting preferences.

### 5-Step Pager Flow
1. **Welcome to Another Launcher**:
   - High-level overview of core pillars: Foldable-Native geometry, Liquid Translucent Glass, and Fluid Workspace Dynamics.
   - Glass cards highlight key advantages over conventional single-screen launchers.
2. **Workspace Architecture**:
   - Visual architectural diagram illustrating the 3-tier horizontal pager structure:
     - **Page 0: Hub**: weather and user-managed native widgets. Search lives in the App Library.
     - **Pages 1..N: Home Spreads**: Dynamic user home pages featuring widgets and favorite apps, automatically pairing as dual-page spreads on wide unfolded displays.
     - **Page N+1: App Library**: Native last page over wallpaper with 2×2 category folder cards, A-Z continuous list, and recent apps.
3. **Power Gestures**:
   - Visual cards explaining widget stacking, 4-way drag resizing with live handles, foldable dual-pane pairing across physical hinge seams, and page manager edit mode.
4. **Permissions & Integrations**:
   - Interactive checklist with real-time status indicators (green checkmarks for granted/active services, frosted setup buttons for pending permissions):
     - **Default Home**: Direct invocation of Android's `RoleManager.ROLE_HOME`.
     - **Notification Dots**: System notification listener settings routing for live red alert badges.
     - **Weather Location**: System runtime permission request (`ACCESS_COARSE_LOCATION`) with immediate Open-Meteo weather location detection.
     - **Contact Search**: Quick dial and contact search integration via `READ_CONTACTS`.
     - **Phone Signal**: Precise cellular network generation (5G+/5G/4G) and signal dot telemetry via `READ_PHONE_STATE`.
5. **You're All Set**:
   - Summary and "Start using Another Launcher" button (or "Return to Settings" in tour mode).

## Extension points

- Adjust normalization thresholds in `FoldMath` only after collecting sensor ranges from the target device.
- Tune the freshness deadline/time constants in `TransitionDynamics`; tune blur cap and AGSL coefficients in `DuoRenderLayer`/`FlowShader`.
- Keep new interactive components inside the existing layout hierarchy. Use the existing root interaction suppression for additional gesture modes.
- Keep widget resizing in provider size options and stored spans; never change widget IDs for a geometry edit. Future interactive edge handles should use the same collision/minimum-size rules.
- Profile the release build on real hardware before selecting stronger blur or additional shader taps.

Shared glass keeps decorative fills at 16–36% opacity, with concentrated reflections. Emphasized text panels retain 68–80% tint for contrast over wallpaper. Hub and Library glass controls use onSurface foregrounds; wallpaper controls use HomeColors.

Library search places ranked apps first on a tinted glass results panel with solid theme-aware labels, a subtle best-match highlight, and secondary contact/web actions after the apps. Check readability against detailed bright/dark photos in both themes, including no results and keyboard-visible states.

## Unified Library search and focus

`HomeScreen` owns the saveable query so opt-in contacts observe the same input as app search. Contact permission refreshes on lifecycle resume, queries run on IO, and contact launches use NEW_TASK. `Workspace` passes a request token into `AllAppsDrawer`; requests are consumed only after the Library is selected, programmatic navigation finishes and the pager settles. Focus is requested on a frame boundary and the keyboard shown on the next frame. Each shortcut tap issues a new token, so reopening after Cancel also works. Offscreen drawers cannot handle Back or retain focus/category overlays. Home intents clear query and pending focus requests; swipe-away dismisses the keyboard.

Ranked app tiles remain local. A separate provider-labeled Web row remains visible for nonblank input even with matching apps, and the IME Search action invokes the same `WebSearch.intent`. Clear keeps focus, while Cancel/Back clears input and dismisses the IME. Calculator, all matching deep Settings shortcuts, and contacts are in the scrolling local results so small windows retain reachable search controls. Add-apps mode remains an app-only modal. No preference schema or widget ID migration is needed. Discover requirements are in [DISCOVER.md](DISCOVER.md).

## Wallpaper edits and disclosures

`WallpaperImageSizing` derives a bounded pixel budget from available heap, preserves aspect ratio and never upscales. `ImageDecoder` applies that target during decode (including image orientation). One Main-owned busy state serializes photo/preset/rotation work on IO/Default; errors retain the selection and re-enable controls. Wallpaper application remains off-thread with Home, Lock and Both targets. The manually opened privacy policy caps width and scrolls its body while keeping its close action reachable. Permission explanations appear inline, with complete text before each feature action. Weather permissions and behavior remain; the explanation describes approximate/precise choice, local persistence/exports, Open-Meteo coordinate requests and Android geocoding rather than claiming network providers never log data. Widget preview production keys include current density for fold/display changes.

Home requests clear the shared library query and wait for the ViewModel's default-page selection before consuming the pager jump. Settled pager positions update selection only after scrolling, outside programmatic navigation and pending Home requests; this avoids letting a stale position overwrite a requested destination during layout changes.

Permission requests are user-initiated directly from onboarding actions and Settings controls. `PermissionExplanation` supplies visible, untruncated in-context data-handling text before the OS prompt; the optional privacy policy remains manually accessible. Location keeps approximate/precise choices, provider/backup disclosures and manual city search. Notification access still opens Android settings; contacts only become enabled after permission is granted, and denial leaves the switch off and retryable. No permission requests launch from composition, navigation, or dismissal.
