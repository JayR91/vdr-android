package com.jayr91.vdr.engine

/**
 * Port of VDR focus_guard.decide_policy.
 * On Android, "idle" is approximated as the app being in the background
 * (user is not actively interacting with VDR).
 */
object FocusGuard {
    const val POLICY_OFF = "off"
    const val POLICY_FULL = "full"
    const val POLICY_CRAWL = "active"
    const val POLICY_HOLD = "battery"
    const val CRAWL_BYTES_PER_SEC = 256L * 1024L

    fun decidePolicy(
        enabled: Boolean,
        onBattery: Boolean,
        powerSave: Boolean,
        appInForeground: Boolean,
    ): String {
        if (!enabled) return POLICY_OFF
        if (onBattery || powerSave) return POLICY_HOLD
        if (appInForeground) return POLICY_CRAWL
        return POLICY_FULL
    }

    fun detail(policy: String): String = when (policy) {
        POLICY_OFF -> "Off — downloads run at your speed limit"
        POLICY_HOLD -> "Paused — phone is on battery or battery saver"
        POLICY_CRAWL -> "Crawling at 256 KB/s while you use the phone"
        else -> "Full speed — plugged in"
    }
}
