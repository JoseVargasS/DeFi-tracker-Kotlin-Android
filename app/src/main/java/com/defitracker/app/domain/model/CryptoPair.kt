package com.defitracker.app.domain.model

data class CryptoPair(
    val symbol: String,
    val baseAsset: String,
    val quoteAsset: String,
    val price: String,
    val priceChangePercent: String,
    val isPositive: Boolean,
    val source: String
)

data class AvailableCryptoPair(
    val symbol: String,
    val baseAsset: String,
    val quoteAsset: String,
    val source: String? = null,
    val keywords: String = ""
) {
    val displayName: String = if (quoteAsset.isBlank()) baseAsset else "$baseAsset/$quoteAsset"
}

data class PairDetail(
    val symbol: String,
    val price: String,
    val priceChange: String,
    val priceChangePercent: String,
    val highPrice: String,
    val lowPrice: String,
    val volume: String,
    val quoteVolume: String,
    val isPositive: Boolean
)

// cotizacion en vivo por socket, key "$symbol-$source" igual que pairKey del VM
data class LiveQuote(
    val price: String,
    val priceChangePercent: String,
    val isPositive: Boolean,
    val quoteVolume: Double
)

// buy/sell del tomador (Binance Futuros), en moneda base
data class TakerVolume(
    val timeMs: Long,
    val buy: Double,
    val sell: Double
)
