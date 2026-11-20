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
                I = I + 1
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
        assertEquals("1.20", result.program?.languageVersion)
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
    fun normalizesRenamedAndroidCommandsAndKeepsLegacyAliases() {
        val source = """
            MỞ_ỨNG_DỤNG_GẦN_ĐÂY()
            MỞ_THANH_THÔNG_BÁO()
            MỞ_BẢNG_CÀI_ĐẶT_NHANH()
            MỞ_HỘP_THOẠI_NGUỒN()
            BẬT_TẮT_CHIA_MÀN_HÌNH()
            ỨNG_DỤNG_GẦN_ĐÂY()
            MỞ_THÔNG_BÁO()
            MỞ_CÀI_ĐẶT_NHANH()
            HIỆN_MENU_NGUỒN()
            CHIA_MÀN_HÌNH()
        """.trimIndent()
        val result = ScriptCompiler.compile(source)
        assertTrue(result.diagnostics.toString(), result.isSuccess)
        val names = result.program!!.instructions
            .filterIsInstance<ScriptInstruction.Command>()
            .map { it.name }
        assertEquals(
            listOf(
                "MỞ_ỨNG_DỤNG_GẦN_ĐÂY",
                "MỞ_THANH_THÔNG_BÁO",
                "MỞ_BẢNG_CÀI_ĐẶT_NHANH",
                "MỞ_HỘP_THOẠI_NGUỒN",
                "BẬT_TẮT_CHIA_MÀN_HÌNH",
                "MỞ_ỨNG_DỤNG_GẦN_ĐÂY",
                "MỞ_THANH_THÔNG_BÁO",
                "MỞ_BẢNG_CÀI_ĐẶT_NHANH",
                "MỞ_HỘP_THOẠI_NGUỒN",
                "BẬT_TẮT_CHIA_MÀN_HÌNH"
            ),
            names
        )
    }

    @Test
    fun rejectsIncorrectMultiTouchCommandArgumentCounts() {
        val tap = ScriptCompiler.compile("CHẠM_ĐA_ĐIỂM(TẠO_DANH_SÁCH(), 100, 200)")
        val swipe = ScriptCompiler.compile("VUỐT_ĐA_ĐIỂM(TẠO_DANH_SÁCH(), 100, 200)")

        assertEquals("argument_count", tap.diagnostics.first().code)
        assertEquals("argument_count", swipe.diagnostics.first().code)
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
            "CHẠM_ĐA_ĐIỂM(TẠO_DANH_SÁCH(TẠO_DANH_SÁCH(10, 20), TẠO_DANH_SÁCH(30, 40)), 100)",
            "VUỐT_ĐA_ĐIỂM(TẠO_DANH_SÁCH(TẠO_DANH_SÁCH(10, 20, 30, 40), TẠO_DANH_SÁCH(50, 60, 70, 80)), 200)",
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
        assertEquals("1.20", result.program?.languageVersion)
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
        assertEquals("1.20", result.program?.languageVersion)
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
    fun supportsTypedVietnameseMapsAndMapKeyIteration() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT CAU_HINH: BẢN_ĐỒ = TẠO_BẢN_ĐỒ("TEN", "AUTU-LIO", "SO", 3)
            ĐẶT SO_LUONG: SỐ = ĐỘ_DÀI(CAU_HINH)
            ĐẶT DANH_SACH_KHOA: DANH_SÁCH = DANH_SÁCH_KHÓA(CAU_HINH)
            LẶP_MỖI (KHOA TRONG DANH_SACH_KHOA) {
                ĐẶT GIA_TRI = LẤY_GIÁ_TRỊ(CAU_HINH, KHOA)
                CHỜ(1)
            }
            """.trimIndent()
        )
        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.20", result.program?.languageVersion)
    }

    @Test
    fun rejectsInvalidMapPairsAndTooManyEntries() {
        val oddPairs = ScriptCompiler.compile("ĐẶT MAP: BẢN_ĐỒ = TẠO_BẢN_ĐỒ(\"A\", 1, \"B\")")
        val tooMany = ScriptCompiler.compile(
            "ĐẶT MAP = TẠO_BẢN_ĐỒ(" +
                (0 until ScriptCompiler.MAX_MAP_ENTRIES + 1).joinToString(",") { "\"K$it\", $it" } +
                ")"
        )
        assertEquals("map_argument_count", oddPairs.diagnostics.first().code)
        assertEquals("map_too_large", tooMany.diagnostics.first().code)
    }

    @Test
    fun supportsUppercaseVietnameseStringFunctionSignatures() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT NGUON: CHUỖI = " AUTU-LIO "
            ĐẶT CHUAN: CHUỖI = CẮT_KHOẢNG_TRẮNG(NGUON)
            ĐẶT HOA: CHUỖI = CHỮ_HOA(CHUAN)
            ĐẶT THUONG: CHUỖI = CHỮ_THƯỜNG(HOA)
            ĐẶT CAC_TU: DANH_SÁCH = TÁCH_CHUỖI("A,B,C", ",")
            ĐẶT GHEP: CHUỖI = NỐI_CHUỖI(CAC_TU, "-")
            ĐẶT CAT: CHUỖI = CẮT_CHUỖI(HOA, 0, 4)
            ĐẶT KY_TU: CHUỖI = KÝ_TỰ_TẠI(HOA, 0)
            ĐẶT THAY: CHUỖI = THAY_THẾ(HOA, "AUTU", "LIO")
            NẾU (CHỨA_CHUỖI(HOA, "AUTU") VÀ BẮT_ĐẦU_BẰNG(HOA, "AUTU") VÀ KẾT_THÚC_BẰNG(HOA, "LIO")) {
                CHỜ(1)
            }
            """.trimIndent()
        )
        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.20", result.program?.languageVersion)
    }

    @Test
    fun rejectsIncorrectStringFunctionArguments() {
        val wrongCount = ScriptCompiler.compile("ĐẶT KET_QUA = CHỮ_HOA(\"A\", \"B\")")
        assertEquals("function_argument_count", wrongCount.diagnostics.first().code)
    }

    @Test
    fun rejectsReturnOutsideFunctionAndUndefinedFunctions() {
        val invalidReturn = ScriptCompiler.compile("TRẢ_VỀ 10")
        val undefinedCall = ScriptCompiler.compile("ĐẶT KET_QUA = HAM_CHUA_DINH_NGHIA(10)")

        assertEquals("return_outside_function", invalidReturn.diagnostics.first().code)
        assertEquals("unknown_function", undefinedCall.diagnostics.first().code)
    }

    @Test
    fun supportsScreenColorAnalysisBuiltins() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT MÀU: BẢN_ĐỒ = MÀU_TẠI(20, 30)
            ĐẶT CÓ_MÀU: LUẬN_LÝ = MÀN_HÌNH_CÓ_MÀU(255, 0, 0, 12)
            ĐẶT TỌA_ĐỘ: DANH_SÁCH = TÌM_VỊ_TRÍ_MÀU(0, 255, 0, 8)
            ĐẶT TỶ_LỆ: SỐ = TỶ_LỆ_MÀU_TRONG_VÙNG(0, 0, 255, 10, 10, 20, 300, 400)
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.20", result.program?.languageVersion)
    }

    @Test
    fun rejectsIncorrectScreenColorBuiltinArgumentCounts() {
        val point = ScriptCompiler.compile("ĐẶT MÀU = MÀU_TẠI(10)")
        val screen = ScriptCompiler.compile("ĐẶT CÓ_MÀU = MÀN_HÌNH_CÓ_MÀU(255, 0, 0)")
        val region = ScriptCompiler.compile("ĐẶT TỶ_LỆ = TỶ_LỆ_MÀU_TRONG_VÙNG(0, 0, 0, 0, 1, 2, 3)")

        assertEquals("function_argument_count", point.diagnostics.first().code)
        assertEquals("function_argument_count", screen.diagnostics.first().code)
        assertEquals("function_argument_count", region.diagnostics.first().code)
    }

    @Test
    fun supportsDeviceModelAndroidVersionAndKeyguardBuiltins() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT HÃNG: CHUỖI = HÃNG_SẢN_XUẤT()
            ĐẶT MẪU: CHUỖI = MẪU_THIẾT_BỊ()
            ĐẶT PHIÊN_BẢN: CHUỖI = PHIÊN_BẢN_ANDROID()
            ĐẶT API: SỐ = MỨC_API_ANDROID()
            ĐẶT ĐANG_KHÓA: LUẬN_LÝ = MÀN_HÌNH_BỊ_KHÓA()
            ĐẶT ĐƯỢC_BẢO_MẬT: LUẬN_LÝ = THIẾT_BỊ_ĐƯỢC_BẢO_MẬT()
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.20", result.program?.languageVersion)
    }

    @Test
    fun rejectsArgumentsForDeviceAndKeyguardBuiltins() {
        val model = ScriptCompiler.compile("ĐẶT MẪU = MẪU_THIẾT_BỊ(1)")
        val api = ScriptCompiler.compile("ĐẶT API = MỨC_API_ANDROID(1)")
        val keyguard = ScriptCompiler.compile("ĐẶT KHÓA = MÀN_HÌNH_BỊ_KHÓA(1)")

        assertEquals("function_argument_count", model.diagnostics.first().code)
        assertEquals("function_argument_count", api.diagnostics.first().code)
        assertEquals("function_argument_count", keyguard.diagnostics.first().code)
    }

    @Test
    fun supportsDisplayOrientationAndForegroundAppBuiltins() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT RỘNG_DP: SỐ = CHIỀU_RỘNG_DP()
            ĐẶT CAO_DP: SỐ = CHIỀU_CAO_DP()
            ĐẶT TỶ_LỆ: SỐ = TỶ_LỆ_KHUNG_HÌNH()
            ĐẶT HƯỚNG: CHUỖI = HƯỚNG_MÀN_HÌNH()
            ĐẶT XOAY: SỐ = XOAY_MÀN_HÌNH()
            ĐẶT GÓC_XOAY: SỐ = GÓC_XOAY_MÀN_HÌNH()
            ĐẶT GÓI: CHUỖI = GÓI_ỨNG_DỤNG_ĐANG_MỞ()
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.20", result.program?.languageVersion)
    }

    @Test
    fun rejectsArgumentsForDisplayOrientationAndForegroundAppBuiltins() {
        val width = ScriptCompiler.compile("ĐẶT RỘNG = CHIỀU_RỘNG_DP(1)")
        val orientation = ScriptCompiler.compile("ĐẶT HƯỚNG = HƯỚNG_MÀN_HÌNH(1)")
        val foreground = ScriptCompiler.compile("ĐẶT GÓI = GÓI_ỨNG_DỤNG_ĐANG_MỞ(1)")

        assertEquals("function_argument_count", width.diagnostics.first().code)
        assertEquals("function_argument_count", orientation.diagnostics.first().code)
        assertEquals("function_argument_count", foreground.diagnostics.first().code)
    }

    @Test
    fun supportsDensityAndRelativeCoordinateBuiltins() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT MAT_DO: SỐ = MẬT_ĐỘ_MÀN_HÌNH()
            ĐẶT X_PIXEL: SỐ = DP_SANG_PIXEL(24)
            ĐẶT Y_DP: SỐ = PIXEL_SANG_DP(48)
            ĐẶT X_TƯƠNG_ĐỐI: SỐ = TỌA_ĐỘ_X_THEO_TỶ_LỆ(0.5)
            ĐẶT Y_TƯƠNG_ĐỐI: SỐ = TỌA_ĐỘ_Y_THEO_TỶ_LỆ(0.75)
            ĐẶT TỶ_LỆ_X: SỐ = TỶ_LỆ_X_THEO_TỌA_ĐỘ(100)
            ĐẶT TỶ_LỆ_Y: SỐ = TỶ_LỆ_Y_THEO_TỌA_ĐỘ(200)
            CHẠM(X_TƯƠNG_ĐỐI, Y_TƯƠNG_ĐỐI)
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.20", result.program?.languageVersion)
    }

    @Test
    fun validatesDensityAndRelativeCoordinateArgumentCounts() {
        val density = ScriptCompiler.compile("ĐẶT X = MẬT_ĐỘ_MÀN_HÌNH(1)")
        val dp = ScriptCompiler.compile("ĐẶT X = DP_SANG_PIXEL()")
        val ratio = ScriptCompiler.compile("ĐẶT X = TỌA_ĐỘ_X_THEO_TỶ_LỆ(0.5, 0.7)")

        assertEquals("function_argument_count", density.diagnostics.first().code)
        assertEquals("function_argument_count", dp.diagnostics.first().code)
        assertEquals("function_argument_count", ratio.diagnostics.first().code)
    }

    @Test
    fun supportsSafeAreaAndNaturalOrientationCoordinateBuiltins() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT X_AN_TOAN: SỐ = TỌA_ĐỘ_X_VÙNG_AN_TOÀN(0.5)
            ĐẶT Y_AN_TOAN: SỐ = TỌA_ĐỘ_Y_VÙNG_AN_TOÀN(0.75)
            ĐẶT TY_LE_X: SỐ = TỶ_LỆ_X_VÙNG_AN_TOÀN(X_AN_TOAN)
            ĐẶT TY_LE_Y: SỐ = TỶ_LỆ_Y_VÙNG_AN_TOÀN(Y_AN_TOAN)
            ĐẶT TOA_DO_HIEN_TAI: DANH_SÁCH = TỌA_ĐỘ_TỪ_HƯỚNG_GỐC(100, 200)
            ĐẶT TOA_DO_GOC: DANH_SÁCH = TỌA_ĐỘ_VỀ_HƯỚNG_GỐC(
                LẤY_PHẦN_TỬ(TOA_DO_HIEN_TAI, 0),
                LẤY_PHẦN_TỬ(TOA_DO_HIEN_TAI, 1)
            )
            CHẠM(LẤY_PHẦN_TỬ(TOA_DO_HIEN_TAI, 0), LẤY_PHẦN_TỬ(TOA_DO_HIEN_TAI, 1))
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.20", result.program?.languageVersion)
    }

    @Test
    fun validatesSafeAreaAndRotationFunctionArgumentCounts() {
        val safeX = ScriptCompiler.compile("ĐẶT X = TỌA_ĐỘ_X_VÙNG_AN_TOÀN()")
        val safeY = ScriptCompiler.compile("ĐẶT Y = TỶ_LỆ_Y_VÙNG_AN_TOÀN(1, 2)")
        val natural = ScriptCompiler.compile("ĐẶT P = TỌA_ĐỘ_TỪ_HƯỚNG_GỐC(1)")
        val current = ScriptCompiler.compile("ĐẶT P = TỌA_ĐỘ_VỀ_HƯỚNG_GỐC(1, 2, 3)")

        assertEquals("function_argument_count", safeX.diagnostics.first().code)
        assertEquals("function_argument_count", safeY.diagnostics.first().code)
        assertEquals("function_argument_count", natural.diagnostics.first().code)
        assertEquals("function_argument_count", current.diagnostics.first().code)
    }

    @Test
    fun supportsStructuredAccessibilityTextBuiltin() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT CÁC_KHỐI: DANH_SÁCH = ĐỌC_CẤU_TRÚC_VĂN_BẢN_MÀN_HÌNH()
            """.trimIndent()
        )

        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.20", result.program?.languageVersion)
    }

    @Test
    fun supportsOnDemandAccessibilityTextBuiltins() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT VAN_BAN: CHUỖI = ĐỌC_VĂN_BẢN_MÀN_HÌNH()
            ĐẶT CO_CHU: LUẬN_LÝ = MÀN_HÌNH_CHỨA_CHỮ("Đăng nhập")
            ĐẶT VI_TRI: DANH_SÁCH = TÌM_VỊ_TRÍ_CHỮ("Đăng nhập")
            """.trimIndent()
        )
        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.20", result.program?.languageVersion)
    }

    @Test
    fun validatesAccessibilityTextFunctionArgumentCounts() {
        val missingQuery = ScriptCompiler.compile("ĐẶT X = MÀN_HÌNH_CHỨA_CHỮ()")
        val tooMany = ScriptCompiler.compile("ĐẶT X = TÌM_VỊ_TRÍ_CHỮ(\"A\", \"B\")")
        assertEquals("function_argument_count", missingQuery.diagnostics.first().code)
        assertEquals("function_argument_count", tooMany.diagnostics.first().code)
    }

    @Test
    fun supportsStructuredTryCatchFinallySyntax() {
        val result = ScriptCompiler.compile(
            """
            THỬ {
                MỞ_ỨNG_DỤNG("invalid")
            } BẮT_LỖI (LỖI) {
                ĐẶT MÃ_LỖI: CHUỖI = LẤY_GIÁ_TRỊ(LỖI, "MÃ")
            } LUÔN_THỰC_HIỆN {
                CHẠM(10, 20)
            }
            """.trimIndent()
        )
        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.20", result.program?.languageVersion)
    }

    @Test
    fun requiresCatchOrFinallyAndUppercaseCatchVariable() {
        val missingHandler = ScriptCompiler.compile(
            """
            THỬ {
                CHỜ(1)
            }
            """.trimIndent()
        )
        val lowercaseVariable = ScriptCompiler.compile(
            """
            THỬ {
                CHỜ(1)
            } BẮT_LỖI (loi) {
                CHỜ(1)
            }
            """.trimIndent()
        )
        assertEquals("missing_catch_or_finally", missingHandler.diagnostics.first().code)
        assertEquals("identifier_must_be_uppercase", lowercaseVariable.diagnostics.first().code)
    }

    @Test
    fun supportsTryFinallyWithoutCatch() {
        val result = ScriptCompiler.compile(
            """
            THỬ {
                CHỜ(1)
            } LUÔN_THỰC_HIỆN {
                CHỜ(1)
            }
            """.trimIndent()
        )
        assertTrue(result.diagnostics.toString(), result.isSuccess)
    }

    @Test
    fun supportsAccessibilityElementActionsByTextAndResourceId() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT CÓ_NÚT: LUẬN_LÝ = TÌM_THÀNH_PHẦN_VĂN_BẢN("Đăng nhập")
            ĐẶT CÓ_ID: LUẬN_LÝ = TÌM_THÀNH_PHẦN_THEO_ID("login_button")
            ĐẶT TỌA_ĐỘ: DANH_SÁCH = TÌM_TỌA_ĐỘ_THÀNH_PHẦN("Đăng nhập")
            NHẤN_THÀNH_PHẦN_VĂN_BẢN("Đăng nhập")
            NHẬP_VĂN_BẢN_VÀO_THÀNH_PHẦN("Tên", "LIO")
            NHẤN_THÀNH_PHẦN_THEO_ID("login_button")
            NHẬP_VĂN_BẢN_THEO_ID("username", "AUTU")
            CUỘN_XUỐNG()
            CUỘN_LÊN()
            """.trimIndent()
        )
        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.20", result.program?.languageVersion)
    }

    @Test
    fun validatesAccessibilityElementFunctionArgumentCounts() {
        val missingText = ScriptCompiler.compile("ĐẶT X = TÌM_THÀNH_PHẦN_VĂN_BẢN()")
        val wrongSetText = ScriptCompiler.compile("ĐẶT X = NHẬP_VĂN_BẢN_THEO_ID(\"username\")")
        val tooManyScrollArguments = ScriptCompiler.compile("ĐẶT X = CUỘN_XUỐNG(1)")
        assertEquals("function_argument_count", missingText.diagnostics.first().code)
        assertEquals("function_argument_count", wrongSetText.diagnostics.first().code)
        assertEquals("function_argument_count", tooManyScrollArguments.diagnostics.first().code)
    }

    @Test
    fun supportsNullLiteralNestedCollectionsAndJsonBuiltins() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT CẤU_HÌNH: BẢN_ĐỒ = PHÂN_TÍCH_JSON("{\"TEN\":\"AUTU\",\"ITEMS\":[1,{\"X\":2},null]}")
            ĐẶT CÁC_MỤC: DANH_SÁCH = LẤY_GIÁ_TRỊ(CẤU_HÌNH, "ITEMS")
            ĐẶT PHẦN_TỬ: BẢN_ĐỒ = LẤY_PHẦN_TỬ(CÁC_MỤC, 1)
            ĐẶT TỌA_ĐỘ_X: SỐ = LẤY_GIÁ_TRỊ(PHẦN_TỬ, "X")
            ĐẶT GIÁ_TRỊ_RỖNG: BẤT_KỲ = RỖNG
            ĐẶT BẢN_SAO: DANH_SÁCH = TẠO_DANH_SÁCH(CẤU_HÌNH, RỖNG)
            ĐẶT JSON_KẾT_QUẢ: CHUỖI = CHUYỂN_JSON(CẤU_HÌNH)
            """.trimIndent()
        )
        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.20", result.program?.languageVersion)
    }

    @Test
    fun validatesJsonBuiltinFunctionArgumentCounts() {
        val wrongParse = ScriptCompiler.compile("ĐẶT X = PHÂN_TÍCH_JSON()")
        val wrongSerialize = ScriptCompiler.compile("ĐẶT X = CHUYỂN_JSON(1, 2)")
        assertEquals("function_argument_count", wrongParse.diagnostics.first().code)
        assertEquals("function_argument_count", wrongSerialize.diagnostics.first().code)
    }

    @Test
    fun supportsSandboxedFileAndJsonFileFunctions() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT CÓ_TỆP: LUẬN_LÝ = TỆP_TỒN_TẠI("settings/config.json")
            ĐẶT NỘI_DUNG: CHUỖI = ĐỌC_TỆP("settings/config.json")
            GHI_TỆP("settings/config.json", NỘI_DUNG)
            ĐẶT CẤU_HÌNH: BẢN_ĐỒ = ĐỌC_JSON_TỆP("settings/config.json")
            GHI_JSON_TỆP("settings/config.json", CẤU_HÌNH)
            TẠO_THƯ_MỤC("settings/backup")
            ĐẶT TỆP: DANH_SÁCH = LIỆT_KÊ_TỆP("settings")
            XÓA_TỆP("settings/old.txt")
            """.trimIndent()
        )
        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.20", result.program?.languageVersion)
    }

    @Test
    fun validatesFileFunctionArgumentCounts() {
        val wrongRead = ScriptCompiler.compile("ĐẶT X = ĐỌC_TỆP()")
        val wrongWrite = ScriptCompiler.compile("ĐẶT X = GHI_TỆP(\"a.txt\")")
        val wrongList = ScriptCompiler.compile("ĐẶT X = LIỆT_KÊ_TỆP()")
        val wrongDelete = ScriptCompiler.compile("ĐẶT X = XÓA_TỆP(\"a.txt\", \"b.txt\")")
        assertEquals("function_argument_count", wrongRead.diagnostics.first().code)
        assertEquals("function_argument_count", wrongWrite.diagnostics.first().code)
        assertEquals("function_argument_count", wrongList.diagnostics.first().code)
        assertEquals("function_argument_count", wrongDelete.diagnostics.first().code)
    }

    @Test
    fun supportsSecureHttpRequestBuiltInSignature() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT PHẢN_HỒI: BẢN_ĐỒ = GỬI_YÊU_CẦU_HTTP(
                "GET",
                "https://api.example.com/status",
                RỖNG,
                TẠO_BẢN_ĐỒ(),
                10000
            )
            """.trimIndent()
        )
        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.20", result.program?.languageVersion)
    }

    @Test
    fun validatesHttpRequestArgumentCount() {
        val invalid = ScriptCompiler.compile(
            """ĐẶT PHẢN_HỒI = GỬI_YÊU_CẦU_HTTP("GET", "https://api.example.com", RỖNG, TẠO_BẢN_ĐỒ())"""
        )
        assertEquals("function_argument_count", invalid.diagnostics.first().code)
    }

    @Test
    fun supportsMultilineNestedFunctionArguments() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT KẾT_QUẢ: SỐ = LỚN_NHẤT(
                10,
                NHỎ_NHẤT(
                    20,
                    15
                )
            )
            ĐẶT CẤU_HÌNH: BẢN_ĐỒ = TẠO_BẢN_ĐỒ(
                "MÃ", 200,
                "THÀNH_CÔNG", ĐÚNG
            )
            """.trimIndent()
        )
        assertTrue(result.diagnostics.toString(), result.isSuccess)
    }

    @Test
    fun supportsPrivateSqliteQueryAndMutationSignatures() {
        val result = ScriptCompiler.compile(
            """
            SQL_THỰC_THI(
                "CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY, name TEXT)",
                TẠO_DANH_SÁCH()
            )
            ĐẶT THAY_ĐỔI: BẢN_ĐỒ = SQL_THỰC_THI(
                "INSERT INTO users(name) VALUES (?)",
                TẠO_DANH_SÁCH("LIO")
            )
            ĐẶT NGƯỜI_DÙNG: DANH_SÁCH = SQL_TRUY_VẤN(
                "SELECT id, name FROM users WHERE name = ?",
                TẠO_DANH_SÁCH("LIO")
            )
            """.trimIndent()
        )
        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.20", result.program?.languageVersion)
    }

    @Test
    fun validatesSqlFunctionArgumentCounts() {
        val missingParams = ScriptCompiler.compile("""ĐẶT X = SQL_TRUY_VẤN("SELECT 1")""")
        val tooMany = ScriptCompiler.compile("""ĐẶT X = SQL_THỰC_THI("DELETE FROM users", TẠO_DANH_SÁCH(), 1)""")
        assertEquals("function_argument_count", missingParams.diagnostics.first().code)
        assertEquals("function_argument_count", tooMany.diagnostics.first().code)
    }


    @Test
    fun supportsOnDeviceMlVisionBuiltins() {
        val result = ScriptCompiler.compile(
            """
            ĐẶT VAN_BAN: CHUỖI = ĐỌC_VĂN_BẢN_VÙNG(0, 0, 300, 200)
            ĐẶT CO_CHU: LUẬN_LÝ = MÀN_HÌNH_CHỨA_CHỮ_TRONG_VÙNG("OK", 0, 0, 300, 200)
            ĐẶT VI_TRI_CHU: DANH_SÁCH = TÌM_VỊ_TRÍ_CHỮ_TRONG_VÙNG("OK", 0, 0, 300, 200)
            ĐẶT NHAN: DANH_SÁCH = NHẬN_DIỆN_NHÃN_ẢNH_MÀN_HÌNH()
            ĐẶT CO_NHAN: LUẬN_LÝ = MÀN_HÌNH_CHỨA_NHÃN_ẢNH("Phone")
            ĐẶT QR: DANH_SÁCH = QUÉT_MÃ_QR_MÀN_HÌNH()
            ĐẶT MA_VACH: DANH_SÁCH = QUÉT_MÃ_VẠCH_MÀN_HÌNH()
            ĐẶT QR_CHI_TIET: DANH_SÁCH = CHI_TIẾT_MÃ_QR_MÀN_HÌNH()
            ĐẶT VACH_CHI_TIET: DANH_SÁCH = CHI_TIẾT_MÃ_VẠCH_MÀN_HÌNH()
            ĐẶT VAT_THE: DANH_SÁCH = PHÁT_HIỆN_VẬT_THỂ_MÀN_HÌNH()
            ĐẶT CO_VAT_THE: LUẬN_LÝ = MÀN_HÌNH_CÓ_VẬT_THỂ("Food")
            ĐẶT TOA_DO: DANH_SÁCH = TÌM_TỌA_ĐỘ_VẬT_THỂ("Food")
            ĐẶT SO_LUONG: SỐ = ĐẾM_VẬT_THỂ_MÀN_HÌNH()
            """.trimIndent()
        )
        assertTrue(result.diagnostics.toString(), result.isSuccess)
        assertEquals("1.20", result.program?.languageVersion)
    }

    @Test
    fun validatesOnDeviceMlVisionFunctionArgumentCounts() {
        val wrongRegion = ScriptCompiler.compile("ĐẶT X = ĐỌC_VĂN_BẢN_VÙNG(1, 2, 3)")
        val wrongLabel = ScriptCompiler.compile("ĐẶT X = MÀN_HÌNH_CHỨA_NHÃN_ẢNH()")
        val wrongQr = ScriptCompiler.compile("ĐẶT X = QUÉT_MÃ_QR_MÀN_HÌNH(1)")
        val wrongObjects = ScriptCompiler.compile("ĐẶT X = PHÁT_HIỆN_VẬT_THỂ_MÀN_HÌNH(1)")
        assertEquals("function_argument_count", wrongRegion.diagnostics.first().code)
        assertEquals("function_argument_count", wrongLabel.diagnostics.first().code)
        assertEquals("function_argument_count", wrongQr.diagnostics.first().code)
        assertEquals("function_argument_count", wrongObjects.diagnostics.first().code)
    }

}
