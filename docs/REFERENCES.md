# Transition reference study

The five supplied repositories were inspected for animation math, trigger choices, rendering ownership and edge behavior. Another Launcher's engine is an original Kotlin/AGSL implementation; no source assets or wallpaper images from these projects are bundled.

| Reference and inspected revision | Files studied | Takeaway applied |
| --- | --- | --- |
| [DuoFoldWallpaper](https://github.com/Vyom-2007/DuoFoldWallpaper/tree/e43f17dd1e9dc85b30e1b7ec4b9e65997da16cb1) | `FoldMath.kt`, `ShaderCode.kt` | Hinge-relative coordinates, smoothstep masks, deriving geometry from metrics, and clamped sample positions to avoid dark edge artifacts |
| [duo_animation_playground](https://github.com/narayann7/duo_animation_playground/tree/c622fb6dd97aa900f72c0fa9b4cfe8b8597c216c) | `lib/config/demo_config.dart` | Explicit direction/axis constraints, restrained haze tint and inspectable tuning controls |
| [duo_animation](https://github.com/narayann7/duo_animation/tree/dfeeefcbdb0e556e0127f2acc471b07e7fb3c37b) | `shaders/duo_animation.frag`, `lib/src/motion/fold_motion_model.dart` | Live subtree shader filtering, optical separation driving blur, finite-value protection, and smoothing between sensor updates |
| [AndroidDuo](https://github.com/JavSaia/AndroidDuo/tree/b42973d4ada0cff4a43c30ee1e3b2ca79490c2c0) | `hook/DuoEngine.java` | RenderEffect on real UI, transform/blur cleanup at detach, and limited blur cost |
| [duo-open](https://github.com/marcoazeem/duo-open/tree/2008747ceaabc22da030e4d93430ee8ed4677a0c) | `fold/TiltFollower.kt`, `fold/HingeAngleSource.kt` | Real TYPE_HINGE_ANGLE events and exponential `dt/τ` following scheduled only while needed |

The Flutter references principally model device **tilt** using orientation/gyro data; those are not physical hinge measurements. Another Launcher uses the standard hinge sensor and WindowManager as its real triggers. The root-hook and system-overlay approaches in other PoCs are unnecessary for rendering a launcher-owned surface.

The visual design is inspired by the supplied Duo demonstrations. The device-independent motion curves here are tunable rather than an assertion of an exact proprietary OS implementation.

## Home UX reference for 0.2 alpha

[DuoLauncher](https://github.com/jakesgoodapps/DuoLauncher/tree/f1bc0f1c5a76637f3fc46a60beed5c331b5fd917) was studied at revision `f1bc0f1c5a76637f3fc46a60beed5c331b5fd917`: `docs/architecture.md`, `LayoutModel.kt`, `Appearance.kt`, and `AppLibrary.kt`. Useful patterns were durable cell/widget identities, a shared four-column grid, native widget gesture ownership, and separating appearance settings from home editing. No source or assets from that repository are bundled.

[Folio](https://github.com/McCal-Codes/folio) and [Lawnchair](https://github.com/LawnchairLauncher/lawnchair) were supplied as additional launcher-behavior references. Folio and DuoLauncher are permissive references; Lawnchair is GPL-3.0 and is used only to understand public behavior and Android edge cases. No Lawnchair source is copied, adapted, linked, or bundled, preserving Another Launcher’s independent licensing options.

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
| [Flex mode](https://developer.samsung.com/galaxy-z/flex-mode.html): content top / controls bottom, visual transition, keep interactive elements off the crease | Compliant: tabletop postures reserve a real hinge row-gap, spanned widgets keep full height, short blur bridge plays on posture change and settles (no persistent effect while reading half-open) |
| [FoldingFeature API](https://developer.android.com/reference/kotlin/androidx/window/layout/FoldingFeature): `isSeparating`/`occlusionType` decide whether content must lay out around the feature | `FoldStateTracker` validates feature bounds against the window (zero-thickness lines valid) and splits wide panes with a 12 dp gutter per side |
| [Landscape-first Fold 8 guidance](https://developer.android.com/blog/posts/optimize-your-apps-for-the-next-generation-of-samsung-galaxy-devices): no hardcoded orientations/sizes, track actual window space, fold-aware layouts | No hardcoded Fold resolutions anywhere; dock stays right under Auto and honors an explicit Left setting, panes derive from live window + `maximumSmallestWidthDp`, hinge placement converts window → workspace coordinates |
| Display-cutout guidance (`shortEdges` so content renders into the cutout area in every orientation) | `Theme.AnotherLauncher` sets `windowLayoutInDisplayCutoutMode=shortEdges`; safe-drawing/cutout/navigation insets reserve interactive UI |

Launcher-parity notes from 2026 Pixel/third-party reviews: type-ahead Enter launches the top match (already the drawer behavior), published app shortcuts surface in long-press menus, notification dots use a listener service with explicit user grant, and widget stacks (already implemented here) remain absent from Pixel itself.

## Additional Architectural & Adaptive Layout References (0.7.0)

The following references are maintained as permissive architectural, ergonomic, and adaptive design guides for ongoing development:

- **[Folio](https://github.com/McCal-Codes/folio)** (MIT License): Studied for Duo/iOS aesthetic styling on foldables. Used as reference for layout inspiration; day/date header styling over wallpaper was explicitly audited and removed in 0.7.0 in favor of a clean, high-contrast dedicated native widget and search canvas.
- **[DuoLauncher](https://github.com/jakesgoodapps/DuoLauncher)** (MIT License): Studied for dual-screen and foldable ergonomics, zero-padding widget hosting patterns, and clean separation of appearance tokens.
- **[react-native-android-widget](https://github.com/sAleksovski/react-native-android-widget)** (MIT License): Studied for Android widget tree layouts, RemoteViews composition, click action dispatch patterns (`clickAction`), and deep-link routing. Informs our widget hosting architecture to ensure external widget structures, touch bounding boxes, and click intents are uniformly preserved without coordinate skewing or duplicate container padding.
- **[adaptive_platform_ui](https://github.com/berkaycatak/adaptive_platform_ui)** (MIT License): Studied for modern platform-adaptive liquid glass UI, multi-layered translucent surfaces, and specular edge reflections. Informs Another Launcher's `Modifier.glass(..., frosted = true)` shader and styling pipeline across homescreen controls and Launcher Settings.
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

No proprietary or non-permissive code or assets are copied or bundled; all open-source patterns are adapted and innovated upon with original Kotlin and Jetpack Compose implementations.

