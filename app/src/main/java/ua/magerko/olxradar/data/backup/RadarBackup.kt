package ua.magerko.olxradar.data.backup

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import ua.magerko.olxradar.data.model.Watch

/**
 * Экспорт/импорт радаров в файл `list.config` (JSON).
 * Формат версионированный: { "app": "OLX Radar", "format": 1, "exported_at": ..., "radars": [...] }
 */
object RadarBackup {

    private const val TAG = "RadarBackup"
    const val DEFAULT_FILE_NAME = "olx-radar-list.config"
    private const val FORMAT = 1

    fun toJson(watches: List<Watch>, exportedAt: Long = System.currentTimeMillis()): String {
        val root = JSONObject()
        root.put("app", "OLX Radar")
        root.put("format", FORMAT)
        root.put("exported_at", exportedAt)
        val arr = JSONArray()
        watches.forEach { w ->
            val o = JSONObject()
            o.put("title", w.title)
            o.put("query", w.query)
            o.put("price_from", w.priceFrom ?: JSONObject.NULL)
            o.put("price_to", w.priceTo ?: JSONObject.NULL)
            o.put("currency", w.priceCurrency)
            o.put("city_name", w.cityName ?: "")
            o.put("city_id", w.cityId ?: JSONObject.NULL)
            o.put("category_id", w.categoryId ?: JSONObject.NULL)
            o.put("category_preset", w.categoryPreset)
            o.put("owner_type", w.ownerType ?: "any")
            o.put("state", w.state ?: "any")
            o.put("stop_words", w.stopWords)
            o.put("must_words", w.mustWords)
            o.put("must_words_mode", w.mustWordsMode)
            o.put("publication_age", w.publicationAge)
            o.put("condition_target", w.conditionTarget)
            o.put("condition_threshold", w.conditionThreshold)
            o.put("notify_mode", w.notifyMode)
            o.put("include_no_price", w.includeNoPrice)
            o.put("interval_sec_override", w.intervalSecOverride ?: JSONObject.NULL)
            o.put("is_active", w.isActive)
            arr.put(o)
        }
        root.put("radars", arr)
        return root.toString(2)
    }

    fun fromJson(json: String): List<Watch> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("radars") ?: run {
            Log.w(TAG, "fromJson: no 'radars' array")
            return emptyList()
        }
        val out = mutableListOf<Watch>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                out += Watch(
                    id = 0L,
                    title = o.optString("title", "").ifBlank { o.optString("query", "Radar") },
                    query = o.optString("query", ""),
                    priceFrom = o.optIntOrNullOrNull("price_from"),
                    priceTo = o.optIntOrNullOrNull("price_to"),
                    priceCurrency = o.optString("currency", "UAH").ifBlank { "UAH" },
                    cityName = o.optString("city_name", "").takeIf { it.isNotBlank() },
                    cityId = o.optIntOrNullOrNull("city_id"),
                    categoryId = o.optIntOrNullOrNull("category_id"),
                    categoryPreset = o.optString("category_preset", "any").ifBlank { "any" },
                    ownerType = o.optString("owner_type", "any").takeIf { it.isNotBlank() && it != "any" },
                    state = o.optString("state", "any").takeIf { it.isNotBlank() && it != "any" },
                    stopWords = o.optString("stop_words", ""),
                    mustWords = o.optString("must_words", ""),
                    mustWordsMode = o.optString("must_words_mode", "any").ifBlank { "any" },
                    publicationAge = o.optString("publication_age", "any").ifBlank { "any" },
                    conditionTarget = o.optString("condition_target", "any").ifBlank { "any" },
                    conditionThreshold = o.optInt("condition_threshold", 0).coerceIn(0, 100),
                    notifyMode = o.optString("notify_mode", "new_pushup").ifBlank { "new_pushup" },
                    includeNoPrice = o.optBoolean("include_no_price", true),
                    intervalSecOverride = o.optIntOrNullOrNull("interval_sec_override"),
                    isActive = o.optBoolean("is_active", true),
                    createdAt = System.currentTimeMillis(),
                    initialized = false
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed radar #${i + 1}: ${e.message}")
            }
        }
        return out.filter { it.query.isNotBlank() }
    }

    private fun JSONObject.optIntOrNullOrNull(name: String): Int? {
        if (!has(name) || isNull(name)) return null
        return optInt(name)
    }
}
