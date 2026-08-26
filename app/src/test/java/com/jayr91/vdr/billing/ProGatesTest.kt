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

    @Test
    fun productId() {
        assertEquals("vdr_pro", ProGates.PRODUCT_ID)
    }
}
