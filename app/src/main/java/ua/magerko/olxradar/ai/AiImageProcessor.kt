package ua.magerko.olxradar.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AiImageProcessor {

    companion object {
        private const val TAG = "AiImageProcessor"
        private const val MAX_IMAGE_DIMENSION = 640
        private const val JPEG_QUALITY = 75
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 5000

        // In-memory LRU cache for Base64 encoded images (keeps up to 30 images)
        private val base64Cache = LruCache<String, String>(30)
    }

    /**
     * Downloads and converts up to [maxPhotos] images to Base64 JPEG data URIs concurrently.
     * Reuses cached images to avoid repeated downloads.
     */
    suspend fun processImages(photoUrls: List<String>, maxPhotos: Int = 2): List<String> = withContext(Dispatchers.IO) {
        val urlsToProcess = photoUrls.filter { it.isNotBlank() }.take(maxPhotos)
        if (urlsToProcess.isEmpty()) return@withContext emptyList()

        coroutineScope {
            val deferreds = urlsToProcess.map { url ->
                async {
                    val cached = base64Cache.get(url)
                    if (cached != null) {
                        return@async cached
                    }

                    try {
                        val base64Data = downloadAndEncode(url)
                        if (base64Data != null) {
                            base64Cache.put(url, base64Data)
                            return@async base64Data
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to download/process image: $url", e)
                    }
                    null
                }
            }
            deferreds.awaitAll().filterNotNull()
        }
    }

    private fun downloadAndEncode(urlStr: String): String? {
        var connection: HttpURLConnection? = null
        var curUrl = urlStr
        var redirects = 0
        val maxRedirects = 3

        return try {
            while (redirects <= maxRedirects) {
                val url = URL(curUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doInput = true
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                    setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    connect()
                }

                val statusCode = connection.responseCode
                if (statusCode in listOf(301, 302, 303, 307, 308)) {
                    val newLoc = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (newLoc.isNullOrBlank()) {
                        return null
                    }
                    curUrl = if (newLoc.startsWith("http://") || newLoc.startsWith("https://")) {
                        newLoc
                    } else {
                        URL(URL(curUrl), newLoc).toString()
                    }
                    redirects++
                    continue
                }

                if (statusCode !in 200..299) {
                    Log.w(TAG, "Image HTTP $statusCode for $curUrl")
                    return null
                }
                break
            }

            val bytes = connection?.inputStream?.use { it.readBytes() } ?: return null
            if (bytes.isEmpty()) return null

            val bitmap = decodeSampledBitmap(bytes, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION) ?: return null

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            bitmap.recycle()

            val encoded = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$encoded"
        } catch (e: Exception) {
            Log.w(TAG, "Error encoding image $urlStr: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun decodeSampledBitmap(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false

        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

        // Further scale if still larger than max dimension
        if (decoded.width > reqWidth || decoded.height > reqHeight) {
            val ratio = decoded.width.toFloat() / decoded.height.toFloat()
            val targetW: Int
            val targetH: Int
            if (ratio > 1.0f) {
                targetW = reqWidth
                targetH = (reqWidth / ratio).toInt().coerceAtLeast(32)
            } else {
                targetH = reqHeight
                targetW = (reqHeight * ratio).toInt().coerceAtLeast(32)
            }
            val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            if (scaled != decoded) {
                decoded.recycle()
            }
            return scaled
        }

        return decoded
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize.coerceAtLeast(1)
    }

    fun clearCache() {
        base64Cache.evictAll()
    }
}
