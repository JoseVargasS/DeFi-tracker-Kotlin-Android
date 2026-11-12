package com.defitracker.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TrackedPairEntity::class, WalletEntity::class, DivAlertEntity::class, PriceAlertEntity::class],
    version = 7,
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

// 6->7: PK compuesta (symbol, source). El mismo simbolo puede vivir en 4 brokers;
// con PK = symbol, agregar Bitunix BTCUSDT reemplazaba el de Binance.
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // el orden de PRIMARY KEY debe coincidir con primaryKeys = ["symbol", "source"]
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tracked_pairs_new` (" +
                "`symbol` TEXT NOT NULL, `baseAsset` TEXT NOT NULL, " +
                "`quoteAsset` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                "PRIMARY KEY(`symbol`, `source`))"
        )
        db.execSQL(
            "INSERT OR REPLACE INTO `tracked_pairs_new` " +
                "(`symbol`, `baseAsset`, `quoteAsset`, `source`) " +
                "SELECT `symbol`, `baseAsset`, `quoteAsset`, `source` FROM `tracked_pairs`"
        )
        db.execSQL("DROP TABLE `tracked_pairs`")
        db.execSQL("ALTER TABLE `tracked_pairs_new` RENAME TO `tracked_pairs`")
    }
}
