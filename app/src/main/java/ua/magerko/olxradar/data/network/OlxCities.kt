package ua.magerko.olxradar.data.network

/**
 * Справочник украинских городов (UA + RU варианты написания) для точного сопоставления
 * локации объявления с настройками радара без ложных отсевов.
 */
object OlxCities {

    data class CityAliasGroup(
        val canonicalUk: String,
        val aliases: List<String>
    )

    val POPULAR_CITIES = listOf(
        "Киев", "Харьков", "Одесса", "Днепр", "Львов",
        "Запорожье", "Винница", "Николаев", "Полтава", "Черкассы"
    )

    private val groups = listOf(
        CityAliasGroup("Київ", listOf("київ", "киев", "kyiv", "kiev")),
        CityAliasGroup("Харків", listOf("харків", "харьков", "kharkiv", "kharkov")),
        CityAliasGroup("Одеса", listOf("одеса", "одесса", "odesa", "odessa")),
        CityAliasGroup("Дніпро", listOf("дніпро", "днепр", "дніпропетровськ", "днепропетровск", "dnipro")),
        CityAliasGroup("Львів", listOf("львів", "львов", "lviv", "lvov")),
        CityAliasGroup("Запоріжжя", listOf("запоріжжя", "запорожье", "zaporizhzhia", "zaporozhye")),
        CityAliasGroup("Кривий Ріг", listOf("кривий ріг", "кривой рог", "kryvyi rih")),
        CityAliasGroup("Миколаїв", listOf("миколаїв", "николаев", "mykolaiv", "nikolaev")),
        CityAliasGroup("Вінниця", listOf("вінниця", "винница", "vinnytsia", "vinnitsa")),
        CityAliasGroup("Полтава", listOf("полтава", "poltava")),
        CityAliasGroup("Чернігів", listOf("чернігів", "чернигов", "chernihiv", "chernigov")),
        CityAliasGroup("Черкаси", listOf("черкаси", "черкассы", "cherkasy", "cherkassy")),
        CityAliasGroup("Житомир", listOf("житомир", "zhytomyr", "zhitomir")),
        CityAliasGroup("Суми", listOf("суми", "сумы", "sumy")),
        CityAliasGroup("Рівне", listOf("рівне", "ровно", "rivne", "rovno")),
        CityAliasGroup("Івано-Франківськ", listOf("івано-франківськ", "ивано-франковск", "ivano-frankivsk", "франківськ", "франковск")),
        CityAliasGroup("Тернопіль", listOf("тернопіль", "тернополь", "ternopil")),
        CityAliasGroup("Луцьк", listOf("луцьк", "луцк", "lutsk")),
        CityAliasGroup("Ужгород", listOf("ужгород", "uzhhorod", "uzhgorod")),
        CityAliasGroup("Хмельницький", listOf("хмельницький", "хмельницкий", "khmelnytskyi")),
        CityAliasGroup("Кропивницький", listOf("кропивницький", "кропивницкий", "кіровоград", "кировоград", "kropyvnytskyi")),
        CityAliasGroup("Чернівці", listOf("чернівці", "черновцы", "chernivtsi", "chernovtsy")),
        CityAliasGroup("Кременчук", listOf("кременчук", "кременчуг", "kremenchuk")),
        CityAliasGroup("Біла Церква", listOf("біла церква", "белая церковь", "bila tserkva")),
        CityAliasGroup("Бровари", listOf("бровари", "бровары", "brovary")),
        CityAliasGroup("Бориспіль", listOf("бориспіль", "борисполь", "boryspil")),
        CityAliasGroup("Ірпінь", listOf("ірпінь", "ирпень", "irpin")),
        CityAliasGroup("Буча", listOf("буча", "bucha")),
        CityAliasGroup("Кам'янське", listOf("кам'янське", "каменское", "днепродзержинск")),
        CityAliasGroup("Нікополь", listOf("нікополь", "никополь", "nikopol")),
        CityAliasGroup("Умань", listOf("умань", "uman")),
        CityAliasGroup("Мукачево", listOf("мукачево", "мукачеве", "mukachevo")),
        CityAliasGroup("Херсон", listOf("херсон", "kherson"))
    )

    private val aliasToGroup: Map<String, CityAliasGroup> = buildMap {
        for (g in groups) {
            for (a in g.aliases) {
                put(normalize(a), g)
            }
        }
    }

    private fun normalize(value: String): String {
        return value.trim()
            .lowercase()
            .replace("'", "")
            .replace("’", "")
            .replace("`", "")
            .replace(Regex("(^|\\s)[мг]\\."), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun splitCities(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun isMultiCity(watchCity: String?): Boolean = splitCities(watchCity).size > 1

    /**
     * Строгое совпадение города (точное или через словарь UA/RU синонимов) для авто-определения city_id.
     */
    fun citiesMatchExact(listingCity: String?, watchCity: String?): Boolean {
        if (watchCity.isNullOrBlank() || listingCity.isNullOrBlank() || isMultiCity(watchCity)) return false
        val normWatch = normalize(watchCity)
        val normListing = normalize(listingCity)
        if (normWatch == normListing) return true

        val watchGroup = aliasToGroup[normWatch] ?: return false
        val listingGroup = aliasToGroup[normListing] ?: return false
        return watchGroup == listingGroup
    }

    /**
     * Проверяет, совпадает ли город объявления с указанным пользователем городом или списком городов ("Киев, Бровары")
     * (поддерживает украинские, русские и транслитерированные названия, а также "Город, район").
     */
    fun citiesMatch(listingCity: String?, watchCity: String?): Boolean {
        val targets = splitCities(watchCity)
        if (targets.isEmpty()) return true
        if (listingCity.isNullOrBlank()) return false
        return targets.any { singleCityMatch(listingCity, it) }
    }

    private fun singleCityMatch(listingCity: String, watchCity: String): Boolean {
        val normWatch = normalize(watchCity)
        val normListing = normalize(listingCity)
        if (normWatch.isBlank()) return true
        if (normListing == normWatch) return true

        // Разделяем по запятой/пробелу для случаев "Київ, Дарницький район"
        val listingPrimary = normListing.substringBefore(',').trim()
        val watchPrimary = normWatch.substringBefore(',').trim()
        if (listingPrimary == watchPrimary) return true

        val watchGroup = aliasToGroup[watchPrimary]
        val listingGroup = aliasToGroup[listingPrimary]
        if (watchGroup != null && listingGroup != null) {
            return watchGroup == listingGroup
        }

        // Проверка по целым словам/токенам, чтобы "буча" не срабатывало на "бучач"
        val tokenRegex = Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(watchPrimary) + "([^\\p{L}\\p{N}]|$)")
        return tokenRegex.containsMatchIn(normListing)
    }
}
