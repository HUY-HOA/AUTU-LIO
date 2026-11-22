package com.autulio.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal data class EditorQuickKey(
    val label: String,
    val insertion: String,
    val cursorOffset: Int = insertion.length
)

internal fun insertEditorQuickText(
    value: TextFieldValue,
    insertion: String,
    cursorOffset: Int = insertion.length
): TextFieldValue {
    val start = minOf(value.selection.start, value.selection.end)
        .coerceIn(0, value.text.length)
    val end = maxOf(value.selection.start, value.selection.end)
        .coerceIn(start, value.text.length)
    val updatedText = buildString(value.text.length - (end - start) + insertion.length) {
        append(value.text, 0, start)
        append(insertion)
        append(value.text, end, value.text.length)
    }
    val cursor = (start + cursorOffset.coerceIn(0, insertion.length))
        .coerceIn(0, updatedText.length)
    return TextFieldValue(
        text = updatedText,
        selection = TextRange(cursor)
    )
}
