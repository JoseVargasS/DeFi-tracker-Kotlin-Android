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
import okio.ByteString
import com.google.gson.JsonObject
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

// tick crudo de BingX futuros (@ticker). Solo precio y volumen quote: su `o`/`P` arrancan
// en la suscripcion (no son 24h), asi que el % 24h sale del openPrice del REST.
data class BingxTick(val lastPrice: Double, val quoteVolume: Double)

class BingxTickerStream(
    private val okHttpClient: OkHttpClient
) {
    fun observe(symbols: Flow<List<String>>): Flow<Map<String, BingxTick>> = callbackFlow {
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
                        Log.w(TAG, "BingX stream dropped, retrying", e)
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
        onBatch: (Map<String, BingxTick>) -> Unit
    ) {
        val done = CompletableDeferred<Unit>()
        val request = Request.Builder().url(WS_URL).build()
        coroutineScope {
            val socket: WebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // una sub por simbolo: la lista separada por comas se acepta y no empuja nada
                    symbols.forEach { webSocket.send(subscribeMessage(it)) }
                    // sin ping cada ~4s el server corta la conexion
                    launch {
                        while (true) {
                            delay(PING_MS)
                            try {
                                webSocket.send(pingMessage())
                            } catch (_: Exception) {
                                break
                            }
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    gunzip(bytes)?.let(::parseTick)?.let(onBatch)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    // el ack de suscripcion llega sin comprimir
                    parseTick(text)?.let(onBatch)
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
        private const val TAG = "BingxStream"
        private const val WS_URL = "wss://open-api-swap.bingx.com/swap-market"
        private const val PING_MS = 4_000L

        internal fun subscribeMessage(symbol: String): String =
            JsonObject()
                .apply { addProperty("id", symbol) }
                .apply { addProperty("reqType", "sub") }
                .apply { addProperty("dataType", "$symbol@ticker") }
                .toString()

        internal fun pingMessage(): String =
            JsonObject()
                .apply { addProperty("ping", System.currentTimeMillis().toString()) }
                .toString()

        // todos los frames de datos llegan binarios y comprimidos (1f 8b 08)
        internal fun gunzip(bytes: ByteString): String? = try {
            GZIPInputStream(ByteArrayInputStream(bytes.toByteArray()))
                .bufferedReader()
                .use { it.readText() }
        } catch (_: Exception) {
            null
        }

        internal fun parseTick(text: String): Map<String, BingxTick>? {
            val d = parseSocketObject(text)?.optObj("data") ?: return null
            val symbol = d.optString("s") ?: return null
            val close = d.optString("c")?.toDoubleOrNull() ?: return null
            return mapOf(symbol to BingxTick(close, d.optString("q")?.toDoubleOrNull() ?: 0.0))
        }
    }
}
