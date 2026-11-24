package ua.magerko.olxradar.data.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class ChromiumBridge(private val context: Context) {

    companion object {
        private const val TAG = "ChromiumBridge"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    @Volatile
    private var basePageReady: CompletableDeferred<Unit> = CompletableDeferred()
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val idCounter = AtomicLong(0)

    init {
        mainHandler.post {
            initWebView()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        try {
            destroyWebView()
            basePageReady = CompletableDeferred()
            val wv = WebView(context.applicationContext)
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(wv, true)

            val settings = wv.settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url != null && url.startsWith("https://www.olx.ua")) {
                        basePageReady.complete(Unit)
                    }
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    val didCrash = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        detail?.didCrash()
                    } else {
                        false
                    }
                    Log.w(
                        TAG,
                        "onRenderProcessGone detected (didCrash: $didCrash). Recreating WebView."
                    )
                    destroyWebView()
                    initWebView()
                    val ex = RuntimeException("WebView renderer process gone")
                    ua.magerko.olxradar.util.AppLog.w(TAG, "Процесс WebView упал — пересоздаю")
                    val keys = pendingRequests.keys().toList()
                    for (k in keys) {
                        pendingRequests.remove(k)?.completeExceptionally(ex)
                    }
                    return true
                }
            }
            wv.addJavascriptInterface(JsInterface(), "ChromiumBridge")

            wv.loadDataWithBaseURL("https://www.olx.ua", "<html><body></body></html>", "text/html", "UTF-8", null)
            webView = wv
        } catch (e: Exception) {
            basePageReady.completeExceptionally(e)
            ua.magerko.olxradar.util.AppLog.w(TAG, "Не удалось создать WebView", e)
        }
    }

    fun destroy() {
        val ex = CancellationException("ChromiumBridge destroyed")
        val keys = pendingRequests.keys().toList()
        for (k in keys) {
            pendingRequests.remove(k)?.completeExceptionally(ex)
        }
        mainHandler.post { destroyWebView() }
    }

    private fun destroyWebView() {
        try {
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
            webView?.destroy()
        } catch (e: Exception) {
            ua.magerko.olxradar.util.AppLog.w(TAG, "Ошибка уничтожения WebView", e)
        } finally {
            webView = null
        }
    }

    /**
     * B10: All calls to WebView are on the main thread.
     * Retries up to 3 times with backoff on transient errors (HTTP 500, burst limit).
     */
    suspend fun fetchJson(url: String, timeoutMs: Long = 25000): String {
        var lastException: Exception? = null
        for (attempt in 1..3) {
            try {
                return executeFetch(url, timeoutMs)
            } catch (e: Exception) {
                lastException = e
                val is5xx = e.message?.contains("HTTP 5") == true
                if (attempt < 3) {
                    val waitMs = if (is5xx) 3500L * attempt else 1200L
                    ua.magerko.olxradar.util.AppLog.d(TAG, "Ответ OLX ($attempt/3: ${e.message}) — мягкий повтор через ${waitMs / 1000}с")
                    kotlinx.coroutines.delay(waitMs)
                    withContext(Dispatchers.Main) {
                        if (e.message?.contains("renderer", ignoreCase = true) == true) {
                            initWebView()
                        } else {
                            ensureWebView()
                        }
                    }
                } else {
                    ua.magerko.olxradar.util.AppLog.w(TAG, "Все 3 попытки запроса не удались (${e.message})")
                }
            }
        }
        throw lastException ?: RuntimeException("Запрос не удался после 3 попыток")
    }

    private suspend fun executeFetch(url: String, timeoutMs: Long): String {
        val reqId = "req_${idCounter.incrementAndGet()}"
        val deferred = CompletableDeferred<String>()
        pendingRequests[reqId] = deferred

        withContext(Dispatchers.Main) {
            ensureWebView()
        }
        // Ждём коммита https://www.olx.ua (до 3с), чтобы fetch не упал с CORS на about:blank
        withTimeoutOrNull(3000L) {
            runCatching { basePageReady.await() }
        }

        withContext(Dispatchers.Main) {
            val wv = webView
            if (wv == null) {
                pendingRequests.remove(reqId)?.completeExceptionally(IllegalStateException("WebView unavailable"))
                return@withContext
            }
            val quotedUrl = JSONObject.quote(url)
            val js = """
                (function() {
                    fetch($quotedUrl, {
                        method: 'GET',
                        credentials: 'include',
                        headers: {
                            'Accept': 'application/json, text/plain, */*',
                            'Accept-Language': 'uk-UA,uk;q=0.9,ru;q=0.8,en;q=0.7'
                        }
                    })
                    .then(function(res) {
                        if (!res.ok) {
                            return res.text().then(function(t) {
                                var titleMatch = t ? t.match(/<title>([^<]*)<\/title>/i) : null;
                                var h1Match = t ? t.match(/<h[12][^>]*>([^<]*)<\/h[12]>/i) : null;
                                var errDetail = titleMatch ? titleMatch[1].trim() : (h1Match ? h1Match[1].trim() : '');
                                if (!errDetail && t) {
                                    errDetail = t.substring(0, 60).replace(/\s+/g, ' ').trim();
                                }
                                throw new Error('HTTP ' + res.status + (errDetail ? ': ' + errDetail : ''));
                            }).catch(function(e) {
                                if (e && e.message && e.message.indexOf('HTTP') === 0) {
                                    throw e;
                                }
                                throw new Error('HTTP ' + res.status);
                            });
                        }
                        return res.text();
                    })
                    .then(function(text) {
                        window.ChromiumBridge.deliverSuccess('$reqId', text);
                    })
                    .catch(function(err) {
                        window.ChromiumBridge.deliverError('$reqId', '' + err);
                    });
                })();
            """.trimIndent()

            wv.evaluateJavascript(js, null)
        }

        return try {
            withTimeout(timeoutMs) {
                deferred.await()
            }
        } finally {
            pendingRequests.remove(reqId)
        }
    }

    private fun ensureWebView() {
        if (webView == null) {
            initWebView()
        }
    }

    internal inner class JsInterface {
        @JavascriptInterface
        fun deliverSuccess(reqId: String, payload: String) {
            pendingRequests[reqId]?.complete(payload)
        }

        @JavascriptInterface
        fun deliverError(reqId: String, errorMsg: String) {
            pendingRequests[reqId]?.completeExceptionally(RuntimeException(errorMsg))
        }
    }
}
