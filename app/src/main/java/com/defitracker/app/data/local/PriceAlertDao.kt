package com.defitracker.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceAlertDao {
    @Query("SELECT * FROM price_alerts WHERE symbol = :symbol AND source = :source ORDER BY createdAt DESC")
    fun observeForPair(symbol: String, source: String): Flow<List<PriceAlertEntity>>

    @Query("SELECT * FROM price_alerts WHERE enabled = 1")
    suspend fun getEnabled(): List<PriceAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alert: PriceAlertEntity)

    @Query("DELETE FROM price_alerts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE price_alerts SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE price_alerts SET lastNotifiedAt = :now WHERE id = :id")
    suspend fun markNotified(id: String, now: Long)
}
