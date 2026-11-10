# Another Launcher 0.7.0-beta.4 — closed-test candidate

Historical notes for version code **9**, originally prepared for Google Play closed testing. Current APKs are linked in the [README](../README.md).

## Changes for testers

- The App Library remembers A–Z or category view across folds, reopening, and restarts.
- **Settings → Dock → Wider inner-screen layout** adds a side dock and an edge-aligned home grid on the unfolded portrait screen. The centered layout with a bottom dock remains the default.
- Cover-screen home icons sit closer to the bottom controls, and the page dots are brighter on light wallpapers.
- **Settings → Web search** offers Google, DuckDuckGo, Brave, and Startpage for Hub web searches. Local app, contact, Settings, and calculator results are unchanged.

## Feedback request

When reporting an issue, include your phone model, Android/app versions, a screenshot, and steps to reproduce. Check widget placement after changing the inner layout and cover-screen spacing above the bottom controls.

## Release gate

The recorded build passed 162 JVM tests and lint. The developer confirmed A–Z persistence across folds; QA checked the wider layout visually and launched a DuckDuckGo Hub query. The Play layout was backed up and verified before QA.

Remaining checks at that point: varied wallpapers, other providers, real widgets in the wider layout, and a Play-signed upgrade. These notes do not claim those checks later passed. Search has since moved from the Hub to the Library.
