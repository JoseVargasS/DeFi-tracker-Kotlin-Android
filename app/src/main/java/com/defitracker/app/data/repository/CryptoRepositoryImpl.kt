package com.defitracker.app.data.repository

import android.util.Log
import com.defitracker.app.core.Constants
import com.defitracker.app.data.local.TrackedPairDao
import com.defitracker.app.data.local.TrackedPairEntity
import com.defitracker.app.data.local.WalletDao
import com.defitracker.app.data.local.WalletEntity
import com.defitracker.app.data.remote.BinanceApi
import com.defitracker.app.data.remote.CoinStatsApi
import com.defitracker.app.data.remote.MexcFuturesApi
import com.defitracker.app.data.remote.dto.CoinStatsBalanceDto
import com.defitracker.app.data.remote.dto.CoinStatsTransactionDto
import com.defitracker.app.data.remote.dto.CoinStatsTransactionSyncRequest
import com.defitracker.app.data.remote.dto.CoinStatsTransactionSyncWalletDto
import com.defitracker.app.data.remote.dto.EtherscanTransactionDto
import com.defitracker.app.domain.model.AvailableCryptoPair
import com.defitracker.app.domain.model.CryptoPair
import com.defitracker.app.domain.model.PairDetail
import com.defitracker.app.domain.repository.CryptoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoRepositoryImpl @Inject constructor(
    private val binanceApi: BinanceApi,
    private val coinStatsApi: CoinStatsApi,
    private val mexcFuturesApi: MexcFuturesApi,
    private val trackedPairDao: TrackedPairDao,
    private val walletDao: WalletDao
) : CryptoRepository {

    private fun logNonFatal(context: String, throwable: Throwable) {
        Log.w(TAG, context, throwable)
    }

    private var cachedSymbols: List<AvailableCryptoPair> = emptyList()
    private var cachedMexcSymbols: List<AvailableCryptoPair> = emptyList()
    private val klineCache = object : LinkedHashMap<String, CachedKlines>(
        KLINE_CACHE_MAX_ENTRIES,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedKlines>?): Boolean {
            return size > KLINE_CACHE_MAX_ENTRIES
        }
    }
    private val transactionSyncTimes = mutableMapOf<String, Long>()

    override fun getTrackedPairs(): Flow<List<CryptoPair>> {
        return trackedPairDao.getAllTrackedPairs().map { entities ->
            entities.map {
                CryptoPair(
                    symbol = it.symbol,
                    baseAsset = it.baseAsset,
                    quoteAsset = it.quoteAsset,
                    price = "0.00",
                    priceChangePercent = "0.00",
                    isPositive = true,
                    source = it.source
                )
            }
        }
    }

    override suspend fun addTrackedPair(symbol: String, baseAsset: String, quoteAsset: String, source: String) {
        trackedPairDao.insertTrackedPair(
            TrackedPairEntity(
                symbol = symbol,
                baseAsset = baseAsset,
                quoteAsset = quoteAsset,
                source = source
            )
        )
    }

    override suspend fun removeTrackedPair(symbol: String) {
        trackedPairDao.deleteTrackedPairBySymbol(symbol)
    }

    override suspend fun getPairDetail(symbol: String, source: String): PairDetail {
        return try {
            when (source) {
                "MEXC" -> getMexcFuturesPairDetail(symbol)
                else -> getBinancePairDetail(symbol)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            PairDetail(symbol, "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", true)
        }
    }

    override suspend fun getKlines(
        symbol: String,
        interval: String,
        source: String,
        forceRefresh: Boolean
    ): List<List<Any>> {
        val cacheKey = "$source:$symbol:$interval"
        // ponytail: el rollover no puede comer cache o la vela nueva nunca aparece
        if (!forceRefresh) {
            getFreshCachedKlines(cacheKey)?.let { return it }
        }

        return try {
            val klines = when (source) {
                "MEXC" -> getMexcFuturesKlines(symbol, interval)
                else -> getBinanceKlines(symbol, interval)
            }
            if (klines.isNotEmpty()) {
                klineCache[cacheKey] = CachedKlines(System.currentTimeMillis(), klines)
            }
            klines
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logNonFatal("Klines request failed for $symbol/$interval", e)
            klineCache[cacheKey]?.rows ?: emptyList()
        }
    }

    // ponytail: cola liviana sin cache pa' syncTail, 1 sola llamada, sin paginado
    override suspend fun getLatestKlines(
        symbol: String,
        interval: String,
        source: String,
        sinceTimeMs: Long,
        limit: Int
    ): List<List<Any>> {
        return try {
            when (source) {
                "MEXC" -> getMexcFuturesKlines(
                    symbol,
                    interval,
                    startSec = if (sinceTimeMs > 0L) sinceTimeMs / 1000L else null,
                    singlePage = true
                )
                else -> binanceApi.getKlines(
                    symbol = symbol,
                    interval = interval,
                    limit = limit.coerceIn(2, 50)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logNonFatal("Tail klines request failed for $symbol/$interval", e)
            emptyList()
        }
    }

    override suspend fun getAvailableSymbols(source: String): List<AvailableCryptoPair> {
        return when (source) {
            "MEXC" -> getMexcFuturesSymbols()
            else -> getBinanceSymbols()
        }
    }

    // ponytail: solo futuros USDT; la API devuelve contratos spot/coin-m/futuros mezclados
    private suspend fun getMexcFuturesSymbols(): List<AvailableCryptoPair> {
        if (cachedMexcSymbols.isNotEmpty()) return cachedMexcSymbols
        return try {
            cachedMexcSymbols = mexcFuturesApi.getContracts().data
                .filter { it.futureType == 1 && it.state == 0 && it.quoteCoin == "USDT" }
                .map {
                    AvailableCryptoPair(
                        symbol = it.symbol,
                        baseAsset = it.baseCoin,
                        quoteAsset = it.quoteCoin
                    )
                }
            cachedMexcSymbols
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun getBinanceSymbols(): List<AvailableCryptoPair> {
        return try {
            val info = binanceApi.getExchangeInfo()
            cachedSymbols = info.symbols
                .map {
                    AvailableCryptoPair(
                        symbol = it.symbol,
                        baseAsset = it.baseAsset,
                        quoteAsset = it.quoteAsset
                    )
                }
            cachedSymbols
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Wallets implementation
    override fun getSavedWallets(): Flow<List<WalletEntity>> = walletDao.getWallets()

    override suspend fun saveWallet(address: String, name: String) {
        walletDao.insertWallet(WalletEntity(address, name))
    }

    override suspend fun deleteWallet(address: String) {
        walletDao.getWalletByAddress(address)?.let {
            walletDao.deleteWallet(it)
        }
    }

    override suspend fun getWalletBalances(address: String): Map<String, List<CoinStatsBalanceDto>> = coroutineScope {
        val results = linkedMapOf<String, List<CoinStatsBalanceDto>>()

        walletBalanceChains.forEachIndexed { index, chain ->
            try {
                if (index > 0) delay(500)

                val balances = coinStatsApi.getWalletBalance(
                    address,
                    chain.id,
                    Constants.COINSTATS_API_KEY
                )

                val filtered = balances.filter { balance ->
                    (balance.amount ?: 0.0) * (balance.price ?: 0.0) > 0.0001
                }

                if (filtered.isNotEmpty()) {
                    results[chain.name] = filtered
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code() == 401 || e.code() == 403) {
                    throw IllegalStateException("CoinStats API key is invalid or expired.")
                }
                if (e.code() == 429) {
                    throw IllegalStateException("CoinStats rate limit reached. Try again later.")
                }
                logNonFatal("Wallet balances request failed for ${chain.name}", e)
            } catch (e: Exception) {
                logNonFatal("Unexpected wallet balances failure for ${chain.name}", e)
            }
        }

        results
    }

    override suspend fun getWalletTransactions(
        address: String,
        limit: Int,
        forceRefresh: Boolean
    ): List<EtherscanTransactionDto> = coroutineScope {
        val allTransactions = mutableListOf<EtherscanTransactionDto>()
        val normalizedLimit = limit.coerceIn(TRANSACTION_MIN_DISPLAY_LIMIT, TRANSACTION_MAX_DISPLAY_LIMIT)
        val queryWindow = TransactionQueryWindow.recent(TRANSACTION_QUERY_DAYS)

        transactionChains.forEachIndexed { index, chain ->
            try {
                if (index > 0) delay(TRANSACTION_CHAIN_DELAY_MS)

                if (forceRefresh) {
                    syncWalletTransactions(address, chain)
                }

                val transactions = getWalletTransactionsForChain(
                    address = address,
                    chain = chain,
                    limit = normalizedLimit,
                    queryWindow = queryWindow
                )

                allTransactions += transactions
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code() == 401 || e.code() == 403) {
                    throw IllegalStateException("CoinStats API key is invalid or expired.")
                }
                if (e.code() == 429) {
                    throw IllegalStateException("CoinStats rate limit reached. Try again later.")
                }
                logNonFatal("Wallet transactions request failed for ${chain.name}", e)
            } catch (e: Exception) {
                logNonFatal("Unexpected wallet transactions failure for ${chain.name}", e)
            }
        }

        allTransactions
            .distinctBy { "${it.network}-${it.hash}-${it.tokenSymbol}-${it.value}" }
            .sortedWith(
                compareByDescending<EtherscanTransactionDto> { it.timeStamp.toLongOrNull() ?: 0L }
                    .thenBy { it.network ?: "" }
            )
            .take(normalizedLimit)
    }

    private suspend fun syncWalletTransactions(address: String, chain: CoinStatsChain) {
        val syncKey = "${address.lowercase()}:${chain.id}"
        if (isFreshTransactionSync(syncKey)) return

        try {
            val response = coinStatsApi.syncWalletTransactions(
                apiKey = Constants.COINSTATS_API_KEY,
                body = CoinStatsTransactionSyncRequest(
                    wallets = listOf(
                        CoinStatsTransactionSyncWalletDto(
                            address = address,
                            connectionId = chain.id
                        )
                    )
                )
            )
            if (response.status.equals("syncing", ignoreCase = true)) {
                waitForWalletTransactionsSync(address, chain)
            }
            transactionSyncTimes[syncKey] = System.currentTimeMillis()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) {
                throw IllegalStateException("CoinStats API key is invalid or expired.")
            }
            if (e.code() == 429) {
                throw IllegalStateException("CoinStats rate limit reached. Try again later.")
            }
            if (e.code() == 409) {
                waitForWalletTransactionsSync(address, chain)
                transactionSyncTimes[syncKey] = System.currentTimeMillis()
            } else {
                logNonFatal("Wallet transactions sync failed for ${chain.name}", e)
            }
        } catch (e: Exception) {
            logNonFatal("Unexpected wallet transactions sync failure for ${chain.name}", e)
        }
    }

    private fun isFreshTransactionSync(syncKey: String): Boolean {
        val syncedAt = transactionSyncTimes[syncKey] ?: return false
        return System.currentTimeMillis() - syncedAt <= TRANSACTION_SYNC_TTL_MS
    }

    private suspend fun waitForWalletTransactionsSync(address: String, chain: CoinStatsChain) {
        repeat(TRANSACTION_SYNC_STATUS_ATTEMPTS) {
            delay(TRANSACTION_SYNC_STATUS_DELAY_MS)

            val status = coinStatsApi.getWalletTransactionSyncStatus(
                address = address,
                chainId = chain.id,
                apiKey = Constants.COINSTATS_API_KEY
            ).status

            if (status.equals("synced", ignoreCase = true)) {
                return
            }
        }
    }

    private suspend fun getWalletTransactionsForChain(
        address: String,
        chain: CoinStatsChain,
        limit: Int,
        queryWindow: TransactionQueryWindow
    ): List<EtherscanTransactionDto> {
        val transactions = mutableListOf<EtherscanTransactionDto>()
        val pageLimit = limit.coerceIn(TRANSACTION_MIN_DISPLAY_LIMIT, TRANSACTION_PAGE_LIMIT)

        val result = coinStatsApi.getWalletTransactions(
            address = address,
            chainId = chain.id,
            page = 1,
            limit = pageLimit,
            from = queryWindow.from,
            to = queryWindow.to,
            apiKey = Constants.COINSTATS_API_KEY
        ).result

        transactions += result
            .filterNot { it.type.equals("Fill", ignoreCase = true) }
            .mapNotNull { it.toTransactionDto(address, chain.name) }

        return transactions
    }

    private fun CoinStatsTransactionDto.toTransactionDto(
        walletAddress: String,
        networkName: String
    ): EtherscanTransactionDto? {
        val firstItem = transactions.firstOrNull()?.items?.firstOrNull()
        val count = firstItem?.count ?: coinData?.count ?: return null
        val symbol = firstItem?.coin?.symbol ?: coinData?.symbol ?: return null
        val action = transactions.firstOrNull()?.action ?: type.orEmpty()
        val isSent = action.equals("Sent", ignoreCase = true) || count < 0.0
        val hashId = hash?.id ?: firstItem?.id ?: return null
        val timestamp = try {
            date?.let { Instant.parse(it).epochSecond.toString() } ?: "0"
        } catch (_: Exception) {
            "0"
        }

        return EtherscanTransactionDto(
            hash = hashId,
            from = firstItem?.fromAddress ?: if (isSent) walletAddress else "",
            to = firstItem?.toAddress ?: if (isSent) "" else walletAddress,
            value = kotlin.math.abs(count).toString(),
            timeStamp = timestamp,
            tokenSymbol = symbol.trim(),
            tokenDecimal = "0",
            tokenName = firstItem?.coin?.name ?: symbol.trim(),
            input = null,
            symbol = null,
            decimals = null,
            network = networkName
        )
    }

    private suspend fun getBinancePairDetail(symbol: String): PairDetail {
        return try {
            val stats = binanceApi.get24hStats(symbol)
            val priceDouble = stats.lastPrice?.toDoubleOrNull() ?: 0.0
            PairDetail(
                symbol = symbol,
                price = formatPrice(priceDouble),
                priceChange = stats.priceChange,
                priceChangePercent = stats.priceChangePercent,
                highPrice = stats.highPrice,
                lowPrice = stats.lowPrice,
                volume = stats.volume,
                quoteVolume = stats.quoteVolume,
                isPositive = (stats.priceChangePercent.toDoubleOrNull() ?: 0.0) >= 0
            )
        } catch (exception: Exception) {
            logNonFatal("24h stats request failed for $symbol, falling back to ticker price", exception)
            val fallbackPrice = binanceApi.getPrice(symbol).price.toDoubleOrNull() ?: 0.0
            PairDetail(
                symbol = symbol,
                price = formatPrice(fallbackPrice),
                priceChange = "0.00",
                priceChangePercent = "0.00",
                highPrice = "0.00",
                lowPrice = "0.00",
                volume = "0.00",
                quoteVolume = "0.00",
                isPositive = true
            )
        }
    }

    private suspend fun getBinanceKlines(symbol: String, interval: String): List<List<Any>> {
        val allKlines = mutableListOf<List<Any>>()
        var endTime: Long? = null
        var pagesFetched = 0
        val pageCount = chartPageCountForInterval(interval)

        while (pagesFetched < pageCount) {
            val page = binanceApi.getKlines(
                symbol = symbol,
                interval = interval,
                limit = CHART_KLINE_PAGE_SIZE,
                endTime = endTime
            )
            if (page.isEmpty()) break

            allKlines.addAll(0, page)
            val oldestOpenTime = page.firstOrNull()?.getOrNull(0)?.toString()?.toDoubleOrNull()?.toLong()
                ?: break
            endTime = oldestOpenTime - 1
            pagesFetched++

            if (page.size < CHART_KLINE_PAGE_SIZE) break
        }

        return allKlines
    }

    // ponytail: riseFallRate viene en fraccion, volumen en contratos (no en monedas)
    private suspend fun getMexcFuturesPairDetail(symbol: String): PairDetail {
        val t = mexcFuturesApi.getTicker(symbol).data
        val changePct = t.riseFallRate * 100.0
        return PairDetail(
            symbol = symbol,
            price = formatPrice(t.lastPrice),
            priceChange = formatPrice(t.riseFallValue),
            priceChangePercent = String.format(java.util.Locale.US, "%.2f", changePct),
            highPrice = formatPrice(t.high24Price),
            lowPrice = formatPrice(t.lower24Price),
            volume = t.volume24.toString(),
            quoteVolume = t.amount24.toString(),
            isPositive = changePct >= 0
        )
    }

    // ponytail: kline columnar (segundos) -> filas estilo Binance + agregacion en repo
    private suspend fun getMexcFuturesKlines(
        symbol: String,
        interval: String,
        startSec: Long? = null,
        singlePage: Boolean = false
    ): List<List<Any>> {
        val (mexcInterval, factor) = mexcKlinePlan(interval)
        val allRows = mutableListOf<List<Any>>()
        var end: Long? = null
        var pagesFetched = 0
        val pageCount = chartPageCountForInterval(interval)

        while (pagesFetched < pageCount) {
            val data = mexcFuturesApi.getKlines(
                symbol = symbol,
                interval = mexcInterval,
                start = startSec,
                end = end
            ).data
            val n = data.time.size
            if (n == 0) break

            for (i in n - 1 downTo 0) {
                allRows.add(
                    0,
                    listOf<Any>(
                        data.time[i] * 1000L,
                        data.open.getOrNull(i) ?: 0.0,
                        data.high.getOrNull(i) ?: 0.0,
                        data.low.getOrNull(i) ?: 0.0,
                        data.close.getOrNull(i) ?: 0.0,
                        data.vol.getOrNull(i) ?: 0.0,
                        0L, 0.0, 0, 0.0
                    )
                )
            }
            end = data.time.firstOrNull()?.minus(1) ?: break
            pagesFetched++

            if (singlePage || n < MEXC_KLINE_PAGE_SIZE) break
        }

        if (factor <= 1) return allRows
        return aggregateRows(allRows, factor, mexcInterval)
    }

    private fun aggregateRows(rows: List<List<Any>>, factor: Int, mexcInterval: String): List<List<Any>> {
        if (factor <= 1 || rows.isEmpty()) return rows
        val chunkDurMs = mexcBaseDurMs(mexcInterval) * factor
        if (chunkDurMs <= 0L) return rows
        // ponytail: buckets anclados a calendario, no al indice; si no los bordes bailan cada fetch
        val weekAnchored = mexcInterval == "Week1"
        val buckets = LinkedHashMap<Long, MutableList<List<Any>>>()
        for (row in rows) {
            val t = row.toLongAt(0)
            val key = if (weekAnchored) {
                t - ((t - WEEK_ANCHOR_MS) % chunkDurMs)
            } else {
                t - (t % chunkDurMs)
            }
            buckets.getOrPut(key) { mutableListOf() }.add(row)
        }
        val out = ArrayList<List<Any>>(buckets.size)
        for ((key, bucket) in buckets) {
            var high = Double.NEGATIVE_INFINITY
            var low = Double.POSITIVE_INFINITY
            var volume = 0.0
            for (row in bucket) {
                high = high.coerceAtLeast(row.toDoubleAt(2))
                low = low.coerceAtMost(row.toDoubleAt(3))
                volume += row.toDoubleAt(5)
            }
            val first = bucket.first()
            val last = bucket.last()
            out.add(
                listOf<Any>(
                    key, first.toDoubleAt(1), high, low,
                    last.toDoubleAt(4), volume, 0L, 0.0, 0, 0.0
                )
            )
        }
        return out
    }

    private fun List<Any>.toDoubleAt(index: Int): Double {
        return when (val v = getOrNull(index)) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun List<Any>.toLongAt(index: Int): Long {
        return when (val v = getOrNull(index)) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong() ?: 0L
            else -> 0L
        }
    }

    // ponytail: base MEXC mas cercana + factor para agregar en repo
    private fun mexcBaseDurMs(mexcInterval: String): Long = when (mexcInterval) {
        "Min1" -> 60_000L
        "Min5" -> 300_000L
        "Min15" -> 900_000L
        "Min30" -> 1_800_000L
        "Min60" -> 3_600_000L
        "Hour4" -> 14_400_000L
        "Day1" -> 86_400_000L
        "Week1" -> 604_800_000L
        else -> 0L
    }
    private fun mexcKlinePlan(interval: String): Pair<String, Int> {
        return when (interval) {
            "1m" -> "Min1" to 1
            "5m" -> "Min5" to 1
            "15m" -> "Min15" to 1
            "30m" -> "Min30" to 1
            "1h" -> "Min60" to 1
            "2h" -> "Min60" to 2
            "4h" -> "Hour4" to 1
            "6h" -> "Min60" to 6
            "12h" -> "Hour4" to 3
            "1d" -> "Day1" to 1
            "3d" -> "Day1" to 3
            "5d" -> "Day1" to 5
            "1w" -> "Week1" to 1
            "2w" -> "Week1" to 2
            "1mo" -> "Month1" to 1
            else -> "Min60" to 1
        }
    }

    private fun getFreshCachedKlines(cacheKey: String): List<List<Any>>? {
        val cached = klineCache[cacheKey] ?: return null
        val ageMs = System.currentTimeMillis() - cached.createdAtMs
        return cached.rows.takeIf { ageMs <= KLINE_CACHE_TTL_MS && it.isNotEmpty() }
    }

    private fun chartPageCountForInterval(interval: String): Int {
        return when (interval) {
            "1m", "5m", "15m" -> 3
            "30m", "1h" -> 2
            else -> 1
        }
    }

    private fun formatPrice(price: Double): String {
        return if (price < 1.0) {
            String.format(java.util.Locale.US, "%.6f", price)
        } else {
            String.format(java.util.Locale.US, "%.2f", price)
        }
    }

    private data class CachedKlines(
        val createdAtMs: Long,
        val rows: List<List<Any>>
    )

    private data class TransactionQueryWindow(
        val from: String,
        val to: String
    ) {
        companion object {
            fun recent(days: Long): TransactionQueryWindow {
                val now = Instant.now()
                return TransactionQueryWindow(
                    from = now.minus(days, ChronoUnit.DAYS).toString(),
                    to = now.toString()
                )
            }
        }
    }

    private data class CoinStatsChain(val id: String, val name: String)

    private companion object {
        const val TAG = "CryptoRepository"
        const val CHART_KLINE_PAGE_SIZE = 1000
        const val MEXC_KLINE_PAGE_SIZE = 2000
        const val KLINE_CACHE_MAX_ENTRIES = 24
        const val KLINE_CACHE_TTL_MS = 30_000L
        const val TAIL_SYNC_LIMIT = 10
        // ponytail: lunes 2020-01-06T00:00Z, ancla de buckets semanales (la epoca cae jueves)
        const val WEEK_ANCHOR_MS = 1_578_182_400_000L
        const val TRANSACTION_MIN_DISPLAY_LIMIT = 5
        const val TRANSACTION_MAX_DISPLAY_LIMIT = 100
        const val TRANSACTION_PAGE_LIMIT = 100
        const val TRANSACTION_CHAIN_DELAY_MS = 450L
        const val TRANSACTION_SYNC_STATUS_ATTEMPTS = 3
        const val TRANSACTION_SYNC_STATUS_DELAY_MS = 650L
        const val TRANSACTION_SYNC_TTL_MS = 60_000L
        const val TRANSACTION_QUERY_DAYS = 365L

        val walletBalanceChains = listOf(
            CoinStatsChain("ethereum", "Ether"),
            CoinStatsChain("base-wallet", "Base"),
            CoinStatsChain("binancesmartchain", "BSC"),
            CoinStatsChain("solana", "Solana"),
            CoinStatsChain("optimism", "Optimism"),
            CoinStatsChain("arbitrum", "Arbitrum"),
            CoinStatsChain("polygon", "Polygon")
        )

        val transactionChains = listOf(
            CoinStatsChain("ethereum", "Ethereum"),
            CoinStatsChain("base-wallet", "Base"),
            CoinStatsChain("binancesmartchain", "BSC"),
            CoinStatsChain("polygon", "Polygon"),
            CoinStatsChain("arbitrum", "Arbitrum"),
            CoinStatsChain("optimism", "Optimism"),
            CoinStatsChain("solana", "Solana")
        )
    }
}

