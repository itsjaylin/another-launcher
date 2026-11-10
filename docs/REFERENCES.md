# Transition reference study

These references informed the experimental Kotlin/AGSL fold engine, now disconnected from the active workspace. No source assets or wallpapers from these five projects are bundled.

| Reference and inspected revision | Files studied | Takeaway applied |
| --- | --- | --- |
| [DuoFoldWallpaper](https://github.com/Vyom-2007/DuoFoldWallpaper/tree/e43f17dd1e9dc85b30e1b7ec4b9e65997da16cb1) | `FoldMath.kt`, `ShaderCode.kt` | Hinge-relative coordinates, smoothstep masks, deriving geometry from metrics, and clamped sample positions to avoid dark edge artifacts |
| [duo_animation_playground](https://github.com/narayann7/duo_animation_playground/tree/c622fb6dd97aa900f72c0fa9b4cfe8b8597c216c) | `lib/config/demo_config.dart` | Explicit direction/axis constraints, restrained haze tint and inspectable tuning controls |
| [duo_animation](https://github.com/narayann7/duo_animation/tree/dfeeefcbdb0e556e0127f2acc471b07e7fb3c37b) | `shaders/duo_animation.frag`, `lib/src/motion/fold_motion_model.dart` | Live subtree shader filtering, optical separation driving blur, finite-value protection, and smoothing between sensor updates |
| [AndroidDuo](https://github.com/JavSaia/AndroidDuo/tree/b42973d4ada0cff4a43c30ee1e3b2ca79490c2c0) | `hook/DuoEngine.java` | RenderEffect on real UI, transform/blur cleanup at detach, and limited blur cost |
| [duo-open](https://github.com/marcoazeem/duo-open/tree/2008747ceaabc22da030e4d93430ee8ed4677a0c) | `fold/TiltFollower.kt`, `fold/HingeAngleSource.kt` | Real TYPE_HINGE_ANGLE events and exponential `dt/τ` following scheduled only while needed |

The Flutter references principally model device **tilt** using orientation/gyro data; those are not physical hinge measurements. Another Launcher uses the standard hinge sensor and WindowManager as its real triggers. The root-hook and system-overlay approaches in other PoCs are unnecessary for rendering a launcher-owned surface.

The motion curves are tunable experiments, not an exact reproduction of a proprietary OS.

## Home UX reference for 0.2 alpha

[DuoLauncher](https://github.com/jakesgoodapps/DuoLauncher/tree/f1bc0f1c5a76637f3fc46a60beed5c331b5fd917) was studied at revision `f1bc0f1c5a76637f3fc46a60beed5c331b5fd917`: `docs/architecture.md`, `LayoutModel.kt`, `Appearance.kt`, and `AppLibrary.kt`. Patterns studied: durable item IDs, a four-column grid, widget gesture ownership, and separate appearance/editing settings. See [third-party notices](../THIRD_PARTY_NOTICES.md) for attribution and licensing.

[Folio](https://github.com/McCal-Codes/folio) and [Lawnchair](https://github.com/LawnchairLauncher/lawnchair) were supplied as additional launcher-behavior references. Folio declares MIT at the repository root; that does not establish the license or provenance of every component. DuoLauncher declares MIT. Lawnchair is GPL-3.0 and was used only to study public behavior and Android edge cases; no Lawnchair code is copied, adapted, linked, or bundled.

The supplied unfolded and close-up status images informed the two aligned page sections, edge dock/search placement, and time-plus-circular status module. Familiar long-press, pickup, remove-target and page-edge gestures guide direct manipulation; the full configurable gesture systems of other launchers are outside this alpha's scope.

## Widget sizing and menu refinement in 0.4

The Android API 37 `AppWidgetHostView` source was checked to confirm that `updateAppWidgetSize` subtracts framework padding even after a host removes its own padding. DuoLauncher's `ZeroPaddingWidgetHost.kt` was also reviewed as a reference for measured, post-layout size publication. Another Launcher's host additionally uses an independent live-view fitting container with native touch transforms and pure sizing rules.

Pixel Launcher informs the menu hierarchy and Material You presentation: tonal surfaces, recognizable action icons, comfortable rows, compact home controls and restrained reveal motion. This is a stylistic adaptation, not copied Pixel Launcher source or assets.

## Samsung foldable guidance (2026 review)

Reviewed against Samsung Developer and Android guidance for the Galaxy Z Fold 8 generation; Another Launcher's posture column notes compliance, not copied source.

| Samsung / Android guidance | Another Launcher posture |
| --- | --- |
| [App continuity](https://developer.samsung.com/galaxy-z/app-continuity.html): `resizeableActivity="true"`, keep task state across fold/unfold, retain UI state in ViewModel | Compliant: resizable activity, full config-change handling, `LauncherViewModel` + DataStore + `SavedStateHandle` survive display switches; search text, scroll and selected page persist |
| [Continue apps on cover screen](https://developer.samsung.com/codelab/galaxy-z/app-continuity.html) is a **per-app system toggle** (`Settings → Display`) | Surfaced in-app: Settings → Device & integration names the toggle, since a missing opt-in looks like a launcher blackout on close |
| [Designing for foldables](https://developer.samsung.com/one-ui/largescreen-and-foldable/designing_for_foldable.html): fill the screen, keep scroll position, keep text input + keyboard, no letterboxing | Compliant: edge-to-edge, no letterboxing; drawer scroll, query and selected page are `rememberSaveable`/ViewModel state |
| [Flex mode](https://developer.samsung.com/galaxy-z/flex-mode.html): content top / controls bottom, visual transition, keep interactive elements off the crease | Future work: dedicated tabletop layout. Current home grids stay continuous, and the experimental fold blur is inactive |
| [FoldingFeature API](https://developer.android.com/reference/kotlin/androidx/window/layout/FoldingFeature): `isSeparating`/`occlusionType` decide whether content must lay out around the feature | `FoldStateTracker` validates feature bounds against the window (zero-thickness lines valid) and splits wide panes with a 12 dp gutter per side |
| [Landscape-first Fold 8 guidance](https://developer.android.com/blog/posts/optimize-your-apps-for-the-next-generation-of-samsung-galaxy-devices): no hardcoded orientations/sizes, track actual window space, fold-aware layouts | Live metrics and `maximumSmallestWidthDp` drive layouts. Dock side is explicit Left/Right, with bottom as the inner-portrait default; there is no Auto option |
| Display-cutout guidance (`shortEdges` so content renders into the cutout area in every orientation) | `Theme.AnotherLauncher` sets `windowLayoutInDisplayCutoutMode=shortEdges`; safe-drawing/cutout/navigation insets reserve interactive UI |

Behavior references include published app shortcuts in long-press menus, notification access, and widget stacks. Current Library IME Search opens the selected web provider; app results have separate launch actions.

## Additional Architectural & Adaptive Layout References (0.7.0)

Additional layout, widget, and styling references:

- **[Folio](https://github.com/McCal-Codes/folio)**: Foldable/Duo-style layout inspiration. Root license declares MIT; see [the licensing notes](../THIRD_PARTY_NOTICES.md#mccal-codesfolio) for scope and upstream sources.
- **[DuoLauncher](https://github.com/jakesgoodapps/DuoLauncher)** (MIT): Foldable ergonomics, widget sizing, and appearance separation.
- **[react-native-android-widget](https://github.com/sAleksovski/react-native-android-widget)** (MIT): RemoteViews layouts, click actions, and deep links.
- **[adaptive_platform_ui](https://github.com/berkaycatak/adaptive_platform_ui)** (MIT): Layered glass surfaces and edge reflections.
- **[Android ConstraintLayout Foldables](https://developer.android.com/training/constraint-layout/foldables)**: Official developer guide on reactive foldable guidelines, `SharedValues`, and fold-aware constraint positioning.
- **[Android Large Screens Guide](https://developer.android.com/guide/topics/large-screens)**: Best practices for responsive window sizing, multi-window mode, and dynamic metrics handling.
- **[Android UI Design Gallery](https://developer.android.com/design/ui/gallery)**: Material Design and adaptive layout showcase patterns for Android applications.
- **[Support Foldable Display Modes in Compose](https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/support-foldable-display-modes)**: Tabletop, book mode, and posture transitions in Jetpack Compose.
- **[Make Your App Fold-Aware in Compose](https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/make-your-app-fold-aware)**: `FoldingFeature` tracking, hinge occlusion zones, and dual-pane separation.
- **[Saving UI States](https://developer.android.com/topic/libraries/architecture/saving-states)**: Preserving state across process death, task switching, and configuration changes using `SavedStateHandle` and ViewModel state flows.
- **[Handling Runtime Changes](https://developer.android.com/guide/topics/resources/runtime-changes)**: Best practices for fold/unfold, screen size transitions, display density, and orientation changes without restarting activities.
- **Android Developers Video Sessions**:
  - [The future unfolds! How to optimize Android apps for adaptive layouts (`pLNJ-fNYTKU`)](https://www.youtube.com/watch?v=pLNJ-fNYTKU): Guidance on adaptive Jetpack Compose development for foldable and large-screen form factors.
  - [Foldable App Continuity & Multi-window (`-9zVrVmnbO4`)](https://www.youtube.com/watch?v=-9zVrVmnbO4): Form factor transitions and display handoff techniques.

Licenses and preserved notices are in [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

