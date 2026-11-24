package ua.magerko.olxradar.data.model

import java.util.concurrent.ConcurrentHashMap

/**
 * «Сенсор состояния» — локальный анализатор текста объявления.
 * Читает заголовок и описание и оценивает, насколько вероятно,
 * что товар рабочий (0 — точно нерабочий/на запчасти, 100 — точно рабочий).
 *
 * Не требует нейросети: работает офлайн на словарях ключевых слов.
 */
object ConditionAnalyzer {

    // Слова-признаки нерабочего состояния (укр + рус).
    private val brokenSignals = listOf(
        "нерабоч", "не робоч", "не працює", "не работает", "розбит", "разбит",
        "разбить", "трісну", "треснул", "треснут", "скол", "розкол",
        "на запчастини", "на запчасти", "под запчасти", "на детали", "не включає",
        "не включается", "не вмика", "не запускає", "не запускается", "залит",
        "після води", "после воды", "утоплен", "розбитий екран", "разбитый экран",
        "битий", "битый", "дефект екрана", "дефект экрана", "відломан", "отломан",
        "не тримає", "не держит", "глюч", "глюк", "зависає", "зависает",
        "замкну", "куйовд", "клиентский брак", "після ремонту", "после ремонта",
        "потрібен ремонт", "нужен ремонт", "требует ремонт", "розібраний",
        "разобран", "не оригінал", "втрачає", "теряет", "меркне", "мигає", "мигает",
        "биті пікселі", "битые пиксели", "вигорання", "выгорание", "артефакти", "артефакты",
        "відвал", "отвал", "донор", "під відновлення", "под восстановление",
        "не бачить сімку", "не видит сим", "заблокований", "заблокирован", "mdm", "icloud",
        "фейс не працює", "face id не работает", "не заряджається", "не заряжается"
    )

    // Слова-признаки рабочего состояния.
    private val workingSignals = listOf(
        "робочий", "робоче", "робоча", "працює", "працюють", "все працює",
        "рабочий", "рабочее", "рабочая", "работает", "всё работает", "все работает",
        "ідеальний", "идеальное", "идеальний", "як новий", "как новый",
        "як нова", "як нове", "без дефектів", "без дефектов", "без подряпин",
        "без царапин", "в ідеальному", "в идеале", "відмінний стан", "отличное состояние",
        "повністю справний", "полностью исправен", "справний", "исправен",
        "перевірений", "проверен", "тест пройдено", "перевірений тестом",
        "без нюансів", "без нюансов", "не розбирався", "не вскрывался",
        "пломба", "на пломбі", "на пломбе", "гарантія", "гарантия",
        "офіціал", "официал", "neverlock", "неверлок", "true tone працює"
    )

    private val regexCache = ConcurrentHashMap<String, Regex>()

    /**
     * Оценка «рабочести» объявления от 0 до 100.
     * 50 — признаков нет (нейтрально), >50 — рабочие сигналы,
     * <50 — сигналы поломки.
     */
    fun scoreWorking(title: String, description: String): Int {
        val text = "${title.lowercase()} ${description.lowercase()}"
        var broken = 0
        var working = 0

        for (signal in brokenSignals) {
            if (matchesWordBoundary(text, signal)) broken++
        }
        for (signal in workingSignals) {
            if (matchesWordBoundary(text, signal)) working++
        }

        // «На запчасти» и «нерабочий» в заголовке — самый сильный сигнал.
        val titleLower = title.lowercase()
        val titleBroken = brokenSignals.any { matchesWordBoundary(titleLower, it) }
        if (titleBroken) broken += 2

        val raw = 50 + (working - broken) * 18
        return raw.coerceIn(0, 100)
    }

    /** Проверка слова по границам (кириллица/латиница) с кэшированием Regex. */
    private fun matchesWordBoundary(text: String, word: String): Boolean {
        if (word.isBlank() || text.isBlank()) return false
        val pattern = regexCache.getOrPut(word) {
            Regex(
                "(^|[^\\p{L}\\p{N}])" + Regex.escape(word) + "([^\\p{L}\\p{N}]|$)",
                RegexOption.IGNORE_CASE
            )
        }
        return pattern.containsMatchIn(text)
    }

    /**
     * Решение фильтра по состоянию:
     * target=working — объявление проходит, если оценка >= порога.
     * target=broken — проходит, если оценка <= (100 - порога).
     */
    fun passes(score: Int, target: String?, threshold: Int): Boolean {
        if (target.isNullOrEmpty() || target == "any" || threshold <= 0) return true
        return when (target) {
            "working" -> score >= threshold
            "broken" -> score <= (100 - threshold)
            else -> true
        }
    }

    /** Алиас, используемый движком фильтрации (OlxParser). */
    fun analyze(title: String, description: String): Int = scoreWorking(title, description)

    /** Синоним passes для OlxParser. */
    fun matchesTarget(score: Int, target: String, threshold: Int): Boolean =
        passes(score, target, threshold)

    /** Человекочитаемое описание порога для UI-ползунка. */
    fun explain(target: String, threshold: Int): String = when (target) {
        "working" -> when {
            threshold <= 20 -> "пропускаем почти всё, кроме явного брака"
            threshold <= 45 -> "отсекаем явные «нерабочий / на запчасти»"
            threshold <= 70 -> "нужны уверенные признаки работы в тексте"
            else -> "только объявления с явным «робочий / працює / ідеальний»"
        }
        "broken" -> when {
            threshold <= 20 -> "пропускаем почти всё, кроме явно исправных"
            threshold <= 45 -> "отсекаем «робочий / ідеальний / як новий»"
            threshold <= 70 -> "нужны уверенные признаки поломки"
            else -> "только объявления с явным «нерабочий / на запчастини»"
        }
        else -> "выключен"
    }

    /** Короткая подпись оценки для карточки объявления. */
    fun explainScore(score: Int): String = when {
        score >= 85 -> "Отличное состояние"
        score >= 65 -> "Рабочий"
        score >= 45 -> "Состояние неясно"
        score >= 25 -> "Возможны проблемы"
        else -> "Нерабочий / на запчасти"
    }
}
