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
            ĐẶT SỐ_LẦN = 2 + 1
            NẾU (SỐ_LẦN == 3 VÀ ĐÚNG) {
                CHẠM(120, 240)
            } NGƯỢC_LẠI {
                CHỜ(100)
            }
            LẶP(SỐ_LẦN) {
                NHẤN_GIỮ(20, 30, 500)
            }
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals(ScriptCompiler.LANGUAGE_VERSION, result.program?.languageVersion)
        assertEquals(103L, result.program?.expandedInstructionCount)
    }

    @Test
    fun supportsNestedRepeatInVietnameseSyntax() {
        val result = ScriptCompiler.compile(
            """
            LẶP(2) {
                CHẠM(10, 20)
                CHỜ(100)
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
            ĐẶT I = 0
            LẶP_KHI (I < 10) {
                I = i + 1
                NẾU (I == 5) {
                    THOÁT_LẶP
                }
                CHẠM(I, 20)
            }
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
    }

    @Test
    fun supportsWaitUntilAndLoopUntilCommands() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT DA_XONG = SAI
            CHỜ_ĐẾN_KHI(DA_XONG == ĐÚNG, 5000, 100)
            LẶP_ĐẾN_KHI(DA_XONG == ĐÚNG) {
                DA_XONG = ĐÚNG
            }
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.0", result.program?.languageVersion)
    }

    @Test
    fun rejectsInvalidWaitUntilTimeoutAndPollingInterval() {
        val invalidTimeout = ScriptCompiler.compile("CHỜ_ĐẾN_KHI(ĐÚNG, 0, 100)")
        val invalidInterval = ScriptCompiler.compile("CHỜ_ĐẾN_KHI(ĐÚNG, 1000, 20)")

        assertEquals("wait_until_timeout_range", invalidTimeout.diagnostics.first().code)
        assertEquals("wait_until_interval_range", invalidInterval.diagnostics.first().code)
    }

    @Test
    fun supportsStringsAndFunctionCallsInExpressions() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT TEN_GOI = "com.example.game"
            NẾU (ỨNG_DỤNG_ĐÃ_CÀI(TEN_GOI) == ĐÚNG HOẶC PIN() < 10) {
                MỞ_ỨNG_DỤNG(TEN_GOI)
            }
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
    }

    @Test
    fun compilesVietnameseAndEnglishSystemNavigationCommands() {
        val vietnamese = ScriptCompiler.compile("QUAY_LẠI()\nVỀ_MÀN_HÌNH_CHÍNH()")
        val english = ScriptCompiler.compile("BACK()\nHOME()")

        assertTrue(vietnamese.diagnostics.toString(), vietnamese.isSuccess)
        assertFalse("English command aliases must not be accepted", english.isSuccess)
        assertEquals(2L, vietnamese.program?.expandedInstructionCount)
        assertEquals(null, english.program)

        val supportedActions = listOf(
            "ỨNG_DỤNG_GẦN_ĐÂY()", "MỞ_THÔNG_BÁO()", "MỞ_CÀI_ĐẶT_NHANH()",
            "HIỆN_MENU_NGUỒN()", "KHÓA_MÀN_HÌNH()", "CHIA_MÀN_HÌNH()",
            "ỨNG_DỤNG_GẦN_ĐÂY()", "MỞ_THÔNG_BÁO()", "MỞ_CÀI_ĐẶT_NHANH()",
            "HIỆN_MENU_NGUỒN()", "KHÓA_MÀN_HÌNH()", "CHIA_MÀN_HÌNH()"
        )
        supportedActions.forEach { source ->
            val result = ScriptCompiler.compile(source)
            assertTrue("$source: ${result.diagnostics}", result.isSuccess)
        }
    }

    @Test
    fun rejectsArgumentsForSystemNavigationCommands() {
        val back = ScriptCompiler.compile("QUAY_LẠI(1)")
        val home = ScriptCompiler.compile("VỀ_MÀN_HÌNH_CHÍNH(1)")

        assertEquals("argument_count", back.diagnostics.first().code)
        assertEquals("argument_count", home.diagnostics.first().code)
        val recents = ScriptCompiler.compile("ỨNG_DỤNG_GẦN_ĐÂY(1)")
        val lock = ScriptCompiler.compile("KHÓA_MÀN_HÌNH(1)")
        assertEquals("argument_count", recents.diagnostics.first().code)
        assertEquals("argument_count", lock.diagnostics.first().code)
    }

    @Test
    fun everyRegisteredSystemCommandHasTheCorrectVietnameseSignature() {
        val validCommands = listOf(
            "CHẠM(1, 2)",
            "NHẤN_GIỮ(1, 2, 100)",
            "VUỐT(1, 2, 3, 4, 100)",
            "CHỜ(100)",
            "MỞ_ỨNG_DỤNG(\"com.example.game\")",
            "QUAY_LẠI()",
            "VỀ_MÀN_HÌNH_CHÍNH()",
            "ỨNG_DỤNG_GẦN_ĐÂY()",
            "MỞ_THÔNG_BÁO()",
            "MỞ_CÀI_ĐẶT_NHANH()",
            "HIỆN_MENU_NGUỒN()",
            "KHÓA_MÀN_HÌNH()",
            "CHIA_MÀN_HÌNH()"
        )
        validCommands.forEach { source ->
            val result = ScriptCompiler.compile(source)
            assertTrue("$source: ${result.diagnostics}", result.isSuccess)
        }

        val invalidCommands = listOf(
            "ỨNG_DỤNG_GẦN_ĐÂY(1)",
            "MỞ_THÔNG_BÁO(1)",
            "MỞ_CÀI_ĐẶT_NHANH(1)",
            "HIỆN_MENU_NGUỒN(1)",
            "CHIA_MÀN_HÌNH(1)"
        )
        invalidCommands.forEach { source ->
            val result = ScriptCompiler.compile(source)
            assertFalse(source, result.isSuccess)
            assertEquals("argument_count", result.diagnostics.first().code)
        }
    }

    @Test
    fun canonicalVietnameseKeywordsPreserveDiacriticsAndCompileInUppercase() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT SỐ_LẦN = 2
            NẾU (SỐ_LẦN > 1 VÀ ĐÚNG) {
                LẶP(SỐ_LẦN) {
                    CHẠM(10, 20)
                }
            } NGƯỢC_LẠI {
                CHỜ(100)
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

    @Test
    fun requiresUppercaseVietnameseCommandsAndIdentifiers() {
        val lowercaseKeyword = ScriptCompiler.compile("đặt SO_LAN = 2")
        val lowercaseCommand = ScriptCompiler.compile("chạm(10, 20)")
        val missingDiacritics = ScriptCompiler.compile("CHAM(10, 20)")
        val englishAlias = ScriptCompiler.compile("TAP(10, 20)")
        val lowercaseVariable = ScriptCompiler.compile("ĐẶT so_lan = 2")

        assertFalse(lowercaseKeyword.isSuccess)
        assertFalse(lowercaseCommand.isSuccess)
        assertFalse(missingDiacritics.isSuccess)
        assertFalse(englishAlias.isSuccess)
        assertFalse(lowercaseVariable.isSuccess)
        assertEquals("identifier_must_be_uppercase", lowercaseKeyword.diagnostics.first().code)
        assertEquals("identifier_must_be_uppercase", lowercaseVariable.diagnostics.first().code)
    }

    @Test
    fun supportsTypedVariablesAndUppercaseVietnameseBuiltins() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT SO_LAN: SỐ = 3
            ĐẶT NOI_DUNG: CHUỖI = "AUTU-LIO"
            ĐẶT DA_SANG: LUẬN_LÝ = ĐÚNG
            NẾU (DA_SANG VÀ ĐỘ_DÀI(NOI_DUNG) > 0) {
                LẶP(SO_LAN) {
                    CHẠM(100, 200)
                }
            }
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.0", result.program?.languageVersion)
    }

    @Test
    fun validatesBuiltinFunctionNamesAndArgumentCounts() {
        val unknown = ScriptCompiler.compile("ĐẶT GIA_TRI = OCR(\"nội dung\")")
        val wrongCount = ScriptCompiler.compile("ĐẶT GIA_TRI = PIN(1)")

        assertEquals("unknown_function", unknown.diagnostics.first().code)
        assertEquals("function_argument_count", wrongCount.diagnostics.first().code)
    }

    @Test
    fun supportsExponentOperatorWithRightAssociativeGrammar() {
        val result = ScriptCompiler.compile("ĐẶT KET_QUA: SỐ = 2 ^ 3 ^ 2")

        assertTrue(result.diagnostics.toString(), result.isSuccess)
    }


    @Test
    fun supportsUppercaseUserDefinedFunctionsWithTypedParametersAndReturns() {
        val result = ScriptCompiler.compile(
            """
            ĐỊNH_NGHĨA_HÀM TINH_TONG(A: SỐ, B: SỐ): SỐ {
                TRẢ_VỀ A + B
            }
            ĐẶT KET_QUA: SỐ = TINH_TONG(10, 20)
            CHẠM(KET_QUA, 30)
            """.trimIndent()
        )
        assertTrue(result.diagnostics.toString(), result.isSuccess)
    }

    @Test
    fun supportsTypedListsAndVietnameseForEachSyntax() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT DANH_SÁCH: DANH_SÁCH = TẠO_DANH_SÁCH(10, 20, 30)
            ĐẶT SO_LUONG: SỐ = ĐỘ_DÀI(DANH_SÁCH)
            LẶP_MỖI (PHAN_TU TRONG DANH_SÁCH) {
                CHẠM(PHAN_TU, 40)
            }
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.1", result.program?.languageVersion)
    }

    @Test
    fun rejectsInvalidForEachSeparatorAndLowercaseLoopVariable() {
        val missingSeparator = ScriptCompiler.compile(
            "ĐẶT DANH_SÁCH = TẠO_DANH_SÁCH(1)\nLẶP_MỖI (ITEM IN DANH_SÁCH) { CHỜ(1) }"
        )
        val lowercaseVariable = ScriptCompiler.compile(
            "ĐẶT DANH_SÁCH = TẠO_DANH_SÁCH(1)\nLẶP_MỖI (item TRONG DANH_SÁCH) { CHỜ(1) }"
        )
        assertEquals("expected_in_keyword", missingSeparator.diagnostics.first().code)
        assertEquals("identifier_must_be_uppercase", lowercaseVariable.diagnostics.first().code)
    }

    @Test
    fun validatesListBuiltinFunctionArgumentCountsAndSize() {
        val wrongGetCount = ScriptCompiler.compile("ĐẶT X = LẤY_PHẦN_TỬ(TẠO_DANH_SÁCH(), 1, 2)")
        val tooManyItems = ScriptCompiler.compile(
            "ĐẶT X = TẠO_DANH_SÁCH(" + (0 until ScriptCompiler.MAX_LIST_ITEMS + 1).joinToString(",") + ")"
        )
        assertEquals("function_argument_count", wrongGetCount.diagnostics.first().code)
        assertEquals("list_too_large", tooManyItems.diagnostics.first().code)
    }

    @Test
    fun rejectsReturnOutsideFunctionAndUndefinedFunctions() {
        val invalidReturn = ScriptCompiler.compile("TRẢ_VỀ 10")
        val undefinedCall = ScriptCompiler.compile("ĐẶT KET_QUA = HAM_CHUA_DINH_NGHIA(10)")

        assertEquals("return_outside_function", invalidReturn.diagnostics.first().code)
        assertEquals("unknown_function", undefinedCall.diagnostics.first().code)
    }

}
