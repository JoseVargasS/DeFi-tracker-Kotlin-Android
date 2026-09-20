package com.defitracker.app.presentation.crypto_detail

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// pulso del momento: sesgo + confluencias, funciones puras sin Android (testeables)
enum class PulseBias { BULLISH, BEARISH, NEUTRAL }

data class PulseItem(
    val id: String,
    val label: String,
    val bullish: Boolean?,
    val hit: Boolean,
    val detail: String? = null
)

data class PulseAnalysis(
    val bias: PulseBias,
    val score: Int,
    val contraTrend: Boolean,
    val items: List<PulseItem>,
    val support: Double?,
    val resistance: Double?,
    val headline: String,
    val updatedAt: Long = System.currentTimeMillis()
)

data class SrZone(
    val top: Double,
    val bottom: Double,
    val touches: Int,
    val isSupport: Boolean
)

private fun pulseAtr(candles: List<CandleData>, period: Int = 14): Double {
    if (candles.size < 2) return 0.0
    var sum = 0.0
    var n = 0
    var i = max(1, candles.size - period)
    while (i < candles.size) {
        val prev = candles[i - 1].close
        sum += maxOf(candles[i].high - candles[i].low, abs(candles[i].high - prev), abs(candles[i].low - prev))
        n++
        i++
    }
    return if (n > 0) sum / n else 0.0
}

// zonas horizontales por cluster de swings con tolerancia ATR, minimo 3 toques
fun detectSrZones(candles: List<CandleData>, lookback: Int = 150, minTouches: Int = 3): List<SrZone> {
    if (candles.size < 30) return emptyList()
    val sample = candles.takeLast(lookback.coerceAtMost(candles.size))
    val atr = pulseAtr(candles)
    if (atr <= 0.0) return emptyList()
    val tol = atr * 0.3
    val swings = detectSwings(sample, 5)
    fun cluster(prices: List<Double>, isSupport: Boolean): List<SrZone> {
        if (prices.isEmpty()) return emptyList()
        val sorted = prices.sorted()
        val out = ArrayList<SrZone>()
        var group = ArrayList<Double>()
        group.add(sorted[0])
        for (i in 1 until sorted.size) {
            if (sorted[i] - group.last() <= tol) {
                group.add(sorted[i])
            } else {
                if (group.size >= minTouches) {
                    out.add(SrZone(group.maxOrNull() ?: 0.0, group.minOrNull() ?: 0.0, group.size, isSupport))
                }
                group = ArrayList<Double>()
                group.add(sorted[i])
            }
        }
        if (group.size >= minTouches) {
            out.add(SrZone(group.maxOrNull() ?: 0.0, group.minOrNull() ?: 0.0, group.size, isSupport))
        }
        return out
    }
    return cluster(swings.filter { !it.isHigh }.map { it.price }, true) +
        cluster(swings.filter { it.isHigh }.map { it.price }, false)
}

fun fmtPulsePrice(v: Double): String {
    if (!v.isFinite()) return "--"
    val s = when {
        v >= 1000.0 -> String.format(java.util.Locale.US, "%,.1f", v)
        v >= 1.0 -> String.format(java.util.Locale.US, "%.4f", v)
        else -> String.format(java.util.Locale.US, "%.6f", v)
    }
    return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
}

private fun smaLast(values: List<Double>, period: Int): Double? {
    if (values.size < period) return null
    return values.takeLast(period).average()
}

private fun emaLast(values: List<Double>, period: Int): Double? {
    if (values.size < period) return null
    val k = 2.0 / (period + 1)
    var ema = values.takeLast(period).take(period).average()
    for (i in values.size - period + 1 until values.size) {
        ema = values[i] * k + ema * (1 - k)
    }
    return ema
}

// matriz 21/50/200 x TFs: cada par (periodo, TF) vota si el precio esta sobre ambas o bajo ambas
data class MaMatrix(
    val bullVotes: Int,
    val bearVotes: Int,
    val perTf: List<String>
)

val PULSE_MA_PERIODS = listOf(21, 50, 200)
val PULSE_MA_TFS = listOf("15m", "1h", "4h")

fun maMatrixScore(
    closesByTf: Map<String, List<Double>>,
    chartTf: String,
    chartCloses: List<Double>,
    close: Double
): MaMatrix {
    var bull = 0
    var bear = 0
    val perTf = ArrayList<String>()
    for (tf in PULSE_MA_TFS) {
        val values = if (tf == chartTf) chartCloses else closesByTf[tf].orEmpty()
        var tfBull = 0
        var tfBear = 0
        for (period in PULSE_MA_PERIODS) {
            val sma = smaLast(values, period)
            val ema = emaLast(values, period)
            if (sma == null || ema == null) continue
            when {
                close > sma && close > ema -> tfBull++
                close < sma && close < ema -> tfBear++
            }
        }
        bull += tfBull
        bear += tfBear
        val arrow = when {
            tfBull > tfBear -> "▲"
            tfBear > tfBull -> "▼"
            else -> "·"
        }
        perTf.add("$tf $tfBull/3 $arrow")
    }
    return MaMatrix(bull, bear, perTf)
}

// analisis completo sobre las velas del chart + cierres por TF (pueden venir vacios)
fun analyzePulse(
    candles: List<CandleData>,
    interval: String,
    closesByTf: Map<String, List<Double>>
): PulseAnalysis? {
    if (candles.size < 60) return null
    val idx = candles.size - 2
    val c = candles[idx]
    val close = candles.lastOrNull()?.close ?: c.close
    val range = (c.high - c.low).takeIf { it > 0 } ?: return null
    val body = abs(c.close - c.open) / range
    val atr = pulseAtr(candles).takeIf { it > 0 } ?: return null

    // 1. zona + rebote ahora
    val zones = detectSrZones(candles)
    val sup = zones.filter { it.isSupport && it.bottom <= close }.maxByOrNull { it.top }
    val res = zones.filter { !it.isSupport && it.top >= close }.minByOrNull { it.bottom }
    var bounceDir: Boolean? = null
    sup?.let { z ->
        if (c.low <= z.top && c.low >= z.bottom - atr * 0.25 && c.close > z.top && body >= 0.3) bounceDir = true
    }
    res?.let { z ->
        if (c.high >= z.bottom && c.high <= z.top + atr * 0.25 && c.close < z.bottom && body >= 0.3) bounceDir = false
    }

    // 2. matriz de medias 21/50/200 en 15m, 1h y 4h
    val chartCloses = candles.map { it.close }
    val matrix = maMatrixScore(closesByTf, interval, chartCloses, close)
    val maBull = matrix.bullVotes - matrix.bearVotes >= 3
    val maBear = matrix.bearVotes - matrix.bullVotes >= 3
    val maTotal = matrix.bullVotes + matrix.bearVotes
    val maLabel = when {
        maTotal == 0 -> "Medias (sin dato 15m/1h/4h)"
        maBull -> "Medias ${matrix.bullVotes}/9 alcistas"
        maBear -> "Medias ${matrix.bearVotes}/9 bajistas"
        else -> "Medias mixtas ${matrix.bullVotes}-${matrix.bearVotes}"
    }

    // 3. divergencia RSI fresca misma vela de analisis
    val rsi = calculateRsi(candles)
    var divDir: Boolean? = null
    if (rsi.size == candles.size) {
        val fresh = (detectRsiDivergences(candles, rsi, RSI_DIV_EARLY_LOOKBACK) + detectRsiDivergences(candles, rsi))
            .filter { candles.size - 1 - it.idx2 in 0..3 }
            .maxByOrNull { it.idx2 }
        divDir = fresh?.bullish
    }

    // 4. barrido + reclaim
    val sweepDir = try {
        detectSweepReclaim(candles)?.bullish
    } catch (_: Exception) {
        null
    }

    // 5. estructura (ultimo evento BOS/CHoCH)
    val structDir = try {
        computeSmc(candles, interval).events.maxByOrNull { it.breakIdx }?.bullish
    } catch (_: Exception) {
        null
    }

    val bullVotes = listOf(bounceDir == true, maBull, divDir == true, sweepDir == true, structDir == true).count { it }
    val bearVotes = listOf(bounceDir == false, maBear, divDir == false, sweepDir == false, structDir == false).count { it }
    val bias = when {
        bullVotes > bearVotes -> PulseBias.BULLISH
        bearVotes > bullVotes -> PulseBias.BEARISH
        else -> PulseBias.NEUTRAL
    }
    val score = max(bullVotes, bearVotes)
    val contraTrend = bounceDir != null && structDir != null && bounceDir != structDir

    fun dirLabel(b: Boolean?) = when (b) {
        true -> "alcista"
        false -> "bajista"
        else -> null
    }
    val items = listOf(
        PulseItem(
            "zona",
            bounceDir?.let { "Rebote ${dirLabel(it)} en zona (${sup?.touches ?: res?.touches ?: 0} toques)" } ?: "Sin rebote en zona ahora",
            bounceDir,
            bounceDir != null
        ),
        PulseItem("ma200", maLabel, when {
            maBull -> true
            maBear -> false
            else -> null
        }, maBull || maBear, detail = matrix.perTf.joinToString(" · ")),
        PulseItem(
            "div",
            divDir?.let { "Div ${dirLabel(it)} fresca en RSI" } ?: "Sin div fresca",
            divDir,
            divDir != null
        ),
        PulseItem(
            "sweep",
            sweepDir?.let { "Barrido + reclaim ${dirLabel(it)}" } ?: "Sin barrido reciente",
            sweepDir,
            sweepDir != null
        ),
        PulseItem(
            "struct",
            structDir?.let { "Estructura ${dirLabel(it)}" } ?: "Estructura mixta",
            structDir,
            structDir != null
        )
    )

    val headline = when (bias) {
        PulseBias.BULLISH -> if (sup != null) {
            "Sesgo alcista · esperar reacción en ${fmtPulsePrice(sup.bottom)}–${fmtPulsePrice(sup.top)}" +
                (if (contraTrend) " (contratendencia)" else "")
        } else {
            "Sesgo alcista · sin zona cercana, no perseguir" + (if (contraTrend) " (contratendencia)" else "")
        }
        PulseBias.BEARISH -> if (res != null) {
            "Sesgo bajista · techo en ${fmtPulsePrice(res.bottom)}–${fmtPulsePrice(res.top)}" +
                (if (contraTrend) " (contratendencia)" else "")
        } else {
            "Sesgo bajista · sin techo cercano, no perseguir" + (if (contraTrend) " (contratendencia)" else "")
        }
        PulseBias.NEUTRAL -> "Sin ventaja clara · esperar cierre fuera de rango"
    }

    return PulseAnalysis(
        bias = bias,
        score = score,
        contraTrend = contraTrend,
        items = items,
        support = sup?.top,
        resistance = res?.bottom,
        headline = headline
    )
}
