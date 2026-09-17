package com.defitracker.app.domain.repository

import com.defitracker.app.data.local.WalletEntity
import com.defitracker.app.data.remote.dto.CoinStatsBalanceDto
import com.defitracker.app.data.remote.dto.EtherscanTransactionDto
import com.defitracker.app.domain.model.AvailableCryptoPair
import com.defitracker.app.domain.model.CryptoPair
import com.defitracker.app.domain.model.PairDetail
import kotlinx.coroutines.flow.Flow

interface CryptoRepository {
    fun getTrackedPairs(): Flow<List<CryptoPair>>
    suspend fun addTrackedPair(symbol: String, baseAsset: String, quoteAsset: String, source: String)
    suspend fun removeTrackedPair(symbol: String)
    suspend fun getPairDetail(symbol: String, source: String): PairDetail
    suspend fun getKlines(symbol: String, interval: String, source: String, forceRefresh: Boolean = false): List<List<Any>>
    // ponytail: cola fresca sin cache pa' syncTail, 1 sola llamada
    suspend fun getLatestKlines(
        symbol: String,
        interval: String,
        source: String,
        sinceTimeMs: Long = 0L,
        limit: Int = 10
    ): List<List<Any>>
    suspend fun getAvailableSymbols(source: String = "Binance"): List<AvailableCryptoPair>

    // Wallets
    fun getSavedWallets(): Flow<List<WalletEntity>>
    suspend fun saveWallet(address: String, name: String)
    suspend fun deleteWallet(address: String)
    suspend fun getWalletBalances(address: String): Map<String, List<CoinStatsBalanceDto>>
    suspend fun getWalletTransactions(
        address: String,
        limit: Int = 5,
        forceRefresh: Boolean = true
    ): List<EtherscanTransactionDto>
}
