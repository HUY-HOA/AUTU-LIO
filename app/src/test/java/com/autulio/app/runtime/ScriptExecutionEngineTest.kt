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
    fun performsRegionOcrImageLabelingAndBarcodeRecognitionBuiltins() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                ĐẶT VAN_BAN: CHUỖI = ĐỌC_VĂN_BẢN_VÙNG(0, 0, 100, 100)
                NẾU (MÀN_HÌNH_CHỨA_CHỮ_TRONG_VÙNG("OK", 0, 0, 200, 200) VÀ CHỨA_CHUỖI(VAN_BAN, "vùng")) {
                    ĐẶT VI_TRI: DANH_SÁCH = TÌM_VỊ_TRÍ_CHỮ_TRONG_VÙNG("OK", 0, 0, 200, 200)
                    CHẠM(LẤY_PHẦN_TỬ(VI_TRI, 0), LẤY_PHẦN_TỬ(VI_TRI, 1))
                }
                ĐẶT NHAN: DANH_SÁCH = NHẬN_DIỆN_NHÃN_ẢNH_MÀN_HÌNH()
                NẾU (MÀN_HÌNH_CHỨA_NHÃN_ẢNH("Phone")) {
                    ĐẶT QR: DANH_SÁCH = QUÉT_MÃ_QR_MÀN_HÌNH()
                    ĐẶT MA_VACH: DANH_SÁCH = QUÉT_MÃ_VẠCH_MÀN_HÌNH()
                    CHỜ(1)
                }
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("tap:110,220"), dispatcher.actions)
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
    fun detectsObjectsAndUsesBoundingBoxCoordinatesFromNestedResults() {
        val dispatcher = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                ĐẶT VẬT_THỂ: DANH_SÁCH = PHÁT_HIỆN_VẬT_THỂ_MÀN_HÌNH()
                ĐẶT SO_LUONG: SỐ = ĐẾM_VẬT_THỂ_MÀN_HÌNH()
                NẾU (MÀN_HÌNH_CÓ_VẬT_THỂ("Food") VÀ SO_LUONG == 1) {
                    ĐẶT NHÃN: DANH_SÁCH = LẤY_GIÁ_TRỊ(LẤY_PHẦN_TỬ(VẬT_THỂ, 0), "NHÃN")
                    ĐẶT TÊN_NHÃN: CHUỖI = LẤY_GIÁ_TRỊ(LẤY_PHẦN_TỬ(NHÃN, 0), "TÊN")
                    ĐẶT TỌA_ĐỘ: DANH_SÁCH = TÌM_TỌA_ĐỘ_VẬT_THỂ(TÊN_NHÃN)
                    CHẠM(LẤY_PHẦN_TỬ(TỌA_ĐỘ, 0), LẤY_PHẦN_TỬ(TỌA_ĐỘ, 1))
                }
                """.trimIndent()
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(ScriptExecutionState.COMPLETED, engine.snapshot().state)
            assertEquals(listOf("tap:30,50"), dispatcher.actions)
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

        override fun tap(x: Int, y: Int): Boolean {
            actions += "tap:$x,$y"
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
                "ĐỌC_VĂN_BẢN_MÀN_HÌNH" -> "AUTU-LIO Đăng nhập"
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
                "ĐỌC_VĂN_BẢN_VÙNG" -> "OK vùng"
                "MÀN_HÌNH_CHỨA_CHỮ_TRONG_VÙNG" -> arguments.firstOrNull() == "OK"
                "TÌM_VỊ_TRÍ_CHỮ_TRONG_VÙNG" -> listOf(110, 220)
                "NHẬN_DIỆN_NHÃN_ẢNH_MÀN_HÌNH" -> listOf("Phone", "Screen")
                "MÀN_HÌNH_CHỨA_NHÃN_ẢNH" -> arguments.firstOrNull() == "Phone"
                "QUÉT_MÃ_QR_MÀN_HÌNH" -> listOf("https://example.test")
                "QUÉT_MÃ_VẠCH_MÀN_HÌNH" -> listOf("123456789")
                "PHÁT_HIỆN_VẬT_THỂ_MÀN_HÌNH" -> listOf(
                    linkedMapOf<String, Any?>(
                        "X" to 10.0,
                        "Y" to 20.0,
                        "RỘNG" to 40.0,
                        "CAO" to 60.0,
                        "NHÃN" to listOf(
                            linkedMapOf<String, Any?>(
                                "TÊN" to "Food",
                                "CHỈ_SỐ" to 1.0,
                                "ĐỘ_TIN_CẬY" to 0.91
                            )
                        )
                    )
                )
                "MÀN_HÌNH_CÓ_VẬT_THỂ" -> arguments.firstOrNull() == "Food"
                "TÌM_TỌA_ĐỘ_VẬT_THỂ" -> listOf(30, 50)
                "ĐẾM_VẬT_THỂ_MÀN_HÌNH" -> 1
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
