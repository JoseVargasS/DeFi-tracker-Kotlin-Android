package com.defitracker.app.data.repository

import com.defitracker.app.core.Timeframes
import com.defitracker.app.data.remote.BitunixTrade
import com.defitracker.app.domain.model.LiveBucket
import java.util.concurrent.ConcurrentHashMap

// acumulador de trades por simbolo y TF: arma la vela en curso real de Bitunix.
// cada trade entra a todos los TF con su propia fase de bucket (la misma que usa
// el exchange para sus velas, asi el live cae en el bucket que le toca)
internal class BitunixLiveBuckets {

    private val buckets = ConcurrentHashMap<String, ConcurrentHashMap<String, LiveBucket>>()

    fun onTrades(symbol: String, trades: List<BitunixTrade>) {
        if (trades.isEmpty()) return
        val perTf = buckets.getOrPut(symbol) { ConcurrentHashMap() }
        for (trade in trades) {
            if (trade.price <= 0.0 || trade.volume < 0.0) continue
            for (def in Timeframes.ALL) {
                val start = Timeframes.bucketStartMs(def.key, trade.timeMs)
                val cur = perTf[def.key]
                perTf[def.key] = when {
                    cur == null -> newBucket(start, trade)
                    // el bucket nuevo arranca con este trade
                    start > cur.startMs -> newBucket(start, trade)
                    // trade atrasado de un bucket viejo: no se pisa el actual
                    start < cur.startMs -> cur
                    else -> cur.copy(
                        high = maxOf(cur.high, trade.price),
                        low = minOf(cur.low, trade.price),
                        close = if (trade.timeMs >= cur.lastTradeMs) trade.price else cur.close,
                        volume = cur.volume + trade.volume,
                        lastTradeMs = maxOf(cur.lastTradeMs, trade.timeMs)
                    )
                }
            }
        }
    }

    fun bucket(symbol: String, tf: String): LiveBucket? = buckets[symbol]?.get(tf)

    private fun newBucket(start: Long, trade: BitunixTrade) = LiveBucket(
        startMs = start,
        open = trade.price,
        high = trade.price,
        low = trade.price,
        close = trade.price,
        volume = trade.volume,
        firstTradeMs = trade.timeMs,
        lastTradeMs = trade.timeMs
    )
}
