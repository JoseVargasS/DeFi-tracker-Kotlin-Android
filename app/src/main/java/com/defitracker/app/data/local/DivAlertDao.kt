package com.defitracker.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DivAlertDao {
    @Query("SELECT * FROM div_alerts ORDER BY createdAt DESC LIMIT 200")
    fun observeAlerts(): Flow<List<DivAlertEntity>>

    @Query("SELECT COUNT(*) FROM div_alerts WHERE seen = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("SELECT * FROM div_alerts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DivAlertEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(alert: DivAlertEntity): Long

    @Query("UPDATE div_alerts SET seen = 1 WHERE seen = 0")
    suspend fun markAllSeen()

    @Query("UPDATE div_alerts SET seen = 1 WHERE id = :id")
    suspend fun markSeen(id: String)

    @Query("SELECT * FROM div_alerts WHERE symbol = :symbol AND `interval` = :interval AND kind = :kind AND status = :status AND candleTime BETWEEN :minTime AND :maxTime ORDER BY candleTime DESC LIMIT 1")
    suspend fun findNearby(symbol: String, interval: String, kind: String, minTime: Long, maxTime: Long, status: String): DivAlertEntity?

    @Query("UPDATE div_alerts SET status = 'CONFIRMED', message = :message, candleTime = :candleTime, createdAt = :now WHERE id = :id")
    suspend fun upgradeToConfirmed(id: String, message: String, candleTime: Long, now: Long)

    @Query("DELETE FROM div_alerts WHERE createdAt < :beforeMs")
    suspend fun pruneOlderThan(beforeMs: Long)

    @Query("SELECT COUNT(*) FROM div_alerts WHERE symbol = :symbol AND kind = :kind AND createdAt > :sinceMs")
    suspend fun countSince(symbol: String, kind: String, sinceMs: Long): Int

    @Query("DELETE FROM div_alerts")
    suspend fun deleteAll()
}
