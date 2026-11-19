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
import kotlin.math.pow

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
    private var activeRunDeadlineNanos = 0L
    private var completedInstructions = 0L
    private var totalInstructions = 0L
    private var cancellationRequested = false
    private var closed = false
    private var lastErrorCode: String? = null
    private var lastMessage: String? = null
    private var activeFunctions: Map<String, ScriptInstruction.FunctionDefinition> = emptyMap()
    private val maxFunctionCallDepth = 16

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
            activeRunDeadlineNanos = System.nanoTime() +
                TimeUnit.MILLISECONDS.toNanos(ScriptCompiler.MAX_TOTAL_DURATION_MS)
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
            activeFunctions = program.instructions
                .filterIsInstance<ScriptInstruction.FunctionDefinition>()
                .associateBy { it.name }
            val mainInstructions = program.instructions.filterNot { it is ScriptInstruction.FunctionDefinition }
            executeBlock(runId, mainInstructions, mutableMapOf(), mutableMapOf(), 0)
            finish(runId, ScriptExecutionState.COMPLETED, null, null)
        } catch (_: StopRequested) {
            finish(runId, ScriptExecutionState.CANCELLED, null, null)
        } catch (returned: ReturnValue) {
            finish(runId, ScriptExecutionState.FAILED, "return_outside_function", "TRẢ_VỀ không thể thoát ra ngoài hàm.")
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
        } finally {
            activeFunctions = emptyMap()
        }
    }

    private fun executeBlock(
        runId: Long,
        instructions: List<ScriptInstruction>,
        variables: MutableMap<String, Any?>,
        declaredTypes: MutableMap<String, String>,
        callDepth: Int
    ) {
        for (instruction in instructions) {
            checkpoint(runId)
            when (instruction) {
                is ScriptInstruction.FunctionDefinition ->
                    throw ScriptRuntimeFailure("nested_function", "Không thể khai báo hàm bên trong khối lệnh.")
                is ScriptInstruction.Return -> {
                    val value = evaluate(instruction.value, variables, declaredTypes, runId, callDepth)
                    completeInstruction(runId)
                    throw ReturnValue(value)
                }
                is ScriptInstruction.Command ->
                    executeCommand(runId, instruction, variables, declaredTypes, callDepth)
                is ScriptInstruction.SetVariable -> {
                    val value = evaluate(instruction.value, variables, declaredTypes, runId, callDepth)
                    val previousType = declaredTypes[instruction.name]
                    val declaredType = instruction.declaredType
                    if (declaredType != null && previousType != null) {
                        throw ScriptRuntimeFailure("variable_already_typed", "Biến '" + instruction.name + "' đã được khai báo kiểu.")
                    }
                    val effectiveType = declaredType ?: previousType
                    if (effectiveType != null && !matchesDeclaredType(value, effectiveType)) {
                        throw ScriptRuntimeFailure("variable_type_mismatch", "Giá trị gán cho '" + instruction.name + "' không đúng kiểu " + effectiveType + ".")
                    }
                    if (declaredType != null) declaredTypes[instruction.name] = declaredType
                    variables[instruction.name] = value
                    completeInstruction(runId)
                }
                is ScriptInstruction.If -> {
                    val condition = requireBoolean(
                        evaluate(instruction.condition, variables, declaredTypes, runId, callDepth),
                        "Điều kiện NẾU phải trả về ĐÚNG hoặc SAI."
                    )
                    completeInstruction(runId)
                    executeBlock(
                        runId,
                        if (condition) instruction.thenBody else instruction.elseBody,
                        variables,
                        declaredTypes,
                        callDepth
                    )
                }
                is ScriptInstruction.Repeat -> {
                    val count = requireInteger(
                        evaluate(instruction.count, variables, declaredTypes, runId, callDepth),
                        "Số lần lặp phải là số nguyên."
                    )
                    if (count !in 1..ScriptCompiler.MAX_REPEAT_COUNT) {
                        throw ScriptRuntimeFailure("repeat_range", "Số lần lặp phải từ 1 đến " + ScriptCompiler.MAX_REPEAT_COUNT + ".")
                    }
                    var shouldBreak = false
                    for (iteration in 0 until count) {
                        checkpoint(runId)
                        try {
                            executeBlock(runId, instruction.body, variables, declaredTypes, callDepth)
                        } catch (_: ContinueLoop) {
                            Unit
                        } catch (_: BreakLoop) {
                            shouldBreak = true
                        }
                        if (shouldBreak) break
                    }
                }
                is ScriptInstruction.ForEach -> {
                    val value = evaluate(instruction.iterable, variables, declaredTypes, runId, callDepth)
                    val sourceList = value as? List<*>
                        ?: throw ScriptRuntimeFailure("foreach_requires_list", "LẶP_MỖI cần một giá trị kiểu DANH_SÁCH.")
                    if (sourceList.size > ScriptCompiler.MAX_LIST_ITEMS) {
                        throw ScriptRuntimeFailure("list_too_large", "Danh sách vượt quá ${ScriptCompiler.MAX_LIST_ITEMS} phần tử.")
                    }
                    val snapshot = sourceList.toList()
                    val hadPreviousValue = variables.containsKey(instruction.variableName)
                    val previousValue = variables[instruction.variableName]
                    val previousType = declaredTypes.remove(instruction.variableName)
                    try {
                        var shouldBreak = false
                        for (item in snapshot) {
                            checkpoint(runId)
                            variables[instruction.variableName] = item
                            completeInstruction(runId)
                            try {
                                executeBlock(runId, instruction.body, variables, declaredTypes, callDepth)
                            } catch (_: ContinueLoop) {
                                Unit
                            } catch (_: BreakLoop) {
                                shouldBreak = true
                            }
                            if (shouldBreak) break
                        }
                    } finally {
                        if (hadPreviousValue) variables[instruction.variableName] = previousValue
                        else variables.remove(instruction.variableName)
                        if (previousType != null) declaredTypes[instruction.variableName] = previousType
                        else declaredTypes.remove(instruction.variableName)
                    }
                }
                is ScriptInstruction.WaitUntil ->
                    executeWaitUntil(runId, instruction, variables, declaredTypes, callDepth)
                is ScriptInstruction.While -> {
                    var terminated = false
                    var iteration = 0
                    while (iteration < ScriptCompiler.MAX_WHILE_ITERATIONS) {
                        checkpoint(runId)
                        val condition = requireBoolean(
                            evaluate(instruction.condition, variables, declaredTypes, runId, callDepth),
                            "Điều kiện LẶP_KHI phải trả về ĐÚNG hoặc SAI."
                        )
                        completeInstruction(runId)
                        if (!condition) {
                            terminated = true
                            break
                        }
                        try {
                            executeBlock(runId, instruction.body, variables, declaredTypes, callDepth)
                        } catch (_: ContinueLoop) {
                            Unit
                        } catch (_: BreakLoop) {
                            terminated = true
                            break
                        }
                        iteration++
                    }
                    if (!terminated) {
                        throw ScriptRuntimeFailure(
                            "loop_limit_reached",
                            "Vòng lặp đã chạm giới hạn " + ScriptCompiler.MAX_WHILE_ITERATIONS + " lượt để tránh chạy vô hạn."
                        )
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
        variables: MutableMap<String, Any?>,
        declaredTypes: MutableMap<String, String>,
        callDepth: Int
    ) {
        val timeoutMs = requireDuration(
            evaluate(instruction.timeoutMs, variables, declaredTypes, runId, callDepth),
            1,
            ScriptCompiler.MAX_WAIT_MS
        )
        val intervalMs = requireDuration(
            evaluate(instruction.intervalMs, variables, declaredTypes, runId, callDepth),
            50,
            5_000
        )
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.toLong())

        while (true) {
            checkpoint(runId)
            val ready = requireBoolean(
                evaluate(instruction.condition, variables, declaredTypes, runId, callDepth),
                "Điều kiện CHỜ_ĐẾN_KHI phải trả về ĐÚNG hoặc SAI."
            )
            if (ready) {
                completeInstruction(runId)
                return
            }

            val remainingNanos = deadlineNanos - System.nanoTime()
            if (remainingNanos <= 0L) {
                throw ScriptRuntimeFailure("wait_until_timeout", "Đã hết thời hạn chờ điều kiện trở thành ĐÚNG.")
            }
            val remainingMs = TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1L)
            waitInterruptibly(runId, minOf(intervalMs.toLong(), remainingMs).toInt())
        }
    }

    private fun executeCommand(
        runId: Long,
        instruction: ScriptInstruction.Command,
        variables: MutableMap<String, Any?>,
        declaredTypes: MutableMap<String, String>,
        callDepth: Int
    ) {
        val args = instruction.arguments.map {
            evaluate(it, variables, declaredTypes, runId, callDepth)
        }
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
            "MỞ_ỨNG_DỤNG_GẦN_ĐÂY" -> dispatcher.showRecents()
            "MỞ_THANH_THÔNG_BÁO" -> dispatcher.showNotifications()
            "MỞ_BẢNG_CÀI_ĐẶT_NHANH" -> dispatcher.showQuickSettings()
            "MỞ_HỘP_THOẠI_NGUỒN" -> dispatcher.showPowerDialog()
            "KHÓA_MÀN_HÌNH" -> dispatcher.lockScreen()
            "BẬT_TẮT_CHIA_MÀN_HÌNH" -> dispatcher.toggleSplitScreen()
            else -> throw ScriptRuntimeFailure("unsupported_command", "Lệnh '" + instruction.name + "' chưa được hỗ trợ.")
        }
        if (!success) {
            throw ScriptRuntimeFailure("dispatch_failed", "Không thực hiện được lệnh '" + instruction.name + "'. Hãy kiểm tra quyền và trạng thái Android.")
        }
        completeInstruction(runId)
    }

    private fun evaluate(
        expression: ScriptExpression,
        variables: MutableMap<String, Any?>,
        declaredTypes: MutableMap<String, String>,
        runId: Long,
        callDepth: Int
    ): Any? = when (expression) {
        is ScriptExpression.NumberLiteral -> expression.value
        is ScriptExpression.StringLiteral -> expression.value
        is ScriptExpression.BooleanLiteral -> expression.value
        is ScriptExpression.Variable -> {
            if (!variables.containsKey(expression.name)) {
                throw ScriptRuntimeFailure("unknown_variable", "Chưa khai báo biến '" + expression.name + "'.")
            }
            variables[expression.name]
        }
        is ScriptExpression.Unary -> {
            val value = evaluate(expression.operand, variables, declaredTypes, runId, callDepth)
            when (expression.operator) {
                "KHÔNG" -> !requireBoolean(value, "Toán tử KHÔNG chỉ dùng với ĐÚNG/SAI.")
                "-" -> -requireNumber(value, "Dấu trừ chỉ dùng với số.")
                "+" -> requireNumber(value, "Dấu cộng chỉ dùng với số.")
                else -> throw ScriptRuntimeFailure("invalid_operator", "Toán tử một ngôi không hợp lệ.")
            }
        }
        is ScriptExpression.Binary ->
            evaluateBinary(expression, variables, declaredTypes, runId, callDepth)
        is ScriptExpression.FunctionCall -> {
            val args = expression.arguments.map {
                evaluate(it, variables, declaredTypes, runId, callDepth)
            }
            evaluateFunction(expression.name, args, variables, declaredTypes, runId, callDepth)
        }
    }

    private fun evaluateBinary(
        expression: ScriptExpression.Binary,
        variables: MutableMap<String, Any?>,
        declaredTypes: MutableMap<String, String>,
        runId: Long,
        callDepth: Int
    ): Any? {
        val left = evaluate(expression.left, variables, declaredTypes, runId, callDepth)
        if (expression.operator == "VÀ") {
            val value = requireBoolean(left, "Vế trái của VÀ phải là ĐÚNG/SAI.")
            return value && requireBoolean(
                evaluate(expression.right, variables, declaredTypes, runId, callDepth),
                "Vế phải của VÀ phải là ĐÚNG/SAI."
            )
        }
        if (expression.operator == "HOẶC") {
            val value = requireBoolean(left, "Vế trái của HOẶC phải là ĐÚNG/SAI.")
            return value || requireBoolean(
                evaluate(expression.right, variables, declaredTypes, runId, callDepth),
                "Vế phải của HOẶC phải là ĐÚNG/SAI."
            )
        }
        val right = evaluate(expression.right, variables, declaredTypes, runId, callDepth)
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
            "^" -> {
                val result = requireNumber(left, "Lũy thừa cần hai số.").pow(requireNumber(right, "Lũy thừa cần hai số."))
                if (!result.isFinite()) throw ScriptRuntimeFailure("numeric_overflow", "Kết quả lũy thừa vượt giới hạn số hợp lệ.")
                result
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

    private fun evaluateFunction(
        name: String,
        args: List<Any?>,
        variables: MutableMap<String, Any?>,
        declaredTypes: MutableMap<String, String>,
        runId: Long,
        callDepth: Int
    ): Any? = when (name) {
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
        "CHỮ_HOA" -> {
            requireArgs(name, args, 1)
            (args[0] as? String)?.uppercase(Locale.ROOT)
                ?: throw ScriptRuntimeFailure("invalid_argument", "CHỮ_HOA cần một chuỗi.")
        }
        "CHỮ_THƯỜNG" -> {
            requireArgs(name, args, 1)
            (args[0] as? String)?.lowercase(Locale.ROOT)
                ?: throw ScriptRuntimeFailure("invalid_argument", "CHỮ_THƯỜNG cần một chuỗi.")
        }
        "CẮT_KHOẢNG_TRẮNG" -> {
            requireArgs(name, args, 1)
            (args[0] as? String)?.trim()
                ?: throw ScriptRuntimeFailure("invalid_argument", "CẮT_KHOẢNG_TRẮNG cần một chuỗi.")
        }
        "CHỨA_CHUỖI" -> {
            requireArgs(name, args, 2)
            val source = args[0] as? String
                ?: throw ScriptRuntimeFailure("invalid_argument", "CHỨA_CHUỖI cần chuỗi ở tham số đầu.")
            val part = args[1] as? String
                ?: throw ScriptRuntimeFailure("invalid_argument", "CHỨA_CHUỖI cần chuỗi ở tham số thứ hai.")
            source.contains(part)
        }
        "BẮT_ĐẦU_BẰNG" -> {
            requireArgs(name, args, 2)
            val source = args[0] as? String
                ?: throw ScriptRuntimeFailure("invalid_argument", "BẮT_ĐẦU_BẰNG cần chuỗi ở tham số đầu.")
            val prefix = args[1] as? String
                ?: throw ScriptRuntimeFailure("invalid_argument", "BẮT_ĐẦU_BẰNG cần chuỗi ở tham số thứ hai.")
            source.startsWith(prefix)
        }
        "KẾT_THÚC_BẰNG" -> {
            requireArgs(name, args, 2)
            val source = args[0] as? String
                ?: throw ScriptRuntimeFailure("invalid_argument", "KẾT_THÚC_BẰNG cần chuỗi ở tham số đầu.")
            val suffix = args[1] as? String
                ?: throw ScriptRuntimeFailure("invalid_argument", "KẾT_THÚC_BẰNG cần chuỗi ở tham số thứ hai.")
            source.endsWith(suffix)
        }
        "THAY_THẾ" -> {
            requireArgs(name, args, 3)
            val source = args[0] as? String
                ?: throw ScriptRuntimeFailure("invalid_argument", "THAY_THẾ cần chuỗi nguồn.")
            val oldValue = args[1] as? String
                ?: throw ScriptRuntimeFailure("invalid_argument", "THAY_THẾ cần chuỗi cần thay.")
            val newValue = args[2] as? String
                ?: throw ScriptRuntimeFailure("invalid_argument", "THAY_THẾ cần chuỗi thay thế.")
            if (oldValue.isEmpty()) throw ScriptRuntimeFailure("empty_search_text", "Chuỗi cần thay không được để trống.")
            source.replace(oldValue, newValue)
        }
        "TÁCH_CHUỖI" -> {
            requireArgs(name, args, 2)
            val source = args[0] as? String
                ?: throw ScriptRuntimeFailure("invalid_argument", "TÁCH_CHUỖI cần chuỗi nguồn.")
            val delimiter = args[1] as? String
                ?: throw ScriptRuntimeFailure("invalid_argument", "TÁCH_CHUỖI cần chuỗi phân cách.")
            if (delimiter.isEmpty()) throw ScriptRuntimeFailure("empty_delimiter", "Chuỗi phân cách không được để trống.")
            val parts = source.split(delimiter)
            if (parts.size > ScriptCompiler.MAX_LIST_ITEMS) {
                throw ScriptRuntimeFailure("list_too_large", "Kết quả TÁCH_CHUỖI vượt quá ${ScriptCompiler.MAX_LIST_ITEMS} phần tử.")
            }
            parts.toMutableList()
        }
        "NỐI_CHUỖI" -> {
            requireArgs(name, args, 2)
            val values = args[0] as? List<*>
                ?: throw ScriptRuntimeFailure("invalid_argument", "NỐI_CHUỖI cần danh sách chuỗi.")
            val separator = args[1] as? String
                ?: throw ScriptRuntimeFailure("invalid_argument", "NỐI_CHUỖI cần chuỗi phân cách.")
            if (values.any { it !is String }) {
                throw ScriptRuntimeFailure("invalid_list_item", "NỐI_CHUỖI chỉ nhận danh sách gồm chuỗi.")
            }
            values.joinToString(separator) { it as String }
        }
        "KÝ_TỰ_TẠI" -> {
            requireArgs(name, args, 2)
            val source = args[0] as? String
                ?: throw ScriptRuntimeFailure("invalid_argument", "KÝ_TỰ_TẠI cần một chuỗi.")
            val codePoints = source.codePoints().toArray()
            val index = requireListIndex(args[1], codePoints.size, allowEnd = false)
            String(codePoints, index, 1)
        }
        "CẮT_CHUỖI" -> {
            requireArgs(name, args, 3)
            val source = args[0] as? String
                ?: throw ScriptRuntimeFailure("invalid_argument", "CẮT_CHUỖI cần một chuỗi.")
            val codePoints = source.codePoints().toArray()
            val start = requireInteger(args[1], "Vị trí bắt đầu phải là số nguyên.")
            val length = requireInteger(args[2], "Độ dài cần cắt phải là số nguyên.")
            if (start < 0 || length < 0 || start > codePoints.size || length > codePoints.size - start) {
                throw ScriptRuntimeFailure("string_range", "Vị trí hoặc độ dài CẮT_CHUỖI nằm ngoài chuỗi.")
            }
            String(codePoints, start, length)
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
            when (val value = args[0]) {
                is String -> value.length.toDouble()
                is List<*> -> value.size.toDouble()
                is Map<*, *> -> value.size.toDouble()
                else -> throw ScriptRuntimeFailure("invalid_argument", "ĐỘ_DÀI cần chuỗi, danh sách hoặc bản đồ.")
            }
        }
        "TẠO_DANH_SÁCH" -> {
            if (args.size > ScriptCompiler.MAX_LIST_ITEMS) {
                throw ScriptRuntimeFailure("list_too_large", "Danh sách không được vượt quá ${ScriptCompiler.MAX_LIST_ITEMS} phần tử.")
            }
            if (args.any { !isSupportedListItem(it) }) {
                throw ScriptRuntimeFailure("invalid_list_item", "Danh sách chỉ chứa số, chuỗi hoặc giá trị ĐÚNG/SAI.")
            }
            args.toMutableList()
        }
        "LẤY_PHẦN_TỬ" -> {
            requireArgs(name, args, 2)
            val list = requireMutableList(args[0], name)
            val index = requireListIndex(args[1], list.size, allowEnd = false)
            list[index]
        }
        "THÊM_PHẦN_TỬ" -> {
            requireArgs(name, args, 2)
            val list = requireMutableList(args[0], name)
            if (!isSupportedListItem(args[1])) {
                throw ScriptRuntimeFailure("invalid_list_item", "Phần tử chỉ được là số, chuỗi hoặc giá trị ĐÚNG/SAI.")
            }
            if (list.size >= ScriptCompiler.MAX_LIST_ITEMS) {
                throw ScriptRuntimeFailure("list_too_large", "Danh sách đã đạt giới hạn ${ScriptCompiler.MAX_LIST_ITEMS} phần tử.")
            }
            list.add(args[1])
            list.size.toDouble()
        }
        "ĐẶT_PHẦN_TỬ" -> {
            requireArgs(name, args, 3)
            val list = requireMutableList(args[0], name)
            val index = requireListIndex(args[1], list.size, allowEnd = false)
            if (!isSupportedListItem(args[2])) {
                throw ScriptRuntimeFailure("invalid_list_item", "Phần tử chỉ được là số, chuỗi hoặc giá trị ĐÚNG/SAI.")
            }
            list[index] = args[2]
            args[2]
        }
        "XÓA_PHẦN_TỬ" -> {
            requireArgs(name, args, 2)
            val list = requireMutableList(args[0], name)
            val index = requireListIndex(args[1], list.size, allowEnd = false)
            list.removeAt(index)
        }
        "CÓ_PHẦN_TỬ" -> {
            requireArgs(name, args, 2)
            val list = args[0] as? List<*>
                ?: throw ScriptRuntimeFailure("invalid_argument", "CÓ_PHẦN_TỬ cần danh sách ở tham số đầu.")
            list.contains(args[1])
        }
        "TẠO_BẢN_ĐỒ" -> {
            if (args.size > ScriptCompiler.MAX_MAP_ENTRIES * 2 || args.size % 2 != 0) {
                throw ScriptRuntimeFailure("map_argument_count", "TẠO_BẢN_ĐỒ cần tối đa ${ScriptCompiler.MAX_MAP_ENTRIES} cặp KHÓA, GIÁ_TRỊ.")
            }
            val map = linkedMapOf<String, Any?>()
            for (index in args.indices step 2) {
                val key = args[index] as? String
                    ?: throw ScriptRuntimeFailure("invalid_map_key", "Khóa bản đồ phải là chuỗi.")
                if (key.isBlank()) throw ScriptRuntimeFailure("invalid_map_key", "Khóa bản đồ không được để trống.")
                if (!isSupportedMapValue(args[index + 1])) {
                    throw ScriptRuntimeFailure("invalid_map_value", "Giá trị bản đồ chỉ được là số, chuỗi hoặc ĐÚNG/SAI.")
                }
                if (map.containsKey(key)) throw ScriptRuntimeFailure("duplicate_map_key", "Khóa bản đồ bị trùng: " + key + ".")
                map[key] = args[index + 1]
            }
            map
        }
        "LẤY_GIÁ_TRỊ" -> {
            requireArgs(name, args, 2)
            val map = requireMutableMap(args[0], name)
            val key = requireMapKey(args[1])
            if (!map.containsKey(key)) throw ScriptRuntimeFailure("map_key_not_found", "Không tìm thấy khóa '" + key + "'.")
            map[key]
        }
        "ĐẶT_GIÁ_TRỊ" -> {
            requireArgs(name, args, 3)
            val map = requireMutableMap(args[0], name)
            val key = requireMapKey(args[1])
            if (!isSupportedMapValue(args[2])) {
                throw ScriptRuntimeFailure("invalid_map_value", "Giá trị bản đồ chỉ được là số, chuỗi hoặc ĐÚNG/SAI.")
            }
            if (!map.containsKey(key) && map.size >= ScriptCompiler.MAX_MAP_ENTRIES) {
                throw ScriptRuntimeFailure("map_too_large", "Bản đồ đã đạt giới hạn ${ScriptCompiler.MAX_MAP_ENTRIES} cặp khóa–giá trị.")
            }
            map[key] = args[2]
            args[2]
        }
        "XÓA_KHÓA" -> {
            requireArgs(name, args, 2)
            val map = requireMutableMap(args[0], name)
            val key = requireMapKey(args[1])
            if (!map.containsKey(key)) throw ScriptRuntimeFailure("map_key_not_found", "Không tìm thấy khóa '" + key + "'.")
            map.remove(key)
        }
        "CÓ_KHÓA" -> {
            requireArgs(name, args, 2)
            val map = args[0] as? Map<*, *>
                ?: throw ScriptRuntimeFailure("invalid_argument", "CÓ_KHÓA cần bản đồ ở tham số đầu.")
            map.containsKey(requireMapKey(args[1]))
        }
        "DANH_SÁCH_KHÓA" -> {
            requireArgs(name, args, 1)
            val map = args[0] as? Map<*, *>
                ?: throw ScriptRuntimeFailure("invalid_argument", "DANH_SÁCH_KHÓA cần một bản đồ.")
            map.keys.map { it as String }.toMutableList()
        }
        "NHỎ_NHẤT" -> {
            requireArgs(name, args, 2)
            min(requireNumber(args[0], "NHỎ_NHẤT cần hai số."), requireNumber(args[1], "NHỎ_NHẤT cần hai số."))
        }
        "LỚN_NHẤT" -> {
            requireArgs(name, args, 2)
            max(requireNumber(args[0], "LỚN_NHẤT cần hai số."), requireNumber(args[1], "LỚN_NHẤT cần hai số."))
        }
        else -> {
            if (activeFunctions.containsKey(name)) {
                invokeUserFunction(name, args, variables, declaredTypes, runId, callDepth)
            } else {
                dispatcher.evaluateFunction(name, args)
            }
        }
    }

    private fun invokeUserFunction(
        name: String,
        args: List<Any?>,
        variables: MutableMap<String, Any?>,
        declaredTypes: MutableMap<String, String>,
        runId: Long,
        callDepth: Int
    ): Any? {
        val function = activeFunctions[name]
            ?: throw ScriptRuntimeFailure("unknown_function", "Không tìm thấy hàm '" + name + "'.")
        if (callDepth >= maxFunctionCallDepth) {
            throw ScriptRuntimeFailure("function_depth_exceeded", "Hàm gọi lồng nhau quá sâu; giới hạn là " + maxFunctionCallDepth + ".")
        }
        if (args.size != function.parameters.size) {
            throw ScriptRuntimeFailure("function_argument_count", "Hàm '" + name + "' cần đúng " + function.parameters.size + " tham số.")
        }

        val localVariables = variables.toMutableMap()
        val localTypes = declaredTypes.toMutableMap()
        function.parameters.forEachIndexed { index, parameter ->
            val value = args[index]
            if (parameter.declaredType != null && !matchesDeclaredType(value, parameter.declaredType)) {
                throw ScriptRuntimeFailure("function_parameter_type", "Tham số '" + parameter.name + "' của hàm '" + name + "' phải có kiểu " + parameter.declaredType + ".")
            }
            localVariables[parameter.name] = value
            if (parameter.declaredType != null) localTypes[parameter.name] = parameter.declaredType
        }

        return try {
            executeBlock(runId, function.body, localVariables, localTypes, callDepth + 1)
            if (function.returnType != null) {
                throw ScriptRuntimeFailure("missing_return", "Hàm '" + name + "' phải trả về giá trị kiểu " + function.returnType + ".")
            }
            null
        } catch (returned: ReturnValue) {
            if (function.returnType != null && !matchesDeclaredType(returned.value, function.returnType)) {
                throw ScriptRuntimeFailure("function_return_type", "Giá trị trả về của hàm '" + name + "' không đúng kiểu " + function.returnType + ".")
            }
            returned.value
        }
    }

    private fun requireArgs(name: String, args: List<Any?>, count: Int) {
        if (args.size != count) throw ScriptRuntimeFailure("argument_count", "Hàm '" + name + "' cần " + count + " tham số.")
    }

    private fun matchesDeclaredType(value: Any?, type: String): Boolean = when (type) {
        "SỐ" -> value is Number
        "CHUỖI" -> value is String
        "LUẬN_LÝ" -> value is Boolean
        "DANH_SÁCH" -> value is List<*>
        "BẢN_ĐỒ" -> value is Map<*, *>
        "BẤT_KỲ" -> true
        else -> false
    }

    private fun isSupportedListItem(value: Any?): Boolean =
        (value is Number && value.toDouble().isFinite()) || value is String || value is Boolean

    private fun isSupportedMapValue(value: Any?): Boolean = isSupportedListItem(value)

    @Suppress("UNCHECKED_CAST")
    private fun requireMutableMap(value: Any?, functionName: String): MutableMap<String, Any?> =
        value as? MutableMap<String, Any?>
            ?: throw ScriptRuntimeFailure("invalid_argument", "Hàm '$functionName' cần bản đồ được tạo bằng TẠO_BẢN_ĐỒ.")

    private fun requireMapKey(value: Any?): String =
        (value as? String)?.takeIf { it.isNotBlank() }
            ?: throw ScriptRuntimeFailure("invalid_map_key", "Khóa bản đồ phải là chuỗi không rỗng.")

    @Suppress("UNCHECKED_CAST")
    private fun requireMutableList(value: Any?, functionName: String): MutableList<Any?> =
        value as? MutableList<Any?>
            ?: throw ScriptRuntimeFailure("invalid_argument", "Hàm '$functionName' cần danh sách được tạo bằng TẠO_DANH_SÁCH.")

    private fun requireListIndex(value: Any?, size: Int, allowEnd: Boolean): Int {
        val index = requireInteger(value, "Chỉ số danh sách phải là số nguyên.")
        val upper = if (allowEnd) size else size - 1
        if (index < 0 || index > upper) {
            throw ScriptRuntimeFailure("list_index_out_of_range", "Chỉ số danh sách nằm ngoài phạm vi.")
        }
        return index
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
            if (activeRunDeadlineNanos > 0L && System.nanoTime() >= activeRunDeadlineNanos) {
                throw ScriptRuntimeFailure(
                    "runtime_duration_limit",
                    "Kịch bản đã vượt giới hạn thời gian chạy thực tế 10 phút."
                )
            }
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
            if (completedInstructions >= ScriptCompiler.MAX_EXPANDED_INSTRUCTIONS) {
                throw ScriptRuntimeFailure("instruction_budget_exceeded", "Kịch bản đã chạm giới hạn thực tế 5.000 lượt thực thi lệnh.")
            }
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
            activeRunDeadlineNanos = 0L
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
        if (program.instructions.none { it !is ScriptInstruction.FunctionDefinition }) {
            return "empty_program" to "Chương trình không có lệnh chạy."
        }
        val functionDefinitions = program.instructions.filterIsInstance<ScriptInstruction.FunctionDefinition>()
        if (functionDefinitions.map { it.name }.distinct().size != functionDefinitions.size) {
            return "duplicate_function" to "Tên hàm bị trùng."
        }
        if (!validateBlock(program.instructions, 0, 0, false)) return "invalid_program" to "Kịch bản chứa cấu trúc hoặc lệnh không hợp lệ."
        val metrics = ScriptCompiler.measureProgram(program.instructions)
        if (metrics.expandedInstructionCount > ScriptCompiler.MAX_EXPANDED_INSTRUCTIONS) return "instruction_budget_exceeded" to "Kịch bản vượt giới hạn số lệnh."
        if (metrics.estimatedDurationMs > ScriptCompiler.MAX_TOTAL_DURATION_MS) return "duration_budget_exceeded" to "Kịch bản vượt giới hạn thời lượng."
        if (program.expandedInstructionCount != metrics.expandedInstructionCount || program.estimatedDurationMs != metrics.estimatedDurationMs) {
            return "program_metadata_mismatch" to "Thông tin ngân sách không khớp với mã kịch bản."
        }
        return null
    }

    private fun validateBlock(
        instructions: List<ScriptInstruction>,
        depth: Int,
        loopDepth: Int,
        inFunction: Boolean
    ): Boolean {
        if (depth > ScriptCompiler.MAX_NESTING_DEPTH || instructions.isEmpty()) return false
        return instructions.all { instruction ->
            when (instruction) {
                is ScriptInstruction.FunctionDefinition ->
                    depth == 0 &&
                        instruction.name.isNotBlank() &&
                        instruction.name == instruction.name.uppercase(Locale.ROOT) &&
                        instruction.parameters.size <= 16 &&
                        instruction.parameters.map { it.name }.distinct().size == instruction.parameters.size &&
                        instruction.parameters.all { parameter ->
                            parameter.name.isNotBlank() &&
                                parameter.name == parameter.name.uppercase(Locale.ROOT) &&
                                (parameter.declaredType == null || parameter.declaredType in setOf("SỐ", "CHUỖI", "LUẬN_LÝ", "DANH_SÁCH", "BẢN_ĐỒ", "BẤT_KỲ"))
                        } &&
                        (instruction.returnType == null || instruction.returnType in setOf("SỐ", "CHUỖI", "LUẬN_LÝ", "DANH_SÁCH", "BẢN_ĐỒ", "BẤT_KỲ")) &&
                        validateBlock(instruction.body, depth + 1, 0, true)
                is ScriptInstruction.Return ->
                    inFunction && validateExpression(instruction.value, 0)
                is ScriptInstruction.Command -> {
                    val arity = when (instruction.name) {
                        "CHẠM" -> 2
                        "NHẤN_GIỮ" -> 3
                        "VUỐT" -> 5
                        "CHỜ", "MỞ_ỨNG_DỤNG" -> 1
                        "QUAY_LẠI", "VỀ_MÀN_HÌNH_CHÍNH",
                        "MỞ_ỨNG_DỤNG_GẦN_ĐÂY", "MỞ_THANH_THÔNG_BÁO", "MỞ_BẢNG_CÀI_ĐẶT_NHANH",
                        "MỞ_HỘP_THOẠI_NGUỒN", "KHÓA_MÀN_HÌNH", "BẬT_TẮT_CHIA_MÀN_HÌNH" -> 0
                        else -> return@all false
                    }
                    instruction.arguments.size == arity && instruction.arguments.all { validateExpression(it, 0) }
                }
                is ScriptInstruction.SetVariable ->
                    instruction.name.isNotBlank() &&
                        instruction.name == instruction.name.uppercase(Locale.ROOT) &&
                        instruction.name.first().let { it.isLetter() || it == '_' } &&
                        instruction.name.all { it.isLetterOrDigit() || it == '_' } &&
                        (instruction.declaredType == null || instruction.declaredType in setOf("SỐ", "CHUỖI", "LUẬN_LÝ", "DANH_SÁCH", "BẢN_ĐỒ", "BẤT_KỲ")) &&
                        validateExpression(instruction.value, 0)
                is ScriptInstruction.If ->
                    validateExpression(instruction.condition, 0) &&
                        validateBlock(instruction.thenBody, depth + 1, loopDepth, inFunction) &&
                        (instruction.elseBody.isEmpty() || validateBlock(instruction.elseBody, depth + 1, loopDepth, inFunction))
                is ScriptInstruction.Repeat ->
                    loopDepth < ScriptCompiler.MAX_NESTING_DEPTH && validateExpression(instruction.count, 0) &&
                        validateBlock(instruction.body, depth + 1, loopDepth + 1, inFunction)
                is ScriptInstruction.While ->
                    loopDepth < ScriptCompiler.MAX_NESTING_DEPTH && validateExpression(instruction.condition, 0) &&
                        validateBlock(instruction.body, depth + 1, loopDepth + 1, inFunction)
                is ScriptInstruction.WaitUntil ->
                    validateExpression(instruction.condition, 0) &&
                        validateExpression(instruction.timeoutMs, 0) &&
                        validateExpression(instruction.intervalMs, 0)
                is ScriptInstruction.ForEach ->
                    loopDepth < ScriptCompiler.MAX_NESTING_DEPTH &&
                        instruction.variableName.isNotBlank() &&
                        instruction.variableName == instruction.variableName.uppercase(Locale.ROOT) &&
                        instruction.variableName.all { it.isLetterOrDigit() || it == '_' } &&
                        validateExpression(instruction.iterable, 0) &&
                        validateBlock(instruction.body, depth + 1, loopDepth + 1, inFunction)
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
            is ScriptExpression.Binary -> expression.operator in setOf("VÀ", "HOẶC", "==", "!=", ">", ">=", "<", "<=", "+", "-", "*", "/", "%", "^") &&
                validateExpression(expression.left, depth + 1) && validateExpression(expression.right, depth + 1)
            is ScriptExpression.FunctionCall -> expression.name.isNotBlank() && expression.arguments.size <= ScriptCompiler.MAX_MAP_ENTRIES * 2 &&
                expression.arguments.all { validateExpression(it, depth + 1) }
        }
    }

    private class StopRequested : RuntimeException()
    private class BreakLoop : RuntimeException()
    private class ContinueLoop : RuntimeException()
    private class ReturnValue(val value: Any?) : RuntimeException()
    private class ScriptRuntimeFailure(val code: String, message: String) : RuntimeException(message)
}
