package com.defitracker.app.presentation.crypto_detail

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Cálculo incremental del último punto de las series del chart.
 *
 * Todas las series (Bandas, RSI, StochRSI, MACD y MAs) son causales: el valor del
 * índice i sólo depende de las velas 0..i. Como el tick de 2s únicamente cambia la
 * vela en curso (el último índice), la historia cacheada sigue siendo válida y sólo
 * hay que recalcular el último punto, partiendo del estado exacto en el índice n-2.
 *
 * La aritmética replica la del cálculo completo (mismo orden de operaciones) para que
 * el resultado sea idéntico; cualquier caso dudoso devuelve null y el llamador cae al
 * cálculo completo.
 */

internal const val BB_PERIOD = 20
internal const val BB_MULTIPLIER = 2.0
internal const val RSI_PERIOD = 14
internal const val STOCH_RSI_WINDOW = 14
internal const val MACD_FAST = 12
internal const val MACD_SLOW = 26
internal const val MACD_SIGNAL = 9

internal data class ChartComputation(
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
    val smc: SmcData = SmcData(),
    val rsiDiv: List<RsiDiv> = emptyList()
)

// estado en el índice n-2: todo lo que la vela en curso no toca
internal data class IndicatorTailState(
    val bbSum: Double,
    val bbSumSq: Double,
    val rsiAvgGain: Double,
    val rsiAvgLoss: Double,
    val rsiMaSum: Double,
    val macdFastEma: Double,
    val macdSlowEma: Double
)

internal data class IndicatorTailSeed(
    val key: String,
    val computation: ChartComputation,
    val state: IndicatorTailState
)

/**
 * Clave que decide si se puede reusar la cola cacheada.
 *
 * Fuera de la clave queda la vela viva (índice n-1): su cierre/high/low cambian en cada
 * tick y son justo lo que la cola recalcula. Sí entra el tiempo de la vela n-2 (la última
 * cerrada) porque unas MAs de otro TF se alinean por tiempo. Cambios en velas cerradas
 * (sin cambiar tiempo ni tamaño) los cubre `closedVersion`.
 */
internal fun indicatorTailKey(
    symbol: String,
    interval: String,
    prefs: IndicatorPrefs,
    closedVersion: Int,
    candles: List<CandleData>
): String {
    val closedTime = if (candles.size >= 2) candles[candles.size - 2].time else 0L
    return "$symbol|$interval|${prefs.prefsKey()}|$closedVersion|${candles.size}|$closedTime"
}

internal class IndicatorTailCache {
    private var seed: IndicatorTailSeed? = null

    @Synchronized
    fun seedFor(key: String): IndicatorTailSeed? = seed?.takeIf { it.key == key }

    @Synchronized
    fun store(value: IndicatorTailSeed) {
        seed = value
    }

    @Synchronized
    fun clear() {
        seed = null
    }
}

// confirmadas (lookback 5) más tempranas (lookback 2) sin repetir el mismo evento
internal fun mergeConfirmedAndEarly(confirmed: List<RsiDiv>, early: List<RsiDiv>): List<RsiDiv> {
    val fresh = early.map { it.copy(early = true) }.filter { e ->
        confirmed.none { c -> c.kind == e.kind && kotlin.math.abs(c.idx2 - e.idx2) <= 2 }
    }
    return (confirmed + fresh).sortedBy { it.idx2 }
}

// EMA estilo emaSeries: semilla SMA de los primeros `period` cierres
internal fun emaValueAt(closes: List<Double>, period: Int, index: Int): Double? {
    if (period <= 0 || index < period - 1 || index >= closes.size) return null
    val k = 2.0 / (period + 1)
    var ema = 0.0
    for (i in 0 until period) ema += closes[i]
    ema /= period
    for (i in period..index) {
        ema = closes[i] * k + ema * (1 - k)
    }
    return ema
}

/**
 * Corre las acumulaciones hasta el índice n-2 (la última vela cerrada) y devuelve el
 * estado que la cola necesita. Devuelve null si la lista es demasiado corta.
 */
internal fun buildIndicatorTailState(candles: List<CandleData>): IndicatorTailState? {
    val n = candles.size
    if (n < 2) return null
    val lastClosed = n - 2
    // ventanas mínimas para que el estado sea representativo
    if (lastClosed < BB_PERIOD - 1) return null
    if (lastClosed < RSI_PERIOD) return null
    if (lastClosed < MACD_SLOW - 1) return null
    if (lastClosed < RSI_MA_PERIOD - 1) return null

    // Bandas: suma y suma de cuadrados tal como las lleva el cálculo completo
    var bbSum = 0.0
    var bbSumSq = 0.0
    for (i in 0..lastClosed) {
        val c = candles[i].close
        bbSum += c
        bbSumSq += c * c
        if (i >= BB_PERIOD) {
            val dropped = candles[i - BB_PERIOD].close
            bbSum -= dropped
            bbSumSq -= dropped * dropped
        }
    }

    // RSI(Wilder): mismos pasos que calculateRsi, hasta n-2
    var avgGain = 0.0
    var avgLoss = 0.0
    for (i in 1..RSI_PERIOD) {
        val diff = candles[i].close - candles[i - 1].close
        if (diff >= 0) avgGain += diff else avgLoss -= diff
    }
    avgGain /= RSI_PERIOD
    avgLoss /= RSI_PERIOD
    for (i in RSI_PERIOD + 1..lastClosed) {
        val diff = candles[i].close - candles[i - 1].close
        val gain = if (diff >= 0) diff else 0.0
        val loss = if (diff < 0) -diff else 0.0
        avgGain = (avgGain * (RSI_PERIOD - 1) + gain) / RSI_PERIOD
        avgLoss = (avgLoss * (RSI_PERIOD - 1) + loss) / RSI_PERIOD
    }

    // suma rodante del RSI-MA (signal line) hasta n-2, con el relleno de ceros del RSI
    val rsiValues = calculateRsi(candles)
    var rsiMaSum = 0.0
    if (rsiValues.isNotEmpty()) {
        for (i in 0..lastClosed) {
            if (i >= rsiValues.size) break
            rsiMaSum += rsiValues[i]
            if (i >= RSI_MA_PERIOD) rsiMaSum -= rsiValues[i - RSI_MA_PERIOD]
        }
    }

    val closes = candles.map { it.close }
    val fastEma = emaValueAt(closes, MACD_FAST, lastClosed) ?: return null
    val slowEma = emaValueAt(closes, MACD_SLOW, lastClosed) ?: return null

    return IndicatorTailState(
        bbSum = bbSum,
        bbSumSq = bbSumSq,
        rsiAvgGain = avgGain,
        rsiAvgLoss = avgLoss,
        rsiMaSum = rsiMaSum,
        macdFastEma = fastEma,
        macdSlowEma = slowEma
    )
}

/**
 * Recalcula sólo el último punto de cada serie. Devuelve null cuando no puede
 * garantizar equivalencia (series de otra forma, warm-up incompleto, etc.).
 */
internal fun recomputeIndicatorTail(
    candles: List<CandleData>,
    seed: IndicatorTailSeed,
    interval: String,
    mas: List<MaConfig>,
    prefs: IndicatorPrefs,
    smc: SmcData
): ChartComputation? {
    val n = candles.size
    if (n < 2) return null
    val prev = seed.computation
    val state = seed.state
    val lastIdx = (n - 1).toLong()
    val close = candles[n - 1].close

    fun tailed(
        series: List<Pair<Long, Double>>,
        value: Double
    ): List<Pair<Long, Double>>? {
        if (series.isEmpty() || series.last().first != lastIdx) return null
        return series.subList(0, series.size - 1) + (lastIdx to value)
    }

    // ── Bandas de Bollinger ──
    var bbSum = state.bbSum
    var bbSumSq = state.bbSumSq
    bbSum += close
    bbSumSq += close * close
    val dropIndex = n - 1 - BB_PERIOD
    if (dropIndex >= 0) {
        val dropped = candles[dropIndex].close
        bbSum -= dropped
        bbSumSq -= dropped * dropped
    }
    val bbSma = bbSum / BB_PERIOD
    val variance = (bbSumSq / BB_PERIOD) - bbSma.pow(2.0)
    val stdDev = sqrt(variance.coerceAtLeast(0.0))
    val bbMiddle = tailed(prev.bbMiddle, bbSma) ?: return null
    val bbUpper = tailed(prev.bbUpper, bbSma + BB_MULTIPLIER * stdDev) ?: return null
    val bbLower = tailed(prev.bbLower, bbSma - BB_MULTIPLIER * stdDev) ?: return null

    // ── RSI ──
    val diff = close - candles[n - 2].close
    val gain = if (diff >= 0) diff else 0.0
    val loss = if (diff < 0) -diff else 0.0
    val avgGain = (state.rsiAvgGain * (RSI_PERIOD - 1) + gain) / RSI_PERIOD
    val avgLoss = (state.rsiAvgLoss * (RSI_PERIOD - 1) + loss) / RSI_PERIOD
    val rsiValue = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
    val rsi = tailed(prev.rsi, rsiValue) ?: return null
    val rsiValues = rsi.map { it.second }

    // ── RSI-MA (signal line) ──
    if (rsi.size < RSI_MA_PERIOD) return null
    var rsiMaSum = state.rsiMaSum + rsiValue
    if (n - 1 >= RSI_MA_PERIOD) rsiMaSum -= rsiValues[n - 1 - RSI_MA_PERIOD]
    val rsiMa = tailed(prev.rsiMa, rsiMaSum / RSI_MA_PERIOD) ?: return null

    // ── StochRSI (K suavizada 3, D suavizada 3) ──
    val stochK: List<Pair<Long, Double>>
    val stochD: List<Pair<Long, Double>>
    if (rsiValues.size >= STOCH_RSI_WINDOW) {
        if (prev.stochK.isEmpty() || prev.stochD.isEmpty()) return null
        if (n - 6 < 0) return null
        fun stochAt(i: Int): Double {
            val from = i - STOCH_RSI_WINDOW + 1
            if (from < 0) return 0.0
            var low = rsiValues[from]
            var high = low
            for (j in from..i) {
                val v = rsiValues[j]
                if (v < low) low = v
                if (v > high) high = v
            }
            val current = rsiValues[i]
            return if (high - low != 0.0) (current - low) / (high - low) * 100.0 else 0.0
        }
        fun mean3(a: Double, b: Double, c: Double) = (a + b + c) / 3.0
        // smoothK[i] = media de stochRSI[i-2..i]
        val kNew = mean3(stochAt(n - 3), stochAt(n - 2), stochAt(n - 1))
        val kPrev1 = mean3(stochAt(n - 4), stochAt(n - 3), stochAt(n - 2))
        val kPrev2 = mean3(stochAt(n - 5), stochAt(n - 4), stochAt(n - 3))
        // smoothD[i] = media de smoothK[i-2..i]
        val dNew = mean3(kPrev2, kPrev1, kNew)
        stochK = tailed(prev.stochK, kNew) ?: return null
        stochD = tailed(prev.stochD, dNew) ?: return null
    } else {
        stochK = emptyList()
        stochD = emptyList()
    }

    // ── MACD ──
    val macdDif: List<Pair<Long, Double>>
    val macdDea: List<Pair<Long, Double>>
    val macdHist: List<Pair<Long, Double>>
    if (prev.macdDif.size >= 2 && prev.macdDea.size >= 2 && prev.macdHist.size >= 2) {
        val kFast = 2.0 / (MACD_FAST + 1)
        val kSlow = 2.0 / (MACD_SLOW + 1)
        val kSignal = 2.0 / (MACD_SIGNAL + 1)
        val fastEma = close * kFast + state.macdFastEma * (1 - kFast)
        val slowEma = close * kSlow + state.macdSlowEma * (1 - kSlow)
        val dif = fastEma - slowEma
        val deaPrev = prev.macdDea[prev.macdDea.size - 2].second
        val dea = dif * kSignal + deaPrev * (1 - kSignal)
        macdDif = tailed(prev.macdDif, dif) ?: return null
        macdDea = tailed(prev.macdDea, dea) ?: return null
        macdHist = tailed(prev.macdHist, 2.0 * (dif - dea)) ?: return null
    } else {
        // historial MACD mínimo: con exactamente slow+signal-1 velas la serie tiene
        // UN punto y en ese índice el DEA es la semilla (media de 9 DIF), no un paso
        // EMA, así que no se puede derivar del estado cacheado. Se recalcula sólo el
        // MACD completo, que con esa cantidad de velas es baratísimo.
        val macd = calculateMacd(candles)
        macdDif = macd.dif
        macdDea = macd.dea
        macdHist = macd.hist
    }

    // ── Medias móviles ──
    val maLines = computeMaLinesTail(candles, prev, interval, mas) ?: return null

    val wantDiv = prefs.rsiDivVisible
    return ChartComputation(
        candles = candles,
        bbUpper = bbUpper,
        bbMiddle = bbMiddle,
        bbLower = bbLower,
        stochK = stochK,
        stochD = stochD,
        maLines = maLines,
        rsi = rsi,
        rsiMa = rsiMa,
        macdDif = macdDif,
        macdDea = macdDea,
        macdHist = macdHist,
        smc = smc,
        // igual que en el cálculo completo: no se cachean porque el pivote de n-6
        // se confirma con una ventana que incluye la vela viva
        rsiDiv = if (wantDiv && rsiValues.isNotEmpty()) mergeConfirmedAndEarly(
            detectRsiDivergences(candles, rsiValues),
            detectRsiDivergences(candles, rsiValues, RSI_DIV_EARLY_LOOKBACK)
        ) else emptyList()
    )
}

// MAs del TF del chart: la SMA se recalcula con la ventana, la EMA con el valor previo.
// Las MAs de otro TF no dependen de la vela viva: se reusan tal cual.
private fun computeMaLinesTail(
    candles: List<CandleData>,
    prev: ChartComputation,
    interval: String,
    mas: List<MaConfig>
): Map<String, List<Pair<Long, Double>>>? {
    val n = candles.size
    val lastIdx = (n - 1).toLong()
    val out = HashMap<String, List<Pair<Long, Double>>>()
    val seen = mutableSetOf<Triple<MaType, Int, String>>()
    val ordered = mas.sortedBy { if (it.timeframe == "chart") 0 else 1 }
    for (ma in ordered) {
        if (!ma.visible) continue
        val effectiveTf = if (ma.timeframe == "chart" || ma.timeframe == interval) interval else ma.timeframe
        val dedupeKey = Triple(ma.type, ma.period, effectiveTf)
        if (!seen.add(dedupeKey)) continue
        if (ma.timeframe != "chart" && ma.timeframe != interval) {
            prev.maLines[ma.id]?.let { out[ma.id] = it }
            continue
        }
        if (n < ma.period) continue
        val line = prev.maLines[ma.id] ?: return null
        if (line.isEmpty() || line.last().first != lastIdx) return null
        val value = when (ma.type) {
            MaType.EMA -> {
                val prevEma = line.getOrNull(line.size - 2)?.second ?: return null
                val k = 2.0 / (ma.period + 1)
                candles[n - 1].close * k + prevEma * (1 - k)
            }
            else -> {
                var sum = 0.0
                for (i in n - ma.period until n) sum += candles[i].close
                sum / ma.period
            }
        }
        out[ma.id] = line.subList(0, line.size - 1) + (lastIdx to value)
    }
    return out
}
