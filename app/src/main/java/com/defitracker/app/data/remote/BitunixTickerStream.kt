package com.defitracker.app.data.remote

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
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
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// tick crudo de Bitunix futuros (ch "ticker"): ultimo precio, open 24h y volumen quote
data class BitunixTick(val lastPrice: Double, val open24h: Double, val quoteVolume: Double)

// trade individual del canal "trade": momento, precio y volumen base
data class BitunixTrade(
    val timeMs: Long,
    val price: Double,
    val volume: Double
)

// el socket de Bitunix trae dos canales en la misma conexion: el precio (ticker) y
// los trades. El trade es lo unico que arma la vela en curso real: el exchange solo
// publica velas cerradas
sealed interface BitunixEvent {
    data class Price(val symbol: String, val tick: BitunixTick) : BitunixEvent
    data class Trades(val symbol: String, val trades: List<BitunixTrade>) : BitunixEvent
}

class BitunixTickerStream(
    private val okHttpClient: OkHttpClient
) {
    // Bitunix no tiene stream de todo el mercado: se suscribe a los pares trackeados.
    // collectLatest cancela el socket anterior cuando la lista cambia (agregar/quitar par).
    fun observe(symbols: Flow<List<String>>): Flow<BitunixEvent> = callbackFlow {
        val subscription = launch {
            symbols.distinctUntilChanged().collectLatest { wanted ->
                if (wanted.isEmpty()) return@collectLatest
                val attempt = AtomicInteger(0)
                val openedAt = AtomicLong(0L)
                while (true) {
                    openedAt.set(0L)
                    try {
                        connectOnce(wanted, onOpen = { openedAt.set(System.currentTimeMillis()) }) { trySend(it) }
                        attempt.set(0)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Bitunix stream dropped, retrying", e)
                    }
                    // si la conexion duro lo normal (el exchange corta a los ~61s), eso no
                    // es un fallo de red: el backoff se reinicia (si no el socket pasa 30s
                    // caido y se pierden los trades del bucket, con la vela incompleta)
                    val opened = openedAt.get()
                    if (opened > 0L && System.currentTimeMillis() - opened > 10_000L) attempt.set(0)
                    attempt.incrementAndGet()
                    delay(minOf(2_000L shl attempt.get().coerceAtMost(4), 30_000L))
                }
            }
        }
        awaitClose { subscription.cancel() }
    }

    private suspend fun connectOnce(
        symbols: List<String>,
        onOpen: () -> Unit,
        onEvent: (BitunixEvent) -> Unit
    ) {
        val done = CompletableDeferred<Unit>()
        val request = Request.Builder().url(WS_URL).build()
        coroutineScope {
            val socket: WebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    onOpen()
                    // un solo mensaje con N simbolos: probado con varios
                    webSocket.send(subscribeMessage(symbols))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    parseEvent(text)?.let(onEvent)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!done.isCompleted) done.completeExceptionally(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!done.isCompleted) done.completeExceptionally(Exception("closed $code $reason"))
                }
            })
            // el exchange corta la conexion a los ~61s si no se le habla: con el PING la
            // socket aguanta y no se pierden los trades del bucket (la vela en curso)
            val pingJob = launch {
                while (true) {
                    delay(PING_INTERVAL_MS)
                    if (!socket.send("PING")) break
                }
            }
            try {
                done.await()
            } finally {
                pingJob.cancel()
                try {
                    socket.close(1000, null)
                } catch (_: Exception) {}
            }
        }
    }

    internal companion object {
        private const val TAG = "BitunixStream"
        private const val WS_URL = "wss://fapi.bitunix.com/public/"
        private const val PING_INTERVAL_MS = 20_000L

        // dos args por simbolo: el precio y los trades van en el mismo socket
        // (el array de canales en un solo arg no lo acepta el exchange)
        internal fun subscribeMessage(symbols: List<String>): String {
            val args = JsonArray()
            symbols.forEach { symbol ->
                args.add(
                    JsonObject()
                        .apply { addProperty("symbol", symbol) }
                        .apply { addProperty("ch", "ticker") }
                )
                args.add(
                    JsonObject()
                        .apply { addProperty("symbol", symbol) }
                        .apply { addProperty("ch", "trade") }
                )
            }
            return JsonObject()
                .apply { addProperty("op", "subscribe") }
                .apply { add("args", args) }
                .toString()
        }

        // el primer frame es {"op":"connect"}, sin ch ni data: se ignora solo
        internal fun parseEvent(text: String): BitunixEvent? {
            val root = parseSocketObject(text) ?: return null
            return when (root.optString("ch")) {
                "ticker" -> parseTick(root)?.let { BitunixEvent.Price(it.first, it.second) }
                "trade" -> parseTrades(root)?.let { BitunixEvent.Trades(it.first, it.second) }
                else -> null
            }
        }

        internal fun parseTick(root: JsonObject): Pair<String, BitunixTick>? {
            val d = root.optObj("data") ?: return null
            val symbol = d.optString("s") ?: return null
            val last = d.optString("la")?.toDoubleOrNull() ?: return null
            val open = d.optString("o")?.toDoubleOrNull() ?: 0.0
            return symbol to BitunixTick(last, open, d.optString("q")?.toDoubleOrNull() ?: 0.0)
        }

        // frame de trades: {"ch":"trade","symbol":"BTCUSDT","ts":...,
        // "data":[{"t":"2026-11-12T21:50:28Z","p":"71569.8","v":"0.0186"}]}
        internal fun parseTrades(root: JsonObject): Pair<String, List<BitunixTrade>>? {
            val symbol = root.optString("symbol") ?: return null
            val data = root.get("data")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
            val pushTs = root.optString("ts")?.toLongOrNull() ?: 0L
            val trades = ArrayList<BitunixTrade>(data.size())
            for (el in data) {
                val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                val price = o.optString("p")?.toDoubleOrNull() ?: continue
                val volume = o.optString("v")?.toDoubleOrNull() ?: continue
                val timeMs = o.optString("t")?.let { parseTradeTime(it) } ?: pushTs
                if (timeMs <= 0L) continue
                trades.add(BitunixTrade(timeMs, price, volume))
            }
            return symbol to trades
        }

        // el exchange manda el instante del trade en ISO UTC ("2026-11-12T21:50:28Z")
        private fun parseTradeTime(raw: String): Long? = try {
            Instant.parse(raw).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}
