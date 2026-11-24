package ua.magerko.olxradar.data.network

import android.net.Uri
import ua.magerko.olxradar.data.model.OlxCategory
import ua.magerko.olxradar.data.model.Watch
import java.net.URLDecoder

/**
 * Разбирает скопированную пользователем ссылку поиска OLX.ua и превращает её в предзаполненный радар.
 */
object OlxUrlImporter {

    fun parseOlxUrl(rawUrl: String): Watch? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return null
        val normalizedUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

        return try {
            val uri = Uri.parse(normalizedUrl)
            val host = uri.host?.lowercase() ?: return null
            if (!host.contains("olx.ua")) return null

            val pathSegments = uri.pathSegments ?: emptyList()

            // Извлекаем поисковый запрос из сегмента q-... (например, /uk/list/q-iphone-13-pro/)
            var query = ""
            for (seg in pathSegments) {
                if (seg.startsWith("q-", ignoreCase = true)) {
                    val decoded = URLDecoder.decode(seg.substring(2), "UTF-8")
                    query = decoded.replace("-", " ").replace("+", " ").trim()
                    break
                }
            }

            // Параметры из query string (декодируем вручную, т.к. Uri.getQueryParameter чувствителен к %5B vs [ и : vs %3A)
            val params = parseQueryParams(uri)
            val queryParam = params["q"] ?: params["query"]
            if (query.isBlank() && !queryParam.isNullOrBlank()) {
                query = queryParam.trim()
            }

            val priceFrom = (params["search[filter_float_price:from]"]
                ?: params["filter_float_price:from"])?.toIntOrNull()
            val priceTo = (params["search[filter_float_price:to]"]
                ?: params["filter_float_price:to"])?.toIntOrNull()

            val stateRaw = params["search[filter_enum_state][0]"]
                ?: params["filter_enum_state[0]"]
            val state = when (stateRaw?.lowercase()) {
                "new" -> "new"
                "used" -> "used"
                else -> null
            }

            val ownerRaw = params["search[private_business]"]
                ?: params["owner_type"]
            val ownerType = when (ownerRaw?.lowercase()) {
                "private" -> "private"
                "business" -> "business"
                else -> null
            }

            val currencyRaw = params["currency"]?.uppercase()
            val currency = when (currencyRaw) {
                "USD", "EUR", "UAH" -> currencyRaw
                else -> "UAH"
            }

            val fullPath = pathSegments.joinToString("/").lowercase()
            val categoryId = when {
                fullPath.contains("telefony") || fullPath.contains("smartfony") -> OlxCategory.PHONES.id
                fullPath.contains("komplektuyuschie") || fullPath.contains("komplektuyuchi") ||
                    fullPath.contains("zapchasti-dlya-pk") || fullPath.contains("zapchastyny-dlya-pk") -> OlxCategory.PC_COMPONENTS.id
                fullPath.contains("kompyutery") || fullPath.contains("noutbuki") -> OlxCategory.COMPUTERS.id
                else -> null
            }

            // Пытаемся извлечь город из пути вида /uk/elektronika/.../kiev/q-.../
            val nonCitySegments = setOf(
                "uk", "ru", "list", "obyavleniya", "elektronika",
                "telefony-i-aksesuary", "telefony-ta-aksesuary",
                "kompyutery-i-komplektuyuschie", "kompyutery-ta-komplektuyuchi",
                "komplektuyuschie-i-aksessuary", "komplektuyuchi-ta-zapchastyny"
            )
            val qIdx = pathSegments.indexOfFirst { it.startsWith("q-", ignoreCase = true) }
            val candidateCitySlug = if (qIdx > 0) {
                pathSegments[qIdx - 1].lowercase().takeIf { it !in nonCitySegments && !it.startsWith("q-") }
            } else null
            val detectedCity = candidateCitySlug?.replace("-", " ")?.let { slug ->
                if (OlxCities.POPULAR_CITIES.any { OlxCities.citiesMatchExact(slug, it) }) {
                    OlxCities.POPULAR_CITIES.firstOrNull { OlxCities.citiesMatchExact(slug, it) } ?: slug
                } else null
            }

            if (query.isBlank()) {
                query = pathSegments.lastOrNull {
                    !it.startsWith("q-") && it.lowercase() !in nonCitySegments
                }?.replace("-", " ")?.trim() ?: ""
            }
            if (query.isBlank()) return null

            Watch(
                title = query.replaceFirstChar { it.uppercase() },
                query = query,
                priceFrom = priceFrom,
                priceTo = priceTo,
                priceCurrency = currency,
                cityName = detectedCity,
                categoryId = categoryId,
                state = state,
                ownerType = ownerType,
                notifyMode = "new_pushup",
                includeNoPrice = true
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseQueryParams(uri: Uri): Map<String, String> {
        val raw = uri.encodedQuery ?: return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (pair in raw.split('&')) {
            if (pair.isBlank()) continue
            val eqIdx = pair.indexOf('=')
            val encodedKey = if (eqIdx >= 0) pair.substring(0, eqIdx) else pair
            val encodedVal = if (eqIdx >= 0) pair.substring(eqIdx + 1) else ""
            val key = URLDecoder.decode(encodedKey, "UTF-8")
            val value = URLDecoder.decode(encodedVal, "UTF-8")
            result[key] = value
        }
        return result
    }
}
