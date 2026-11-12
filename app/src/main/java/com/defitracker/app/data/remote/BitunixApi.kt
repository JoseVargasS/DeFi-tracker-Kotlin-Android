package com.defitracker.app.data.remote

import com.defitracker.app.data.remote.dto.BitunixEnvelope
import com.defitracker.app.data.remote.dto.BitunixKlineDto
import com.defitracker.app.data.remote.dto.BitunixPairDto
import com.defitracker.app.data.remote.dto.BitunixTickerDto
import retrofit2.http.GET
import retrofit2.http.Query

// futuros Bitunix (fapi.bitunix.com, v1). simbolo sin separador: BTCUSDT
interface BitunixApi {
    @GET("market/trading_pairs")
    suspend fun getTradingPairs(): BitunixEnvelope<List<BitunixPairDto>>

    @GET("market/tickers")
    suspend fun getTickers(@Query("symbols") symbols: String): BitunixEnvelope<List<BitunixTickerDto>>

    // ojo: topea en 200 velas por request y devuelve de la mas nueva a la mas vieja
    @GET("market/kline")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int,
        @Query("startTime") startTime: Long? = null,
        @Query("endTime") endTime: Long? = null
    ): BitunixEnvelope<List<BitunixKlineDto>>
}
