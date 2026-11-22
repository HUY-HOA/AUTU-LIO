package com.autulio.app.script

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutuSyntaxHighlighterTest {

    @Test
    fun highlightsDifferentAutuTokenCategories() {
        val source = """BIẾN DEM: SỐ = 12
// ghi chú
NẾU (DEM > 0) {
    CHẠM(10, 20)
    PIN()
    BIẾN TEN = "AUTU-LIO"
}"""
        val transformed = AutuSyntaxHighlighter.filter(AnnotatedString(source))
        val styledText = transformed.text

        assertColor(source, styledText, "BIẾN", AutuSyntaxHighlighter.keywordColor)
        assertColor(source, styledText, "SỐ", AutuSyntaxHighlighter.typeColor)
        assertColor(source, styledText, "12", AutuSyntaxHighlighter.numberColor)
        assertColor(source, styledText, "// ghi chú", AutuSyntaxHighlighter.commentColor)
        assertColor(source, styledText, "NẾU", AutuSyntaxHighlighter.keywordColor)
        assertColor(source, styledText, "CHẠM", AutuSyntaxHighlighter.commandColor)
        assertColor(source, styledText, "PIN", AutuSyntaxHighlighter.functionColor)
        assertColor(source, styledText, "\"AUTU-LIO\"", AutuSyntaxHighlighter.stringColor)
        assertColor(source, styledText, "DEM", AutuSyntaxHighlighter.identifierColor)
    }

    @Test
    fun preservesSourceTextAndCursorOffsets() {
        val source = "BIẾN A = 1\\nCHẠM(A, 2)"
        val transformed = AutuSyntaxHighlighter.filter(AnnotatedString(source))

        assertEquals(source, transformed.text.text)
        assertEquals(source.length, transformed.offsetMapping.originalToTransformed(source.length))
        assertEquals(source.length, transformed.offsetMapping.transformedToOriginal(source.length))
    }

    private fun assertColor(
        source: String,
        transformed: AnnotatedString,
        token: String,
        expected: androidx.compose.ui.graphics.Color
    ) {
        val start = source.indexOf(token)
        assertTrue("Token not found: $token", start >= 0)
        val end = start + token.length
        assertTrue(
            "Expected '$token' to have color $expected. Spans: ${transformed.spanStyles}",
            transformed.spanStyles.any { range ->
                range.start == start && range.end == end && range.item.color == expected
            }
        )
    }
}
