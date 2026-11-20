package ua.magerko.olxradar.ai

/**
 * Предустановленные и кастомные провайдеры для мультимодальных (Vision) нейросетей.
 * Поддерживает любой OpenAI-совместимый API (Groq, OpenAI, OpenRouter, Ollama, LM Studio, vLLM и др.).
 */
enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val keyPlaceholder: String,
    val keyHint: String,
    val suggestedVisionModels: List<String>
) {
    GROQ(
        id = "groq",
        displayName = "Groq",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "qwen/qwen3.8-27b",
        keyPlaceholder = "gsk_...",
        keyHint = "Получите ключ на console.groq.com",
        suggestedVisionModels = listOf("qwen/qwen3.8-27b")
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        keyPlaceholder = "sk-proj-...",
        keyHint = "Получите ключ на platform.openai.com",
        suggestedVisionModels = listOf("gpt-4o-mini", "gpt-4o")
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "qwen/qwen-2.5-vl-72b-instruct:free",
        keyPlaceholder = "sk-or-v1-...",
        keyHint = "Получите ключ на openrouter.ai/keys",
        suggestedVisionModels = listOf(
            "qwen/qwen-2.5-vl-72b-instruct:free",
            "meta-llama/llama-3.2-11b-vision-instruct:free",
            "google/gemini-2.0-flash-exp:free",
            "openai/gpt-4o-mini"
        )
    ),
    CUSTOM(
        id = "custom",
        displayName = "Кастомный (OpenAI API)",
        defaultBaseUrl = "http://localhost:11434/v1",
        defaultModel = "llava",
        keyPlaceholder = "sk-... (для Ollama можно пусто)",
        keyHint = "Любой OpenAI-совместимый сервер (Ollama, LM Studio, vLLM, локальный прокси)",
        suggestedVisionModels = listOf("llava", "minicpm-v", "qwen2.5-vl")
    );

    companion object {
        fun fromId(id: String?): AiProvider {
            if (id.isNullOrBlank()) return GROQ
            return entries.find { it.id.equals(id.trim(), ignoreCase = true) } ?: CUSTOM
        }
    }
}
