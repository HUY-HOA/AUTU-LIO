package com.autu.lio.runtime

import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Bounded, device-local date/time helpers for AUTU Script.
 *
 * Parsing requires an explicit UTC offset so a timestamp never silently
 * depends on the device timezone. Formatting intentionally uses the device
 * timezone and locale unless a caller supplies them in unit tests.
 */
object ScriptDateTimeCodec {
    const val MAX_ISO_TEXT_LENGTH = 64
    const val MAX_PATTERN_LENGTH = 128
    const val MAX_FORMATTED_TEXT_LENGTH = 256

    fun currentIso(clock: Clock = Clock.systemDefaultZone()): String =
        OffsetDateTime.now(clock).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    fun parseIsoEpochMillis(value: String): Long {
        require(value.isNotEmpty() && value.length <= MAX_ISO_TEXT_LENGTH) {
            "Chuỗi ngày giờ ISO phải có từ 1 đến $MAX_ISO_TEXT_LENGTH ký tự."
        }
        val instant = try {
            OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
        } catch (_: DateTimeException) {
            throw IllegalArgumentException(
                "Thời gian phải theo ISO 8601 và có múi giờ Z hoặc độ lệch UTC."
            )
        }
        return try {
            instant.toEpochMilli()
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("Thời gian nằm ngoài giới hạn epoch milliseconds.")
        }
    }

    fun formatEpochMillis(
        epochMillis: Long,
        pattern: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String {
        require(pattern.isNotEmpty() && pattern.length <= MAX_PATTERN_LENGTH) {
            "Mẫu định dạng phải có từ 1 đến $MAX_PATTERN_LENGTH ký tự."
        }
        val formatter = try {
            DateTimeFormatter.ofPattern(pattern, locale)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Mẫu định dạng ngày giờ không hợp lệ.")
        }
        val formatted = try {
            Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(formatter)
        } catch (_: DateTimeException) {
            throw IllegalArgumentException("Không thể định dạng mốc thời gian đã cung cấp.")
        }
        require(formatted.length <= MAX_FORMATTED_TEXT_LENGTH) {
            "Kết quả định dạng vượt quá $MAX_FORMATTED_TEXT_LENGTH ký tự."
        }
        return formatted
    }
}
