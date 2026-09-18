package com.defitracker.app.presentation.crypto_detail

// RSI(14) + parseo de klines compartido entre chart y worker de alertas

fun calculateRsi(candles: List<CandleData>, period: Int = 14): List<Double> {
    val rsi = mutableListOf<Double>()
    if (candles.size <= period) return emptyList()
    var avgGain = 0.0
    var avgLoss = 0.0
    for (i in 1..period) {
        val diff = candles[i].close - candles[i - 1].close
        if (diff >= 0) avgGain += diff else avgLoss -= diff
    }
    avgGain /= period
    avgLoss /= period
    rsi.add(if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss)))
    for (i in period + 1 until candles.size) {
        val diff = candles[i].close - candles[i - 1].close
        val gain = if (diff >= 0) diff else 0.0
        val loss = if (diff < 0) -diff else 0.0
        avgGain = (avgGain * (period - 1) + gain) / period
        avgLoss = (avgLoss * (period - 1) + loss) / period
        rsi.add(if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss)))
    }
    return List(period) { 0.0 } + rsi
}

fun List<List<Any>>.toCandleList(): List<CandleData> {
    val out = ArrayList<CandleData>(size)
    forEach { row ->
        val time = row.getOrNull(0).toLongValue()
        if (time > 0L) {
            out.add(
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
    return out
}

private fun Any?.toLongValue(): Long = when (this) {
    is Number -> toLong()
    is String -> toLongOrNull() ?: toDoubleOrNull()?.toLong() ?: 0L
    else -> toString().toLongOrNull() ?: toString().toDoubleOrNull()?.toLong() ?: 0L
}

private fun Any?.toDoubleValue(): Double = when (this) {
    is Number -> toDouble()
    is String -> toDoubleOrNull() ?: 0.0
    else -> toString().toDoubleOrNull() ?: 0.0
}
