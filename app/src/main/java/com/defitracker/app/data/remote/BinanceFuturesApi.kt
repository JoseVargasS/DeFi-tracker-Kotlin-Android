package com.defitracker.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

// Futuros USDⓈ-M (host distinto al spot): klines con volumen del tomador, sin auth.
// Se usan klines y no futures/data/takerlongshortRatio porque ese endpoint solo
// publica buckets cerrados: en 15m/1h/4h/1d la vela en curso nunca aparecia.
interface BinanceFuturesApi {
    @GET("fapi/v1/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 500
    ): List<List<Any>>
}
