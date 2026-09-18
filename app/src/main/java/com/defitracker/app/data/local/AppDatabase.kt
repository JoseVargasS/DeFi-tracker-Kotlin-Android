package com.defitracker.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TrackedPairEntity::class, WalletEntity::class, DivAlertEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract val trackedPairDao: TrackedPairDao
    abstract val walletDao: WalletDao
    abstract val divAlertDao: DivAlertDao
}
