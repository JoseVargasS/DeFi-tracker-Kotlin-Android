package com.defitracker.app.presentation.crypto_list

import android.app.Application
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.defitracker.app.domain.model.AvailableCryptoPair
import com.defitracker.app.domain.model.CryptoPair
import com.defitracker.app.data.repository.CryptoRepositoryImpl
import com.defitracker.app.presentation.crypto_detail.toDoubleValue
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
    private val repository: CryptoRepositoryImpl,
    private val pairOrderRepo: PairOrderRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _state = mutableStateOf(CryptoListState())
    val state: State<CryptoListState> = _state

    private val _pairOrder = mutableStateOf<List<String>>(emptyList())
    val pairOrder: State<List<String>> = _pairOrder

    private var getPairsJob: Job? = null
    private var refreshJob: Job? = null
    private var sparkJob: Job? = null
    private var isRefreshing = false
    // jalar-para-actualizar manual (la lista ya se refresca sola cada 30s, esto es extra)
    private val _manualRefreshing = mutableStateOf(false)
    val manualRefreshing: State<Boolean> = _manualRefreshing

    fun manualRefresh() {
        if (_manualRefreshing.value) return
        viewModelScope.launch {
            _manualRefreshing.value = true
            try {
                refreshPrices()
                // jalar reintenta MEXC aunque antes haya fallado
                symbolsCache = null
                fetchAvailableSymbols()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                _manualRefreshing.value = false
            }
        }
    }
    // cache de la lista combinada para no refetchear en cada recomposicion
    private var symbolsCache: List<AvailableCryptoPair>? = null

    // sparkline 24 cierres 1h + volumen 24h por par, key "symbol-source"
    private val _sparklines = mutableStateOf<Map<String, List<Double>>>(emptyMap())
    val sparklines: State<Map<String, List<Double>>> = _sparklines
    private val _volumes = mutableStateOf<Map<String, Double>>(emptyMap())
    val volumes: State<Map<String, Double>> = _volumes
    // precision de costumbre por par: 1 call diaria extra por par cada 5 min (cache 5 min del repo)
    private val _dayExt = mutableStateOf<Map<String, Boolean>>(emptyMap())
    val dayExt: State<Map<String, Boolean>> = _dayExt
    private val _sortMode = mutableStateOf(SortMode.DEFAULT)
    val sortMode: State<SortMode> = _sortMode

    fun pairKey(symbol: String, source: String) = "$symbol-$source"

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    init {
        getTrackedPairs()
        startPriceUpdates()
        startSparklineUpdates()
        startLivePrices()
        loadAvailableSymbols()
        viewModelScope.launch {
            try {
                _pairOrder.value = pairOrderRepo.orderFlow.first()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    private fun loadAvailableSymbols() {
        symbolsCache?.let { cached ->
            _state.value = state.value.copy(availableSymbols = cached, symbolsError = "")
            return
        }
        viewModelScope.launch { fetchAvailableSymbols() }
    }

    private suspend fun fetchAvailableSymbols() {
        val all = mutableListOf<AvailableCryptoPair>()
        var mexcOk = false
        // buscador unificado: spot + futuros + TV, cada entrada etiquetada con su fuente
        for (source in listOf("Binance", "MEXC")) {
            try {
                val syms = repository.getAvailableSymbols(source).map { it.copy(source = source) }
                all += syms
                if (source == "MEXC") mexcOk = syms.isNotEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
        // solo cachea la lista completa; si MEXC fallo, el proximo refresh reintenta
        if (mexcOk) symbolsCache = all
        _state.value = state.value.copy(
            availableSymbols = all,
            symbolsError = if (all.isEmpty()) "Could not load symbols. Check your connection." else "",
            mexcAvailable = mexcOk
        )
    }

    private fun getTrackedPairs() {
        getPairsJob?.cancel()
        getPairsJob = repository.getTrackedPairs()
            .onEach { pairs ->
                _state.value = state.value.copy(
                    pairs = pairs
                )
                refreshPrices()
            }
            .launchIn(viewModelScope)
    }

    private fun startPriceUpdates() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(PRICE_REFRESH_MS)
                refreshPrices()
            }
        }
    }

    // sockets en vivo: pisan precio/% al instante, el REST de abajo queda de respaldo
    private fun startLivePrices() {
        viewModelScope.launch {
            try {
                repository.observeLivePrices().collect { quotes ->
                    val pairs = state.value.pairs
                    if (pairs.isEmpty() || quotes.isEmpty()) return@collect
                    var changed = false
                    val updated = pairs.map { pair ->
                        val q = quotes[pairKey(pair.symbol, pair.source)]
                        if (q != null && (q.price != pair.price || q.priceChangePercent != pair.priceChangePercent)) {
                            changed = true
                            pair.copy(price = q.price, priceChangePercent = q.priceChangePercent, isPositive = q.isPositive)
                        } else pair
                    }
                    if (changed) _state.value = state.value.copy(pairs = updated)
                    val vols = quotes.mapValues { it.value.quoteVolume }
                    if (vols.isNotEmpty()) _volumes.value = _volumes.value + vols
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
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
                            // un fallo del REST llega como 0.00: se queda el precio que ya puso el socket
                            if (detail.price.toDoubleValue() <= 0.0) pair to null
                            else pair.copy(
                                price = detail.price,
                                priceChangePercent = detail.priceChangePercent,
                                isPositive = detail.isPositive
                            ) to (detail.quoteVolume.toDoubleValue())
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
        // referencia diaria de precision junto al sparkline (misma frecuencia, cacheada)
        try {
            coroutineScope {
                pairs.chunked(5).forEach { inner ->
                    inner.map { pair ->
                        async {
                            try {
                                pairKey(pair.symbol, pair.source) to
                                    repository.getRecentCloses(pair.symbol, pair.source, "1d", 45).any { it < 1.0 }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull().forEach { (k, v) -> _dayExt.value = _dayExt.value + (k to v) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }

    fun onAddPair(symbol: String, baseAsset: String, quoteAsset: String, source: String) {
        viewModelScope.launch {
            repository.addTrackedPair(symbol, baseAsset, quoteAsset, source)
            // sparkline al toque: el loop general pasa cada 5 min, el par nuevo no espera
            try {
                val closes = repository.getSparklineCloses(symbol, source, 30).takeLast(24)
                if (closes.size >= 2) {
                    _sparklines.value = _sparklines.value + (pairKey(symbol, source) to closes)
                }
                val ext = repository.getRecentCloses(symbol, source, "1d", 45).any { it < 1.0 }
                _dayExt.value = _dayExt.value + (pairKey(symbol, source) to ext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Sparkline fetch failed for $symbol-$source", e)
            }
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
        // respaldo REST cada 30s (los sockets ya cubren Binance/MEXC al segundo)
        const val PRICE_REFRESH_MS = 30_000L
        const val SPARK_REFRESH_MS = 300_000L
        const val SPARK_CHUNK_DELAY_MS = 60L
    }
}

enum class SortMode {
    DEFAULT, VOL_DESC, CHG_DESC
}

data class CryptoListState(
    val pairs: List<CryptoPair> = emptyList(),
    val availableSymbols: List<AvailableCryptoPair> = emptyList(),
    val isLoading: Boolean = false,
    val error: String = "",
    val symbolsError: String = "",
    // si MEXC no cargo, el buscador lo avisa en vez de callarse
    val mexcAvailable: Boolean = true
)
