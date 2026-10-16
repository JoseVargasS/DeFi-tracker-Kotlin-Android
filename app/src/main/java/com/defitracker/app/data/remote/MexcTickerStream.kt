package com.defitracker.app.data.remote

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

// tick crudo de MEXC futuros (push.tickers): ultimo precio, tasa 24h y volumen en contratos
data class MexcTick(val lastPrice: Double, val riseFallRate: Double, val volume24: Double)

class MexcTickerStream(
    private val okHttpClient: OkHttpClient
) {
    // mapa simbolo (BTC_USDT) -> tick, una emision por push del server (~2s)
    fun observe(): Flow<Map<String, MexcTick>> = callbackFlow {
        val loop = launch {
            var attempt = 0
            while (true) {
                try {
                    connectOnce { trySend(it) }
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
        awaitClose { loop.cancel() }
    }

    private suspend fun connectOnce(onBatch: (Map<String, MexcTick>) -> Unit) {
        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        val request = Request.Builder().url(WS_URL).build()
        // ping de la app: el server tumba conexiones sin trafico a los ~60s.
        // coroutineScope: al salir se cancela el ping solo
        kotlinx.coroutines.coroutineScope {
            val socket: WebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(SUB_MSG)
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
                    try {
                        val root = JSONObject(text)
                        if (root.optString("channel") != "push.tickers") return
                        val data = root.optJSONArray("data") ?: return
                        val out = HashMap<String, MexcTick>(data.length())
                        for (i in 0 until data.length()) {
                            val t = data.optJSONObject(i) ?: continue
                            val symbol = t.optString("symbol")
                            if (symbol.isEmpty()) continue
                            val last = t.optDouble("lastPrice", Double.NaN)
                            if (!last.isFinite()) continue
                            out[symbol] = MexcTick(last, t.optDouble("riseFallRate", 0.0), t.optDouble("volume24", 0.0))
                        }
                        if (out.isNotEmpty()) onBatch(out)
                    } catch (_: Exception) {}
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

    private companion object {
        const val TAG = "MexcStream"
        const val WS_URL = "wss://contract.mexc.com/edge"
        const val SUB_MSG = "{\"method\":\"sub.tickers\",\"param\":{}}"
        const val PING_MSG = "{\"method\":\"ping\"}"
        const val PING_MS = 15_000L
    }
}
