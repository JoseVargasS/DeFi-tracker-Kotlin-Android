package com.defitracker.app.data.local

import androidx.room.Entity

// la PK es (symbol, source): el mismo BTCUSDT existe en Binance y en Bitunix
@Entity(tableName = "tracked_pairs", primaryKeys = ["symbol", "source"])
data class TrackedPairEntity(
    val symbol: String, // e.g., "BTCUSDT" (Binance/Bitunix) o "BTC-USDT" (BingX)
    val baseAsset: String,
    val quoteAsset: String = "USDT",
    val source: String = "Binance"
)
