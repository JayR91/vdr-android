# Publish VDR to Google Play

Play Console requires your Google account. This repo can only prepare the
listing copy, privacy policy, and a signed App Bundle.

**App:** `com.jayr91.vdr` · **Version:** 1.5.9 (`versionCode` 16)  
**Category:** Tools · **Price:** Free (optional one-time Pro IAP) · **Default language:** English (United States)  
**Play Console app:** developer `5667084395209045347` · app `4975586487357388428`  
**Privacy policy (live):** https://jayr91.github.io/vdr-android/privacy-policy.html  
**Signed AAB:** `app/build/outputs/bundle/release/app-release.aab` (~12 MiB) · also `play/ready/app-release-1.5.8.aab`

## Status dashboard (2026-08-28 ~15:47 IST)

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
| Signed AAB 1.5.9 / vc16 | **BUILT, NOT UPLOADED** | `play/artifacts/vdr-1.5.9-vc16.aab`; needs `PLAY_JSON` to upload |
| On-device smoke test | **DONE (2026-08-28)** | realme RMX3312, Android 13 / API 33. Launch, share-to-download, segmented reassembly (byte-identical), Pro dialog — all pass |
| Scan page (Pro) | **DONE (2026-08-28)** | archive.org details page → 3 videos listed → pick → download byte-identical. 3 defects found and fixed |
| Store listing | **DONE (en-US)** | Title/short/full + icon + feature graphic saved; **6× 10-inch tablet screenshots** uploaded (6/8 max); **Saved** 28 Aug. Phone screenshots still empty (optional for closed-test unlock; add from `play/screenshots/phone/` if Console flags them) |
| Store settings | **DONE** | Category **Tools**; contact email **jayradbus@gmail.com**; phone/website left blank |
| Policy forms | **DONE** | Ads, Ad ID, Sign-in, IARC, Target audience, Data safety, Financial, Health saved |
| Internal testing | **DONE (Active)** | **15 (1.5.8)** available to internal testers (Aug 26); not reviewed |
| Closed testing | **READY TO RESUME** | The awaited new release exists: **1.5.9 / vc16**, reviewed and device-verified. Upload `play/artifacts/vdr-1.5.9-vc16.aab` to **Closed testing** (API track `alpha`, *not* `internal`). See `play/HANDOFF-CLOSED-TESTING.md`. |
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
