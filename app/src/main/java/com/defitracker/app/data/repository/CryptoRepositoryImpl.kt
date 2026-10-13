package com.defitracker.app.data.repository

import android.util.Log
import com.defitracker.app.core.Constants
import com.defitracker.app.data.local.TrackedPairDao
import com.defitracker.app.data.local.TrackedPairEntity
import com.defitracker.app.data.local.WalletDao
import com.defitracker.app.data.local.WalletEntity
import com.defitracker.app.data.remote.BinanceApi
import com.defitracker.app.data.remote.BinanceFuturesApi
import com.defitracker.app.data.remote.BinanceTickerStream
import com.defitracker.app.data.remote.CoinStatsApi
import com.defitracker.app.data.remote.MexcFuturesApi
import com.defitracker.app.data.remote.MexcTickerStream
import com.defitracker.app.data.remote.tv.TradingViewClient
import com.defitracker.app.data.remote.tv.tvResolution
import com.defitracker.app.data.remote.dto.CoinStatsBalanceDto
import com.defitracker.app.data.remote.dto.CoinStatsTransactionDto
import com.defitracker.app.data.remote.dto.CoinStatsTransactionSyncRequest
import com.defitracker.app.data.remote.dto.CoinStatsTransactionSyncWalletDto
import com.defitracker.app.data.remote.dto.EtherscanTransactionDto
import com.defitracker.app.domain.model.AvailableCryptoPair
import com.defitracker.app.domain.model.CryptoPair
import com.defitracker.app.domain.model.LiveQuote
import com.defitracker.app.domain.model.PairDetail
import com.defitracker.app.domain.model.TakerVolume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    private val binanceFuturesApi: BinanceFuturesApi,
    private val coinStatsApi: CoinStatsApi,
    private val mexcFuturesApi: MexcFuturesApi,
    private val tradingViewClient: TradingViewClient,
    private val binanceTickerStream: BinanceTickerStream,
    private val mexcTickerStream: MexcTickerStream,
    private val trackedPairDao: TrackedPairDao,
    private val walletDao: WalletDao
) {

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
    // cache chico solo pa' sparklines, 1 llamada por par
    private val sparkCache = object : LinkedHashMap<String, CachedKlines>(
        SPARK_CACHE_MAX_ENTRIES,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedKlines>?): Boolean {
            return size > SPARK_CACHE_MAX_ENTRIES
        }
    }

    suspend fun getSparklineCloses(symbol: String, source: String, limit: Int = 30): List<Double> =
        recentCloses(symbol, source, "1h", limit.coerceIn(2, 100))

    suspend fun getRecentCloses(symbol: String, source: String, interval: String, limit: Int = 260): List<Double> =
        recentCloses(symbol, source, interval, limit.coerceIn(2, 1000))

    private suspend fun recentCloses(symbol: String, source: String, interval: String, cleanLimit: Int): List<Double> {
        val cacheKey = "$source:$symbol:$interval"
        sparkCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.createdAtMs <= SPARK_CACHE_TTL_MS && cached.rows.isNotEmpty()) {
                return closesOf(cached.rows, cleanLimit)
            }
        }
        return try {
            val rows = when (source) {
                "MEXC" -> getMexcFuturesKlines(symbol, interval, singlePage = true)
                "TV" -> tradingViewClient.getKlines(tvTicker(symbol), tvResolution(interval), 1000)
                else -> binanceApi.getKlines(symbol = symbol, interval = interval, limit = cleanLimit.coerceAtMost(1000))
            }
            if (rows.isNotEmpty()) sparkCache[cacheKey] = CachedKlines(System.currentTimeMillis(), rows)
            closesOf(rows, cleanLimit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logNonFatal("Recent closes request failed for $symbol/$interval", e)
            closesOf(sparkCache[cacheKey]?.rows ?: emptyList(), cleanLimit)
        }
    }

    private fun closesOf(rows: List<List<Any>>, limit: Int): List<Double> =
        rows.takeLast(limit * 2).mapNotNull { row ->
            (row.getOrNull(4) as? Number)?.toDouble()
                ?: row.getOrNull(4)?.toString()?.toDoubleOrNull()
        }.takeLast(limit)

    fun getTrackedPairs(): Flow<List<CryptoPair>> {
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

    suspend fun addTrackedPair(symbol: String, baseAsset: String, quoteAsset: String, source: String) {
        trackedPairDao.insertTrackedPair(
            TrackedPairEntity(
                symbol = symbol,
                baseAsset = baseAsset,
                quoteAsset = quoteAsset,
                source = source
            )
        )
    }

    suspend fun removeTrackedPair(symbol: String) {
        trackedPairDao.deleteTrackedPairBySymbol(symbol)
    }

    suspend fun getPairDetail(symbol: String, source: String): PairDetail {
        return try {
            when (source) {
                "MEXC" -> getMexcFuturesPairDetail(symbol)
                "TV" -> getTvPairDetail(symbol)
                else -> getBinancePairDetail(symbol)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            PairDetail(symbol, "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", true)
        }
    }

    // junta ambos sockets en un mapa key "$symbol-$source"; si un socket falla emite vacio
    fun observeLivePrices(): Flow<Map<String, LiveQuote>> {
        val binance = binanceTickerStream.observe()
            .map { batch ->
                batch.mapNotNull { (symbol, t) ->
                    if (t.close <= 0 || t.open24h <= 0) return@mapNotNull null
                    val pct = (t.close - t.open24h) / t.open24h * 100.0
                    "$symbol-Binance" to LiveQuote(
                        price = formatPrice(t.close),
                        priceChangePercent = String.format(java.util.Locale.US, "%.2f", pct),
                        isPositive = pct >= 0,
                        quoteVolume = t.quoteVolume
                    )
                }.toMap()
            }
            .catch { logNonFatal("Binance live stream failed", it); emit(emptyMap()) }
        val mexc = mexcTickerStream.observe()
            .map { batch ->
                batch.mapNotNull { (symbol, t) ->
                    if (t.lastPrice <= 0) return@mapNotNull null
                    val pct = t.riseFallRate * 100.0
                    "$symbol-MEXC" to LiveQuote(
                        price = formatPrice(t.lastPrice),
                        priceChangePercent = String.format(java.util.Locale.US, "%.2f", pct),
                        isPositive = pct >= 0,
                        // el push no trae amount24: precio*contratos como aprox, el REST lo corrige
                        quoteVolume = t.lastPrice * t.volume24
                    )
                }.toMap()
            }
            .catch { logNonFatal("MEXC live stream failed", it); emit(emptyMap()) }
        return combine(binance, mexc) { b, m -> b + m }
    }

    suspend fun getKlines(
        symbol: String,
        interval: String,
        source: String,
        forceRefresh: Boolean = false
    ): List<List<Any>> {
        val cacheKey = "$source:$symbol:$interval"
        // el rollover no puede comer cache o la vela nueva nunca aparece
        if (!forceRefresh) {
            getFreshCachedKlines(cacheKey)?.let { return it }
        }

        return try {
            val klines = when (source) {
                "MEXC" -> getMexcFuturesKlines(symbol, interval)
                "TV" -> tradingViewClient.getKlines(tvTicker(symbol), tvResolution(interval), tvCountbackFor(interval))
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

    // cola liviana sin cache pa' syncTail, 1 sola llamada, sin paginado
    suspend fun getLatestKlines(
        symbol: String,
        interval: String,
        source: String,
        sinceTimeMs: Long = 0L,
        limit: Int = 10
    ): List<List<Any>> {
        return try {
            when (source) {
                "MEXC" -> getMexcFuturesKlines(
                    symbol,
                    interval,
                    startSec = if (sinceTimeMs > 0L) sinceTimeMs / 1000L else null,
                    singlePage = true
                )
                "TV" -> tradingViewClient.getKlines(tvTicker(symbol), tvResolution(interval), TAIL_SYNC_LIMIT * 5)
                    .let { rows -> if (sinceTimeMs > 0L) rows.filter { (it[0] as? Number)?.toLong() ?: it[0].toString().toLong() >= sinceTimeMs } else rows.takeLast(limit) }
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

    // nota: taker siempre de Binance Futuros (peso 0, sin auth); MEXC se mapea quitando el "_"
    suspend fun getTakerVolumes(
        symbol: String,
        interval: String,
        source: String,
        limit: Int = 500
    ): List<TakerVolume> {
        if (interval.trim() !in TAKER_NATIVE_INTERVALS) return emptyList()
        if (source == "TV") return emptyList()
        return try {
            val binSymbol = if (source == "MEXC") symbol.replace("_", "") else symbol
            binanceFuturesApi.getTakerVolume(
                symbol = binSymbol,
                period = interval.trim(),
                limit = limit.coerceIn(2, 500)
            ).mapNotNull { row ->
                if (row.timestamp <= 0L) return@mapNotNull null
                TakerVolume(
                    timeMs = row.timestamp,
                    buy = row.buyVol?.toDoubleOrNull() ?: 0.0,
                    sell = row.sellVol?.toDoubleOrNull() ?: 0.0
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logNonFatal("Taker volume request failed for $symbol/$interval", e)
            emptyList()
        }
    }

    suspend fun getAvailableSymbols(source: String = "Binance"): List<AvailableCryptoPair> {
        return when (source) {
            "MEXC" -> getMexcFuturesSymbols()
            else -> getBinanceSymbols()
        }
    }

    // solo futuros USDT; la API devuelve contratos spot/coin-m/futuros mezclados
    private suspend fun getMexcFuturesSymbols(): List<AvailableCryptoPair> {
        if (cachedMexcSymbols.isNotEmpty()) return cachedMexcSymbols
        try {
            val fromDetail = mexcFuturesApi.getContracts().data
                .filter { it.futureType == 1 && it.state == 0 && it.quoteCoin == "USDT" }
                .map {
                    AvailableCryptoPair(
                        symbol = it.symbol,
                        baseAsset = it.baseCoin,
                        quoteAsset = it.quoteCoin
                    )
                }
            if (fromDetail.isNotEmpty()) {
                cachedMexcSymbols = fromDetail
                return fromDetail
            }
            logNonFatal("MEXC contract/detail vacio, usando respaldo ticker", Exception("detail-vacio"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logNonFatal("MEXC contract/detail fallo, usando respaldo ticker", e)
        }
        // respaldo: otro endpoint (ticker) por si detail se cae o lo bloquean,
        // el buscador nunca se queda sin MEXC en silencio
        return try {
            val fromTicker = mexcFuturesApi.getAllTickers().data
                .mapNotNull { t ->
                    val sym = t.symbol
                    if (!sym.endsWith("_USDT")) return@mapNotNull null
                    AvailableCryptoPair(
                        symbol = sym,
                        baseAsset = sym.substringBeforeLast("_"),
                        quoteAsset = "USDT"
                    )
                }
            if (fromTicker.isNotEmpty()) cachedMexcSymbols = fromTicker
            fromTicker
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logNonFatal("MEXC respaldo ticker tambien fallo", e)
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
    fun getSavedWallets(): Flow<List<WalletEntity>> = walletDao.getWallets()

    suspend fun saveWallet(address: String, name: String) {
        walletDao.insertWallet(WalletEntity(address, name))
    }

    suspend fun deleteWallet(address: String) {
        walletDao.getWalletByAddress(address)?.let {
            walletDao.deleteWallet(it)
        }
    }

    suspend fun getWalletBalances(address: String): Map<String, List<CoinStatsBalanceDto>> = coroutineScope {
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

    suspend fun getWalletTransactions(
        address: String,
        limit: Int = 5,
        forceRefresh: Boolean = true
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

    private fun tvTicker(symbol: String) =
        if (symbol.startsWith("CRYPTOCAP:")) symbol else "CRYPTOCAP:$symbol"

    private fun tvCountbackFor(interval: String): Int = when (interval) {
        "1m", "5m", "15m" -> 3000
        "30m", "1h" -> 2000
        else -> 1000
    }

    private suspend fun getTvPairDetail(symbol: String): PairDetail {
        val rows = tradingViewClient.getKlines(tvTicker(symbol), "1D", 3)
        if (rows.size < 2) {
            return PairDetail(symbol, "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", true)
        }
        val last = rows.last()
        val prev = rows[rows.size - 2]
        val close = last[4].toString().toDoubleOrNull() ?: 0.0
        val prevClose = prev[4].toString().toDoubleOrNull() ?: 0.0
        val change = close - prevClose
        val pct = if (prevClose == 0.0) 0.0 else change / prevClose * 100.0
        return PairDetail(
            symbol = symbol,
            price = formatPrice(close),
            priceChange = formatPrice(change),
            priceChangePercent = String.format(java.util.Locale.US, "%.2f", pct),
            highPrice = formatPrice(last[2].toString().toDoubleOrNull() ?: 0.0),
            lowPrice = formatPrice(last[3].toString().toDoubleOrNull() ?: 0.0),
            volume = last[5].toString(),
            quoteVolume = "0.00",
            isPositive = pct >= 0
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

    // riseFallRate viene en fraccion, volumen en contratos (no en monedas)
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

    // kline columnar (segundos) -> filas estilo Binance + agregacion en repo
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
        // buckets anclados a calendario, no al indice; si no los bordes bailan cada fetch
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

    // base MEXC mas cercana + factor para agregar en repo
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
            // 2 paginas (2000 velas) sobran pa' la MA200 y el arranque en 100;
            // la tercera solo alargaba la apertura
            "1m", "5m", "15m" -> 2
            "30m", "1h" -> 2
            else -> 1
        }
    }

    private fun formatPrice(price: Double): String {
        return when {
            price < 1.0 -> String.format(java.util.Locale.US, "%.6f", price)
            // nota: banda de cruce $1-10 con 4 decimales; la UI recorta a 2 si es consolidada
            price < 10.0 -> String.format(java.util.Locale.US, "%.4f", price)
            else -> String.format(java.util.Locale.US, "%.2f", price)
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
        const val SPARK_CACHE_MAX_ENTRIES = 60
        const val SPARK_CACHE_TTL_MS = 300_000L
        const val KLINE_CACHE_TTL_MS = 30_000L
        const val TAIL_SYNC_LIMIT = 10
        // TFs con periodo taker nativo en Binance Futuros
        val TAKER_NATIVE_INTERVALS = setOf("5m", "15m", "30m", "1h", "2h", "4h", "6h", "12h", "1d")
        // lunes 2020-01-06T00:00Z, ancla de buckets semanales (la epoca cae jueves)
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

