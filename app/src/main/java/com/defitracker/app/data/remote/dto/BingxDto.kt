package com.defitracker.app.data.remote.dto

data class BingxEnvelope<T>(
    val code: Int = 0,
    val msg: String? = null,
    val data: T? = null
)

// status 25 son perps de acciones (NCCOGOLD2USD & cia.): los crypto son status 1
data class BingxContractDto(
    val symbol: String = "",
    val asset: String = "",
    val currency: String = "",
    val status: Int = 0,
    val apiStateOpen: String? = null
)

// time viene como numero (ms)
data class BingxKlineDto(
    val open: String? = null,
    val close: String? = null,
    val high: String? = null,
    val low: String? = null,
    val volume: String? = null,
    val time: Long = 0L
)

data class BingxTickerDto(
    val symbol: String = "",
    val priceChange: String? = null,
    val priceChangePercent: String? = null,
    val lastPrice: String? = null,
    val highPrice: String? = null,
    val lowPrice: String? = null,
    val volume: String? = null,
    val quoteVolume: String? = null,
    // el open de 24h: el socket no lo manda bien, el % en vivo se calcula contra este
    val openPrice: String? = null
)
