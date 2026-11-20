package ua.magerko.olxradar.ai

import android.util.Log
import ua.magerko.olxradar.data.local.AiCacheDao
import ua.magerko.olxradar.data.local.AiCacheEntry
import ua.magerko.olxradar.data.local.FindingDao
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.data.model.Listing
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.util.AppLog

data class AiEvaluation(
    val status: String,
    val shouldNotify: Boolean,
    val shouldSaveFinding: Boolean,
    val result: AiAnalysisResult?,
    val errorMessage: String? = null
)

class AiFilterEngine(
    private val aiPreferences: AiPreferences,
    private val groqClient: GroqVisionClient,
    private val imageProcessor: AiImageProcessor,
    private val aiCacheDao: AiCacheDao? = null
) {

    companion object {
        private const val TAG = "AiFilterEngine"
    }

    suspend fun evaluateListing(listing: Listing, watch: Watch): AiEvaluation {
        if (!aiPreferences.isAiEnabled || aiPreferences.apiKey.isBlank()) {
            return AiEvaluation(
                status = AiStatus.SKIPPED,
                shouldNotify = true,
                shouldSaveFinding = true,
                result = null
            )
        }

        // Periodically prune stale cache entries (older than 14 days)
        try {
            val twoWeeksAgo = System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000L
            aiCacheDao?.pruneOld(twoWeeksAgo)
        } catch (e: Exception) {
            AppLog.w(TAG, "Не удалось очистить устаревший ИИ-кэш", e)
        }

        // 1. Check AI cache to avoid re-analyzing the same listing
        val cached = try {
            aiCacheDao?.getByListingId(listing.id)
        } catch (e: Exception) {
            Log.w(TAG, "Cache lookup error: ${e.message}")
            null
        }

        val analysisResult: AiAnalysisResult
        if (cached != null) {
            val parsed = AiAnalysisResult.fromJson(cached.resultJson)
            // Если в кэше записан отказ (!isMatch), но сейчас лот проверяет ДРУГОЙ радар с ДРУГИМ запросом —
            // старый отказ неприменим (возможно, для нового радара товар подходит идеально).
            val cachedQuery = parsed?.query
            val queryMismatchForRejected = parsed != null && !parsed.isMatch && cachedQuery != null &&
                    !cachedQuery.equals(watch.query.trim(), ignoreCase = true)

            if (parsed != null && !queryMismatchForRejected) {
                analysisResult = parsed
                val statusLabel = when {
                    analysisResult.condition == "broken" || analysisResult.visibleDefects.isNotEmpty() -> AiStatus.NEEDS_REVIEW
                    !analysisResult.isMatch && analysisResult.confidence >= 75 -> AiStatus.REJECTED
                    else -> AiStatus.MATCH
                }
                val reasonText = analysisResult.reasons.firstOrNull() ?: analysisResult.rejectReason ?: (if (analysisResult.isMatch) "Подходит под условия поиска" else "Не подходит")
                AiRuntime.recordCached(
                    listingId = listing.id,
                    title = listing.title,
                    url = listing.url,
                    photoUrl = listing.photoUrl ?: listing.photoUrls.firstOrNull(),
                    price = listing.price,
                    currency = listing.currency ?: "грн",
                    radarTitle = watch.title,
                    status = statusLabel,
                    confidence = analysisResult.confidence,
                    reasons = reasonText
                )
            } else {
                val fresh = runAnalysis(listing, watch)
                if (fresh.isFailure) {
                    return handleAiError(fresh.exceptionOrNull(), listing, watch)
                }
                analysisResult = fresh.getOrThrow().copy(query = watch.query.trim())
                try {
                    aiCacheDao?.insert(AiCacheEntry(listing.id, analysisResult.toJsonString()))
                } catch (e: Exception) {
                    Log.w(TAG, "Cache insert error: ${e.message}")
                }
            }
        } else {
            val fresh = runAnalysis(listing, watch)
            if (fresh.isFailure) {
                return handleAiError(fresh.exceptionOrNull(), listing, watch)
            }
            analysisResult = fresh.getOrThrow().copy(query = watch.query.trim())
            try {
                aiCacheDao?.insert(AiCacheEntry(listing.id, analysisResult.toJsonString()))
            } catch (e: Exception) {
                Log.w(TAG, "Cache insert error: ${e.message}")
            }
        }

        val eval = evaluateResult(analysisResult)
        val reasonsJoined = if (analysisResult.reasons.isNotEmpty()) {
            analysisResult.reasons.joinToString("\n• ", prefix = "• ")
        } else {
            analysisResult.rejectReason ?: (if (analysisResult.isMatch) "Подходит под условия поиска" else "Не подходит под критерии")
        }
        AiRuntime.finish(eval.status, analysisResult.confidence, reasonsJoined)
        AiThoughtLogger.log(
            AiThoughtLog(
                listingId = listing.id,
                title = listing.title,
                price = if (listing.price != null) "${listing.price} ${listing.currency ?: "грн"}" else "Договорная",
                radarTitle = watch.title,
                status = eval.status,
                confidence = analysisResult.confidence,
                reasons = reasonsJoined,
                rawJson = analysisResult.toJsonString()
            )
        )
        return eval
    }
    /**
     * Повторный ИИ-анализ уже сохранённой находки: сбрасывает кэш по listing_id,
     * запускает свежий анализ и обновляет ИИ-поля находки в БД.
     */
    suspend fun reanalyzeFinding(finding: Finding, watch: Watch, findingDao: FindingDao): AiEvaluation {
        val listing = Listing(
            id = finding.listingId,
            url = finding.url,
            title = finding.title,
            description = finding.description,
            price = finding.price,
            currency = finding.currency,
            cityName = finding.cityName,
            cityId = null,
            categoryId = null,
            isBusiness = false,
            state = null,
            photoUrl = finding.photoUrl,
            photoUrls = emptyList(),
            createdAt = finding.createdAt,
            pushedAt = null,
            refreshedAt = null
        )
        try {
            aiCacheDao?.deleteByListingId(listing.id)
        } catch (e: Exception) {
            Log.w(TAG, "Cache delete error on reanalysis: ${e.message}")
        }

        val fresh = runAnalysis(listing, watch)
        val eval = fresh.fold(
            onSuccess = { rawResult ->
                val withQuery = rawResult.copy(query = watch.query.trim())
                evaluateResult(withQuery).copy(result = withQuery)
            },
            onFailure = { handleAiError(it) }
        )

        val updated = finding.copy(
            aiScore = eval.result?.confidence,
            aiStatus = eval.status,
            aiProduct = eval.result?.detectedProduct,
            aiModel = eval.result?.detectedModel,
            aiPhotoMatch = eval.result?.photoMatchesTitle,
            aiReasons = eval.result?.reasons?.joinToString("\n• ", prefix = "• "),
            aiRejectReason = eval.result?.rejectReason,
            aiDetailsJson = eval.result?.toJsonString()
        )
        try {
            findingDao.updateFinding(updated)
        } catch (e: Exception) {
            Log.w(TAG, "Finding update error: ${e.message}")
        }
        if (eval.result != null) {
            try {
                aiCacheDao?.insert(AiCacheEntry(listing.id, eval.result.toJsonString()))
            } catch (e: Exception) {
                Log.w(TAG, "Cache insert error on reanalysis: ${e.message}")
            }
        }
        return eval
    }

    private suspend fun runAnalysis(listing: Listing, watch: Watch): Result<AiAnalysisResult> {
        val photosToProcess = if (listing.photoUrls.isNotEmpty()) {
            listing.photoUrls
        } else if (!listing.photoUrl.isNullOrEmpty()) {
            listOf(listing.photoUrl)
        } else {
            emptyList()
        }

        // Внешний статус: что нейросеть обрабатывает прямо сейчас (с ссылкой и фото)
        AiRuntime.start(
            listingId = listing.id,
            listingTitle = listing.title,
            url = listing.url,
            photoUrl = listing.photoUrl ?: listing.photoUrls.firstOrNull(),
            price = listing.price,
            currency = listing.currency ?: "грн",
            radarTitle = watch.title,
            photosTotal = photosToProcess.take(aiPreferences.maxPhotos).size
        )
        AppLog.d(TAG, "ИИ: анализ '${listing.title.take(60)}' (радар '${watch.title}', фото: ${photosToProcess.size})")

        val base64Images = imageProcessor.processImages(photosToProcess, aiPreferences.maxPhotos)
        val result = groqClient.analyzeListing(listing, watch, base64Images)
        result.onFailure { AiRuntime.finishError(it.message ?: it.toString()) }
        return result
    }

    internal fun evaluateResult(ai: AiAnalysisResult): AiEvaluation {
        val confidence = ai.confidence
        val threshold = aiPreferences.confidenceThreshold
        val isManual = aiPreferences.isManualReviewMode
        val isStrict = aiPreferences.isStrictMode
        val notifyRejected = aiPreferences.isNotifyRejected
        // Отклонённые находки сохраняются в режиме ручной проверки (когда strict mode выключен),
        // чтобы они корректно отображались во вкладке «Отфильтрованные» (rejectedFindingsFlow).
        val shouldSaveRejected = !isStrict && isManual

        // 1. Completeness filter: Box only or parts only -> REJECTED
        // Отклоняем ТОЛЬКО если продаётся исключительно коробка или запчасти БЕЗ самого товара (!ai.isMatch).
        // Если же основной товар присутствует (isMatch == true), лот НЕ бракуется!
        val isBoxOrParts = ai.completeness in listOf("box_only", "parts_only", "parts", "box")
        if (isBoxOrParts && !ai.isMatch) {
            return AiEvaluation(
                status = AiStatus.REJECTED,
                shouldNotify = notifyRejected,
                shouldSaveFinding = shouldSaveRejected,
                result = ai
            )
        }

        // 2. Photo does not match title with high confidence -> REJECTED
        if (!ai.photoMatchesTitle && confidence >= 75) {
            return AiEvaluation(
                status = AiStatus.REJECTED,
                shouldNotify = notifyRejected,
                shouldSaveFinding = shouldSaveRejected,
                result = ai
            )
        }

        // 3. AI reports definite mismatch (confidence >= 75) -> REJECTED
        if (!ai.isMatch && confidence >= 75) {
            return AiEvaluation(
                status = AiStatus.REJECTED,
                shouldNotify = notifyRejected,
                shouldSaveFinding = shouldSaveRejected,
                result = ai
            )
        }

        // 4. Defective product (condition == "broken" or visible defects present)
        // Should not be auto-rejected (user might want for repair/cheap price), mark NEEDS_REVIEW
        val isDefective = ai.condition == "broken" || ai.visibleDefects.isNotEmpty()
        if (isDefective) {
            return AiEvaluation(
                status = AiStatus.NEEDS_REVIEW,
                shouldNotify = true,
                shouldSaveFinding = true,
                result = ai
            )
        }

        // 5. Explicit suspicious flag
        if (ai.isSuspicious && aiPreferences.isCheckSuspicious) {
            return AiEvaluation(
                status = AiStatus.NEEDS_REVIEW,
                shouldNotify = true,
                shouldSaveFinding = true,
                result = ai
            )
        }

        // 6. AI reports is_match == true
        if (ai.isMatch) {
            return when {
                confidence >= threshold -> {
                    AiEvaluation(
                        status = AiStatus.MATCH,
                        shouldNotify = true,
                        shouldSaveFinding = true,
                        result = ai
                    )
                }
                confidence >= 50 -> {
                    // 50-74: requires review
                    AiEvaluation(
                        status = AiStatus.NEEDS_REVIEW,
                        shouldNotify = true,
                        shouldSaveFinding = true,
                        result = ai
                    )
                }
                else -> {
                    // 0-49: low relevance
                    if (isStrict) {
                        AiEvaluation(
                            status = AiStatus.REJECTED,
                            shouldNotify = notifyRejected,
                            shouldSaveFinding = shouldSaveRejected,
                            result = ai
                        )
                    } else if (isManual) {
                        // "Не пропускать потенциально выгодные объявления из-за недостаточной уверенности AI"
                        AiEvaluation(
                            status = AiStatus.NEEDS_REVIEW,
                            shouldNotify = true,
                            shouldSaveFinding = true,
                            result = ai
                        )
                    } else {
                        AiEvaluation(
                            status = AiStatus.REJECTED,
                            shouldNotify = notifyRejected,
                            shouldSaveFinding = shouldSaveRejected,
                            result = ai
                        )
                    }
                }
            }
        }

        // 7. AI reports is_match == false with low confidence (< 75, AI is uncertain)
        // "Не пропускать потенциально выгодные объявления из-за недостаточной уверенности AI"
        return if (isStrict) {
            AiEvaluation(
                status = AiStatus.REJECTED,
                shouldNotify = notifyRejected,
                shouldSaveFinding = shouldSaveRejected,
                result = ai
            )
        } else if (isManual) {
            AiEvaluation(
                status = AiStatus.NEEDS_REVIEW,
                shouldNotify = true,
                shouldSaveFinding = true,
                result = ai
            )
        } else {
            AiEvaluation(
                status = AiStatus.REJECTED,
                shouldNotify = notifyRejected,
                shouldSaveFinding = shouldSaveRejected,
                result = ai
            )
        }
    }

    private fun handleAiError(e: Throwable?): AiEvaluation = handleAiError(e, null, null)

    private fun handleAiError(e: Throwable?, listing: Listing? = null, watch: Watch? = null): AiEvaluation {
        val errorMsg = e?.message ?: "AI request failed"
        Log.w(TAG, "AI processing error: $errorMsg")
        if (listing != null && watch != null) {
            AiThoughtLogger.log(
                AiThoughtLog(
                    listingId = listing.id,
                    title = listing.title,
                    price = if (listing.price != null) "${listing.price} ${listing.currency ?: "грн"}" else "Договорная",
                    radarTitle = watch.title,
                    status = AiStatus.ERROR,
                    confidence = null,
                    reasons = "Ошибка запроса к Groq Vision API",
                    rawJson = "",
                    error = errorMsg
                )
            )
        }
        // Graceful fallback: do NOT drop the listing when AI fails!
        return AiEvaluation(
            status = AiStatus.ERROR,
            shouldNotify = true,
            shouldSaveFinding = true,
            result = null,
            errorMessage = errorMsg
        )
    }
}
