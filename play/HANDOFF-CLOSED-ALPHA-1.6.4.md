# Handoff: upload 1.6.4 (versionCode 22) to Closed Alpha only

Written 2026-09-24. Play declined production access the same day. Closed testing continues for another 14 days. Android Desk uploads this build to **Closed testing** after it lands. Do not create a Production release. Do not change the upload key.

## What to ship

| | |
| --- | --- |
| versionName | **1.6.4** |
| versionCode | **22** |
| applicationId | `com.jayr91.vdr` (unchanged) |
| Track | **Closed testing**. Play Developer API name: **`alpha`** |
| Language | en-US |

`app/build.gradle.kts` `defaultConfig` is the version source of truth. `AndroidManifest.xml` does not set `versionCode` or `versionName`; the Android Gradle Plugin writes them into the merged manifest at build time.

Committed Gradle before this bump was **20 / 1.6.2**. `play/PLAYSTORE.md` described a local **21 / 1.6.3** billing-library build that was never committed. This release uses **22** so it does not collide with either code.

## Release notes (en-US)

Paste this into the Closed testing release, language **English (United States)**. The same text is in `play/release-notes/en-US/closed-alpha-1.6.4.txt`. `play/upload-internal.sh` does not send release notes.

```
This closed-test build acts on tester feedback. It is a stability and polish update, with the same features as the build you already have. Install it from Closed testing and keep using VDR. Tell us if a download, pause, or resume still feels wrong.
```

## Build

The signing key is not in git. `keystore.properties` and `~/.android/vdr-upload.jks` stay on the machine that already signs VDR. Do not create a new keystore. `*.aab` is gitignored, so this pull request has no bundle attached.

On that machine, from the repo root, with JDK 17 and the Android SDK:

```bash
./gradlew :app:bundleRelease
```

`bundleRelease` is the same task as `./gradlew bundleRelease`. When `keystore.properties` exists, the release build type uses the existing `release` signing config. The signed App Bundle is:

```
app/build/outputs/bundle/release/app-release.aab
```

Confirm the merged manifest before upload (`versionCode` 22, `versionName` 1.6.4, package `com.jayr91.vdr`). A bundle signed with any other key will be rejected.

Optional, after the bundle exists. Play still asks for native debug symbols for the small prebuilt AndroidX libraries:

```bash
./scripts/make-native-symbols.sh app/build/outputs/bundle/release/app-release.aab
```

Attach that zip from the bundle row's menu. It is not required to create the release.

## Upload

Console: **Closed testing**, not Internal and not Production. Internal testing does not count toward production access.

API, only if a service-account JSON is already on that machine. Do not commit the JSON. `TRACK` must be `alpha`. The script defaults to `internal` if you omit it.

```bash
PLAY_JSON=/path/to/service-account.json TRACK=alpha \
  AAB=app/build/outputs/bundle/release/app-release.aab \
  ./play/upload-internal.sh
```

Do not pass `TRACK=production`.

## Not in this bump

- Package name, `applicationId`, and signing config are unchanged.
- `billing-ktx` in this tree is still **7.1.1**. Play asked for **8.0.0 or newer** by 31 Aug 2026. The 1.6.3 note in `play/PLAYSTORE.md` described that migration and the Gradle edit was never committed. This closed-test bump does not include it. If Play rejects the bundle for the billing library, that is a separate change.
