package com.defitracker.app.di

import android.app.Application
import androidx.room.Room
import com.defitracker.app.core.Constants
import com.defitracker.app.data.local.AppDatabase
import com.defitracker.app.data.local.TrackedPairDao
import com.defitracker.app.data.local.WalletDao
import com.defitracker.app.data.remote.BinanceApi
import com.defitracker.app.data.remote.BinanceFuturesApi
import com.defitracker.app.data.remote.CoinStatsApi
import com.defitracker.app.data.remote.MexcFuturesApi
import com.defitracker.app.data.repository.CryptoRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBinanceApi(@Named("rest") okHttpClient: OkHttpClient): BinanceApi {
        return Retrofit.Builder()
            .baseUrl(Constants.BINANCE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BinanceApi::class.java)
    }

    @Provides
    @Singleton
    fun provideBinanceFuturesApi(@Named("rest") okHttpClient: OkHttpClient): BinanceFuturesApi {
        return Retrofit.Builder()
            .baseUrl(Constants.BINANCE_FUTURES_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BinanceFuturesApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCoinStatsApi(@Named("rest") okHttpClient: OkHttpClient): CoinStatsApi {
        return Retrofit.Builder()
            .baseUrl(Constants.COINSTATS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinStatsApi::class.java)
    }

    @Provides
    @Singleton
    fun provideMexcFuturesApi(@Named("rest") okHttpClient: OkHttpClient): MexcFuturesApi {
        return Retrofit.Builder()
            .baseUrl(Constants.MEXC_FUTURES_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MexcFuturesApi::class.java)
    }

    @Provides
    @Singleton
    @Named("rest")
    fun provideRestOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    // un solo pool de conexiones para los 3 websockets (streams + TV con su propio timeout)
    @Provides
    @Singleton
    fun provideOkHttpClient(): okhttp3.OkHttpClient {
        return okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .pingInterval(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideTradingViewClient(
        okHttpClient: okhttp3.OkHttpClient
    ): com.defitracker.app.data.remote.tv.TradingViewClient {
        return com.defitracker.app.data.remote.tv.TradingViewClient(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideBinanceTickerStream(
        okHttpClient: okhttp3.OkHttpClient
    ): com.defitracker.app.data.remote.BinanceTickerStream {
        return com.defitracker.app.data.remote.BinanceTickerStream(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideMexcTickerStream(
        okHttpClient: okhttp3.OkHttpClient
    ): com.defitracker.app.data.remote.MexcTickerStream {
        return com.defitracker.app.data.remote.MexcTickerStream(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(app: Application): AppDatabase {
        return Room.databaseBuilder(
            app,
            AppDatabase::class.java,
            Constants.DATABASE_NAME
        ).addMigrations(com.defitracker.app.data.local.MIGRATION_5_6)
            .fallbackToDestructiveMigration()
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
    fun provideDivAlertDao(db: AppDatabase) = db.divAlertDao

    @Provides
    @Singleton
    fun providePriceAlertDao(db: AppDatabase) = db.priceAlertDao

    @Provides
    @Singleton
    fun provideCryptoRepository(
        binanceApi: BinanceApi,
        binanceFuturesApi: BinanceFuturesApi,
        coinStatsApi: CoinStatsApi,
        mexcFuturesApi: MexcFuturesApi,
        tradingViewClient: com.defitracker.app.data.remote.tv.TradingViewClient,
        binanceTickerStream: com.defitracker.app.data.remote.BinanceTickerStream,
        mexcTickerStream: com.defitracker.app.data.remote.MexcTickerStream,
        trackedPairDao: TrackedPairDao,
        walletDao: WalletDao
    ): CryptoRepositoryImpl {
        return CryptoRepositoryImpl(
            binanceApi, binanceFuturesApi, coinStatsApi, mexcFuturesApi,
            tradingViewClient, binanceTickerStream, mexcTickerStream, trackedPairDao, walletDao
        )
    }
}
