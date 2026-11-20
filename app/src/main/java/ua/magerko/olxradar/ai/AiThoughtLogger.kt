package ua.magerko.olxradar.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
    val error: String? = null,
    val thinking: String? = null
) {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    fun timeFormatted(): String = timeFormat.format(Date(timeMs))

    fun toJson(): JSONObject = JSONObject().apply {
        put("timeMs", timeMs)
        put("listingId", listingId)
        put("title", title)
        put("price", price)
        put("radarTitle", radarTitle)
        put("status", status)
        if (confidence != null) put("confidence", confidence)
        put("reasons", reasons)
        put("rawJson", rawJson)
        if (!error.isNullOrBlank()) put("error", error)
        if (!thinking.isNullOrBlank()) put("thinking", thinking)
    }

    companion object {
        fun fromJson(obj: JSONObject): AiThoughtLog {
            return AiThoughtLog(
                timeMs = obj.optLong("timeMs", System.currentTimeMillis()),
                listingId = obj.optLong("listingId", 0L),
                title = obj.optString("title", ""),
                price = obj.optString("price", ""),
                radarTitle = obj.optString("radarTitle", ""),
                status = obj.optString("status", ""),
                confidence = if (obj.has("confidence")) obj.optInt("confidence") else null,
                reasons = obj.optString("reasons", ""),
                rawJson = obj.optString("rawJson", ""),
                error = obj.optString("error").takeIf { it.isNotBlank() },
                thinking = obj.optString("thinking").takeIf { it.isNotBlank() }
            )
        }
    }
}

object AiThoughtLogger {
    private const val TAG = "AiThoughtLogger"
    private const val MAX_LOGS = 120
    private const val FILE_NAME = "ai_thought_logs.json"

    private val logs = ArrayDeque<AiThoughtLog>()
    private val _logsFlow = MutableStateFlow<List<AiThoughtLog>>(emptyList())
    val logsFlow: StateFlow<List<AiThoughtLog>> = _logsFlow.asStateFlow()

    private var storageDir: File? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        storageDir = context.filesDir
        loadFromDisk()
    }

    private fun loadFromDisk() {
        val dir = storageDir ?: return
        try {
            val file = File(dir, FILE_NAME)
            if (!file.exists()) return
            val content = file.readText()
            if (content.isNotBlank()) {
                val array = JSONArray(content)
                val loaded = mutableListOf<AiThoughtLog>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    loaded.add(AiThoughtLog.fromJson(obj))
                }
                synchronized(logs) {
                    logs.clear()
                    logs.addAll(loaded.take(MAX_LOGS))
                    _logsFlow.value = logs.toList()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load thought logs from disk: ${e.message}")
        }
    }

    private fun saveToDiskAsync() {
        val dir = storageDir ?: return
        val snapshot = synchronized(logs) { logs.toList() }
        scope.launch {
            try {
                val array = JSONArray()
                snapshot.forEach { array.put(it.toJson()) }
                val file = File(dir, FILE_NAME)
                file.writeText(array.toString())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save thought logs to disk: ${e.message}")
            }
        }
    }

    fun log(entry: AiThoughtLog) {
        synchronized(logs) {
            logs.addFirst(entry)
            while (logs.size > MAX_LOGS) logs.removeLast()
            _logsFlow.value = logs.toList()
        }
        saveToDiskAsync()
    }

    fun clear() {
        synchronized(logs) {
            logs.clear()
            _logsFlow.value = emptyList()
        }
        val dir = storageDir
        if (dir != null) {
            scope.launch {
                try {
                    File(dir, FILE_NAME).delete()
                } catch (_: Exception) {}
            }
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
                if (!log.thinking.isNullOrBlank()) {
                    appendLine("МЫШЛЕНИЕ МОДЕЛИ (<think>):")
                    appendLine(log.thinking)
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
