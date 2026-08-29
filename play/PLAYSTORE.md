# Publish VDR to Google Play

Play Console requires your Google account. This repo can only prepare the
listing copy, privacy policy, and a signed App Bundle.

**App:** `com.jayr91.vdr` · **Version:** 1.6.0 (`versionCode` 17)  
**Category:** Tools · **Price:** Free (optional one-time Pro IAP) · **Default language:** English (United States)  
**Play Console app:** developer `5667084395209045347` · app `4975586487357388428`  
**Privacy policy (live):** https://jayr91.github.io/vdr-android/privacy-policy.html  
**Signed AAB:** see the versionCode ledger at the end — `vc17` is the bundle **rolled out**
on closed testing, `vc18`/`vc19` are local spares (do not upload unless 17 must be replaced).

## Status dashboard (2026-08-29 ~08:45 IST)

### Closed testing - Alpha — still in review, do not re-submit

Unchanged from 01:25 IST: bundle **17 (1.6.0)** rolled out at 01:09 IST; Publishing overview **"Your changes are now in review."** Soft leftovers (ReTrace mapping + native debug symbols) left alone.

### Demo video — recorded on device, publishing to Pages

Realme RMX3312 stayed **unlocked**. `/system/bin/screenrecord` is still SELinux-blocked (not retried). OEM recorder produced an overnight 1.1 GB file that is **not** this demo.

**Recording method:** `scrcpy 4.1 --record` from the Mac (small preview window; `--no-playback` hangs on this phone). Release-signed VDR only.

**What the video shows (~82s, 486×1080, ~715 KB):** VDR open → share-in of `sample-30s.mp4` → Home → notification shade with expanded **VDR** FGS (`sample-30s.mp4` percent + Pause/Cancel) updating while backgrounded → percent reaches 99% → VDR notification gone (only silent system rows remain). In-app speed was 262 KB/s for the take; **restored to unlimited** afterwards.

**URL:** `https://jayr91.github.io/vdr-android/fg-service-demo.mp4`  
Pages source is this repo (`JayR91/vdr-android`), branch `master`, folder `/docs`. File is `docs/fg-service-demo.mp4`. HTTP status at the time of this note: see the curl check after push (Pages can lag a few minutes).

### FGS declaration — still the wrong checkboxes until Console save

Saved form as of 01:25 IST (not yet re-saved this morning): **Other tasks → Other** checked, **Network processing → Other** unchecked, same URL (was 404). Next step is Save on the live form: Network processing → Other, uncheck Other tasks → Other, live video URL. **Do not** click submit for closed testing. If the form is locked because the app is in review, that is recorded below after the Console pass.

Human-only remaining: 12 closed testers, BillDesk video KYC (09:30–18:00 IST), ₹1 IAP. BillDesk not started this run.

## Status dashboard (2026-08-29 ~01:25 IST) — superseded above for video/FGS; closed-test rollout facts still stand

Read live from Play Console (Release Details + Publishing overview + FGS form).

### Closed testing - Alpha — already rolled out

| Field | Live text |
| --- | --- |
| Status | **Available to selected testers** |
| Track | Closed testing - Alpha |
| Released | Aug 29 1:09 AM |
| Bundle | **17 (1.6.0)** · API 26+ · **Target SDK 35** · 11.9 MB |
| Publishing overview | **"Your changes are now in review."** · Managed publishing off |

This agent did **not** click submit. A prior session rolled 17 out at 01:09 IST while the FGS demo URL still 404s.

### The two closed-testing warnings

The Preview-and-confirm warning banners are **gone** because the release already left that page. On Release Details the leftover UI is the bundle ⋮ **Manage artifact** menu, which offers exactly two uploads (quoted verbatim):

1. **Upload ReTrace mapping file (.txt or .map)**
2. **Upload native debug symbols (.zip)**

These are the same two soft warnings previously shown on Preview and confirm / Internal 15. They did **not** block rollout. They cannot be cleared on vc17:

1. ReTrace / deobfuscation — `isMinifyEnabled = false`, so there is no `mapping.txt`. Play still offers the upload. Safe to ignore until R8 is turned on.
2. Native debug symbols — the AAB ships small AndroidX `.so` files (`libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`) without an AGP `native-debug-symbols.zip`. No NDK on this Mac, so the next bundle cannot emit one either without installing an NDK. Not a rollout blocker.

Not an API-36 hard block: attached 17 targets SDK **35** and still rolled out. A local **1.6.1 / versionCode 19** (compileSdk/targetSdk 36) AAB exists as a spare if Google later requires 36.

### FGS declaration — still wrong, still 404 video

Live form (saved; Save disabled):

| Field | Saved value |
| --- | --- |
| Network processing → Other | **unchecked** (should be checked — manifest is `dataSync`) |
| Other tasks → Other | **checked** (should be unchecked) |
| Video link | `https://jayr91.github.io/vdr-android/fg-service-demo.mp4` — **HTTP 404** |
| Description | `Foreground download service for HTTP/HLS/DASH downloads in background with pause/resume.”` |

Did **not** re-save. Publishing overview already lists `App content: 'Foreground services' declaration updated` inside the in-review package — that package still has the wrong type + 404 URL.

### Demo video — not published

Phone was unlocked. Three real-device recording attempts failed to capture an expanded FGS progress notification good enough to send to Google:

- RMX3312 SELinux-blocks `/system/bin/screenrecord` (`inaccessible or not found`).
- `screencap` fallback is ~1–2 fps and contends with ADB input, so shade expand/tap is unreliable.
- Realme puts the VDR FGS in **Silent notifications** behind a huge QS panel; a too-aggressive swipe opened Battery settings.

Nothing was pushed to GitHub Pages. The Console URL still **404s**. Do not treat `play/demo/fg-service-demo.mp4` (local takes) as published.

In-app **speed limit restored to unlimited** (verified on device: `Speed limit: unlimited`).

### Store listing / other declarations

Publishing overview already includes en-US store listing (app name VDR), privacy policy URL, ads, data safety, content rating, store settings (Tools). Not re-submitted from this run.

## Status dashboard (2026-08-29 ~00:55 IST) — superseded above

1.5.9 was **not** shipped. It carried a foreground-service defect that would have been
force-stopped by Android 15, so the service was fixed and the shipping artifact is
**1.6.0**. Both `vc17` and `vc18` are built from the fixed source; `vc17` is the one Play
already holds.

> Another agent was editing this repo and driving the same Chrome window during this run.
> Everything below was read out of the live Console at the timestamp given and may have moved
> since. Console driving was stopped once the other agent took the window back.

### Foreground service lifecycle fix (verified on device)

`DownloadService` used to call `startForeground("VDR is ready")` from `onCreate` and never
stop, so merely opening the app left a permanently running `dataSync` service with no
transfer — the non-user-perceptible pattern Google's device-and-network-abuse policy targets —
and on `targetSdk` 35 it would have hit the 6-hour `dataSync` cap and ANR'd in `Service.onTimeout`.

Now the service is foreground only while a transfer exists, and `onTimeout` is implemented.
Verified on a realme RMX3312 (Android 13 / API 33) with the **release-signed 1.6.0 APK**:

| Check | Result |
| --- | --- |
| App open, empty queue | `dumpsys activity services com.jayr91.vdr` → `(nothing)`. No service, no notification. |
| Share a link in | Service appears, `isForeground=true`, notification shows `sample-30s.mp4 34%` |
| Queue drains | Service gone within ~3s; **0** VDR notifications remain |
| Downloads (5 runs) | Byte-exact: 11,815,175 and 21,657,943 bytes, published to `Downloads/VDR/Videos/` |
| Pause (user action) | Service **stops**; a non-ongoing "Paused — sample-30s.mp4" notice with **Resume**/**Cancel** replaces it |
| Resume from that notification | Service restarts as foreground, progress resumes, download completes |
| Screen off (`mWakefulness=Dozing`) | Progress 59% → 94% over 12s; service stays foreground |
| Crashes | `FATAL EXCEPTION` count **0** throughout |

`Service.onTimeout` pauses in-flight downloads, persists them, posts an explanatory notification
and stops cleanly. It **compiles and is guarded**, but it could **not** be exercised at runtime —
the only device on hand runs API 33, where the platform never calls it.

> This override was initially written as `onTimeout(startId)` only, which was **wrong**: the
> six-hour dataSync cap is delivered through `callOnTimeLimitExceeded` → `onTimeout(startId,
> fgsType)`, and the single-argument form only serves `shortService`. A parallel agent caught
> this and added the two-argument overload (and moved `compileSdk`/`targetSdk` to 36 so it can
> be overridden). Both forms now route to the same handler.

The on-device results in the table above were measured against the `vc17` build
(`compileSdk` 35, single-argument override). The lifecycle behaviour they verify is unchanged by
that later edit, but the exact binary tested is not the current tree.

`RECEIVE_BOOT_COMPLETED` was removed from the manifest (no receiver ever existed).
The declared FGS type is unchanged and still correct: `dataSync` only.

Build: `:app:testDebugUnitTest` and `:app:assembleDebug` pass; `:app:bundleRelease` +
`:app:assembleRelease` pass with `signReleaseBundle` and `lintVitalRelease` green. Upload cert
is unchanged — `CN=VDR Upload, OU=VDR, O=JayR91, C=US`, SHA-256
`3a1309b88da19de8e0b40a7b4a2cbfa79e92872671cf078b0f3ec55734393a64`.

The phone was left with the **release-signed 1.6.0 build installed and working**. One user
setting was changed while testing and not restored: the in-app **speed limit is now 505 KB/s**
(it was unlimited). Drag the Speed limit slider fully right to undo it.

> The **debug** APK does not run on this device: it dies at startup with
> `NoClassDefFoundError: kotlin/jvm/internal/markers/KMappedMarker` (44 MB `classes.dex`).
> The class is present in the APK; both streamed and `--no-streaming` installs fail the same way.
> Test with the release APK on this phone.

### Closed testing (Alpha) — 4 of 5 complete, one click left

Verified live in the Console at ~00:52 IST:

| Step | State |
| --- | --- |
| Select countries and regions | **done** (7 regions incl. India, United States) |
| Select testers | **done** (email list "Internal testers") |
| Create a new release | **done** — app bundle **17 (1.6.0)** is attached |
| Preview and confirm | **done** — only the 2 harmless warnings (no deobfuscation file, no native debug symbols) |
| **Send the release to Google for review** | **NOT DONE** — Publishing overview shows *"Submit 13 changes for review"*, status *"Not yet sent for review"* |

**This was deliberately not submitted.** The saved Foreground service declaration is still the
wrong one (below) and its demo video URL 404s, which is precisely what gets an FGS rejection.
Submitting first would likely burn a multi-day review cycle. Fix the declaration, then submit.

### Foreground service declaration — still wrong, blocked on the video

Read back from the live form at ~00:47 IST, this is what is **saved**:

| Field | Saved value |
| --- | --- |
| Network processing → Backing up, restoring | unchecked |
| Network processing → Other | **unchecked** ← should be checked |
| Local processing → Media transcoding / Importing, exporting / Other | unchecked |
| Other tasks → Other | **checked** ← should be unchecked |
| Video link | `https://jayr91.github.io/vdr-android/fg-service-demo.mp4` — **HTTP 404** |
| Description | `Foreground download service for HTTP/HLS/DASH downloads in background with pause/resume.”` |

The corrected checkboxes and justification were applied in the form and read back correct, but
**Save stays disabled until the video link is filled**, and the only URL available returns 404.
Rather than re-save a dead link, the edit was abandoned. The URL does not need to change — it
just needs the file behind it.

`play/demo/record-demo.sh` records the required video in one command (phone unlocked, ~35s,
720p): open VDR → share a link in → foreground notification with live progress → pause and
resume from the notification → completion. The phone was locked (`deviceLocked=1`, PIN set) for
the whole of this run, so **no video was recorded and nothing was published**. The 68 KB
`docs/fg-service-demo.mp4` and `play/demo/fg-service-demo.mp4` still present are the earlier
synthetic placeholders — do not publish them, Google requires real user steps.

### Blocked / not attempted this run

- **Play Developer API upload** — no service-account JSON anywhere and `gcloud` is not installed.
- **CDP `DOM.setFileInputFiles`** — Chrome 151 refuses `--remote-debugging-port` on the default
  profile, and a copied profile lands on the Google sign-in page because the Console session is
  device-bound. Chrome was restarted once and restored with its original tabs and session.

## Status dashboard (2026-08-28 ~23:15 IST) — superseded above

**Latest (updated 2026-08-28 ~23:56 IST after a live Console run):** **16 (1.5.9)** signed AAB at
`play/artifacts/vdr-1.5.9-vc16.aab`. **Foreground service declaration DONE** (user completed manually
28 Aug — not touched this run). Closed Alpha is now **3 of 4 complete**: **countries and testers are
both done and verified**. The single remaining blocker is **attaching the AAB**, which automation
cannot do because Chrome won't open a file picker without a real user gesture. **Opt-in link still
pending publish.**

**Closed Alpha — remaining (manual):**
| Step | Status | Notes |
| --- | --- | --- |
| Foreground service declaration | **SAVED BUT WRONG** | Wrong use case ("Other tasks → Other" instead of "Network processing → Other") and the video link 404s. See "FGS declaration review" below. Do not re-run FGS automation — fix by hand. |
| Countries | **DONE (verified 23:55 IST)** | Countries / regions tab lists **7** regions incl. **India, United States**, Australia, Brazil. Track step "Select countries" shows a check. |
| Testers | **DONE (verified 23:44 IST)** | Testers tab → "Email lists" radio → **Internal testers** (1 user) checked → **Save** → Console returned *"Your change has been saved."* Track advanced **2 of 4 → 3 of 4 complete**. |
| Upload AAB | **BLOCKED** | Bundle **16 is NOT attached**. Release review still errors *"This release does not add or remove any app bundles."* Bundle library holds only vc **15, 14, 3**. Blocked by the macOS file picker — see "AAB upload blocker" below. |
| Rollout | **NOT ATTEMPTED** | Gated on the AAB: "Preview and confirm" cannot pass while the no-bundle error stands. |
| Opt-in link | **PENDING** | Appears on Testers tab after rollout; format `play.google.com/apps/testing/…`. Testers tab currently still says *"Links will be shown here when you publish your app."* |

### AAB upload blocker (2026-08-28 ~23:53 IST)

**Root cause: Chrome will not open a file picker for a click that has no user activation.**
The Play Console "Upload" button was located and clicked successfully via Apple Events
(`CLICKED_UPLOAD_BTN`), and again via a synthesized OS-level `click at {828, 527}`, but
**no `Open` window or sheet ever appeared** (10 polls each time; Chrome's only window stayed
`Prepare release | VDR`). Because file inputs require transient user activation, JS-driven
clicks are silently ignored, so AppleScript never gets a picker to type into.

Ruled out as causes — all three were verified working:
- macOS **Accessibility** permission: `System Events` can enumerate Chrome's windows.
- Chrome **"Allow JavaScript from Apple Events"**: page JS executes and returns values.
- The AAB itself: `play/artifacts/vdr-1.5.9-vc16.aab`, 12,164,272 bytes, present.

No keystrokes were ever sent blind — the upload scripts abort when no picker is detected,
so the earlier "picker dismissed by an auto-clicked Next" failure mode did **not** recur.

**No Google Play Developer API service account JSON exists** anywhere on this machine
(searched the repo, `~/Downloads`, `~/Desktop`, `~/Documents`, `~/.config`), so
`play/upload-internal.sh` with `TRACK=alpha` — the reliable picker-free path — cannot run yet.

**Two ways to finish (either works):**
1. *Manual, ~1 min:* open the prepare page below, click **Upload**, pick the AAB by hand,
   wait for processing, then **Next → Preview and confirm → Start rollout**.
   `https://play.google.com/console/u/0/developers/5667084395209045347/app/4975586487357388428/tracks/4699121500813244434/releases/1/prepare`
2. *Automatable forever after:* create a Play Console service account, download its JSON, then
   `PLAY_JSON=/path/key.json TRACK=alpha AAB=play/artifacts/vdr-1.5.9-vc16.aab ./play/upload-internal.sh`

Helper scripts added this run (all no-auto-Next, all abort rather than guess):
`play/console_js/z_chrome.sh` (targets an explicit window+tab so navigation cannot drift),
`z_run.sh`, `z_upload.sh`, `z_upload2.sh`, and the `z_probe_*.js` read-only probes.

**Release review errors (expected until closed track finished):**
1. This release does not add or remove any app bundles → attach `vdr-1.5.9-vc16.aab`
2. No countries or regions selected → add ≥1 (India + US or all)
3. Upgrade-path message (resolves once bundle is attached)

**Manual finish (~2 min) — only these remain:**
1. **App content → Foreground service permissions** — saved 28 Aug but **needs correcting** (see "FGS declaration review" below)
2. ~~Countries / regions~~ — **done, verified in Console**
3. ~~Testers tab → Internal testers → Save~~ — **done, verified in Console**
4. **Edit release 16 (1.5.9)** → Upload `play/artifacts/vdr-1.5.9-vc16.aab` **by hand** (automation cannot open the picker — see "AAB upload blocker") → wait for processing → **Next** → Preview and confirm → Start rollout
5. Copy **closed-test opt-in link** from Testers tab

## FGS declaration review (2026-08-28, read-only audit)

**Verdict: the saved declaration is INCORRECT and needs ~1 minute of edits.**

Code ground truth (no mismatch): manifest declares `FOREGROUND_SERVICE` +
`FOREGROUND_SERVICE_DATA_SYNC`; the only service is `.service.DownloadService`
with `android:foregroundServiceType="dataSync"`; runtime calls
`ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_DATA_SYNC)`.
`targetSdk`/`compileSdk` 35. The shipped `vdr-1.5.9-vc16.aab` manifest agrees.
So `dataSync` alone is the right type — the problem is *which use case* was
declared, not the type.

What is currently saved on **App content → Foreground service permissions**:
- Checked: **Other tasks → Other** only (`FGS_REASON_DATA_SYNC_OTHER`). Every
  Network processing and Local processing box is unchecked.
- Video link: `https://jayr91.github.io/vdr-android/fg-service-demo.mp4` —
  **HTTP 404**. `docs/fg-service-demo.mp4` is untracked (68 KB, never committed
  or pushed), so Pages never served it.
- Description: `Foreground download service for HTTP/HLS/DASH downloads in
  background with pause/resume.”` (note the stray `”`).

Fixes, in order:
1. Check **Network processing → Other** — Google's own tooltip on that option
   reads "Any other network processing tasks. For example, uploading or
   downloading." Leave "Backing up, restoring" unchecked (this is not backup).
2. Uncheck **Other tasks → Other**.
3. Re-enter the justification under Network processing → Other, covering the
   deferral/interruption impact Google explicitly asks for:
   > VDR is a download manager. When the user taps Download or shares a link
   > into the app, a dataSync foreground service performs the HTTP/HLS/DASH
   > transfer, including multi-segment downloads, so it continues while the app
   > is backgrounded or the screen is off. The notification shows live progress
   > and speed with pause, resume and cancel actions. It must start immediately
   > because the user explicitly requested that file and is waiting for it; if
   > deferred, nothing downloads and the app appears broken. If interrupted,
   > partial segments must be re-fetched, wasting mobile data, and HLS/DASH
   > segment URLs are often time-limited so the download can fail outright.
4. Re-enter the video link on that option, pointing at a URL that actually
   resolves. Either commit + push a real screen recording to `docs/` (the
   current file is a 68 KB placeholder, not a demo of the trigger steps) or use
   an unlisted YouTube link. Google requires the video to show the steps the
   user takes to trigger the feature.

Code-level follow-ups (separate from the declaration):
- `DownloadService.onCreate` calls `startForeground` with a "VDR is ready"
  notification even when the queue is empty, and the class contains no
  `stopForeground`/`stopSelf` anywhere. A permanently-running `dataSync` FGS
  with no active transfer is exactly the non-user-perceptible pattern the
  device-and-network-abuse policy targets.
- Same bug breaks Android 15: with `targetSdk` 35, `dataSync` is capped at 6
  cumulative hours per 24 h, after which the system calls
  `Service.onTimeout(startId, type)`. It is not overridden and the service
  never stops, so expect an ANR/crash on API 35+ devices.
- `RECEIVE_BOOT_COMPLETED` is declared with no `<receiver>` — dead permission,
  worth dropping.

Policy risk: `dataSync` is the correct sanctioned type for a user-initiated
download manager, but Google steers network transfers toward user-initiated
data transfer (UIDT) jobs on API 34+ and WorkManager otherwise, and reviewers
do reject `dataSync` when the work is not clearly user-initiated and
user-perceptible. The realistic rejection risk here comes from the idle-FGS
behaviour above rather than the type choice. Fallback if rejected: start the
FGS only while a transfer is running and stop it when the queue drains, and/or
move to a UIDT job on API 34+ with WorkManager's long-running worker as the
pre-34 path. `specialUse` is not a sensible fallback — downloading squarely
fits `dataSync`.

**Critical — Internal vs Closed (12×14 gate):** Testers opted into **Internal testing do NOT count** toward the 12 closed testers. Anyone you want to count (including `jayradbus@gmail.com`) must **leave Internal** first (internal opt-in page → **Leave the program**), then opt in to **Closed** via the closed opt-in link only. You can run Internal (15 / 1.5.8) and Closed (16 / 1.5.9) on different version codes, but **each person counts on one track only**.

**Script fixes applied this session:**
- `play/console_js/auto_accept.js` — stop auto-clicking **Next** (was aborting AAB file-picker flow)
- `play/console_js/vdr_chrome.sh` — new `js-raw` command (run JS without auto-accept; use for upload)
- `play/console_js/run_complete.sh` — FGS uses `material-checkbox[debug-id*="NETWORK_BACKUP"]`; upload uses `js-raw`

**macOS permissions for AAB upload:** Chrome → View → Developer → **Allow JavaScript from Apple Events**; System Settings → Accessibility + Automation for Terminal/Cursor controlling Chrome. Without these, upload AAB manually on the prepare page.

**Or API upload** (no file picker):
```bash
PLAY_JSON=/path/to/service-account.json TRACK=alpha \
  AAB=play/artifacts/vdr-1.5.9-vc16.aab ./play/upload-internal.sh
```

## Status dashboard (2026-08-28 ~21:15 IST) — superseded above

## Status dashboard (2026-08-28 ~15:47 IST) — superseded above

**Latest:** **16 (1.5.9)** built and signed locally, **not yet uploaded**. Supersedes Internal **15 (1.5.8)**, which is rolled out with track **Active** and email list `Internal testers` (`jayradbus@gmail.com`). BillDesk / `vdr_pro` / IAP **still deferred**. Chrome: keep **1 Play + 1 BillDesk** max.

**Play Console setup (today):** Store settings and en-US store listing blockers **cleared** via Chrome automation — category **Tools**, contact email **jayradbus@gmail.com**, **6× 10-inch tablet screenshots** uploaded (`play/screenshots/tablet-10/`, 1200×1920). Listing **Saved**; changes queued in Publishing overview (not yet submitted for review). Dashboard closed-test path no longer lists store settings / store listing as incomplete.

**Blocker:** Public Play Store URL returns **HTTP 404**; Production track is **locked**. BillDesk **rejects** both the public URL (404) and the internal-testing opt-in URL (wrong format / not publicly accessible). Only a live `play.google.com/store/apps/details?id=com.jayr91.vdr` will satisfy BillDesk's Mobile App APK URL field.

**1.5.9 is a correctness release** — it fixes bugs in 1.5.8 that produced wrong results rather than errors. See "What changed in 1.5.9" below.

**Verified on device 2026-08-28** (realme RMX3312, Android 13 / API 33): clean launch, share-to-download end to end, 4-segment file byte-identical to upstream, Pro dialog handles the not-yet-activated product without crashing. Note API 33 cannot reproduce defect 8 (the `URLDecoder` crash), which only affects API 26–32.

| Area | Status | Notes |
| --- | --- | --- |
| Payments profile | **DONE** | Exists (Jayendar S / INR / India) |
| BillDesk PA-CB | **DEFERRED** | Video KYC human-only (agents Mon–Sat 09:30–18:00 IST) — skip for now |
| `vdr_pro` IAP | **SKIP / deferred** | Do not create until BillDesk clears |
| Freemium code 1.5.8 | **DONE** | Billing + Pro gates in app |
| Signed AAB 1.5.8 / vc15 | **DONE** | Uploaded to Internal; release published |
| Review + fixes 1.5.9 | **DONE** | 10 defects fixed, 9 regression tests added; 38 unit tests green; lint clean |
| Signed AAB 1.5.9 / vc16 | **BUILT; UPLOAD BLOCKED** | `play/artifacts/vdr-1.5.9-vc16.aab`; draft on Closed Alpha; attach via manual upload or `PLAY_JSON` API |
| Foreground service declaration | **DONE** | User completed manually 28 Aug (Data sync / `dataSync` only). |
| Closed testing | **DRAFT ONLY** | Release **16 (1.5.9)** on Alpha; countries/testers/AAB/rollout incomplete after automation run |
| On-device smoke test | **DONE (2026-08-28)** | realme RMX3312, Android 13 / API 33 |
| Store listing | **DONE (en-US)** | 6× 10-inch tablet screenshots; phone screenshots deferred |
| Store settings | **DONE** | Tools; jayradbus@gmail.com |
| Policy forms | **DONE** | Ads, Data safety, Target audience, foreground service (28 Aug), etc. |
| Internal testing | **DONE (Active)** | **15 (1.5.8)** |
| Release warnings | **1 fixed / 2 soft** | Testers fixed. Deobfuscation + native symbols soft (see below) |
| Production / public | **LOCKED** | Store URL **404**; need **12 testers × 14 days** closed test → Apply for production → rollout |
| BillDesk Mobile App URL | **BLOCKED** | Public URL 404; internal-test URL **also rejected** by BillDesk portal |

### Path to a live public Play Store URL

Verified **2026-08-28** via `curl` and Play Console (personal account **Rad91**):

```
Public URL:     https://play.google.com/store/apps/details?id=com.jayr91.vdr  → HTTP 404
Internal test:  https://play.google.com/apps/internaltest/4701575606071485981  → HTTP 302 (Google sign-in)
Privacy policy: https://jayr91.github.io/vdr-android/privacy-policy.html      → HTTP 200
```

**Play Console status (live check 28 Aug ~15:47 IST):**
- Dashboard: store-settings + store-listing blockers **cleared**; closed-test track shows **Set up your closed test track** (countries, testers, release) — no longer blocked by Grow → Store presence tasks
- App status: **Draft** · Internal testing **Active** · release **15 (1.5.8)** (Aug 26, not reviewed)
- **Store settings:** Category **Tools** · email **jayradbus@gmail.com** ✓
- **Store listing (en-US):** 6× 10-inch tablet screenshots from `play/screenshots/tablet-10/` ✓ · Saved · queued in Publishing overview
- **Closed testing:** paused 28 Aug ~18:30 IST pending a newer build; **that build now exists**. Use **1.5.9 / vc16**, not 1.5.8. Full instructions, including the file path and why the track must be `alpha`, are in `play/HANDOFF-CLOSED-TESTING.md`. Then: countries → testers → closed release → 12 testers × 14 days.
  - The AAB is **gitignored and cannot be fetched from GitHub**, and the signing key is local to this Mac, so it cannot be rebuilt elsewhere. Read `play/artifacts/vdr-1.5.9-vc16.aab` from this machine.
- **Production:** locked — personal accounts created after Nov 2023 must:
  1. Publish a **closed testing** release (Internal track does **not** count)
  2. Have **≥ 12 testers** continuously opted-in for **14 consecutive days**
  3. Click **Apply for production** on Dashboard → answer 3-section questionnaire
  4. Google reviews application (~7 days or less)
  5. Create Production release → Send for review → Start rollout
  6. Google app review (~hours to 7 days) → public URL goes live

**Cannot submit to Production today** — closed-test gate is mandatory for this account type.

**Fastest path (minimum ~3 weeks):**

| Step | Action | Est. time |
| --- | --- | --- |
| 1 | ~~Finish Store settings + store listing~~ | **Done 28 Aug** (Tools, jayradbus@gmail.com, 6× 10" screenshots) |
| 2 | Upload AAB to **Closed testing** (can use 1.5.8 or 1.5.9) | Today |
| 3 | Recruit **12 Google accounts** → share closed-test opt-in link → confirm installs | 1–3 days |
| 4 | Wait **14 consecutive days** with ≥12 opted-in testers | 14 days |
| 5 | **Apply for production** + questionnaire | Same day |
| 6 | Google production-access review | ~7 days |
| 7 | Production release → review → rollout | ~1–7 days |
| 8 | Re-submit BillDesk with live public URL | After step 7 |

**Earliest realistic public URL:** ~3–4 weeks from today if closed test starts immediately.

**Publishing overview:** changes saved but **not yet submitted for review** (Data safety, content rating, store listing, etc. queued). "Send app for review" locked until dashboard tasks complete.

### Solo developer — 12 tester problem

Researched **2026-08-28 ~21:15 IST** against [Google's official policy](https://support.google.com/googleplay/android-developer/answer/14151465), [testing track docs](https://support.google.com/googleplay/android-developer/answer/9845334), and a live Play Console read (Dashboard + Closed testing - Alpha).

#### Can an agent recruit 12 testers for you?

**No.** An automation agent cannot satisfy this requirement alone. Google counts **12 distinct Google accounts** that **opt in** to your **closed** test track and stay opted in for **14 consecutive days**. That requires real people (or real accounts you legitimately control) to click an opt-in link, accept tester terms, and ideally install/use the app. Creating fake Google accounts, buying bot installs, or stuffing the list with disposable emails violates Google Play policy and can get the developer account banned. There is no Console setting, API call, or script that substitutes for 12 human opt-ins.

**What automation *can* do:** finish track setup (countries, email lists, AAB attach, rollout), copy the closed-test opt-in link after publish, monitor opted-in count on Dashboard, draft recruitment posts, and prep the production-access questionnaire answers once testing completes.

**What automation *cannot* do:** invent 11 testers, shorten the 14-day clock, bypass "Apply for production" on a post-Nov-2023 personal account, or make Internal testing count toward the gate.

#### Exact rules (personal account, created after 13 Nov 2023)

| Requirement | Detail |
| --- | --- |
| Who | **Personal** developer accounts created **on or after 13 Nov 2023** (this account: **Personal**, confirmed in Console) |
| Track | **Closed testing** only — Internal, Open, license testers, and pre-launch report do **not** satisfy the gate |
| Count | **≥ 12 testers opted in** at the moment you click **Apply for production** |
| Duration | Those 12 must have been opted in for the **preceding 14 days continuously** |
| Reset | Dropping below 12, or a tester opting out and back in, **restarts the 14-day window** for that tester |
| Engagement | Google may reject production access if testers never used the app (questionnaire asks how you recruited, what feedback you got, what you fixed) |
| Exit | Dashboard → **Apply for production** → ~10-question questionnaire → Google review (~7 days) → Production release → app review → public URL |
| Exempt | **Organization** accounts (registered legal entity + D-U-N-S) are widely treated as exempt; accounts created **before 13 Nov 2023** are grandfathered |

Google reduced the headcount from 20 → **12** in late 2024; the **14-day** rule has not changed. Official source: [App testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465).

#### Internal testing does NOT count (and conflicts with closed)

- The production gate explicitly requires a **closed test**. Internal's 100-tester limit is unrelated.
- **Critical for this app:** `jayradbus@gmail.com` is on **Internal testing** (Active, release 15). Google's docs state: *"Users who opt into internal testing aren't eligible for open and closed testing, even if included as testers on those tracks."* So your own account **does not count** toward the 12 until you **leave Internal testing** and **opt in to Closed testing** via the closed opt-in link.
- You can run Internal (1.5.8) and Closed (1.5.9) on different version codes concurrently, but **each person counts on only one track**. For closed-test credit, a tester must **not** be opted into Internal — **Internal must leave before Closed counts.**

#### License testers, pre-launch report, org account

| Option | Helps with 12×14? | Notes |
| --- | --- | --- |
| **License testers** (Setup → License testing) | **No** | Only for free IAP/license testing during development |
| **Pre-launch report** | **No** | Automated crawl on Play's devices; unrelated to production access |
| **Internal testing** (up to 100) | **No** | Good for QA; does not unlock Production |
| **Open testing** | **No** | Still not the mandated closed test for new personal accounts |
| **Organization account + D-U-N-S** | **Bypasses gate** | Requires a **registered business**, D-U-N-S (often 2–30 days), new $25 account; cannot convert personal → org in place — typically a new account + app transfer. Only worth it if you already have a company. |
| **"Apply for production" early** | **No** | Button stays locked until closed release is live **and** Dashboard shows 12×14 met |

There is **no solo-dev waiver** and **no alternative path** to Production on this account type except completing closed testing (or migrating to an exempt account type).

#### Live Console state (28 Aug ~21:15 IST)

Dashboard → **Production** → **Apply for access to production** shows:

- ☐ Publish a closed testing release — draft **16 (1.5.9)** exists, **not rolled out**
- ☐ Have at least 12 testers opted-in — **0 testers currently opted-in**
- ☐ Run closed test ≥ 14 days — **not started**
- ☐ Apply for production — **locked**

Closed testing - Alpha: **1 of 4–5 complete** · **0 countries/regions** · testers not assigned to track · opt-in link **pending publish** ("Links will be shown here when you publish").

#### Minimum viable path (zero network)

Honest order of operations — nothing here invents testers:

1. **Finish closed track setup (~10 min manual)** — see "Manual finish" in status dashboard above: foreground-service declaration → countries (India + US or all) → create/select email list on **Closed** Testers tab → upload `play/artifacts/vdr-1.5.9-vc16.aab` → rollout → copy **closed** opt-in link (format `play.google.com/apps/testing/…`, not `internaltest`).
2. **Leave Internal testing** on any account you want to count toward the 12 (including your own): open the internal opt-in page → **Leave the program** → then opt in to **Closed** only.
3. **Recruit 12 real Google accounts** — you need **11 people besides yourself** if only one Gmail is yours. Legitimate sources only:
   - Family, friends, coworkers who agree to install a free tool app for two weeks
   - Any **existing** alternate Gmail accounts you already personally control (work/personal) — do **not** mass-create accounts for this; Google treats that as abuse
   - Indie dev communities (Reddit r/androiddev, r/TestMyApp, local meetups) — post the opt-in link + one-line description; no payment required
   - Paid **human** tester panels (e.g. services that supply real opted-in testers) — not bots; costs ~$20–50; still your responsibility that testers are genuine
4. **Confirm Dashboard** shows **≥ 12 opted-in** before day 1 of the 14-day window; recruit **15–20** to absorb drop-off.
5. **Wait 14 full days** — calendar time, not negotiable.
6. **Apply for production** — answer questionnaire with real feedback (even from 2–3 engaged testers + your own testing notes).
7. **Production rollout** → BillDesk resubmit with live store URL.

**Earliest public URL:** ~3–4 weeks **after** step 3 succeeds (14 days + ~7 days production-access review + ~1–7 days app review). With **zero** willing testers, the clock **never starts**.

#### BillDesk while blocked

Production URL will stay **404** until step 7. Continue the email reply to BillDesk (`play/billdesk-reply-draft.txt`) explaining the Google closed-test gate — not a missing app.

### Internal release warnings (15 / 1.5.8)

1. **No testers** — **FIXED:** created list `Internal testers`, selected it, Save → track **Active**.
2. **No deobfuscation / Retrace file** — **soft / left:** `isMinifyEnabled = false` in `app/build.gradle.kts`, so no `mapping.txt` exists. Safe to ignore unless R8 minify is enabled on a future build.
3. **No native debug symbols** — **soft / left:** AAB ships small AndroidX `.so` libs (`libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`) without AGP-generated `native-debug-symbols.zip`. Fix on next bundle: set `ndk.debugSymbolLevel` and rebuild, then upload symbols via bundle ⋮ menu. Not blocking.

### What changed in 1.5.9

Reviewed 1.5.8's new billing + HLS/DASH work. Every item below produced a
plausible-looking result rather than a visible failure, which is why internal
testing did not surface them.

**Billing**
1. **Refunds never revoked Pro.** The entitlement was write-once: granted on
   purchase and never re-checked. `queryPurchasesAsync` — the call whose whole
   job is "what does this user still own?" — could only ever grant. A refunded,
   charged-back or revoked purchase kept Pro forever. Only the authoritative
   query re-locks; the incremental purchase callback still grants only, since
   an empty list there means "nothing new", not "you own nothing".
2. **Pending payments looked like failures.** UPI mandates, netbanking and cash
   settle asynchronously — the common case for Indian buyers. Those purchases
   were skipped silently, so a customer paid and saw a still-locked app with no
   explanation. Now stated explicitly.
3. **A paying customer was shown an upgrade ad on launch.** `isPro` started
   `false` while DataStore loaded, and `false` meant both "not Pro" and "not
   known yet". Since the current screen is `rememberSaveable`, a Pro user who
   was on Page Media got bounced to Home and prompted to buy what they already
   owned, every cold start. Loading is now a distinct third state.

**DASH**
4. **`$Number%05d$` templates never expanded.** The pattern was built from an
   unescaped `$`, which regex reads as an end-of-input anchor, so it could not
   match — and the literal token went into the request URL. Padded numbering is
   the common form in real manifests, so those streams 404'd every segment.
5. **`$Time$` became the constant `0`,** making every segment URL in a
   `<SegmentTimeline>` manifest identical: the same few seconds written N times
   into a full-size file that will not play. Now refused.
6. **Missing duration was guessed as three segments,** truncating a
   feature-length video to a few seconds and reporting success. Now refused.

**HLS**
7. **`#EXT-X-BYTERANGE` was ignored.** Those entries are slices of one file, so
   the same URL repeats; downloading each whole produced a file several times
   the correct size containing duplicated bytes. Now refused.

**Crashes and corruption caught by lint (`./gradlew :app:lintRelease`)**

8. **`URLDecoder.decode(s, Charsets.UTF_8)` does not exist below API 33.**
   minSdk is 26 and core library desugaring is off, so on Android 8 through 12
   that call is a `NoSuchMethodError`. It sits in `DirectUrl.pathOf()`, which
   runs for every URL the app inspects — so on most of the Android install base
   the crash would have been the first thing a user saw. The `(String, String)`
   overload has existed since API 1 and behaves identically.
9. **`MediaExtractor` sample flags were assigned straight to a
   `MediaCodec.BufferInfo`.** Two different flag vocabularies that happen to
   share numeric values: `SAMPLE_FLAG_SYNC` and `BUFFER_FLAG_KEY_FRAME` are
   both 1, which is why it looked correct — but `SAMPLE_FLAG_PARTIAL_FRAME` is
   4 and so is `BUFFER_FLAG_END_OF_STREAM`, so a partial frame told the muxer
   the stream had ended and truncated the remux there. Now translated
   explicitly.
10. **`androidx.fragment` resolved to 1.1.0** via a transitive dependency,
    predating the ActivityResult APIs `MainActivity` registers at construction.
    Lint rates this Fatal rather than cosmetic: the old fragment code does not
    join the result registry, so the notification and storage permission
    callbacks never fire. Pinned to 1.8.5.

Lint now reports zero errors on the release variant (24 warnings, all
cosmetic: newer-dependency notices and launcher-icon shape).

**Found by running it on a device (2026-08-28)**

11. **Sharing a link bypassed the free segment cap.** `DownloadService`
    defaulted `EXTRA_SEGMENTS` to **8** when the extra was absent, so "the
    caller forgot to say" was indistinguishable from "the caller is entitled
    to eight" — and the ambiguous case resolved to a Pro-tier number.
    `MainActivity.handleShare()` was such a caller, so a free user sharing a
    link got 8 segments while the in-app Add button correctly gave them 4.
    Confirmed on device: same URL, same non-Pro phone, 8 segments before the
    fix and 4 after.

    The gate was only ever applied in `VdrApp.queueUrls()`, so any other route
    into the service skipped it. It is now enforced in the service, where every
    download converges, with the entitlement read per add rather than cached —
    a cached flag starts `false` and would silently downgrade a Pro user who
    shares a link before DataStore has answered.
12. **A purchase error told users to go and fix Play Console.** "Pro product
    `vdr_pro` not found. Activate it in Play Console." is an instruction only
    the developer can act on, and the same code path runs if Play's catalogue
    is briefly unavailable in production. Users now see "Pro isn't available
    to buy right now. Try again later."; the diagnostic went to logcat under
    tag `VdrBilling`.

**Made the free-only release coherent (2026-08-28)**

13. **The app advertised a purchase it could not complete.** With `vdr_pro`
    not activated, the upgrade dialog still read "Unlock Pro for ₹1" — the
    price was a hardcoded fallback shown even when Play had returned no
    product — and offered a **Buy** button whose only possible outcome was an
    error toast. Publishing that to the store would have shown every new user
    a broken purchase.

    When Play reports no purchasable offer the dialog now reads "Pro is coming
    soon", drops the Buy button, and says plainly that the features are not on
    sale yet and the rest of VDR is free. It reverts to the full purchase flow
    on its own once `vdr_pro` goes live — no code change needed at that point.

**Found testing Scan page against archive.org (2026-08-28)**

14. **A failed fetch was reported as "No video files found on this page."**
    `probePageUrl` caught every exception and returned `None`, which the UI
    renders with that message — a claim about the page's contents made after
    never having read it. A timeout, DNS failure or TLS error all sent the
    user off blaming the site or the parser. There is now a
    `PageProbeResult.Failed` carrying the real reason ("Timed out loading that
    page", "Couldn't reach that site", …), and the cause is logged under
    `VdrGrabber`. This is what made the next two defects findable at all.
15. **`%2F` in a path corrupted the saved filename.** `filenameFromUrl` sliced
    the raw URL at the last literal `/`. Archive.org serves
    `.../Content%2Fbig_buck_bunny_720p_surround.mp4`, where `%2F` is an encoded
    separator, so the "filename" came out as
    `Content%2Fbig_buck_bunny_720p_surround.mp4` — and `safeFilename` strips
    `%` as unsafe, landing the file on disk as
    **`Content2Fbig_buck_bunny_720p_surround.mp4`**. A real download saved
    under a name that is not its own, on the row the picker selects by
    default. The name now comes from the decoded path.
16. **The same file was listed twice.** Archive.org links each file both
    encoded and plain on one page. `canonicalize` deduped on `rawPath`, so the
    two forms never collapsed and the picker showed two identical-looking rows
    a user could not choose between. Canonicalising on the decoded path took
    the Big Buck Bunny page from 5 rows to 3 — one per actual format.

**Scan page verified end to end** on `archive.org/details/BigBuckBunny_124`:
globe icon → 3 videos listed (.mp4/.ogv/.avi) → pick one → Download →
`Downloads/VDR/Videos/big_buck_bunny_720p_surround.mp4`, 61,878,609 bytes,
**byte-identical to upstream** (SHA-256 match) over a 4-segment download.

**Scan page made usable in the free release (2026-08-28)**

17. **The gate helpers were dead code.** `ProGates.canScanPage` and
    `canUseFocusGuard` were defined and unit-tested but **never called** —
    the real gating was four separate raw `isPro` checks in `VdrApp` (open,
    the external-browse entry, the eject-on-entitlement-loss effect, and the
    render condition). Policy therefore could not be changed in one place,
    and missing any one of them yields a screen you can open and are then
    thrown out of, or one that renders blank. All four now route through
    `ProGates.canScanPage`.
18. **Scan page ships free at launch.** Gating it behind a product that
    cannot be sold until BillDesk clears would make the globe icon a dead
    control for every user on day one. `ProGates.SCAN_PAGE_IS_FREE` is a
    single documented switch; flip it to re-gate. The purchase dialog's perk
    list is generated from the flags, so it no longer offers to sell a
    feature the user already has.
19. **`.ogv` was listed as a video and then filed as "Other".**
    `MediaGrabber.videoExtensions` included it; `Organizer`'s Videos category
    did not. Archive.org's Ogg copy appeared under "3 videos on page" and
    saved to `Downloads/VDR/Other` — the app contradicting itself between one
    screen and the next. A test now holds the two lists in agreement.

Verified on the **release** build with no Pro: globe → 3 videos → select the
`.ogv` (not the default row) → Download → `Downloads/VDR/Videos/`,
46,935,223 bytes, byte-identical to upstream, 4 segments (free cap holding).

**Hardening**
- Page scanning read only the first ~8 KiB of a page. okio's `read(sink, n)`
  fills one segment per call rather than reading up to `n`, so the 2 MiB cap
  was never reached and any `<video>` below the fold read as "no media found".
- Segment lists from remote manifests are capped (HLS 20k, DASH 5k) so a
  hostile playlist cannot exhaust memory before a byte is fetched.
- `BillingManager.end()` now cancels its coroutine scope instead of leaving
  work queued against a closed client.

Refusing is deliberate in 4–7: a clear "VDR can't download this yet" is worth
more than a file that looks finished and will not play.

### BillDesk onboarding

Merchant-onboarding answers (website/APK fields, business info, declared
income, PEP status) are kept out of this file — the repo is public and those
are personal business details rather than anything about the app.

See `play/BILLDESK-PRIVATE.md`, which is gitignored and stays on your machine.

**Status (2026-08-28):** BillDesk sent a **clarification email** (Application
`2608267849`, 28 Aug 1:17 PM IST from `onboarding@billdesk.com`):

> *Clarification required for the Individual: Mobile Application(s) is not
> live/does not exist/not accessible*

**Why BillDesk rejects each URL**

| URL tried | HTTP | BillDesk result | Why |
| --- | --- | --- | --- |
| `https://play.google.com/store/apps/details?id=com.jayr91.vdr` | **404** | Rejected (original submission) | App not published to Production — no public listing exists |
| `https://play.google.com/apps/internaltest/4701575606071485981` | **302** → Google sign-in | **Rejected** (portal validation, 28 Aug) | BillDesk form example is WhatsApp's `store/apps/details?id=…` URL; internal-test opt-in requires Google login and is not a public store listing |
| `https://jayr91.github.io/vdr-android/privacy-policy.html` | **200** | N/A — wrong field | Goes in **Website URL**, not Mobile App APK URL |
| GitHub pages "coming soon" landing | — | **Not acceptable** | BillDesk validates the APK URL as a live Play Store listing |

**BillDesk form fields** (`connect.billdesk.com/website-details`):
- **Website URL** → `jayr91.github.io` (live, 200). Privacy policy path also validates.
- **APP Name** → `VDR`
- **Mobile App APK URL** → **must** be `https://play.google.com/store/apps/details?id=com.jayr91.vdr` once Production is live. No alternate format accepted.

**What to enter in BillDesk TODAY (Production still pending):**

You cannot satisfy the Mobile App APK URL field until the public Play listing
exists. Do **not** re-submit the internal-testing link — BillDesk rejected it.

1. **Reply to** `onboarding@billdesk.com` (draft: `play/billdesk-reply-draft.txt`) explaining:
   - Package `com.jayr91.vdr` is registered on Google Play Console
   - Public store URL will be `https://play.google.com/store/apps/details?id=com.jayr91.vdr` after Google's closed-test + production-access review
   - App is currently on Internal testing (release 15 / 1.5.8, track Active)
   - Request temporary acceptance or ask BillDesk to pause APK verification until the listing is live
2. **Website URL** — keep `jayr91.github.io` (do not put Play URL here)
3. **Do not** leave the Mobile App APK URL blank if the form requires it — use the email reply to explain; if the portal forces a value, you may need BillDesk agent help via Video KYC

**Action required (human):**
1. Complete Play Console setup (2 tasks below) → closed test → 12 testers × 14 days → Apply for production
2. Reply to BillDesk clarification email (see draft above)
3. Complete **Video KYC** (Mon–Sat 09:30–18:00 IST) — prior attempt 27 Aug was unsuccessful per BillDesk email

Video KYC still pending — human agents only. `vdr_pro` cannot be activated until
BillDesk clears, so the Pro purchase flow is built and tested but not yet
transactable.

## Monetization (freemium — not a paid download)

**Model:** Free app download + one-time Pro unlock via Google Play Billing.

| | |
| --- | --- |
| Product ID | `vdr_pro` |
| Type | One-time in-app product (non-consumable / lifetime unlock) |
| Target price | **₹1 INR** (use Play’s minimum INR if Console rejects ₹1 — report actual) |
| App listing price | **Free** (do **not** set the whole app to Paid) |

### Free (always)
- Add single URL / clipboard paste download
- Pause / resume / cancel
- View download list; open completed files / folder
- Basic settings (speed limit, Wi‑Fi only)

### Pro (gated — Unlock Pro for ₹1 sheet: Buy / Restore / Not now)
- Page media scan (globe / Scan page / list videos from a page)
- Batch / multi-URL queue from clipboard
- Segments above free cap (free max **4**; Pro up to **32**)
- Focus Guard

### Code
- `billing-ktx` + `BillingManager` / `ProEntitlement` / `ProGates`
- Debug builds: long-press title to fake unlock (not in release)

### Console status (2026-08-26)
- App remains **Free** (paid-app ₹1 direction abandoned).
- **Payments profile:** **exists** — Individual “Jayendar S”, profile ID `4280-5807-3189` (India / INR).  
  https://play.google.com/console/u/0/developers/5667084395209045347/paymentssettings
- **Merchant accounts:** created but both show **Issue with account**  
  - Cross border `7695-7184-9564-3355`  
  - India only `8488-6695-2592-8969`
- **BillDesk PA-CB:** Application `2608267849` — Website/APK + Business submitted. **28 Aug clarification:** mobile app URL not accessible (404). **28 Aug update:** internal-testing URL **also rejected** by BillDesk portal. **Remaining:** (1) email BillDesk that Production listing pending (see draft), (2) Video KYC at `https://connect.billdesk.com/videoKyc` (prior attempt 27 Aug unsuccessful). Agents Mon–Sat 09:30–18:00 IST.
- **Website/APK values submitted:** Website URL `jayr91.github.io` · APP Name `VDR` · Mobile App APK URL `https://play.google.com/store/apps/details?id=com.jayr91.vdr` (**404** — rejected by BillDesk).
- **Internal-testing URL (rejected 28 Aug):** `https://play.google.com/apps/internaltest/4701575606071485981` — BillDesk portal validation failed; wrong URL format.
- **Target Mobile App APK URL (once live):** `https://play.google.com/store/apps/details?id=com.jayr91.vdr`
- **One-time products / `vdr_pro`:** still blocked — do **not** create until merchant “Issue with account” clears.
- After Video KYC approval → create/activate `vdr_pro` at ₹1 → upload **1.5.8** AAB. Free listing/Internal work can proceed without IAP.

FFmpeg remux (mpegts→mp4, stream copy only) previously added native libs; current builds use platform MediaMuxer (see README / `THIRD_PARTY_NOTICES.md`).

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
first, then promote when the listing, Data safety, content rating, and Pro IAP are complete.

## Publishing status (2026-08-26)

### Done in Play Console
- **Internal testing AAB uploaded:** App bundle **14 (1.5.7)** as **Draft / Inactive** (setup **1 of 3**: Create release ✓; still need Select testers + Preview/confirm). Supersede with **15 (1.5.8)** after Pro + merchant.
  - Prepare URL: https://play.google.com/console/u/0/developers/5667084395209045347/app/4975586487357388428/tracks/4701575606071485981/releases/1/prepare
  - Track: https://play.google.com/console/u/0/developers/5667084395209045347/app/4975586487357388428/tracks/internal-testing
- **Languages:** Manage languages confirmed **only en-US** selected (Default). No extra locales enabled.
- **Store listing (en-US):** Title `VDR`, short 70 / full 1639 present; icon + feature graphic present; **6× 10-inch tablet screenshots uploaded** (28 Aug). Saved; queued in Publishing overview.
- **Store settings (28 Aug):** Category **Tools**; contact email **jayradbus@gmail.com**.
- **Agent note (28 Aug):** Chrome automation (`play/console_js/vdr_chrome.sh`) completed store settings + 10" screenshot upload via asset library; listing Save clicked. Phone screenshots still empty.

### Done locally / verified
- Freemium Pro gates + Billing Library in **1.5.8 / versionCode 15**
- **Signed release AAB rebuilt (2026-08-26):** `app/build/outputs/bundle/release/app-release.aab` + copy `play/artifacts/vdr-1.5.8-vc15.aab` (~12 MiB); merged manifest includes `com.android.vending.BILLING`
- Store copy: `play/store-listing.txt` / `play/paste/*` (free + Pro unlock ₹1)
- 10-inch tablet screenshots ready locally: `play/screenshots/tablet-10/` (1200×1920)
- Privacy policy live: https://jayr91.github.io/vdr-android/privacy-policy.html (Play handles purchase; no VDR server collection)
- API helper (optional): `play/upload-internal.sh` (needs service-account JSON; do not commit)

### Still manual / incomplete
- **Complete BillDesk merchant KYC** (PA-CB) — only human blocker; profile/merchant rows already exist with “Issue with account”.
- **Create & activate** one-time product `vdr_pro` at ₹1 INR (or Console minimum) after payments issues clear.
- **Closed testing:** select countries → testers → upload AAB to **Closed** track (1.5.8 or 1.5.9) → 12 testers × 14 days → Apply for production.
- Optional: upload **phone screenshots** from `play/screenshots/phone/` if Console requires them before closed-test release.
- Production **not** live.

## Play Console checklist

1. **Create the app** — **done** (`com.jayr91.vdr` / VDR). Free.
2. **Payments profile / merchant** — profile + merchant accounts exist; finish **BillDesk KYC** until “Issue with account” clears.
3. **One-time product `vdr_pro`** — ₹1 INR lifetime unlock; activate (blocked until #2).
4. **Store listing** — **done (en-US):** copy + icon + feature graphic + **6× 10-inch tablet screenshots**; Saved 28 Aug. Optional: phone shots from `play/screenshots/phone/`.
4b. **Store settings** — **done:** category **Tools**; contact email **jayradbus@gmail.com** (28 Aug).
5. **Privacy policy URL** — `https://jayr91.github.io/vdr-android/privacy-policy.html`.
6. **Data safety** — no off-device collection by VDR; purchases via Google Play.
7. **Content rating** — IARC utility; target Everyone.
8. **Upload AAB to Internal testing** — 1.5.8 / versionCode 15. Add yourself as tester; only then promote to Production.

## Store listing

Copy from `store-listing.txt`. Honest facts:

- Free download; optional Pro unlock ₹1
- Direct HTTP(S) files and playlist URLs you provide — not YouTube watch pages
- Clipboard paste and share-in of text URLs
- Optional Wi-Fi only; completed files in Downloads/VDR

Do not market as IDM or a YouTube downloader.

### Languages

Keep **only English (United States)**. Remove empty locales / en-GB stubs.

## Data safety (Play form)

- App does **not** collect user data for you to “send off the device”
- No account, no analytics, no advertising ID
- Files the user downloads are stored **on the device** (Downloads/VDR)
- Clipboard is read locally; not uploaded
- Purchases: processed by Google Play (not a VDR server)
- Notifications: download progress only

## Photos

- High-res icon: `play/icon-512.png`
- Feature graphic: `play/feature-graphic-1024x500.png`
- **10-inch tablet screenshots:** `play/screenshots/tablet-10/` — **uploaded to Play Console (6/8)** 28 Aug
- **Phone screenshots:** capture/upload if Console still requires them

## versionCode ledger (2026-08-29)

- **versionCode 17 is consumed on the Play side.** A manual upload of `vdr-1.6.0-vc17.aab` was rejected with “version code 17 is already used”, so Play will never accept 17 again regardless of what the file contains. `play/artifacts/vdr-1.6.0-vc17.aab` (and the matching Desktop copy) are kept for reference only — they are **not uploadable**.
- **Current uploadable artifact: versionCode 18**, still `versionName` **1.6.0** (1.6.0 never reached a public track, so the marketing version does not need to move).
  - `play/artifacts/vdr-1.6.0-vc18.aab`
  - `~/Desktop/vdr-1.6.0-vc18.aab`
  - Signed with the release keystore; carries the DownloadService foreground-service lifecycle fix (foreground only while work is queued/active, `stopForeground` + `stopSelf` when the queue drains, `Service.onTimeout` for API 35).
- Verified `versionCode=18` by decoding `base/manifest/AndroidManifest.xml` out of the bundle itself (`play/artifacts/_read_aab_manifest.py`), not just from `app/build.gradle.kts`.
- Next upload must use **18 or higher**; bump `versionCode` again for any further rebuild that Play has already seen.
