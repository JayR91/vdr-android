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
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew bundleRelease
```

Upload `app/build/outputs/bundle/release/app-release.aab` to **Internal testing**
first, then promote when the listing, Data safety, and content rating are complete.

## Play Console checklist

Do these in order. Do not upload a production release until Internal testing
installs cleanly.

1. **Create the app** in [Play Console](https://play.google.com/console) → Create app. Package name must be `com.jayr91.vdr`. App name: **VDR**. Default language: English. App or game: App. Free. Declarations: this is not a news app, not a government app.
2. **Store listing** — copy from `store-listing.txt`. Short description: direct HTTP files, resume, clipboard paste, Wi-Fi only. Full description must say VDR downloads **direct files only**, saves to **Downloads/VDR**, and does **not** download YouTube/watch pages. Upload `play/icon-512.png`, `play/feature-graphic-1024x500.png`, plus phone (and 7-inch tablet if you have one) screenshots of the download list. Screenshots are not in-repo — capture from a device and upload under Store listing.
3. **Privacy policy URL** — Play needs a public HTTPS page. Enable GitHub Pages on this repo (Settings → Pages → Deploy from branch `master` / folder `/docs`) and use `https://jayr91.github.io/vdr-android/privacy-policy.html`. Source file: `docs/privacy-policy.html` (same content as `play/privacy-policy.html`). Until Pages is live, host that HTML on any HTTPS site you control. Do not use a raw GitHub URL.
4. **Data safety** — Data collected: none sent off-device to you. No account, no analytics, no advertising ID. Files stay on the device under Downloads/VDR. Clipboard is read locally to offer a URL and is not uploaded. Notifications: download progress only.
5. **Content rating** — start the IARC questionnaire. This is a utility; no user-generated content, no violence, no sharing other people’s personal info. Target: Everyone.
6. **Upload the AAB to Internal testing first** — Testing → Internal testing → Create a new release → upload `app/build/outputs/bundle/release/app-release.aab` (1.1.0 / versionCode 3). Add yourself as a tester, install from the testing link, confirm a direct file downloads to Downloads/VDR and that a YouTube page URL is rejected. Only then promote to Closed / Production.

Countries, ads declaration (no ads), and news/COVID declarations are also required before Production.

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

1. Preferred: enable GitHub Pages on [JayR91/vdr-android](https://github.com/JayR91/vdr-android) → Settings → Pages → Branch `master` / folder `/docs`. Then use `https://jayr91.github.io/vdr-android/privacy-policy.html`.
2. Until Pages is on: paste the HTML from `docs/privacy-policy.html` onto any HTTPS host you control.
3. Avoid relying on GitHub **raw** (`raw.githubusercontent.com/.../privacy-policy.html`) — Play often wants a real page, and browsers show the markup as text. The blob URL is also a GitHub UI page, not a clean policy.

Source in this repo: [docs/privacy-policy.html](https://github.com/JayR91/vdr-android/blob/master/docs/privacy-policy.html) (mirrored at `play/privacy-policy.html`)

## Data safety (Play form)

- App does **not** collect user data for you to “send off the device”
- No account, no analytics, no advertising ID
- Files the user downloads are stored **on the device** (Downloads/VDR)
- Clipboard is read locally to offer a URL; not uploaded
- Notifications: download progress only

## Photos

Phone and 7-inch tablet screenshots of the download list (Play requires them).
High-res icon: `play/icon-512.png`. Feature graphic: `play/feature-graphic-1024x500.png`.
Phone screenshots still need to be captured on a device and uploaded in Console.
