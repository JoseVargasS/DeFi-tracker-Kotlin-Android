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
    val quoteVolume: Double,
    // precio crudo del push: el string de arriba viene formateado y el chart no puede
    // redondear (un sub-$1 con 6 decimales pierde hasta un 3% del valor real)
    val priceValue: Double = 0.0,
    // momento del push: el guardian de frescura lo compara contra el reloj para saber
    // si el socket sigue vivo o si el precio quedaria congelado
    val receivedAtMs: Long = 0L
)

// buy/sell del tomador (Binance Futuros), en moneda base
data class TakerVolume(
    val timeMs: Long,
    val buy: Double,
    val sell: Double
)
