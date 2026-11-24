package ua.magerko.olxradar.data.network

import android.util.Log
import org.json.JSONObject
import ua.magerko.olxradar.data.model.ConditionAnalyzer
import ua.magerko.olxradar.data.model.Listing
import ua.magerko.olxradar.data.model.Watch
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

object OlxParser {
    private const val TAG = "OlxParser"
    private const val BASE_URL = "https://www.olx.ua/api/v1/offers/"
    private const val PHOTO_WIDTH = "800"
    private const val PHOTO_HEIGHT = "600"

    // Каталог OLX.ua (проверено по API: offers?category_id=...)
    const val CATEGORY_COMPUTERS = 38      // «Комп'ютери та комплектуючі»
    const val CATEGORY_PC_COMPONENTS = 458 // «Комплектуючі та запчастини»
    const val CATEGORY_PHONES = 44         // «Телефони та аксесуари»
    private val SUBCATEGORIES_COMPUTERS = listOf(78, 83, 84, 453, 466, 476, 480, 3825, 3826)

    private val regexCache = ConcurrentHashMap<String, Regex>()

    private val antiSpamSignals = listOf(
        "скупка", "выкуп", "викуп", "аренда", "оренда", "прокат",
        "под заказ", "під замовлення", "реплика", "репліка",
        "копия", "копія", "пустая коробка", "порожня коробка",
        "послуги ремонту", "услуги ремонта", "ремонт телефонов", "ремонт ноутбуков",
        "ломбард", "в рассрочку", "в розстрочку", "витринный образец", "вітринний зразок"
    )

    data class ParsedQuery(
        val positiveQuery: String,
        val positiveTokens: List<String>,
        val inlineExclusions: List<String>
    )

    data class SearchMatchOptions(
        val smartAntiSpam: Boolean = true,
        val convertForeignCurrency: Boolean = false,
        val usdToUahRate: Float = 41.5f,
        val eurToUahRate: Float = 44.5f,
        val strictQueryWordMatch: Boolean = false,
        val onlyDelivery: Boolean = false
    )

    fun parseQuery(rawQuery: String): ParsedQuery {
        val tokens = rawQuery.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val positives = mutableListOf<String>()
        val exclusions = mutableListOf<String>()
        for (t in tokens) {
            if (t.startsWith("-") && t.length > 1) {
                val cleaned = t.substring(1).trim { !it.isLetterOrDigit() }
                if (cleaned.isNotBlank()) exclusions.add(cleaned)
            } else {
                positives.add(t)
            }
        }
        val posQuery = positives.joinToString(" ")
        return ParsedQuery(
            positiveQuery = posQuery,
            positiveTokens = positives.filter { it.length >= 2 || it.all(Char::isDigit) },
            inlineExclusions = exclusions
        )
    }

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val isoFormatMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val isoFormatAlt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun buildSearchUrl(watch: Watch, offset: Int = 0, limit: Int = 40): String {
        val parsed = parseQuery(watch.query)
        val params = mutableListOf<Pair<String, String>>()
        params.add("offset" to offset.toString())
        params.add("limit" to limit.toString())
        params.add("query" to parsed.positiveQuery)
        params.add("sort_by" to "created_at:desc")

        watch.priceFrom?.let { params.add("filter_float_price:from" to it.toString()) }
        watch.priceTo?.let { params.add("filter_float_price:to" to it.toString()) }
        if (!OlxCities.isMultiCity(watch.cityName)) {
            watch.cityId?.let { params.add("city_id" to it.toString()) }
        }
        val effectiveCategoryId = watch.categoryId ?: presetCategoryId(watch.categoryPreset)
        effectiveCategoryId?.let { params.add("category_id" to it.toString()) }
        watch.state?.let { params.add("filter_enum_state[0]" to it) }
        watch.ownerType?.let { params.add("owner_type" to it) }

        // B3: OLX не предоставляет серверного параметра фильтрации по валюте.
        // Фильтр цены (filter_float_price:from/to) применяется сервером в валюте объявления,
        // а клиентская проверка совпадения валюты выполняется в matchesWatch().

        val queryStr = params.joinToString("&") { (k, v) ->
            val safeK = URLEncoder.encode(k, "UTF-8")
                .replace("%3A", ":")
                .replace("%5B", "[")
                .replace("%5D", "]")
            val safeV = URLEncoder.encode(v, "UTF-8")
                .replace("%3A", ":")
            "$safeK=$safeV"
        }
        return "$BASE_URL?$queryStr"
    }

    fun parseListings(jsonStr: String): List<Listing> {
        val root = JSONObject(jsonStr)
        val dataArray = root.optJSONArray("data") ?: return emptyList()
        val results = mutableListOf<Listing>()

        for (i in 0 until dataArray.length()) {
            val item = dataArray.getJSONObject(i)
            val listing = parseSingleListing(item)
            if (listing != null) {
                results.add(listing)
            }
        }
        return results.distinctBy { it.id }
    }

    private fun parseSingleListing(obj: JSONObject): Listing? {
        return try {
            val id = obj.getLong("id")
            val url = obj.getString("url")
            val title = obj.getString("title")
            val description = obj.optString("description", "")
            val isBusiness = obj.optBoolean("business", false)

            var price: Int? = null
            var currency: String? = null
            var state: String? = null

            val params = obj.optJSONArray("params")
            if (params != null) {
                for (j in 0 until params.length()) {
                    val p = params.getJSONObject(j)
                    val key = p.optString("key")
                    if (key == "price") {
                        val v = p.optJSONObject("value")
                        if (v != null) {
                            price = if (v.isNull("value")) null else v.optInt("value")
                            currency = if (v.has("currency") && !v.isNull("currency")) v.optString("currency") else null
                        }
                    } else if (key == "state") {
                        val v = p.optJSONObject("value")
                        if (v != null) {
                            state = if (v.has("key") && !v.isNull("key")) v.optString("key") else null
                        }
                    }
                }
            }

            val location = obj.optJSONObject("location")
            var cityName = ""
            var cityId: Int? = null
            if (location != null) {
                val city = location.optJSONObject("city")
                if (city != null) {
                    cityName = city.optString("name", "")
                    val parsedCityId = if (city.has("id") && !city.isNull("id")) city.optInt("id", 0) else 0
                    cityId = parsedCityId.takeIf { it > 0 }
                }
            }

            val category = obj.optJSONObject("category")
            val categoryId = category?.optInt("id")

            val photos = obj.optJSONArray("photos")
            var photoUrl: String? = null
            val photoUrls = mutableListOf<String>()
            if (photos != null) {
                for (pIdx in 0 until photos.length()) {
                    val pObj = photos.optJSONObject(pIdx) ?: continue
                    val link = pObj.optString("link", "")
                    if (link.isNotEmpty()) {
                        val formattedLink = link.replace("{width}", PHOTO_WIDTH).replace("{height}", PHOTO_HEIGHT)
                        photoUrls.add(formattedLink)
                        if (photoUrl == null) {
                            photoUrl = formattedLink
                        }
                    }
                }
            }

            val createdTimeStr = obj.optString("created_time", "")
            val createdAt = parseDate(createdTimeStr) ?: System.currentTimeMillis()

            // B13: 'pushup_time' и 'last_refresh_time' могут отсутствовать в ответе API —
            // безопасно откатываемся к null (pushup-логика в MonitorService учитывает null).
            val pushupTimeStr = if (obj.has("pushup_time") && !obj.isNull("pushup_time")) obj.optString("pushup_time") else null
            val pushedAt = if (pushupTimeStr.isNullOrEmpty() || pushupTimeStr == "null") null else parseDate(pushupTimeStr)

            val refreshTimeStr = if (obj.has("last_refresh_time") && !obj.isNull("last_refresh_time")) obj.optString("last_refresh_time") else null
            val refreshedAt = if (refreshTimeStr.isNullOrEmpty() || refreshTimeStr == "null") null else parseDate(refreshTimeStr)

            // SafeDeal / OLX Доставка и продавец
            val safedeal = obj.optJSONObject("safedeal")
            val deliveryObj = obj.optJSONObject("delivery")
            val isDelivery = (safedeal != null && (safedeal.optBoolean("action", false) || safedeal.optBoolean("available", false))) ||
                    (deliveryObj != null && deliveryObj.optBoolean("status", false)) ||
                    obj.optString("delivery", "").isNotBlank()
            val userObj = obj.optJSONObject("user")
            val sellerName = userObj?.optString("name", "")?.trim()?.takeIf { it.isNotBlank() }

            Listing(
                id = id,
                url = url,
                title = title,
                description = description,
                price = price,
                currency = currency,
                cityName = cityName,
                cityId = cityId,
                categoryId = categoryId,
                isBusiness = isBusiness,
                state = state,
                photoUrl = photoUrl,
                photoUrls = photoUrls,
                createdAt = createdAt,
                pushedAt = pushedAt,
                refreshedAt = refreshedAt,
                isDelivery = isDelivery,
                sellerName = sellerName
            )
        } catch (e: Exception) {
            null
        }
    }

    internal fun parseDate(str: String?): Long? {
        if (str.isNullOrBlank()) return null
        val trimmed = str.trim()
        return try {
            synchronized(isoFormat) {
                isoFormat.parse(trimmed)?.time
            }
        } catch (_: Exception) {
            try {
                synchronized(isoFormatMillis) {
                    isoFormatMillis.parse(trimmed)?.time
                }
            } catch (_: Exception) {
                try {
                    val noFrac = trimmed.replace(Regex("\\.\\d+"), "")
                    synchronized(isoFormat) {
                        isoFormat.parse(noFrac)?.time
                    }
                } catch (_: Exception) {
                    try {
                        synchronized(isoFormatAlt) {
                            isoFormatAlt.parse(trimmed)?.time
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
    }

    /**
     * B3, B4, B5 matching logic (backward-compatible default behavior).
     */
    fun matchesWatch(listing: Listing, watch: Watch): Boolean {
        return matchesWatchInternal(
            listing = listing,
            watch = watch,
            options = SearchMatchOptions(smartAntiSpam = false, convertForeignCurrency = false)
        )
    }

    /**
     * Улучшенная фильтрация с поддержкой анти-спама, конвертации валют, строгого совпадения слов запроса
     * и проверки города по названию (UA/RU).
     */
    fun matchesWatchSmart(
        listing: Listing,
        watch: Watch,
        options: SearchMatchOptions
    ): Boolean {
        return matchesWatchInternal(listing, watch, options)
    }

    fun normalizeCurrency(c: String?): String = when (c?.trim()?.uppercase()) {
        "UAH", "ГРН", "ГРН.", "₴" -> "UAH"
        "USD", "$", "ДОЛ" -> "USD"
        "EUR", "€", "ЕВРО" -> "EUR"
        else -> c?.trim()?.uppercase() ?: ""
    }

    fun convertPriceToTargetCurrency(
        price: Int,
        fromCurrency: String,
        targetCurrency: String,
        usdToUah: Float,
        eurToUah: Float
    ): Int? {
        val normFrom = normalizeCurrency(fromCurrency)
        val normTarget = normalizeCurrency(targetCurrency)
        if (normFrom == normTarget) return price
        val inUah: Double = when (normFrom) {
            "UAH" -> price.toDouble()
            "USD" -> price * usdToUah.toDouble()
            "EUR" -> price * eurToUah.toDouble()
            else -> return null
        }
        return when (normTarget) {
            "UAH" -> inUah.toInt()
            "USD" -> (inUah / usdToUah.toDouble()).toInt()
            "EUR" -> (inUah / eurToUah.toDouble()).toInt()
            else -> null
        }
    }

    private fun matchesWatchInternal(
        listing: Listing,
        watch: Watch,
        options: SearchMatchOptions
    ): Boolean {
        // Delivery filter
        if (options.onlyDelivery && !listing.isDelivery) {
            return false
        }

        // Price constraints (B3, B4)
        if (listing.price == null) {
            if (!watch.includeNoPrice) {
                return false
            }
        } else {
            val listingCurr = normalizeCurrency(listing.currency)
            val watchCurr = normalizeCurrency(watch.priceCurrency)
            val currenciesMatch = listingCurr == watchCurr

            val effectivePrice: Int? = if (currenciesMatch) {
                listing.price
            } else if (options.convertForeignCurrency && listingCurr.isNotBlank() && watchCurr.isNotBlank()) {
                convertPriceToTargetCurrency(
                    price = listing.price,
                    fromCurrency = listingCurr,
                    targetCurrency = watchCurr,
                    usdToUah = options.usdToUahRate,
                    eurToUah = options.eurToUahRate
                )
            } else {
                Log.d(
                    TAG,
                    "Currency mismatch: listing currency '${listing.currency}' != watch currency '${watch.priceCurrency}'. Price bounds not enforced."
                )
                null
            }

            if (effectivePrice != null) {
                if (watch.priceFrom != null && effectivePrice < watch.priceFrom) {
                    return false
                }
                if (watch.priceTo != null && effectivePrice > watch.priceTo) {
                    return false
                }
            }
        }

        // City (by ID or by UA/RU city / multi-city list)
        if (watch.cityId != null && listing.cityId != null && !OlxCities.isMultiCity(watch.cityName)) {
            if (listing.cityId != watch.cityId) return false
        } else if (!watch.cityName.isNullOrBlank()) {
            if (!OlxCities.citiesMatch(listing.cityName, watch.cityName)) {
                return false
            }
        }

        // Category check (exact or subcategory match)
        val targetCatId = watch.categoryId ?: presetCategoryId(watch.categoryPreset)
        if (targetCatId != null && listing.categoryId != null) {
            if (!isCategoryOrSubcategory(listing.categoryId, targetCatId)) {
                Log.d(TAG, "Категория лота ${listing.categoryId} отличается от целевой $targetCatId")
                return false
            }
        }

        // State (new / used)
        if (watch.state != null && listing.state != null && listing.state != watch.state) {
            return false
        }

        // Owner type (private / business)
        if (watch.ownerType != null) {
            if (watch.ownerType == "private" && listing.isBusiness) return false
            if (watch.ownerType == "business" && !listing.isBusiness) return false
        }

        val textToSearch = "${listing.title} ${listing.description}"
        val parsedQuery = parseQuery(watch.query)

        // Inline -exclude tokens from query (e.g. "iphone 13 -max -чехол")
        for (excl in parsedQuery.inlineExclusions) {
            if (matchesWordBoundary(textToSearch, excl)) {
                return false
            }
        }

        // Strict query word matching (filters OLX "similar/recommended" noise)
        if (options.strictQueryWordMatch && parsedQuery.positiveTokens.isNotEmpty()) {
            val allTokensPresent = parsedQuery.positiveTokens.all { token ->
                matchesWordBoundary(textToSearch, token)
            }
            if (!allTokensPresent) return false
        }

        // Smart Anti-Spam filter (skips buyback/rental/repair/replica unless negated or requested in query)
        if (options.smartAntiSpam) {
            val queryLower = parsedQuery.positiveQuery.lowercase()
            for (spam in antiSpamSignals) {
                if (!queryLower.contains(spam) && matchesAntiSpamSignal(textToSearch, spam)) {
                    return false
                }
            }
        }

        // B5: Stop words check (matched on word boundaries, not simple substring)
        if (watch.stopWords.isNotBlank()) {
            val stopList = watch.stopWords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            for (stop in stopList) {
                if (matchesWordBoundary(textToSearch, stop)) {
                    return false
                }
            }
        }

        // Must words check: matches all or any based on watch.mustWordsMode ("all" vs "any")
        if (watch.mustWords.isNotBlank()) {
            val mustList = watch.mustWords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (mustList.isNotEmpty()) {
                val isAllMode = watch.mustWordsMode.equals("all", ignoreCase = true)
                val matches = if (isAllMode) {
                    mustList.all { matchesWordBoundary(textToSearch, it) }
                } else {
                    mustList.any { matchesWordBoundary(textToSearch, it) }
                }
                if (!matches) {
                    return false
                }
            }
        }

        // Сенсор состояния: анализирует заголовок и описание, отсекает объявления
        // не того состояния (например, «нерабочий», когда нужен рабочий)
        if (watch.conditionTarget != "any" && watch.conditionThreshold > 0) {
            val score = ConditionAnalyzer.analyze(listing.title, listing.description)
            if (!ConditionAnalyzer.matchesTarget(score, watch.conditionTarget, watch.conditionThreshold)) {
                return false
            }
        }

        // Фильтр по дате публикации (свежести) объявления
        if (watch.publicationAge.isNotBlank() && watch.publicationAge != "any") {
            val now = System.currentTimeMillis()
            val ageMillis = now - listing.createdAt
            val ageHours = ageMillis / (1000 * 60 * 60)
            when (watch.publicationAge.lowercase()) {
                "fresh" -> if (ageHours > 24) return false
                "recent" -> if (ageHours > 72) return false
                "week" -> if (ageHours > 7 * 24) return false
                "old" -> if (ageHours < 7 * 24) return false
                "very_old" -> if (ageHours < 14 * 24) return false
            }
        }

        return true
    }

    /**
     * Вычисляет локальный «Рейтинг выгодности сделки» (0..100) на основе цены относительно рынка,
     * состояния, наличия OLX Доставки, частного продавца и совпадения ключевых слов в заголовке.
     */
    fun computeDealScore(
        listing: Listing,
        watch: Watch,
        marketMedianPrice: Int? = null,
        conditionScore: Int = 50,
        usdToUahRate: Float = 41.5f,
        eurToUahRate: Float = 44.5f
    ): Int {
        var score = 50

        if (marketMedianPrice != null && marketMedianPrice > 0 && listing.price != null && listing.price > 0) {
            val listingCurr = normalizeCurrency(listing.currency)
            val watchCurr = normalizeCurrency(watch.priceCurrency)
            val normalizedPrice = when {
                listingCurr == watchCurr || listingCurr.isBlank() -> listing.price
                else -> convertPriceToTargetCurrency(listing.price, listingCurr, watchCurr, usdToUahRate, eurToUahRate)
            }
            if (normalizedPrice != null && normalizedPrice > 0) {
                val diffPct = ((marketMedianPrice - normalizedPrice).toDouble() / marketMedianPrice) * 100.0
                score += when {
                    diffPct >= 35 -> 30
                    diffPct >= 20 -> 22
                    diffPct >= 10 -> 14
                    diffPct >= 5 -> 7
                    diffPct <= -25 -> -15
                    diffPct <= -10 -> -7
                    else -> 0
                }
            }
        }

        if (watch.conditionTarget != "broken") {
            score += ((conditionScore - 50) * 0.25).toInt()
        }
        if (listing.isDelivery) score += 6
        if (!listing.isBusiness) score += 5
        if (!listing.photoUrl.isNullOrBlank()) score += 4

        val positiveTokens = parseQuery(watch.query).positiveTokens
        if (positiveTokens.isNotEmpty() && positiveTokens.all { matchesWordBoundary(listing.title, it) }) {
            score += 8
        }

        return score.coerceIn(0, 100)
    }

    private fun matchesAntiSpamSignal(text: String, spam: String): Boolean {
        if (!matchesWordBoundary(text, spam)) return false
        val pattern = regexCache.getOrPut("antispam_$spam") {
            Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(spam) + "([^\\p{L}\\p{N}]|$)", RegexOption.IGNORE_CASE)
        }
        val match = pattern.find(text) ?: return false
        val prefixStart = (match.range.first - 14).coerceAtLeast(0)
        val prefix = text.substring(prefixStart, match.range.first).lowercase()
        // Не блокируем фразы вроде «не копия», «не репліка», «без ремонта»
        if (Regex("(^|\\s)(не|без|ні)\\s*$").containsMatchIn(prefix)) {
            return false
        }
        return true
    }

    /**
     * Matches word on boundary across Unicode characters (Cyrillic, Latin, etc.) with cached Regex.
     */
    fun matchesWordBoundary(text: String, word: String): Boolean {
        if (word.isBlank() || text.isBlank()) return false
        val pattern = regexCache.getOrPut(word) {
            Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(word) + "([^\\p{L}\\p{N}]|$)", RegexOption.IGNORE_CASE)
        }
        return pattern.containsMatchIn(text)
    }

    /**
     * Оценка состояния объявления (0..100) от «Сенсора состояния».
     * Вызывается после matchesWatch при сохранении находки.
     */
    fun conditionScore(listing: Listing): Int =
        ConditionAnalyzer.analyze(listing.title, listing.description)

    /**
     * Маппинг пресета категории в числовой ID каталога OLX.ua.
     */
    fun presetCategoryId(preset: String?): Int? = when (preset) {
        "computers" -> CATEGORY_COMPUTERS
        "pc_components" -> CATEGORY_PC_COMPONENTS
        "phones" -> CATEGORY_PHONES
        else -> null
    }

    /** Проверяет, что объявление принадлежит категории или её подкатегории. */
    fun isCategoryOrSubcategory(listingCategoryId: Int, targetCategoryId: Int): Boolean {
        if (listingCategoryId == targetCategoryId) return true
        if (targetCategoryId == CATEGORY_COMPUTERS && (listingCategoryId in SUBCATEGORIES_COMPUTERS || listingCategoryId in 70..90)) return true
        if (targetCategoryId == CATEGORY_PC_COMPONENTS && (listingCategoryId in SUBCATEGORIES_COMPUTERS || listingCategoryId in 450..490)) return true
        if (targetCategoryId == CATEGORY_PHONES && (listingCategoryId in 44..55 || listingCategoryId in 1200..1220)) return true
        return false
    }
}
