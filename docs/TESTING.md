# Android Studio and device validation

## Build setup

- Android SDK Platform 37, Android Studio supporting AGP 9.4.0, Gradle wrapper 9.7.1.
- Use Studio's bundled JDK or a compatible installed JDK. The implementation build used Studio's bundled JBR 25 and produced Java 17 bytecode.
- Android Studio normally creates `local.properties` with `sdk.dir`. For terminal builds you can instead export `ANDROID_HOME` and `JAVA_HOME`.
- First sync needs internet access for uncached dependencies; later builds can use `--offline`.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
./gradlew :app:assembleRelease
```

Outputs:

- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release, unsigned: `app/build/outputs/apk/release/app-release-unsigned.apk`
- JVM test report: `app/build/reports/tests/testDebugUnitTest/index.html`
- Android lint: `app/build/reports/lint-results-debug.html`

## Local state-machine experiment

Added 25 JVM regression tests (10 `HomeNavigationTest`, 11 `WidgetSetupFlowTest`, 4 `DragLifecycleTest`). The complete suite, including the folder-drop regression tests below, executes 219 tests with zero failures, errors or skips. Navigation tests cover Settings return paths, Home reset and replacement-target ownership; widget tests cover new/existing ownership, cancellation, busy phases and restored transaction state; drag tests cover cancel, duplicate/reentrant completion and callback failure.

Debug and instrumentation APKs compile. Android lint has zero errors and 40 warnings. No instrumentation suite, physical-device fold checks or signed release build has been run for this experiment. Historical release/device results below refer to the earlier source and are not validation of these changes.

Managed cloud validation uses the inherited proxy through the local Gradle helper and system Java CA trust:

```sh
JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts python3 /workspace/.cloud-setup/gradle.py :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Device checks before accepting:

- Open each of Settings, Widgets, Pages, Weather and Wallpaper, then press system Home. Confirm all close and the default home page is selected, including when Wallpaper was opened from Settings.
- Open Pages and Wallpaper from Settings; dismiss to Settings. Select a page from Pages to exit to Home. Rotate/recreate while each is open and verify its return path and replacement target.
- Choose a widget, deny binding consent, cancel mandatory provider setup, then retry. Confirm one new ID is released and the saved layout is unchanged on cancellation. Repeat configuration for an existing widget; cancelling must keep that binding and layout.
- Recreate the activity while consent/configuration is open. Verify the returned result completes the original transaction once. Try another setup action while busy; it must not overwrite the original target.
- Replace an orphan/prompt widget, cancel, then open normal Add widget. Confirm it adds normally without inheriting the cancelled replacement target.
- Exercise app/widget hold, drop, cancel, edge paging and rotation. Confirm no duplicate drop, provider click on release, or lost binding.

## Folder-drop regression

App-on-app drops now create folders, replacing the earlier swap policy. Hover keeps app/folder targets stationary so the target does not move away before release. Nine new `FolderDropTest` cases cover hover/commit behavior, cross-page and library grouping, existing folders, duplicate shortcuts, self-drops, widget reflow, Hub rejection and persistence; the two former swap tests now verify folder creation on ordinary and full grids.

Device check: drag A over B and release, open the new folder and check both apps; add C to it, then repeat across pages and from the library/Dock. Verify normal empty-cell moves, widget reflow and cancellation still work. This interaction fix has JVM/build validation; its physical-device rerun remains outstanding.

## Group title and organization controls

Eight `FolderEditingTest` cases cover persistent label-based A–Z ordering, case-insensitive stable ties, Swedish collation, preservation of unavailable apps, idempotent/empty sorting, invalid targets, unchanged folder identity/geometry/widget bindings, and rename validation without losing membership or order. The complete JVM suite executes 219 tests.

Device checks: open a group and tap its title; verify selection, keyboard focus and a 40-character limit. Save with keyboard Done and the check button, then reopen. Try a blank name (falls back to Folder), outside dismissal, and rotation while typing. Use + to select/deselect apps and check to return to the group; rename during selection, then tap app search and confirm the keyboard remains usable. Press A–Z and confirm the displayed order survives closing/reopening without altering membership. New additions append until A–Z is pressed again. Physical UI/IME checks remain to be run on the phone.

## Library/search drag handoff

Nine `LibraryDragTest` cases cover source removal/callback ownership, absolute root movement, exactly-once completion, cancellation, unchanged non-handoff gestures, stationary long presses and last/default/deleted-page destination rules. The complete suite executes 219 tests. These are policy/lifecycle tests; pointer dispatch and IME timing still need device input.

Device check: open Library, search for an app, hold and move it. Confirm search/IME disappear, the last home page appears, and the follower stays attached to the same finger until release. Drop on an empty cell, another app, an existing folder and the Dock. Repeat edge paging, Add apps picker, A–Z browsing, stationary long press, cancellation, multi-touch, rotation and system Home. Also confirm original home/dock/widget drags work. No placement should be committed on cancellation. Category-folder dialogs use their existing separate-window path and are outside this inline search-result handoff.

## Automated coverage

219 JVM tests cover:

- first-run default layout generation with dual 2×2 top widget prompt tiles and Row 2 app flow, selective in-place prompt replacement for square widgets while retaining sibling prompts, wide 4×2 widget replacement clearing sibling prompt slots, and codec round-trip validation;
- device status telemetry defaults, copy integrity, cellular network formatting, and multi-cell signal strength level extraction;
- first-run onboarding preference persistence, default incomplete state resolution on fresh install, completed state round-trip, backup restore bypass, 5-step pager bounds, and theme-adaptive contrast invariant resolution;
- interactive color picker presets, HSV roundtrip conversion fidelity, hex parsing and formatting, invalid hex fallback, and dark/light contrast luminance calculation for UI element tinting;
- multi-tier smart app categorization (`LibraryCategory.of`, `AppCategory.classify`) using Android platform categories, game flags, publisher/package signatures, and semantic keyword matching on label/package tokens to minimize "Other", with collision disambiguation (Perplexity, Uber Eats, Kindle, Prime Video), user override persistence, and in-settings refresh/reset;

- the exact dock-position matrix, handedness, rotation and split-screen classification;
- app-on-app folder creation on ordinary/full grids, same-page/cross-page/library drops, existing-folder deduplication, duplicate-shortcut no-op, stationary grouping previews, persistence round trips, preserved widget bindings, and non-grouping reflow;
- off-center zero-thickness hinges on both axes;
- invalid/missing angle samples and recovery from CLOSED posture;
- no startup animation when WindowManager delivers its first feature;
- sensorless display handoff, reversal, stale resume, interaction suppression and complete settling;
- equivalent motion response at 60 and 120 Hz;
- favorites insertion boundaries, capacity and duplicate prevention;
- legacy pin/widget migration, overflow pages, shared app/widget occupancy, folder creation, cross-page widget moves, page deletion and strict layout decoding;
- Hub widget isolation from normal page counts, widget-only enforcement, full-canvas fail-closed placement, move/resize/remove binding preservation, schema-5 round trip, and schema-4 empty-Hub migration;
- wallpaper luminance/foreground selection, app category mapping (including Another Launcher Settings under Utilities) and ranked global search;
- Weather/Analog placement, schema-3 upgrade preservation, stack/unstack round trips with real widget IDs, incompatible-span rejection, provider/profile deduplication, weather-location validation and package-removal cleanup;
- schema-4 provider-identity round trip, v3 back-compat without provider keys, in-place binding replacement (same cell, nearest-cell fallback, fail-closed without room), orphan partitioning across top-level and stacked widgets, and stacked item page resolution;
- full backup and restore round-trip including cellular indicator toggle persistence and legacy import, strict backup schema versioning, corrupt backup payload rejection, and legacy v3 layout import;
- widget fit bounds/aspect ratio, transient zero-size handling, measured-grid span selection, provider resize limits, continuous home-grid sizing and home-screen provider filtering;
- default home page designation: setting default page, persistent default across page reordering, fallback reassignment on page deletion or keeping-items removal, and undo restoration;
- multi-directional widget resizing with origin coordinate shifting (left handle shifts column, top handle shifts row, boundary clamping at 0,0);
- accurate drop target region bounds computation across multi-cell widget spans;
- responsive grid cell capping: default `MAX_CELL_DP = 100dp`, optional 140dp inner portrait side layout cap, width-tracking on narrow cover screens, and height-tracking in short windows;
- widget resize constraints: single-axis vs dual-axis locking, minimum 1×1 bounds, zero/negative span rejection, and max-dimension constraints;
- inline math expression parser and evaluator (supporting arithmetic, percentages, powers, and zero-division protection);
- search ranking enhancements: CamelCase-aware acronym/initial matching, ordered subsequence ranking, and 21 deep system settings shortcuts;
- App Library category classification heuristics, 2x2 folder card layouts, and category sheet grouping;
- edit-mode haptic jiggle animation clock dynamics;
- PagerMap workspace pagination: Page 0 Hub mapping on single screen vs unfolded dual-pane Hub + Page 1 pairing on wide inner displays, Page N+1 App Library mapping, and bidirectional key-to-index resolution;
- recent apps launch tracking, deduplication, capacity limiting, and uninstalled-app filtering;
- theme contrast resolution for modal sheets (`CategoryFolderOverlay` and `FolderPanel`), ensuring crisp light typography and controls in dark mode and dark typography in light mode.

`DuoRenderLayerTest` is an instrumentation test because real AGSL compilation is an Android framework operation. It builds/binds effects for both axes, several hinge positions and verifies the idle/zero-size bypass:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Run on API 31/32 for the Gaussian-blur path and API 33+ (preferably API 37) for AGSL. The shader test passed on the connected Samsung API 37 device during this iteration. It validates shader creation/binding, not visual pixel equivalence or GPU frame timing.

`HomeInteractionTest` uses public UI Automation input. It verifies the bottom dock, full-page Settings/Back, popup geometry, the drag follower/source placeholder, visible keyboard search results, and Samsung One UI "Wallpaper and style" (Dressroom) library launching via the Wallpaper button. On a full home page it uses the exposed Home-options accessibility action instead of modifying the user's layout. Drag input is canceled; no pin/uninstall/binding actions are committed.

`WidgetHostFitTest` adds three checks: an oversized native-content fixture must fit completely and receive correctly transformed touch coordinates; an existing Samsung Weather binding must publish its real unpadded dimensions and keep its provider/ID intact; and a long-press on that live widget must pick it up for dragging (`drag-follower`) without opening `item-quick-popup`, with a same-cell drop preserving the exact layout. The Samsung-specific checks skip on devices without a bound Samsung Weather widget. The final connected-device run completed all device tests with normal pass statuses and no skips.

## If Android Studio reports “Can't find module entity for anotherlauncher.app”

This is an IDE project-import failure. The Gradle root name is deliberately lowercase
`anotherlauncher`, matching the project directory and the saved `anotherlauncher.app` module ID.
The displayed app name remains **Another Launcher**.

1. Open the project root containing `settings.gradle.kts` and `gradlew`.
2. Choose **File → Sync Project with Gradle Files** and wait for sync to complete.
3. If that exact import error remains, use **File → Invalidate Caches → Invalidate and Restart**,
   then sync again. A command-line build cannot refresh Studio's in-memory project model.
4. In **Run → Edit Configurations**, select the Android App configuration and choose the
   imported `anotherlauncher.app` module and **Default Activity** if the previous selection is invalid.
5. Start a virtual device from **Tools → Device Manager**, or connect a phone with USB debugging
   enabled and accept its debugging prompt. Select that device before pressing Run.

`adb devices -l` must list a device in the `device` state for installation or instrumentation tests.

## First device run

1. Run the `app` configuration. Hold an empty home cell → **Settings → Set as default home**.
2. Open Settings and note the diagnostic window size, display ID, evidence, hinge angle and axis.
3. Fold/unfold slowly with the launcher visible. Confirm the angle changes if One UI exposes the standard sensor. If it stays “not reported,” confirm the short metrics/posture transition still works.
4. Open All apps, search, and switch displays while the drawer remains open. Search and scroll state should survive.
5. Pin favorites, reorder them by dragging, add an app to the workspace, and reopen the app after a cold start. Ordered pins should persist.
6. Add a widget, configure it if requested, then fold/rotate. Interact with the widget and confirm content updates and touch behavior. Cancel a second widget pick/configuration and verify it adds no empty tile.

## Posture matrix

| Scenario | Expected |
| --- | --- |
| Cover portrait | Side dock; compact workspace |
| Cover landscape | Right-side Dock under Auto; no false unfold from rotation alone |
| Inner landscape / square | Right-side Dock under Auto; Left only when explicitly selected |
| Inner portrait | Bottom dock by default; optional left/right rail with wider edge-aligned 4×6 grid |
| Half-open, stationary | No persistent blur; widgets/icons remain usable |
| Rapid open → close → open | Flow changes direction; no stuck effects |
| Split-screen/freeform | Side dock; narrow inner window stays classified by maximum metrics |
| Launch directly on already-open device | Clear initial scene, no artificial opening animation |
| Background, unfold, return later | Clear current layout; no replay of old motion |
| Rotate while dragging | Drag cancels; no drop onto stale coordinates |

In the emulator, create a foldable device in Device Manager and use **Extended controls → Virtual sensors / Device pose**. Exact controls depend on emulator/device profile. Where supported, `adb emu sensor set hinge-angle 90` can supply an intermediate hinge sample; changing a sensor alone may not switch the emulator's physical display mode, so also use its fold/unfold controls. `adb shell wm size` is a resize test, not a substitute for a true cover/inner transition.

## Beta.4 closed-test acceptance

- Before changing an existing launcher install or layout, export **Settings → Backup and restore → Export backup** to Downloads and verify the JSON contains the expected home pages and favorites. Do not uninstall the Play-installed app to work around a signing mismatch.
- On a cover screen, confirm the lowest home icons sit near the bottom Hub/library controls, the page dots remain legible on light and dark wallpaper, and all tiles remain reachable without overlap with system gesture insets.
- On an inner portrait screen, toggle **Settings → Dock → Wider inner-screen layout**. Confirm the default bottom dock and optional left/right rail both show the same home items and live widgets, and that drag/drop, widget taps, and page navigation still work.
- Switch the App Library to A–Z, fold/unfold, close/reopen the library, and restart the process. The selected view must remain. This fold/unfold behavior was confirmed by the developer on the beta.4 QA build.
- Choose each provider under **Settings → Web search**. Search from the App Library and confirm the browser opens the selected provider; app, contact, Settings, and math results must remain local. Confirm the selected provider survives a restart and backup/restore.
- Install the Play-distributed beta.4 over beta.3 on at least one tester device to check saved layouts and widget bindings. A locally signed APK cannot update the Play-signed package because Android rejects the signature mismatch.

## Interaction and accessibility

- Navigate to App Library via the last page or bottom search button toggle. Vertical scrolling inside a widget or app list must stay with that child.
- Long-hold/release an icon for options. Long-hold/drag for dock insertion. Test the six-favorite limit and moving an existing favorite in a full dock.
- Long-press several unobstructed wallpaper margins outside the 4×6 grid and Dock; each should open Home options. Confirm the same hold on icons, widgets, status controls, and Dock items keeps their own behavior, while horizontal movement still pages normally.
- Open the bottom Home options dock → Widgets, Wallpaper, Pages, Edit, Settings. Add an app via empty page Add apps or Dock +, then tap it again to remove it. Verify checkmarks and counts update without uninstalling the package.
- Tap the **Another Launcher Settings** app from the App Library or home grid; confirm it opens directly into the full **Launcher settings** page with Back navigation returning smoothly to home.
- Confirm app names start hidden on Home, remain in the library, and can be enabled in the full Settings screen.
- Check Weather at columns 0–1 and Analog clock at columns 2–3, each spanning two rows. Set a weather city manually, then use current-location detection and verify permission denial, disabled Location Services, real data, and offline behavior. Confirm the analog hands follow local time.
- Hold an app/widget and verify an attached popup rather than a full dialog. Open Create stack, choose a compatible item, cycle the stack and unstack it. Native widget bindings must survive each operation.
- Open Widgets and inspect application groups. Exact provider/profile duplicates must be absent; distinct provider sizes/variants must remain selectable. Cancel a bind and a required configuration; neither may leave an empty tile. Cancel reconfiguration of an existing widget and confirm it remains bound.
- Enable Edit grid and use a minus control on Home and Dock. Drag an existing placement onto the Remove target. Confirm each operation removes only the intended placement.
- Create a second page, move an app/widget to it, swipe between pages, and restart. Drag app-on-app to create a folder; rename it and toggle contents. Nonempty pages must not be deletable.
- Long-press a widget, check movement and grid-size options, and verify a vertical scroll inside the widget does not open a menu. Test resize minimums with real providers.
- Check Samsung Weather on both displays, in a small slot, and in a stack. The launcher should not clip its content or cover it with stack controls. Providers may intentionally choose different content/layouts at different sizes; this is distinct from host clipping.
- On the connected test device, the existing Samsung Weather provider previously received 130×157dp after double padding. Version 0.4 reports its actual 146×173.33dp content area at scale 1.0, with zero host padding, preserving the same binding.
- Select a library category, then type a query for an app in another category. Results should search globally; clearing the query should restore browsing. Verify accent-insensitive and word-prefix searches.
- Enable TalkBack; check icon labels, App options custom actions, drawer controls and the single grouped status announcement.
- Unfold in portrait and verify every app/widget page remains one evenly spaced continuous grid with no inserted hinge row gap. Fold, rotate, and unfold again; placements and widget bindings must not change.
- In unfolded portrait, verify the 68dp Search button is flush to the bottom-left content corner, matches the Dock height, and does not shift or touch the centered Dock. Confirm every shortcut uses the magnifier and accessibility label Search, focuses the field after transition, and shows the keyboard.
- Increase font/display size. Confirm the rail time and circular emblem fit safely below cutouts, and tap the emblem for the device-controls panel. Verify Wi-Fi, power-saving, and airplane-mode switches show live state, launch the matching Android control, and refresh after returning. Test both 12-hour and 24-hour formats.
- Change Android/Samsung light and dark mode while Home, Settings, the library and a folder are open. Test bright and dark system wallpapers independently of system mode.
- Enable Reduce motion and set Android's Animator duration scale to Off separately. Both should disable the fold effect; the local setting also disables grid rearrangement animation.
- Deny cellular permission, then grant it from Settings. No crash and no fabricated signal levels in either state. Test airplane mode and a Wi-Fi-only device.
- Open the keyboard in All apps, fold/rotate, dismiss the keyboard and press Home. Insets should keep the dock/search field reachable.

## Frame-time and continuity checks on hardware

Use the release build with Android Studio's System Trace/Perfetto and FrameTimeline, observing several slow and fast folds at the device's supported refresh rates. Compare frame deadlines to the actual refresh rate (8.3 ms at 120 Hz; 16.7 ms at 60 Hz), and inspect both CPU and GPU work. Watch the first expanded layout, widget-heavy pages, and the maximum blur interval.

Confirm that the render effect becomes null at rest and the controller stops scheduling frames. Pause half-open for a few seconds and background the Activity while an effect is running. Neither should leave continuous rendering or foreground sensor listeners active.

Record any visible black interval during physical panel switching separately from an app-rendering hitch: One UI owns that panel/compositor handoff. If available, film the fold with an external camera to see the two physical panels and the hinge together.

Before release, reset `dumpsys gfxinfo com.anotherlauncher`, perform repeated Hub/Home/Library swipes and return Home from another app, then inspect deadline misses separately from cold composition. Verify widgets retain their IDs, remain visible during the brief listener deferral, and refresh normally after resume.

### Historical validation before the state-machine experiment

The source before this experiment passed 168 JVM tests and builds debug, instrumentation and signed release bundle artifacts. Lint has zero errors and 40 reviewed warnings: UseKtx (11), ConfigurationScreenWidthHeight (6), ObsoleteSdkInt (6), IconLauncherShape (5), IconDuplicates (5), ModifierParameter (3), and one each for RedundantLabel, AndroidGradlePluginVersion, Wakelock and IconLocation. The timed wake-lock/fold handoff is retained; no warning suppressions were added. Earlier beta.3 hardware checks are historical, not current proof. Comprehensive provider variants, physical handoff timing and frame-time targets still require the manual checks above.

Glass contrast manual check: inspect Hub weather, Add Widget, Library results, Library search, A-Z toggle, Recent apps, and folder sheets in both themes over bright and dark wallpapers. Verify text, placeholders, icons, and selected states remain readable.

Library search separates ranked Apps from an explicit provider-labeled Web action. Test a matching app, an unmatched term, blank/whitespace input, calculator, Settings shortcuts and opt-in contacts. App taps launch apps; the Web action and keyboard Search key open the selected provider. Clear retains focus/IME, Cancel/Back exits search, and subsequent Back/Home returns to default Home without an offscreen drawer consuming it.

## Search and widget Hub device checks

`SearchHubInteractionTest` contains eight UI Automation checks and requires an isolated application ID ending in `.qa`; it skips against the production package because it seeds preferences and temporarily binds a widget. Build a disposable copy with that application ID and a distinct QA label, install both APKs with `--user 0`, and grant widget binding only to that QA package. Never clear, uninstall, or replace the Play-installed package for these tests.

Coverage: Search accessibility/focus/keyboard and repeated taps; app + Google actions and clear/empty input; unmatched search/Back/Home; app-result launch; calculator/Settings results; selected provider action and IME launch; weather picker/Hub widget catalog with no Hub EditText; real native Hub widget bind/render/remove with weather location unchanged. Native-fit and touch-cancellation fixtures can run separately without touching a user's layout. Six sizing unit tests cover bounded wallpaper decode dimensions, aspect ratio, no upscale, invalid sizes and extreme integer bounds. Wallpaper picker photo/preset/rotation/application and contact-provider behavior still require manual hardware checks.

Discover preference persistence is not applicable: no accepted genuine Discover client is implemented. See [DISCOVER.md](DISCOVER.md); do not report a web-view substitute as Discover.

### October 12 device evidence and limits

Hardware checks used an isolated `com.wiselion.anotherlauncher.qa` install on the connected Samsung foldable; the Play install and its data were not modified. All eight search/Hub cases passed on the inner display in individual or grouped runs before the final Home-navigation guards. Cover-screen runs passed repeated search focus/IME, app and web results, clear/blank input, unmatched-query Back/Home, app launch, calculator/Settings actions, external provider/IME launch and native Hub widget add/render/remove. Native-fit and touch-cancellation fixtures also passed.

The final cover-screen Hub weather-dialog rerun failed because automation tapped during the remaining page animation. The test now waits for those frames; its final rerun was not completed before the phone was released. Combined instrumentation runs also experienced intermittent USB/UI Automation disconnects, so the complete suite is not claimed as passing in one final run. The newest Home-navigation guards have only limited device coverage; repeat the full posture matrix before production rollout. Manual photo/preset/wallpaper application, optional contact permission/provider results, TalkBack and broader widget-provider variants remain outstanding.

## Streamlined optional permissions

Check onboarding and Settings with permissions initially denied: a single feature tap must open Android's permission prompt (contacts, signal, location) or notification-access settings directly, with no Agree/Continue dialog. Inline explanations must be complete on narrow screens and large fonts. Declining contacts leaves search disabled and retryable; denying location keeps manual city search usable; denying signal keeps the launcher usable; returning from notification settings refreshes access state. Already-granted permissions must not prompt again. Skipping onboarding or leaving Settings must not request any permission. Preserve the manually opened privacy policy.

`PermissionFlowTest` adds five isolated-QA-only cases. All five passed individually on the connected Samsung foldable: contacts opens the OS prompt directly, denial leaves search off and permits retry; cellular permission grant activates the feature; location denial retains editable manual city search; onboarding opens notification-access settings directly and returns to the tour; and the manually opened privacy policy still dismisses normally. Each case reset only the `.qa` package. The Play install, its default-Home role and layout were unchanged. Final local checks passed 168 JVM tests, compiled the instrumentation APK, and produced zero lint errors with the same 40 reviewed warnings. The signed version-code 12 release bundle was built and its package, version and signature verified. Large-font, TalkBack and already-granted permission variants remain manual checks.
