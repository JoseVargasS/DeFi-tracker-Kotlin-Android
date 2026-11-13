package com.defitracker.app.domain.model

// vela en curso de Bitunix armada con los trades del socket: el exchange solo
// publica velas cerradas, asi que el chart necesita el O/H/L/C/V real del bucket
data class LiveBucket(
    val startMs: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    // primer trade del bucket: si llego tarde, el open real no se conoce
    val firstTradeMs: Long,
    val lastTradeMs: Long
)
