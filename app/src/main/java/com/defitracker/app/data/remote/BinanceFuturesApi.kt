package com.defitracker.app.data.remote

import com.defitracker.app.data.remote.dto.BinanceTakerVolumeDto
import retrofit2.http.GET
import retrofit2.http.Query

// Futuros USDⓈ-M (host distinto al spot): buy/sell del tomador, peso 0, sin auth
interface BinanceFuturesApi {
    @GET("futures/data/takerlongshortRatio")
    suspend fun getTakerVolume(
        @Query("symbol") symbol: String,
        @Query("period") period: String,
        @Query("limit") limit: Int = 500
    ): List<BinanceTakerVolumeDto>
}
