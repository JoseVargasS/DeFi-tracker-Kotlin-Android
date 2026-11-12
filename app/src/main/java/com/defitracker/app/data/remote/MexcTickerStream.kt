package com.defitracker.app.data.remote

import android.util.Log
import kotlinx.coroutines.CancellationException
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

// tick crudo de MEXC futuros (sub.ticker): ultimo precio, tasa 24h, volumen en contratos
// y el monto en moneda quote (amount24), que es el volumen real
data class MexcTick(
    val lastPrice: Double,
    val riseFallRate: Double,
    val volume24: Double,
    val amount24: Double
)

class MexcTickerStream(
    private val okHttpClient: OkHttpClient
) {
    // MEXC tiene el push de todo el mercado (sub.tickers, ~70 KB/s) pero tambien acepta
    // una suscripcion por simbolo (sub.ticker, ~0.4 KB/s). Se pide lo trackeado: si no hay
    // pares de esta fuente, no hay socket.
    // collectLatest cancela el socket anterior cuando la lista cambia (agregar/quitar par).
    fun observe(symbols: Flow<List<String>>): Flow<Map<String, MexcTick>> = callbackFlow {
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
                        Log.w(TAG, "MEXC stream dropped, retrying", e)
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
        onBatch: (Map<String, MexcTick>) -> Unit
    ) {
        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        val request = Request.Builder().url(WS_URL).build()
        // ping de la app: el server tumba conexiones sin trafico a los ~60s.
        // coroutineScope: al salir se cancela el ping solo
        kotlinx.coroutines.coroutineScope {
            val socket: WebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // un mensaje por simbolo: MEXC acepta varias suscripciones en el mismo
                    // socket (probado con 3 a la vez)
                    symbols.forEach { webSocket.send(subscribeMessage(it)) }
                    launch {
                        while (true) {
                            delay(PING_MS)
                            try {
                                webSocket.send(PING_MSG)
                            } catch (_: Exception) {
                                break
                            }
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    parseTick(text)?.let { (symbol, tick) -> onBatch(mapOf(symbol to tick)) }
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
    }

    internal companion object {
        private const val TAG = "MexcStream"
        private const val WS_URL = "wss://contract.mexc.com/edge"
        private const val PING_MSG = "{\"method\":\"ping\"}"
        private const val PING_MS = 15_000L

        internal fun subscribeMessage(symbol: String): String = JsonObject()
            .apply { addProperty("method", "sub.ticker") }
            .apply {
                add(
                    "param",
                    JsonObject().apply { addProperty("symbol", symbol) }
                )
            }
            .toString()

        // el ack {"channel":"rs.sub.ticker","data":"success"} no trae symbol: se descarta
        internal fun parseTick(text: String): Pair<String, MexcTick>? {
            val root = parseSocketObject(text) ?: return null
            val symbol = root.optString("symbol") ?: return null
            val data = root.optObj("data") ?: return null
            val last = data.optString("lastPrice")?.toDoubleOrNull() ?: return null
            val rise = data.optString("riseFallRate")?.toDoubleOrNull() ?: 0.0
            val volume = data.optString("volume24")?.toDoubleOrNull() ?: 0.0
            val amount = data.optString("amount24")?.toDoubleOrNull() ?: 0.0
            return symbol to MexcTick(last, rise, volume, amount)
        }
    }
}
