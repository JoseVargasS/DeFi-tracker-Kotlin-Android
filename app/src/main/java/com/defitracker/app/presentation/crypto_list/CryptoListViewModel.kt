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
import kotlinx.coroutines.Dispatchers
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
    private var sparkJob: Job? = null
    private var isRefreshing = false
    // simbolos por fuente con cache para no refetchear al alternar
    private val symbolsBySource = mutableMapOf<String, List<AvailableCryptoPair>>()

    // sparkline 24 cierres 1h + volumen 24h por par, key "symbol-source"
    private val _sparklines = mutableStateOf<Map<String, List<Double>>>(emptyMap())
    val sparklines: State<Map<String, List<Double>>> = _sparklines
    private val _volumes = mutableStateOf<Map<String, Double>>(emptyMap())
    val volumes: State<Map<String, Double>> = _volumes
    private val _sortMode = mutableStateOf(SortMode.MANUAL)
    val sortMode: State<SortMode> = _sortMode

    fun pairKey(symbol: String, source: String) = "$symbol-$source"

    fun cycleSort() {
        _sortMode.value = when (_sortMode.value) {
            SortMode.MANUAL -> SortMode.VOL_DESC
            SortMode.VOL_DESC -> SortMode.VOL_ASC
            SortMode.VOL_ASC -> SortMode.CHG_DESC
            SortMode.CHG_DESC -> SortMode.CHG_ASC
            SortMode.CHG_ASC -> SortMode.MANUAL
        }
    }

    init {
        getTrackedPairs()
        startPriceUpdates()
        startSparklineUpdates()
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
                // ignora respuesta tardia si el usuario ya cambio de fuente
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
                            ) to (detail.quoteVolume.toDoubleOrNull() ?: 0.0)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            pair to null
                        }
                    }
                }.awaitAll()
            }
            _state.value = state.value.copy(pairs = updatedPairs.map { it.first })
            val vols = updatedPairs.mapNotNull { (pair, vol) ->
                vol?.let { pairKey(pair.symbol, pair.source) to it }
            }.toMap()
            if (vols.isNotEmpty()) _volumes.value = _volumes.value + vols
        } finally {
            isRefreshing = false
        }
    }

    // sparklines 1h en tandas de 3, arranque casi inmediato, conservador con el rate-limit
    private fun startSparklineUpdates() {
        sparkJob?.cancel()
        sparkJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500L)
            while (true) {
                try {
                    loadSparklines()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
                delay(SPARK_REFRESH_MS)
            }
        }
    }

    private suspend fun loadSparklines() {
        val pairs = state.value.pairs
        if (pairs.isEmpty()) return
        val out = mutableMapOf<String, List<Double>>()
        // ruta liviana (1 llamada por par): tandas de 5 para pintar casi al toque
        for (chunk in pairs.chunked(5)) {
            try {
                coroutineScope {
                    chunk.map { pair ->
                        async {
                            try {
                                val closes = repository.getSparklineCloses(pair.symbol, pair.source, 30)
                                    .takeLast(24)
                                if (closes.size >= 2) pairKey(pair.symbol, pair.source) to closes else null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull().forEach { (k, v) -> out[k] = v }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
            if (out.isNotEmpty()) _sparklines.value = _sparklines.value + out
            delay(SPARK_CHUNK_DELAY_MS)
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

    // orden manual desde drag, se persiste tal cual
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
        const val SPARK_REFRESH_MS = 300_000L
        const val SPARK_CHUNK_DELAY_MS = 60L
    }
}

enum class SortMode {
    MANUAL, VOL_DESC, VOL_ASC, CHG_DESC, CHG_ASC
}

data class CryptoListState(
    val pairs: List<CryptoPair> = emptyList(),
    val availableSymbols: List<AvailableCryptoPair> = emptyList(),
    val selectedSource: String = "Binance",
    val isLoading: Boolean = false,
    val error: String = "",
    val symbolsError: String = ""
)
