# Another Launcher

An Android home launcher inspired by the **iPhone Duo homescreen launcher**, bringing its foldable homescreen aesthetic to Android with added features and customization. Shared-grid home pages, a floating favorites rail, a circular status module, and a live-content unfold effect form the core experience.

## Inspiration and added features

The iPhone Duo homescreen launcher is the main visual inspiration: a spacious layout for foldables, translucent controls, and a distinctive dock and status display. Another Launcher develops that direction with its own Android implementation and features:

- **Native Android widgets:** add, resize, and stack live widgets on your home pages.
- **Dedicated widget Hub:** a separate page for weather and your chosen widgets.
- **Searchable App Library:** find installed apps, calculate results, open Settings shortcuts, search optional contacts, or send a query to your chosen web search provider.
- **Flexible home layouts:** manage and reorder pages, choose a default home, organize folders, and adjust dock placement.
- **Personalization:** frosted glass surfaces, status colors, optional app labels, and notification dots.
- **Backup and restore:** export your layout and settings to JSON.

## Credits and acknowledgments

Another Launcher draws on the work of these creators and projects. Credit goes to them for the ideas, examples, and techniques that helped shape it:

- **[jakesgoodapps — DuoLauncher](https://github.com/jakesgoodapps/DuoLauncher):** dual-screen home layouts, native widget sizing, and launcher organization. Also credited in the app.
- **[Berkay Catak — adaptive_platform_ui](https://github.com/berkaycatak/adaptive_platform_ui):** liquid glass styling, layered reflections, and translucent surface techniques. Also credited in the app.
- **[McCal-Codes — Folio](https://github.com/McCal-Codes/folio):** Duo/iOS-inspired foldable layout and visual references.
- **[Vyom-2007 — DuoFoldWallpaper](https://github.com/Vyom-2007/DuoFoldWallpaper):** hinge-relative animation geometry and fold-effect references.
- **[narayann7 — duo_animation](https://github.com/narayann7/duo_animation)** and **[duo_animation_playground](https://github.com/narayann7/duo_animation_playground):** motion curves, blur, and animation experiments.
- **[JavSaia — AndroidDuo](https://github.com/JavSaia/AndroidDuo):** live UI rendering and transition-effect references.
- **[marcoazeem — duo-open](https://github.com/marcoazeem/duo-open):** hinge sensor handling and motion smoothing references.
- **[sAleksovski — react-native-android-widget](https://github.com/sAleksovski/react-native-android-widget):** Android widget behavior, layout, and interaction references.
- **[Lawnchair contributors](https://github.com/LawnchairLauncher/lawnchair):** launcher behavior and Android edge-case references; consulted for behavior only, with no Lawnchair code bundled.

See [the reference study](docs/REFERENCES.md) for the specific techniques and revisions studied, and [third-party notices](THIRD_PARTY_NOTICES.md) for the license texts accompanying the in-app credits.

**Current source: 0.7.0-beta.5 (version code 12). Open source under the [MIT License](LICENSE). The app is no longer distributed through Google Play.**

**Target:** Android 17 / API 37. **Minimum:** Android 12 / API 31. Geometry comes from the device's actual window and folding feature; there are no hardcoded Fold 8 resolutions.

## Run in Android Studio

1. Open this directory as a project. Use an Android Studio version supporting **Android Gradle Plugin 9.4.1** and its bundled Gradle JDK (JDK 17 or newer supported by Gradle 9.8.0).
2. Install **Android SDK Platform 37** through SDK Manager and let Gradle sync. The included Gradle wrapper downloads **9.8.0**.
3. Select the `app` configuration, connect your foldable, and Run.
4. Hold an empty home cell → **Settings → Set as default home**.

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## Using your home

- **Favorites:** a side rail on the cover and unfolded landscape; a bottom dock on unfolded portrait by default, with an optional wider, edge-aligned inner-screen layout and side rail. Settings offers Left and Right (defaulting to the right edge). Liquid frosted glass surface with up to six favorites.
- **Home options:** hold any unobstructed wallpaper area outside the app/widget grid and Dock to slide up a streamlined bottom dock: **Widgets**, **Wallpaper**, **Pages**, **Edit**, and **Settings**. Material You colors, tonal icon buttons and softer surfaces follow the system palette. Settings stays a full-page screen with Back navigation. Alternatively, tap the dedicated **Another Launcher** app from the App Library or home grid to jump directly into the full homescreen options surface.
- **Add/remove apps:** tap **Add apps** in edit mode or Dock +, select **Home n** or **Dock**, and tap apps in the single unified A–Z list to toggle membership; or drag directly from the library onto the grid. Added apps show a check. **Edit** exposes minus buttons on home/dock placements.
- **Quick options:** hold/release an app or widget to open a small popup attached to it. Published app shortcuts (pinned, manifest and dynamic) lead the menu when the app offers them. Action icons, an app header, grouped destructive actions and a short reveal animation make the menu easier to scan. Home items offer removal, widget resizing, and stack creation; apps also offer Uninstall through Android's confirmation UI. A settings icon appears only where configuration is needed.
- **Arrange:** hold an icon for a haptic pickup, then drag to a cell or Dock. Other icons and widgets automatically slide out of the way while you hover; dropping onto another app performs an effortless 1-to-1 swap. Dropping onto an existing folder adds to the folder, and intentional new folders are created via Edit mode. The follower tracks your finger while a dim duplicate stays at the source until release; cancellation keeps the original placement. Long-pressing a widget picks it up directly without opening its popup, and scrolling inside a widget still belongs to the provider. A Remove drop target appears for existing placements.
- **First-run setup & widget prompts:** on first launch, instead of dumping arbitrary alphabetical apps across the top rows, the home screen presents two 2×2 liquid frosted glass widget prompt cards across Row 0 (`Add widget · 2×2 or 4×2`) with four initial apps cleanly placed on Row 2. Tapping either prompt opens the widget catalog; picking a square 2×2 widget binds that slot while preserving the sibling prompt, whereas picking a wide 4×2 widget automatically clears the sibling prompt so the rectangle spans seamlessly across the top. Prompt cards can also be removed via their minus button in Edit mode.
- **Pages and folders:** every page has a 4×6 grid. Wide inner layouts center two aligned pages with comfortable margins, a continuous 4×6 flow across portrait folds, and a 140dp maximum cell cap. Designate any page as the **Default Home page** with a single tap on the home icon in **Pages** or via **Set as home** in edit mode; the default home persists across page moves, re-entry, and safe deletion. Use **Pages** or edit mode to preview, reorder, add, and remove pages; removing reflows items into free space elsewhere (with Undo). **Remove empty pages** cleans up blanks in one tap. The workspace pager strictly tracks actual pages without trailing ghost slots. The page indicator features a widget glyph for the Hub and spread dots. Create folders through Edit; open a folder → **Edit folder** to rename it and toggle its apps.
- **Hub (Page 0):** a dedicated weather and native-widget page. Swipe right or tap its widget indicator. The existing weather card, weather app picker/settings, startup location behavior, units, refresh, and offline states remain. Add, configure, resize, move, or remove native widgets; bindings and saved Hub placements are preserved. On wide layouts, adding from the Hub explicitly targets the Hub rather than the adjacent Home pane. Genuine Google Discover is not integrated; see [the investigation](docs/DISCOVER.md).
- **App library & search:** the final native page over wallpaper combines ranked installed-app results with a separate **Search Google** (or selected provider) action labeled **Web**, plus calculator results, system Settings shortcuts and opt-in contacts. Settings offers Google, DuckDuckGo, Brave and Startpage; only explicit web actions or the keyboard Search key open the provider. Blank queries never launch web search. Tap an app result to launch it, clear to keep typing, or Cancel/Back to return to browsing; another Back returns Home. The magnifying-glass shortcut is labeled **Search** and focuses the field/opens the keyboard after the page transition, including repeated taps. Existing A–Z/category preference, recent-app dropdown, category overrides, Google Play fallback for unmatched apps, and drag-to-home remain. Shortcut placement follows the existing dock/rail layouts (bottom-left in the default inner portrait layout).
- **Dashboard:** the large digital clock is replaced by a 2×2 Weather tile at the left and a 2×2 analog clock at the right. Search for a city or choose **Use current location**, then toggle between Fahrenheit (°F), Celsius (°C), or Auto (locale default) under **Settings → Weather** for real Open-Meteo data. Location permission is requested only when current-location detection is selected. The clock uses local device time and opens the clock app when tapped.
- **Widgets:** **Widgets** opens a grouped catalog with one entry per home-screen-compatible provider/profile, each variant showing its real framework preview artwork (provider icon when a preview is unavailable). Legitimate size variants stay grouped under their app. Native widgets receive their complete measured content area with no duplicate padding or launcher-imposed corner clip. A widget larger than its slot is uniformly fitted as a live view, retaining working touch controls. New widgets use the actual grid and provider sizing requirements; existing bindings survive the update. If a reinstall or provider removal orphans a binding, the tile is kept and can be rebound in place instead of recreated.
- **Interactive widget resizing:** hold a widget → **Resize** or select it to reveal live edge and corner drag handles (`WidgetResizeOverlay`). A floating "$W \times H$" pill displays the current dimensions. Supports multi-directional resizing with origin coordinate shifting (expanding left or upward), haptic feedback (`TextHandleMove`) on cell crossings, and a 12% hysteresis deadband to prevent flutter.
- **Widget stacks:** navigation controls have their own footer instead of covering the widget. The half-open layout preserves every spanned row rather than shortening widgets at the hinge.
- **Stacks:** hold a home app/widget → **Create stack**, then choose another same-size item. Tap the stack counter (small app stacks) or arrows (larger widget stacks) to cycle contents. **Unstack** restores all items, using another page if needed. Stacking never captures a widget screenshot or replaces its native binding.
- **Names and dots:** home app/folder names are hidden by default. Enable **Settings → Show app names** to restore them; the app library always keeps names and accessibility labels remain available. **Settings → Notification dots** (with explicit system notification-access grant) displays a crisp solid red dot on apps with live notifications across Home, Dock and the library.
- **Backup & restore:** export complete home screen configuration, page layouts, folder structures, pinned favorites, category overrides, and settings to a JSON file via Android Storage Access Framework (**Settings → Backup & restore**). Restore safely with schema validation, rollback protection on error, and preserved widget positions that can be rebound in place.
- **Settings & UI personalization:** full-page **Settings** features a standardized component design system (`SettingGroup`, `SettingSwitchRow`, `SettingSegmentedRow`, `SettingNavigationRow`, `SettingActionRow`, and `SettingStatTile`), category management with one-tap refresh and override reset, and instant search filtering. UI element tinting (Status Bar & Duo Status Pill) includes an interactive Color Picker modal (`ColorPickerModal`) with a live status pill preview (`9:41 • 5G • 88%`), 12 curated swatches (including dynamic wallpaper-adaptive Auto), precision HSV rainbow hue/saturation/brightness sliders, and validated hex code input.
- **Appearance:** menus and surfaces follow Android/Samsung light/dark mode. Features a custom in-app **Wallpaper Picker Sheet** (`WallpaperPickerSheet`) supporting direct photo gallery selection, live previews, target options (Home, Lock, or Both), and 6 built-in HD themes (*Midnight OLED*, *Emerald Velvet*, *Solar Dusk*, *Cobalt Wave*, *Rose Quartz*, *Slate Charcoal*) without 3rd-party chooser popups. Directly layers over the Android/Samsung system wallpaper with optional fallback links to Samsung Dressroom and system settings. Docks and drawers feature a layered liquid-glass finish with palette-specific density, curved top-left reflections, a lower gloss return, and a crisp inner rim tuned separately for light and dark mode. Icons are fetched at high launcher density (up to 384px) with high-quality filtering for retina sharpness. Public wallpaper color metadata determines the home-label foreground; labels include a contrast shadow.
- **Status:** a time label sits above a circular emblem on side-dock layouts. The outer arc indicates battery percentage, the center displays Wi-Fi or active mobile network type (**5G+**, **5G**, **4G**, **3G**, etc.), and four lower dots show cellular signal strength. Features an iconic Apple systemGreen charging indicator (`#34C759`) with gentle pulsing and an ultra-sheer blurred drop shadow matching the time label. Tap for a frosted device-controls panel with live Wi-Fi, power-saving, and airplane-mode states; each switch opens the protected Android control needed to confirm the change. Page swipes keep their visual transform in the render layer to avoid per-frame recomposition, and Home returns defer widget-provider refresh until after the critical first frame.
- **Fold continuity & display handoff:** dynamic screen wake locks and display transition listeners prevent foldable devices (such as Samsung Galaxy Z Fold) from going to sleep when folded shut from the launcher, seamlessly handing off home state to the cover screen.
- **First-run onboarding & Welcome Tour:** fresh installs open an interactive 5-step onboarding guide (`OnboardingScreen`) explaining Another Launcher's 3-tier workspace pager (Hub, Spreads, Library), foldable dual-pane dynamics, live widget resizing, and gestures. Features dynamic light/dark theme contrast (dark typography and icons in light mode) and background workspace blur (`Modifier.blur` + `FLAG_BLUR_BEHIND`) so homescreen widgets and wallpapers never clash with tour content. An interactive permissions wizard configures Default Home, Notification dots, Weather location, Contacts search, and Phone signal status with live checkmarks and complete inline permission explanations. The tour can be replayed at any time from **Settings → Welcome Tour**.
- **Motion:** follows the hinge when exposed by the device; posture/window changes provide a short fallback transition. As the device unfolds, the launcher smoothly settles into place. The scene becomes sharp when motion stops, during touch interaction, and when Reduce motion is enabled.

## Main implementation files

| Source file | Role |
| :--- | :--- |
| `duo/FoldStateTracker.kt` | Activity lifecycle, WindowManager, hinge sensor, display handoff and closing detection |
| `duo/FoldState.kt` | Immutable fold geometry, posture resolution, progress normalization |
| `transition/DuoUnfoldTransition.kt` | Vsync controller, per-layer AGSL, RenderEffect and Canvas fallback |
| `transition/TransitionDynamics.kt` | Clock-injected transition triggers, reversal, smoothing and settling |
| `LauncherActivity.kt` | Home lifecycle, cover screen wake lock, system bars, role/permission requests, config changes |
| `LauncherViewModel.kt` | Persistent launcher state, default page fallback, and transition continuity |
| `ui/HomeScreen.kt` | Live render-layer integration, docking, drawer, drag targets, live resize controller |
| `ui/Workspace.kt` | Hinge-safe panes, responsive home grid (100dp default cap, 140dp optional inner side layout cap), and native widget views |
| `data/HomeLayout.kt`, `data/HomeLayoutCodec.kt`, `data/BackupCodec.kt` | Durable page/item IDs, default page persistence, collision rules, folders, stacks, schema 5 migration, orphaned-binding repair, portable JSON backup/restore |
| `ui/HomeOptionsDock.kt`, `ui/QuickOptions.kt`, `ui/LauncherSettings.kt`, `ui/SettingsComponents.kt` | Streamlined bottom dock, anchored item popups, standardized settings components, interactive color picker, and backup/restore controls |
| `ui/HomeMenus.kt` | Live drag handles overlay (`WidgetResizeOverlay`), floating size pill, origin shifting |
| `ui/PageManagerScreen.kt` | Page preview miniatures, reordering, deletion, and default home page assignment |
| `ui/OnboardingScreen.kt` | Guided 5-step first-run onboarding wizard, permissions setup, and re-playable Welcome Tour |
| `ui/PermissionExplanation.kt`, `ui/PrivacyPolicyDialog.kt` | Inline permission explanations, privacy policy viewer and About dialog |
| `ui/CreditsDialog.kt` | Open-source project credits and repository links for architectures/shaders used in development |
| `data/WeatherRepository.kt` | Hub weather and city-based forecast data |
| `ui/WidgetPickerScreen.kt`, `widgets/WidgetController.kt` | Grouped/deduplicated providers and Android binding/configuration |
| `widgets/LauncherWidgetHostView.kt`, `widgets/WidgetSizing.kt` | Unpadded size reporting, provider-aware fitting, and responsive grid cell sizing |
| `data/AppSearch.kt`, `data/AppCategory.kt`, `ui/LibraryFolders.kt` | Ranked global search, multi-tier smart category classification engine, and App Library folders |
| `duo/WallpaperToneTracker.kt` | Wallpaper color observation and foreground contrast |
| `duo/StatusMonitor.kt`, `duo/DuoStatusPill.kt` | Foreground telemetry, telephony network type (5G/4G), and status emblem |

See [Project Roadmap](ROADMAP.md), [Architecture](docs/ARCHITECTURE.md), [Android Studio testing](docs/TESTING.md), [reference study](docs/REFERENCES.md), [Privacy Policy](PRIVACY_POLICY.md), and [Terms of Service](TERMS_OF_SERVICE.md).

## Checks

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
./gradlew :app:assembleRelease
# With a running emulator or connected device:
./gradlew :app:connectedDebugAndroidTest
```

Current local verification: **168 JVM tests passed; Android lint completed with zero errors**. Device validation uses a separately identified QA package so the Play-installed layout and widget bindings are untouched. See [TESTING.md](docs/TESTING.md) for exact device results and remaining checks. Export and verify a backup before updating an existing install; uninstalling removes local placements and widget bindings.

The unfold effect processes the launcher's live scene and retains logical UI state through display changes. Android/One UI controls when each physical panel becomes visible, so a third-party launcher cannot guarantee a gap-free hardware display handoff. System wallpaper is rendered by Android behind the launcher and is not sampled by the app's unfold effect. Cross-profile/private-space browsing, icon packs, notification badges and fully remappable multi-touch gestures remain tracked in the [Project Roadmap](ROADMAP.md).

Glass styling uses clearer decorative surfaces and more tinted text panels. Hub weather and Library search controls use theme-aware foreground colors in light and dark mode.

Library search places ranked apps first on a tinted glass results panel with solid theme-aware labels, a subtle best-match highlight, and secondary contact/web actions after the apps. Check readability against detailed bright/dark photos in both themes, including no results and keyboard-visible states.

Wallpaper photo decoding now uses a heap-aware pixel budget; decode, rotation and presets run off the UI thread. A single busy state prevents overlapping edits and failures retain the previous selection. Location disclosures describe approximate/precise permission, locally saved coordinates, Open-Meteo requests, and Android geocoding accurately.

Optional permission features explain their data use on the onboarding card or Settings row. One feature tap opens Android's permission prompt or access settings directly; no separate Agree/Continue modal is shown.

## Open-source builds and contributions

Build a debug APK with `./gradlew :app:assembleDebug`. No private signing files are required. Release signing is optional and uses your own local `keystore.properties`; never commit signing credentials or keys. Independently signed builds cannot update the former Play installation. Export your launcher backup before changing installations.

For validation, run `./gradlew :app:testDebugUnitTest :app:lintDebug`. Bug reports and pull requests are welcome. Include device model, Android version, and reproduction steps. See AGENTS.md for development conventions.

Original project code is available under the [MIT License](LICENSE). Third-party components retain their own licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
