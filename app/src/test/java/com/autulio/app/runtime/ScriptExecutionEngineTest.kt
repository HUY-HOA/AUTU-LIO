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
                ĐẶT i = 0
                LẶP(3) {
                    CHẠM(i, 20)
                    i = i + 1
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
                ĐẶT pin = 12
                NẾU (pin > 10 VÀ KHÔNG SAI) {
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
                ĐẶT i = 0
                LẶP_KHI (i < 10) {
                    i = i + 1
                    NẾU (i == 3) {
                        THOÁT_LẶP
                    }
                    CHẠM(i, 20)
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
                ĐẶT i = 0
                LẶP(3) {
                    i = i + 1
                    NẾU (i == 2) {
                        BỎ_QUA_LƯỢT
                    }
                    CHẠM(i, 30)
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
                """
            )
            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("back", "home"), dispatcher.actions)
            assertEquals(2L, engine.snapshot().completedInstructions)
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
                name == "SẴN_SÀNG" && calls.incrementAndGet() >= 3
        }
        val engine = ScriptExecutionEngine(dispatcher) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile("CHỜ_ĐẾN_KHI(SẴN_SÀNG(), 2000, 50)\nCHẠM(5, 6)")
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
            assertEquals(ScriptExecutionStartResult.Started, engine.start(compile("CHỜ_ĐẾN_KHI(SẴN_SÀNG(), 150, 50)")))
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
                ĐẶT dem = 0
                LẶP_ĐẾN_KHI(dem >= 3) {
                    dem = dem + 1
                    CHẠM(dem, 30)
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

        override fun evaluateFunction(name: String, arguments: List<Any?>): Any? {
            functions += "function:$name"
            return name == "CÓ_MẠNG"
        }
    }
}
