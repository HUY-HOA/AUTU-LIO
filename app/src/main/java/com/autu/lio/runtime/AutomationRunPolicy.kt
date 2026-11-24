package com.autu.lio.runtime

/**
 * Bounded retry policy for automation runs.
 *
 * Retries are opt-in because a failed script may already have performed
 * external side effects. The default policy is never to retry automatically.
 */
object AutomationRunPolicy {
    const val MAX_QUEUED_RUNS = 10
    const val MAX_AUTOMATIC_RETRIES = 2
    const val MAX_SCHEDULE_DELAY_MINUTES = 24 * 60
    const val MIN_RETRY_DELAY_MS = 1_000L
    const val MAX_RETRY_DELAY_MS = 60_000L

    private val packageNamePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun isValidTargetPackage(packageName: String?): Boolean =
        !packageName.isNullOrBlank() &&
            packageName.length <= 255 &&
            packageNamePattern.matches(packageName)

    fun matchesTargetAppForeground(targetPackage: String?, foregroundPackage: String?): Boolean =
        isValidTargetPackage(targetPackage) && foregroundPackage == targetPackage

    fun scheduledStartAt(nowEpochMs: Long, delayMinutes: Long): Long =
        if (delayMinutes <= 0L) 0L else nowEpochMs +
            delayMinutes.coerceAtMost(MAX_SCHEDULE_DELAY_MINUTES.toLong()) * 60_000L

    fun isScheduledStartDue(startAtEpochMs: Long, nowEpochMs: Long): Boolean =
        startAtEpochMs <= 0L || startAtEpochMs <= nowEpochMs

    fun shouldRetry(retryOnFailure: Boolean, attempts: Int, maxRetries: Int): Boolean =
        retryOnFailure &&
            maxRetries in 1..MAX_AUTOMATIC_RETRIES &&
            attempts in 0 until maxRetries

    fun retryDelayMs(baseDelayMs: Long, nextAttempt: Int): Long {
        val base = baseDelayMs.coerceIn(MIN_RETRY_DELAY_MS, 30_000L)
        val exponent = (nextAttempt - 1).coerceIn(0, 6)
        val multiplier = 1L shl exponent
        return (base * multiplier).coerceAtMost(MAX_RETRY_DELAY_MS)
    }
}
