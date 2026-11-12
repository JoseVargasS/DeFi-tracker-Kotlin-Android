package com.defitracker.app.presentation.crypto_list

import android.app.Application
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.defitracker.app.domain.model.AvailableCryptoPair
import com.defitracker.app.domain.model.CryptoPair
import com.defitracker.app.domain.model.PairDetail
import com.defitracker.app.data.repository.CryptoRepositoryImpl
import com.defitracker.app.core.Brokers
import com.defitracker.app.presentation.crypto_detail.toDoubleValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    // un solo refresco REST a la vez: con un flag booleano el segundo se perdia
    private val refreshMutex = Mutex()
    // hora del ultimo tick de socket por par: decide si el REST de respaldo hace falta
    private val lastTickAt = HashMap<String, Long>()
    // jalar-para-actualizar manual (la lista ya se refresca sola cada 30s, esto es extra)
    private val _manualRefreshing = mutableStateOf(false)
    val manualRefreshing: State<Boolean> = _manualRefreshing

    fun manualRefresh() {
        if (_manualRefreshing.value) return
        viewModelScope.launch {
            _manualRefreshing.value = true
            try {
                refreshPrices(force = true)
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

    fun pairKey(symbol: String, source: String) = trackedKey(symbol, source)

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
        val failed = mutableListOf<String>()
        // buscador unificado: cada entrada etiquetada con su fuente
        for (source in listOf(Brokers.BINANCE, Brokers.MEXC, Brokers.BITUNIX, Brokers.BINGX)) {
            try {
                val syms = repository.getAvailableSymbols(source).map { it.copy(source = source) }
                all += syms
                if (syms.isEmpty()) failed += source
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed += source
            }
        }
        // solo cachea la lista completa: si una fuente fallo, el proximo refresh reintenta
        if (failed.isEmpty()) symbolsCache = all
        _state.value = state.value.copy(
            availableSymbols = all,
            symbolsError = if (all.isEmpty()) "Could not load symbols. Check your connection." else "",
            unavailableSources = failed
        )
    }

    private fun getTrackedPairs() {
        getPairsJob?.cancel()
        // par ya visto: solo los nuevos disparan REST, una baja no pide nada
        var known = emptySet<String>()
        getPairsJob = repository.getTrackedPairs()
            .onEach { dbPairs ->
                val keys = dbPairs.map { trackedKey(it.symbol, it.source) }.toSet()
                val fresh = dbPairs.filter { trackedKey(it.symbol, it.source) !in known }
                known = keys
                // la DB devuelve todo con precio 0.00: se conserva el precio vivo
                val merged = mergeTrackedPairs(dbPairs, state.value.pairs)
                _state.value = state.value.copy(
                    pairs = merged
                )
                if (fresh.isNotEmpty()) refreshPrices(only = fresh)
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
                var lastPublishAt = 0L
                repository.observeLivePrices()
                    .conflate()
                    .collect { quotes ->
                        val pairs = state.value.pairs
                        if (pairs.isEmpty() || quotes.isEmpty()) return@collect
                        val now = System.currentTimeMillis()
                        val updates = HashMap<String, CryptoPair>(pairs.size)
                        for (pair in pairs) {
                            val key = trackedKey(pair.symbol, pair.source)
                            val q = quotes[key] ?: continue
                            // el tick se anota siempre, aunque el pintado se salte por el tope
                            lastTickAt[key] = now
                            updates[key] = pair.copy(
                                price = q.price,
                                priceChangePercent = q.priceChangePercent,
                                isPositive = q.isPositive
                            )
                        }
                        val updated = applyPrices(pairs, updates)
                        val changed = updated !== pairs

                        val currentVols = _volumes.value
                        var volsChanged = false
                        val newVols = HashMap(currentVols)
                        for (p in pairs) {
                            val k = trackedKey(p.symbol, p.source)
                            val qv = quotes[k]?.quoteVolume
                            if (qv != null && qv > 0.0 && currentVols[k] != qv) {
                                newVols[k] = qv
                                volsChanged = true
                            }
                        }
                        if (!changed && !volsChanged) return@collect
                        // el socket empuja mas rapido de lo que se ve: tope de 4 actualizaciones
                        // por segundo. Si se salta esta, el siguiente push trae el dato mas nuevo
                        if (now - lastPublishAt < LIVE_PUBLISH_MS) return@collect
                        lastPublishAt = now
                        if (changed) _state.value = state.value.copy(pairs = updated)
                        if (volsChanged) _volumes.value = newVols
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    // respaldo REST: por defecto solo lo que el socket no cubre (TradingView, pares sin
    // tick fresco y pares que siguen en 0.00). force = refresca todo, only = solo esos.
    private suspend fun refreshPrices(only: List<CryptoPair>? = null, force: Boolean = false) {
        refreshMutex.withLock {
            val current = state.value.pairs
            if (current.isEmpty()) return@withLock
            val targets = when {
                only != null -> {
                    val live = current.associateBy { trackedKey(it.symbol, it.source) }
                    only.filter { live.containsKey(trackedKey(it.symbol, it.source)) }
                }
                force -> current
                else -> selectForRest(current, lastTickAt, System.currentTimeMillis(), STALE_TICK_MS)
            }
            if (targets.isEmpty()) return@withLock

            val results: List<Pair<CryptoPair, PairDetail?>> = coroutineScope {
                targets.map { pair ->
                    async {
                        try {
                            pair to repository.getPairDetail(pair.symbol, pair.source)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            pair to null
                        }
                    }
                }.awaitAll()
            }

            val updates = HashMap<String, CryptoPair>(results.size)
            val vols = HashMap<String, Double>(results.size)
            for ((pair, detail) in results) {
                // un fallo del REST llega como 0.00: se queda el precio que ya puso el socket
                if (detail == null || detail.price.toDoubleValue() <= 0.0) continue
                val key = trackedKey(pair.symbol, pair.source)
                updates[key] = pair.copy(
                    price = detail.price,
                    priceChangePercent = detail.priceChangePercent,
                    isPositive = detail.isPositive
                )
                vols[key] = detail.quoteVolume.toDoubleValue()
            }
            if (updates.isEmpty()) return@withLock

            // se aplica sobre la lista ACTUAL: un par borrado mientras volaba el REST no vuelve
            val latest = state.value.pairs
            val updated = applyPrices(latest, updates)
            if (updated !== latest) _state.value = state.value.copy(pairs = updated)
            if (vols.isNotEmpty()) _volumes.value = _volumes.value + vols
        }
    }

    // sparklines 1h en tandas de 5, arranque casi inmediato, conservador con el rate-limit
    private fun startSparklineUpdates() {
        sparkJob?.cancel()
        sparkJob = viewModelScope.launch(Dispatchers.IO) {
            var missing: Set<String>? = null
            var misses = 0
            while (true) {
                // el estado de Compose se lee en main: desde un worker se puede leer un snapshot
                // que aun no se aplico y tira IllegalStateException en el arranque
                val pairs = withContext(Dispatchers.Main) { state.value.pairs }
                val still = try {
                    loadSparklines(pairs, missing)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptySet()
                }
                // lista vacia (arranque en frio) o par sin datos: reintento corto y solo de lo
                // que falta. agotados los intentos vuelve a los 5 min pa' no golpear la API
                val incomplete = still.isNotEmpty() || pairs.isEmpty()
                misses = if (incomplete) misses + 1 else 0
                val fast = incomplete && misses <= SPARK_FAST_RETRIES
                missing = if (fast) still.takeIf { it.isNotEmpty() } else null
                delay(if (fast) SPARK_RETRY_MS else SPARK_REFRESH_MS)
            }
        }
    }

    // devuelve las keys que quedaron sin sparkline; `only` limita el reintento a los pares que
    // fallaron, sin volver a pedir los que ya estan pintados
    private suspend fun loadSparklines(pairsAll: List<CryptoPair>, only: Set<String>?): Set<String> {
        val pairs = pairsAll.filter { only == null || pairKey(it.symbol, it.source) in only }
        if (pairs.isEmpty()) return emptySet()
        val out = mutableMapOf<String, List<Double>>()
        // ruta liviana (1 llamada por par): tandas de 5 para pintar casi al toque
        for (chunk in pairs.chunked(5)) {
            try {
                coroutineScope {
                    chunk.map { pair ->
                        async {
                            val key = pairKey(pair.symbol, pair.source)
                            try {
                                val closes = repository.getSparklineCloses(pair.symbol, pair.source, 30)
                                    .takeLast(24)
                                key to closes.takeIf { it.size >= 2 }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                key to null
                            }
                        }
                    }.awaitAll().forEach { (k, v) -> if (v != null) out[k] = v }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
            if (out.isNotEmpty()) _sparklines.value = _sparklines.value + out
            delay(SPARK_CHUNK_DELAY_MS)
        }
        // referencia diaria de precision junto al sparkline (misma frecuencia, cacheada).
        // un fetch vacio no pisa la banda conocida: solo se guarda lo que trajo datos
        try {
            coroutineScope {
                pairs.chunked(5).forEach { inner ->
                    inner.map { pair ->
                        async {
                            try {
                                val closes = repository.getRecentCloses(pair.symbol, pair.source, "1d", 45)
                                if (closes.isEmpty()) null else
                                    pairKey(pair.symbol, pair.source) to closes.any { it < 1.0 }
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
        return pairs.map { pairKey(it.symbol, it.source) }.toSet() - out.keys
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

    // eliminacion optimista: el par sale de la UI al instante y el DELETE va en segundo
    // plano. Si el DELETE falla, el par vuelve (no se pierde en silencio).
    fun onRemovePair(symbol: String, source: String) {
        val key = trackedKey(symbol, source)
        val current = state.value.pairs
        val removed = current.firstOrNull { trackedKey(it.symbol, it.source) == key }
        if (removed != null) {
            _state.value = state.value.copy(
                pairs = current.filter { trackedKey(it.symbol, it.source) != key }
            )
            lastTickAt.remove(key)
            _volumes.value = _volumes.value - key
            _sparklines.value = _sparklines.value - key
        }
        viewModelScope.launch {
            try {
                repository.removeTrackedPair(symbol, source)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                val latest = state.value.pairs
                if (removed != null && latest.none { trackedKey(it.symbol, it.source) == key }) {
                    _state.value = state.value.copy(pairs = latest + removed)
                }
            }
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
        // tope de actualizaciones visuales del socket (4 por segundo)
        const val LIVE_PUBLISH_MS = 250L
        const val SPARK_REFRESH_MS = 300_000L
        // reintento corto cuando aun falta sparkline (arranque en frio, par con error)
        const val SPARK_RETRY_MS = 3_000L
        const val SPARK_FAST_RETRIES = 3
        const val SPARK_CHUNK_DELAY_MS = 60L
        // sin tick de socket por mas de esto, el REST vuelve a traer ese par.
        // alineado con PRICE_REFRESH_MS: un ciclo entero sin tick del socket ya es hueco
        const val STALE_TICK_MS = 30_000L
    }
}

// key de par: symbol + source (el mismo symbol existe en varios brokers)
internal fun trackedKey(symbol: String, source: String) = "$symbol-$source"

// aplica precios a la lista actual por key. Un par borrado mientras volaba el REST no
// vuelve a aparecer y uno nuevo no se cuela hasta que Room lo confirme.
internal fun applyPrices(current: List<CryptoPair>, updates: Map<String, CryptoPair>): List<CryptoPair> {
    if (updates.isEmpty()) return current
    var changed = false
    val out = current.map { pair ->
        val u = updates[trackedKey(pair.symbol, pair.source)] ?: return@map pair
        if (u.price == pair.price && u.priceChangePercent == pair.priceChangePercent && u.isPositive == pair.isPositive) {
            pair
        } else {
            changed = true
            pair.copy(
                price = u.price,
                priceChangePercent = u.priceChangePercent,
                isPositive = u.isPositive
            )
        }
    }
    return if (changed) out else current
}

// Room devuelve los pares con precio 0.00. Si se pisa ese 0.00, toda la lista parpadea
// en cada alta o baja: se conserva el precio vivo que ya hay para esa key.
internal fun mergeTrackedPairs(fromDb: List<CryptoPair>, current: List<CryptoPair>): List<CryptoPair> {
    if (fromDb.isEmpty()) return fromDb
    val known = current.associateBy { trackedKey(it.symbol, it.source) }
    return fromDb.map { pair ->
        val old = known[trackedKey(pair.symbol, pair.source)]
        if (old != null && old.price.toDoubleValue() > 0.0) {
            pair.copy(
                price = old.price,
                priceChangePercent = old.priceChangePercent,
                isPositive = old.isPositive
            )
        } else {
            pair
        }
    }
}

// el REST de respaldo solo va a lo que el socket no cubre: TradingView, pares sin tick
// fresco y pares que siguen en 0.00
internal fun selectForRest(
    pairs: List<CryptoPair>,
    lastTickAt: Map<String, Long>,
    nowMs: Long,
    staleAfterMs: Long
): List<CryptoPair> = pairs.filter { pair ->
    val last = lastTickAt[trackedKey(pair.symbol, pair.source)]
    pair.price.toDoubleValue() <= 0.0 || last == null || nowMs - last > staleAfterMs
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
    // fuentes que no cargaron: el buscador lo avisa en vez de callarse
    val unavailableSources: List<String> = emptyList()
)
