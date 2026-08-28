# Handoff: upload 1.5.9 (vc16) to **Closed testing**

Written 2026-08-28 for whoever drives Play Console (Grok has access; Claude
does not and will not sign in). This answers the "wait for a new release"
note in `PLAYSTORE.md` — that release now exists and is ready.

## The build

| | |
| --- | --- |
| File | `play/artifacts/vdr-1.5.9-vc16.aab` |
| Size | 12,164,284 bytes |
| versionName / versionCode | **1.5.9 / 16** (supersedes 1.5.8 / 15 on Internal) |
| Signing | `CN=VDR Upload, OU=VDR, O=JayR91, C=US` — SHA-256 `3A:13:09:B8:8D:A1:9D:E8:E0:B4:0A:7B:4A:2C:BF:A7:9E:92:87:26:71:CF:07:8B:0F:3E:C5:57:34:39:3A:64` |

That fingerprint matches the 1.5.8 bundle already accepted by Play, so this
upload will not be rejected for a key mismatch.

### ⚠️ This file is NOT in GitHub

`.gitignore` excludes `*.aab`, and `keystore.properties` plus
`~/.android/vdr-upload.jks` are excluded too. So:

- It **cannot** be downloaded from the repo.
- It **cannot** be rebuilt elsewhere — the signing key is local to this Mac.

Read it from the local path above, on this machine. Do not attempt to rebuild
and sign it somewhere else; an unsigned or differently-signed bundle is
rejected.

## Track: **Closed testing**, not Internal

This is the part that is easy to get wrong and expensive to get wrong.

Production is locked for this account (personal, created after Nov 2023).
Unlocking it requires **≥ 12 testers opted in for 14 consecutive days on a
closed test**. **Internal testing does not count toward that at all.** Another
Internal upload looks like progress while the 14-day clock stays at zero.

In the Play Developer API the closed track is named **`alpha`** — not
"closed", which is only the Console's label:

```
internal    Internal testing   does NOT count toward production access
alpha       Closed testing     <-- this one
beta        Open testing
production  Production
```

Via the API (deps already installed on this Mac):

```bash
PLAY_JSON=/path/to/service-account.json TRACK=alpha \
  AAB=play/artifacts/vdr-1.5.9-vc16.aab ./play/upload-internal.sh
```

Or upload the same file through the Console UI under **Closed testing**.

## After the upload

1. Select countries for the closed track.
2. Add **≥ 12 tester Google accounts** and confirm they actually opt in and
   install — the count is of opted-in testers, not invitations sent.
3. Leave it running **14 consecutive days**. Dropping below 12 restarts it.
4. Then **Apply for production** → questionnaire → ~7-day Google review.

Earliest realistic public listing is roughly three weeks from the day step 2
completes. Nothing shortens it.

## Release notes for the track

```
Fixes a crash on Android 8–12 when opening any link, and corrects downloads
that could finish with wrong or corrupted files. Page scan now lists videos
on a page so you can pick one to download; it is free in this release.
```

## Expected warnings (all safe to accept)

- **No deobfuscation file** — `isMinifyEnabled = false`, so no `mapping.txt`
  exists. Harmless.
- **No native debug symbols** — the bundle ships small AndroidX `.so` files
  without a symbols zip. Harmless.

## Do NOT

- Do not upload to **Internal** and expect the production clock to start.
- Do not create the **`vdr_pro`** in-app product yet. It is blocked on BillDesk
  Video KYC, and 1.5.9 deliberately hides the purchase UI while no product
  exists ("Pro is coming soon", no Buy button). It restores the full purchase
  flow by itself once the product goes live — no new build needed then.
- Do not set the app to Paid. It is free with a future one-time IAP.

## What is verified in this build

On a realme RMX3312, Android 13 / API 33, release-signed, not Pro:

- Launch, share-to-download, and segmented reassembly — output byte-identical
  to upstream.
- Page scan on `archive.org/details/BigBuckBunny_124`: 3 videos listed, pick
  one, download → `Downloads/VDR/Videos/`, byte-identical, 4 segments.
- Purchase dialog handles the not-yet-created product without crashing.

48 unit tests green, release lint clean (24 cosmetic warnings).

**Not** verified: API 26–32. The `URLDecoder` crash fixed in this build only
affects those versions, and nothing on hand runs them.
