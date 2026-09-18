package com.defitracker.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// centro de notis de divergencias RSI, dedupe por simbolo+TF+tipo+v candle
@Entity(tableName = "div_alerts")
data class DivAlertEntity(
    @PrimaryKey val id: String, // "$symbol|$interval|$kind|$candleTime"
    val symbol: String,
    val source: String = "MEXC",
    val interval: String,
    val kind: String, // REG_BULL, REG_BEAR, HID_BULL, HID_BEAR
    val bullish: Boolean,
    val message: String,
    val createdAt: Long = System.currentTimeMillis(),
    val candleTime: Long = 0L,
    val seen: Boolean = false,
    val status: String = "CONFIRMED"
)
