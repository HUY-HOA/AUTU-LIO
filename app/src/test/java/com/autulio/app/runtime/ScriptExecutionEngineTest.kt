package com.autulio.app.runtime

import com.autulio.app.script.ScriptCompiler
import com.autulio.app.script.ScriptExpression
import com.autulio.app.script.ScriptInstruction
import com.autulio.app.script.ScriptProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ScriptExecutionEngineTest {
    @Test
    fun executesVariablesAndExpressionBasedRepeat() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                ĐẶT I = 0
                LẶP(3) {
                    CHẠM(I, 20)
                    I = I + 1
                }
                """
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("tap:0,20", "tap:1,20", "tap:2,20"), dispatcher.actions)
            assertEquals(ScriptExecutionState.COMPLETED, engine.snapshot().state)
            assertEquals(7L, engine.snapshot().completedInstructions)
        } finally {
            engine.close()
        }
    }

    @Test
    fun evaluatesConditionsAndShortCircuitBooleanOperators() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                ĐẶT PIN_MUC_TIEU: SỐ = 12
                NẾU (PIN_MUC_TIEU > 10 VÀ KHÔNG SAI) {
                    CHẠM(10, 20)
                } NGƯỢC_LẠI {
                    CHẠM(30, 40)
                }
                """
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("tap:10,20"), dispatcher.actions)
        } finally {
            engine.close()
        }
    }

    @Test
    fun whileLoopStopsAtBreakInsteadOfContinuingTheRemainingIterations() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                ĐẶT I = 0
                LẶP_KHI (I < 10) {
                    I = I + 1
                    NẾU (I == 3) {
                        THOÁT_LẶP
                    }
                    CHẠM(I, 20)
                }
                """
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("tap:1,20", "tap:2,20"), dispatcher.actions)
            assertEquals(ScriptExecutionState.COMPLETED, engine.snapshot().state)
        } finally {
            engine.close()
        }
    }

    @Test
    fun continueSkipsTheRestOfCurrentLoopIteration() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                ĐẶT I = 0
                LẶP(3) {
                    I = I + 1
                    NẾU (I == 2) {
                        BỎ_QUA_LƯỢT
                    }
                    CHẠM(I, 30)
                }
                """
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("tap:1,30", "tap:3,30"), dispatcher.actions)
        } finally {
            engine.close()
        }
    }

    @Test
    fun pauseFreezesWaitAndResumeContinuesTheProgram() {
        val actions = RecordingDispatcher()
        val firstAction = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(actions) {
            if (it.completedInstructions == 1L) firstAction.countDown()
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            assertEquals(
                ScriptExecutionStartResult.Started,
                engine.start(compile("CHẠM(1, 1)\nCHỜ(250)\nCHẠM(2, 2)"))
            )
            assertTrue(firstAction.await(2, TimeUnit.SECONDS))
            assertTrue(engine.pause())
            Thread.sleep(350)
            assertEquals(listOf("tap:1,1"), actions.actions)
            assertEquals(ScriptExecutionState.PAUSED, engine.snapshot().state)
            assertTrue(engine.resume())
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("tap:1,1", "tap:2,2"), actions.actions)
        } finally {
            engine.close()
        }
    }

    @Test
    fun stopCancelsLongWaitPromptly() {
        val cancelled = CountDownLatch(1)
        val engine = ScriptExecutionEngine(RecordingDispatcher()) {
            if (it.state == ScriptExecutionState.CANCELLED) cancelled.countDown()
        }
        try {
            assertEquals(ScriptExecutionStartResult.Started, engine.start(compile("CHỜ(60000)")))
            assertTrue(engine.stop())
            assertTrue(cancelled.await(2, TimeUnit.SECONDS))
            assertEquals(ScriptExecutionState.CANCELLED, engine.snapshot().state)
        } finally {
            engine.close()
        }
    }

    @Test
    fun stoppingWhilePausedRunsFinallyBlockBeforeCancellation() {
        val dispatcher = RecordingDispatcher()
        val firstTapEntered = CountDownLatch(1)
        val releaseFirstTap = CountDownLatch(1)
        dispatcher.firstTapEntered = firstTapEntered
        dispatcher.releaseFirstTap = releaseFirstTap

        val cancelled = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.CANCELLED) cancelled.countDown()
        }
        try {
            val program = compile(
                """
                THỬ {
                    CHẠM(10, 20)
                } LUÔN_THỰC_HIỆN {
                    CHẠM(30, 40)
                }
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(
                "Kịch bản chưa đi vào thao tác chạm.",
                firstTapEntered.await(5, TimeUnit.SECONDS)
            )
            assertTrue("Không thể tạm dừng kịch bản.", engine.pause())
            assertEquals(ScriptExecutionState.PAUSED, engine.snapshot().state)
            assertTrue("Không thể dừng kịch bản đang tạm dừng.", engine.stop())
            assertEquals(ScriptExecutionState.STOPPING, engine.snapshot().state)
            releaseFirstTap.countDown()

            assertTrue(
                "Kịch bản không kết thúc sau khi dừng từ trạng thái tạm dừng.",
                cancelled.await(3, TimeUnit.SECONDS)
            )
            assertEquals(ScriptExecutionState.CANCELLED, engine.snapshot().state)
            assertEquals(
                listOf("tap:10,20", "tap:30,40"),
                dispatcher.actions
            )
        } finally {
            releaseFirstTap.countDown()
            engine.close()
        }
    }

    @Test
    fun dispatchesAppLaunchAndDeviceQueryFunctions() {
        val dispatcher = RecordingDispatcher().apply { appLaunchResult = true }
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                NẾU (CÓ_MẠNG() VÀ ĐÚNG) {
                    MỞ_ỨNG_DỤNG("com.example.game")
                }
                """
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("function:CÓ_MẠNG"), dispatcher.functions)
            assertEquals(listOf("open:com.example.game"), dispatcher.actions)
        } finally {
            engine.close()
        }
    }

    @Test
    fun dispatchesBackAndHomeNavigationActions() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                QUAY_LẠI()
                VỀ_MÀN_HÌNH_CHÍNH()
                MỞ_ỨNG_DỤNG_GẦN_ĐÂY()
                MỞ_THANH_THÔNG_BÁO()
                MỞ_BẢNG_CÀI_ĐẶT_NHANH()
                MỞ_HỘP_THOẠI_NGUỒN()
                KHÓA_MÀN_HÌNH()
                BẬT_TẮT_CHIA_MÀN_HÌNH()
                """
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(
                listOf("back", "home", "recents", "notifications", "quickSettings", "powerDialog", "lockScreen", "splitScreen"),
                dispatcher.actions
            )
            assertEquals(8L, engine.snapshot().completedInstructions)
        } finally {
            engine.close()
        }
    }

    @Test
    fun rejectsProgramWithTamperedInstructionMetadata() {
        val engine = ScriptExecutionEngine(RecordingDispatcher())
        try {
            val instructions = listOf(
                ScriptInstruction.Command(
                    "CHẠM",
                    listOf(ScriptExpression.NumberLiteral(1.0), ScriptExpression.NumberLiteral(2.0))
                )
            )
            val forged = ScriptProgram(
                languageVersion = ScriptCompiler.LANGUAGE_VERSION,
                instructions = instructions,
                expandedInstructionCount = 5_000,
                estimatedDurationMs = 0
            )
            val result = engine.start(forged)
            assertTrue(result is ScriptExecutionStartResult.Rejected)
            assertEquals("program_metadata_mismatch", (result as ScriptExecutionStartResult.Rejected).code)
        } finally {
            engine.close()
        }
    }

    @Test
    fun rejectsExpandedInstructionBudgetExceededEvenIfMetadataClaimsSafe() {
        val engine = ScriptExecutionEngine(RecordingDispatcher())
        try {
            val body = listOf(
                ScriptInstruction.Repeat(
                    ScriptExpression.NumberLiteral(100.0),
                    listOf(
                        ScriptInstruction.Repeat(
                            ScriptExpression.NumberLiteral(100.0),
                            listOf(
                                ScriptInstruction.Command(
                                    "CHẠM",
                                    listOf(ScriptExpression.NumberLiteral(1.0), ScriptExpression.NumberLiteral(2.0))
                                )
                            )
                        )
                    )
                )
            )
            val forged = ScriptProgram(
                languageVersion = ScriptCompiler.LANGUAGE_VERSION,
                instructions = body,
                expandedInstructionCount = 1,
                estimatedDurationMs = 0
            )
            val result = engine.start(forged)
            assertTrue(result is ScriptExecutionStartResult.Rejected)
            assertEquals("instruction_budget_exceeded", (result as ScriptExecutionStartResult.Rejected).code)
        } finally {
            engine.close()
        }
    }

    @Test
    fun failedActionMovesEngineToFailedState() {
        val failure = CountDownLatch(1)
        val dispatcher = object : ScriptActionDispatcher {
            override fun tap(x: Int, y: Int) = false
            override fun longPress(x: Int, y: Int, durationMs: Int) = false
            override fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int) = false
        }
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.FAILED) failure.countDown()
        }
        try {
            assertEquals(ScriptExecutionStartResult.Started, engine.start(compile("CHẠM(1, 2)")))
            assertTrue(failure.await(2, TimeUnit.SECONDS))
            assertEquals("dispatch_failed", engine.snapshot().errorCode)
        } finally {
            engine.close()
        }
    }

    @Test
    fun waitUntilPollsConditionAndContinuesWhenItBecomesTrue() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val taps = CopyOnWriteArrayList<String>()
        val completed = CountDownLatch(1)
        val dispatcher = object : ScriptActionDispatcher {
            override fun tap(x: Int, y: Int): Boolean {
                taps += "$x,$y"
                return true
            }
            override fun longPress(x: Int, y: Int, durationMs: Int) = true
            override fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int) = true
            override fun evaluateFunction(name: String, arguments: List<Any?>): Any? =
                name == "ĐANG_SẠC" && calls.incrementAndGet() >= 3
        }
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile("CHỜ_ĐẾN_KHI(ĐANG_SẠC(), 2000, 50)\nCHẠM(5, 6)")
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(3, calls.get())
            assertEquals(listOf("5,6"), taps)
        } finally {
            engine.close()
        }
    }

    @Test
    fun waitUntilFailsWithTimeoutInsteadOfWaitingForever() {
        val failed = CountDownLatch(1)
        val dispatcher = object : ScriptActionDispatcher {
            override fun tap(x: Int, y: Int) = true
            override fun longPress(x: Int, y: Int, durationMs: Int) = true
            override fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int) = true
            override fun evaluateFunction(name: String, arguments: List<Any?>): Any? = false
        }
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.FAILED) failed.countDown()
        }
        try {
            assertEquals(ScriptExecutionStartResult.Started, engine.start(compile("CHỜ_ĐẾN_KHI(ĐANG_SẠC(), 150, 50)")))
            assertTrue(failed.await(2, TimeUnit.SECONDS))
            assertEquals("wait_until_timeout", engine.snapshot().errorCode)
        } finally {
            engine.close()
        }
    }

    @Test
    fun loopUntilStopsWhenConditionBecomesTrue() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                ĐẶT DEM = 0
                LẶP_ĐẾN_KHI(DEM >= 3) {
                    DEM = DEM + 1
                    CHẠM(DEM, 30)
                }
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("tap:1,30", "tap:2,30", "tap:3,30"), dispatcher.actions)
        } finally {
            engine.close()
        }
    }

    @Test
    fun createsMutatesAndIteratesTypedVietnameseLists() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                ĐẶT DANH_SÁCH: DANH_SÁCH = TẠO_DANH_SÁCH(10, 20)
                ĐẶT SO_LUONG: SỐ = THÊM_PHẦN_TỬ(DANH_SÁCH, 30)
                ĐẶT CO_20: LUẬN_LÝ = CÓ_PHẦN_TỬ(DANH_SÁCH, 20)
                ĐẶT PHAN_TU: SỐ = LẤY_PHẦN_TỬ(DANH_SÁCH, 1)
                ĐẶT DA_XOA: SỐ = XÓA_PHẦN_TỬ(DANH_SÁCH, 1)
                ĐẶT DA_DAT: SỐ = ĐẶT_PHẦN_TỬ(DANH_SÁCH, 0, 15)
                NẾU (SO_LUONG == 3 VÀ CO_20 VÀ PHAN_TU == 20 VÀ DA_XOA == 20 VÀ DA_DAT == 15 VÀ ĐỘ_DÀI(DANH_SÁCH) == 2) {
                    LẶP_MỖI (GIA_TRI TRONG DANH_SÁCH) {
                        CHẠM(GIA_TRI, 40)
                    }
                }
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("tap:15,40", "tap:30,40"), dispatcher.actions)
        } finally {
            engine.close()
        }
    }

    @Test
    fun createsUpdatesRemovesAndIteratesVietnameseMaps() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                ĐẶT CAU_HINH: BẢN_ĐỒ = TẠO_BẢN_ĐỒ("X", 10, "Y", 20)
                ĐẶT GIA_TRI_X: SỐ = LẤY_GIÁ_TRỊ(CAU_HINH, "X")
                ĐẶT DA_DAT: SỐ = ĐẶT_GIÁ_TRỊ(CAU_HINH, "Z", 30)
                ĐẶT CO_Z: LUẬN_LÝ = CÓ_KHÓA(CAU_HINH, "Z")
                ĐẶT DA_XOA: SỐ = XÓA_KHÓA(CAU_HINH, "Y")
                ĐẶT KHOA: DANH_SÁCH = DANH_SÁCH_KHÓA(CAU_HINH)
                NẾU (GIA_TRI_X == 10 VÀ DA_DAT == 30 VÀ CO_Z VÀ DA_XOA == 20 VÀ ĐỘ_DÀI(CAU_HINH) == 2) {
                    LẶP_MỖI (K TRONG KHOA) {
                        CHẠM(ĐỘ_DÀI(K), 40)
                    }
                }
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("tap:1,40", "tap:1,40"), dispatcher.actions)
        } finally {
            engine.close()
        }
    }

    @Test
    fun transformsSearchesSplitsAndJoinsUnicodeStrings() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                ĐẶT NGUON: CHUỖI = " AUTU-LIO "
                ĐẶT CHUAN: CHUỖI = CẮT_KHOẢNG_TRẮNG(NGUON)
                ĐẶT HOA: CHUỖI = CHỮ_HOA(CHUAN)
                ĐẶT THUONG: CHUỖI = CHỮ_THƯỜNG(HOA)
                ĐẶT CAT: CHUỖI = CẮT_CHUỖI(HOA, 0, 4)
                ĐẶT KY_TU: CHUỖI = KÝ_TỰ_TẠI(HOA, 0)
                ĐẶT THAY: CHUỖI = THAY_THẾ(HOA, "AUTU", "LIO")
                ĐẶT CAC_TU: DANH_SÁCH = TÁCH_CHUỖI("A,B,C", ",")
                ĐẶT GHEP: CHUỖI = NỐI_CHUỖI(CAC_TU, "-")
                NẾU (CHỨA_CHUỖI(HOA, "AUTU") VÀ BẮT_ĐẦU_BẰNG(HOA, "AUTU") VÀ KẾT_THÚC_BẰNG(HOA, "LIO") VÀ CAT == "AUTU" VÀ KY_TU == "A" VÀ THAY == "LIO-LIO" VÀ GHEP == "A-B-C" VÀ THUONG == "autu-lio") {
                    CHẠM(1, 1)
                }
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("tap:1,1"), dispatcher.actions)
        } finally {
            engine.close()
        }
    }

    @Test
    fun rejectsEmptySplitDelimiterAndInvalidUnicodeSlice() {
        val splitFailed = CountDownLatch(1)
        val splitEngine = ScriptExecutionEngine(RecordingDispatcher()) {
            if (it.state == ScriptExecutionState.FAILED) splitFailed.countDown()
        }
        try {
            assertEquals(ScriptExecutionStartResult.Started, splitEngine.start(
                compile("ĐẶT KET_QUA = TÁCH_CHUỖI(\"ABC\", \"\")")
            ))
            assertTrue(splitFailed.await(3, TimeUnit.SECONDS))
            assertEquals("empty_delimiter", splitEngine.snapshot().errorCode)
        } finally {
            splitEngine.close()
        }

        val sliceFailed = CountDownLatch(1)
        val sliceEngine = ScriptExecutionEngine(RecordingDispatcher()) {
            if (it.state == ScriptExecutionState.FAILED) sliceFailed.countDown()
        }
        try {
            assertEquals(ScriptExecutionStartResult.Started, sliceEngine.start(
                compile("ĐẶT KET_QUA = CẮT_CHUỖI(\"ABC\", 2, 5)")
            ))
            assertTrue(sliceFailed.await(3, TimeUnit.SECONDS))
            assertEquals("string_range", sliceEngine.snapshot().errorCode)
        } finally {
            sliceEngine.close()
        }
    }

    @Test
    fun readsScreenTextFindsTextAndUsesItsCenterCoordinates() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                ĐẶT NOI_DUNG: CHUỖI = ĐỌC_VĂN_BẢN_MÀN_HÌNH()
                ĐẶT TOA_DO: DANH_SÁCH = TÌM_VỊ_TRÍ_CHỮ("Đăng nhập")
                NẾU (MÀN_HÌNH_CHỨA_CHỮ("Đăng nhập") VÀ CHỨA_CHUỖI(NOI_DUNG, "AUTU")) {
                    CHẠM(LẤY_PHẦN_TỬ(TOA_DO, 0), LẤY_PHẦN_TỬ(TOA_DO, 1))
                }
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("tap:100,200"), dispatcher.actions)
        } finally {
            engine.close()
        }
    }

    @Test
    fun readsStructuredAccessibilityTextFromDispatcher() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                ĐẶT CÁC_KHỐI: DANH_SÁCH = ĐỌC_CẤU_TRÚC_VĂN_BẢN_MÀN_HÌNH()
                ĐẶT KHỐI_ĐẦU: BẢN_ĐỒ = LẤY_PHẦN_TỬ(CÁC_KHỐI, 0)
                ĐẶT CÁC_DÒNG: DANH_SÁCH = LẤY_GIÁ_TRỊ(KHỐI_ĐẦU, "CÁC_DÒNG")
                ĐẶT DÒNG_ĐẦU: BẢN_ĐỒ = LẤY_PHẦN_TỬ(CÁC_DÒNG, 0)
                NẾU (LẤY_GIÁ_TRỊ(DÒNG_ĐẦU, "VĂN_BẢN") == "Đăng nhập") {
                    CHẠM(LẤY_GIÁ_TRỊ(DÒNG_ĐẦU, "X"), LẤY_GIÁ_TRỊ(DÒNG_ĐẦU, "Y"))
                }
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("tap:120,240"), dispatcher.actions)
        } finally {
            engine.close()
        }
    }

    @Test
    fun dispatchesMultiFingerTapsAndSimultaneousSwipePaths() {
        val dispatcher = RecordingDispatcher()
        val terminal = CountDownLatch(1)
        var terminalSnapshot: ScriptExecutionSnapshot? = null
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state in setOf(
                    ScriptExecutionState.COMPLETED,
                    ScriptExecutionState.FAILED,
                    ScriptExecutionState.CANCELLED
                )
            ) {
                terminalSnapshot = it
                terminal.countDown()
            }
        }
        try {
            val program = compile(
                """
                CHẠM_ĐA_ĐIỂM(
                    TẠO_DANH_SÁCH(TẠO_DANH_SÁCH(10, 20), TẠO_DANH_SÁCH(30, 40)),
                    100
                )
                VUỐT_ĐA_ĐIỂM(
                    TẠO_DANH_SÁCH(
                        TẠO_DANH_SÁCH(10, 20, 15, 25),
                        TẠO_DANH_SÁCH(30, 40, 25, 35)
                    ),
                    200
                )
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue("Execution did not reach a terminal state: " + engine.snapshot(), terminal.await(3, TimeUnit.SECONDS))
            assertEquals("Unexpected terminal snapshot: " + terminalSnapshot, ScriptExecutionState.COMPLETED, terminalSnapshot?.state)
            assertEquals(
                listOf(
                    "multiTouch:100:10,20,10,20;30,40,30,40",
                    "multiTouch:200:10,20,15,25;30,40,25,35"
                ),
                dispatcher.actions
            )
        } finally {
            engine.close()
        }
    }

    @Test
    fun catchesRuntimeFailureAndAlwaysRunsFinallyBlock() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                THỬ {
                    MỞ_ỨNG_DỤNG("invalid")
                } BẮT_LỖI (LỖI) {
                    ĐẶT MÃ_LỖI: CHUỖI = LẤY_GIÁ_TRỊ(LỖI, "MÃ")
                    NẾU (MÃ_LỖI == "invalid_package_name") {
                        CHẠM(10, 20)
                    }
                } LUÔN_THỰC_HIỆN {
                    CHẠM(30, 40)
                }
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("tap:10,20", "tap:30,40"), dispatcher.actions)
        } finally {
            engine.close()
        }
    }

    @Test
    fun runsFinallyBlockAfterExecutionIsStopped() {
        val dispatcher = RecordingDispatcher()
        val firstTapEntered = CountDownLatch(1)
        val releaseFirstTap = CountDownLatch(1)
        dispatcher.firstTapEntered = firstTapEntered
        dispatcher.releaseFirstTap = releaseFirstTap

        val cancelled = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.CANCELLED) cancelled.countDown()
        }
        try {
            val program = compile(
                """
                THỬ {
                    CHẠM(10, 20)
                } LUÔN_THỰC_HIỆN {
                    CHẠM(30, 40)
                }
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue("Kịch bản chưa đi vào thao tác chạm.", firstTapEntered.await(5, TimeUnit.SECONDS))
            assertTrue("Không thể gửi yêu cầu dừng.", engine.stop())
            releaseFirstTap.countDown()

            assertTrue("Kịch bản không chuyển sang trạng thái đã dừng.", cancelled.await(3, TimeUnit.SECONDS))
            assertEquals(ScriptExecutionState.CANCELLED, engine.snapshot().state)
            assertEquals(
                listOf("tap:10,20", "tap:30,40"),
                dispatcher.actions
            )
        } finally {
            releaseFirstTap.countDown()
            engine.close()
        }
    }

    @Test
    fun runsFinallyBeforePropagatingAnUncaughtFailure() {
        val dispatcher = RecordingDispatcher()
        val failed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.FAILED) failed.countDown()
        }
        try {
            val program = compile(
                """
                THỬ {
                    MỞ_ỨNG_DỤNG("invalid")
                } LUÔN_THỰC_HIỆN {
                    CHẠM(50, 60)
                }
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(failed.await(3, TimeUnit.SECONDS))
            assertEquals("invalid_package_name", engine.snapshot().errorCode)
            assertEquals(listOf("tap:50,60"), dispatcher.actions)
        } finally {
            engine.close()
        }
    }

    @Test
    fun findsAndActsOnAccessibilityElementsByTextAndResourceId() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                NẾU (TÌM_THÀNH_PHẦN_VĂN_BẢN("Đăng nhập")) {
                    NHẤN_THÀNH_PHẦN_VĂN_BẢN("Đăng nhập")
                    NHẬP_VĂN_BẢN_VÀO_THÀNH_PHẦN("Tên người dùng", "LIO")
                    ĐẶT TOA_DO: DANH_SÁCH = TÌM_TỌA_ĐỘ_THÀNH_PHẦN("Đăng nhập")
                    CHẠM(LẤY_PHẦN_TỬ(TOA_DO, 0), LẤY_PHẦN_TỬ(TOA_DO, 1))
                }
                NẾU (TÌM_THÀNH_PHẦN_THEO_ID("login_button")) {
                    NHẤN_THÀNH_PHẦN_THEO_ID("login_button")
                }
                NHẬP_VĂN_BẢN_THEO_ID("username", "AUTU")
                CUỘN_XUỐNG()
                CUỘN_LÊN()
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(
                listOf(
                    "accessibilityClick:Đăng nhập",
                    "accessibilitySetText:Tên người dùng=LIO",
                    "tap:150,250",
                    "accessibilityClickId:login_button",
                    "accessibilitySetTextId:username=AUTU"
                ),
                dispatcher.actions
            )
        } finally {
            engine.close()
        }
    }

    @Test
    fun parsesAndSerializesNestedJsonWithNullValues() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                ĐẶT CẤU_HÌNH: BẢN_ĐỒ = PHÂN_TÍCH_JSON("{\"A\":[1,{\"B\":2},null]}")
                ĐẶT CÁC_MỤC: DANH_SÁCH = LẤY_GIÁ_TRỊ(CẤU_HÌNH, "A")
                ĐẶT PHẦN_TỬ: BẢN_ĐỒ = LẤY_PHẦN_TỬ(CÁC_MỤC, 1)
                ĐẶT GIA_TRI_B: SỐ = LẤY_GIÁ_TRỊ(PHẦN_TỬ, "B")
                ĐẶT JSON_KẾT_QUẢ: CHUỖI = CHUYỂN_JSON(CẤU_HÌNH)
                NẾU (GIA_TRI_B == 2 VÀ LẤY_PHẦN_TỬ(CÁC_MỤC, 2) == RỖNG VÀ CHỨA_CHUỖI(JSON_KẾT_QUẢ, "null")) {
                    CHẠM(70, 80)
                }
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(ScriptExecutionState.COMPLETED, engine.snapshot().state)
            assertEquals(listOf("tap:70,80"), dispatcher.actions)
        } finally {
            engine.close()
        }
    }

    @Test
    fun rejectsMalformedJsonInsideScript() {
        val failed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(RecordingDispatcher()) {
            if (it.state == ScriptExecutionState.FAILED) failed.countDown()
        }
        try {
            val program = compile("ĐẶT X = PHÂN_TÍCH_JSON(\"{bad}\")")
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(failed.await(3, TimeUnit.SECONDS))
            assertEquals("invalid_json", engine.snapshot().errorCode)
        } finally {
            engine.close()
        }
    }

    @Test
    fun runsSandboxedFileAndJsonStorageFunctions() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                ĐẶT NỘI_DUNG: CHUỖI = ĐỌC_TỆP("settings/config.txt")
                GHI_TỆP("settings/config.txt", NỘI_DUNG + " updated")
                NẾU (TỆP_TỒN_TẠI("settings/config.txt")) {
                    ĐẶT CẤU_HÌNH: BẢN_ĐỒ = ĐỌC_JSON_TỆP("settings/config.json")
                    GHI_JSON_TỆP("settings/backup.json", CẤU_HÌNH)
                }
                TẠO_THƯ_MỤC("settings/backup")
                ĐẶT TỆP: DANH_SÁCH = LIỆT_KÊ_TỆP("settings")
                XÓA_TỆP("settings/old.txt")
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(ScriptExecutionState.COMPLETED, engine.snapshot().state)
            assertEquals(
                listOf(
                    "writeFile:settings/config.txt=AUTU-LIO updated",
                    "writeJson:settings/backup.json"
                ),
                dispatcher.actions
            )
        } finally {
            engine.close()
        }
    }

    @Test
    fun sendsHttpRequestAndReadsStructuredResponseMap() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                ĐẶT PHẢN_HỒI: BẢN_ĐỒ = GỬI_YÊU_CẦU_HTTP(
                    "GET",
                    "https://api.example.com/status",
                    RỖNG,
                    TẠO_BẢN_ĐỒ("Authorization", "Bearer example-token"),
                    10000
                )
                ĐẶT MÃ_TRẠNG_THÁI: SỐ = LẤY_GIÁ_TRỊ(PHẢN_HỒI, "TRẠNG_THÁI")
                ĐẶT NOI_DUNG: CHUỖI = LẤY_GIÁ_TRỊ(PHẢN_HỒI, "NỘI_DUNG")
                NẾU (MÃ_TRẠNG_THÁI == 200 VÀ CHỨA_CHUỖI(NOI_DUNG, "sẵn sàng")) {
                    CHẠM(40, 50)
                }
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("tap:40,50"), dispatcher.actions)
            assertTrue(dispatcher.functions.any { it == "function:GỬI_YÊU_CẦU_HTTP" })
        } finally {
            engine.close()
        }
    }

    @Test
    fun evaluatesIsoDateTimeBuiltinsInsideScripts() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                ĐẶT MỐC: SỐ = PHÂN_TÍCH_THỜI_GIAN_ISO("2026-11-20T14:30:00+07:00")
                ĐẶT NĂM: CHUỖI = ĐỊNH_DẠNG_THỜI_GIAN(MỐC, "yyyy")
                NẾU (NĂM == "2026" VÀ ĐỘ_DÀI(THỜI_GIAN_ISO()) > 0) {
                    CHẠM(40, 50)
                }
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("tap:40,50"), dispatcher.actions)
        } finally {
            engine.close()
        }
    }

    @Test
    fun rejectsIsoDateTimeWithoutAnExplicitOffsetAtRuntime() {
        val failed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(RecordingDispatcher()) {
            if (it.state == ScriptExecutionState.FAILED) failed.countDown()
        }
        try {
            val program = compile(
                """ĐẶT MỐC = PHÂN_TÍCH_THỜI_GIAN_ISO("2026-11-20T14:30:00")"""
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(failed.await(3, TimeUnit.SECONDS))
            assertEquals("invalid_datetime", engine.snapshot().errorCode)
        } finally {
            engine.close()
        }
    }

    private fun compile(source: String): ScriptProgram {
        val result = ScriptCompiler.compile(source)
        assertTrue(result.diagnostics.toString(), result.isSuccess)
        return result.program!!
    }

    private class RecordingDispatcher : ScriptActionDispatcher {
        val actions = CopyOnWriteArrayList<String>()
        val functions = CopyOnWriteArrayList<String>()
        var appLaunchResult = false
        var firstTapEntered: CountDownLatch? = null
        var releaseFirstTap: CountDownLatch? = null

        override fun tap(x: Int, y: Int): Boolean {
            actions += "tap:$x,$y"
            if (actions.size == 1) {
                firstTapEntered?.countDown()
                releaseFirstTap?.await(5, TimeUnit.SECONDS)
            }
            return true
        }

        override fun longPress(x: Int, y: Int, durationMs: Int): Boolean {
            actions += "longPress:$x,$y,$durationMs"
            return true
        }

        override fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int): Boolean {
            actions += "swipe:$startX,$startY,$endX,$endY,$durationMs"
            return true
        }

        override fun multiTouch(paths: List<List<Int>>, durationMs: Int): Boolean {
            actions += "multiTouch:$durationMs:" + paths.joinToString(";") { it.joinToString(",") }
            return true
        }

        override fun openApp(packageName: String): Boolean {
            actions += "open:$packageName"
            return appLaunchResult
        }

        override fun goBack(): Boolean {
            actions += "back"
            return true
        }

        override fun goHome(): Boolean {
            actions += "home"
            return true
        }

        override fun showRecents(): Boolean {
            actions += "recents"
            return true
        }

        override fun showNotifications(): Boolean {
            actions += "notifications"
            return true
        }

        override fun showQuickSettings(): Boolean {
            actions += "quickSettings"
            return true
        }

        override fun showPowerDialog(): Boolean {
            actions += "powerDialog"
            return true
        }

        override fun lockScreen(): Boolean {
            actions += "lockScreen"
            return true
        }

        override fun toggleSplitScreen(): Boolean {
            actions += "splitScreen"
            return true
        }

        override fun evaluateFunction(name: String, arguments: List<Any?>): Any? {
            functions += "function:$name"
            return when (name) {
                "GỬI_YÊU_CẦU_HTTP" -> linkedMapOf(
                    "TRẠNG_THÁI" to 200,
                    "THÀNH_CÔNG" to true,
                    "NỘI_DUNG" to """{"trạng_thái":"sẵn sàng"}""",
                    "TIÊU_ĐỀ" to emptyMap<String, Any?>(),
                    "URL" to "https://api.example.com/status"
                )
                "ĐỌC_VĂN_BẢN_MÀN_HÌNH" -> "AUTU-LIO Đăng nhập"
                "ĐỌC_CẤU_TRÚC_VĂN_BẢN_MÀN_HÌNH" -> listOf(
                    linkedMapOf<String, Any?>(
                        "VĂN_BẢN" to "Đăng nhập",
                        "X" to 100.0,
                        "Y" to 200.0,
                        "RỘNG" to 160.0,
                        "CAO" to 40.0,
                        "CÁC_DÒNG" to listOf(
                            linkedMapOf<String, Any?>(
                                "VĂN_BẢN" to "Đăng nhập",
                                "X" to 120.0,
                                "Y" to 240.0,
                                "RỘNG" to 120.0,
                                "CAO" to 24.0
                            )
                        )
                    )
                )
                "MÀN_HÌNH_CHỨA_CHỮ" -> arguments.firstOrNull() == "Đăng nhập"
                "TÌM_VỊ_TRÍ_CHỮ" -> listOf(100, 200)
                "TÌM_THÀNH_PHẦN_VĂN_BẢN" -> arguments.firstOrNull() == "Đăng nhập"
                "NHẤN_THÀNH_PHẦN_VĂN_BẢN" -> {
                    actions += "accessibilityClick:${arguments.firstOrNull()}"
                    true
                }
                "NHẬP_VĂN_BẢN_VÀO_THÀNH_PHẦN" -> {
                    actions += "accessibilitySetText:${arguments.firstOrNull()}=${arguments.getOrNull(1)}"
                    true
                }
                "TÌM_TỌA_ĐỘ_THÀNH_PHẦN" -> listOf(150, 250)
                "TÌM_THÀNH_PHẦN_THEO_ID" -> arguments.firstOrNull() == "login_button"
                "NHẤN_THÀNH_PHẦN_THEO_ID" -> {
                    actions += "accessibilityClickId:${arguments.firstOrNull()}"
                    true
                }
                "NHẬP_VĂN_BẢN_THEO_ID" -> {
                    actions += "accessibilitySetTextId:${arguments.firstOrNull()}=${arguments.getOrNull(1)}"
                    true
                }
                "CUỘN_XUỐNG" -> true
                "CUỘN_LÊN" -> true
                "ĐỌC_TỆP" -> "AUTU-LIO"
                "GHI_TỆP" -> {
                    actions += "writeFile:${arguments.firstOrNull()}=${arguments.getOrNull(1)}"
                    true
                }
                "TỆP_TỒN_TẠI" -> true
                "XÓA_TỆP" -> true
                "TẠO_THƯ_MỤC" -> true
                "LIỆT_KÊ_TỆP" -> listOf("config.json", "state.txt")
                "ĐỌC_JSON_TỆP" -> linkedMapOf("VALUE" to 7.0)
                "GHI_JSON_TỆP" -> {
                    actions += "writeJson:${arguments.firstOrNull()}"
                    true
                }
                else -> name == "CÓ_MẠNG"
            }
        }
    }

    @Test
    fun enforcesDeclaredVariableTypesAndEvaluatesExponentExpressions() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                ĐẶT MUC_TIEU: SỐ = 21
                ĐẶT CO_THE_CHAY: LUẬN_LÝ = ĐÚNG
                ĐẶT NOI_DUNG: CHUỖI = "OK"
                NẾU (2 ^ 3 ^ 2 == 512 VÀ CO_THE_CHAY VÀ ĐỘ_DÀI(NOI_DUNG) == 2) {
                    MUC_TIEU = MUC_TIEU + 1
                    CHẠM(MUC_TIEU, 40)
                }
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("tap:22,40"), dispatcher.actions)
        } finally {
            engine.close()
        }
    }

    @Test
    fun rejectsRuntimeValueThatViolatesDeclaredType() {
        val failed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(RecordingDispatcher()) {
            if (it.state == ScriptExecutionState.FAILED) failed.countDown()
        }
        try {
            val program = compile("ĐẶT SO_LAN: SỐ = \"SAI\"")
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(failed.await(2, TimeUnit.SECONDS))
            assertEquals("variable_type_mismatch", engine.snapshot().errorCode)
        } finally {
            engine.close()
        }
    }


    @Test
    fun executesUserDefinedFunctionWithTypedParametersAndReturnValue() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                ĐỊNH_NGHĨA_HÀM TINH_TONG(A: SỐ, B: SỐ): SỐ {
                    TRẢ_VỀ A + B
                }
                ĐẶT KET_QUA: SỐ = TINH_TONG(10, 12)
                CHẠM(KET_QUA, 30)
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("tap:22,30"), dispatcher.actions)
        } finally {
            engine.close()
        }
    }

    @Test
    fun stopsRecursiveFunctionCallsAtTheConfiguredDepthLimit() {
        val failed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(RecordingDispatcher()) {
            if (it.state == ScriptExecutionState.FAILED) failed.countDown()
        }
        try {
            val program = compile(
                """
                ĐỊNH_NGHĨA_HÀM DE_QUY(N: SỐ): SỐ {
                    TRẢ_VỀ DE_QUY(N + 1)
                }
                ĐẶT KET_QUA = DE_QUY(0)
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(failed.await(3, TimeUnit.SECONDS))
            assertEquals("function_depth_exceeded", engine.snapshot().errorCode)
        } finally {
            engine.close()
        }
    }

}
