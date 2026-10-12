package com.defitracker.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// alerta de precio por par: LONG/SHORT, precio puntual o rango [low, high]
@Entity(tableName = "price_alerts")
data class PriceAlertEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val source: String = "MEXC",
    val direction: String = "LONG", // LONG | SHORT
    val targetPrice: Double = 0.0, // si low/high estan en 0 se usa este
    val low: Double = 0.0,
    val high: Double = 0.0,
    val enabled: Boolean = true,
    val lastNotifiedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
