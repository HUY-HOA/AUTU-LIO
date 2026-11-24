package com.autu.lio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class ScriptDateTimeCodecTest {
    @Test
    fun emitsIso8601WithTheClockOffset() {
        val clock = Clock.fixed(
            Instant.parse("2026-11-20T07:30:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh")
        )
        assertEquals(
            "2026-11-20T14:30:00+07:00",
            ScriptDateTimeCodec.currentIso(clock)
        )
    }

    @Test
    fun parsesIso8601OffsetIntoEpochMilliseconds() {
        val expected = Instant.parse("2026-11-20T07:30:00Z").toEpochMilli()
        assertEquals(
            expected,
            ScriptDateTimeCodec.parseIsoEpochMillis("2026-11-20T14:30:00+07:00")
        )
        assertEquals(
            expected,
            ScriptDateTimeCodec.parseIsoEpochMillis("2026-11-20T07:30:00Z")
        )
    }

    @Test
    fun rejectsIsoTextWithoutTimezoneOrOutsideBounds() {
        val invalid = listOf(
            "",
            "2026-11-20T14:30:00",
            "not-a-date",
            "x".repeat(ScriptDateTimeCodec.MAX_ISO_TEXT_LENGTH + 1)
        )
        invalid.forEach { value ->
            assertFalse(
                "Value should be rejected: $value",
                runCatching { ScriptDateTimeCodec.parseIsoEpochMillis(value) }.isSuccess
            )
        }
    }

    @Test
    fun formatsEpochMillisecondsInTheRequestedTimezone() {
        val epoch = Instant.parse("2026-11-20T07:30:00Z").toEpochMilli()
        assertEquals(
            "20/11/2026 14:30:00",
            ScriptDateTimeCodec.formatEpochMillis(
                epoch,
                "dd/MM/yyyy HH:mm:ss",
                ZoneId.of("Asia/Ho_Chi_Minh"),
                Locale.ROOT
            )
        )
    }

    @Test
    fun rejectsInvalidOrOversizedFormatPatterns() {
        assertFalse(
            runCatching {
                ScriptDateTimeCodec.formatEpochMillis(0L, "x".repeat(129))
            }.isSuccess
        )
        assertFalse(
            runCatching {
                ScriptDateTimeCodec.formatEpochMillis(0L, "not-a-valid-pattern-Q")
            }.isSuccess
        )
    }
}
