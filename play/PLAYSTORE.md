# Publish VDR to Google Play

Play Console requires your Google account. This repo can only prepare the
listing copy, privacy policy, and a signed App Bundle.

**App:** `com.jayr91.vdr` · **Version:** 1.6.5 (`versionCode` 23) — Closed testing, then Production  
**Next upload:** see `play/HANDOFF-CLOSED-ALPHA-1.6.5.md`. Billing Library 8.0.0 is in this tree.
**Category:** Tools · **Price:** Free (optional one-time Pro IAP) · **Default language:** English (United States)  
**Play Console app:** developer `5667084395209045347` · app `4975586487357388428`  
**Privacy policy (live):** https://jayr91.github.io/vdr-android/privacy-policy.html  
**Signed AAB:** `play/artifacts/vdr-1.6.3-vc21.aab` (and `~/Desktop/vdr-1.6.3-vc21.aab`). Closed testing still holds **20 (1.6.2)** until the user uploads 21.

## Status dashboard (2026-08-29 ~16:20 IST) — Play emails, quoted

Gmail (`jayendarsubramaniam@gmail.com`) on 29 Aug 2026 **2:50 PM IST**, from **Google Play Support**. Not invented. No Gmail tab existed; opened on the existing Chrome Play tab. `jayradbus@gmail.com` inbox was not used for these two.

### Email 1 — Billing

**Subject:** `[Action required] Your app is affected by changes to Google Play Billing Library requirements`

> 1 of your apps is affected by changes to Google Play Billing Library requirements
>
> Your app, 'VDR', uses a version of Google Play Billing Library that will be deprecated soon. From Aug 31, 2026, all apps must use version 8.0.0 or later.
>
> Update to a newer version by this date to prevent your updates from being rejected.
>
> How to fix
>
> Update all APKs, and any third-party SDKs and libraries used for billing on Google Play to version 8.0.0 or later, across all your tracks. We recommend updating to version 9 to make use of the latest monetization features.

Play Console notification (same day, Closed testing bell), quoted:

> Action by Aug 31
> Update to a newer version of Google Play Billing Library within 3 days to prevent updates from being rejected
>
> We recently told you that the version of Google Play Billing Library your app uses will be deprecated soon. Update to a newer version by August 31, 2026 to prevent updates from being rejected (3 days away).

Duplicate Console card (shorter):

> Action by Aug 31
> Update to a newer version of Google Play Billing Library to prevent your updates from being rejected
>
> Your app uses a version of Google Play Billing Library that will be deprecated soon. Update to a newer version by August 31, 2026 to prevent your updates from being rejected.

**Fact:** Closed testing **20 (1.6.2)** ships `billing-ktx` **7.1.1** (read from `base/root/billing.properties` in `vdr-1.6.2-vc20.aab`). That is why they flagged it.

**Fix (local, not uploaded):** `billing-ktx:8.0.0` (Play's stated minimum). 8.1+/9.x want kotlin-stdlib 2.2.10; this repo is Kotlin **2.0.21**. `BillingManager.queryProductDetailsAsync` now takes `QueryProductDetailsResult`. `enableAutoServiceReconnection()` added. versionCode **21** / 1.6.3.

### Email 2 — target API

**Subject:** `[Action required] Your app is affected by Google Play's target API level requirements`

> Your app is affected by Google Play's target API level requirements
>
> We've detected that your app, 'VDR', is targeting an old version of Android. To provide users with a safe and secure experience, Google Play requires all apps to meet target API level requirements before Aug 31, 2026.
>
> How to fix
>
> Update your app to target the latest Android version.

Play Console notification, quoted (two identical cards):

> Action by Aug 31
> Update your target API level by August 31, 2026 to release updates to your app
>
> We've detected that your app is targeting an old version of Android. To provide users with a safe and secure experience, Google Play requires all apps to meet target API level requirements. From August 31, 2026, if your target API level is not within 1 year of the latest Android release, you won't be able to update your app.

Official Play rule for that date: new apps and updates must target **Android 16 / API 36**. Source already had `compileSdk`/`targetSdk` **36** on vc20; merged manifest for vc21 is `minSdk 26` / `targetSdk 36`. The email still went out at 2:50 PM — Play may still be scoring an older track artifact (Internal 15 was API 35) or had not finished rescanning 20. vc21 restates API 36 so one upload covers both mails.

### What was not done

- **Not uploaded.** User uploads themselves. File: `play/artifacts/vdr-1.6.3-vc21.aab` (~4.0 MiB) and Desktop copy. Symbols zip beside it for the kebab menu after attach.
- **Did not** re-submit over in-review 20 from this machine. Play's own mail says update APKs **across all tracks**, so 21 has to go to Closed testing (and Internal if that track still serves 7.1.1 / API 35).
- **Did not** create `vdr_pro`, **did not** do BillDesk KYC, **did not** recruit testers.
- Account Policy status: **"No issues found with your developer account."** These are deadline warnings, not a policy strike.

Dashboard at 16:00 IST still showed Closed testing - Alpha **Release: 20 (1.6.2)** last updated Aug 29, 0 testers opted in, Production locked.

## versionCode ledger (2026-08-29 afternoon)

- **21 (1.6.3) is the uploadable fix** for the 29 Aug Play emails (Billing Library 8.0.0 + target API 36). Not on Play yet.
  - `play/artifacts/vdr-1.6.3-vc21.aab`
  - `~/Desktop/vdr-1.6.3-vc21.aab`
  - Symbols: `play/artifacts/vdr-1.6.3-vc21-native-debug-symbols.zip` (and Desktop copy)
  - Verified from the AAB: `versionCode=21`, `versionName=1.6.3`, `compileSdkVersion=36`, `billing.properties` `8.0.0`, merged `uses-sdk` target 36.
- **20 (1.6.2)** is what Closed testing currently holds. Consumed. Has billing **7.1.1** — that is the flagged binary.
- **17 is consumed.** 18/19 are local spares only.
- Next rebuild after 21 is uploaded must use **22 or higher**.
