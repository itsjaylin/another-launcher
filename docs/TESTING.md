# Android Studio and device validation

## Build setup

Use Android SDK 37, an Android Studio version supporting AGP 9.4.0, and the Gradle 9.7.1 wrapper. The recorded build used Studio's JBR 25 and produced Java 17 bytecode.

Studio creates `local.properties` with `sdk.dir`; terminal builds can use `ANDROID_HOME` and `JAVA_HOME`. First sync needs network access; cached builds can use `--offline`.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
./gradlew :app:assembleRelease
```

| Output | Path |
| --- | --- |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Unsigned release | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| JVM results | `app/build/reports/tests/testDebugUnitTest/index.html` |
| Lint results | `app/build/reports/lint-results-debug.html` |

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

The latest interaction-update record reports **219 passing JVM tests**, with no failures, errors, or skips. Earlier device/release evidence below covers the prior 168-test source. These are recorded results, not a fresh run for this documentation update.

| Area | Coverage |
| --- | --- |
| First run | Two widget prompts, square/wide replacement, initial app positions, codec round trips |
| Onboarding | Completion persistence, restored backups, safe replay, five-step bounds, theme contrast |
| Status and colors | Network formatting, multi-cell signal, defaults, presets, HSV/hex conversion and invalid input |
| Categories and search | Platform/package/label classification, collision guards, overrides, ranking, accent/prefix/acronym/subsequence matching |
| Dock | Placement/rotation/multi-window policy, ordered favorites, capacity, duplicates, insertion bounds |
| Home layout | Grouping on full grids, stationary hover, folder editing/sorting, nearest-cell reflow, no-space rejection, stacks, cross-page moves, removal and undo |
| Persistence | Legacy migrations, schemas 3–5, provider metadata, strict decoding, default-page fallback, unique identities |
| Hub | Separate widget-only canvas, full-grid rejection, edits preserving bindings, schema-4 migration |
| Widget repair | Top-level/stacked orphan detection, same-cell/nearest-cell replacement, failure without space |
| Backup | Full round trips, settings, legacy import, corrupt/unsupported schema rejection |
| Widget geometry | Native fit, aspect ratio, zero sizes, provider limits, axis locks, origin shifts, drop bounds, resize deadband |
| Responsive sizing | Default 100dp and optional 140dp caps, narrow/short windows, bounded wallpaper decoding without upscale |
| Fold policy | Zero-thickness/off-center hinges, bad samples, closed recovery, sensorless handoff, stale resume, suppression |
| Experimental motion | No initial animation, reversal, settling, equivalent 60/120Hz response |
| Library and pager | Recent apps, filtering, Hub/Home/Library mapping, category layout, modal contrast |
| Other local behavior | Weather location validation, package removal, arithmetic/percent/power parsing, Settings shortcuts, edit haptics |

### Instrumentation

```sh
./gradlew :app:connectedDebugAndroidTest
```

- **`DuoRenderLayerTest`:** Builds experimental shaders for both hinge axes and checks idle/zero-size bypass. The recorded Samsung API 37 run passed. Use API 31/32 for the fallback and API 33+ for AGSL. This tests construction, not pixels, GPU timing, or the active fold path.
- **`HomeInteractionTest`:** Dock, Settings/Back, popup geometry, drag follower, keyboard results, and Samsung Wallpaper & style. It cancels drag input and avoids committed pin/uninstall/binding changes.
- **`WidgetHostFitTest`:** Native fit/touch fixture, Samsung Weather dimensions/identity, and pickup followed by a same-cell drop. Samsung checks skip without a bound provider. The earlier recorded run had no skips; it does not establish current full-suite status.

## If Android Studio reports “Can't find module entity for anotherlauncher.app”

The Gradle root is lowercase `anotherlauncher`; the app's displayed name is **Another Launcher**. This error is an IDE import problem.

1. Open the root containing `settings.gradle.kts` and `gradlew`, then sync Gradle.
2. If it persists, invalidate caches/restart and sync again. A terminal build cannot refresh Studio's project model.
3. In Run configurations, choose the imported `anotherlauncher.app` module and Default Activity.
4. Start an emulator or authorize a USB-debugging device. `adb devices -l` must show it as `device`.

## First device run

Before changing an existing install or layout, export **Settings → Backup and restore → Export backup** to Downloads. Verify the JSON is nonempty and contains the expected pages and favorites. Never uninstall a Play build to bypass a signature mismatch. Use an isolated QA package for destructive tests.

1. Run `app` and set it as the default Home app.
2. Note Settings diagnostics: window/display ID, posture evidence, hinge angle, and axis.
3. Fold slowly. If no angle is reported, check metrics/posture handoff instead.
4. Search in the Library and switch displays; query, browsing choice, and scroll should survive.
5. Add/reorder favorites and a home app, then cold-start to check persistence.
6. Add/configure a widget, fold/rotate, and test touch/content updates. Cancel another bind/configuration and check for empty tiles.

## Posture matrix

| Scenario | Expected |
| --- | --- |
| Cover portrait/landscape | Selected side dock; compact grid, safe insets |
| Inner landscape/square | Selected side dock |
| Inner portrait | Centered grid/bottom dock by default; optional wider grid/side dock |
| Half-open, stationary | Usable widgets/icons; no fold blur |
| Rapid open → close → open | Correct layout and cover wake; no stale state |
| Split-screen/freeform | Side dock; maximum metrics retain inner classification |
| Launch already unfolded | Current layout without an opening animation |
| Background, unfold, return | Current layout without replaying motion |
| Rotate during drag | Cancel drag; no drop at stale coordinates |

Use the emulator's foldable profile and **Extended controls → Virtual sensors / Device pose**. Where supported, `adb emu sensor set hinge-angle 90` changes the sensor, but may not switch panels. `adb shell wm size` tests resizing, not physical fold handoff.

## Beta.4 feedback checks

These checks remain useful for current APKs; the old Play beta.3→beta.4 upgrade was a historical release gate.

- Cover icons and page dots should stay reachable/readable over varied wallpapers without gesture-inset overlap.
- Toggle **Dock → Wider inner-screen layout**. Both layouts must retain items, live widgets, drag/drop, and navigation.
- Choose A–Z, fold, reopen, and restart. The choice must persist; folding was confirmed on the beta.4 QA build.
- Try all four web providers; verify browser launch, saved choice, and backup/restore. Local results must remain local.
- Test a compatible signed APK update without losing layouts/bindings. Different signing keys cannot update an existing install.

## Interaction and accessibility

- **Navigation:** Reach the Library from paging and Search. Home/Back returns to the default page; offscreen search must not consume Back. Child lists/widgets retain vertical scrolling.
- **Wallpaper holds:** Open Home options from empty margins; holds on controls/items retain their own actions, and horizontal swipes still page.
- **Apps and dock:** Check options, drag insertion, the six-pin limit, full-dock reorder, add/remove badges, outside dismissal, and the separate Settings app entry.
- **Pages and folders:** Move items between pages and restart; test default-page selection, reorder, deletion/repacking, and undo. App-on-app creates a folder; test title rename, + app selection, persisted A–Z order, and dropping onto existing folders.
- **Widgets:** Test popup vs pickup, resize axes/min/max, stacking/unstacking, and provider/profile variants. Canceling new configuration leaves no tile; canceling reconfiguration preserves the old binding.
- **Samsung Weather:** Check both displays, small slots, and stacks for clipping or covered controls. Provider layout changes are distinct from host clipping. The historical 0.4 check corrected reported content from 130×157dp to 146×173.33dp at scale 1.0 without rebinding.
- **First run and weather:** Check removable widget prompts and square/wide replacement. Test manual city, allowed/denied location, disabled location services, and offline weather.
- **Library search:** Test categories, global results, accents/prefixes, matched/unmatched/blank queries, calculator, Settings, and opt-in contacts. Apps launch locally; the labeled Web action and IME Search open the chosen provider. Clear keeps focus; Cancel/Back closes search.
- **Fold geometry:** Keep continuous 4×6 home grids with no hinge row. Preserve placements/bindings through rotation/folding. In inner portrait, check the independent bottom-left Search button and its deferred focus/IME.
- **TalkBack and size:** Check icon names, custom actions, search controls, grouped status announcements, large fonts, and cutout clearance. Home labels start hidden and can be enabled.
- **Status controls:** Test 12/24-hour time, live system-panel status, denied/granted signal permission, airplane mode, and Wi-Fi-only hardware. Never display invented signal levels.
- **Theme and motion:** Check Home, Settings, Library, folders, and tour in both themes over bright/dark photos. Inspect glass labels, placeholders, icons, and selections. Reduce motion/system animator Off must be respected; the fold effect is already inactive.
- **Keyboard:** Fold/rotate with search open, dismiss the keyboard, and press Home. Keep search/dock controls reachable throughout.

## Frame-time and continuity checks on hardware

Profile a release build with System Trace/Perfetto and FrameTimeline at supported refresh rates. Compare CPU/GPU deadlines (8.3ms at 120Hz; 16.7ms at 60Hz) during slow/fast folds, widget-heavy paging, and return Home. Check the first expanded layout and deferred widget refresh.

Pause half-open and background the activity: no continuous fold rendering or foreground sensor listeners should remain. Record physical panel blanking separately from app-rendering hitches; One UI owns the panel/compositor switch. An external camera can help distinguish them.

Reset `dumpsys gfxinfo com.wiselion.anotherlauncher`, perform Hub/Home/Library swipes and return Home from an app, then separate deadline misses from cold composition. Confirm widgets retain IDs, remain visible during listener deferral, and refresh after resume.

### Recorded validation status

The October 12 record reports 168 passing JVM tests, compiled debug/instrumentation artifacts, and a verified signed version-code 12 bundle. Lint had zero errors and 40 reviewed warnings: UseKtx (11), ConfigurationScreenWidthHeight (6), ObsoleteSdkInt (6), IconLauncherShape (5), IconDuplicates (5), ModifierParameter (3), and one each of RedundantLabel, AndroidGradlePluginVersion, Wakelock, and IconLocation. No suppressions were added; the timed fold wake lock was retained.

Earlier beta.3 hardware results are historical. Broader provider variants, physical handoff timing, and frame targets still need manual validation.

## Search and widget Hub device checks

`SearchHubInteractionTest` has eight cases and requires an application ID ending in `.qa`; it skips production because it seeds settings and temporarily binds widgets. Build a disposable copy with a distinct label, install app/test APKs with `--user 0`, and grant widget binding only to QA. Never clear, replace, or uninstall the user's Play package for these tests.

Coverage includes repeated Search/focus/IME, app/web actions, clear/empty input, Back/Home, app launch, calculator/Settings, provider/IME launch, weather dialog, widget-only Hub picker, and native widget add/render/remove without changing weather location. Native-fit/touch fixtures run separately. Six wallpaper-sizing unit cases cover memory bounds, aspect ratio, no upscale, invalid sizes, and integer extremes.

Discover preference tests do not apply until a genuine client works. See [DISCOVER.md](DISCOVER.md).

### October 12 device evidence and limits

Tests used `com.wiselion.anotherlauncher.qa` on a Samsung foldable, leaving the Play app and its data unchanged. All eight search/Hub cases passed individually or in groups on the inner display before the final Home guards. Cover runs passed search/IME, app/web results, clear/blank queries, Back/Home, app launch, calculator/Settings, provider launch, and native Hub widget management. Native-fit/touch-cancellation fixtures also passed.

The final cover weather-dialog retest failed after a tap during page animation. A wait was added, but not rerun before the phone was released. Combined runs also had USB/UI Automation disconnects: there is no final all-green combined suite. Latest Home guards have limited device coverage. Repeat the posture matrix before release; photos/presets/application, contacts, TalkBack, and more widget variants remain manual work.

## Streamlined optional permissions

One feature tap in onboarding/Settings should open the OS permission prompt or notification settings directly. Keep the inline explanation complete on narrow screens and large fonts; do not add Agree/Continue dialogs.

- Denied contacts stay off and retryable; denied location keeps manual city search; denied signal leaves the launcher usable.
- Returning from notification settings refreshes access state. Granted permissions must not prompt again.
- Skipping onboarding, navigation, and dismissal must never request access. The manually opened privacy policy remains dismissible.

All five `PermissionFlowTest` cases passed individually on the isolated Samsung QA install: contacts prompt/denial/retry, cellular grant, location denial/manual city, notification-settings return, and privacy-policy dismissal. Only QA was reset; the Play layout and default-Home role were unchanged. Large-font, TalkBack, and already-granted variants remain manual checks.
