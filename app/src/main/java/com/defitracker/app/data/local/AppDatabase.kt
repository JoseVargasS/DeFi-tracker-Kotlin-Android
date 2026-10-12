package com.defitracker.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TrackedPairEntity::class, WalletEntity::class, DivAlertEntity::class, PriceAlertEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract val trackedPairDao: TrackedPairDao
    abstract val walletDao: WalletDao
    abstract val divAlertDao: DivAlertDao
    abstract val priceAlertDao: PriceAlertDao
}

// 5->6: solo agrega la tabla de alertas de precio, la data existente no se toca
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `price_alerts` (" +
                "`id` TEXT NOT NULL, `symbol` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                "`direction` TEXT NOT NULL, `targetPrice` REAL NOT NULL, `low` REAL NOT NULL, " +
                "`high` REAL NOT NULL, `enabled` INTEGER NOT NULL, " +
                "`lastNotifiedAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
    }
}
