package com.jayr91.vdr.billing

/**
 * Pure helpers for freemium limits. Free vs Pro product decisions live here
 * so UI and tests stay aligned without touching Play Billing.
 */
object ProGates {
    const val PRODUCT_ID = "vdr_pro"

    /** Free users may use up to this many parallel segments. */
    const val FREE_MAX_SEGMENTS = 4

    /** Absolute slider max (Pro). */
    const val PRO_MAX_SEGMENTS = 32

    fun maxSegments(isPro: Boolean): Int =
        if (isPro) PRO_MAX_SEGMENTS else FREE_MAX_SEGMENTS

    fun clampSegments(requested: Int, isPro: Boolean): Int =
        requested.coerceIn(1, maxSegments(isPro))

    /**
     * Page media scan ships **free** in the launch release.
     *
     * It is built as a Pro feature and is meant to move behind the paywall
     * once `vdr_pro` can actually be sold, which is blocked on merchant
     * onboarding rather than on anything in the app. Shipping it gated would
     * mean the globe icon is a dead control for every user at launch, so it
     * is free for now.
     *
     * Flip this to false to re-gate it. Be aware that is a takeaway: people
     * who have been using a free feature tend to react badly to losing it, so
     * it is worth pairing with a release note rather than doing it silently.
     */
    const val SCAN_PAGE_IS_FREE = true

    /** Page media scan (globe / Scan page). See [SCAN_PAGE_IS_FREE]. */
    fun canScanPage(isPro: Boolean): Boolean = SCAN_PAGE_IS_FREE || isPro

    /** Single-URL clipboard queue is free; multi-URL batch is Pro. */
    fun canBatchQueue(urlCount: Int, isPro: Boolean): Boolean =
        isPro || urlCount <= 1

    /** Focus Guard is a Pro advanced option. */
    fun canUseFocusGuard(isPro: Boolean): Boolean = isPro

    fun segmentsNeedPro(requested: Int): Boolean =
        requested > FREE_MAX_SEGMENTS
}
