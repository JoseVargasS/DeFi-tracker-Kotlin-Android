package com.defitracker.app.data.remote.tv

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class TradingViewClient(
    private val okHttpClient: OkHttpClient
) {
    // memo chico para que el tick del detalle (5s) no abra un socket por llamado
    private val memo = ConcurrentHashMap<String, Memoed>()

    suspend fun getKlines(symbol: String, resolution: String, countback: Int): List<List<Any>> {
        val key = "$symbol:$resolution:$countback"
        val cached = memo[key]
        if (cached != null && System.currentTimeMillis() - cached.at <= MEMO_TTL_MS) return cached.rows

        val rows = try {
            fetchRaw(symbol, resolution, countback)
        } catch (e: TimeoutCancellationException) {
            // TV no respondio a tiempo: se usa el memo y no se tumba la carga del chart
            Log.w(TAG, "TV timeout for $symbol/$resolution")
            memo[key]?.rows ?: emptyList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "TV fetch failed for $symbol/$resolution", e)
            memo[key]?.rows ?: emptyList()
        }
        if (rows.isNotEmpty()) memo[key] = Memoed(System.currentTimeMillis(), rows)
        return rows
    }

    private suspend fun fetchRaw(symbol: String, resolution: String, countback: Int): List<List<Any>> {
        val chartSession = "cs_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val done = CompletableDeferred<List<List<Any>>>()

        val request = Request.Builder()
            .url(WS_URL)
            .header("Origin", "https://www.tradingview.com")
            .build()

        val socket: WebSocket
        val bars = ArrayList<List<Any>>()

        socket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(frame(JSONObject().put("m", "set_auth_token").put("p", JSONArray().put("unauthorized_user_token"))))
                webSocket.send(frame(JSONObject().put("m", "chart_create_session").put("p", JSONArray().put(chartSession).put(""))))
                val resolveJson = JSONObject()
                    .put("symbol", symbol)
                    .put("adjustment", "splits")
                    .put("session", "extended")
                webSocket.send(frame(JSONObject().put("m", "resolve_symbol").put("p", JSONArray().put(chartSession).put("ser_1").put("=$resolveJson"))))
                webSocket.send(frame(JSONObject().put("m", "create_series").put("p", JSONArray().put(chartSession).put("s1").put("s1").put("ser_1").put(resolution).put(countback).put(""))))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frames = parseFrames(text)
                for (f in frames) {
                    try {
                        val msg = JSONObject(f)
                        when (msg.optString("m")) {
                            "protocol_heartbeat" -> {
                                val p = msg.optJSONArray("p")
                                webSocket.send(frame(JSONObject().put("m", "protocol_heartbeat").put("p", p ?: JSONArray())))
                            }
                            "timescale_update" -> {
                                val s1 = msg.optJSONArray("p")?.optJSONObject(1)?.optJSONObject("s1")
                                val series = s1?.optJSONArray("s")
                                if (series != null) {
                                    bars.clear()
                                    bars.addAll(extractBars(series))
                                }
                            }
                            "du" -> {
                                val s1 = msg.optJSONArray("p")?.optJSONObject(1)?.optJSONObject("s1")
                                val series = s1?.optJSONArray("s")
                                if (series != null) {
                                    for (upd in extractBars(series)) {
                                        val updTime = (upd[0] as Number).toLong()
                                        val lastTime = if (bars.isEmpty()) 0L else (bars.last()[0] as Number).toLong()
                                        if (bars.isNotEmpty() && lastTime == updTime) {
                                            bars[bars.lastIndex] = upd
                                        } else if (bars.isEmpty() || lastTime < updTime) {
                                            bars.add(upd)
                                        }
                                    }
                                }
                            }
                            "series_completed" -> {
                                if (!done.isCompleted) done.complete(bars.toList())
                            }
                            "symbol_error", "critical_error" -> {
                                if (!done.isCompleted) done.complete(emptyList())
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "TV frame parse failed", e)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!done.isCompleted) done.completeExceptionally(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!done.isCompleted) done.complete(bars.toList())
            }
        })

        try {
            return withTimeout(FETCH_TIMEOUT_MS) { done.await() }
        } finally {
            socket.close(1000, "done")
        }
    }

    private fun extractBars(series: JSONArray): List<List<Any>> {
        val out = ArrayList<List<Any>>(series.length())
        for (i in 0 until series.length()) {
            val v = series.optJSONObject(i)?.optJSONArray("v") ?: continue
            if (v.length() < 6) continue
            val timeMs = (v.optDouble(0) * 1000.0).toLong()
            out.add(
                listOf<Any>(
                    timeMs,
                    v.optDouble(1),
                    v.optDouble(2),
                    v.optDouble(3),
                    v.optDouble(4),
                    v.optDouble(5),
                    0L, 0.0, 0, 0.0
                )
            )
        }
        return out
    }

    // payloads JSON encuadrados como ~m~<len>~m~<json>
    private fun parseFrames(raw: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < raw.length) {
            val start = raw.indexOf("~m~", i)
            if (start < 0) break
            val lenStart = start + 3
            val lenEnd = raw.indexOf("~m~", lenStart)
            if (lenEnd < 0) break
            val len = raw.substring(lenStart, lenEnd).toIntOrNull() ?: return out
            val payloadStart = lenEnd + 3
            if (payloadStart + len > raw.length) return out
            out.add(raw.substring(payloadStart, payloadStart + len))
            i = payloadStart + len
        }
        return out
    }

    private fun frame(json: JSONObject): String {
        val payload = json.toString()
        return "~m~${payload.length}~m~$payload"
    }

    private data class Memoed(val at: Long, val rows: List<List<Any>>)

    companion object {
        const val TAG = "TradingViewClient"
        const val WS_URL = "wss://data.tradingview.com/socket.io/websocket?type=chart"
        const val MEMO_TTL_MS = 15_000L
        const val FETCH_TIMEOUT_MS = 12_000L
    }
}

// TF del chart a resolucion TV, compartido por repo y ViewModel
// nota: el VM ya manda la resolucion resuelta; si llega otra vez aca no se
// vuelve a mapear (antes "5" caia al default "60" y todo TV quedaba en 1h)
private val TV_RESOLUTIONS = setOf("1", "3", "5", "15", "30", "60", "120", "240", "360", "720", "1D", "3D", "1W", "1M")

fun tvResolution(interval: String): String {
    val tf = interval.trim()
    if (tf in TV_RESOLUTIONS) return tf
    return when (tf) {
        "1m" -> "1"
        "3m" -> "3"
        "5m" -> "5"
        "15m" -> "15"
        "30m" -> "30"
        "1h" -> "60"
        "2h" -> "120"
        "4h" -> "240"
        "6h" -> "360"
        "12h" -> "720"
        "1d", "5d" -> "1D"
        "3d" -> "3D"
        "1w", "2w" -> "1W"
        "1mo" -> "1M"
        else -> "60"
    }
}
