package com.autulio.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LicenseDaysRemainingTest {
    @Test
    fun showsOneDayWhenLessThanTwentyFourHoursRemain() {
        val now = 1_800_000_000L
        assertEquals(1L, licenseDaysRemaining(now + 1L, now))
        assertEquals(1L, licenseDaysRemaining(now + 23L * 60L * 60L, now))
    }

    @Test
    fun roundsUpPartialDaysAndKeepsExactWholeDays() {
        val now = 1_800_000_000L
        assertEquals(1L, licenseDaysRemaining(now + 86_400L, now))
        assertEquals(2L, licenseDaysRemaining(now + 86_401L, now))
        assertEquals(2L, licenseDaysRemaining(now + 2L * 86_400L, now))
    }

    @Test
    fun returnsZeroWhenLicenseHasExpired() {
        val now = 1_800_000_000L
        assertEquals(0L, licenseDaysRemaining(now, now))
        assertEquals(0L, licenseDaysRemaining(now - 60L, now))
    }
}
