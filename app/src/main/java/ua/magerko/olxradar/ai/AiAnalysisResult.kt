package ua.magerko.olxradar.ai

import org.json.JSONArray
import org.json.JSONObject

data class AiAnalysisResult(
    val isMatch: Boolean,
    val confidence: Int, // 0..100
    val detectedProduct: String?,
    val detectedModel: String?,
    val detectedSpecs: Map<String, String>?,
    val photoMatchesTitle: Boolean,
    val condition: String?, // "new", "used", "broken", etc.
    val completeness: String?, // "complete", "box_only", "parts_only", etc.
    val visibleDefects: List<String>,
    val isSuspicious: Boolean,
    val categoryMatch: Boolean,
    val reasons: List<String>,
    val rejectReason: String?,
    val rawJson: String? = null,
    val query: String? = null
) {
    fun toJsonString(): String {
        return JSONObject().apply {
            put("is_match", isMatch)
            put("confidence", confidence)
            put("detected_product", detectedProduct ?: JSONObject.NULL)
            put("detected_model", detectedModel ?: JSONObject.NULL)
            if (detectedSpecs != null) {
                val specsObj = JSONObject()
                detectedSpecs.forEach { (k, v) -> specsObj.put(k, v) }
                put("detected_specs", specsObj)
            } else {
                put("detected_specs", JSONObject.NULL)
            }
            put("photo_matches_title", photoMatchesTitle)
            put("condition", condition ?: JSONObject.NULL)
            put("completeness", completeness ?: JSONObject.NULL)
            put("visible_defects", JSONArray(visibleDefects))
            put("is_suspicious", isSuspicious)
            put("category_match", categoryMatch)
            put("reasons", JSONArray(reasons))
            put("reject_reason", rejectReason ?: JSONObject.NULL)
            put("query", query ?: JSONObject.NULL)
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): AiAnalysisResult? {
            if (jsonStr.isBlank()) return null
            return try {
                // Strip <think>...</think> reasoning blocks if present (Qwen / DeepSeek reasoning models)
                val cleanStr = jsonStr.replace(Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE), "").trim()

                // Find first '{' and last '}' to handle potential surrounding formatting
                val start = cleanStr.indexOf('{')
                val end = cleanStr.lastIndexOf('}')
                if (start == -1 || end == -1 || end <= start) return null
                val cleanJson = cleanStr.substring(start, end + 1)
                val obj = JSONObject(cleanJson)

                val isMatch = obj.optBoolean("is_match", false)
                val confidence = obj.optInt("confidence", 0).coerceIn(0, 100)
                val detectedProduct = if (obj.isNull("detected_product")) null else obj.optString("detected_product").takeIf { it.isNotBlank() }
                val detectedModel = if (obj.isNull("detected_model")) null else obj.optString("detected_model").takeIf { it.isNotBlank() }

                val specsMap = mutableMapOf<String, String>()
                val specsObj = obj.optJSONObject("detected_specs")
                if (specsObj != null) {
                    val keys = specsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = specsObj.optString(k, "")
                        if (v.isNotBlank()) {
                            specsMap[k] = v
                        }
                    }
                }

                val photoMatchesTitle = obj.optBoolean("photo_matches_title", true)
                val condition = if (obj.isNull("condition")) null else obj.optString("condition").takeIf { it.isNotBlank() }
                val completeness = if (obj.isNull("completeness")) null else obj.optString("completeness").takeIf { it.isNotBlank() }

                val defects = mutableListOf<String>()
                val defectsArr = obj.optJSONArray("visible_defects")
                if (defectsArr != null) {
                    for (i in 0 until defectsArr.length()) {
                        val d = defectsArr.optString(i, "")
                        if (d.isNotBlank()) defects.add(d)
                    }
                }

                val isSuspicious = obj.optBoolean("is_suspicious", false)
                val categoryMatch = obj.optBoolean("category_match", true)

                val reasons = mutableListOf<String>()
                val reasonsArr = obj.optJSONArray("reasons")
                if (reasonsArr != null) {
                    for (i in 0 until reasonsArr.length()) {
                        val r = reasonsArr.optString(i, "")
                        if (r.isNotBlank()) reasons.add(r)
                    }
                }

                val rejectReason = if (obj.isNull("reject_reason")) null else obj.optString("reject_reason").takeIf { it.isNotBlank() }
                val query = if (obj.isNull("query")) null else obj.optString("query").takeIf { it.isNotBlank() }

                AiAnalysisResult(
                    isMatch = isMatch,
                    confidence = confidence,
                    detectedProduct = detectedProduct,
                    detectedModel = detectedModel,
                    detectedSpecs = specsMap.takeIf { it.isNotEmpty() },
                    photoMatchesTitle = photoMatchesTitle,
                    condition = condition,
                    completeness = completeness,
                    visibleDefects = defects,
                    isSuspicious = isSuspicious,
                    categoryMatch = categoryMatch,
                    reasons = reasons,
                    rejectReason = rejectReason,
                    rawJson = cleanJson,
                    query = query
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
