# Another Launcher

> I work on this in my free time. So updates are scheduled for whenever life stops opening new tabs.

An Android launcher inspired by the **iPhone Duo homescreen launcher**, with a few added features for foldable phones. A floating dock, frosted glass, and a home screen that moves as you unfold it.

It's off Google Play and now open source. Download it, customize it, and see how it feels.

## Download it

[Get the APK↗](https://github.com/itsjaylin/another-launcher/releases/latest/download/Another-Launcher.apk) · [Release notes↗](https://github.com/itsjaylin/another-launcher/releases/latest)

1. Download the APK on your phone. Requires **Android 12 or newer**.
2. Open it. Android will ask you to allow installs from your browser.
3. Choose Another Launcher as your default home app. Make it yours.

Already using it? Export your layout backup first. If Android says the signatures don't match, keep your existing app until your backup is saved.

## What's in it

- Native Android widgets. Resize them, stack them, move them around.
- A separate page for weather and widgets.
- An App Library with app search, a calculator, and web search. Drag a search result to place it on your home screen.
- Custom pages, folders, dock placement, and colors. Drag an app onto another to group them. Tap the group title to rename, use + to add apps, or A–Z to sort.
- Backup and restore for your layout.

## Build it yourself

Open the project in Android Studio, install Android SDK 37, and let Gradle sync. Run the `app` configuration, or build an APK:

```sh
./gradlew :app:assembleDebug
```

You'll find it in `app/build/outputs/apk/debug/`. No original signing keys needed. A build signed with a different key can't update the Play version.

## Credits

- [jakesgoodapps — DuoLauncher](https://github.com/jakesgoodapps/DuoLauncher): the foundation of the code
- [Berkay Catak — adaptive_platform_ui](https://github.com/berkaycatak/adaptive_platform_ui): liquid glass styling
- [McCal-Codes — Folio](https://github.com/McCal-Codes/folio): inspired features

License notices are [here](THIRD_PARTY_NOTICES.md).

## AI-assisted development

This started with Fable. Astra in Codex helped code it from there.

## Found a bug?

Open an issue with your phone model, Android version, and what happened. Steps to reproduce help. Pull requests are welcome too.

For the technical stuff: [development guidelines](AGENTS.md), [testing notes](docs/TESTING.md), [state and navigation guide](docs/STATE_MACHINES.md), and [roadmap](ROADMAP.md).

## License

[MIT](LICENSE). [Privacy policy](PRIVACY_POLICY.md).
