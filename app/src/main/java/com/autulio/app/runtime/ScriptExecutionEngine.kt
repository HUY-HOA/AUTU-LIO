package com.autulio.app.runtime

import com.autulio.app.script.ScriptCompiler
import com.autulio.app.script.ScriptExpression
import com.autulio.app.script.ScriptInstruction
import com.autulio.app.script.ScriptProgram
import java.io.Closeable
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

interface ScriptActionDispatcher {
    fun tap(x: Int, y: Int): Boolean
    fun longPress(x: Int, y: Int, durationMs: Int): Boolean
    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int): Boolean
    fun openApp(packageName: String): Boolean = false
    fun goBack(): Boolean = false
    fun goHome(): Boolean = false
    fun showRecents(): Boolean = false
    fun showNotifications(): Boolean = false
    fun showQuickSettings(): Boolean = false
    fun showPowerDialog(): Boolean = false
    fun lockScreen(): Boolean = false
    fun toggleSplitScreen(): Boolean = false
    fun evaluateFunction(name: String, arguments: List<Any?>): Any? =
        throw UnsupportedOperationException("Hàm '$name' chưa được hỗ trợ trên thiết bị này.")
}

enum class ScriptExecutionState { IDLE, RUNNING, PAUSED, STOPPING, COMPLETED, CANCELLED, FAILED }

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
        if (validationError != null) return ScriptExecutionStartResult.Rejected(validationError.first, validationError.second)

        val runId: Long
        val initial: ScriptExecutionSnapshot
        lock.withLock {
            if (closed) return ScriptExecutionStartResult.Rejected("engine_closed", "Bộ thực thi đã đóng.")
            if (state == ScriptExecutionState.RUNNING || state == ScriptExecutionState.PAUSED || state == ScriptExecutionState.STOPPING) {
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
            if (activeRunId != runId) null else try {
                worker.submit { execute(runId, program) }
                null
            } catch (_: RejectedExecutionException) {
                "Không thể khởi động luồng thực thi."
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
            if (state != ScriptExecutionState.RUNNING && state != ScriptExecutionState.PAUSED) return false
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
            executeBlock(runId, program.instructions, mutableMapOf())
            finish(runId, ScriptExecutionState.COMPLETED, null, null)
        } catch (_: StopRequested) {
            finish(runId, ScriptExecutionState.CANCELLED, null, null)
        } catch (error: ScriptRuntimeFailure) {
            val cancelled = lock.withLock { cancellationRequested || state == ScriptExecutionState.STOPPING || closed }
            if (cancelled) finish(runId, ScriptExecutionState.CANCELLED, null, null)
            else finish(runId, ScriptExecutionState.FAILED, error.code, error.message)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            val cancelled = lock.withLock { cancellationRequested || state == ScriptExecutionState.STOPPING || closed }
            if (cancelled) finish(runId, ScriptExecutionState.CANCELLED, null, null)
            else finish(runId, ScriptExecutionState.FAILED, "interrupted", "Kịch bản bị gián đoạn.")
        } catch (error: Exception) {
            finish(runId, ScriptExecutionState.FAILED, "runtime_error", error.message ?: "Lỗi thực thi không xác định.")
        }
    }

    private fun executeBlock(
        runId: Long,
        instructions: List<ScriptInstruction>,
        variables: MutableMap<String, Any?>
    ) {
        for (instruction in instructions) {
            checkpoint(runId)
            when (instruction) {
                is ScriptInstruction.Command -> executeCommand(runId, instruction, variables)
                is ScriptInstruction.SetVariable -> {
                    variables[instruction.name] = evaluate(instruction.value, variables)
                    completeInstruction(runId)
                }
                is ScriptInstruction.If -> {
                    val condition = requireBoolean(evaluate(instruction.condition, variables), "Điều kiện NẾU phải trả về ĐÚNG hoặc SAI.")
                    completeInstruction(runId)
                    executeBlock(runId, if (condition) instruction.thenBody else instruction.elseBody, variables)
                }
                is ScriptInstruction.Repeat -> {
                    val count = requireInteger(evaluate(instruction.count, variables), "Số lần lặp phải là số nguyên.")
                    if (count !in 1..ScriptCompiler.MAX_REPEAT_COUNT) {
                        throw ScriptRuntimeFailure("repeat_range", "Số lần lặp phải từ 1 đến ${ScriptCompiler.MAX_REPEAT_COUNT}.")
                    }
                    var shouldBreak = false
                    for (iteration in 0 until count) {
                        checkpoint(runId)
                        try {
                            executeBlock(runId, instruction.body, variables)
                        } catch (_: ContinueLoop) {
                            Unit
                        } catch (_: BreakLoop) {
                            shouldBreak = true
                        }
                        if (shouldBreak) break
                    }
                }
                is ScriptInstruction.WaitUntil -> executeWaitUntil(runId, instruction, variables)
                is ScriptInstruction.While -> {
                    var terminated = false
                    var iteration = 0
                    while (iteration < ScriptCompiler.MAX_WHILE_ITERATIONS) {
                        checkpoint(runId)
                        val condition = requireBoolean(evaluate(instruction.condition, variables), "Điều kiện LẶP_KHI phải trả về ĐÚNG hoặc SAI.")
                        completeInstruction(runId)
                        if (!condition) {
                            terminated = true
                            break
                        }
                        try {
                            executeBlock(runId, instruction.body, variables)
                        } catch (_: ContinueLoop) {
                            Unit
                        } catch (_: BreakLoop) {
                            terminated = true
                            break
                        }
                        iteration++
                    }
                    if (!terminated) {
                        throw ScriptRuntimeFailure("loop_limit_reached", "Vòng lặp đã chạm giới hạn ${ScriptCompiler.MAX_WHILE_ITERATIONS} lượt để tránh chạy vô hạn.")
                    }
                }
                ScriptInstruction.BreakLoop -> {
                    completeInstruction(runId)
                    throw BreakLoop()
                }
                ScriptInstruction.ContinueLoop -> {
                    completeInstruction(runId)
                    throw ContinueLoop()
                }
            }
        }
    }

    private fun executeWaitUntil(
        runId: Long,
        instruction: ScriptInstruction.WaitUntil,
        variables: MutableMap<String, Any?>
    ) {
        val timeoutMs = requireDuration(
            evaluate(instruction.timeoutMs, variables),
            1,
            ScriptCompiler.MAX_WAIT_MS
        )
        val intervalMs = requireDuration(evaluate(instruction.intervalMs, variables), 50, 5_000)
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.toLong())

        while (true) {
            checkpoint(runId)
            val ready = requireBoolean(
                evaluate(instruction.condition, variables),
                "Điều kiện CHỜ_ĐẾN_KHI phải trả về ĐÚNG hoặc SAI."
            )
            if (ready) {
                completeInstruction(runId)
                return
            }

            val remainingNanos = deadlineNanos - System.nanoTime()
            if (remainingNanos <= 0L) {
                throw ScriptRuntimeFailure(
                    "wait_until_timeout",
                    "Đã hết thời hạn chờ điều kiện trở thành ĐÚNG."
                )
            }
            val remainingMs = TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1L)
            waitInterruptibly(runId, minOf(intervalMs.toLong(), remainingMs).toInt())
        }
    }

    private fun executeCommand(
        runId: Long,
        instruction: ScriptInstruction.Command,
        variables: MutableMap<String, Any?>
    ) {
        val args = instruction.arguments.map { evaluate(it, variables) }
        val success = when (instruction.name) {
            "CHẠM" -> dispatcher.tap(requireCoordinate(args[0]), requireCoordinate(args[1]))
            "NHẤN_GIỮ" -> dispatcher.longPress(
                requireCoordinate(args[0]),
                requireCoordinate(args[1]),
                requireDuration(args[2], 100, ScriptCompiler.MAX_GESTURE_MS)
            )
            "VUỐT" -> dispatcher.swipe(
                requireCoordinate(args[0]),
                requireCoordinate(args[1]),
                requireCoordinate(args[2]),
                requireCoordinate(args[3]),
                requireDuration(args[4], 1, ScriptCompiler.MAX_GESTURE_MS)
            )
            "CHỜ" -> {
                waitInterruptibly(runId, requireDuration(args[0], 1, ScriptCompiler.MAX_WAIT_MS))
                true
            }
            "MỞ_ỨNG_DỤNG" -> {
                val packageName = args[0] as? String
                    ?: throw ScriptRuntimeFailure("invalid_argument", "MỞ_ỨNG_DỤNG cần tên gói ứng dụng dạng chuỗi.")
                if (!packageName.matches(Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$"))) {
                    throw ScriptRuntimeFailure("invalid_package_name", "Tên gói ứng dụng không hợp lệ.")
                }
                dispatcher.openApp(packageName)
            }
            "QUAY_LẠI" -> dispatcher.goBack()
            "VỀ_MÀN_HÌNH_CHÍNH" -> dispatcher.goHome()
            "ỨNG_DỤNG_GẦN_ĐÂY" -> dispatcher.showRecents()
            "MỞ_THÔNG_BÁO" -> dispatcher.showNotifications()
            "MỞ_CÀI_ĐẶT_NHANH" -> dispatcher.showQuickSettings()
            "HIỆN_MENU_NGUỒN" -> dispatcher.showPowerDialog()
            "KHÓA_MÀN_HÌNH" -> dispatcher.lockScreen()
            "CHIA_MÀN_HÌNH" -> dispatcher.toggleSplitScreen()
            else -> throw ScriptRuntimeFailure("unsupported_command", "Lệnh '${instruction.name}' chưa được hỗ trợ.")
        }
        if (!success) throw ScriptRuntimeFailure("dispatch_failed", "Không thực hiện được lệnh '${instruction.name}'. Hãy kiểm tra quyền và trạng thái Android.")
        completeInstruction(runId)
    }

    private fun evaluate(expression: ScriptExpression, variables: MutableMap<String, Any?>): Any? = when (expression) {
        is ScriptExpression.NumberLiteral -> expression.value
        is ScriptExpression.StringLiteral -> expression.value
        is ScriptExpression.BooleanLiteral -> expression.value
        is ScriptExpression.Variable -> {
            if (!variables.containsKey(expression.name)) throw ScriptRuntimeFailure("unknown_variable", "Chưa khai báo biến '${expression.name}'.")
            variables[expression.name]
        }
        is ScriptExpression.Unary -> {
            val value = evaluate(expression.operand, variables)
            when (expression.operator) {
                "KHÔNG" -> !requireBoolean(value, "Toán tử KHÔNG chỉ dùng với ĐÚNG/SAI.")
                "-" -> -requireNumber(value, "Dấu trừ chỉ dùng với số.")
                "+" -> requireNumber(value, "Dấu cộng chỉ dùng với số.")
                else -> throw ScriptRuntimeFailure("invalid_operator", "Toán tử một ngôi không hợp lệ.")
            }
        }
        is ScriptExpression.Binary -> evaluateBinary(expression, variables)
        is ScriptExpression.FunctionCall -> {
            val args = expression.arguments.map { evaluate(it, variables) }
            evaluateFunction(expression.name, args)
        }
    }

    private fun evaluateBinary(
        expression: ScriptExpression.Binary,
        variables: MutableMap<String, Any?>
    ): Any? {
        val left = evaluate(expression.left, variables)
        if (expression.operator == "VÀ") {
            val value = requireBoolean(left, "Vế trái của VÀ phải là ĐÚNG/SAI.")
            return value && requireBoolean(evaluate(expression.right, variables), "Vế phải của VÀ phải là ĐÚNG/SAI.")
        }
        if (expression.operator == "HOẶC") {
            val value = requireBoolean(left, "Vế trái của HOẶC phải là ĐÚNG/SAI.")
            return value || requireBoolean(evaluate(expression.right, variables), "Vế phải của HOẶC phải là ĐÚNG/SAI.")
        }
        val right = evaluate(expression.right, variables)
        return when (expression.operator) {
            "+" -> if (left is String || right is String) left.toString() + right.toString()
                else requireNumber(left, "Phép cộng cần số hoặc chuỗi.") + requireNumber(right, "Phép cộng cần số hoặc chuỗi.")
            "-" -> requireNumber(left, "Phép trừ cần số.") - requireNumber(right, "Phép trừ cần số.")
            "*" -> requireNumber(left, "Phép nhân cần số.") * requireNumber(right, "Phép nhân cần số.")
            "/" -> {
                val divisor = requireNumber(right, "Phép chia cần số.")
                if (divisor == 0.0) throw ScriptRuntimeFailure("division_by_zero", "Không thể chia cho 0.")
                requireNumber(left, "Phép chia cần số.") / divisor
            }
            "%" -> {
                val divisor = requireNumber(right, "Phép chia dư cần số.")
                if (divisor == 0.0) throw ScriptRuntimeFailure("division_by_zero", "Không thể chia dư cho 0.")
                requireNumber(left, "Phép chia dư cần số.") % divisor
            }
            "==" -> valuesEqual(left, right)
            "!=" -> !valuesEqual(left, right)
            ">" -> compareValues(left, right) > 0
            ">=" -> compareValues(left, right) >= 0
            "<" -> compareValues(left, right) < 0
            "<=" -> compareValues(left, right) <= 0
            else -> throw ScriptRuntimeFailure("invalid_operator", "Toán tử không được hỗ trợ.")
        }
    }

    private fun evaluateFunction(name: String, args: List<Any?>): Any? = when (name.uppercase(Locale.ROOT)) {
        "SỐ" -> {
            requireArgs(name, args, 1)
            when (val value = args[0]) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull() ?: throw ScriptRuntimeFailure("invalid_number", "Không đổi được chuỗi thành số.")
                else -> throw ScriptRuntimeFailure("invalid_argument", "Hàm SỐ cần số hoặc chuỗi số.")
            }
        }
        "CHUỖI" -> {
            requireArgs(name, args, 1)
            args[0]?.toString() ?: ""
        }
        "LÀM_TRÒN" -> {
            requireArgs(name, args, 1)
            round(requireNumber(args[0], "LÀM_TRÒN cần một số."))
        }
        "TUYỆT_ĐỐI" -> {
            requireArgs(name, args, 1)
            abs(requireNumber(args[0], "TUYỆT_ĐỐI cần một số."))
        }
        "ĐỘ_DÀI" -> {
            requireArgs(name, args, 1)
            (args[0] as? String)?.length?.toDouble()
                ?: throw ScriptRuntimeFailure("invalid_argument", "ĐỘ_DÀI cần một chuỗi.")
        }
        "NHỎ_NHẤT" -> {
            requireArgs(name, args, 2)
            min(requireNumber(args[0], "NHỎ_NHẤT cần hai số."), requireNumber(args[1], "NHỎ_NHẤT cần hai số."))
        }
        "LỚN_NHẤT" -> {
            requireArgs(name, args, 2)
            max(requireNumber(args[0], "LỚN_NHẤT cần hai số."), requireNumber(args[1], "LỚN_NHẤT cần hai số."))
        }
        else -> dispatcher.evaluateFunction(name.uppercase(Locale.ROOT), args)
    }

    private fun requireArgs(name: String, args: List<Any?>, count: Int) {
        if (args.size != count) throw ScriptRuntimeFailure("argument_count", "Hàm '$name' cần $count tham số.")
    }

    private fun valuesEqual(left: Any?, right: Any?): Boolean =
        if (left is Number && right is Number) left.toDouble() == right.toDouble() else left == right

    private fun compareValues(left: Any?, right: Any?): Int = when {
        left is Number && right is Number -> left.toDouble().compareTo(right.toDouble())
        left is String && right is String -> left.compareTo(right)
        else -> throw ScriptRuntimeFailure("type_mismatch", "Chỉ so sánh được hai số hoặc hai chuỗi cùng kiểu.")
    }

    private fun requireBoolean(value: Any?, message: String): Boolean =
        value as? Boolean ?: throw ScriptRuntimeFailure("type_mismatch", message)

    private fun requireNumber(value: Any?, message: String): Double =
        (value as? Number)?.toDouble()?.takeIf { it.isFinite() }
            ?: throw ScriptRuntimeFailure("type_mismatch", message)

    private fun requireInteger(value: Any?, message: String): Int {
        val number = requireNumber(value, message)
        if (number % 1.0 != 0.0 || number < Int.MIN_VALUE || number > Int.MAX_VALUE) {
            throw ScriptRuntimeFailure("invalid_integer", message)
        }
        return number.toInt()
    }

    private fun requireCoordinate(value: Any?): Int {
        val number = requireInteger(value, "Tọa độ phải là số nguyên.")
        if (number !in 0..ScriptCompiler.MAX_COORDINATE) {
            throw ScriptRuntimeFailure("coordinate_range", "Tọa độ phải từ 0 đến ${ScriptCompiler.MAX_COORDINATE}.")
        }
        return number
    }

    private fun requireDuration(value: Any?, min: Int, max: Int): Int {
        val number = requireInteger(value, "Thời lượng phải là số nguyên mili giây.")
        if (number !in min..max) throw ScriptRuntimeFailure("duration_range", "Thời lượng phải từ $min đến $max ms.")
        return number
    }

    private fun checkpoint(runId: Long) {
        lock.withLock {
            while (state == ScriptExecutionState.PAUSED && !cancellationRequested) changed.await()
            ensureActiveLocked(runId)
        }
    }

    private fun waitInterruptibly(runId: Long, durationMs: Int) {
        var remainingNanos = TimeUnit.MILLISECONDS.toNanos(durationMs.toLong())
        while (remainingNanos > 0L) {
            lock.withLock {
                while (state == ScriptExecutionState.PAUSED && !cancellationRequested) changed.await()
                ensureActiveLocked(runId)
                remainingNanos = changed.awaitNanos(remainingNanos)
            }
        }
        checkpoint(runId)
    }

    private fun ensureActiveLocked(runId: Long) {
        if (runId != activeRunId || cancellationRequested || state == ScriptExecutionState.STOPPING || closed) throw StopRequested()
        if (state != ScriptExecutionState.RUNNING) throw IllegalStateException("Trạng thái thực thi không hợp lệ.")
    }

    private fun completeInstruction(runId: Long) {
        val update = lock.withLock {
            while (state == ScriptExecutionState.PAUSED && !cancellationRequested) changed.await()
            ensureActiveLocked(runId)
            completedInstructions++
            snapshotLocked()
        }
        publish(update)
    }

    private fun finish(runId: Long, finalState: ScriptExecutionState, errorCode: String?, message: String?) {
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
            return "unsupported_language_version" to "Phiên bản ngôn ngữ AUTU không được hỗ trợ."
        }
        if (program.instructions.isEmpty()) return "empty_program" to "Kịch bản không có lệnh."
        if (!validateBlock(program.instructions, 0, 0)) return "invalid_program" to "Kịch bản chứa cấu trúc hoặc lệnh không hợp lệ."
        val metrics = ScriptCompiler.measureProgram(program.instructions)
        if (metrics.expandedInstructionCount > ScriptCompiler.MAX_EXPANDED_INSTRUCTIONS) return "instruction_budget_exceeded" to "Kịch bản vượt giới hạn số lệnh."
        if (metrics.estimatedDurationMs > ScriptCompiler.MAX_TOTAL_DURATION_MS) return "duration_budget_exceeded" to "Kịch bản vượt giới hạn thời lượng."
        if (program.expandedInstructionCount != metrics.expandedInstructionCount || program.estimatedDurationMs != metrics.estimatedDurationMs) {
            return "program_metadata_mismatch" to "Thông tin ngân sách không khớp với mã kịch bản."
        }
        return null
    }

    private fun validateBlock(instructions: List<ScriptInstruction>, depth: Int, loopDepth: Int): Boolean {
        if (depth > ScriptCompiler.MAX_NESTING_DEPTH || instructions.isEmpty()) return false
        return instructions.all { instruction ->
            when (instruction) {
                is ScriptInstruction.Command -> {
                    val arity = when (instruction.name) {
                        "CHẠM" -> 2
                        "NHẤN_GIỮ" -> 3
                        "VUỐT" -> 5
                        "CHỜ", "MỞ_ỨNG_DỤNG" -> 1
                        "QUAY_LẠI", "VỀ_MÀN_HÌNH_CHÍNH",
                        "ỨNG_DỤNG_GẦN_ĐÂY", "MỞ_THÔNG_BÁO", "MỞ_CÀI_ĐẶT_NHANH",
                        "HIỆN_MENU_NGUỒN", "KHÓA_MÀN_HÌNH", "CHIA_MÀN_HÌNH" -> 0
                        else -> return@all false
                    }
                    instruction.arguments.size == arity && instruction.arguments.all { validateExpression(it, 0) }
                }
                is ScriptInstruction.SetVariable ->
                    instruction.name.isNotBlank() && instruction.name.first().let { it.isLetter() || it == '_' } &&
                        instruction.name.all { it.isLetterOrDigit() || it == '_' } && validateExpression(instruction.value, 0)
                is ScriptInstruction.If ->
                    validateExpression(instruction.condition, 0) &&
                        validateBlock(instruction.thenBody, depth + 1, loopDepth) &&
                        (instruction.elseBody.isEmpty() || validateBlock(instruction.elseBody, depth + 1, loopDepth))
                is ScriptInstruction.Repeat ->
                    loopDepth < ScriptCompiler.MAX_NESTING_DEPTH && validateExpression(instruction.count, 0) &&
                        validateBlock(instruction.body, depth + 1, loopDepth + 1)
                is ScriptInstruction.While ->
                    loopDepth < ScriptCompiler.MAX_NESTING_DEPTH && validateExpression(instruction.condition, 0) &&
                        validateBlock(instruction.body, depth + 1, loopDepth + 1)
                is ScriptInstruction.WaitUntil ->
                    validateExpression(instruction.condition, 0) &&
                        validateExpression(instruction.timeoutMs, 0) &&
                        validateExpression(instruction.intervalMs, 0)
                ScriptInstruction.BreakLoop, ScriptInstruction.ContinueLoop -> loopDepth > 0
            }
        }
    }

    private fun validateExpression(expression: ScriptExpression, depth: Int): Boolean {
        if (depth > 32) return false
        return when (expression) {
            is ScriptExpression.NumberLiteral -> expression.value.isFinite()
            is ScriptExpression.StringLiteral -> expression.value.length <= 4_096
            is ScriptExpression.BooleanLiteral -> true
            is ScriptExpression.Variable -> expression.name.isNotBlank()
            is ScriptExpression.Unary -> expression.operator in setOf("KHÔNG", "+", "-") && validateExpression(expression.operand, depth + 1)
            is ScriptExpression.Binary -> expression.operator in setOf("VÀ", "HOẶC", "==", "!=", ">", ">=", "<", "<=", "+", "-", "*", "/", "%") &&
                validateExpression(expression.left, depth + 1) && validateExpression(expression.right, depth + 1)
            is ScriptExpression.FunctionCall -> expression.name.isNotBlank() && expression.arguments.size <= 16 &&
                expression.arguments.all { validateExpression(it, depth + 1) }
        }
    }

    private class StopRequested : RuntimeException()
    private class BreakLoop : RuntimeException()
    private class ContinueLoop : RuntimeException()
    private class ScriptRuntimeFailure(val code: String, message: String) : RuntimeException(message)
}
