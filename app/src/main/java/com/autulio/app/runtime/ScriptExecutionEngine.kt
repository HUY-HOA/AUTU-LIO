package com.autulio.app.runtime

import com.autulio.app.script.ScriptCompiler
import com.autulio.app.script.ScriptInstruction
import com.autulio.app.script.ScriptProgram
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface ScriptActionDispatcher {
    fun tap(x: Int, y: Int): Boolean
    fun longPress(x: Int, y: Int, durationMs: Int): Boolean
    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int): Boolean
}

enum class ScriptExecutionState {
    IDLE,
    RUNNING,
    PAUSED,
    STOPPING,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class ScriptExecutionSnapshot(
    val state: ScriptExecutionState,
    val completedInstructions: Long,
    val totalInstructions: Long,
    val errorCode: String? = null,
    val message: String? = null
)

sealed interface ScriptExecutionStartResult {
    data object Started : ScriptExecutionStartResult
    data object AlreadyRunning : ScriptExecutionStartResult
    data class Rejected(val code: String, val message: String) : ScriptExecutionStartResult
}

class ScriptExecutionEngine(
    private val dispatcher: ScriptActionDispatcher,
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "AUTU-LIO-ScriptRuntime").apply { isDaemon = true }
    },
    private val observer: (ScriptExecutionSnapshot) -> Unit = {}
) : Closeable {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()

    private var state = ScriptExecutionState.IDLE
    private var activeRunId = 0L
    private var completedInstructions = 0L
    private var totalInstructions = 0L
    private var cancellationRequested = false
    private var closed = false
    private var lastErrorCode: String? = null
    private var lastMessage: String? = null

    fun snapshot(): ScriptExecutionSnapshot = lock.withLock { snapshotLocked() }

    fun start(program: ScriptProgram): ScriptExecutionStartResult {
        val validationError = validate(program)
        if (validationError != null) {
            return ScriptExecutionStartResult.Rejected(validationError.first, validationError.second)
        }

        val runId: Long
        val initial: ScriptExecutionSnapshot
        lock.withLock {
            if (closed) {
                return ScriptExecutionStartResult.Rejected("engine_closed", "The script engine is closed.")
            }
            if (state == ScriptExecutionState.RUNNING ||
                state == ScriptExecutionState.PAUSED ||
                state == ScriptExecutionState.STOPPING
            ) {
                return ScriptExecutionStartResult.AlreadyRunning
            }

            activeRunId++
            runId = activeRunId
            cancellationRequested = false
            lastErrorCode = null
            lastMessage = null
            completedInstructions = 0L
            totalInstructions = program.expandedInstructionCount
            state = ScriptExecutionState.RUNNING
            initial = snapshotLocked()
        }

        publish(initial)

        val submitFailure = lock.withLock {
            if (activeRunId != runId) {
                null
            } else {
                try {
                    worker.submit { execute(runId, program) }
                    null
                } catch (_: RejectedExecutionException) {
                    "The script worker is not available."
                }
            }
        }

        if (submitFailure != null) {
            finish(runId, ScriptExecutionState.FAILED, "worker_unavailable", submitFailure)
            return ScriptExecutionStartResult.Rejected("worker_unavailable", submitFailure)
        }
        return ScriptExecutionStartResult.Started
    }

    fun pause(): Boolean {
        val update = lock.withLock {
            if (state != ScriptExecutionState.RUNNING) return false
            state = ScriptExecutionState.PAUSED
            changed.signalAll()
            snapshotLocked()
        }
        publish(update)
        return true
    }

    fun resume(): Boolean {
        val update = lock.withLock {
            if (state != ScriptExecutionState.PAUSED) return false
            state = ScriptExecutionState.RUNNING
            changed.signalAll()
            snapshotLocked()
        }
        publish(update)
        return true
    }

    fun stop(): Boolean {
        val update = lock.withLock {
            if (state != ScriptExecutionState.RUNNING && state != ScriptExecutionState.PAUSED) {
                return false
            }
            cancellationRequested = true
            state = ScriptExecutionState.STOPPING
            changed.signalAll()
            snapshotLocked()
        }
        publish(update)
        return true
    }

    override fun close() {
        val update = lock.withLock {
            if (closed) return
            closed = true
            if (state == ScriptExecutionState.RUNNING || state == ScriptExecutionState.PAUSED) {
                cancellationRequested = true
                state = ScriptExecutionState.STOPPING
                changed.signalAll()
            }
            snapshotLocked()
        }
        publish(update)
        worker.shutdownNow()
    }

    private fun execute(runId: Long, program: ScriptProgram) {
        try {
            executeBlock(runId, program.instructions)
            finish(runId, ScriptExecutionState.COMPLETED, null, null)
        } catch (_: StopRequested) {
            finish(runId, ScriptExecutionState.CANCELLED, null, null)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            val wasCancelled = lock.withLock {
                cancellationRequested || state == ScriptExecutionState.STOPPING || closed
            }
            if (wasCancelled) {
                finish(runId, ScriptExecutionState.CANCELLED, null, null)
            } else {
                finish(runId, ScriptExecutionState.FAILED, "interrupted", "Script execution was interrupted.")
            }
        } catch (error: DispatchFailure) {
            val wasCancelled = lock.withLock {
                cancellationRequested || state == ScriptExecutionState.STOPPING || closed
            }
            if (wasCancelled) {
                finish(runId, ScriptExecutionState.CANCELLED, null, null)
            } else {
                finish(runId, ScriptExecutionState.FAILED, "dispatch_failed", error.message)
            }
        } catch (error: Exception) {
            finish(
                runId,
                ScriptExecutionState.FAILED,
                "runtime_error",
                error.message ?: "An unexpected runtime error occurred."
            )
        }
    }

    private fun executeBlock(runId: Long, instructions: List<ScriptInstruction>) {
        for (instruction in instructions) {
            checkpoint(runId)
            when (instruction) {
                is ScriptInstruction.Tap -> {
                    if (!dispatcher.tap(instruction.x, instruction.y)) {
                        throw DispatchFailure("The tap action could not be dispatched.")
                    }
                    completeInstruction(runId)
                }
                is ScriptInstruction.LongPress -> {
                    if (!dispatcher.longPress(instruction.x, instruction.y, instruction.durationMs)) {
                        throw DispatchFailure("The long-press action could not be dispatched.")
                    }
                    completeInstruction(runId)
                }
                is ScriptInstruction.Swipe -> {
                    if (!dispatcher.swipe(
                            instruction.startX,
                            instruction.startY,
                            instruction.endX,
                            instruction.endY,
                            instruction.durationMs
                        )
                    ) {
                        throw DispatchFailure("The swipe action could not be dispatched.")
                    }
                    completeInstruction(runId)
                }
                is ScriptInstruction.Wait -> {
                    waitInterruptibly(runId, instruction.durationMs)
                    completeInstruction(runId)
                }
                is ScriptInstruction.Repeat -> {
                    repeat(instruction.count) {
                        checkpoint(runId)
                        executeBlock(runId, instruction.body)
                    }
                }
            }
        }
    }

    private fun checkpoint(runId: Long) {
        lock.withLock {
            while (state == ScriptExecutionState.PAUSED && !cancellationRequested) {
                changed.await()
            }
            ensureActiveLocked(runId)
        }
    }

    private fun waitInterruptibly(runId: Long, durationMs: Int) {
        var remainingNanos = TimeUnit.MILLISECONDS.toNanos(durationMs.toLong())
        while (remainingNanos > 0L) {
            lock.withLock {
                while (state == ScriptExecutionState.PAUSED && !cancellationRequested) {
                    changed.await()
                }
                ensureActiveLocked(runId)
                remainingNanos = changed.awaitNanos(remainingNanos)
            }
        }
        checkpoint(runId)
    }

    private fun ensureActiveLocked(runId: Long) {
        if (runId != activeRunId ||
            cancellationRequested ||
            state == ScriptExecutionState.STOPPING ||
            closed
        ) {
            throw StopRequested()
        }
        if (state != ScriptExecutionState.RUNNING) {
            throw IllegalStateException("Invalid script execution state.")
        }
    }

    private fun completeInstruction(runId: Long) {
        val update = lock.withLock {
            while (state == ScriptExecutionState.PAUSED && !cancellationRequested) {
                changed.await()
            }
            ensureActiveLocked(runId)
            completedInstructions++
            snapshotLocked()
        }
        publish(update)
    }

    private fun finish(
        runId: Long,
        finalState: ScriptExecutionState,
        errorCode: String?,
        message: String?
    ) {
        val update = lock.withLock {
            if (runId != activeRunId) return
            state = finalState
            cancellationRequested = false
            lastErrorCode = errorCode
            lastMessage = message
            changed.signalAll()
            snapshotLocked()
        }
        publish(update)
    }

    private fun snapshotLocked() = ScriptExecutionSnapshot(
        state = state,
        completedInstructions = completedInstructions,
        totalInstructions = totalInstructions,
        errorCode = lastErrorCode,
        message = lastMessage
    )

    private fun publish(snapshot: ScriptExecutionSnapshot) {
        runCatching { observer(snapshot) }
    }

    private fun validate(program: ScriptProgram): Pair<String, String>? {
        if (program.languageVersion != ScriptCompiler.LANGUAGE_VERSION) {
            return "unsupported_language_version" to "The script language version is not supported."
        }
        if (program.instructions.isEmpty()) {
            return "empty_program" to "The script has no instructions."
        }

        val metrics = inspect(program.instructions, depth = 0)
            ?: return "invalid_program" to "The script contains an invalid instruction."
        if (metrics.statements > ScriptCompiler.MAX_STATEMENTS) {
            return "too_many_statements" to "The script exceeds the source statement limit."
        }
        if (metrics.instructions > ScriptCompiler.MAX_EXPANDED_INSTRUCTIONS) {
            return "instruction_budget_exceeded" to "The expanded instruction limit was exceeded."
        }
        if (metrics.durationMs > ScriptCompiler.MAX_TOTAL_DURATION_MS) {
            return "duration_budget_exceeded" to "The total execution duration limit was exceeded."
        }
        if (program.expandedInstructionCount != metrics.instructions ||
            program.estimatedDurationMs != metrics.durationMs
        ) {
            return "program_metadata_mismatch" to "The compiled program metadata does not match its instructions."
        }
        return null
    }

    private fun inspect(instructions: List<ScriptInstruction>, depth: Int): Metrics? {
        var count = 0L
        var duration = 0L
        var statements = 0L

        for (instruction in instructions) {
            statements = cappedAdd(statements, 1L, ScriptCompiler.MAX_STATEMENTS + 1L)
            val item = when (instruction) {
                is ScriptInstruction.Tap -> {
                    if (!validCoordinate(instruction.x) || !validCoordinate(instruction.y)) return null
                    Metrics(1L, 0L, 0L)
                }
                is ScriptInstruction.LongPress -> {
                    if (!validCoordinate(instruction.x) ||
                        !validCoordinate(instruction.y) ||
                        instruction.durationMs !in 100..10_000
                    ) return null
                    Metrics(1L, instruction.durationMs.toLong(), 0L)
                }
                is ScriptInstruction.Swipe -> {
                    if (!validCoordinate(instruction.startX) ||
                        !validCoordinate(instruction.startY) ||
                        !validCoordinate(instruction.endX) ||
                        !validCoordinate(instruction.endY) ||
                        instruction.durationMs !in 1..10_000
                    ) return null
                    Metrics(1L, instruction.durationMs.toLong(), 0L)
                }
                is ScriptInstruction.Wait -> {
                    if (instruction.durationMs !in 1..60_000) return null
                    Metrics(1L, instruction.durationMs.toLong(), 0L)
                }
                is ScriptInstruction.Repeat -> {
                    if (depth >= ScriptCompiler.MAX_NESTING_DEPTH ||
                        instruction.count !in 1..ScriptCompiler.MAX_REPEAT_COUNT ||
                        instruction.body.isEmpty()
                    ) return null
                    val nested = inspect(instruction.body, depth + 1) ?: return null
                    statements = cappedAdd(
                        statements,
                        nested.statements,
                        ScriptCompiler.MAX_STATEMENTS + 1L
                    )
                    Metrics(
                        cappedMultiply(nested.instructions, instruction.count.toLong(), ScriptCompiler.MAX_EXPANDED_INSTRUCTIONS + 1L),
                        cappedMultiply(nested.durationMs, instruction.count.toLong(), ScriptCompiler.MAX_TOTAL_DURATION_MS + 1L),
                        nested.statements
                    )
                }
            }
            count = cappedAdd(count, item.instructions, ScriptCompiler.MAX_EXPANDED_INSTRUCTIONS + 1L)
            duration = cappedAdd(duration, item.durationMs, ScriptCompiler.MAX_TOTAL_DURATION_MS + 1L)
        }
        return Metrics(count, duration, statements)
    }

    private fun validCoordinate(value: Int) = value in 0..10_000

    private fun cappedAdd(left: Long, right: Long, cap: Long): Long =
        if (left >= cap || right >= cap || left > cap - right) cap else left + right

    private fun cappedMultiply(value: Long, multiplier: Long, cap: Long): Long =
        if (value == 0L || multiplier == 0L) 0L
        else if (value >= cap || value > cap / multiplier) cap
        else value * multiplier

    private data class Metrics(
        val instructions: Long,
        val durationMs: Long,
        val statements: Long
    )

    private class StopRequested : RuntimeException()
    private class DispatchFailure(message: String) : RuntimeException(message)
}
