# ZClock

An LSPosed module for **Android 11** that customizes the **status bar clock** and the **lock screen clock**: size, font weight, stretch, position, colour and completely different clock styles.

## Features
- Lock screen styles: stacked digital, bold + thin, analog classic, analog minimal, binary dots, progress ring, outline (plus the stock clock, modified)
- **60 extra lock screen clocks ported from [Iconify](https://github.com/Mahmud0808/Iconify)** ("Iconify 1" ... "Iconify 60")
- Status bar formats: 24h, 12h, seconds, day/date, stacked two-line, or a custom `SimpleDateFormat` pattern
- Size, font weight, width/height stretch, X/Y position, colour, letter spacing
- Live preview and live apply (no reboot needed after the first activation)

## Install
1. Download `ZClock-debug.apk` from the **Releases** page (or from the latest **Actions** run → Artifacts).
2. Install it, open **LSPosed → Modules → ZClock**, enable it and select the **System UI** scope.
3. Restart System UI (or reboot), then open the ZClock app.

## Build
GitHub Actions builds the APK on every push (see `.github/workflows/android.yml`).

Locally: open the folder in Android Studio, or run `gradle assembleDebug` (Gradle 7.5.1, JDK 11).
To add a Gradle wrapper, run `gradle wrapper --gradle-version 7.5.1` and commit the generated files.

To publish a release, push a tag:
```
git tag v1.0
git push origin v1.0
```

## Notes
- Targets stock AOSP SystemUI (`Clock`, `KeyguardClockSwitch`). Manufacturer skins may use different classes.
- Iconify clocks are ported as static layouts: live battery / volume / weather / user-photo widgets from the original are not wired up, so those parts show their placeholder values.
- Requires LSPosed (or any Xposed API 82+ framework).

## License
GPL-3.0, because the Iconify clock layouts are GPL-3.0. See `LICENSE` and `NOTICE.md`.
