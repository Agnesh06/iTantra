package com.itantra.app.platform

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.regex.Pattern

enum class LogCategory {
    APP, AI, NETWORK, PERF, SECURITY
}

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val category: LogCategory,
    val level: String,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
)

/**
 * Section 24: Logging and Diagnostics wrapper.
 * Enforces peer IP redaction, maintains 500-entry ring buffers per category,
 * and decouples domain code from android.util.Log.
 */
object AppLogger {

    private const val MAX_LOG_RETENTION = 500
    private var isIpRedactionEnabled = true

    private val categoryLogs = ConcurrentHashMap<LogCategory, ConcurrentLinkedDeque<LogEntry>>().apply {
        LogCategory.values().forEach { put(it, ConcurrentLinkedDeque()) }
    }

    private val IP_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")

    fun setDiagnosticsDebugMode(enabled: Boolean) {
        isIpRedactionEnabled = !enabled
    }

    private fun sanitize(message: String): String {
        return if (isIpRedactionEnabled) {
            IP_PATTERN.matcher(message).replaceAll("XXX.XXX.XXX.XXX")
        } else {
            message
        }
    }

    private fun record(category: LogCategory, level: String, tag: String, message: String, throwable: Throwable? = null) {
        val sanitized = sanitize(message)
        val entry = LogEntry(category = category, level = level, tag = tag, message = sanitized, throwable = throwable)
        val deque = categoryLogs[category] ?: return
        deque.addLast(entry)
        while (deque.size > MAX_LOG_RETENTION) {
            deque.pollFirst()
        }
    }

    fun i(tag: String, message: String, category: LogCategory = LogCategory.APP) {
        val sanitized = sanitize(message)
        Log.i(tag, sanitized)
        record(category, "INFO", tag, sanitized)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null, category: LogCategory = LogCategory.APP) {
        val sanitized = sanitize(message)
        Log.w(tag, sanitized, throwable)
        record(category, "WARN", tag, sanitized, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null, category: LogCategory = LogCategory.APP) {
        val sanitized = sanitize(message)
        Log.e(tag, sanitized, throwable)
        record(category, "ERROR", tag, sanitized, throwable)
    }

    fun perf(stage: String, metrics: Map<String, Any>) {
        val msg = "Stage: $stage | " + metrics.entries.joinToString(", ") { "${it.key}=${it.value}" }
        Log.d("PERF", msg)
        record(LogCategory.PERF, "PERF", stage, msg)
    }

    fun getLogs(category: LogCategory): List<LogEntry> {
        return categoryLogs[category]?.toList() ?: emptyList()
    }

    fun getAllLogs(): List<LogEntry> {
        return categoryLogs.values.flatMap { it.toList() }.sortedBy { it.timestamp }
    }

    fun clear() {
        categoryLogs.values.forEach { it.clear() }
    }
}
