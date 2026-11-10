# Another Launcher Development Guidelines & Invariants

## 1. Multi-IDE Synchronization & Workspace State
- **Check Workspace Before Modifying**: Run `git status` or inspect diffs before editing. Work may be changing in another IDE; preserve concurrent edits.
- **Documentation & Roadmap Synchronicity**: Keep documentation in sync when features, layouts, or architecture change:
  - `ROADMAP.md`: Update milestones, current state, and near-term backlog.
  - `README.md`: Update feature descriptions, version numbers, and test counts.
  - `docs/ARCHITECTURE.md`: Document architectural patterns, codec schemas, lifecycle hooks, and rendering shaders.
  - `docs/TESTING.md`: Update automated coverage counts and test scenarios.
- **Atomic Git Commits**: After verification and user approval, commit each cohesive change separately.

## 2. Testing & Quality Invariants
- **Verification Commands**: Run `./gradlew :app:testDebugUnitTest :app:lintDebug` before completing any significant feature or refactor.
- **Lint Review Policy**: Require zero Android lint errors. Review warnings for crashes, data loss, privacy, lifecycle, and fold continuity; fix material problems and document intentional tradeoffs. Style suggestions, duplicate legacy icon assets, obsolete guards, and tool update notices do not independently block a release. Do not suppress warnings merely to reduce the count or remove working features to satisfy lint.
- **Accurate Test Metrics**: Document exact test counts from test runner outputs (e.g., 101 JVM unit tests, 6 device tests), never approximate or guess.
- **Test Integrity**: When modifying layouts, resizing, or page management, ensure tests in `PageManagementTest.kt`, `HomeLayoutTest.kt`, and `WidgetSizingTest.kt` pass and cover edge cases (boundary clamping, undo, reordering).
- **Local Device Deployment & ADB**: `adb` is located at `~/Library/Android/sdk/platform-tools/adb`. When pushing builds to an attached physical device, target the hardware serial (e.g., `-s <serial>`) to resolve ambiguous USB/Wi-Fi endpoints, install with `install -r`, and launch `com.wiselion.anotherlauncher/com.anotherlauncher.LauncherActivity`.
- **Back Up Before Device Changes**: Before installing over, uninstalling, clearing data, restoring, or running device QA that could affect an existing launcher layout, use the installed app’s Settings → Backup and restore → Export backup. Save the JSON to Downloads and verify it is nonempty, valid, and contains the expected home pages and favorites. Never remove the Play-installed app to bypass a signing mismatch. A separately identified QA package has separate storage and starts with a fresh layout.
- **Device Screenshot Capture**: Do not use `adb exec-out screencap -p > file.png` as stream encoding on macOS shells can corrupt the PNG binary stream. Always use `adb -s <serial> shell screencap -p /sdcard/<name>.png && adb -s <serial> pull /sdcard/<name>.png <local_path>` to ensure clean artifact captures.
- **Respect User Approval**: When the user approves a visual state, stop probing/tapping and finalize the task.
- **Build Environment (macOS)**: When system Java is not linked in default `PATH`, use Android Studio's bundled JBR runtime via `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

## 3. Foldable & Hardware Architecture Guardrails
- **No Hardcoded Resolutions**: Derive geometry from `WindowMetrics`, `FoldingFeature`, and display cutouts; never hardcode device resolutions.
- **Fold Continuity & Power Management**: Fold closing transitions from unfolded states must maintain display continuity (via `wakeCoverScreen`, dynamic `setTurnScreenOn(true)` / `setShowWhenLocked(true)`, and `PowerManager.SCREEN_BRIGHT_WAKE_LOCK`).
- **Responsive Workspace Centering**: Default grid cells cap at `WidgetSizing.MAX_CELL_DP = 100dp`. The optional wider inner-portrait layout uses a 140dp cap and edge alignment; the default inner layout centers the 4×6 grid.

## 4. Native Widget Host & Layout Engine
- **Preserve Real Widget Bindings**: Stacking, dragging, page reordering, and resizing must never destroy or duplicate widget IDs.
- **Zero-Padding Host Scaling**: `LauncherWidgetHostView` removes framework duplicate padding; scaling uses uniform matrix transformation to preserve touch coordinates.
- **Live Drag Resizing**: Widget resizing must use live drag handles with `positionChange()`, a 12% hysteresis deadband to prevent flutter, and haptic feedback (`TextHandleMove`).
- **Strict Layout Codec Versioning**: `HomeLayoutCodec` must remain backward-compatible with older schemas (v2 -> v5) and fail closed on corrupted data without overwriting persistent storage.
- **Exact Page Bounds (No Ghost Pages)**: Home spreads must match the actual home-page mapping, with only the dedicated Hub and Library slots added. Never append dummy Add page cards. Create pages through `PageManagerScreen` or the edit dock; use `PagerMap` for narrow/wide mapping.
- **Default Home Page Continuity**: Default home page selection is persisted by unique ID in `HomeLayoutCodec`, fails safe to the first remaining page if deleted or corrupted, and governs both system Home intents (`homeRequest`) and secondary-page Back gesture dispatch.
- **Three-Tier Workspace Pager Architecture**: The workspace horizontal pager strictly implements a 3-tier structure:
  - **Page 0**: dedicated widget Hub with existing weather and user-managed native widgets; no search field.
  - **Pages 1..N**: Dynamic user home spreads (`spreads = 1..N`).
  - **Page N+1**: App Library rendered natively over wallpaper as the last page, combining ranked apps, explicit provider-labeled web search, calculator, Settings and optional contacts. The Search shortcut uses a magnifier and exact label Search, focusing/opening the IME only after the page settles.
  - Derive pager bounds from `PagerMap`; wide layouts can pair Hub with Home 1.
- **App Library Search Dropdown & A-Z Toggle**:
  - Recent apps are hidden during normal library browsing and presented in a frosted glass dropdown modal (`Modifier.glass(20, frosted = true)`) under the search capsule only when search is focused with an empty query.
  - When search is active, an animated "Cancel" button replaces the A-Z toggle; tapping Cancel or pressing system Back collapses the dropdown modal and unfocuses search.
  - The view toggle beside the search capsule is a 50dp frosted glass square button with an inner rounded square "A-Z" badge switching between 2×2 grouped category cards and a continuous alphabetical list (do not introduce pill tab buttons like "Categories" / "A-Z List").
- **Intent & Navigation Flags**: All external system intents launched from the launcher context (system Settings, Samsung Dressroom, Notification Listener access, Default Home Role Manager) must specify `Intent.FLAG_ACTIVITY_NEW_TASK` to prevent runtime exceptions and back stack corruption.
- **Samsung Wallpaper and Style Integration**: Samsung Wallpaper & style opens via public intent `com.samsung.intent.action.WALLPAPER_SETTING` with fallback to system Settings. Do not declare privileged signature permissions such as `WRITE_SECURE_SETTINGS`.
- **Workspace Pager State Synchronization**: Never write pager selection to the ViewModel/storage from synchronous `SideEffect`: stale positions can cancel navigation. Observe settled states (`snapshotFlow { if (pager.isScrollInProgress) null else pager.settledPage }`) inside a `LaunchedEffect`, guarded by a `navigating` flag during programmatic scrolling.
- **Hub Widget Canvas & Circular Add Button**: Keep widgets below weather without a WIDGETS/+add header. Add widgets through a 54dp frosted glass circular `+` button (`Modifier.size(54.dp).glass(27, frosted = true)`) styled with a 90° drop-shadowed white `Glyph.PLUS`. In the occupied state, it renders centered below the widget grid; in the empty state, it renders centered on the canvas.

## 5. Visual Design & Theme Principles
- **Material You & System Theme**: Match dynamic light/dark palettes; honor wallpaper luminance and contrast shadows for text.
- **Frosted Glass**: Use `Modifier.glass(..., frosted = true)` for liquid translucent surfaces with top-lit specular gradient borders.
- **Sheet & Overlay Contrast Invariant**: In dark mode, text, icons, and close glyphs inside frosted glass sheets (category folder overlays, dropdown modals, settings) must be light/white (`#F8FAFF`), while in light mode they use dark theme tokens.
- **No Scrim Dimming on Library Overlays**: When opening category folder sheets in the App Library, do not place dark scrims (`Color.Black.copy(alpha = 0.5f)`) behind the sheet; maintain natural wallpaper and library brightness.
- **High-DPI Assets**: App icons must be fetched using `launcherLargeIconDensity` and `launcherLargeIconSize` (up to 384px) with `FilterQuality.High`.
- **Notification Alert Dots**: Must be solid red (`Color.Red`) with zero border stroke (`Modifier.size(10.dp).background(Color.Red, RoundedCornerShape(50))`). Avoid theme error container colors or surface outline borders.
- **Homescreen Drop Shadows (90° Invariant)**: All drop shadows across homescreen controls (search button icon, Duo status pill, time text, page indicator dots, and wallpaper labels) must be strictly 90 degrees (`Offset(0f, y)`).
- **Drop Shadows Only on Wallpaper**: `HomeLabel` drop shadows apply exclusively to items rendered directly over wallpaper (`onWallpaper = true`). Inside sheets, cards, modals, and dropdowns, drop shadows must be disabled (`onWallpaper = false`).
- **Light Mode Homescreen Chrome & Drop Shadow Contrast**: In light mode, homescreen chrome controls (time, `DuoStatusPill`, page indicator dots, search icon) do not invert to dark/black text; they remain pure white (`Color.White`) against wallpapers. To maintain legibility over light wallpapers, drop shadows must have low blur spread (tighter radius, e.g. 1dp–1.5dp) and sufficient opacity so the shadow is clearly noticeable without appearing fuzzy or washed out.
- **Charging Indicator Invariant**: The charging status indicator must use Apple systemGreen (`#34C759`), 100% solid opacity (no alpha transparency at any point in the size pulse animation), and must never have a drop shadow.

