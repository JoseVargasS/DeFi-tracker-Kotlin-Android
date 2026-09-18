package com.defitracker.app.presentation.alerts

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.defitracker.app.alerts.DivMonitorPrefs
import com.defitracker.app.alerts.DivScanService
import com.defitracker.app.data.local.DivAlertDao
import com.defitracker.app.data.local.DivAlertEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertsState(
    val alerts: List<DivAlertEntity> = emptyList(),
    val unread: Int = 0,
    val monitoring: Boolean = true,
    val intervals: Set<String> = setOf("5m", "15m", "30m", "1h")
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val dao: DivAlertDao,
    private val prefs: DivMonitorPrefs,
    application: Application
) : AndroidViewModel(application) {

    private val _state = mutableStateOf(AlertsState())
    val state: State<AlertsState> = _state

    init {
        dao.observeAlerts()
            .onEach { list -> _state.value = state.value.copy(alerts = list) }
            .launchIn(viewModelScope)
        dao.observeUnreadCount()
            .onEach { n -> _state.value = state.value.copy(unread = n) }
            .launchIn(viewModelScope)
        prefs.configFlow
            .onEach { c -> _state.value = state.value.copy(monitoring = c.enabled, intervals = c.intervals) }
            .launchIn(viewModelScope)
    }

    fun markAllSeen() {
        viewModelScope.launch {
            try {
                dao.markAllSeen()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    fun markSeenAndOpen(alert: DivAlertEntity, onOpen: (DivAlertEntity) -> Unit) {
        viewModelScope.launch {
            try {
                dao.markSeen(alert.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
        onOpen(alert)
    }

    fun toggleMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            try {
                prefs.setEnabled(enabled)
                if (enabled) DivScanService.start(getApplication())
                else DivScanService.stop(getApplication())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    fun toggleInterval(tf: String) {
        val current = state.value.intervals.toMutableSet()
        if (tf in current) {
            if (current.size <= 1) return // minimo 1 TF o el worker no tiene que escanear
            current.remove(tf)
        } else {
            current.add(tf)
        }
        viewModelScope.launch {
            try {
                prefs.setIntervals(current)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }
}
