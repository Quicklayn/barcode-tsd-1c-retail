package ru.local.barcodetsd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSubmissionGateTest {

    @Test
    fun duplicateHardwareEventIsAcceptedOnlyOnce() {
        val gate = ScanSubmissionGate()

        assertTrue(gate.acceptHardwareEnter(eventTime = 100L, repeatCount = 0))
        assertFalse(gate.acceptHardwareEnter(eventTime = 100L, repeatCount = 0))
        assertTrue(gate.acceptHardwareEnter(eventTime = 101L, repeatCount = 0))
    }

    @Test
    fun repeatedKeyDownIsRejected() {
        val gate = ScanSubmissionGate()

        assertFalse(gate.acceptHardwareEnter(eventTime = 100L, repeatCount = 1))
    }

    @Test
    fun scanNormalizationRemovesOnlySurroundingWhitespaceAndTerminator() {
        assertEquals("12 34", normalizeScanInput("  12 34\r\n"))
        assertEquals("", normalizeScanInput("\r\n"))
    }
}
