# VDR for Android

Android port of [VDR](https://github.com/JayR91/VDR), the GPLv3 segmented download
manager. This app keeps the desktop engine’s behavior: HTTP range segments, pause
and resume, checkpoint files, retries, a shared speed cap, category folders, and
Focus Guard (pause on battery / crawl while the app is in the foreground).

YouTube/stream extraction from the desktop app is **not** included. Google Play
rejects apps that download videos from streaming sites in violation of those
sites’ terms. Direct `http`/`https` file URLs still download.

## Features (from the GitHub project)

- Up to 32 parallel byte-range segments
- Pause / resume, including after the app is killed (`.vdrstate.json` sidecar)
- Per-segment retries with backoff
- Global speed limit
- Focus Guard: hold on battery or battery saver, 256 KB/s crawl while you use the phone
- Files grouped into Videos, Documents, Zips, Audio, Images, Other
- Share a URL from the browser into VDR

## Build

Install Android Studio (or JDK 17 + Android SDK), then:

```bash
./gradlew assembleDebug
```

Release bundle for Play Console:

```bash
./gradlew bundleRelease
```

The `.aab` lands in `app/build/outputs/bundle/release/`.

## Play Store

Publishing cannot be done from this machine without your Google Play Console
login, a one-time $25 developer registration, identity verification, and a
signing key. Follow `play/PLAYSTORE.md`.

## License

GNU GPL v3 — same as desktop VDR. See [LICENSE](LICENSE).
