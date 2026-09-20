package com.defitracker.app.presentation.crypto_detail

// señales de entrada sobre velas, funciones puras sin Android (testeables)
data class TradeSignal(
    val kind: String, // MA_REJECT_BULL, FVG_TAP_BEAR, SWEEP_BULL, ...
    val bullish: Boolean,
    val label: String // "EMA50", "FVG", "Barrido"
)

private fun closesOf(candles: List<CandleData>): List<Double> = candles.map { it.close }

private fun smaSeries(values: List<Double>, period: Int): List<Double> {
    val out = ArrayList<Double>(values.size)
    var sum = 0.0
    for (i in values.indices) {
        sum += values[i]
        if (i >= period) sum -= values[i - period]
        out.add(if (i >= period - 1) sum / period else Double.NaN)
    }
    return out
}

private fun emaSeries(values: List<Double>, period: Int): List<Double> {
    val out = ArrayList<Double>(values.size)
    if (values.size < period) return out
    val k = 2.0 / (period + 1)
    var ema = values.take(period).average()
    for (i in values.indices) {
        if (i < period - 1) {
            out.add(Double.NaN)
        } else if (i == period - 1) {
            out.add(ema)
        } else {
            ema = values[i] * k + ema * (1 - k)
            out.add(ema)
        }
    }
    return out
}

// mechazo en media: mecha >=40% del rango perfora SMA/EMA y cierra de vuelta, ultima cerrada
fun detectMaWickRejection(candles: List<CandleData>): TradeSignal? {
    if (candles.size < 52) return null
    val idx = candles.size - 2
    val c = candles[idx]
    val range = c.high - c.low
    if (range <= 0.0) return null
    val closes = closesOf(candles)
    // la 50 manda, luego la 21; EMA antes que SMA
    val checks = listOf(
        Triple(50, false, "EMA50"),
        Triple(50, true, "SMA50"),
        Triple(21, false, "EMA21"),
        Triple(21, true, "SMA21")
    )
    for ((period, useSma, label) in checks) {
        val series = if (useSma) smaSeries(closes, period) else emaSeries(closes, period)
        val ma = series.getOrNull(idx) ?: continue
        if (!ma.isFinite()) continue
        val lowerWick = minOf(c.open, c.close) - c.low
        val upperWick = c.high - maxOf(c.open, c.close)
        if (c.low < ma && c.close > ma && lowerWick >= range * 0.4) {
            return TradeSignal("MA_REJECT_BULL", true, label)
        }
        if (c.high > ma && c.close < ma && upperWick >= range * 0.4) {
            return TradeSignal("MA_REJECT_BEAR", false, label)
        }
    }
    return null
}

// toque en FVG sin mitigar + cierre de rechazo, ultima cerrada
fun detectFvgTap(candles: List<CandleData>): TradeSignal? {
    if (candles.size < 10) return null
    val idx = candles.size - 2
    val c = candles[idx]
    val zones = detectFvg(candles).filter { !it.mitigated && it.startIdx < idx }
    for (z in zones) {
        if (z.bullish && c.low <= z.top && c.low >= z.bottom && c.close > z.top) {
            return TradeSignal("FVG_TAP_BULL", true, "FVG")
        }
        if (!z.bullish && c.high >= z.bottom && c.high <= z.top && c.close < z.bottom) {
            return TradeSignal("FVG_TAP_BEAR", false, "FVG")
        }
    }
    return null
}

// barrido de maximo/minimo previo + cierre de vuelta adentro (SFP), ultima cerrada
fun detectSweepReclaim(candles: List<CandleData>): TradeSignal? {
    if (candles.size < 12) return null
    val idx = candles.size - 2
    val c = candles[idx]
    val swings = detectSwings(candles, 3)
    val lastHigh = swings.filter { it.isHigh && it.idx < idx }.maxByOrNull { it.idx }
    val lastLow = swings.filter { !it.isHigh && it.idx < idx }.maxByOrNull { it.idx }
    if (lastHigh != null && c.high > lastHigh.price && c.close < lastHigh.price) {
        return TradeSignal("SWEEP_BEAR", false, "Barrido")
    }
    if (lastLow != null && c.low < lastLow.price && c.close > lastLow.price) {
        return TradeSignal("SWEEP_BULL", true, "Barrido")
    }
    return null
}
