package com.autulio.app.runtime

import com.autulio.app.script.ScriptCompiler
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
    fun executesCommandsAndNestedRepeatsInOrder() {
        val actions = RecordingDispatcher()
        val completed = CountDownLatch(1)
        val engine = ScriptExecutionEngine(actions) {
            if (it.state == ScriptExecutionState.COMPLETED) completed.countDown()
        }
        try {
            val program = compile(
                """
                tap(1, 2)
                repeat(2) {
                    longPress(3, 4, 100)
                    swipe(5, 6, 7, 8, 20)
                }
                wait(1)
                """
            )

            assertEquals(ScriptExecutionStartResult.Started, engine.start(program))
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertEquals(
                listOf("tap:1,2", "longPress:3,4,100", "swipe:5,6,7,8,20",
                    "longPress:3,4,100", "swipe:5,6,7,8,20"),
                actions.actions
            )
            assertEquals(7L, engine.snapshot().completedInstructions)
            assertEquals(ScriptExecutionState.COMPLETED, engine.snapshot().state)
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
                engine.start(compile("tap(1, 1)\nwait(250)\ntap(2, 2)"))
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
        val actions = RecordingDispatcher()
        val cancelled = CountDownLatch(1)
        val engine = ScriptExecutionEngine(actions) {
            if (it.state == ScriptExecutionState.CANCELLED) cancelled.countDown()
        }
        try {
            assertEquals(
                ScriptExecutionStartResult.Started,
                engine.start(compile("wait(60000)"))
            )
            assertTrue(engine.stop())
            assertTrue(cancelled.await(2, TimeUnit.SECONDS))
            assertEquals(ScriptExecutionState.CANCELLED, engine.snapshot().state)
        } finally {
            engine.close()
        }
    }

    @Test
    fun rejectsProgramWithTamperedInstructionMetadata() {
        val engine = ScriptExecutionEngine(RecordingDispatcher())
        try {
            val forged = ScriptProgram(
                languageVersion = ScriptCompiler.LANGUAGE_VERSION,
                instructions = listOf(ScriptInstruction.Tap(1, 2)),
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
                    100,
                    listOf(ScriptInstruction.Repeat(100, listOf(ScriptInstruction.Tap(1, 2))))
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
        val engine = ScriptExecutionEngine(object : ScriptActionDispatcher {
            override fun tap(x: Int, y: Int) = false
            override fun longPress(x: Int, y: Int, durationMs: Int) = false
            override fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int) = false
        }) {
            if (it.state == ScriptExecutionState.FAILED) failure.countDown()
        }
        try {
            assertEquals(ScriptExecutionStartResult.Started, engine.start(compile("tap(1, 2)")))
            assertTrue(failure.await(2, TimeUnit.SECONDS))
            assertEquals("dispatch_failed", engine.snapshot().errorCode)
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

        override fun tap(x: Int, y: Int): Boolean {
            actions += "tap:$x,$y"
            return true
        }

        override fun longPress(x: Int, y: Int, durationMs: Int): Boolean {
            actions += "longPress:$x,$y,$durationMs"
            return true
        }

        override fun swipe(
            startX: Int,
            startY: Int,
            endX: Int,
            endY: Int,
            durationMs: Int
        ): Boolean {
            actions += "swipe:$startX,$startY,$endX,$endY,$durationMs"
            return true
        }
    }
}
