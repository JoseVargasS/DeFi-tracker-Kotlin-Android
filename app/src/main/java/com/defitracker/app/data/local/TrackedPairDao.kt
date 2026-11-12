package com.defitracker.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedPairDao {
    @Query("SELECT * FROM tracked_pairs")
    fun getAllTrackedPairs(): Flow<List<TrackedPairEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackedPair(pair: TrackedPairEntity)

    @Query("DELETE FROM tracked_pairs WHERE symbol = :symbol AND source = :source")
    suspend fun deleteTrackedPair(symbol: String, source: String)

}
