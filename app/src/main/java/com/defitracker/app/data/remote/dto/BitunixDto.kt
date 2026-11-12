package com.defitracker.app.data.remote.dto

// la API envuelve todo en {code, msg, data}
data class BitunixEnvelope<T>(
    val code: Int = 0,
    val msg: String? = null,
    val data: T? = null
)

data class BitunixPairDto(
    val symbol: String = "",
    val base: String = "",
    val quote: String = "",
    val symbolStatus: String = ""
)

// time viene como String en milisegundos
data class BitunixKlineDto(
    val open: String? = null,
    val high: String? = null,
    val low: String? = null,
    val close: String? = null,
    val baseVol: String? = null,
    val quoteVol: String? = null,
    val time: String? = null
)

// el ticker no trae porcentaje: se calcula con open (24h)
data class BitunixTickerDto(
    val symbol: String = "",
    val lastPrice: String? = null,
    val open: String? = null,
    val high: String? = null,
    val low: String? = null,
    val baseVol: String? = null,
    val quoteVol: String? = null
)
