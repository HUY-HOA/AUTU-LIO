package com.autulio.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorQuickInsertTest {

    @Test
    fun insertsAtCurrentCursorPosition() {
        val initial = TextFieldValue("NHẤN(12)", selection = TextRange(6))

        val updated = insertEditorQuickText(initial, "_")

        assertEquals("NHẤN(1_2)", updated.text)
        assertEquals(7, updated.selection.start)
    }

    @Test
    fun placesCursorInsidePairedSymbols() {
        val initial = TextFieldValue("NẾU", selection = TextRange(3))

        val updated = insertEditorQuickText(initial, "()", cursorOffset = 1)

        assertEquals("NẾU()", updated.text)
        assertEquals(4, updated.selection.start)
    }

    @Test
    fun replacesSelectedText() {
        val initial = TextFieldValue("ABC", selection = TextRange(1, 2))

        val updated = insertEditorQuickText(initial, "_")

        assertEquals("A_C", updated.text)
        assertEquals(2, updated.selection.start)
    }
}
