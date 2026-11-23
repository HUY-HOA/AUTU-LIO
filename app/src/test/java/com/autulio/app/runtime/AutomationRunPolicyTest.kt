package com.autulio.app.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRunPolicyTest {
    @Test
    fun retryIsDisabledByDefaultAndRequiresExplicitOptIn() {
        assertFalse(AutomationRunPolicy.shouldRetry(false, attempts = 0, maxRetries = 2))
        assertFalse(AutomationRunPolicy.shouldRetry(true, attempts = 0, maxRetries = 0))
        assertTrue(AutomationRunPolicy.shouldRetry(true, attempts = 0, maxRetries = 2))
    }

    @Test
    fun retryCountIsBounded() {
        assertTrue(AutomationRunPolicy.shouldRetry(true, attempts = 1, maxRetries = 2))
        assertFalse(AutomationRunPolicy.shouldRetry(true, attempts = 2, maxRetries = 2))
        assertFalse(AutomationRunPolicy.shouldRetry(true, attempts = 0, maxRetries = 3))
    }

    @Test
    fun retryDelayUsesExponentialBackoffAndCapsAtOneMinute() {
        assertEquals(2_000L, AutomationRunPolicy.retryDelayMs(2_000L, 1))
        assertEquals(4_000L, AutomationRunPolicy.retryDelayMs(2_000L, 2))
        assertEquals(8_000L, AutomationRunPolicy.retryDelayMs(2_000L, 3))
        assertEquals(60_000L, AutomationRunPolicy.retryDelayMs(30_000L, 3))
    }
}
