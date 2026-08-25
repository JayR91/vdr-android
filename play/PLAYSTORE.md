# Publish VDR to Google Play

Play Console requires your Google account. This repo can only prepare the
listing copy, privacy policy, and a signed App Bundle.

**App:** `com.jayr91.vdr` · **Version:** 1.1.0 (`versionCode` 3)  
**Category:** Tools · **Price:** Free · **Default language:** English

## Signing (already created on this machine)

Upload keystore (keep forever; losing it blocks updates):

- Keystore: `~/.android/vdr-upload.jks` (outside git)
- Alias: `vdr`
- Passwords: `keystore.properties` at the repo root (gitignored)

`app/build.gradle.kts` reads `keystore.properties` when that file exists.
Do not commit the `.jks` or `keystore.properties`. Back them up offline.

Build the bundle:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew bundleRelease
```

Upload `app/build/outputs/bundle/release/app-release.aab` to **Internal testing**
first, then promote when the listing, Data safety, and content rating are complete.

## Store listing

Copy from `store-listing.txt`. Honest facts for this build:

- Direct HTTP(S) **files** only — not YouTube or other watch/share pages
- Clipboard paste and Android share-in of text URLs
- Optional **Wi-Fi only**
- Completed files in **Downloads/VDR** (by category)
- No account, ads, or analytics

Do not market this as Internet Download Manager, IDM, or a YouTube downloader.

## Privacy policy URL

Play needs a **public HTTPS page**, not a gitignored file.

1. Preferred: enable GitHub Pages on [JayR91/vdr-android](https://github.com/JayR91/vdr-android) and point it at `/play` (or copy `privacy-policy.html` to `/docs`). Then use `https://jayr91.github.io/vdr-android/privacy-policy.html`.
2. Until Pages is on: paste the HTML from `play/privacy-policy.html` onto any HTTPS host you control.
3. Avoid relying on GitHub **raw** (`raw.githubusercontent.com/.../privacy-policy.html`) — Play often wants a real page, and browsers show the markup as text. The blob URL (`https://github.com/JayR91/vdr-android/blob/main/play/privacy-policy.html`) is also a GitHub UI page, not a clean policy.

Source in this repo: [play/privacy-policy.html](https://github.com/JayR91/vdr-android/blob/main/play/privacy-policy.html)

## Data safety (Play form)

- App does **not** collect user data for you to “send off the device”
- No account, no analytics, no advertising ID
- Files the user downloads are stored **on the device** (Downloads/VDR)
- Clipboard is read locally to offer a URL; not uploaded
- Notifications: download progress only

## Photos

Phone and 7-inch tablet screenshots of the download list (Play requires them).
High-res icon: `play/icon-512.png`. Feature graphic is still required in Console
(1024×500) — create one from the launcher art; this repo does not ship it.
