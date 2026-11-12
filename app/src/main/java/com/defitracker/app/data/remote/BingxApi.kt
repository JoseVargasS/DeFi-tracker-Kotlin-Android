package com.defitracker.app.data.remote

import com.defitracker.app.data.remote.dto.BingxContractDto
import com.defitracker.app.data.remote.dto.BingxEnvelope
import com.defitracker.app.data.remote.dto.BingxKlineDto
import com.defitracker.app.data.remote.dto.BingxTickerDto
import retrofit2.http.GET
import retrofit2.http.Query

// futuros BingX (open-api.bingx.com). simbolo con guion: BTC-USDT
interface BingxApi {
    @GET("openApi/swap/v2/quote/contracts")
    suspend fun getContracts(): BingxEnvelope<List<BingxContractDto>>

    @GET("openApi/swap/v2/quote/ticker")
    suspend fun getTicker(@Query("symbol") symbol: String): BingxEnvelope<BingxTickerDto>

    // v3: hasta 1000 velas en una sola llamada, tambien de la mas nueva a la mas vieja
    @GET("openApi/swap/v3/quote/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): BingxEnvelope<List<BingxKlineDto>>
}
