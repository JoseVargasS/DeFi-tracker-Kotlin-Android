package com.defitracker.app.presentation.crypto_detail

import com.defitracker.app.core.Brokers
import com.defitracker.app.core.Timeframes

// El intervalo que pide cada fuente: MEXC usa su plan propio, TV su resolucion y el resto los
// strings de Binance (Bitunix y BingX usan los mismos). Fuente unica: el chart y el escaner de
// alertas tienen que pedir exactamente las mismas velas para dar el mismo resultado.
internal fun intervalForSource(source: String, tf: String): String = when (source) {
    Brokers.MEXC -> tf
    Brokers.TV -> Timeframes.tradingViewResolution(tf)
    else -> Timeframes.binanceInterval(tf)
}

// MEXC ya devuelve las velas agregadas desde el repo; el resto las agrega aca (5d, 2w).
internal fun List<CandleData>.forSourceInterval(source: String, tf: String): List<CandleData> =
    if (source == Brokers.MEXC) this else aggregateForInterval(tf)

// Agregado de calendario (5d, 2w): los buckets van anclados al calendario, no al indice,
// asi los times no bailan en cada fetch.
internal fun List<CandleData>.aggregateForInterval(interval: String): List<CandleData> {
    val chunkDurMs = Timeframes.calendarAggregation(interval) ?: return this
    if (isEmpty()) return this
    val weekAnchored = Timeframes.find(interval)?.weekAnchored == true
    val buckets = LinkedHashMap<Long, MutableList<CandleData>>()
    for (c in this) {
        val key = if (weekAnchored) {
            c.time - ((c.time - Timeframes.WEEK_ANCHOR_MS) % chunkDurMs)
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

// Bitunix no publica la vela en curso: el chart la sintetiza plana y la llena con el precio del
// socket. El escaner la sintetiza con el precio REST para que el resultado sea el mismo.
// En las fuentes que ya la traen (Binance, MEXC, BingX) esto es no-op.
internal fun List<CandleData>.withLiveCandlePrice(
    interval: String,
    price: Double,
    nowMs: Long = System.currentTimeMillis()
): List<CandleData> {
    val withLive = withLiveCandle(interval, nowMs)
    if (price <= 0.0 || withLive.size <= size) return withLive
    val last = withLive.last()
    val filled = last.copy(
        high = maxOf(last.high, price),
        low = minOf(last.low, price),
        close = price
    )
    return withLive.dropLast(1) + filled
}
