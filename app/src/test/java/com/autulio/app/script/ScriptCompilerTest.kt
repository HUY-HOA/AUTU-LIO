package com.autulio.app.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptCompilerTest {
    @Test
    fun acceptsSupportedCommandsAndCalculatesDuration() {
        val result = ScriptCompiler.compile(
            """
            tap(12, 34)
            longPress(4, 5, 800)
            swipe(1, 2, 3, 4, 300)
            wait(500)
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals(4L, result.program?.expandedInstructionCount)
        assertEquals(1_600L, result.program?.estimatedDurationMs)
    }

    @Test
    fun repeatExpandsInstructionAndDurationBudgets() {
        val result = ScriptCompiler.compile(
            """
            repeat(3) {
                tap(10, 20)
                wait(100)
            }
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals(6L, result.program?.expandedInstructionCount)
        assertEquals(300L, result.program?.estimatedDurationMs)
    }

    @Test
    fun acceptsCommentsBlankLinesAndSemicolons() {
        val result = ScriptCompiler.compile(
            """
            // first tap
            tap(10, 20); wait(100)

            repeat(2) {
                // repeated action
                tap(30, 40)
            }
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
    }

    @Test
    fun rejectsUnknownCommands() {
        val result = ScriptCompiler.compile("teleport(1, 2)")

        assertFalse(result.isSuccess)
        assertEquals("unknown_command", result.diagnostics.first().code)
    }

    @Test
    fun rejectsIncorrectArgumentCount() {
        val result = ScriptCompiler.compile("tap(10)")

        assertFalse(result.isSuccess)
        assertEquals("argument_count", result.diagnostics.first().code)
    }

    @Test
    fun rejectsNegativeNumbers() {
        val result = ScriptCompiler.compile("tap(-1, 2)")

        assertFalse(result.isSuccess)
        assertEquals("invalid_character", result.diagnostics.first().code)
    }

    @Test
    fun rejectsCoordinatesOutsideSupportedRange() {
        val result = ScriptCompiler.compile("tap(10001, 20)")

        assertFalse(result.isSuccess)
        assertEquals("coordinate_range", result.diagnostics.first().code)
    }

    @Test
    fun rejectsRepeatCountOutsideSupportedRange() {
        val result = ScriptCompiler.compile("repeat(0) { tap(1, 2) }")

        assertFalse(result.isSuccess)
        assertEquals("repeat_range", result.diagnostics.first().code)
    }

    @Test
    fun rejectsExpandedInstructionBudgetOverflow() {
        val result = ScriptCompiler.compile(
            """
            repeat(100) {
                repeat(100) {
                    tap(1, 2)
                }
            }
            """.trimIndent()
        )

        assertFalse(result.isSuccess)
        assertEquals("instruction_budget_exceeded", result.diagnostics.first().code)
    }

    @Test
    fun rejectsTotalDurationBudgetOverflow() {
        val result = ScriptCompiler.compile("repeat(100) { wait(60000) }")

        assertFalse(result.isSuccess)
        assertEquals("duration_budget_exceeded", result.diagnostics.first().code)
    }

    @Test
    fun rejectsOversizedSource() {
        val result = ScriptCompiler.compile(" ".repeat(ScriptCompiler.MAX_SOURCE_LENGTH + 1))

        assertFalse(result.isSuccess)
        assertEquals("source_too_large", result.diagnostics.first().code)
    }

    @Test
    fun rejectsEmptyAndUnclosedRepeatBlocks() {
        val empty = ScriptCompiler.compile("  // nothing here")
        val unclosed = ScriptCompiler.compile("repeat(2) { tap(1, 2)")

        assertEquals("empty_program", empty.diagnostics.first().code)
        assertEquals("missing_brace", unclosed.diagnostics.first().code)
    }
}
