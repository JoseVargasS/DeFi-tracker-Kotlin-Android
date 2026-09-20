package com.defitracker.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// futuros perpetuos MEXC (contract.mexc.com, v1). Formato columnar, distinto a Binance.
data class MexcFuturesContractDto(
    val symbol: String = "",
    val baseCoin: String = "",
    val quoteCoin: String = "",
    val settleCoin: String = "",
    val futureType: Int = 0,
    val state: Int = -1
)

data class MexcFuturesContractsResponse(
    val success: Boolean = false,
    val code: Int = -1,
    val data: List<MexcFuturesContractDto> = emptyList()
)

data class MexcFuturesTickerDto(
    val symbol: String = "",
    val lastPrice: Double = 0.0,
    val riseFallRate: Double = 0.0,
    val riseFallValue: Double = 0.0,
    val high24Price: Double = 0.0,
    val lower24Price: Double = 0.0,
    val volume24: Double = 0.0,
    val amount24: Double = 0.0
)

data class MexcFuturesTickerResponse(
    val success: Boolean = false,
    val code: Int = -1,
    val data: MexcFuturesTickerDto = MexcFuturesTickerDto()
)

data class MexcFuturesTickersResponse(
    val success: Boolean = false,
    val code: Int = -1,
    val data: List<MexcFuturesTickerDto> = emptyList()
)

data class MexcFuturesKlineData(
    val time: List<Long> = emptyList(),
    val open: List<Double> = emptyList(),
    val close: List<Double> = emptyList(),
    val high: List<Double> = emptyList(),
    val low: List<Double> = emptyList(),
    val vol: List<Double> = emptyList()
)

data class MexcFuturesKlineResponse(
    val success: Boolean = false,
    val code: Int = -1,
    val data: MexcFuturesKlineData = MexcFuturesKlineData()
)

interface MexcFuturesApi {
    @GET("contract/detail")
    suspend fun getContracts(): MexcFuturesContractsResponse

    @GET("contract/ticker")
    suspend fun getTicker(@Query("symbol") symbol: String): MexcFuturesTickerResponse

    @GET("contract/ticker")
    suspend fun getAllTickers(): MexcFuturesTickersResponse

    @GET("contract/kline/{symbol}")
    suspend fun getKlines(
        @Path("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("start") start: Long? = null,
        @Query("end") end: Long? = null
    ): MexcFuturesKlineResponse
}
