package com.autu.lio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptCsvCodecTest {
    @Test
    fun roundTripsVietnameseQuotedCellsCommasAndMultilineValues() {
        val rows = listOf(
            listOf("Tên", "Ghi chú", "Thành phố"),
            listOf("LIO", "Chào, \"AUTU\"\nAndroid", "Hồ Chí Minh"),
            listOf("Có; dấu chấm phẩy", "  giữ khoảng trắng  ", "")
        )

        val encoded = ScriptCsvCodec.serialize(rows, ",")
        val decoded = ScriptCsvCodec.parse(encoded, ",")

        assertEquals(rows, decoded)
    }

    @Test
    fun supportsCustomDelimiterBomAndCrLfRecords() {
        val input = "\uFEFFTên;Giá trị\r\nLIO;\"A;B\"\r\n"
        assertEquals(
            listOf(listOf("Tên", "Giá trị"), listOf("LIO", "A;B")),
            ScriptCsvCodec.parse(input, ";")
        )
    }

    @Test
    fun preservesEmptyFieldsAndDoesNotCreateAnExtraRowAfterTrailingNewline() {
        assertEquals(
            listOf(listOf("", "B", ""), listOf("C", "")),
            ScriptCsvCodec.parse(",B,\r\nC,\r\n", ",")
        )
        assertEquals(emptyList<List<String>>(), ScriptCsvCodec.parse("", ","))
    }

    @Test
    fun rejectsMalformedQuotesAndCharactersAfterClosingQuote() {
        listOf(
            "\"chưa đóng",
            "a\"b,c",
            "\"đúng\"x,b",
            "\"đúng\" ,b"
        ).forEach { source ->
            assertFalse(
                "Should reject malformed CSV: $source",
                runCatching { ScriptCsvCodec.parse(source, ",") }.isSuccess
            )
        }
    }

    @Test
    fun rejectsInvalidDelimiters() {
        listOf("", ",,", '"'.toString(), "\n", "\u0000").forEach { delimiter ->
            assertFalse(runCatching {
                ScriptCsvCodec.parse("a,b", delimiter)
            }.isSuccess)
        }
    }

    @Test
    fun enforcesRowsColumnsCellsAndCellLengthLimits() {
        assertFalse(runCatching {
            ScriptCsvCodec.serialize(
                List(ScriptCsvCodec.MAX_ROWS + 1) { listOf("x") },
                ","
            )
        }.isSuccess)
        assertFalse(runCatching {
            ScriptCsvCodec.serialize(
                listOf(List(ScriptCsvCodec.MAX_COLUMNS + 1) { "x" }),
                ","
            )
        }.isSuccess)
        assertFalse(runCatching {
            ScriptCsvCodec.serialize(
                listOf(List(ScriptCsvCodec.MAX_CELLS + 1) { "x" }),
                ","
            )
        }.isSuccess)
        assertFalse(runCatching {
            ScriptCsvCodec.parse("x".repeat(ScriptCsvCodec.MAX_CELL_CHARS + 1), ",")
        }.isSuccess)
    }

    @Test
    fun rejectsUnsupportedNestedValuesAndNonFiniteNumbers() {
        assertFalse(runCatching {
            ScriptCsvCodec.serialize(listOf(listOf(mapOf("x" to 1))), ",")
        }.isSuccess)
        assertFalse(runCatching {
            ScriptCsvCodec.serialize(listOf(listOf(Double.NaN)), ",")
        }.isSuccess)
    }

    @Test
    fun acceptsPrimitiveValuesAndWritesNullAsAnEmptyCell() {
        assertEquals(
            "1,true,,AUTU\r\n",
            ScriptCsvCodec.serialize(listOf(listOf(1, true, null, "AUTU")), ",")
        )
        assertTrue(ScriptCsvCodec.parse("A,B\n", ",").size == 1)
    }
}
