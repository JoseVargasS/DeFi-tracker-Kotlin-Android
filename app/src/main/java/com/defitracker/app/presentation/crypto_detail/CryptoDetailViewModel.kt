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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
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

    // ponytail: varios fibos por simbolo, cada uno con su estilo+estado propio
    private val _fibOverlays = mutableStateOf<List<FibOverlay>>(emptyList())
    val fibOverlays: State<List<FibOverlay>> = _fibOverlays
    private val _selectedFibId = mutableStateOf<String?>(null)
    val selectedFibId: State<String?> = _selectedFibId

    private val symbol: String = checkNotNull(savedStateHandle["symbol"])
    private val source: String = checkNotNull(savedStateHandle["source"])

    private var refreshJob: Job? = null
    private var chartJob: Job? = null

    init {
        _state.value = state.value.copy(symbol = symbol, source = source)
        viewModelScope.launch {
            try {
                _prefs.value = prefsRepo.prefsFlow.first()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
            // ponytail: carga los fibos guardados de este simbolo sin bloquear el chart
            try {
                val stored = prefsRepo.fibOverlaysFlow(symbol).first()
                _fibOverlays.value = stored.ifEmpty {
                    prefsRepo.migrateLegacyFib(symbol) ?: emptyList()
                }
                _selectedFibId.value = _fibOverlays.value.lastOrNull()?.id
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
        loadDetail()
        startUpdates()
        loadChartData(DEFAULT_CHART_INTERVAL)
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
    fun toggleMA(period: Int) = updatePrefs {
        it.copy(mas = it.mas.map { ma -> if (ma.period == period) ma.copy(visible = !ma.visible) else ma })
    }
    fun setMAColor(period: Int, colorHex: String) = updatePrefs {
        it.copy(mas = it.mas.map { ma -> if (ma.period == period) ma.copy(colorHex = colorHex) else ma })
    }
    fun setMAWidth(period: Int, width: Float) = updatePrefs {
        it.copy(mas = it.mas.map { ma -> if (ma.period == period) ma.copy(width = width) else ma })
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

    // ponytail: preview durante el drag sin spamear DataStore, se persiste al soltar
    fun setFibLive(id: String, overlay: FibOverlay) {
        _fibOverlays.value = _fibOverlays.value.map { if (it.id == id) overlay else it }
    }

    fun commitFib(id: String) {
        persistFibs()
    }

    fun addFib(start: FibAnchor, end: FibAnchor): FibOverlay? {
        if (_fibOverlays.value.size >= MAX_FIBS_PER_SYMBOL) return null
        val overlay = FibOverlay(
            id = java.util.UUID.randomUUID().toString(),
            start = start,
            end = end
        )
        _fibOverlays.value = _fibOverlays.value + overlay
        _selectedFibId.value = overlay.id
        persistFibs()
        return overlay
    }

    fun deleteFib(id: String) {
        _fibOverlays.value = _fibOverlays.value.filterNot { it.id == id }
        if (_selectedFibId.value == id) _selectedFibId.value = _fibOverlays.value.lastOrNull()?.id
        persistFibs()
    }

    fun selectFib(id: String?) {
        _selectedFibId.value = id
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

    fun setFibColor(id: String, hex: String) = updateFib(id, { it.copy(colorHex = hex) })
    fun setFibWidth(id: String, w: Float) = updateFib(id, { it.copy(width = w.coerceIn(0.5f, 3f)) })
    fun toggleFibLevel(id: String, ratio: Float) = updateFib(id, {
        val next = it.enabledLevels.toMutableSet()
        if (ratio in next) next.remove(ratio) else next.add(ratio)
        // ponytail: nunca dejes el fibo sin niveles, vuelve al default
        it.copy(enabledLevels = next.ifEmpty { DEFAULT_FIB_LEVELS.toSet() })
    })
    fun toggleFibHidden(id: String) = updateFib(id, { it.copy(hidden = !it.hidden) })
    fun toggleFibLocked(id: String) = updateFib(id, { it.copy(locked = !it.locked) })

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
                    
                    if (currentPrice > 0.0) {
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
                            updatedCandles.toChartComputation(_state.value.selectedInterval)
                        }
                        
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
                            smc = chartData.smc
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }
        }
    }

    fun loadChartData(interval: String) {
        val normalizedInterval = interval.trim()
        if (normalizedInterval == state.value.selectedInterval && state.value.candles.isNotEmpty()) return

        chartJob?.cancel()
        chartJob = viewModelScope.launch {
            _state.value = state.value.copy(selectedInterval = normalizedInterval, isLoading = true, error = "")
            try {
                val rawKlines = repository.getKlines(
                    symbol,
                    // ponytail: MEXC mapea+agrega en repo, Binance usa su formato
                    if (source == "MEXC") normalizedInterval else normalizedInterval.toBinanceInterval(),
                    source
                )
                val chartData = withContext(Dispatchers.Default) {
                    val candles = rawKlines.toCandles().let {
                        if (source == "MEXC") it else it.aggregateForInterval(normalizedInterval)
                    }

                    if (candles.isEmpty()) {
                        return@withContext ChartComputation()
                    }

                    candles.toChartComputation(normalizedInterval)
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
                    isLoading = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logNonFatal("Chart data load failed for $symbol/$normalizedInterval", e)
                _state.value = state.value.copy(isLoading = false, error = e.message ?: "Error")
            }
        }
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

    private fun List<CandleData>.toChartComputation(interval: String = ""): ChartComputation {
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

        // ponytail: SMA por periodo, una pasada O(n) cada una, fuera del hilo principal
        val maLines = mutableMapOf<Int, List<Pair<Long, Double>>>()
        for (maPeriod in IndicatorPrefs.MA_PERIODS) {
            if (size < maPeriod) continue
            val line = mutableListOf<Pair<Long, Double>>()
            var sum = 0.0
            for (i in indices) {
                sum += this[i].close
                if (i >= maPeriod) sum -= this[i - maPeriod].close
                if (i >= maPeriod - 1) line.add(i.toLong() to sum / maPeriod)
            }
            maLines[maPeriod] = line
        }

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

        // ponytail: RSI(14) alineado a vela para el subpanel, reusa el calculo de arriba
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
            // ponytail: SMC derivado de las velas, se recalcula solo al cambiar TF
            smc = computeSmc(this, interval)
        )
    }

    private fun calculateRSI(candles: List<CandleData>): List<Double> {
        val rsi = mutableListOf<Double>()
        val period = STOCH_RSI_PERIOD
        if (candles.size <= period) return emptyList()
        
        var avgGain = 0.0
        var avgLoss = 0.0
        
        for (i in 1..period) {
            val diff = candles[i].close - candles[i-1].close
            if (diff >= 0) avgGain += diff else avgLoss -= diff
        }
        avgGain /= period
        avgLoss /= period
        
        rsi.add(if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss)))
        
        for (i in period + 1 until candles.size) {
            val diff = candles[i].close - candles[i-1].close
            val gain = if (diff >= 0) diff else 0.0
            val loss = if (diff < 0) -diff else 0.0
            
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
            
            rsi.add(if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss)))
        }
        
        // Pad beginning with zeros to match candle indices
        val padding = List(period) { 0.0 }
        return padding + rsi
    }

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
        val chunkSize = when (interval) {
            "5d" -> 5
            "2w" -> 2
            else -> return this
        }

        val aggregated = ArrayList<CandleData>((size + chunkSize - 1) / chunkSize)
        var index = 0
        while (index < size) {
            val first = this[index]
            var last = first
            var high = first.high
            var low = first.low
            var volume = 0.0
            var takerBuy = 0.0
            val endExclusive = (index + chunkSize).coerceAtMost(size)

            for (itemIndex in index until endExclusive) {
                val item = this[itemIndex]
                last = item
                high = high.coerceAtLeast(item.high)
                low = low.coerceAtMost(item.low)
                volume += item.volume
                takerBuy += item.takerBuyVol
            }

            aggregated.add(
                CandleData(
                    time = first.time,
                    open = first.open,
                    high = high,
                    low = low,
                    close = last.close,
                    volume = volume,
                    takerBuyVol = takerBuy
                )
            )
            index += chunkSize
        }

        return aggregated
    }

    private companion object {
        const val TAG = "CryptoDetailVM"
        const val DETAIL_REFRESH_MS = 5_000L
        const val DEFAULT_CHART_INTERVAL = "15m"
        const val STOCH_RSI_PERIOD = 14
        const val STOCH_SMOOTH_PERIOD = 3
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
    val maLines: Map<Int, List<Pair<Long, Double>>> = emptyMap(),
    val rsi: List<Pair<Long, Double>> = emptyList(),
    val smc: SmcData = SmcData(),
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
    // ponytail: taker buy base volume (indice 9 de klines Binance), sell = volume - buy
    val takerBuyVol: Double = 0.0
)

private data class ChartComputation(
    val candles: List<CandleData> = emptyList(),
    val bbUpper: List<Pair<Long, Double>> = emptyList(),
    val bbMiddle: List<Pair<Long, Double>> = emptyList(),
    val bbLower: List<Pair<Long, Double>> = emptyList(),
    val stochK: List<Pair<Long, Double>> = emptyList(),
    val stochD: List<Pair<Long, Double>> = emptyList(),
    val maLines: Map<Int, List<Pair<Long, Double>>> = emptyMap(),
    val rsi: List<Pair<Long, Double>> = emptyList(),
    val smc: SmcData = SmcData()
)
