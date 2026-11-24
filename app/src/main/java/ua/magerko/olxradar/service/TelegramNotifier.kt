package ua.magerko.olxradar.service

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.data.prefs.AppPreferences
import ua.magerko.olxradar.util.AppLog
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

/**
 * Пересылает выгодные находки в личный Telegram-чат или канал через Bot API.
 */
object TelegramNotifier {

    private const val TAG = "TelegramNotifier"

    fun sendFindingAsync(
        context: Context,
        finding: Finding,
        watchTitle: String,
        scope: CoroutineScope
    ) {
        AppPreferences.init(context)
        val snap = AppPreferences.state.value
        if (!snap.telegramEnabled) return
        val token = snap.telegramBotToken.trim()
        val chatId = snap.telegramChatId.trim()
        if (token.isBlank() || chatId.isBlank()) return

        val score = finding.dealScore ?: 0
        if (snap.telegramMinDealScore > 0 && score < snap.telegramMinDealScore && finding.reason != "price_drop") {
            return
        }

        scope.launch(Dispatchers.IO) {
            val html = buildFindingHtml(finding, watchTitle)
            postMessage(token, chatId, html)
                .onFailure { AppLog.w(TAG, "Ошибка отправки в Telegram: ${it.message}") }
        }
    }

    suspend fun sendTestMessage(botToken: String, chatId: String): Result<String> = withContext(Dispatchers.IO) {
        val token = botToken.trim()
        val chat = chatId.trim()
        if (token.isBlank() || chat.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Укажите Bot Token и Chat ID"))
        }
        val text = "📡 <b>OLX Radar подключён!</b>\nНовые выгодные объявления будут приходить в этот чат."
        postMessage(token, chat, text)
    }

    private fun buildFindingHtml(finding: Finding, watchTitle: String): String {
        val formatter = NumberFormat.getInstance(Locale("uk", "UA"))
        val priceText = if (finding.price != null && finding.price > 0) {
            "${formatter.format(finding.price)} ${finding.currency ?: "UAH"}"
        } else {
            "Договорная"
        }
        val header = when (finding.reason) {
            "price_drop" -> "📉 <b>СНИЖЕНИЕ ЦЕНЫ</b>"
            "pushup" -> "🔼 <b>ПОДНЯТОЕ ОБЪЯВЛЕНИЕ</b>"
            else -> "🔥 <b>НОВАЯ НАХОДКА</b>"
        }
        val oldPriceLine = finding.oldPrice?.let {
            "\nБыла цена: <s>${formatter.format(it)} ${finding.currency ?: "UAH"}</s>"
        } ?: ""
        val dealLine = finding.dealScore?.takeIf { it > 0 }?.let { "\nВыгода: <b>$it/100</b>" } ?: ""
        val sellerLine = finding.sellerName?.takeIf { it.isNotBlank() }?.let { "\nПродавец: ${escapeHtml(it)}" } ?: ""
        val deliveryLine = if (finding.isDelivery) "\n📦 OLX Доставка" else ""
        val aiLine = finding.aiScore?.let { "\n🤖 ИИ: $it% ${escapeHtml(finding.aiProduct ?: "")}".trimEnd() } ?: ""

        return buildString {
            append(header)
            if (watchTitle.isNotBlank()) {
                append(" · <i>${escapeHtml(watchTitle)}</i>")
            }
            append("\n\n<b>${escapeHtml(finding.title)}</b>")
            append("\n💰 <b>${escapeHtml(priceText)}</b>")
            append(oldPriceLine)
            append("\n📍 ${escapeHtml(finding.cityName.ifBlank { "Украина" })}")
            append(sellerLine)
            append(deliveryLine)
            append(dealLine)
            append(aiLine)
            if (finding.url.isNotBlank()) {
                append("\n\n<a href=\"${escapeHtml(finding.url)}\">Открыть объявление на OLX</a>")
            }
        }
    }

    private fun postMessage(botToken: String, chatId: String, htmlText: String): Result<String> {
        var conn: HttpURLConnection? = null
        return try {
            val endpoint = URL("https://api.telegram.org/bot$botToken/sendMessage")
            conn = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("text", htmlText)
                put("parse_mode", "HTML")
                put("disable_web_page_preview", false)
            }.toString()

            conn.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code in 200..299) {
                Result.success("Сообщение доставлено в Telegram ✓")
            } else {
                val errMsg = runCatching { JSONObject(body).optString("description") }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "HTTP $code"
                Result.failure(RuntimeException(errMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    private fun escapeHtml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
