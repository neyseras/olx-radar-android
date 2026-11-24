package ua.magerko.olxradar.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Лёгкий журнал приложения для режима разработчика.
 * Держит кольцевой буфер в памяти (последние MAX_ENTRIES записей)
 * и дублирует события в стандартный Logcat.
 */
object AppLog {

    const val MAX_ENTRIES = 400

    private val timeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    }

    data class Entry(
        val timeMs: Long,
        val level: String,   // D, I, W, E
        val tag: String,
        val message: String
    ) {
        fun formatted(): String =
            "${timeFormat.get()?.format(Date(timeMs)) ?: ""} $level/$tag: $message"
    }

    private val entries = ArrayDeque<Entry>()
    private val _log = MutableStateFlow<List<Entry>>(emptyList())
    val log: StateFlow<List<Entry>> = _log.asStateFlow()

    fun d(tag: String, message: String) = append("D", tag, message, null)
    fun i(tag: String, message: String) = append("I", tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = append("W", tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = append("E", tag, message, error)

    private fun append(level: String, tag: String, message: String, error: Throwable?) {
        val msg = if (error != null) "$message: ${error.message ?: error.javaClass.simpleName}" else message
        val entry = Entry(System.currentTimeMillis(), level, tag, msg.take(500))
        synchronized(entries) {
            entries.addLast(entry)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
            _log.value = entries.toList()
        }
        when (level) {
            "W" -> Log.w(tag, msg, error)
            "E" -> Log.e(tag, msg, error)
            "I" -> Log.i(tag, msg, error)
            else -> Log.d(tag, msg, error)
        }
    }

    fun clear() {
        synchronized(entries) {
            entries.clear()
            _log.value = emptyList()
        }
    }

    fun asText(): String = synchronized(entries) {
        entries.joinToString("\n") { it.formatted() }
    }
}
