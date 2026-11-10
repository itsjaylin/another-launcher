# Google Discover investigation

Checked October 12, 2026 against the source and a Samsung SM-F971U running API 37.

## What was found

The Google app exposes `com.android.launcher3.WINDOW_OVERLAY` through `com.google.android.apps.gsa.nowoverlayservice.DrawerOverlayService`. Service resolution succeeds, but that does not prove Google accepts this launcher or provides a feed session. Another Launcher has no overlay client, companion bridge, or Discover dependency.

No documented public API for embedding the personalized feed was found. AOSP's [LauncherOverlayManager](https://android.googlesource.com/platform/packages/apps/Launcher3/+/bb3af5d904586d8f3165cc10e52e7e445e19ea46/src_plugins/com/android/systemui/plugins/shared/LauncherOverlayManager.java) defines lifecycle/scroll callbacks, not access to Google's feed. [Lawnfeed](https://github.com/LawnchairLauncher/lawnfeed) uses a separate companion and private [bridge](https://github.com/amirzaidi/AIDLBridge/blob/master/app/src/main/java/amirz/aidlbridge/BridgeImpl.java) to bind Google with the companion's identity. The older [launcherclient](https://github.com/LawnchairLauncher/launcherclient) is archived.

## What still needs to work

- A compatible client with suitable licensing and a caller/companion Google accepts.
- A usable Google app/feed/account state, with attachment, scrolling, and lifecycle handling verified on hardware.
- Recovery from service loss, Google updates, folding/window recreation, keyboard/Back, and unavailable feeds.
- A viable companion distribution and compatibility plan. No companion was installed during this investigation.

## Current behavior

Discover is unavailable. The Hub stays a weather/widget page; there is no inactive selector or web view labeled Discover.

Once a real session works, add a saved choice with backup/restore support, preserve Hub content, and fall back to the Hub when unavailable. Test restart/fold behavior before shipping.
