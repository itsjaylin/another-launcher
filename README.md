# Another Launcher

An Android launcher inspired by the **iPhone Duo homescreen launcher**, with added features for foldable phones.

It brings together a spacious home screen, a floating dock, frosted glass controls, and an animated unfold effect.

## Features

- Native Android widgets, resizing, and widget stacks
- A dedicated page for weather and widgets
- An App Library with app search, calculator results, and web search
- Custom pages, folders, dock placement, and colors
- Layout backup and restore

Requires **Android 12 or newer**. The app is no longer on Google Play.

## Download

[Download the APK](https://github.com/itsjaylin/another-launcher/releases/latest/download/Another-Launcher.apk) · [Release notes](https://github.com/itsjaylin/another-launcher/releases/latest)

Open the APK on your phone and allow installation from your browser when Android asks. Then choose Another Launcher as your default home app.

Back up your layout before switching from an existing installation. If Android reports a signing conflict, keep the installed app until you have exported your backup.

## Build it

Open the project in Android Studio, install Android SDK 37, and let Gradle sync. Run the `app` configuration on your device, or build a debug APK:

```sh
./gradlew :app:assembleDebug
```

The APK will be in `app/build/outputs/apk/debug/`. You don't need the original signing keys to build it.

If you're switching from the Play version, export your layout backup first. A build signed with a different key can't update the existing installation.

## Credits

- [McCal-Codes — Folio](https://github.com/McCal-Codes/folio): an early reference for the launcher’s development, foldable home screen, and iPhone-style design
- [jakesgoodapps — DuoLauncher](https://github.com/jakesgoodapps/DuoLauncher): foldable layouts and widget sizing
- [Berkay Catak — adaptive_platform_ui](https://github.com/berkaycatak/adaptive_platform_ui): liquid glass styling

See [third-party notices](THIRD_PARTY_NOTICES.md) for their license notices.

## AI-assisted development

Development started with Fable and continued with help from Astra in Codex.

## Contributing

Bug reports and pull requests are welcome. For bugs, include your device, Android version, and steps to reproduce the problem.

See the [development guidelines](AGENTS.md), [testing notes](docs/TESTING.md), and [roadmap](ROADMAP.md) for technical details.

## License

[MIT](LICENSE). See also the [privacy policy](PRIVACY_POLICY.md).
