package com.autulio.app.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptCompilerTest {
    @Test
    fun compilesVietnameseCommandsVariablesConditionsAndExpressions() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT sốLần = 2 + 1
            NẾU (sốLần == 3 VÀ ĐÚNG) {
                CHẠM(120, 240)
            } NGƯỢC_LẠI {
                CHỜ(100)
            }
            LẶP(sốLần) {
                NHẤN_GIỮ(20, 30, 500)
            }
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals(ScriptCompiler.LANGUAGE_VERSION, result.program?.languageVersion)
        assertEquals(5_000L, result.program?.expandedInstructionCount)
    }

    @Test
    fun supportsNestedRepeatAndLegacyEnglishAliases() {
        val result = ScriptCompiler.compile(
            """
            repeat(2) {
                tap(10, 20)
                wait(100)
            }
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals(4L, result.program?.expandedInstructionCount)
        assertEquals(200L, result.program?.estimatedDurationMs)
    }

    @Test
    fun supportsWhileAndLoopControlStatements() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT i = 0
            LẶP_KHI (i < 10) {
                i = i + 1
                NẾU (i == 5) {
                    THOÁT_LẶP
                }
                CHẠM(i, 20)
            }
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
    }

    @Test
    fun supportsStringsAndFunctionCallsInExpressions() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT tenGoi = "com.example.game"
            NẾU (ỨNG_DỤNG_ĐÃ_CÀI(tenGoi) == ĐÚNG HOẶC PIN() < 10) {
                MỞ_ỨNG_DỤNG(tenGoi)
            }
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
    }

    @Test
    fun rejectsUnknownCommands() {
        val result = ScriptCompiler.compile("DỊCH_CHUYỂN(1, 2)")

        assertFalse(result.isSuccess)
        assertEquals("unknown_command", result.diagnostics.first().code)
    }

    @Test
    fun rejectsIncorrectArgumentCount() {
        val result = ScriptCompiler.compile("CHẠM(10)")

        assertFalse(result.isSuccess)
        assertEquals("argument_count", result.diagnostics.first().code)
    }

    @Test
    fun rejectsRepeatCountOutsideSupportedRange() {
        val result = ScriptCompiler.compile("LẶP(0) { CHẠM(1, 2) }")

        assertFalse(result.isSuccess)
        assertEquals("repeat_range", result.diagnostics.first().code)
    }

    @Test
    fun rejectsExpandedInstructionBudgetOverflow() {
        val result = ScriptCompiler.compile(
            """
            LẶP(100) {
                LẶP(100) {
                    CHẠM(1, 2)
                }
            }
            """.trimIndent()
        )

        assertFalse(result.isSuccess)
        assertEquals("instruction_budget_exceeded", result.diagnostics.first().code)
    }

    @Test
    fun rejectsTotalDurationBudgetOverflow() {
        val result = ScriptCompiler.compile("LẶP(100) { CHỜ(6001) }")

        assertFalse(result.isSuccess)
        assertEquals("duration_budget_exceeded", result.diagnostics.first().code)
    }

    @Test
    fun rejectsLoopControlOutsideLoop() {
        val result = ScriptCompiler.compile("THOÁT_LẶP")

        assertFalse(result.isSuccess)
        assertEquals("loop_control_outside_loop", result.diagnostics.first().code)
    }

    @Test
    fun rejectsUnclosedBlocksAndOversizedSource() {
        val unclosed = ScriptCompiler.compile("LẶP(2) { CHẠM(1, 2)")
        val oversized = ScriptCompiler.compile(" ".repeat(ScriptCompiler.MAX_SOURCE_LENGTH + 1))

        assertEquals("missing_brace", unclosed.diagnostics.first().code)
        assertEquals("source_too_large", oversized.diagnostics.first().code)
    }

    @Test
    fun acceptsCommentsBlankLinesAndSemicolons() {
        val result = ScriptCompiler.compile(
            """
            // Chú thích đầu
            CHẠM(10, 20); CHỜ(100)

            LẶP(2) {
                // Một thao tác lặp
                CHẠM(30, 40)
            }
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
    }
}
