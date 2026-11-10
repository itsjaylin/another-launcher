# Another Launcher — Project Roadmap & Implementation Plan

An Android launcher built around foldable screens, native widgets, and a floating dock. Now open source and available as an APK.

**Current source:** `0.7.0-beta.5` (version code `12`)

**Target:** Android 17 (API 37) | **Minimum:** Android 12 (API 31)

**Recorded testing:** The latest interaction update reports 219 passing JVM tests and zero lint errors. Five optional-permission cases passed individually on an earlier isolated QA build. See [Testing](docs/TESTING.md) for device results and remaining checks. These results have not been rerun for this documentation update.

---

## 1. Project Vision & Core Principles

- **Foldables first:** Account for cover screens, inner displays, hinge position, and rotation.
- **Comfortable layouts:** Keep controls reachable on short, wide screens, with room for widgets on larger displays.
- **Real device geometry:** Use window metrics, hinge bounds, and cutouts instead of fixed resolutions.
- **Native widgets:** Preserve widget bindings through folding, rotation, resizing, and stacking.
- **Direct control:** Local settings and layout storage, Material You colors, and no background analytics.

---

## 2. Completed Milestones

Earlier milestones describe their original implementation. Later changes supersede the old fold blur, automatic dock placement, and Hub search.

### Milestone 1 — Core Foundation & Live Unfold Engine (`v0.1.0`)

- [x] Compose/Kotlin project targeting API 37.
- [x] Hinge tracking through Android sensors and WindowManager.
- [x] AGSL unfold effect with an API 31/32 fallback; later removed from the active workspace.

### Milestone 2 — Dual-Spread Workspace & Adaptive Dock (`v0.2.0`)

- [x] Dual-page spreads with hinge clearance on wide displays.
- [x] Side rail and bottom dock layouts, initially with rotation-based placement.
- [x] Up to six favorites with drag reordering and placement bounds.

### Milestone 3 — Dashboard & Widget Stacking (`v0.3.0`)

- [x] Open-Meteo weather with lifecycle-aware refresh and cached results.
- [x] Built-in 2×2 analog clock.
- [x] Swipeable stacks that preserve widget bindings.
- [x] Grouped widget picker with compatible providers and previews.

### Milestone 4 — Native Widget Host & Samsung Support (`v0.4.0`)

- [x] Removed duplicate widget padding and corrected touch coordinates when scaling.
- [x] Samsung Weather size reporting and aspect-ratio handling.
- [x] Separated widget scrolling from launcher pickup gestures.

### Milestone 5 — Reflow, Categories & Search (`v0.5.0`)

- [x] Nearby items move to free cells during drag.
- [x] Drag Library/search results to the last valid home page, with search/IME hidden and the pointer stream preserved.
- [x] Ranked app search and optional contacts; web search was expanded in later updates.
- [x] Automatic categories with per-app overrides.

### Milestone 6 — Widget Resizing & Fold Handoff (`v0.6.0`)

- [x] **Live resizing:** Edge/corner handles, size feedback, top/left expansion, and haptics with a deadband to prevent jitter.
- [x] **Cover-screen continuity:** Fold detection and a timed wake lock keep the launcher visible when closing the device.
- [x] **Samsung wallpaper settings:** Public Wallpaper & style intent with a system Settings fallback.

### Milestone 7 — Home Pages, Frosted Glass & Launcher Polish (`v0.7.0` betas)

- [x] **First-run layout:** Two removable widget prompts; wide widgets replace both slots, square widgets keep the neighboring prompt.
- [x] **Default home page:** Saved page selection, reorder/delete handling, and Home/Back routing without trailing ghost pages.
- [x] **Gesture navigation:** AOSP/One UI handshake, deferred widget refresh, and focus cleanup to avoid stray keyboards.
- [x] **Widget repair:** Save provider metadata and replace broken bindings without deleting placements. Layout schema 5 reads schemas 2–4.
- [x] **Backup and restore:** Portable JSON for pages, widgets, folders, stacks, favorites, and settings; reject corrupt files before changing storage.
- [x] **Home options:** Widgets, Wallpaper, Pages, Edit, and Settings, plus a separate Settings app entry.
- [x] **Workspace layout:** Continuous 4×6 grid, explicit left/right dock choice, and wallpaper long-press outside the grid.
- [x] **Icons and glass:** High-resolution app icons and shared frosted surfaces with light/dark contrast.
- [x] **Device status:** Network badges, cellular signal, charging indicator, device controls, and red notification dots.
- [x] **Widget Hub:** Weather and native widgets on Page 0, with editing, resizing, repair, and backup support. Search now lives in the Library.
- [x] **App Library:** Last pager page with categories, A–Z browsing, recent-app dropdown, and drag-out support.
- [x] **Add apps and grouping:** Searchable A–Z picker; app-on-app drops create folders, including on full pages. Tap a group title to rename, + to select apps, or A–Z to sort.
- [x] **Wallpaper picker:** Photos, six presets, preview, and Home/Lock/Both targets over native system wallpaper.
- [x] **Settings:** Shared controls, search, and a color picker with swatches, HSV sliders, hex entry, and live preview.
- [x] **Welcome tour:** Five-step setup, optional permissions, saved completion, and replay without resetting the layout.
- [x] **Categories:** Platform/package/label matching, manual overrides, refresh/reset, and Other placed last.
- [x] **Fold blur removal:** Removed the experimental unfold effect while keeping display continuity.
- [x] **Weather location:** Reuse a recent location or request an update when permission is already granted.
- [x] **Privacy and permissions:** Inline explanations, manual weather fallback, privacy policy, and removal of `WRITE_SECURE_SETTINGS`.
- [x] **Tour styling and credits:** Theme-aware text, background blur for overlays, app icon, and open-source credits.

---

## 3. Near-Term Roadmap & Next Priorities

Future phases keep the original grouping, but have no promised release dates or versions.

### Launcher interaction update

- [x] Document state ownership in [STATE_MACHINES.md](docs/STATE_MACHINES.md).
- [x] Explicit foreground navigation and widget setup; consume drag completion before callbacks.
- [x] Regression coverage for navigation, widget cancellation, grouping, folder controls, and Library drag handoff.
- [ ] Test Home/Settings return paths, provider consent/configuration, recreation, folder editing, and drag handoff on hardware.

### Current-build refinement

- [x] Unified Library search: apps, web, calculator, Settings, and optional contacts.
- [x] Search focus/keyboard handling, widget-only Hub, bounded wallpaper decoding, and inline permission explanations.
- [x] Investigated Discover and recorded the missing client/caller requirements in [DISCOVER.md](docs/DISCOVER.md).
- [ ] Repeat fold/rotation and Home/Back checks on both screens; finish the Hub weather-dialog retest and check the latest navigation fixes.
- [ ] Check wallpapers, glass contrast, all search providers, contacts, already-granted permissions, large text, and TalkBack on hardware.
- [ ] Verify APK updates and backup/restore with existing layouts. Export a backup first; keep the existing Play install if signatures differ.
- [ ] Validate a genuine Discover connection before offering a Hub/Discover switch. Preserve Hub widgets and fall back when unavailable.

### Earlier beta.4 feedback

- [x] Optional wider inner-screen grid with side dock; centered grid and bottom dock remain the default.
- [x] Saved A–Z/category preference, adjusted cover spacing, and clearer page dots.
- [x] Google, DuckDuckGo, Brave, and Startpage choices.
- [ ] Check real widgets in both inner layouts and spacing over varied wallpapers. Earlier visual/provider checks do not cover every case.
- [ ] Consider custom search URLs and page-dot colors. App-name and always-show-dot settings already exist.

### Phase 8 — Gestures & Multitasking

- [ ] **Swipe down:** Open notifications from empty wallpaper.
- [ ] **Double-tap to sleep:** Lock from empty wallpaper using an accessibility service.
- [ ] **Pinch to edit:** Open the page manager from a home spread.
- [ ] **App pairs:** Save split-screen shortcuts where Android/OEM support allows it.

### Phase 9 — Icon Packs & Theming

- [ ] **Icon packs:** Read `appfilter.xml` and support per-app overrides.
- [ ] **Themed icons:** Add monochrome fallbacks for apps without them.
- [ ] **Sizing:** Adjustable icons and grid density within layout bounds.
- [ ] **Folders:** Suggested names and 2×2 or 3×3 previews.

### Phase 10 — Dock Ergonomics

- [ ] **Dock capacity:** Choose four to six slots with spacing that fits each layout. Six favorites and left/right placement already work.

### Phase 11 — Flex Mode & Fold Performance

- [ ] **Tabletop layout:** Widgets above the fold and controls below it when half-open.
- [ ] **Frame pacing:** Measure and reduce missed frames during rapid folds, widget-heavy paging, and return-Home transitions.

---

## 4. Architectural Verification Matrix

| Component | Test File | Key Coverage |
| :--- | :--- | :--- |
| Backup and restore | `BackupCodecTest.kt` | Round trips, legacy imports, corrupt-file rejection |
| Pages | `PageManagementTest.kt` | Default page, reorder, deletion, undo |
| Placement and reflow | `HomeLayoutTest.kt` | Free cells, swaps, stacks, drop bounds |
| Widget resizing | `HomeLayoutTest.kt`, `WidgetSizingTest.kt` | Origin shifts, provider limits, deadband |
| Screen sizing | `WidgetSizingTest.kt` | Grid caps, cover screens, short windows |
| Fold handling | `FoldPolicyTest.kt`, `TransitionDynamicsTest.kt` | Hinge samples, recovery, motion behavior |
| Search and categories | `AppSearchTest.kt`, `AppearanceAndCategoryTest.kt` | Ranking, overrides, contrast |
| UI interactions | `HomeInteractionTest.kt` | Dock, Settings, wallpaper launch, keyboard |
| Native widget scaling | `WidgetHostFitTest.kt` | Padding, Samsung sizing, touch coordinates |
| Colors | `ColorPickerTest.kt` | Hex, HSV, presets, invalid input |
| Onboarding | `OnboardingPreferencesTest.kt` | Completion, persistence, safe tour replay |

Coverage details and manual checks live in [Testing](docs/TESTING.md). Historical shader tests do not prove current fold performance.

---

## 5. IDE & Collaboration Workflow

1. **Check the working tree** before editing across IDEs.
2. **Verify code changes:** `./gradlew :app:testDebugUnitTest :app:lintDebug`.
3. **Keep layouts compatible:** Preserve older `HomeLayoutCodec` schemas when changing storage.
4. **Check hardware:** Test on physical foldables and back up existing layouts before device changes.
5. **Keep commits focused** and update the README, architecture, and testing notes when behavior changes.

See [AGENTS.md](AGENTS.md) for the full development rules.
