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

    /** Page media scan (globe / Scan page) is Pro-only. */
    fun canScanPage(isPro: Boolean): Boolean = isPro

    /** Single-URL clipboard queue is free; multi-URL batch is Pro. */
    fun canBatchQueue(urlCount: Int, isPro: Boolean): Boolean =
        isPro || urlCount <= 1

    /** Focus Guard is a Pro advanced option. */
    fun canUseFocusGuard(isPro: Boolean): Boolean = isPro

    fun segmentsNeedPro(requested: Int): Boolean =
        requested > FREE_MAX_SEGMENTS
}
