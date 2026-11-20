package ua.magerko.olxradar.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AiThoughtLog(
    val timeMs: Long = System.currentTimeMillis(),
    val listingId: Long,
    val title: String,
    val price: String,
    val radarTitle: String,
    val status: String,
    val confidence: Int?,
    val reasons: String,
    val rawJson: String,
    val error: String? = null
) {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    fun timeFormatted(): String = timeFormat.format(Date(timeMs))
}

object AiThoughtLogger {
    private const val MAX_LOGS = 120
    private val logs = ArrayDeque<AiThoughtLog>()
    private val _logsFlow = MutableStateFlow<List<AiThoughtLog>>(emptyList())
    val logsFlow: StateFlow<List<AiThoughtLog>> = _logsFlow.asStateFlow()

    fun log(entry: AiThoughtLog) {
        synchronized(logs) {
            logs.addFirst(entry)
            while (logs.size > MAX_LOGS) logs.removeLast()
            _logsFlow.value = logs.toList()
        }
    }

    fun clear() {
        synchronized(logs) {
            logs.clear()
            _logsFlow.value = emptyList()
        }
    }

    fun asFormattedText(): String = synchronized(logs) {
        if (logs.isEmpty()) return "Логи мышления ИИ пусты."
        logs.joinToString("\n\n" + "=".repeat(45) + "\n\n") { log ->
            buildString {
                appendLine("[${log.timeFormatted()}] СТАТУС: ${log.status} (Уверенность: ${log.confidence ?: 0}%)")
                appendLine("Радар: ${log.radarTitle}")
                appendLine("Лот: ${log.title} (${log.price}) [ID: ${log.listingId}]")
                if (!log.error.isNullOrBlank()) {
                    appendLine("ОШИБКА: ${log.error}")
                }
                appendLine("РАССУЖДЕНИЯ ИИ:")
                appendLine(log.reasons.ifBlank { "Нет рассуждений" })
                if (log.rawJson.isNotBlank()) {
                    appendLine("RAW JSON:")
                    appendLine(log.rawJson)
                }
            }
        }
    }
}
