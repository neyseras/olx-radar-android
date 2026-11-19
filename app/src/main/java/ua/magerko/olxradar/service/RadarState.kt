package ua.magerko.olxradar.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WatchStatus(
    val lastSuccessAt: Long? = null,
    val lastError: String? = null,
    val lastHttpCode: Int? = null
)

data class ServiceStatus(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val checksCount: Long = 0,
    val lastCheckedAt: Long? = null,
    val perWatch: Map<Long, WatchStatus> = emptyMap()
)

object RadarState {
    private val _status = MutableStateFlow(ServiceStatus())
    val status: StateFlow<ServiceStatus> = _status.asStateFlow()

    fun update(transform: (ServiceStatus) -> ServiceStatus) {
        _status.update(transform)
    }

    fun setRunning(isRunning: Boolean) {
        _status.update { it.copy(isRunning = isRunning) }
    }

    fun setPaused(isPaused: Boolean) {
        _status.update { it.copy(isPaused = isPaused) }
    }

    fun recordSuccess(watchId: Long, timestamp: Long) {
        _status.update { current ->
            val updatedWatch = (current.perWatch[watchId] ?: WatchStatus()).copy(
                lastSuccessAt = timestamp,
                lastError = null,
                lastHttpCode = 200
            )
            current.copy(
                checksCount = current.checksCount + 1,
                lastCheckedAt = timestamp,
                perWatch = current.perWatch + (watchId to updatedWatch)
            )
        }
    }

    fun recordError(watchId: Long, error: String, httpCode: Int?) {
        _status.update { current ->
            val now = System.currentTimeMillis()
            val updatedWatch = (current.perWatch[watchId] ?: WatchStatus()).copy(
                lastError = error,
                lastHttpCode = httpCode
            )
            current.copy(
                checksCount = current.checksCount + 1,
                lastCheckedAt = now,
                perWatch = current.perWatch + (watchId to updatedWatch)
            )
        }
    }
}
