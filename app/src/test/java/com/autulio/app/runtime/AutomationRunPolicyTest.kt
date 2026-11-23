package com.autulio.app.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRunPolicyTest {
    @Test
    fun targetPackageMustBeAnExactAndroidApplicationId() {
        assertTrue(AutomationRunPolicy.isValidTargetPackage("com.example.app"))
        assertTrue(AutomationRunPolicy.isValidTargetPackage("com.SuperCell.game_2"))
        assertFalse(AutomationRunPolicy.isValidTargetPackage(null))
        assertFalse(AutomationRunPolicy.isValidTargetPackage(""))
        assertFalse(AutomationRunPolicy.isValidTargetPackage("com"))
        assertFalse(AutomationRunPolicy.isValidTargetPackage("com.example.*"))
        assertFalse(AutomationRunPolicy.isValidTargetPackage("com.example app"))
        assertFalse(AutomationRunPolicy.isValidTargetPackage(".example.app"))
        assertFalse(AutomationRunPolicy.isValidTargetPackage("com.example."))
    }

    @Test
    fun scheduleDelayIsBoundedAndZeroMeansManualStart() {
        val now = 1_800_000_000_000L
        assertEquals(0L, AutomationRunPolicy.scheduledStartAt(now, 0))
        assertEquals(now + 5 * 60_000L, AutomationRunPolicy.scheduledStartAt(now, 5))
        assertEquals(
            now + 24 * 60 * 60_000L,
            AutomationRunPolicy.scheduledStartAt(now, 10_000)
        )
        assertTrue(AutomationRunPolicy.isScheduledStartDue(0L, now))
        assertTrue(AutomationRunPolicy.isScheduledStartDue(now - 1, now))
        assertFalse(AutomationRunPolicy.isScheduledStartDue(now + 1, now))
    }

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
