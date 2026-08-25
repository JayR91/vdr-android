# Third-party notices

VDR for Android bundles no third-party native libraries. Remuxing (MPEG-TS →
MP4, stream copy) uses the Android platform's own `MediaExtractor` and
`MediaMuxer` — see `app/src/main/java/com/jayr91/vdr/engine/Remuxer.kt`.

**No FFmpeg.** Earlier builds bundled `ffmpeg-kit-min` purely to remux. It was
replaced with the platform muxer, which removed roughly 15 MiB of native
libraries and the LGPL relinking obligation those libraries carry. No GPL or
LGPL code ships in this application.

## Runtime dependencies

All of the following are permissively licensed and impose no copyleft
obligation on this application.

| Component | License |
|---|---|
| AndroidX / Jetpack Compose (`androidx.compose.*`, `activity-compose`) | Apache-2.0 |
| AndroidX Lifecycle (`lifecycle-*`) | Apache-2.0 |
| AndroidX Core KTX (`androidx.core:core-ktx`) | Apache-2.0 |
| AndroidX DataStore (`datastore-preferences`) | Apache-2.0 |
| AndroidX Room (`room-runtime`, `room-ktx`, `room-compiler`) | Apache-2.0 |
| OkHttp (`com.squareup.okhttp3:okhttp`) | Apache-2.0 |
| Kotlin standard library and coroutines (`kotlinx-coroutines-android`) | Apache-2.0 |

Test-only dependencies (`junit`, `mockwebserver`, `org.json`) are not shipped in
the released application.

## Apache-2.0 attribution

The Apache License 2.0 is available at
<https://www.apache.org/licenses/LICENSE-2.0>. The components above are used
unmodified, as published.
