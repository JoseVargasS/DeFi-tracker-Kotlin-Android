package com.defitracker.app.presentation.crypto_list

import android.app.Application
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.defitracker.app.domain.model.AvailableCryptoPair
import com.defitracker.app.domain.model.CryptoPair
import com.defitracker.app.domain.repository.CryptoRepository
import com.defitracker.app.widget.CryptoWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CryptoListViewModel @Inject constructor(
    private val repository: CryptoRepository,
    private val pairOrderRepo: PairOrderRepository,
    application: Application
) : AndroidViewModel(application) {

    private fun logWidgetUpdateFailure(throwable: Throwable) {
        Log.w(TAG, "Widget update failed", throwable)
    }

    private val _state = mutableStateOf(CryptoListState())
    val state: State<CryptoListState> = _state

    private val _pairOrder = mutableStateOf<List<String>>(emptyList())
    val pairOrder: State<List<String>> = _pairOrder

    private var getPairsJob: Job? = null
    private var refreshJob: Job? = null
    private var isRefreshing = false
    // ponytail: simbolos por fuente con cache para no refetchear al alternar
    private val symbolsBySource = mutableMapOf<String, List<AvailableCryptoPair>>()

    init {
        getTrackedPairs()
        startPriceUpdates()
        loadAvailableSymbols()
        viewModelScope.launch {
            try {
                _pairOrder.value = pairOrderRepo.orderFlow.first()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    private fun loadAvailableSymbols(source: String = state.value.selectedSource) {
        symbolsBySource[source]?.let { cached ->
            _state.value = state.value.copy(availableSymbols = cached, symbolsError = "")
            return
        }
        viewModelScope.launch {
            try {
                val symbols = repository.getAvailableSymbols(source)
                symbolsBySource[source] = symbols
                // ponytail: ignora respuesta tardia si el usuario ya cambio de fuente
                if (state.value.selectedSource != source) return@launch
                _state.value = state.value.copy(
                    availableSymbols = symbols,
                    symbolsError = if (symbols.isEmpty()) "Could not load symbols. Check your connection." else ""
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (state.value.selectedSource != source) return@launch
                _state.value = state.value.copy(
                    symbolsError = "Could not load symbols: ${e.message}"
                )
            }
        }
    }

    fun selectSource(source: String) {
        if (state.value.selectedSource == source) return
        _state.value = state.value.copy(selectedSource = source)
        loadAvailableSymbols(source)
    }

    private fun getTrackedPairs() {
        getPairsJob?.cancel()
        getPairsJob = repository.getTrackedPairs()
            .onEach { pairs ->
                _state.value = state.value.copy(
                    pairs = pairs
                )
                refreshPrices()
                updateWidget()
            }
            .launchIn(viewModelScope)
    }

    private fun startPriceUpdates() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            var tick = 0
            while (true) {
                delay(PRICE_REFRESH_MS)
                refreshPrices()
                tick++
                if (tick % WIDGET_REFRESH_TICKS == 0) {
                    updateWidget()
                }
            }
        }
    }

    private suspend fun refreshPrices() {
        val pairs = state.value.pairs
        if (pairs.isEmpty() || isRefreshing) return

        isRefreshing = true
        try {
            val updatedPairs = coroutineScope {
                pairs.map { pair ->
                    async {
                        try {
                            val detail = repository.getPairDetail(pair.symbol, pair.source)
                            pair.copy(
                                price = detail.price,
                                priceChangePercent = detail.priceChangePercent,
                                isPositive = detail.isPositive
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            pair
                        }
                    }
                }.awaitAll()
            }
            _state.value = state.value.copy(pairs = updatedPairs)
        } finally {
            isRefreshing = false
        }
    }

    private suspend fun updateWidget() {
        try {
            CryptoWidget().updateAll(getApplication())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logWidgetUpdateFailure(e)
        }
    }

    fun onAddPair(symbol: String, baseAsset: String, quoteAsset: String, source: String) {
        viewModelScope.launch {
            repository.addTrackedPair(symbol, baseAsset, quoteAsset, source)
        }
    }

    fun onRemovePair(symbol: String) {
        viewModelScope.launch {
            repository.removeTrackedPair(symbol)
        }
    }

    fun retryLoadSymbols() {
        _state.value = state.value.copy(symbolsError = "")
        loadAvailableSymbols()
    }

    // ponytail: orden manual desde drag, se persiste tal cual
    fun savePairOrder(keys: List<String>) {
        _pairOrder.value = keys
        viewModelScope.launch {
            try {
                pairOrderRepo.save(keys)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    private companion object {
        const val TAG = "CryptoListVM"
        const val PRICE_REFRESH_MS = 5_000L
        const val WIDGET_REFRESH_TICKS = 12
    }
}

data class CryptoListState(
    val pairs: List<CryptoPair> = emptyList(),
    val availableSymbols: List<AvailableCryptoPair> = emptyList(),
    val selectedSource: String = "Binance",
    val isLoading: Boolean = false,
    val error: String = "",
    val symbolsError: String = ""
)
