package com.defitracker.app.presentation.crypto_detail

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.defitracker.app.domain.model.PairDetail
import com.defitracker.app.domain.repository.CryptoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

@HiltViewModel
class CryptoDetailViewModel @Inject constructor(
    private val repository: CryptoRepository,
    private val prefsRepo: IndicatorPrefsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private fun logNonFatal(context: String, throwable: Throwable) {
        Log.w(TAG, context, throwable)
    }

    private val _state = mutableStateOf(CryptoDetailState())
    val state: State<CryptoDetailState> = _state

    private val _prefs = mutableStateOf(IndicatorPrefs.DEFAULT)
    val prefs: State<IndicatorPrefs> = _prefs

    // varios fibos por simbolo, cada uno con su estilo+estado propio
    private val _fibOverlays = mutableStateOf<List<FibOverlay>>(emptyList())
    val fibOverlays: State<List<FibOverlay>> = _fibOverlays
    private val _selectedFibId = mutableStateOf<String?>(null)
    val selectedFibId: State<String?> = _selectedFibId

    // dibujos OKX por simbolo, mismo esquema que los fibos
    private val _drawOverlays = mutableStateOf<List<DrawOverlay>>(emptyList())
    val drawOverlays: State<List<DrawOverlay>> = _drawOverlays
    private val _selectedDrawId = mutableStateOf<String?>(null)
    val selectedDrawId: State<String?> = _selectedDrawId

    private val symbol: String = checkNotNull(savedStateHandle["symbol"])
    private val source: String = checkNotNull(savedStateHandle["source"])
    // deep-link desde noti/sheet con el TF de la alerta
    private val initialInterval: String = savedStateHandle.get<String>("interval")?.trim().orEmpty()
    // deep-link desde noti de señal: abre el sheet de analisis al entrar
    val openAnalysisInitially: Boolean = savedStateHandle.get<String>("analysis") == "1"

    // pulso del momento para el boton fantasma
    private val _analysis = mutableStateOf<PulseAnalysis?>(null)
    val analysis: State<PulseAnalysis?> = _analysis
    private var lastAnalysisAt = 0L
    private var tfClosesCache = mapOf<String, List<Double>>()
    private var tfClosesAt = 0L

    fun refreshAnalysis(force: Boolean = false) {
        val current = _state.value
        if (current.candles.size < 60) return
        val now = System.currentTimeMillis()
        if (!force && now - lastAnalysisAt < ANALYSIS_THROTTLE_MS) return
        lastAnalysisAt = now
        val interval = current.selectedInterval
        val candles = current.candles
        viewModelScope.launch {
            try {
                val byTf = fetchPulseTfs(interval, force)
                val result = withContext(Dispatchers.Default) {
                    analyzePulse(candles, interval, byTf)
                }
                // si cambiaste de TF o entraron velas, este analisis ya no sirve
                if (_state.value.selectedInterval == interval && _state.value.candles.size == candles.size) {
                    result?.let { _analysis.value = it }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    // cierres 15m/1h/4h en paralelo, 1 llamada liviana por TF; force salta el cache
    // el TF del chart no se trae: la matriz reusa sus velas (cero red)
    private suspend fun fetchPulseTfs(chartInterval: String, force: Boolean): Map<String, List<Double>> =
        withContext(Dispatchers.IO) {
            val needed = PULSE_MA_TFS.filter { it != chartInterval }
            val now = System.currentTimeMillis()
            if (!force && needed.all { (tfClosesCache[it]?.size ?: 0) >= 200 } && now - tfClosesAt < ANALYSIS_TF_TTL_MS) {
                return@withContext tfClosesCache
            }
            try {
                val out = coroutineScope {
                    needed.map { tf ->
                        async {
                            tf to try {
                                repository.getRecentCloses(symbol, source, tf, 260)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }.awaitAll().toMap()
                }.filterValues { it.size >= 200 }
                if (out.size == needed.size) {
                    tfClosesCache = tfClosesCache + out
                    tfClosesAt = now
                }
                tfClosesCache + out
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                tfClosesCache
            }
        }

    private var refreshJob: Job? = null
    private var chartJob: Job? = null
    // el loop es secuencial, el flag solo evita solapar tail con full load
    private var tailSyncing = false
    // si el tail no trae vela nueva 3 cierres seguidos, full load de respaldo
    private var noNewCandleStreak = 0

    init {
        _state.value = state.value.copy(symbol = symbol, source = source)
        // si viene de una alerta, abre directo en ese TF
        val startInterval = initialInterval.ifEmpty { DEFAULT_CHART_INTERVAL }
        _state.value = state.value.copy(selectedInterval = startInterval)
        loadDetail()
        startUpdates()
        viewModelScope.launch {
            try {
                _prefs.value = prefsRepo.prefsFlow.first()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
            // carga los fibos guardados de este simbolo sin bloquear el chart
            try {
                val stored = prefsRepo.fibOverlaysFlow(symbol).first()
                _fibOverlays.value = stored.ifEmpty {
                    prefsRepo.migrateLegacyFib(symbol) ?: emptyList()
                }
                _selectedFibId.value = _fibOverlays.value.lastOrNull()?.id
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
            // dibujos del simbolo, si hay uno visible se selecciona
            try {
                _drawOverlays.value = prefsRepo.drawOverlaysFlow(symbol).first()
                if (_drawOverlays.value.any { !it.hidden }) {
                    _selectedDrawId.value = _drawOverlays.value.lastOrNull { !it.hidden }?.id
                    _selectedFibId.value = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
            // el chart espera a los prefs: si no, el primer pintado usaria MAs default
            loadChartData(startInterval, force = true)
        }
    }

    private fun updatePrefs(transform: (IndicatorPrefs) -> IndicatorPrefs) {
        val next = transform(_prefs.value)
        _prefs.value = next
        viewModelScope.launch {
            try {
                prefsRepo.save(next)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    fun toggleBB() = updatePrefs { it.copy(bbVisible = !it.bbVisible) }
    fun toggleProfile() = updatePrefs { it.copy(profileVisible = !it.profileVisible) }
    fun toggleVolumeSub() = updatePrefs { it.copy(volumeVisible = !it.volumeVisible) }
    fun toggleStochSub() = updatePrefs { it.copy(stochVisible = !it.stochVisible) }
    fun toggleRsiSub() = updatePrefs { it.copy(rsiVisible = !it.rsiVisible) }
    fun toggleRsiDiv() = updatePrefs { it.copy(rsiDivVisible = !it.rsiDivVisible) }
    fun toggleRsiDivHidden() = updatePrefs { it.copy(rsiDivHidden = !it.rsiDivHidden) }
    fun toggleMA(period: Int) = updatePrefs { cur ->
        cur.copy(mas = cur.mas.map { ma -> if (ma.period == period) ma.copy(visible = !ma.visible) else ma })
    }
    fun setMAColor(period: Int, colorHex: String) = updatePrefs { cur ->
        cur.copy(mas = cur.mas.map { ma -> if (ma.period == period) ma.copy(colorHex = colorHex) else ma })
    }
    fun setMAWidth(period: Int, width: Float) = updatePrefs { cur ->
        cur.copy(mas = cur.mas.map { ma -> if (ma.period == period) ma.copy(width = width) else ma })
    }

    // MAs por id (SMA/EMA, periodo, color, grosor, TF)
    fun toggleMAById(id: String) {
        updatePrefs { cur ->
            cur.copy(mas = cur.mas.map { ma -> if (ma.id == id) ma.copy(visible = !ma.visible) else ma })
        }
        // la ganadora del dedup puede cambiar al mostrar/ocultar
        recomputeMaLinesFromCache()
    }
    fun setMAColorById(id: String, colorHex: String) = updatePrefs { cur ->
        cur.copy(mas = cur.mas.map { ma -> if (ma.id == id) ma.copy(colorHex = colorHex) else ma })
    }
    fun setMAWidthById(id: String, width: Float) = updatePrefs { cur ->
        cur.copy(mas = cur.mas.map { ma -> if (ma.id == id) ma.copy(width = width.coerceIn(0.5f, 3f)) else ma })
    }
    fun setMAType(id: String, type: MaType) {
        updatePrefs { cur ->
            cur.copy(mas = cur.mas.map { ma -> if (ma.id == id) ma.copy(type = type) else ma })
        }
        recomputeMaLinesFromCache()
    }
    fun setMAPeriod(id: String, period: Int) {
        updatePrefs { cur ->
            cur.copy(mas = cur.mas.map { ma -> if (ma.id == id) ma.copy(period = period.coerceIn(2, 500)) else ma })
        }
        recomputeMaLinesFromCache()
    }
    fun setMATimeframe(id: String, tf: String) {
        if (tf != "chart" && tf !in IndicatorPrefs.MA_TFS) return
        updatePrefs { cur ->
            cur.copy(mas = cur.mas.map { ma -> if (ma.id == id) ma.copy(timeframe = tf) else ma })
        }
        refreshExtraMas(force = true)
    }
    fun addMA() = updatePrefs { cur ->
        if (cur.mas.size >= IndicatorPrefs.MAX_MAS) return@updatePrefs cur
        val usedPeriods = cur.mas.map { it.period }.toSet()
        val period = listOf(9, 21, 50, 200, 10, 30, 100, 55).firstOrNull { it !in usedPeriods } ?: 50
        val usedColors = cur.mas.map { it.colorHex }.toSet()
        val color = IndicatorPrefs.ADD_COLORS.firstOrNull { it !in usedColors } ?: "#FFFFFF"
        val id = "ma${period}_${System.currentTimeMillis() % 100000}"
        cur.copy(mas = cur.mas + MaConfig(id, period, MaType.SMA, "chart", color, 1.2f, true))
    }
    fun deleteMA(id: String) = updatePrefs { cur ->
        if (cur.mas.size <= 1) return@updatePrefs cur
        cur.copy(mas = cur.mas.filterNot { it.id == id })
    }

    // velas cacheadas por TF para MAs de otra temporalidad (plan A, respeta source)
    private val extraTfCandles = mutableMapOf<String, List<CandleData>>()

    private fun intervalForSource(tf: String): String =
        if (source == "MEXC") tf else tf.toBinanceInterval()

    // trae las velas de los TFs que piden las MAs visibles, luego re-alinea sin tocar zoom
    fun refreshExtraMas(force: Boolean = false) {
        val chartInterval = _state.value.selectedInterval
        val needed = _prefs.value.mas
            .filter { it.visible && it.timeframe != "chart" && it.timeframe != chartInterval }
            .map { it.timeframe }.toSet()
        if (needed.isEmpty()) return
        viewModelScope.launch {
            try {
                var changed = false
                for (tf in needed) {
                    try {
                        val candles = fetchExtraCandles(tf, force)
                        if (candles.isNotEmpty()) {
                            extraTfCandles[tf] = candles
                            changed = true
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logNonFatal("Extra TF load failed for $symbol/$tf", e)
                    }
                }
                if (changed) recomputeMaLinesFromCache()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    private suspend fun fetchExtraCandles(tf: String, force: Boolean): List<CandleData> {
        val rows = repository.getKlines(symbol, intervalForSource(tf), source, forceRefresh = force)
        if (rows.isEmpty()) return emptyList()
        return withContext(Dispatchers.Default) {
            rows.toCandles().let { list ->
                if (source == "MEXC") list else list.aggregateForInterval(tf)
            }
        }
    }

    // re-alinea MAs con el cache actual, sin red y sin mover viewport
    private fun recomputeMaLinesFromCache() {
        val current = _state.value
        if (current.candles.isEmpty()) return
        val mas = _prefs.value.mas
        val extras = extraTfCandles.toMap()
        viewModelScope.launch {
            try {
                val aligned = withContext(Dispatchers.Default) {
                    current.candles.computeMaLines(current.selectedInterval, mas, extras)
                }
                if (_state.value.candles.size == current.candles.size) {
                    _state.value = _state.value.copy(maLines = aligned)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    // ─── FIBO (multi-overlay) ───
    private fun persistFibs() {
        val snapshot = _fibOverlays.value
        viewModelScope.launch {
            try {
                prefsRepo.saveFibOverlays(symbol, snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    // preview durante el drag sin spamear DataStore, se persiste al soltar
    fun setFibLive(id: String, overlay: FibOverlay) {
        _fibOverlays.value = _fibOverlays.value.map { if (it.id == id) overlay else it }
    }

    fun commitFib(id: String) {
        persistFibs()
    }

    fun addFib(start: FibAnchor, end: FibAnchor): FibOverlay? {
        if (_fibOverlays.value.size >= MAX_FIBS_PER_SYMBOL) return null
        // cada fibo nuevo hereda la ultima config (color, grosor, niveles)
        val def = _prefs.value.fib
        val overlay = FibOverlay(
            id = java.util.UUID.randomUUID().toString(),
            start = start,
            end = end,
            colorHex = def.colorHex,
            width = def.width,
            enabledLevels = def.enabledLevels
        )
        _fibOverlays.value = _fibOverlays.value + overlay
        _selectedFibId.value = overlay.id
        persistFibs()
        return overlay
    }

    fun deleteFib(id: String) {
        _fibOverlays.value = _fibOverlays.value.filterNot { it.id == id }
        if (_selectedFibId.value == id) _selectedFibId.value = null
        persistFibs()
    }

    fun selectFib(id: String?) {
        _selectedFibId.value = id
        if (id != null) _selectedDrawId.value = null
    }

    fun selectedFib(): FibOverlay? = _fibOverlays.value.firstOrNull { it.id == _selectedFibId.value }

    private fun updateFib(id: String, transform: (FibOverlay) -> FibOverlay, persist: Boolean = true) {
        _fibOverlays.value = _fibOverlays.value.map { if (it.id == id) transform(it) else it }
        if (persist) persistFibs()
    }

    fun moveFibAnchor(id: String, isStart: Boolean, anchor: FibAnchor, persist: Boolean = true) {
        updateFib(id, { if (isStart) it.copy(start = anchor) else it.copy(end = anchor) }, persist)
    }

    fun moveFibWhole(id: String, start: FibAnchor, end: FibAnchor, persist: Boolean = true) {
        updateFib(id, { it.copy(start = start, end = end) }, persist)
    }

    fun setFibColor(id: String, hex: String) {
        updateFib(id, { it.copy(colorHex = hex) })
        updatePrefs { it.copy(fib = it.fib.copy(colorHex = hex)) }
    }
    fun setFibWidth(id: String, w: Float) {
        val cw = w.coerceIn(0.5f, 3f)
        updateFib(id, { it.copy(width = cw) })
        updatePrefs { it.copy(fib = it.fib.copy(width = cw)) }
    }
    fun toggleFibLevel(id: String, ratio: Float) = updateFib(id, {
        val next = it.enabledLevels.toMutableSet()
        if (ratio in next) next.remove(ratio) else next.add(ratio)
        // nunca dejes el fibo sin niveles, vuelve al default
        val levels = next.ifEmpty { DEFAULT_FIB_LEVELS.toSet() }
        updatePrefs { p -> p.copy(fib = p.fib.copy(enabledLevels = levels)) }
        it.copy(enabledLevels = levels)
    })
    fun toggleFibHidden(id: String) = updateFib(id, { it.copy(hidden = !it.hidden) })
    fun toggleFibLocked(id: String) = updateFib(id, { it.copy(locked = !it.locked) })

    // ocultar/mostrar todos, si hay seleccion se maneja afuera
    fun toggleAllFibsHidden() {
        val anyVisible = _fibOverlays.value.any { !it.hidden }
        _fibOverlays.value = _fibOverlays.value.map { it.copy(hidden = anyVisible) }
        persistFibs()
    }

    fun deleteAllFibs() {
        _fibOverlays.value = emptyList()
        _selectedFibId.value = null
        persistFibs()
    }

    // ─── DIBUJOS ───
    private fun persistDraws() {
        val snapshot = _drawOverlays.value
        viewModelScope.launch {
            try {
                prefsRepo.saveDrawOverlays(symbol, snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    fun setDrawLive(id: String, overlay: DrawOverlay) {
        _drawOverlays.value = _drawOverlays.value.map { if (it.id == id) overlay else it }
    }

    fun commitDraw(id: String) {
        persistDraws()
    }

    fun addDraw(kind: DrawKind, start: FibAnchor, end: FibAnchor): DrawOverlay? {
        if (_drawOverlays.value.size >= MAX_DRAWS_PER_SYMBOL) return null
        val overlay = DrawOverlay(
            id = java.util.UUID.randomUUID().toString(),
            kind = kind,
            start = start,
            end = end
        )
        _drawOverlays.value = _drawOverlays.value + overlay
        _selectedDrawId.value = overlay.id
        _selectedFibId.value = null
        persistDraws()
        return overlay
    }

    fun deleteDraw(id: String) {
        _drawOverlays.value = _drawOverlays.value.filterNot { it.id == id }
        if (_selectedDrawId.value == id) _selectedDrawId.value = null
        persistDraws()
    }

    fun selectDraw(id: String?) {
        _selectedDrawId.value = id
        if (id != null) _selectedFibId.value = null
    }

    private fun updateDraw(id: String, transform: (DrawOverlay) -> DrawOverlay) {
        _drawOverlays.value = _drawOverlays.value.map { if (it.id == id) transform(it) else it }
        persistDraws()
    }

    fun setDrawColor(id: String, hex: String) = updateDraw(id, { it.copy(colorHex = hex) })
    fun setDrawWidth(id: String, w: Float) = updateDraw(id, { it.copy(width = w.coerceIn(0.5f, 3f)) })
    fun toggleDrawHidden(id: String) = updateDraw(id, { it.copy(hidden = !it.hidden) })
    fun toggleDrawLocked(id: String) = updateDraw(id, { it.copy(locked = !it.locked) })

    // ocultar/mostrar todos los dibujos
    fun toggleAllDrawsHidden() {
        val anyVisible = _drawOverlays.value.any { !it.hidden }
        _drawOverlays.value = _drawOverlays.value.map { it.copy(hidden = anyVisible) }
        persistDraws()
    }

    fun deleteAllDraws() {
        _drawOverlays.value = emptyList()
        _selectedDrawId.value = null
        persistDraws()
    }

    // ─── SMC ───
    fun toggleSmcStructure() = updatePrefs { it.copy(smcStructure = !it.smcStructure) }
    fun toggleSmcOrderBlocks() = updatePrefs { it.copy(smcOrderBlocks = !it.smcOrderBlocks) }
    fun toggleSmcFvg() = updatePrefs { it.copy(smcFvg = !it.smcFvg) }
    fun toggleSmcPremium() = updatePrefs { it.copy(smcPremium = !it.smcPremium) }
    fun toggleSmcEqhl() = updatePrefs { it.copy(smcEqhl = !it.smcEqhl) }
    fun toggleSmcLiquidity() = updatePrefs { it.copy(smcLiquidity = !it.smcLiquidity) }

    private fun loadDetail() {
        viewModelScope.launch {
            _state.value = state.value.copy(isLoading = true)
            try {
                val detail = repository.getPairDetail(symbol, source)
                _state.value = state.value.copy(detail = detail, isLoading = false)
            } catch (e: Exception) {
                _state.value = state.value.copy(error = e.message ?: "Error", isLoading = false)
            }
        }
    }

    private fun startUpdates() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(DETAIL_REFRESH_MS)
                try {
                    val detail = repository.getPairDetail(symbol, source)
                    val currentPrice = detail.price.toDoubleOrNull() ?: 0.0

                    // al cerrar la vela se mergea la cola fresca, nunca full reload
                    val candlesNow = _state.value.candles
                    val lastNow = candlesNow.lastOrNull()
                    val nowMs = System.currentTimeMillis()
                    val durMs = candleDurationMs(_state.value.selectedInterval)
                    val closed = lastNow != null && durMs > 0L && nowMs >= lastNow.time + durMs
                    if (closed && !tailSyncing) {
                        tailSyncing = true
                        try {
                            syncTail()
                        } finally {
                            tailSyncing = false
                        }
                    }
                    
                    if (currentPrice > 0.0) {
                        val tickInterval = _state.value.selectedInterval
                        val updatedCandles = _state.value.candles.toMutableList()
                        if (updatedCandles.isNotEmpty()) {
                            val lastCandle = updatedCandles.last()
                            val newLastCandle = lastCandle.copy(
                                close = currentPrice,
                                high = currentPrice.coerceAtLeast(lastCandle.high),
                                low = currentPrice.coerceAtMost(lastCandle.low)
                            )
                            updatedCandles[updatedCandles.size - 1] = newLastCandle
                        }

                        val chartData = withContext(Dispatchers.Default) {
                            val mas = _prefs.value.mas
                            val extras = extraTfCandles.toMap()
                            updatedCandles.toChartComputation(_state.value.selectedInterval, mas, extras)
                        }

                        // si cambiaste de TF a mitad del calculo, este tick ya no sirve
                        if (_state.value.selectedInterval != tickInterval) continue

                        _state.value = _state.value.copy(
                            detail = detail,
                            candles = chartData.candles,
                            bbUpper = chartData.bbUpper,
                            bbMiddle = chartData.bbMiddle,
                            bbLower = chartData.bbLower,
                            stochK = chartData.stochK,
                            stochD = chartData.stochD,
                            maLines = chartData.maLines,
                            rsi = chartData.rsi,
                            smc = chartData.smc,
                            rsiDiv = chartData.rsiDiv
                        )
                        refreshAnalysis()
                    }
                    // ponytail: wake-up exacto al cierre; si la vela cierra antes del proximo tick, syncTail justo ahi
                    val lastClose = _state.value.candles.lastOrNull()
                    val durClose = candleDurationMs(_state.value.selectedInterval)
                    if (lastClose != null && durClose > 0L && !tailSyncing) {
                        val msToClose = lastClose.time + durClose - System.currentTimeMillis()
                        if (msToClose in 1L..DETAIL_REFRESH_MS) {
                            val ival = _state.value.selectedInterval
                            delay(msToClose + CLOSE_SYNC_GRACE_MS)
                            if (_state.value.selectedInterval == ival && !tailSyncing) {
                                tailSyncing = true
                                try {
                                    syncTail()
                                } finally {
                                    tailSyncing = false
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // nunca mas silencioso, el loop del chart fallaba sin dejar rastro
                    logNonFatal("Detail refresh tick failed for $symbol", e)
                }
            }
        }
    }

    fun loadChartData(interval: String, force: Boolean = false) {
        val normalizedInterval = interval.trim()
        if (!force && normalizedInterval == state.value.selectedInterval && state.value.candles.isNotEmpty()) return
        noNewCandleStreak = 0

        chartJob?.cancel()
        chartJob = viewModelScope.launch {
            _state.value = state.value.copy(selectedInterval = normalizedInterval, isLoading = true, error = "")
            try {
                val masSnapshot = _prefs.value.mas
                val neededTfs = masSnapshot
                    .filter { it.visible && it.timeframe != "chart" && it.timeframe != normalizedInterval }
                    .map { it.timeframe }.toSet()
                // klines + extras en paralelo: se pinta una sola vez ya con todo
                val rawKlinesDeferred = async(Dispatchers.IO) {
                    repository.getKlines(
                        symbol,
                        // MEXC mapea+agrega en repo, Binance usa su formato
                        if (source == "MEXC") normalizedInterval else normalizedInterval.toBinanceInterval(),
                        source,
                        // carga completa siempre fresca, si no el cambio de TF muestra velas viejas
                        forceRefresh = true
                    )
                }
                val extrasDeferred = neededTfs.map { tf ->
                    async(Dispatchers.IO) {
                        tf to try {
                            fetchExtraCandles(tf, force = false)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
                val rawKlines = rawKlinesDeferred.await()
                extrasDeferred.awaitAll().forEach { (tf, candles) ->
                    if (candles.isNotEmpty()) extraTfCandles[tf] = candles
                }
                val chartData = withContext(Dispatchers.Default) {
                    val candles = rawKlines.toCandles().let {
                        if (source == "MEXC") it else it.aggregateForInterval(normalizedInterval)
                    }

                    if (candles.isEmpty()) {
                        return@withContext ChartComputation()
                    }

                    candles.toChartComputation(normalizedInterval, masSnapshot, extraTfCandles.toMap())
                }

                _state.value = state.value.copy(
                    candles = chartData.candles,
                    bbUpper = chartData.bbUpper,
                    bbMiddle = chartData.bbMiddle,
                    bbLower = chartData.bbLower,
                    stochK = chartData.stochK,
                    stochD = chartData.stochD,
                    maLines = chartData.maLines,
                    rsi = chartData.rsi,
                    smc = chartData.smc,
                            rsiDiv = chartData.rsiDiv,
                    isLoading = false
                )
                // extras ya traidos en paralelo arriba; solo refresca analisis
                refreshAnalysis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logNonFatal("Chart data load failed for $symbol/$normalizedInterval", e)
                _state.value = state.value.copy(isLoading = false, error = e.message ?: "Error")
            }
        }
    }

    // cola fresca mergeada por time; la historia vieja no se toca y el viewport no se mueve
    private suspend fun syncTail() {
        val current = _state.value
        val interval = current.selectedInterval
        if (current.candles.isEmpty()) {
            loadChartData(interval, force = true)
            return
        }
        val sinceMs = current.candles.lastOrNull()?.time ?: 0L
        val rows = repository.getLatestKlines(
            symbol,
            if (source == "MEXC") interval else interval.toBinanceInterval(),
            source,
            sinceTimeMs = sinceMs
        )
        if (rows.isEmpty()) return
        // si cambiaste de TF mientras viajaba la red, esa cola ya no sirve
        if (_state.value.selectedInterval != interval) return
        val fresh = withContext(Dispatchers.Default) {
            rows.toCandles().let {
                if (source == "MEXC") it else it.aggregateForInterval(interval)
            }
        }
        if (fresh.isEmpty()) return
        if (_state.value.selectedInterval != interval) return
        val oldLast = current.candles.lastOrNull()?.time ?: 0L
        val freshByTime = fresh.associateBy { it.time }
        val merged = ArrayList<CandleData>(current.candles.size + fresh.size)
        for (c in current.candles) {
            merged.add(freshByTime[c.time] ?: c)
        }
        for (c in fresh) {
            if (c.time > oldLast) merged.add(c)
        }
        if (merged.size == current.candles.size && merged.lastOrNull()?.time == oldLast) {
            // el exchange aun no publica la vela; al tercer cierre, full load
            noNewCandleStreak++
            if (noNewCandleStreak >= 3) {
                noNewCandleStreak = 0
                loadChartData(interval, force = true)
            }
            return
        }
        noNewCandleStreak = 0
        val chartData = withContext(Dispatchers.Default) {
            merged.toChartComputation(interval, _prefs.value.mas, extraTfCandles.toMap())
        }
        _state.value = _state.value.copy(
            candles = chartData.candles,
            bbUpper = chartData.bbUpper,
            bbMiddle = chartData.bbMiddle,
            bbLower = chartData.bbLower,
            stochK = chartData.stochK,
            stochD = chartData.stochD,
            maLines = chartData.maLines,
            rsi = chartData.rsi,
            smc = chartData.smc,
                            rsiDiv = chartData.rsiDiv
        )
        refreshAnalysis()
    }

    private fun List<List<Any>>.toCandles(): List<CandleData> {
        val candles = ArrayList<CandleData>(size)
        forEach { row ->
            val time = row.getOrNull(0).toLongValue()
            if (time > 0L) {
                candles.add(
                    CandleData(
                        time = time,
                        open = row.getOrNull(1).toDoubleValue(),
                        high = row.getOrNull(2).toDoubleValue(),
                        low = row.getOrNull(3).toDoubleValue(),
                        close = row.getOrNull(4).toDoubleValue(),
                        volume = row.getOrNull(5).toDoubleValue(),
                        takerBuyVol = row.getOrNull(9).toDoubleValue()
                    )
                )
            }
        }
        return candles
    }

    private fun Any?.toLongValue(): Long {
        return when (this) {
            is Number -> toLong()
            is String -> toLongOrNull() ?: toDoubleOrNull()?.toLong() ?: 0L
            else -> toString().toLongOrNull() ?: toString().toDoubleOrNull()?.toLong() ?: 0L
        }
    }

    private fun Any?.toDoubleValue(): Double {
        return when (this) {
            is Number -> toDouble()
            is String -> toDoubleOrNull() ?: 0.0
            else -> toString().toDoubleOrNull() ?: 0.0
        }
    }

    private fun List<CandleData>.toChartComputation(
        interval: String = "",
        mas: List<MaConfig> = _prefs.value.mas,
        extras: Map<String, List<CandleData>> = emptyMap()
    ): ChartComputation {
        if (isEmpty()) return ChartComputation()

        val period = 20
        val multiplier = 2.0
        val bbUpper = mutableListOf<Pair<Long, Double>>()
        val bbMiddle = mutableListOf<Pair<Long, Double>>()
        val bbLower = mutableListOf<Pair<Long, Double>>()
        var rollingSum = 0.0
        var rollingSquaredSum = 0.0

        for (i in indices) {
            val close = this[i].close
            rollingSum += close
            rollingSquaredSum += close * close

            if (i >= period) {
                val droppedClose = this[i - period].close
                rollingSum -= droppedClose
                rollingSquaredSum -= droppedClose * droppedClose
            }

            if (i >= period - 1) {
                val sma = rollingSum / period
                val variance = (rollingSquaredSum / period) - sma.pow(2.0)
                val stdDev = sqrt(variance.coerceAtLeast(0.0))

                bbMiddle.add(i.toLong() to sma)
                bbUpper.add(i.toLong() to (sma + multiplier * stdDev))
                bbLower.add(i.toLong() to (sma - multiplier * stdDev))
            }
        }

        val stochK = mutableListOf<Pair<Long, Double>>()
        val stochD = mutableListOf<Pair<Long, Double>>()
        val rsiValues = calculateRSI(this)

        // SMA/EMA por MA: TF del grafico se calcula local, otro TF se alinea del cache (plan A)
        val maLines = computeMaLines(interval, mas, extras)

        if (rsiValues.size >= STOCH_RSI_PERIOD) {
            val stochRSI = mutableListOf<Double>()
            for (i in rsiValues.indices) {
                if (i >= STOCH_RSI_PERIOD - 1) {
                    val slice = rsiValues.subList(i - STOCH_RSI_PERIOD + 1, i + 1)
                    val low = slice.minOrNull() ?: 0.0
                    val high = slice.maxOrNull() ?: 100.0
                    val current = rsiValues[i]
                    val s = if (high - low != 0.0) (current - low) / (high - low) * 100 else 0.0
                    stochRSI.add(s)
                } else {
                    stochRSI.add(0.0)
                }
            }

            val smoothK = calculateSMA(stochRSI)
            val smoothD = calculateSMA(smoothK)

            for (i in smoothK.indices) {
                val candleIndex = i + (size - smoothK.size)
                if (candleIndex >= 0) {
                    stochK.add(candleIndex.toLong() to smoothK[i])
                    stochD.add(candleIndex.toLong() to smoothD[i])
                }
            }
        }

        // RSI(14) alineado a vela para el subpanel, reusa el calculo de arriba
        val rsi = rsiValues.mapIndexed { i, v -> (i + (size - rsiValues.size)).toLong() to v }

        return ChartComputation(
            candles = this,
            bbUpper = bbUpper,
            bbMiddle = bbMiddle,
            bbLower = bbLower,
            stochK = stochK,
            stochD = stochD,
            maLines = maLines,
            rsi = rsi,
            // SMC derivado de las velas, se recalcula solo al cambiar TF
            smc = computeSmc(this, interval),
            // confirmadas (lookback 5) mas tempranas (lookback 2) sin repetir el mismo evento
            rsiDiv = mergeConfirmedAndEarly(
                detectRsiDivergences(this, rsiValues),
                detectRsiDivergences(this, rsiValues, RSI_DIV_EARLY_LOOKBACK)
            )
        )
    }

    // MAs locales (chart) + alineadas de otro TF con el mismo source
    private fun List<CandleData>.computeMaLines(
        interval: String,
        mas: List<MaConfig>,
        extras: Map<String, List<CandleData>>
    ): Map<String, List<Pair<Long, Double>>> {
        val out = mutableMapOf<String, List<Pair<Long, Double>>>()
        if (isEmpty()) return out
        // duplicadas (mismo tipo, periodo y TF efectivo) se pinta solo una;
        // gana la de TF "chart", las ocultas no bloquean a las visibles
        val seen = mutableSetOf<Triple<MaType, Int, String>>()
        val ordered = mas.sortedBy { if (it.timeframe == "chart") 0 else 1 }
        for (ma in ordered) {
            val effectiveTf = if (ma.timeframe == "chart" || ma.timeframe == interval) interval else ma.timeframe
            if (ma.visible) {
                val key = Triple(ma.type, ma.period, effectiveTf)
                if (key in seen) continue
                seen.add(key)
            }
            val tf = ma.timeframe
            if (tf != "chart" && tf != interval) {
                val extra = extras[tf] ?: continue
                alignExtraMa(ma, extra)?.let { out[ma.id] = it }
                continue
            }
            if (size < ma.period) continue
            out[ma.id] = when (ma.type) {
                MaType.EMA -> emaOf(map { it.close }, ma.period)
                else -> smaOf(map { it.close }, ma.period)
            }
        }
        return out
    }

    private fun smaOf(closes: List<Double>, period: Int): List<Pair<Long, Double>> {
        val line = ArrayList<Pair<Long, Double>>(closes.size)
        var sum = 0.0
        for (i in closes.indices) {
            sum += closes[i]
            if (i >= period) sum -= closes[i - period]
            if (i >= period - 1) line.add(i.toLong() to sum / period)
        }
        return line
    }

    private fun emaOf(closes: List<Double>, period: Int): List<Pair<Long, Double>> {
        val line = ArrayList<Pair<Long, Double>>(closes.size)
        if (closes.size < period) return line
        val k = 2.0 / (period + 1)
        var ema = closes.take(period).average()
        line.add((period - 1).toLong() to ema)
        for (i in period until closes.size) {
            ema = closes[i] * k + ema * (1 - k)
            line.add(i.toLong() to ema)
        }
        return line
    }

    // MA calculada en velas de otro TF, mapeada a indices del chart por tiempo
    private fun List<CandleData>.alignExtraMa(ma: MaConfig, extra: List<CandleData>): List<Pair<Long, Double>>? {
        if (isEmpty() || extra.size < ma.period) return null
        val extraCloses = extra.map { it.close }
        val extraMa = when (ma.type) {
            MaType.EMA -> emaOf(extraCloses, ma.period)
            else -> smaOf(extraCloses, ma.period)
        }
        if (extraMa.isEmpty()) return null
        val times = extra.map { it.time }
        val out = ArrayList<Pair<Long, Double>>(size)
        var j = 0
        var last: Double? = null
        for (i in indices) {
            val t = this[i].time
            while (j < extraMa.size && times[j + (extra.size - extraMa.size)] <= t) {
                last = extraMa[j].second
                j++
            }
            if (last != null) out.add(i.toLong() to last)
        }
        return out.ifEmpty { null }
    }

    // tempranas marcadas y sin las que ya salieron confirmadas
    private fun mergeConfirmedAndEarly(confirmed: List<RsiDiv>, early: List<RsiDiv>): List<RsiDiv> {
        val fresh = early.map { it.copy(early = true) }.filter { e ->
            confirmed.none { c -> c.kind == e.kind && abs(c.idx2 - e.idx2) <= 2 }
        }
        return (confirmed + fresh).sortedBy { it.idx2 }
    }

    // calculo compartido con el worker de alertas (RsiCalc.kt)
    private fun calculateRSI(candles: List<CandleData>): List<Double> =
        calculateRsi(candles, STOCH_RSI_PERIOD)

    private fun calculateSMA(values: List<Double>): List<Double> {
        val period = STOCH_SMOOTH_PERIOD
        val sma = ArrayList<Double>(values.size)
        var rollingSum = 0.0
        for (i in values.indices) {
            rollingSum += values[i]
            if (i >= period) {
                rollingSum -= values[i - period]
            }
            if (i >= period - 1) {
                sma.add(rollingSum / period)
            } else {
                sma.add(values[i])
            }
        }
        return sma
    }

    private fun String.toBinanceInterval(): String {
        return when (this) {
            "5d" -> "1d"
            "2w" -> "1w"
            "1mo" -> "1M"
            else -> this
        }
    }

    private fun List<CandleData>.aggregateForInterval(interval: String): List<CandleData> {
        val chunkDurMs = when (interval) {
            "5d" -> 5 * 86_400_000L
            "2w" -> 14 * 86_400_000L
            else -> return this
        }
        if (isEmpty()) return this
        // buckets anclados a calendario, no al indice; si no los times bailan cada fetch
        val weekAnchored = interval == "2w"
        val buckets = LinkedHashMap<Long, MutableList<CandleData>>()
        for (c in this) {
            val key = if (weekAnchored) {
                c.time - ((c.time - WEEK_ANCHOR_MS) % chunkDurMs)
            } else {
                c.time - (c.time % chunkDurMs)
            }
            buckets.getOrPut(key) { mutableListOf() }.add(c)
        }
        return buckets.map { (key, bucket) ->
            val first = bucket.first()
            val last = bucket.last()
            CandleData(
                time = key,
                open = first.open,
                high = bucket.maxOf { it.high },
                low = bucket.minOf { it.low },
                close = last.close,
                volume = bucket.sumOf { it.volume },
                takerBuyVol = bucket.sumOf { it.takerBuyVol }
            )
        }
    }

    private companion object {
        const val TAG = "CryptoDetailVM"
        const val DETAIL_REFRESH_MS = 5_000L
        // ponytail: gracia post-cierre pa' que el exchange publique la vela antes del tail
        const val CLOSE_SYNC_GRACE_MS = 1_200L
        const val DEFAULT_CHART_INTERVAL = "15m"
        const val STOCH_RSI_PERIOD = 14
        const val STOCH_SMOOTH_PERIOD = 3
        // pulso: throttle entre recomputos + TTL de las 15m
        const val ANALYSIS_THROTTLE_MS = 15_000L
        const val ANALYSIS_TF_TTL_MS = 300_000L
        // lunes 2020-01-06T00:00Z, ancla de buckets semanales (la epoca cae jueves)
        const val WEEK_ANCHOR_MS = 1_578_182_400_000L
    }

    // espejo de intervalDurationMs del chart pa' detectar el cierre sin acoplar
    private fun candleDurationMs(interval: String): Long = when (interval.trim()) {
        "1m" -> 60_000L
        "5m" -> 300_000L
        "15m" -> 900_000L
        "30m" -> 1_800_000L
        "1h" -> 3_600_000L
        "2h" -> 7_200_000L
        "4h" -> 14_400_000L
        "6h" -> 21_600_000L
        "12h" -> 43_200_000L
        "1d" -> 86_400_000L
        "3d" -> 259_200_000L
        "5d" -> 432_000_000L
        "1w" -> 604_800_000L
        "2w" -> 1_209_600_000L
        "1mo" -> 2_592_000_000L
        else -> 0L
    }
}

data class CryptoDetailState(
    val symbol: String = "",
    val source: String = "Binance",
    val detail: PairDetail? = null,
    val candles: List<CandleData> = emptyList(),
    val bbUpper: List<Pair<Long, Double>> = emptyList(),
    val bbMiddle: List<Pair<Long, Double>> = emptyList(),
    val bbLower: List<Pair<Long, Double>> = emptyList(),
    val stochK: List<Pair<Long, Double>> = emptyList(),
    val stochD: List<Pair<Long, Double>> = emptyList(),
    val maLines: Map<String, List<Pair<Long, Double>>> = emptyMap(),
    val rsi: List<Pair<Long, Double>> = emptyList(),
    val smc: SmcData = SmcData(),
    val rsiDiv: List<RsiDiv> = emptyList(),
    val selectedInterval: String = "15m",
    val isLoading: Boolean = false,
    val error: String = ""
)

data class CandleData(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double = 0.0,
    // taker buy base volume (indice 9 de klines Binance), sell = volume - buy
    val takerBuyVol: Double = 0.0
)

private data class ChartComputation(
    val candles: List<CandleData> = emptyList(),
    val bbUpper: List<Pair<Long, Double>> = emptyList(),
    val bbMiddle: List<Pair<Long, Double>> = emptyList(),
    val bbLower: List<Pair<Long, Double>> = emptyList(),
    val stochK: List<Pair<Long, Double>> = emptyList(),
    val stochD: List<Pair<Long, Double>> = emptyList(),
    val maLines: Map<String, List<Pair<Long, Double>>> = emptyMap(),
    val rsi: List<Pair<Long, Double>> = emptyList(),
    val smc: SmcData = SmcData(),
    val rsiDiv: List<RsiDiv> = emptyList()
)
