# Publish VDR to Google Play

I cannot upload the app for you. Play Console requires your Google account,
a paid developer profile, and a signing key that only you should own.

## One-time setup

1. Open [Google Play Console](https://play.google.com/console) and pay the $25 registration fee.
2. Complete identity verification (required for new accounts).
3. Create an app: **Create app** → name `VDR` → default language English → App → Free → declare it's not in the Families program.
4. Host `play/privacy-policy.html` on a public HTTPS URL (GitHub Pages is fine) and paste that URL into the store listing.

## Signing

Create an upload keystore (do this once, keep the file and passwords offline):

```bash
keytool -genkey -v -keystore vdr-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias vdr
```

Put the passwords in `keystore.properties` (gitignored):

```
storeFile=/absolute/path/to/vdr-upload.jks
storePassword=...
keyAlias=vdr
keyPassword=...
```

Then run `./gradlew bundleRelease` and upload
`app/build/outputs/bundle/release/app-release.aab` under **Production** (or
**Internal testing** first).

## Store listing copy

- **Short description:** Fast, resumable segmented file downloads with pause, retry, and battery-aware Focus Guard.
- **Full description:** see `store-listing.txt`
- **Category:** Tools
- **Privacy policy:** required (internet + downloads)
- **Data safety:** no account, no analytics; files the user chose to download are stored on device
- **Photos:** phone and 7-inch tablet screenshots of the download list (Play requires them)

## Policy notes

Do not market this as Internet Download Manager, IDM, or a YouTube downloader.
Those claims will get the listing rejected or taken down.
