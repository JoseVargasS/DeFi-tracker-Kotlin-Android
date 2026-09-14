package com.defitracker.app.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.defitracker.app.core.Constants
import com.defitracker.app.data.local.AppDatabase
import com.defitracker.app.data.local.TrackedPairDao
import com.defitracker.app.data.local.WalletDao
import com.defitracker.app.data.remote.BinanceApi
import com.defitracker.app.data.remote.CoinStatsApi
import com.defitracker.app.data.remote.MexcFuturesApi
import com.defitracker.app.data.repository.CryptoRepositoryImpl
import com.defitracker.app.domain.repository.CryptoRepository
import com.defitracker.app.presentation.crypto_detail.IndicatorPrefsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBinanceApi(): BinanceApi {
        return Retrofit.Builder()
            .baseUrl(Constants.BINANCE_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BinanceApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCoinStatsApi(): CoinStatsApi {
        return Retrofit.Builder()
            .baseUrl(Constants.COINSTATS_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinStatsApi::class.java)
    }

    @Provides
    @Singleton
    fun provideMexcFuturesApi(): MexcFuturesApi {
        return Retrofit.Builder()
            .baseUrl(Constants.MEXC_FUTURES_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MexcFuturesApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(app: Application): AppDatabase {
        return Room.databaseBuilder(
            app,
            AppDatabase::class.java,
            Constants.DATABASE_NAME
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideTrackedPairDao(db: AppDatabase) = db.trackedPairDao

    @Provides
    @Singleton
    fun provideWalletDao(db: AppDatabase) = db.walletDao

    @Provides
    @Singleton
    fun provideIndicatorPrefsRepository(
        @ApplicationContext context: Context
    ): IndicatorPrefsRepository {
        return IndicatorPrefsRepository(context)
    }

    @Provides
    @Singleton
    fun providePairOrderRepository(
        @ApplicationContext context: Context
    ): com.defitracker.app.presentation.crypto_list.PairOrderRepository {
        return com.defitracker.app.presentation.crypto_list.PairOrderRepository(context)
    }

    @Provides
    @Singleton
    fun provideCryptoRepository(
        binanceApi: BinanceApi,
        coinStatsApi: CoinStatsApi,
        mexcFuturesApi: MexcFuturesApi,
        trackedPairDao: TrackedPairDao,
        walletDao: WalletDao
    ): CryptoRepository {
        return CryptoRepositoryImpl(
            binanceApi, coinStatsApi, mexcFuturesApi,
            trackedPairDao, walletDao
        )
    }
}
