# Another Launcher — Project Roadmap & Implementation Plan

A comprehensive roadmap and tracking document for **Another Launcher**, a modern, foldable-first Android launcher engineered for fluid multi-display ergonomics, hardware-adaptive layouts, and live-content unfold dynamics.

**Current source (production access approved):** `0.7.0-beta.5` (version code `12`)

**Target Platform:** Android 17 (API 37) | **Minimum:** Android 12 (API 31)

**Test Suite:** 219 JVM unit tests; five streamlined-permission cases passed individually on the isolated QA install. Earlier device results are recorded in `docs/TESTING.md`. Android lint passes with zero errors.

---

## Local state-machine experiment

- [x] Inventory stateful workflows and their ownership in [STATE_MACHINES.md](docs/STATE_MACHINES.md).
- [x] Make foreground navigation and widget setup explicit; consume drag completion before callbacks.
- [x] Add 25 regression tests for transition sequences, cancellation, identity and callback reentrancy.
- [ ] Run the affected Home/Settings return paths, provider consent/configuration and recreation matrix on a device before accepting the experiment.
- Keep remaining async-work risks documented; no wholesale rewrite of the tested layout or fold engines.

## 1. Project Vision & Core Principles

- **Foldable-First Architecture:** Rather than treating foldables as phones that occasionally get wider, Another Launcher treats dual-panel geometry, variable aspect ratios, and continuous hinge angles as first-class inputs.
- **Short-and-Wide Form Factor Focus:** Purpose-built for modern wide-aspect foldables (e.g. Samsung Galaxy Z Fold 8, Huawei Pura X Max). These devices feature wider booklet cover screens (~19.5:9 to 20:9) and expansive near-square inner displays (~4:3 to 6:5). Ergonomics emphasize lateral thumb zones, natural dual-pane spreads across the hinge, floating side-rail ergonomics, and fluid side-by-side app pairing.
- **True-to-Hardware Geometry:** Zero hardcoded device resolutions. All layouts adapt dynamically from WindowManager metrics, physical hinge bounds, and display cutouts.
- **Native View Preservation:** Widgets run in real `AppWidgetHostView`s with precise zero-padding measurements and scale fitting. Widget bindings are durable across fold/unfold, rotation, reordering, and stacking.
- **Zero Bloat & Direct Control:** Clean Material You integration, ultra-responsive tactile drag interactions, and privacy-respecting local operations (no background analytics, no cloud telemetry).

---

## 2. Completed Milestones

### Milestone 1 — Core Foundation & Live Unfold Engine (`v0.1.0`)
- [x] Initial AGP 9.4.0 + Compose + Kotlin 2.4.20 setup with API 37 target.
- [x] Single-layer live AGSL render effect for hardware-synchronized unfold flow.
- [x] Continuous hinge sensor tracking via `Sensor.TYPE_HINGE_ANGLE` and `WindowInfoTracker`.
- [x] Fallback Gaussian blur and scaling matrix for API 31/32 devices.

### Milestone 2 — Dual-Spread Workspace & Adaptive Dock (`v0.2.0`)
- [x] Dual-page spread on wide inner displays with safe gutter reservation across physical hinge seam.
- [x] Multi-position favorites dock (Side rail on cover/landscape, bottom dock on inner portrait).
- [x] Auto-handedness rotation detection (0°/90° right edge, 180°/270° left edge).
- [x] Up to 6 favorite dock pins with drag reordering and boundary clamping.

### Milestone 3 — Built-in Dashboard & Widget Stacking (`v0.3.0`)
- [x] Hub weather card (Open-Meteo REST API, lifecycle-aware refresh, offline cache).
- [x] Built-in 2×2 Analog Clock drawing local time from shared minute-tick telemetry.
- [x] Real-time widget stacking: combine same-size widgets/apps into swipeable stacks while preserving underlying Android widget bindings.
- [x] Grouped widget catalog filtering for home-screen-compatible providers with live previews.

### Milestone 4 — True-Content Widget Host & Samsung Optimization (`v0.4.0`)
- [x] Custom `LauncherWidgetHostView` removing duplicate framework padding for pixel-accurate content alignment.
- [x] Direct native touch coordinate transformation on scaled widgets.
- [x] Samsung Weather size reporting (`semAppWidgetColumnSpan` / `semAppWidgetRowSpan`) and aspect-ratio preservation.
- [x] Widget pickup gesture separation (`claimGestureForLauncher` with `ACTION_CANCEL`) allowing native vertical scrolling without false drag triggers.

### Milestone 5 — Smart Reflow, Local Categories & Global Search (`v0.5.0`)
- [x] Predictive grid reflow during icon drag: nearby items slide to nearest free cells via Manhattan distance calculation.
- [x] Drag-out from inline App Library/search results hands the pointer stream to the home root, hides search/IME, and reveals the last valid home page without cancelling the drag.
- [x] Ranked global search (apps, contacts opt-in via `READ_CONTACTS`, and Google Play fallback for unmatched App Library terms).
- [x] App categorization engine with per-app user overrides in Settings.

### Milestone 6 — Live Drag Widget Resizing & Hardware Handoff (`v0.6.0`)
- [x] **Live Interactive Widget Resize Handles:** Replaced static size picker dialog with natural drag handles (`WidgetResizeOverlay`):
  - Directional edge handles for single-axis widgets; corner handles for dual-axis widgets.
  - Floating "$W \times H$" size pill indicator above widget.
  - Multi-directional resizing supporting left and top edge expansion with origin coordinate shifts (`expandParams`/`shrinkParams`).
  - Haptic feedback (`TextHandleMove`) on cell threshold crossing with 12% hysteresis deadband to eliminate flutter.
- [x] **Cover Screen Continuity & Wake Lock Handoff:**
  - Dynamic `setTurnScreenOn(true)` and `setShowWhenLocked(true)` on Android 8.1+.
  - `PowerManager.SCREEN_BRIGHT_WAKE_LOCK` acquired during fold-to-shut transitions to prevent Galaxy Z Fold from entering deep sleep when closing from launcher.
  - Handoff detection via `FoldStateTracker` (`angle <= 75f` and display change listener).
- [x] **One UI Wallpaper Integration:** Direct link to Samsung "Wallpaper and style" via public intent `com.samsung.intent.action.WALLPAPER_SETTING` with graceful fallback to system Settings.

### Milestone 7 — Default Home Page, Frosted Glass & Telephony Polish (`v0.7.0-beta.3` — Current)
- [x] **First-Run Setup & Top Widget Call-to-Action (`WIDGET_PROMPT`):**
  - Initial home layout presents two 2×2 liquid frosted glass widget prompt cards across Row 0 with 4 apps cleanly placed on Row 2.
  - In-place binding: picking a square 2×2 widget binds that slot while preserving the sibling prompt for a second square widget.
  - Sibling prompt reflow: picking a wide 4×2 rectangle widget automatically clears the sibling prompt slot so the rectangle widget spans cleanly across the top.
  - Edit-mode dismissal: each prompt card features a top-right `RemoveAppButton` (`X`) so users can easily clear prompt slots.
- [x] **Default Home Screen Designation & Clean Workspace Bounds:**
  - Designate any home page as the default home screen via dedicated Home icon button in Pages manager (`PageManagerScreen`) or edit toolbar.
  - Schema 4 layout persistence (`defaultPage` key and widget provider metadata in DataStore JSON).
  - Clean bounds enforcement: exact horizontal pager bounds matching active spreads with zero ghost or dummy trailing cards.
  - System back gesture dispatches return to default home screen before exiting.
- [x] **AOSP & Samsung One UI Gesture Navigation Handshake (`GestureNavContract`):**
  - Promptly acknowledges `gesture_nav_contract_v1` and sends end position reply to prevent system gesture timeouts and `onAnimationCancelled` bounce-backs.
  - Eliminated main-thread icon cache evictions during `onResume` transition window to maintain 60/120fps gesture handoff.
  - Synchronized `homeRequest` across `LauncherActivity`, `HomeScreen`, and `Workspace` for reliable return-to-default-home navigation.
  - Enforced `stateAlwaysHidden` soft input mode and active focus clearance on home navigation, app launch, and pager scrolling to prevent spurious keyboard popups.
- [x] **Schema 4 Persistence & Orphaned Widget Binding Repair:**
  - Persist widget provider identity (`widgetProvider` / `widgetLabel`) in Schema 4 JSON (`wprov`/`wlabel` keys) while retaining backwards compatibility with schemas 2–3.
  - Orphaned widget binding recovery: layout tiles are never deleted when system widget bindings are broken by host reinstall or package changes.
  - Transient grace period and animated, auto-dismissing repair notice banner with in-place binding replacement (`replaceWidgetBinding`) via Quick Menu and widget catalog.
- [x] **Backup, Restore & Migration (`BackupCodec`):**
  - Full portable JSON backup and restore via Storage Access Framework (`CreateDocument` / `OpenDocument`).
  - Preserves home pages, folders, stacks, widget positions with provider metadata, dock pins, categories, and settings.
  - Atomic DataStore restore with rollback protection and corrupt file rejection.
- [x] **Streamlined Home Options Dock:**
  - Refined to 5 primary actions: Widgets, Wallpaper, Pages, Edit, Settings.
  - Removed redundant "Apps" button shortcut (managed via dedicated Library button, swipe-up, and '+' placement buttons).
  - Enlarged touch targets (66dp width, 78dp height) with refined typography and spacing.
- [x] **Dedicated Launcher Settings & Homescreen Options App:**
  - Registered `SettingsActivity` with `MAIN` + `LAUNCHER` intent filter under label "Another Launcher".
  - Refined `AppRepository` app filtering to include `SettingsActivity` in the App Library, A-Z list, search, and category cards (Utilities).
  - Implemented seamless routing via `ACTION_SHOW_SETTINGS` to immediately open the full `LauncherSettings` surface without UI flicker.
- [x] **Cell Size Cap & Workspace Centering:**
  - Added `WidgetSizing.MAX_CELL_DP = 140dp` cap to prevent awkward icon stretching on ultra-wide unfolded displays.
  - Centered grid with horizontal and vertical breathing room on inner displays.
  - Extended Home-options long press to unobstructed wallpaper across the full display while preserving pager, grid, widget, control, and Dock gestures.
  - Removed physical-hinge row segmentation from home pages so unfolded portrait uses one continuous centered 4×6 grid.
  - Standardized the App Library launcher on the grid glyph and moved its dock-height unfolded-portrait treatment to the independent bottom-left corner.
  - Simplified Dock placement to explicit Left / Right sides, removing redundant Auto option; bottom dock continues to apply on unfolded portrait.
- [x] **Ultra-Crisp High-DPI Icons:**
  - Updated `AppRepository` to fetch `launcherLargeIconDensity` / `launcherLargeIconSize` (up to 384px / 72dp target).
  - Rendered with `FilterQuality.High` to eliminate icon blurriness on high-density displays.
- [x] **Liquid Frosted Glass Styling:**
  - Added `frosted = true` parameter to `Modifier.glass(...)` in `Design.kt`; the shared treatment now layers palette-specific translucent density, curved top-left and lower reflections, plus inner and outer specular rims tuned independently for light and dark mode.
  - Applied to Favorites Dock and App Library button.
- [x] **Telephony Display Info, Cellular Signal Dots & Polished Device Controls:**
  - `StatusMonitor` listens to `TelephonyCallback.DisplayInfoListener` decoupled from phone state permissions (via the Android 12+ API 31 permission waiver).
  - Accurately formats and displays network badges (5G+, 5G, 4G, 3G, 2G, 1x) in the center of `DuoStatusPill` whenever Wi-Fi is disconnected or inactive.
  - Multi-cell signal level extraction across `cellSignalStrengths` supporting 5G NSA topologies (where `mNr` is idle while LTE carrier operates at full bars) to ensure all 4 cellular dots reflect true network quality.
  - Synchronous initial signal strength and network type reads on startup, eliminating unlit 0-of-4 dot flutter.
  - Iconic Apple systemGreen charging indicator (`#34C759`) with gentle pulsing dot and zero drop shadow.
  - Polished frosted `DeviceStatusDialog` popup adhering strictly to the Sheet & Overlay Contrast Invariant (`#F8FAFF` text/glyphs in dark mode) with dedicated custom vector icons and a rich "Mobile data" tile with signal strength and quick permission onboarding.
  - Moved pager fade/scale state reads into render layers, made Home requests snap before the system transition completes, and deferred widget-host refresh work beyond the first resume frame.
- [x] **Notification Alert Dots:**
  - Solid red alert dot styling (`Color.Red`, 10dp circle) for clean visibility on app tiles.
- [x] **Dedicated Hub (Page 0 Today View):**
  - Dedicated page left of Home 1 featuring an interactive frosted-glass search capsule (`Modifier.glass(25, frosted = true)`) with a repeating overflow marquee for the empty prompt, 1-tap clear, and live weather.
  - Deep system search integrating Google web search, 21 deep system settings shortcuts, contacts search with avatar launch, and inline arithmetic evaluation.
  - Dedicated Hub widget canvas with native Android widget hosting, direct picker destination, adaptive occupied-row height, full 4×6 edit surface, drag reflow, resize/configure/remove actions, orphan repair, and backup/restore persistence via layout Schema 5.
- [x] **Native App Library as Last Page (Page N+1):**
  - Converted the App Library from an opaque modal container drawer into the native last page of the horizontal pager, rendered directly over system wallpaper with liquid frosted glass cards.
    - Features dynamic Recent Apps tracking nestled in a frosted glass dropdown modal when tapping search, 2×2 category folder cards with expanding frosted sheets, a square "A-Z" button toggle seamlessly switching to a continuous alphabetical app grid (without redundant pill tab buttons), and instant drag-and-drop out of library.
  - Seamless bidirectional horizontal swipe gestures, system Back gesture return to default home, and dock/rail button toggle.
- [x] **Single A–Z Add Apps Picker & Homescreen Grouping:**
  - Modernized "Add apps" modal into a single unified A–Z list/grid of all apps without category splits or category chips, with liquid frosted glass styling (`Modifier.glass(28, frosted = true)`), "+ Add" / "✓ Remove" badges, and instant search filtering.
  - Drag an app onto another app to form a folder; app and folder targets stay stationary during hover.
  - Non-grouping collisions retain live reflow previews and vacated-cell placement.
  - Grouping also works on full 24-slot grids, across pages and from the library; existing folders accept additional apps.
  - Tap an open group’s title to rename it; + opens app selection and A–Z persists locale-aware alphabetical order.
  - Instant outside-tap dismissal: touching empty rail areas (between the top-right status module and dock, or between the dock and bottom app button), empty space in the bottom dock bar, or the app library button immediately dismisses the Add Apps modal without forcing back button usage.
  - Bottom indicator row features the Hub search pill on left, home spread dots in center, and App Library pill on right (with 90° drop shadow).
- [x] **Native System Wallpaper & Custom In-App Wallpaper Picker:**
  - Direct presentation over native Android/Samsung system wallpaper with `FLAG_SHOW_WALLPAPER`.
  - Added custom frosted glass `WallpaperPickerSheet` supporting direct photo gallery selection (`GetContent`), live in-app preview, target selection (Home/Lock/Both), and 6 built-in high-res HD preset themes without 3rd-party chooser popups.
  - Replaced wallpaper mode toggle chips in Settings with a direct wallpaper launcher shortcut (`Glyph.WALLPAPER`).
  - Fully backward compatible: existing user preferences and legacy JSON backups decode cleanly without errors.
- [x] **Standardized Settings UI/UX & Interactive Color Picker:**
  - Unified settings design system (`SettingsComponents.kt`) using liquid frosted glass containers (`SettingGroup`), themed switches (`SettingSwitchRow`), segmented pill toggles (`SettingSegmentedRow`), navigation rows (`SettingNavigationRow`), action buttons (`SettingActionRow`), and hardware telemetry tiles (`SettingStatTile`).
  - Interactive Color Picker (`ColorPickerModal`, `SettingColorPickerRow`) for UI element tinting (Status Bar / Duo Status Pill) with live status pill preview (`9:41 • 5G • 88%`), 12 curated swatches including Dynamic Auto/wallpaper adaptive, pure Kotlin HSV rainbow hue slider (0°..360°), saturation and value/brightness sliders, and validated hex entry.
  - Instant search and keyword filtering in Settings across section headers, titles, and subtitles with auto-expanding matches.
- [x] **First-Run Onboarding Flow & Permissions Wizard (`OnboardingScreen`):**
  - Elegant 5-step horizontal pager (`OnboardingScreen.kt`) presented automatically on fresh installations:
    - **Step 1: Welcome to Another Launcher**: Overview of foldable-native architecture, liquid frosted glass aesthetic, and fluid workspace dynamics.
    - **Step 2: Workspace Architecture**: Visual diagram explaining the 3-tier horizontal pager (Hub Today View on Page 0, User Home Spreads on Pages 1..N, App Library on Page N+1).
    - **Step 3: Power Gestures**: Interactive guide to widget stacking, live 4-way drag resizing, dual-pane spread pairing on inner displays, and page manager edit mode.
    - **Step 4: Permissions & Integrations**: Interactive permissions checklist with real-time status detection and inline action triggers for Default Home Role (`RoleManager`), Notification Dots (`NotificationListenerService`), Weather Location (`ACCESS_COARSE_LOCATION`), Contact Search (`READ_CONTACTS`), and Phone Signal/Telephony (`READ_PHONE_STATE`).
    - **Step 5: You're All Set**: Final readiness check, quick tips, and smooth animated transition into the homescreen.
  - Persistent state tracked via `LauncherPreferences.onboardingCompleted` (backed by DataStore), ensuring first-run presentation while existing backups safely mark onboarding complete.
  - **Re-playable Welcome Tour**: Accessible at any time from Another Launcher Settings under a dedicated "Help & Tour" section (`LauncherSettings`), running the onboarding wizard in tour mode without overriding user preferences or resetting home state.
- [x] **Smart App Categorization Engine & Library Settings Refresh:**
  - Multi-tier app classification engine (`LibraryCategory.of`, `AppCategory.classify`) combining Android platform categories (`ApplicationInfo.CATEGORY_*`), `FLAG_IS_GAME` metadata, publisher signatures, and semantic keyword matching on app labels and package tokens to minimize unclassified apps falling into "Other".
  - Dedicated category definitions: Creativity (Photo & Video), Entertainment (Streaming & Media), Shopping (Shopping & Food Delivery), Games, Social, Productivity, Utilities, Travel, and Health.
  - Substring collision guards ensuring precision mapping (e.g., Perplexity AI to Productivity, Uber Eats to Food/Shopping, Kindle to Reading/Productivity, Prime Video/Music to Entertainment).
  - Smart category card ordering in App Library: active categories and recently launched apps lead the grid; "Other" is always relegated to the final card slot.
  - Category refresh & override reset: added a high-visibility "Refresh categories" action row in Another Launcher Settings (`LauncherSettings`) with live status confirmation, accompanied by an instant "Reset" overrides button to clear custom user mappings.
- [x] **Fold Blur Animation Removal & Instant Display Continuity:**
  - Removed experimental Duo unfold blur effect (`duoUnfoldLayer`, `RenderEffect` Gaussian blur, and AGSL flow haze) from the active `HomeScreen` layout and `LauncherActivity`/`LauncherViewModel` lifecycle.
  - Folding and unfolding the device now provides clean, instantaneous display handoffs without screen blur, smearing, or background Choreographer vsync overhead.
  - Display continuity and wake lock management (`wakeCoverScreen`) remain strictly preserved.
- [x] **Automatic Startup Weather Location Detection:**
  - Fast-path query against `LocationManager.FUSED_PROVIDER` checking cached `getLastKnownLocation` (< 4h) before fallback to active location sweep.
  - Automatically triggered on launcher startup in `LauncherActivity` and `LauncherViewModel` so the Hub weather card is fully populated immediately out of the box when permission has already been granted.
- [x] **Google Play Developer Program Policies Compliance:**
  - Purged privileged `WRITE_SECURE_SETTINGS` from `AndroidManifest.xml` to satisfy Google Play restricted permission rules.
  - Added prominent explanations for Location, Contacts, Phone State, and Notification Access. These now appear inline before the feature action across Onboarding and Settings.
  - Integrated in-app Privacy Policy dialog (`PrivacyPolicyDialog`) and published standalone `PRIVACY_POLICY.md` covering zero-telemetry, local-first data processing, and Open-Meteo weather API usage.
  - Eliminated unsolicited startup location permission popups on first launch.
- [x] **Walkthrough Overlay Background Blur, Light Mode Contrast & Hero Icon:**
  - Official app icon hero: replaced the generic grid icon (`Glyph.APPS`) on Page 0 with the native high-resolution app icon (`R.drawable.ic_launcher`, 84dp metallic squircle "A").
  - Dynamic theme-aware contrast for `OnboardingScreen`: converts all white typography, icon tints, and pill indicators to dark theme tokens (`MaterialTheme.colorScheme.onSurface`, `onSurfaceVariant`) in light mode, preventing text washout over light wallpapers and cards.
  - Full-page overlay background blur in `HomeScreen`: applies animated Gaussian blur (`28.dp`) to the underlying workspace (`home-root`) when full-page overlays or the Welcome tour are active, eliminating widget, clock, and icon bleed-through.
  - OS window wallpaper blur on Android 12+ (`FLAG_BLUR_BEHIND`, `36.dp`) in `LauncherActivity` during full-page display.
  - Contrast-tuned tour mode scrim (82% white in light mode, 72% black in dark mode).
- [x] **Open-Source Credits & Settings Cleanup:**
  - Removed inactive placeholder GitHub project link from Launcher Settings and Privacy Policy.
  - Implemented dedicated Open Source Credits dialog (`CreditsDialog`) in `LauncherSettings` highlighting open-source repositories (`jakesgoodapps/DuoLauncher`, `berkaycatak/adaptive_platform_ui`) whose architectures, widget host sizing, and liquid glass styling noticeably shaped the launcher.

---

## 3. Near-Term Roadmap & Next Priorities (`v0.8.0+`)

### Production-readiness refinement (October 12, 2026)
- [x] Consolidate web, calculator, system Settings and optional contact search into the App Library alongside existing app ranking.
- [x] Search glyph/accessibility label, deferred focus and IME request after pager settlement, repeated taps, clear/Cancel/Back and Home behavior.
- [x] Widget-only Hub with existing weather, native widget management and explicit Hub picker destination on wide screens.
- [x] Bound wallpaper decode memory and serialize off-thread edits; retain photo, presets, rotation and Home/Lock targets.
- [x] Keep accurate location/privacy information and density-aware widget previews. Permission explanations now appear inline before the feature action, eliminating the extra disclosure confirmation tap.
- [x] Investigate genuine Discover overlay support; document the missing compatible client/accepted caller in [DISCOVER.md](docs/DISCOVER.md).
- [ ] Integrate and validate an accepted genuine Discover overlay client before adding a persistent Hub/Discover choice. No generic web view or inactive selector is shipped.
- [x] Build and verify the signed version-code 12 bundle for the streamlined optional-permission flow.
- [ ] Submit a reviewed production release; approval for production access does not publish a release.

Earlier milestones below describe the implementation at their respective checkpoints; the widget Hub and unified Library search supersede the old Hub-search design.

### Earlier 0.7.0-beta.4 closed-test feedback (historical)
- [x] **Inner-screen layout choice:** Keep the centered portrait grid and bottom dock as the default; offer a wider, edge-aligned grid with a side dock. Visual QA passed on the separate beta.4 build. Real-widget interaction in this layout remains a release check.
- [x] **Persist App Library view preference:** Save the A–Z or category choice across folding, reopening, and restart. The developer confirmed A–Z survives folding on beta.4.
- [x] **Cover-screen spacing and page dots:** Move the home grid toward the bottom controls and improve dot visibility. Check spacing and contrast on varied wallpapers during closed testing.
- [x] **Web search provider choice:** Offer Google, DuckDuckGo, Brave, and Startpage for App Library web search while keeping local results on device. A DuckDuckGo launch was checked on hardware; the other providers have URL tests and await live checks.
- [ ] **Play release verification:** Install the Play-signed beta.4 update over beta.3, confirm existing layouts and widget bindings survive, then invite the full tester group. Export and verify an in-app layout backup before device changes. Gather cover-screen, widget, search, and one-handed-use feedback.
- [ ] **Later customization:** Consider a validated custom web-search URL and adjustable page-indicator color/visibility after closed-test feedback.

### Phase 8 — Short-and-Wide Ergonomics & Dual-Pane Multi-Tasking (`v0.8.0`)
- [ ] **One-Finger Wallpaper Swipe-Down for Notifications:** Expand the notification shade / quick settings panel (`expandNotificationsPanel`) via a fluid one-finger downward swipe anywhere on empty wallpaper, eliminating awkward top-edge reaches on booklet cover screens and wide inner canvases.
- [ ] **Double-Tap Empty Space to Sleep:** Quick screen lock via `AccessibilityService` upon double-tapping unobstructed wallpaper, preventing the need to reposition hands to reach edge power buttons on wide booklet hardware.
- [ ] **Pinch-to-Edit Workspace:** Natural two-thumb or pinch gesture across any home spread to seamlessly zoom out into `PageManagerScreen`.
- [ ] **Wide-Display App Pairs (Split-Screen Shortcuts):** Create and pin paired app shortcuts to the home screen or dock; tapping an app pair on the inner unfolded display immediately launches both apps side-by-side in 50/50 dual-pane multi-window mode, taking full advantage of expansive squarish screens.

### Phase 9 — Icon Pack Engine & Visual Theming (`v0.8.5`)
- [ ] **Standard Icon Pack Support:** Parse and render Play Store icon packs using the industry standard `appfilter.xml` specification, with high-DPI scaling (up to 384px) to look razor-sharp on high-PPI wide foldable displays, along with per-app manual icon overrides.
- [ ] **Dynamic Monochrome / Themed Icon Fallback:** Auto-generate consistent Material You monochrome tinted icons for third-party apps lacking adaptive monochrome assets.
- [ ] **Grid Density & Label Scaling:** Adjustable slider in Settings for icon sizing (80% to 120%) and label visibility toggle (clean icon-only minimal look), respecting the selected home layout’s grid cell cap.
- [ ] **Smart Folder Auto-Naming & Mini-Grid Previews:** Auto-suggest folder names based on the dominant app category of combined items, with selectable 2×2 or 3×3 mini-grid previews.

### Phase 10 — Lateral Dock Ergonomics (`v0.9.0`)
- [ ] **Adaptive Lateral Dock & Rail Pinning:** User-selectable dock capacity (4 to 6 pins) with adaptive spacing, optimized for thumb comfort in portrait (bottom dock) and landscape/cover (side rail).

### Phase 11 — Hinge Dynamics & Flex Mode Experience (`v1.0.0 Release Candidate`)
- [ ] **Tabletop / Flex Mode Layout:** Adaptive layout when the device is half-folded (75°–115°), presenting widgets and glanceable Hub telemetry on the upright upper half, and interactive controls, recent apps, or the dock on the flat tabletop lower half.
- [ ] **Display Transition Frame-Pacing:** Polish the 120Hz LTPO render pipeline to ensure zero dropped frames during rapid fold-and-unfold transitions on variable-refresh-rate foldable panels.

---

## 4. Architectural Verification Matrix

| Component | Test File | Key Coverage |
| :--- | :--- | :--- |
| **Backup, Restore & Migration** | `BackupCodecTest.kt` | Full round-trip encoding/decoding, corrupt schema rejection, legacy layout import, widget continuity. |
| **Default Page & Page Edits** | `PageManagementTest.kt` | Setting default page, persistence across reordering, reassignment on deletion, undo. |
| **Grid Placement & Reflow** | `HomeLayoutTest.kt` | Free-cell finding, Manhattan reflow, stack constraints, multi-cell drop bounds. |
| **Widget Resizing & Origin Shift** | `HomeLayoutTest.kt`, `WidgetSizingTest.kt` | Top/left handle row/column shifting, minimum/maximum provider constraints, deadband. |
| **Screen Sizing & Centering** | `WidgetSizingTest.kt` | `gridCell` 140dp capping on unfolded displays, cover screen adaptation, short window scroll. |
| **Fold Handoff & Sensors** | `FoldPolicyTest.kt`, `TransitionDynamicsTest.kt` | Hinge angle normalization, closed-state recovery, vsync decay, motion suppression. |
| **Search & Categories** | `AppSearchTest.kt`, `AppearanceAndCategoryTest.kt` | Ranked prefix/substring search, category overrides, wallpaper luminance contrast. |
| **UI Automation & Interactions** | `HomeInteractionTest.kt` | Samsung Dressroom launch, bottom dock navigation, full-page Settings, keyboard visibility. |
| **Live Native Host Scaling** | `WidgetHostFitTest.kt` | Zero host padding, Samsung Weather unpadded dimension reporting, touch scaling. |
| **Interactive Color Picker & Presets** | `ColorPickerTest.kt` | Hex encoding/decoding, preset swatches, HSV roundtrip fidelity, invalid fallback. |
| **First-Run Onboarding & Tour** | `OnboardingPreferencesTest.kt` | Default incomplete state on clean install, completion persistence, tour mode safety, and step count bounds. |

---

## 5. IDE & Collaboration Workflow

When switching between Android Studio, Antigravity IDE, and other development environments:
1. **Verification Command:** Run `./gradlew :app:testDebugUnitTest :app:lintDebug` before committing any changes.
2. **Schema Integrity:** Any layout structure changes must increment or maintain backward-compatibility in `HomeLayoutCodec.kt`.
3. **Hardware Testing:** Use `adb devices -l` to verify on physical foldables (Samsung Galaxy Z Fold series or Pixel Fold).
4. **Git Hygiene:** Maintain clean atomic commits with descriptive commit messages following the component prefix pattern (e.g., `ui:`, `data:`, `widgets:`, `duo:`).

- Glass refinement: clearer decorative tiles and dock, concentrated edge reflections, and theme-aware Hub/Library text on contrast-preserving panels.

Library search places ranked apps first on a tinted glass results panel with solid theme-aware labels, a subtle best-match highlight, and secondary contact/web actions after the apps. Check readability against detailed bright/dark photos in both themes, including no results and keyboard-visible states.
