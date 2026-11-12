package com.defitracker.app.data.remote

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import com.google.gson.JsonObject

// tick crudo de Binance spot: precio, open 24h y volumen quote
data class BinanceTick(val close: Double, val open24h: Double, val quoteVolume: Double)

class BinanceTickerStream(
    private val okHttpClient: OkHttpClient
) {
    // Binance tiene el stream de todo el mercado (!miniTicker@arr, ~11 KB/s) pero tambien
    // acepta en la URL solo los simbolos pedidos (~0.5 KB/s con 3 pares). Se pide lo
    // trackeado: si no hay pares de esta fuente, no hay socket.
    // collectLatest cancela el socket anterior cuando la lista cambia (agregar/quitar par).
    fun observe(symbols: Flow<List<String>>): Flow<Map<String, BinanceTick>> = callbackFlow {
        val subscription = launch {
            symbols.distinctUntilChanged().collectLatest { wanted ->
                if (wanted.isEmpty()) return@collectLatest
                var attempt = 0
                while (true) {
                    try {
                        connectOnce(wanted) { trySend(it) }
                        attempt = 0
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Binance stream dropped, retrying", e)
                    }
                    attempt++
                    delay(minOf(2_000L shl attempt.coerceAtMost(4), 30_000L))
                }
            }
        }
        awaitClose { subscription.cancel() }
    }

    private suspend fun connectOnce(
        symbols: List<String>,
        onBatch: (Map<String, BinanceTick>) -> Unit
    ) {
        val done = CompletableDeferred<Unit>()
        val request = Request.Builder().url(streamUrl(symbols)).build()
        val socket: WebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val out = parseTicks(text)
                if (out.isNotEmpty()) onBatch(out)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!done.isCompleted) done.completeExceptionally(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!done.isCompleted) done.completeExceptionally(Exception("closed $code $reason"))
            }
        })
        try {
            done.await()
        } finally {
            try {
                socket.close(1000, null)
            } catch (_: Exception) {}
        }
    }

    internal companion object {
        private const val TAG = "BinanceStream"
        private const val BASE_URL = "wss://stream.binance.com:9443"
        private const val ALL_MARKET_URL = "$BASE_URL/stream?streams=!miniTicker@arr"
        // la URL combinada se pasa de larga con muchos simbolos: ahi se vuelve al mercado
        private const val MAX_COMBINED_STREAMS = 100

        // un stream por simbolo: btcusdt@miniTicker/ethusdt@miniTicker/...
        internal fun streamUrl(symbols: List<String>): String {
            if (symbols.isEmpty()) return ALL_MARKET_URL
            if (symbols.size > MAX_COMBINED_STREAMS) return ALL_MARKET_URL
            val streams = symbols.joinToString("/") { "${it.lowercase()}@miniTicker" }
            return "$BASE_URL/stream?streams=$streams"
        }

        // el frame combinado trae "data" como objeto y el de todo el mercado como array
        internal fun parseTicks(text: String): Map<String, BinanceTick> {
            val root = parseSocketObject(text) ?: return emptyMap()
            val data = root.get("data") ?: return emptyMap()
            val out = HashMap<String, BinanceTick>()
            if (data.isJsonArray) {
                for (element in data.asJsonArray) {
                    val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                    val tick = tickOf(obj) ?: continue
                    out[tick.first] = tick.second
                }
            } else if (data.isJsonObject) {
                val tick = tickOf(data.asJsonObject) ?: return emptyMap()
                out[tick.first] = tick.second
            }
            return out
        }

        private fun tickOf(t: JsonObject): Pair<String, BinanceTick>? {
            val symbol = t.optString("s") ?: return null
            val close = t.optString("c")?.toDoubleOrNull() ?: return null
            val open = t.optString("o")?.toDoubleOrNull() ?: return null
            val quoteVolume = t.optString("q")?.toDoubleOrNull() ?: 0.0
            return symbol to BinanceTick(close, open, quoteVolume)
        }
    }
}
