package com.defitracker.app.presentation.crypto_detail

// divergencias RSI estilo TradingView, funciones puras sobre velas (testeables sin Android)

const val RSI_DIV_LOOKBACK = 5
const val RSI_DIV_EARLY_LOOKBACK = 2
const val RSI_DIV_MIN_SEP = 5
const val RSI_DIV_MAX_SEP = 60
// como TV, todas las del rango (el Pine plotea sin recorte)

enum class RsiDivKind { REG_BULL, REG_BEAR, HID_BULL, HID_BEAR }

data class RsiDiv(
    val idx1: Int,
    val idx2: Int,
    val rsi1: Double,
    val rsi2: Double,
    val kind: RsiDivKind,
    val early: Boolean = false
) {
    val bullish: Boolean = kind == RsiDivKind.REG_BULL || kind == RsiDivKind.HID_BULL
}

// fractal N por lado sobre la serie del RSI
private fun fractalPivots(values: List<Double>, lookback: Int, findHigh: Boolean): List<Int> {
    if (values.size < lookback * 2 + 1) return emptyList()
    val out = ArrayList<Int>()
    for (i in lookback until values.size - lookback) {
        val v = values[i]
        var ok = true
        var j = i - lookback
        while (j <= i + lookback) {
            if (j != i) {
                if (findHigh && values[j] > v) { ok = false; break }
                if (!findHigh && values[j] < v) { ok = false; break }
            }
            j++
        }
        if (ok) out.add(i)
    }
    return out
}

fun detectRsiDivergences(
    candles: List<CandleData>,
    rsi: List<Double>,
    lookback: Int = RSI_DIV_LOOKBACK
): List<RsiDiv> {
    if (candles.isEmpty() || rsi.size != candles.size) return emptyList()
    val lows = candles.map { it.low }
    val highs = candles.map { it.high }
    // ignora el relleno de ceros del inicio del RSI
    val firstValid = rsi.indexOfFirst { it > 0.0 }.let { if (it < 0) return emptyList() else it }
    val out = ArrayList<RsiDiv>()

    fun scan(pivRsi: List<Int>, isLow: Boolean) {
        // como TV, pivotes RSI confirmados y precio en esas mismas velas
        var p = 0
        while (p + 1 < pivRsi.size) {
            val a = pivRsi[p]
            val b = pivRsi[p + 1]
            p++
            if (a < firstValid) continue
            val sep = b - a
            if (sep < RSI_DIV_MIN_SEP || sep > RSI_DIV_MAX_SEP) continue
            val priceA = if (isLow) lows[a] else highs[a]
            val priceB = if (isLow) lows[b] else highs[b]
            val rsiA = rsi[a]
            val rsiB = rsi[b]
            if (rsiA <= 0.0 || rsiB <= 0.0) continue
            if (isLow) {
                if (priceB < priceA && rsiB > rsiA) out.add(RsiDiv(a, b, rsiA, rsiB, RsiDivKind.REG_BULL))
                else if (priceB > priceA && rsiB < rsiA) out.add(RsiDiv(a, b, rsiA, rsiB, RsiDivKind.HID_BULL))
            } else {
                if (priceB > priceA && rsiB < rsiA) out.add(RsiDiv(a, b, rsiA, rsiB, RsiDivKind.REG_BEAR))
                else if (priceB < priceA && rsiB > rsiA) out.add(RsiDiv(a, b, rsiA, rsiB, RsiDivKind.HID_BEAR))
            }
        }
    }

    scan(fractalPivots(rsi, lookback, false), true)
    scan(fractalPivots(rsi, lookback, true), false)
    return out.sortedBy { it.idx2 }
}
