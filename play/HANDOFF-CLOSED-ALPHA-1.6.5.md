# Handoff: 1.6.5 (versionCode 23)

Written 2026-09-25. Play asked for more review after the production-access application. This build is the one to put on **Closed testing** (`alpha`) and, once that track is accepted, to send to **Production**.

## What changed

- Play Billing Library **8.0.0** (Play rejects updates still on 7.x after 31 Aug 2026). Reconnects if Play drops the billing service. Purchase flow passes the offer token Billing 8 requires.
- Removed the unused `RECEIVE_BOOT_COMPLETED` permission. There is no boot receiver.
- A shared message with several links no longer queues all of them for a free user. One URL is queued.
- A segmented download that gets HTTP 200 for a byte-range request fails instead of writing the whole file over later segments.
- When the download service stops, paused workers exit without deleting the file or the resume sidecar.

## Version

| | |
| --- | --- |
| versionName | **1.6.5** |
| versionCode | **23** |
| applicationId | `com.jayr91.vdr` |

`app/build.gradle.kts` is the source of truth. 22 was reserved by the unmerged 1.6.4 bump. 21 was a local billing note that was never committed.

## Release notes (en-US)

```
Fixes downloads that could finish with a corrupted file when a server ignored a range request. Sharing several links at once now queues one file on the free plan. Billing uses Play Billing Library 8, which Google requires for updates.
```

## Build

The upload keystore is not in git. On the machine that already signs VDR:

```bash
./gradlew :app:bundleRelease
```

Signed bundle: `app/build/outputs/bundle/release/app-release.aab`

Upload to **Closed testing** first. Do not create a new upload key.
