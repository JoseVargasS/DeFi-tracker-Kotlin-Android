package com.defitracker.app.data.remote

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
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

// tick crudo de Bitunix futuros (ch "ticker"): ultimo precio, open 24h y volumen quote
data class BitunixTick(val lastPrice: Double, val open24h: Double, val quoteVolume: Double)

class BitunixTickerStream(
    private val okHttpClient: OkHttpClient
) {
    // Bitunix no tiene stream de todo el mercado: se suscribe a los pares trackeados.
    // collectLatest cancela el socket anterior cuando la lista cambia (agregar/quitar par).
    fun observe(symbols: Flow<List<String>>): Flow<Map<String, BitunixTick>> = callbackFlow {
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
                        Log.w(TAG, "Bitunix stream dropped, retrying", e)
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
        onBatch: (Map<String, BitunixTick>) -> Unit
    ) {
        val done = CompletableDeferred<Unit>()
        val request = Request.Builder().url(WS_URL).build()
        coroutineScope {
            val socket: WebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // un solo mensaje con N simbolos: probado con varios
                    webSocket.send(subscribeMessage(symbols))
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
        private const val TAG = "BitunixStream"
        private const val WS_URL = "wss://fapi.bitunix.com/public/"

        internal fun subscribeMessage(symbols: List<String>): String {
            val args = com.google.gson.JsonArray()
            symbols.forEach { symbol ->
                args.add(
                    JsonObject()
                        .apply { addProperty("symbol", symbol) }
                        .apply { addProperty("ch", "ticker") }
                )
            }
            return JsonObject()
                .apply { addProperty("op", "subscribe") }
                .apply { add("args", args) }
                .toString()
        }

        // el primer frame es {"op":"connect"}, sin ch ni data: se ignora solo
        internal fun parseTick(text: String): Pair<String, BitunixTick>? {
            val root = parseSocketObject(text) ?: return null
            if (root.optString("ch") != "ticker") return null
            val d = root.optObj("data") ?: return null
            val symbol = d.optString("s") ?: return null
            val last = d.optString("la")?.toDoubleOrNull() ?: return null
            val open = d.optString("o")?.toDoubleOrNull() ?: 0.0
            return symbol to BitunixTick(last, open, d.optString("q")?.toDoubleOrNull() ?: 0.0)
        }
    }
}
