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
import com.defitracker.app.presentation.crypto_detail.toDoubleValue
import org.json.JSONObject

// tick crudo de Binance spot (!miniTicker@arr): precio, open 24h y volumen quote
data class BinanceTick(val close: Double, val open24h: Double, val quoteVolume: Double)

class BinanceTickerStream(
    private val okHttpClient: OkHttpClient
) {
    // mapa simbolo -> tick, una emision por push del server (~1s)
    fun observe(): Flow<Map<String, BinanceTick>> = callbackFlow {
        val loop = launch {
            var attempt = 0
            while (true) {
                try {
                    connectOnce { trySend(it) }
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
        awaitClose { loop.cancel() }
    }

    private suspend fun connectOnce(onBatch: (Map<String, BinanceTick>) -> Unit) {
        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        val request = Request.Builder().url(WS_URL).build()
        val socket: WebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val data = JSONObject(text).optJSONArray("data") ?: return
                    val out = HashMap<String, BinanceTick>(data.length())
                    for (i in 0 until data.length()) {
                        val t = data.optJSONObject(i) ?: continue
                        val symbol = t.optString("s")
                        if (symbol.isEmpty()) continue
                        val close = t.optString("c").toDoubleOrNull() ?: continue
                        val open = t.optString("o").toDoubleOrNull() ?: continue
                        out[symbol] = BinanceTick(close, open, t.optString("q").toDoubleValue())
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

    private companion object {
        const val TAG = "BinanceStream"
        const val WS_URL = "wss://stream.binance.com:9443/stream?streams=!miniTicker@arr"
    }
}
