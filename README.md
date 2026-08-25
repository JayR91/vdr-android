# VDR for Android

Android port of [VDR](https://github.com/JayR91/VDR), the segmented download
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
- In-app Browse: open a page in WebView; when clear direct media is found, offer download
- Clear HLS / DASH playlist download (no DRM); optional remux of `.ts` /
  playlist concat to `.mp4` (stream copy only, via the platform muxer)

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

**APK size:** ~17 MiB debug, all ABIs included. Remuxing uses the platform's
`MediaExtractor`/`MediaMuxer` rather than a bundled FFmpeg, so the app ships no
third-party native libraries. An earlier build that bundled `ffmpeg-kit-min` for
the same stream-copy remux measured ~32 MiB even with x86 ABIs filtered out;
dropping it removed roughly 15 MiB and restored x86_64 (emulator, Chromebook)
support. The tradeoff is reach: the platform muxer handles the codecs the device
supports in MP4 (H.264/HEVC + AAC, which is what HLS and DASH ship); anything
else fails cleanly and the original download is kept.

## Play Store

Publishing cannot be done from this machine without your Google Play Console
login, a one-time $25 developer registration, identity verification, and a
signing key. Follow `play/PLAYSTORE.md`.

## License

**Proprietary — all rights reserved.** See [LICENSE](LICENSE). The source is
public to read, but it is not open source: redistribution, derivative works,
and store publication need written permission.

The app bundles no GPL or LGPL code. Every runtime dependency is Apache-2.0;
see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
