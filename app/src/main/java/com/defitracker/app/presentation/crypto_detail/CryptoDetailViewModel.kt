package com.defitracker.app.presentation.crypto_detail

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.defitracker.app.core.Timeframes
import com.defitracker.app.data.repository.CryptoRepositoryImpl
import com.defitracker.app.domain.model.PairDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.sqrt

// semilla de la lista (precio/%/vol ya en mano): pinta el header al instante,
// el detalle fresco la reemplaza al llegar. H/L arranca en el precio.
data class DetailSeed(
    val price: String = "",
    val pct: String = "",
    val pos: Boolean = true,
    val vol: String = ""
)

@HiltViewModel
class CryptoDetailViewModel @Inject constructor(    private val repository: CryptoRepositoryImpl,
    private val prefsRepo: IndicatorPrefsRepository,
    private val priceAlertDao: com.defitracker.app.data.local.PriceAlertDao,
    private val monitorPrefs: com.defitracker.app.alerts.DivMonitorPrefs,
    private val notifier: com.defitracker.app.alerts.DivAlertNotifier,
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

    // posiciones LONG/SHORT por simbolo, mismo esquema
    private val _positionOverlays = mutableStateOf<List<PositionOverlay>>(emptyList())
    val positionOverlays: State<List<PositionOverlay>> = _positionOverlays
    private val _selectedPositionId = mutableStateOf<String?>(null)
    val selectedPositionId: State<String?> = _selectedPositionId

    private val symbol: String = checkNotNull(savedStateHandle["symbol"])
    private val source: String = checkNotNull(savedStateHandle["source"])
    // deep-link desde noti/sheet con el TF de la alerta
    private val initialInterval: String = savedStateHandle.get<String>("interval")?.trim().orEmpty()
    // deep-link desde noti de señal: abre el sheet de analisis al entrar
    val openAnalysisInitially: Boolean = savedStateHandle.get<String>("analysis") == "1"
    // semilla de la lista: header con precio al instante, sin esperar la red
    private val seed = DetailSeed(
        price = savedStateHandle.get<String>("price").orEmpty(),
        pct = savedStateHandle.get<String>("pct").orEmpty(),
        pos = savedStateHandle.get<String>("pos") != "0",
        vol = savedStateHandle.get<String>("vol").orEmpty()
    )

    // pulso del momento para el boton fantasma
    private val _analysis = mutableStateOf<PulseAnalysis?>(null)
    val analysis: State<PulseAnalysis?> = _analysis
    private var lastAnalysisAt = 0L
    private var tfClosesCache = mapOf<String, List<Double>>()
    private var tfClosesAt = 0L
    // el pulso solo trabaja con el sheet abierto: abrir el chart ya no lo dispara
    private var analysisOpen = false
    private var analysisJob: Job? = null

    fun setAnalysisOpen(open: Boolean) {
        analysisOpen = open
    }

    fun refreshAnalysis(force: Boolean = false) {
        val current = _state.value
        if (current.candles.size < 60) return
        if (!force && !analysisOpen) return
        val now = System.currentTimeMillis()
        if (!force && now - lastAnalysisAt < ANALYSIS_THROTTLE_MS) return
        lastAnalysisAt = now
        val interval = current.selectedInterval
        val candles = current.candles
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
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
    // generacion del chart: cada cambio de TF sube el numero y los resultados viejos se descartan
    private var chartGen = 0
    private var lastTfPickMs = 0L
    // el loop es secuencial, el flag solo evita solapar tail con full load
    private var tailSyncing = false
    // si el tail no trae vela nueva 3 cierres seguidos, full load de respaldo
    private var noNewCandleStreak = 0

    init {
        _state.value = state.value.copy(symbol = symbol, source = source)
        // si viene de una alerta, abre directo en ese TF; si no, retoma el ultimo
        // TF que estabas mirando en vez de volver siempre a 5m
        val startInterval = initialInterval.ifEmpty {
            prefsRepo.lastChartInterval.ifEmpty { DEFAULT_CHART_INTERVAL }
        }
        _state.value = state.value.copy(selectedInterval = startInterval)
        // semilla de la lista: el header ya muestra precio/%/vol antes del primer fetch
        if (seed.price.toDoubleValue() > 0.0) {
            _state.value = state.value.copy(
                detail = PairDetail(
                    symbol = symbol,
                    price = seed.price,
                    priceChange = "0.00",
                    priceChangePercent = seed.pct.ifBlank { "0.00" },
                    highPrice = seed.price,
                    lowPrice = seed.price,
                    volume = "0.00",
                    quoteVolume = seed.vol.ifBlank { "0.00" },
                    isPositive = seed.pos
                )
            )
        }
        loadDetail()
        startUpdates()
        analysisOpen = openAnalysisInitially
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
                _selectedFibId.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
            // dibujos del simbolo, al entrar no se selecciona nada:
            // la barrita flotante solo aparece cuando tocas un dibujo
            try {
                _drawOverlays.value = prefsRepo.drawOverlaysFlow(symbol).first()
                _selectedDrawId.value = null
                _selectedFibId.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
            // posiciones LONG/SHORT del simbolo, sin seleccion al entrar
            try {
                _positionOverlays.value = prefsRepo.positionOverlaysFlow(symbol).first()
                _selectedPositionId.value = null
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
        // cualquier ajuste de indicadores invalida el cache de velas cerradas
        closedCandleVersion++
        viewModelScope.launch {
            try {
                prefsRepo.save(next)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    fun toggleOverlaysHidden() = updatePrefs { it.copy(overlaysHidden = !it.overlaysHidden) }
    fun toggleBB() = updatePrefs { it.copy(bbVisible = !it.bbVisible) }
    fun toggleProfile() = updatePrefs { it.copy(profileVisible = !it.profileVisible) }
    fun toggleVolumeSub() = updatePrefs { it.copy(volumeVisible = !it.volumeVisible) }
    fun toggleStochSub() = updatePrefs { it.copy(stochVisible = !it.stochVisible) }
    fun toggleRsiSub() = updatePrefs { it.copy(rsiVisible = !it.rsiVisible) }
    fun toggleMacdSub() = updatePrefs { it.copy(macdVisible = !it.macdVisible) }
    fun toggleTakerSub() {
        val next = !_prefs.value.takerVisible
        updatePrefs { it.copy(takerVisible = next) }
        if (next) viewModelScope.launch { syncTaker(500) }
    }

    // nota: taker siempre de Binance Futuros; el tick fusiona (conserva lo previo),
    // el full de 500 solo al abrir/cambiar TF o prender el toggle
    private suspend fun syncTaker(limit: Int) {
        if (!_prefs.value.takerVisible) return
        lastTakerSyncAt = System.currentTimeMillis()
        val interval = _state.value.selectedInterval
        val rows = try {
            repository.getTakerVolumes(symbol, interval, source, limit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        if (rows.isEmpty() || _state.value.selectedInterval != interval) return
        val candles = _state.value.candles
        if (candles.isEmpty()) return
        // lo ya traido se recuerda por hora de vela, no por posicion: la lista se corre
        if (takerCacheKey != "$symbol-$interval") {
            takerCache.clear()
            takerCacheKey = "$symbol-$interval"
        }
        rows.forEach { takerCache[it.timeMs] = it.buy to it.sell }
        if (takerCache.isEmpty()) return
        val buy = ArrayList<Pair<Long, Double>>(candles.size)
        val sell = ArrayList<Pair<Long, Double>>(candles.size)
        candles.forEachIndexed { i, c ->
            val q = takerCache[c.time] ?: return@forEachIndexed
            buy.add(i.toLong() to q.first)
            sell.add(i.toLong() to q.second)
        }
        if (buy.isNotEmpty() && _state.value.selectedInterval == interval) {
            _state.value = _state.value.copy(takerBuy = buy, takerSell = sell)
        }
    }
    fun toggleRsiDiv() {
        updatePrefs { it.copy(rsiDivVisible = !it.rsiDivVisible) }
        // las divs apagadas no se calculan: al prenderlas se recalculan al toque
        recomputeIndicatorsFromCache()
    }
    fun toggleRsiDivHidden() = updatePrefs { it.copy(rsiDivHidden = !it.rsiDivHidden) }
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
        val usedPeriods = cur.mas.map { it.period }.toSet()
        val period = listOf(9, 21, 50, 200, 10, 30, 100, 55).firstOrNull { it !in usedPeriods } ?: 50
        val usedColors = cur.mas.map { it.colorHex }.toSet()
        val color = IndicatorPrefs.ADD_COLORS.firstOrNull { it !in usedColors } ?: "#FFFFFF"
        val id = "ma${period}_${System.currentTimeMillis() % 100000}"
        cur.copy(mas = cur.mas + MaConfig(id, period, MaType.SMA, "chart", color, 1.2f, false))
    }
    fun deleteMA(id: String) = updatePrefs { cur ->
        if (cur.mas.size <= 1) return@updatePrefs cur
        cur.copy(mas = cur.mas.filterNot { it.id == id })
    }

    // velas cacheadas por TF para MAs de otra temporalidad (plan A, respeta source)
    private val extraTfCandles = mutableMapOf<String, List<CandleData>>()
    // velas de TFs superiores para confluencia FVG real (mismo exchange que el chart)
    private val extraSmcCandles = mutableMapOf<String, List<CandleData>>()
    // SMC: sólo depende de velas cerradas, así que el tick de 2s reusa el resultado
    private val smcCache = ClosedCandleCache<SmcData>()
    // series de BB/RSI/Stoch/MACD/MAs: la historia se reusa y el tick sólo mueve el último punto
    private val indicatorTail = IndicatorTailCache()
    // sube cuando las velas cerradas pueden haber cambiado (carga, cola, prefs, TF)
    @Volatile
    private var closedCandleVersion = 0

    // bandas reales si hay cache de TFs superiores, null = deja el fallback sintetico
    private fun overlaySmcConfluence(smc: SmcData): SmcData? {
        val higher = extraSmcCandles.toMap()
        if (higher.values.none { it.isNotEmpty() }) return null
        return smc.copy(confluence = detectFvgConfluenceMultiTF(smc.zones, higher))
    }

    private fun intervalForSource(tf: String): String =
                if (source == "MEXC") tf else if (source == "TV") Timeframes.tradingViewResolution(tf) else tf.toBinanceInterval()

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
                if (changed) {
                    // las MAs de otro TF se alinean de este cache: si cambió, la cola
                    // cacheada ya no sirve y se recalcula todo (mantiene la alineación)
                    closedCandleVersion++
                    recomputeMaLinesFromCache()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    private suspend fun fetchExtraCandles(tf: String, force: Boolean): List<CandleData> {
        val rows = repository.getKlines(symbol, intervalForSource(tf), source, forceRefresh = force)
        if (rows.isEmpty()) return emptyList()
        return withContext(Dispatchers.Default) {
            rows.toCandleList().let { list ->
                if (source == "MEXC") list else list.aggregateForInterval(tf)
            }
        }
    }

    // re-alinea MAs con el cache actual, sin red y sin mover viewport
    private fun recomputeMaLinesFromCache() {        val current = _state.value
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

    // recalcula SMC/divs con las velas en mano al prender el toggle, sin red ni reload
    private fun recomputeIndicatorsFromCache() {
        val current = _state.value
        if (current.candles.isEmpty() || current.isLoading) return
        viewModelScope.launch {
            try {
                val interval = current.selectedInterval
                val comp = withContext(Dispatchers.Default) {
                    val c = current.candles.toChartComputation(interval, _prefs.value.mas, extraTfCandles.toMap())
                    overlaySmcConfluence(c.smc)?.let { c.copy(smc = it) } ?: c
                }
                if (_state.value.selectedInterval == interval && _state.value.candles.size == current.candles.size) {
                    _state.value = _state.value.withComputation(comp)
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

    @Suppress("UNUSED_PARAMETER")
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

    @Suppress("UNUSED_PARAMETER")
    fun commitDraw(id: String) {
        persistDraws()
    }

    fun addDraw(kind: DrawKind, start: FibAnchor, end: FibAnchor): DrawOverlay? {
        if (_drawOverlays.value.size >= MAX_DRAWS_PER_SYMBOL) return null
        val overlay = DrawOverlay(
            id = java.util.UUID.randomUUID().toString(),
            kind = kind,
            start = start,
            end = end,
            // figuras nuevas con relleno suave, el resto sin relleno
            fill = kind.supportsFill(),
            fillAlpha = 0.2f
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
    fun setDrawFill(id: String, on: Boolean) = updateDraw(id, { it.copy(fill = on) })
    fun setDrawFillAlpha(id: String, a: Float) = updateDraw(id, { it.copy(fillAlpha = a.coerceIn(0f, 1f)) })
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

    // ─── POSICIONES LONG/SHORT ───
    private fun persistPositions() {
        val snapshot = _positionOverlays.value
        viewModelScope.launch {
            try {
                prefsRepo.savePositionOverlays(symbol, snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    fun setPositionLive(id: String, overlay: PositionOverlay) {
        _positionOverlays.value = _positionOverlays.value.map { if (it.id == id) overlay else it }
    }

    @Suppress("UNUSED_PARAMETER")
    fun commitPosition(id: String) {
        persistPositions()
    }

    // un tap crea la posicion con defaults (5x, SL/TP 1.5%, 750 USDT)
    fun addPosition(long: Boolean, entryTime: Long, entryPrice: Double, halfWidthMs: Long): PositionOverlay? {
        if (_positionOverlays.value.size >= MAX_POSITIONS_PER_SYMBOL) return null
        if (entryPrice <= 0) return null
        val sl = if (long) entryPrice * (1 - DEFAULT_POSITION_SL_PCT) else entryPrice * (1 + DEFAULT_POSITION_SL_PCT)
        val tp = if (long) entryPrice * (1 + DEFAULT_POSITION_TP_PCT) else entryPrice * (1 - DEFAULT_POSITION_TP_PCT)
        val overlay = PositionOverlay(
            id = java.util.UUID.randomUUID().toString(),
            long = long,
            startTime = entryTime - halfWidthMs,
            endTime = entryTime + halfWidthMs,
            entry = entryPrice,
            sl = sl,
            tp = tp
        )
        _positionOverlays.value = _positionOverlays.value + overlay
        _selectedPositionId.value = overlay.id
        _selectedFibId.value = null
        _selectedDrawId.value = null
        persistPositions()
        return overlay
    }

    fun deletePosition(id: String) {
        _positionOverlays.value = _positionOverlays.value.filterNot { it.id == id }
        if (_selectedPositionId.value == id) _selectedPositionId.value = null
        persistPositions()
    }

    fun selectPosition(id: String?) {
        _selectedPositionId.value = id
        if (id != null) {
            _selectedFibId.value = null
            _selectedDrawId.value = null
        }
    }

    private fun updatePosition(id: String, transform: (PositionOverlay) -> PositionOverlay) {
        _positionOverlays.value = _positionOverlays.value.map { if (it.id == id) transform(it) else it }
        persistPositions()
    }

    fun setPositionLevels(id: String, entry: Double, sl: Double, tp: Double) = updatePosition(id, {
        it.copy(
            entry = if (entry > 0) entry else it.entry,
            sl = if (sl > 0) sl else it.sl,
            tp = if (tp > 0) tp else it.tp
        )
    })

    fun setPositionQty(id: String, qty: Double) = updatePosition(id, {
        it.copy(qtyUsdt = if (qty > 0) qty else it.qtyUsdt)
    })

    fun setPositionLeverage(id: String, lev: Int) = updatePosition(id, {
        it.copy(leverage = lev.coerceIn(1, 200))
    })

    fun setPositionProfitAlpha(id: String, a: Float) = updatePosition(id, {
        it.copy(profitAlpha = a.coerceIn(0f, 1f))
    })

    fun setPositionLossAlpha(id: String, a: Float) = updatePosition(id, {
        it.copy(lossAlpha = a.coerceIn(0f, 1f))
    })

    // ─── ALERTAS DE PRECIO (LONG/SHORT por par) ───
    fun priceAlertsFlow(): kotlinx.coroutines.flow.Flow<List<com.defitracker.app.data.local.PriceAlertEntity>> =
        priceAlertDao.observeForPair(symbol, source)

    fun addPriceAlert(direction: String, target: Double, low: Double, high: Double) {
        val dir = direction.uppercase().takeIf { it == "SHORT" } ?: "LONG"
        viewModelScope.launch {
            try {
                priceAlertDao.upsert(
                    com.defitracker.app.data.local.PriceAlertEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        symbol = symbol,
                        source = source,
                        direction = dir,
                        targetPrice = target,
                        low = low,
                        high = high
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    fun deletePriceAlert(id: String) {
        viewModelScope.launch {
            try { priceAlertDao.delete(id) } catch (_: Exception) {}
        }
    }

    fun togglePriceAlert(id: String, enabled: Boolean) {
        viewModelScope.launch {
            try { priceAlertDao.setEnabled(id, enabled) } catch (_: Exception) {}
        }
    }

    // tono de la noti de alerta de precio (global, picker del sistema)
    fun priceSoundFlow(): kotlinx.coroutines.flow.Flow<String> =
        monitorPrefs.configFlow.map { it.priceSoundUri }

    fun setPriceSound(uri: String) {
        viewModelScope.launch {
            try {
                monitorPrefs.setPriceSoundUri(uri)
                // el tono vive en el canal: se recrea al elegir, no al sonar
                notifier.ensurePriceChannel(uri)
            } catch (_: Exception) {}
        }
    }

    // ─── SMC ───
    // el SMC apagado no se calcula: al prenderlo se recalcula al toque con el cache
    fun toggleSmcStructure() {
        updatePrefs { it.copy(smcStructure = !it.smcStructure) }
        recomputeIndicatorsFromCache()
    }
    fun toggleSmcOrderBlocks() {
        updatePrefs { it.copy(smcOrderBlocks = !it.smcOrderBlocks) }
        recomputeIndicatorsFromCache()
    }
    fun toggleSmcFvg() {
        updatePrefs { it.copy(smcFvg = !it.smcFvg) }
        recomputeIndicatorsFromCache()
    }
    fun toggleSmcPremium() {
        updatePrefs { it.copy(smcPremium = !it.smcPremium) }
        recomputeIndicatorsFromCache()
    }
    fun toggleSmcEqhl() {
        updatePrefs { it.copy(smcEqhl = !it.smcEqhl) }
        recomputeIndicatorsFromCache()
    }
    fun toggleSmcLiquidity() {
        updatePrefs { it.copy(smcLiquidity = !it.smcLiquidity) }
        recomputeIndicatorsFromCache()
    }

    // jalar-para-actualizar: detalle + chart fresco sin pelear con el loop automatico
    fun refreshAll() {
        loadDetail()
        loadChartData(_state.value.selectedInterval, force = true)
    }

    fun loadDetail() {
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

    // tick sin cambios = sin reconstruccion (mata el parpadeo en laterales)
    private var lastTickPrice = Double.NaN
    private var lastTickCandleKey = ""
    private var lastBaseVol24 = Double.NaN
    private var lastTakerSyncAt = 0L
    // taker ya traido, por hora de vela; se limpia al cambiar de moneda o TF
    private val takerCache = HashMap<Long, Pair<Double, Double>>()
    private var takerCacheKey = ""
    private var lastTvDetailAt = 0L

    private fun startUpdates() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                // precio y vela en curso siempre en vivo, sin importar la temporalidad
                delay(DETAIL_REFRESH_MS)
                // mientras el chart esta cargando (cambio de TF / pull), el tick no pisa
                if (_state.value.isLoading) continue
                try {
                    val nowLoop = System.currentTimeMillis()
                    val cached = _state.value.detail
                    // TV no tiene stream intradiario: su fresco real es el memo de ~15s
                    val detail = if (source == "TV" && cached != null && lastTickPrice.isFinite() &&
                        nowLoop - lastTvDetailAt < TV_DETAIL_MS
                    ) {
                        cached
                    } else {
                        repository.getPairDetail(symbol, source).also { lastTvDetailAt = nowLoop }
                    }
                    val currentPrice = detail.price.toDoubleValue()

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
                        val tickCandles = _state.value.candles
                        // la versión va pegada al snapshot: si la cola entra mientras
                        // este tick calcula, no se guarda un SMC viejo con clave nueva
                        val tickClosedVersion = closedCandleVersion
                        val tickLast = tickCandles.lastOrNull()
                        // volumen vivo: el tick antes solo movia precio, la ultima barra quedaba
                        // congelada hasta el cierre. El delta del vol 24h ≈ lo tradeado entre ticks.
                        val baseVol24 = detail.volume.toDoubleValue()
                        var volDelta = 0.0
                        if (lastBaseVol24.isFinite() && baseVol24 > 0.0) {
                            val d = baseVol24 - lastBaseVol24
                            if (d > 0.0) volDelta = d
                        }
                        if (baseVol24 > 0.0) lastBaseVol24 = baseVol24
                        val liveVol = (tickLast?.volume ?: 0.0) + volDelta
                        val tickKey = if (tickLast == null) "empty"
                        else "${tickLast.time}:${tickLast.open}:${tickLast.high}:${tickLast.low}:$currentPrice:$liveVol:${tickCandles.size}"
                        // nada se movio: ni recalculo ni estado nuevo, el chart ni se entera
                        if (currentPrice == lastTickPrice && tickKey == lastTickCandleKey) continue
                        lastTickPrice = currentPrice
                        lastTickCandleKey = tickKey
                        val updatedCandles = tickCandles.toMutableList()
                        if (updatedCandles.isNotEmpty()) {
                            val lastCandle = updatedCandles.last()
                            // el split buy/sell conserva su proporcion al crecer
                            val buyRatio = if (lastCandle.volume > 0.0) {
                                (lastCandle.takerBuyVol / lastCandle.volume).coerceIn(0.0, 1.0)
                            } else 0.5
                            val newLastCandle = lastCandle.copy(
                                close = currentPrice,
                                high = currentPrice.coerceAtLeast(lastCandle.high),
                                low = currentPrice.coerceAtMost(lastCandle.low),
                                volume = liveVol,
                                takerBuyVol = lastCandle.takerBuyVol + volDelta * buyRatio
                            )
                            updatedCandles[updatedCandles.size - 1] = newLastCandle
                        }

                        val chartData = withContext(Dispatchers.Default) {
                            val mas = _prefs.value.mas
                            val extras = extraTfCandles.toMap()
                            val comp = updatedCandles.toChartComputation(
                                _state.value.selectedInterval, mas, extras, closedVersion = tickClosedVersion
                            )
                            // confluencia real desde cache, sin red en el tick
                            val smcReal = overlaySmcConfluence(comp.smc)
                            if (smcReal != null) comp.copy(smc = smcReal) else comp
                        }

                        // si cambiaste de TF a mitad del calculo, este tick ya no sirve
                        if (_state.value.selectedInterval != tickInterval) continue

                        _state.value = _state.value.withComputation(chartData).copy(detail = detail)
                        refreshAnalysis()
                        // taker en vivo: la barra crece local, la red solo corrige la
                        // proporcion; cadencia por TF (2s en 1m/5m hasta 60s en semanales)
                        val takerEvery = Timeframes.takerRefreshMs(tickInterval)
                        if (System.currentTimeMillis() - lastTakerSyncAt >= takerEvery) syncTaker(10)
                    }
                    // nota: wake-up exacto al cierre; si la vela cierra antes del proximo tick, syncTail justo ahi
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
        // recordado para cuando vuelvas a entrar al detalle de este par
        if (normalizedInterval.isNotEmpty()) prefsRepo.lastChartInterval = normalizedInterval
        // debounce: toques locos de TF no encolan cargas, solo vale el ultimo
        val now = System.currentTimeMillis()
        if (!force && now - lastTfPickMs < 250L) return
        lastTfPickMs = now
        noNewCandleStreak = 0
        chartGen += 1
        val gen = chartGen
        // dataset nuevo (moneda/TF/recarga): el SMC cacheado ya no sirve
        closedCandleVersion++
        // la versión que corresponde a las velas que va a traer esta carga
        val loadClosedVersion = closedCandleVersion

        chartJob?.cancel()
        // el analisis viejo no sigue quemando red al cambiar de TF
        analysisJob?.cancel()
        chartJob = viewModelScope.launch {
            // si entro otro TF mientras se pintaba, este ya no pisa nada
            _state.value = state.value.copy(selectedInterval = normalizedInterval, isLoading = true, error = "", takerBuy = emptyList(), takerSell = emptyList())
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
                        intervalForSource(normalizedInterval),
                        source,
                        // force (init/pull) siempre fresco; cambio de TF reusa cache de 30s
                        forceRefresh = force
                    )
                }
                val extrasDeferred = neededTfs.map { tf ->
                    async(Dispatchers.IO) {
                        tf to try {
                            fetchExtraCandles(tf, force = false)
                        } catch (e: TimeoutCancellationException) {
                            // socket TV lento: sin ese extra, el chart igual pinta
                            emptyList()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
                // TFs superiores para confluencia FVG real, mismo exchange (fetchExtraCandles respeta source)
                val neededSmcTfs = if (_prefs.value.smcFvg) higherConfluenceTfs(normalizedInterval) else emptyList()
                val smcDeferred = neededSmcTfs.map { tf ->
                    async(Dispatchers.IO) {
                        tf to try {
                            fetchExtraCandles(tf, force = false)
                        } catch (e: TimeoutCancellationException) {
                            emptyList()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
                // referencia de precision: alguna vela diaria < $1 en ~45 dias (cache 5 min del repo)
                val daySubDollarDeferred = async(Dispatchers.IO) {
                    try {
                        repository.getRecentCloses(symbol, source, "1d", 45).any { it < 1.0 }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        state.value.daySubDollar
                    }
                }
                val rawKlines = rawKlinesDeferred.await()
                if (gen != chartGen) return@launch
                // purga: los TFs viejos no se acumulan al pasear por intervalos
                extraTfCandles.keys.retainAll(neededTfs)
                extraSmcCandles.keys.retainAll(neededSmcTfs.toSet())
                // TV: pintado rapido con el TF principal y los extras ya cacheados.
                // esperar 4-5 sockets mas es lo que trababa el cambio de TF
                if (source == "TV") {
                    val quickCandles = withContext(Dispatchers.Default) { rawKlines.toCandleList() }
                    if (quickCandles.isNotEmpty()) {
                        val quick = withContext(Dispatchers.Default) {
                            quickCandles.toChartComputation(
                                normalizedInterval, masSnapshot, extraTfCandles.toMap(),
                                closedVersion = loadClosedVersion
                            )
                        }
                        if (gen == chartGen && _state.value.selectedInterval == normalizedInterval) {
                            val quickSmc = withContext(Dispatchers.Default) { overlaySmcConfluence(quick.smc) }
                            _state.value = state.value.withComputation(quick.copy(smc = quickSmc ?: quick.smc))
                                .copy(isLoading = false)
                        }
                    }
                }
                extrasDeferred.awaitAll().forEach { (tf, candles) ->
                    if (candles.isNotEmpty()) extraTfCandles[tf] = candles
                }
                smcDeferred.awaitAll().forEach { (tf, candles) ->
                    if (candles.isNotEmpty()) extraSmcCandles[tf] = candles
                }
                if (gen != chartGen) return@launch
                val chartData = withContext(Dispatchers.Default) {
                    val candles = rawKlines.toCandleList().let {
                        if (source == "MEXC") it else it.aggregateForInterval(normalizedInterval)
                    }

                    if (candles.isEmpty()) {
                        return@withContext ChartComputation()
                    }

                    candles.toChartComputation(
                        normalizedInterval, masSnapshot, extraTfCandles.toMap(),
                        closedVersion = loadClosedVersion
                    )
                }
                // llego tarde: otro TF ya mando, no se pinta nada viejo
                if (gen != chartGen || _state.value.selectedInterval != normalizedInterval) return@launch

                val smcReal = withContext(Dispatchers.Default) {
                    overlaySmcConfluence(chartData.smc)
                }
                _state.value = state.value.withComputation(chartData).copy(
                    smc = smcReal ?: chartData.smc,
                    isLoading = false
                )
                // extras ya traidos en paralelo arriba; solo refresca analisis
                if (gen != chartGen) return@launch
                refreshAnalysis()
                // el flag de precision no bloquea el pintado: llega cuando llegue
                try {
                    val subDollar = daySubDollarDeferred.await()
                    if (gen == chartGen) _state.value = _state.value.copy(daySubDollar = subDollar)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
                syncTaker(500)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logNonFatal("Chart data load failed for $symbol/$normalizedInterval", e)
                if (gen == chartGen) {
                    _state.value = state.value.copy(isLoading = false, error = e.message ?: "Error")
                }
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
            intervalForSource(interval),
            source,
            sinceTimeMs = sinceMs
        )
        if (rows.isEmpty()) return
        // si cambiaste de TF mientras viajaba la red, esa cola ya no sirve
        if (_state.value.selectedInterval != interval) return
        val fresh = withContext(Dispatchers.Default) {
            rows.toCandleList().let {
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
            // TV es snapshot: su ultima barra no avanza al ritmo del tick.
            // Recargar ahi es quemar red y flashear el chart en loop, se sale sin strike.
            if (source == "TV") {
                noNewCandleStreak = 0
                return
            }
            // el exchange aun no publica la vela; al tercer cierre, full load
            noNewCandleStreak++
            if (noNewCandleStreak >= 3) {
                noNewCandleStreak = 0
                loadChartData(interval, force = true)
            }
            return
        }
        noNewCandleStreak = 0
        // la cola puede reescribir velas ya cerradas: el SMC cacheado deja de valer
        closedCandleVersion++
        val tailClosedVersion = closedCandleVersion
        val chartData = withContext(Dispatchers.Default) {
            val comp = merged.toChartComputation(
                interval, _prefs.value.mas, extraTfCandles.toMap(),
                closedVersion = tailClosedVersion
            )
            // confluencia real desde cache, sin red en el tail
            val smcReal = overlaySmcConfluence(comp.smc)
            if (smcReal != null) comp.copy(smc = smcReal) else comp
        }
        _state.value = _state.value.withComputation(chartData)
        refreshAnalysis()
        // vela recien cerrada: se corrige la proporcion real del taker una sola vez
        syncTaker(10)
    }

    // el mismo volcado de computo en tick, full load y tail: un solo lugar pa' los 15 campos
    private fun CryptoDetailState.withComputation(c: ChartComputation) = copy(
        candles = c.candles,
        bbUpper = c.bbUpper,
        bbMiddle = c.bbMiddle,
        bbLower = c.bbLower,
        stochK = c.stochK,
        stochD = c.stochD,
        maLines = c.maLines,
        rsi = c.rsi,
        rsiMa = c.rsiMa,
        macdDif = c.macdDif,
        macdDea = c.macdDea,
        macdHist = c.macdHist,
        smc = c.smc,
        rsiDiv = c.rsiDiv
    )

    private fun List<CandleData>.toChartComputation(
        interval: String = "",
        mas: List<MaConfig> = _prefs.value.mas,
        extras: Map<String, List<CandleData>> = emptyMap(),
        prefs: IndicatorPrefs = _prefs.value,
        // versión de velas cerradas que corresponde a ESTA lista: se captura junto
        // con las velas para que el cache no mezcle un cálculo viejo con una clave nueva
        closedVersion: Int = closedCandleVersion
    ): ChartComputation {
        if (isEmpty()) return ChartComputation()

        // lo apagado no se calcula: el tick cada 2s y el cambio de TF lo agradecen.
        // BB/RSI/Stoch/MACD son pasadas baratas y se dejan siempre (el toggle sigue instantaneo);
        // lo caro (SMC, divergencias, MAs ocultas) sí se condiciona.
        val wantDiv = prefs.rsiDivVisible
        val wantSmc = prefs.smcStructure || prefs.smcOrderBlocks || prefs.smcFvg ||
            prefs.smcPremium || prefs.smcEqhl || prefs.smcLiquidity

        // ── camino rápido: sólo cambió la última vela, se recalcula el último punto ──
        // la clave incluye la versión de velas cerradas, el TF, los ajustes y el tamaño,
        // así que un cache válido garantiza que la historia dibujada es la misma
        val tailKey = "$symbol|$interval|${prefs.prefsKey()}|$closedVersion|$size" +
            "|${if (size >= 2) this[size - 2].time else 0L}"
        indicatorTail.seedFor(tailKey)?.let { seed ->
            val tail = recomputeIndicatorTail(this, seed, interval, mas, prefs, smcFor(interval, wantSmc, prefs, closedVersion))
            if (tail != null) {
                // el estado en n-2 no cambió: se reusa para el próximo tick
                indicatorTail.store(IndicatorTailSeed(tailKey, tail, seed.state))
                return tail
            }
        }

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
        val rsiValues = calculateRsi(this)

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

            val smoothK = rsiMaOf(stochRSI, 3)
            val smoothD = rsiMaOf(smoothK, 3)

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
        // signal line: SMA 14 sobre el RSI, alineada igual que el RSI
        val rsiMaValues = rsiMaOf(rsiValues)
        val rsiMa = rsiMaValues.mapIndexed { i, v -> (i + (size - rsiMaValues.size)).toLong() to v }
        val macd = calculateMacd(this)

        val result = ChartComputation(
            candles = this,
            bbUpper = bbUpper,
            bbMiddle = bbMiddle,
            bbLower = bbLower,
            stochK = stochK,
            stochD = stochD,
            maLines = maLines,
            rsi = rsi,
            rsiMa = rsiMa,
            macdDif = macd.dif,
            macdDea = macd.dea,
            macdHist = macd.hist,
            // SMC derivado de las velas cerradas: la vela en curso no debe crear ni
            // matar zonas a media formacion (parpadeo y FVG_TAP perdido).
            // Como sólo depende de lo cerrado, entre ticks el resultado es el mismo:
            // se reusa el cache hasta que cambie la versión de velas cerradas.
            smc = smcFor(interval, wantSmc, prefs, closedVersion),
            // confirmadas (lookback 5) mas tempranas (lookback 2) sin repetir el mismo evento
            rsiDiv = if (wantDiv && rsiValues.isNotEmpty()) mergeConfirmedAndEarly(
                detectRsiDivergences(this, rsiValues),
                detectRsiDivergences(this, rsiValues, RSI_DIV_EARLY_LOOKBACK)
            ) else emptyList()
        )
        // se guarda el estado en n-2 para que el próximo tick sólo mueva el último punto
        buildIndicatorTailState(this)?.let { state ->
            indicatorTail.store(IndicatorTailSeed(tailKey, result, state))
        }
        return result
    }

    // SMC cacheado: la vela en curso no participa, así que entre ticks no cambia
    private fun List<CandleData>.smcFor(
        interval: String,
        wantSmc: Boolean,
        prefs: IndicatorPrefs,
        closedVersion: Int
    ): SmcData {
        if (!wantSmc) return SmcData()
        return smcCache.getOrCompute("$symbol|$interval|${prefs.smcSignature()}|$closedVersion") {
            computeSmc(dropLast(1), interval)
        }
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
            // apagada = ni se calcula
            if (!ma.visible) continue
            val effectiveTf = if (ma.timeframe == "chart" || ma.timeframe == interval) interval else ma.timeframe
            val key = Triple(ma.type, ma.period, effectiveTf)
            if (key in seen) continue
            seen.add(key)
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

    private fun String.toBinanceInterval(): String {
        return Timeframes.binanceInterval(this)
    }

    private fun List<CandleData>.aggregateForInterval(interval: String): List<CandleData> {
        val chunkDurMs = Timeframes.calendarAggregation(interval) ?: return this
        if (isEmpty()) return this
        // buckets anclados a calendario, no al indice; si no los times bailan cada fetch
        val weekAnchored = Timeframes.find(interval)?.weekAnchored == true
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
        // el precio y la vela en curso se refrescan siempre a este ritmo
        const val DETAIL_REFRESH_MS = 2_000L
        // TV sin stream intradiario: el detalle se relee a ritmo de su memo
        const val TV_DETAIL_MS = 15_000L
        // nota: gracia post-cierre pa' que el exchange publique la vela antes del tail
        const val CLOSE_SYNC_GRACE_MS = 1_200L
        const val DEFAULT_CHART_INTERVAL = Timeframes.DEFAULT_CHART_KEY
        const val STOCH_RSI_PERIOD = 14
        // pulso: throttle entre recomputos + TTL de las 15m
        const val ANALYSIS_THROTTLE_MS = 15_000L
        const val ANALYSIS_TF_TTL_MS = 300_000L
        // lunes 2020-01-06T00:00Z, ancla de buckets semanales (la epoca cae jueves)
        const val WEEK_ANCHOR_MS = 1_578_182_400_000L
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
    val rsiMa: List<Pair<Long, Double>> = emptyList(),
    val macdDif: List<Pair<Long, Double>> = emptyList(),
    val macdDea: List<Pair<Long, Double>> = emptyList(),
    val macdHist: List<Pair<Long, Double>> = emptyList(),
    val takerBuy: List<Pair<Long, Double>> = emptyList(),
    val takerSell: List<Pair<Long, Double>> = emptyList(),
    val smc: SmcData = SmcData(),
    val rsiDiv: List<RsiDiv> = emptyList(),
    val selectedInterval: String = "15m",
    val daySubDollar: Boolean = false,
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
