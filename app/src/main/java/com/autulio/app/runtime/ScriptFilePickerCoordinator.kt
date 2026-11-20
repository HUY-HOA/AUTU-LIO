package com.autulio.app.runtime

import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

sealed interface ScriptFilePickerResult {
    data class Imported(val relativePath: String, val bytes: Int, val displayName: String) : ScriptFilePickerResult
    data class Exported(val bytes: Int) : ScriptFilePickerResult
    data object Cancelled : ScriptFilePickerResult
    data class Failed(val message: String) : ScriptFilePickerResult
}

/** One-shot in-process handoff between the runtime worker and the SAF bridge Activity. */
object ScriptFilePickerCoordinator {
    private const val POLL_INTERVAL_MS = 250L
    private val pending = ConcurrentHashMap<String, CompletableFuture<ScriptFilePickerResult>>()

    fun register(): String {
        val requestId = UUID.randomUUID().toString()
        check(pending.putIfAbsent(requestId, CompletableFuture()) == null) { "Không tạo được yêu cầu chọn tệp." }
        return requestId
    }

    fun complete(requestId: String, result: ScriptFilePickerResult): Boolean =
        pending[requestId]?.complete(result) ?: false

    @Throws(InterruptedException::class, TimeoutException::class)
    fun await(requestId: String, timeoutMs: Long, shouldCancel: () -> Boolean): ScriptFilePickerResult {
        require(timeoutMs in 1..120_000L) { "Thời hạn chọn tệp phải từ 1 đến 120.000 ms." }
        val future = pending[requestId] ?: throw IllegalStateException("Yêu cầu chọn tệp không còn tồn tại.")
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (true) {
            if (shouldCancel()) throw InterruptedException("Kịch bản đã dừng trong khi chờ chọn tệp.")
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0L) throw TimeoutException("Đã hết thời gian chờ chọn tệp.")
            try {
                return future.get(minOf(TimeUnit.MILLISECONDS.toNanos(POLL_INTERVAL_MS), remaining), TimeUnit.NANOSECONDS)
            } catch (_: TimeoutException) {
                // Poll again so stop, session revocation and the deadline are observed.
            }
        }
    }

    fun remove(requestId: String) {
        pending.remove(requestId)
    }
}
