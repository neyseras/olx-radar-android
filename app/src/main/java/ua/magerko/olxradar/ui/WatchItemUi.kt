package ua.magerko.olxradar.ui

import ua.magerko.olxradar.data.model.Watch

/** Модель радара для UI-слоя: сам радар + лайв-статистика по находкам. */
data class WatchItemUi(
    val watch: Watch,
    val findingsCount: Int = 0,
    val liveLastSuccessAt: Long? = null,
    val liveLastError: String? = null,
    val liveLastHttpCode: Int? = null
)
