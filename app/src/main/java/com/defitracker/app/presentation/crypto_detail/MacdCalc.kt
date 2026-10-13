package com.defitracker.app.presentation.crypto_detail

// MACD(12, 26, 9) clasico fijo: DIF = EMA12 - EMA26, DEA = EMA9(DIF), histograma = 2 * (DIF - DEA)

data class MacdData(
    val dif: List<Pair<Long, Double>> = emptyList(),
    val dea: List<Pair<Long, Double>> = emptyList(),
    val hist: List<Pair<Long, Double>> = emptyList()
)

fun calculateMacd(candles: List<CandleData>): MacdData {
    val fast = 12
    val slow = 26
    val signal = 9
    if (candles.size < slow + signal - 1) return MacdData()
    val closes = candles.map { it.close }
    val fastEma = emaSeries(closes, fast)
    val slowEma = emaSeries(closes, slow)
    val difRaw = DoubleArray(closes.size) { Double.NaN }
    for (i in slow - 1 until closes.size) {
        difRaw[i] = fastEma[i] - slowEma[i]
    }
    // DEA con la misma semilla SMA que el resto de la app
    val firstDea = slow + signal - 2
    var seed = 0.0
    for (i in slow - 1..firstDea) seed += difRaw[i]
    var dea = seed / signal
    val deaRaw = DoubleArray(closes.size) { Double.NaN }
    deaRaw[firstDea] = dea
    val k = 2.0 / (signal + 1)
    for (i in firstDea + 1 until closes.size) {
        dea = difRaw[i] * k + dea * (1 - k)
        deaRaw[i] = dea
    }
    val dif = ArrayList<Pair<Long, Double>>(closes.size)
    val deaOut = ArrayList<Pair<Long, Double>>(closes.size)
    val hist = ArrayList<Pair<Long, Double>>(closes.size)
    for (i in firstDea until closes.size) {
        val d = difRaw[i]
        val s = deaRaw[i]
        dif.add(i.toLong() to d)
        deaOut.add(i.toLong() to s)
        hist.add(i.toLong() to (2.0 * (d - s)))
    }
    return MacdData(dif, deaOut, hist)
}
