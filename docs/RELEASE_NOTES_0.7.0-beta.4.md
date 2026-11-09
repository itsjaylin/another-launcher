# Another Launcher 0.7.0-beta.4 — closed-test candidate

Version code: **9**. Google Play track: **closed testing**.

## Changes for testers

- The App Library now remembers A–Z or category view when you fold, unfold, reopen the library, or restart the launcher.
- **Settings → Dock → Wider inner-screen layout** adds a side dock and an edge-aligned home grid on the unfolded portrait screen. The centered layout with a bottom dock remains the default.
- Cover-screen home icons sit closer to the bottom controls, and the page dots are brighter on light wallpapers.
- **Settings → Web search** offers Google, DuckDuckGo, Brave, and Startpage for Hub web searches. Local app, contact, Settings, and calculator results are unchanged.

## Feedback request

Please try both screens and report your device model, Android version, app version, a screenshot, and steps for any issue. In particular, check widget placement after changing the inner layout and the spacing above the bottom controls on the cover screen.

## Release gate

The signed Play bundle has been built and 162 JVM tests plus Android lint passed. A–Z persistence across folding was confirmed by the developer. The optional inner portrait side layout was visually checked on the QA build. The Play-installed layout was backed up through app Settings and verified before device QA. A DuckDuckGo Hub query opened the correct browser result on the QA build. Cover spacing on varied wallpapers, the other provider browser launches, widget interaction in the wider layout, and a Play-signed upgrade remain to be checked before inviting all testers to this build.
