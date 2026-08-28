package com.jayr91.vdr.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProGatesTest {
    @Test
    fun freeSegmentCap() {
        assertEquals(4, ProGates.maxSegments(false))
        assertEquals(32, ProGates.maxSegments(true))
        assertEquals(4, ProGates.clampSegments(8, false))
        assertEquals(8, ProGates.clampSegments(8, true))
        assertEquals(1, ProGates.clampSegments(0, false))
        assertTrue(ProGates.segmentsNeedPro(5))
        assertFalse(ProGates.segmentsNeedPro(4))
    }

    @Test
    fun pageScanIsPro() {
        assertFalse(ProGates.canScanPage(false))
        assertTrue(ProGates.canScanPage(true))
    }

    @Test
    fun batchQueueGate() {
        assertTrue(ProGates.canBatchQueue(1, false))
        assertFalse(ProGates.canBatchQueue(2, false))
        assertTrue(ProGates.canBatchQueue(5, true))
    }

    @Test
    fun focusGuardIsPro() {
        assertFalse(ProGates.canUseFocusGuard(false))
        assertTrue(ProGates.canUseFocusGuard(true))
    }

    /**
     * The service used to default EXTRA_SEGMENTS to 8 when the extra was
     * absent, which made "the caller forgot to say" indistinguishable from
     * "the caller is entitled to eight" -- and picked a Pro-tier number for
     * the ambiguous case. The share handler did forget, so sharing a link
     * gave free users the Pro segment count while the in-app Add button gave
     * them four. Whatever the default is, clamping must still bring an
     * unentitled request back to the free ceiling.
     */
    @Test
    fun unspecifiedSegmentsCannotExceedFreeCap() {
        val serviceDefault = ProGates.FREE_MAX_SEGMENTS
        assertEquals(4, ProGates.clampSegments(serviceDefault, isPro = false))
        // The old default, arriving from any caller, must still be clamped.
        assertEquals(4, ProGates.clampSegments(8, isPro = false))
        assertEquals(4, ProGates.clampSegments(32, isPro = false))
        // Pro is unaffected.
        assertEquals(8, ProGates.clampSegments(8, isPro = true))
        assertEquals(32, ProGates.clampSegments(64, isPro = true))
    }

    @Test
    fun productId() {
        assertEquals("vdr_pro", ProGates.PRODUCT_ID)
    }
}
