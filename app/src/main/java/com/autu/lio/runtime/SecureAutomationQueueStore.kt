package com.autu.lio.runtime

import android.content.Context
import com.autu.lio.SecureScriptDraftStore
import org.json.JSONArray
import org.json.JSONObject

data class AutomationQueueEntry(
    val name: String,
    val source: String,
    val retryOnFailure: Boolean = false,
    val maxRetries: Int = 0,
    val retryDelayMs: Long = 2_000L,
    val attempts: Int = 0,
    val startAtEpochMs: Long = 0L,
    val targetPackage: String? = null,
    val startOnTargetAppForeground: Boolean = false
)

data class AutomationQueueSnapshot(
    val prepared: AutomationQueueEntry? = null,
    val inProgress: AutomationQueueEntry? = null,
    val queued: List<AutomationQueueEntry> = emptyList()
)

/**
 * Stores only scripts explicitly prepared by a signed-in user. Source text is
 * encrypted with AES-GCM using an Android Keystore key via SecureScriptDraftStore.
 */
class SecureAutomationQueueStore(context: Context) {
    private val encryptedStore = SecureScriptDraftStore(context, STORAGE_NAMESPACE)

    fun load(): AutomationQueueSnapshot {
        val encoded = runCatching {
            encryptedStore.load().contents[STORAGE_KEY]
        }.getOrNull() ?: return AutomationQueueSnapshot()

        return runCatching {
            val root = JSONObject(encoded)
            val prepared = parseEntry(root.optJSONObject("prepared"))
            val queuedJson = root.optJSONArray("queued") ?: JSONArray()
            val queued = buildList {
                for (index in 0 until queuedJson.length()) {
                    if (size >= AutomationRunPolicy.MAX_QUEUED_RUNS) break
                    val entry = parseEntry(queuedJson.optJSONObject(index)) ?: continue
                    add(entry)
                }
            }
            AutomationQueueSnapshot(
                prepared = prepared,
                inProgress = parseEntry(root.optJSONObject("inProgress")),
                queued = queued
            )
        }.getOrElse { AutomationQueueSnapshot() }
    }

    fun save(snapshot: AutomationQueueSnapshot) {
        require(snapshot.queued.size <= AutomationRunPolicy.MAX_QUEUED_RUNS)
        val root = JSONObject().apply {
            put("version", FORMAT_VERSION)
            put("prepared", snapshot.prepared?.let(::encodeEntry) ?: JSONObject.NULL)
            put("inProgress", snapshot.inProgress?.let(::encodeEntry) ?: JSONObject.NULL)
            put("queued", JSONArray().apply {
                snapshot.queued.forEach { put(encodeEntry(it)) }
            })
        }
        encryptedStore.saveSynchronously(
            names = listOf(STORAGE_KEY),
            contents = mapOf(STORAGE_KEY to root.toString())
        )
    }

    fun clear() {
        encryptedStore.saveSynchronously(emptyList(), emptyMap())
    }

    private fun encodeEntry(entry: AutomationQueueEntry): JSONObject =
        JSONObject().apply {
            put("name", entry.name)
            put("source", entry.source)
            put("retryOnFailure", entry.retryOnFailure)
            put("maxRetries", entry.maxRetries)
            put("retryDelayMs", entry.retryDelayMs)
            put("attempts", entry.attempts)
            put("startAtEpochMs", entry.startAtEpochMs)
            put("targetPackage", entry.targetPackage)
            put("startOnTargetAppForeground", entry.startOnTargetAppForeground)
        }

    private fun parseEntry(json: JSONObject?): AutomationQueueEntry? {
        if (json == null) return null
        val name = json.optString("name", "").trim()
        val source = json.optString("source", "")
        if (name.isBlank() || name.length > 96 ||
            source.isBlank() || source.length > MAX_SOURCE_LENGTH
        ) return null

        val requestedRetries = json.optInt("maxRetries", 0)
        val maxRetries = requestedRetries.coerceIn(0, AutomationRunPolicy.MAX_AUTOMATIC_RETRIES)
        val requestedDelay = json.optLong("retryDelayMs", 2_000L)
        val delay = requestedDelay.coerceIn(
            AutomationRunPolicy.MIN_RETRY_DELAY_MS,
            30_000L
        )
        val attempts = json.optInt("attempts", 0).coerceIn(0, maxRetries)
        val retry = json.optBoolean("retryOnFailure", false) && maxRetries > 0
        val targetPackage = json.optString("targetPackage", "")
            .takeIf { AutomationRunPolicy.isValidTargetPackage(it) }
        val startOnTargetAppForeground =
            json.optBoolean("startOnTargetAppForeground", false) && targetPackage != null

        return AutomationQueueEntry(
            name = name,
            source = source,
            retryOnFailure = retry,
            maxRetries = maxRetries,
            retryDelayMs = delay,
            attempts = attempts,
            startAtEpochMs = json.optLong("startAtEpochMs", 0L).coerceAtLeast(0L),
            targetPackage = targetPackage.takeIf { startOnTargetAppForeground },
            startOnTargetAppForeground = startOnTargetAppForeground
        )
    }

    private companion object {
        const val STORAGE_NAMESPACE = "automation_queue"
        const val STORAGE_KEY = "__AUTU_AUTOMATION_QUEUE_V1__"
        const val FORMAT_VERSION = 3
        const val MAX_SOURCE_LENGTH = 32_768
    }
}
