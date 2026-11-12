package com.defitracker.app.data.repository

import android.util.Log
import com.defitracker.app.BuildConfig
import com.defitracker.app.core.Brokers
import com.defitracker.app.core.Constants
import com.defitracker.app.core.Timeframes
import com.defitracker.app.data.local.TrackedPairDao
import com.defitracker.app.data.local.TrackedPairEntity
import com.defitracker.app.data.local.WalletDao
import com.defitracker.app.data.local.WalletEntity
import com.defitracker.app.data.remote.BinanceApi
import com.defitracker.app.data.remote.BinanceFuturesApi
import com.defitracker.app.data.remote.BinanceTickerStream
import com.defitracker.app.data.remote.BingxApi
import com.defitracker.app.data.remote.BingxTickerStream
import com.defitracker.app.data.remote.BitunixApi
import com.defitracker.app.data.remote.BitunixTickerStream
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
import com.defitracker.app.presentation.crypto_detail.toDoubleValue
import com.defitracker.app.presentation.crypto_detail.formatRepositoryPrice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
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
    private val bitunixApi: BitunixApi,
    private val bingxApi: BingxApi,
    private val tradingViewClient: TradingViewClient,
    private val binanceTickerStream: BinanceTickerStream,
    private val mexcTickerStream: MexcTickerStream,
    private val bitunixTickerStream: BitunixTickerStream,
    private val bingxTickerStream: BingxTickerStream,
    private val trackedPairDao: TrackedPairDao,
    private val walletDao: WalletDao
) {

    private fun logNonFatal(context: String, throwable: Throwable) {
        Log.w(TAG, context, throwable)
    }

    // los sockets son estado compartido de la app, no de un ViewModel: cada colector
    // abria su propio par de conexiones (y cada par es otro par de sockets al exchange)
    private val liveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sharedLivePrices: Flow<Map<String, LiveQuote>> = buildLivePrices()
        .shareIn(liveScope, SharingStarted.WhileSubscribed(LIVE_PRICE_STOP_TIMEOUT_MS), replay = 1)

    private fun requireCoinStatsApiKey(): String =
        BuildConfig.COINSTATS_API_KEY.trim().takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException(
                "CoinStats API key is not configured. Add COINSTATS_API_KEY to local.properties."
            )

    private var cachedSymbols: List<AvailableCryptoPair> = emptyList()
    private var cachedMexcSymbols: List<AvailableCryptoPair> = emptyList()
    private var cachedBitunixSymbols: List<AvailableCryptoPair> = emptyList()
    private var cachedBingxSymbols: List<AvailableCryptoPair> = emptyList()
    // open de 24h por par de BingX: el socket no lo manda, se refresca cada 5 min
    private val bingxOpenCache = HashMap<String, Pair<Double, Long>>()
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
                Brokers.MEXC -> getMexcFuturesKlines(symbol, interval, singlePage = true)
                Brokers.BITUNIX -> getBitunixKlines(symbol, interval, singlePage = true)
                Brokers.BINGX -> getBingxKlines(symbol, interval, limit = cleanLimit)
                Brokers.TV -> tradingViewClient.getKlines(tvTicker(symbol), tvResolution(interval), cleanLimit.coerceAtMost(1000))
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

    suspend fun removeTrackedPair(symbol: String, source: String) {
        trackedPairDao.deleteTrackedPair(symbol, source)
    }

    suspend fun getPairDetail(symbol: String, source: String): PairDetail {
        return try {
            when (source) {
                Brokers.MEXC -> getMexcFuturesPairDetail(symbol)
                Brokers.BITUNIX -> getBitunixPairDetail(symbol)
                Brokers.BINGX -> getBingxPairDetail(symbol)
                Brokers.TV -> getTvPairDetail(symbol)
                else -> getBinancePairDetail(symbol)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            PairDetail(symbol, "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", true)
        }
    }

    // junta los cuatro sockets en un mapa key "$symbol-$source"; si un socket falla emite vacio
    fun observeLivePrices(): Flow<Map<String, LiveQuote>> = sharedLivePrices

    private fun buildLivePrices(): Flow<Map<String, LiveQuote>> {
        // los cuatro sockets son por simbolo: se pide solo lo trackeado de cada fuente.
        // sin pares de esa fuente, no se abre socket
        val binance = binanceTickerStream.observe(trackedSymbolsFor(Brokers.BINANCE))
            .map { batch ->
                val now = System.currentTimeMillis()
                batch.mapNotNull { (symbol, t) ->
                    if (t.close <= 0 || t.open24h <= 0) return@mapNotNull null
                    val pct = (t.close - t.open24h) / t.open24h * 100.0
                    "$symbol-${Brokers.BINANCE}" to LiveQuote(
                        price = formatRepositoryPrice(t.close),
                        priceChangePercent = String.format(java.util.Locale.US, "%.2f", pct),
                        isPositive = pct >= 0,
                        quoteVolume = t.quoteVolume,
                        priceValue = t.close,
                        receivedAtMs = now
                    )
                }.toMap()
            }
            .catch { logNonFatal("Binance live stream failed", it); emit(emptyMap()) }
        val mexc = mexcTickerStream.observe(trackedSymbolsFor(Brokers.MEXC))
            .map { batch ->
                val now = System.currentTimeMillis()
                batch.mapNotNull { (symbol, t) ->
                    if (t.lastPrice <= 0) return@mapNotNull null
                    val pct = t.riseFallRate * 100.0
                    "$symbol-${Brokers.MEXC}" to LiveQuote(
                        price = formatRepositoryPrice(t.lastPrice),
                        priceChangePercent = String.format(java.util.Locale.US, "%.2f", pct),
                        isPositive = pct >= 0,
                        // el push trae amount24 (monto real en quote); si no, precio*contratos
                        quoteVolume = if (t.amount24 > 0.0) t.amount24 else t.lastPrice * t.volume24,
                        priceValue = t.lastPrice,
                        receivedAtMs = now
                    )
                }.toMap()
            }
            .catch { logNonFatal("MEXC live stream failed", it); emit(emptyMap()) }
        // Bitunix/BingX solo aceptan suscripcion por simbolo
        val bitunix = bitunixTickerStream.observe(trackedSymbolsFor(Brokers.BITUNIX))
            .map { batch ->
                val now = System.currentTimeMillis()
                batch.mapNotNull { (symbol, t) ->
                    if (t.lastPrice <= 0 || t.open24h <= 0) return@mapNotNull null
                    val pct = (t.lastPrice - t.open24h) / t.open24h * 100.0
                    "$symbol-${Brokers.BITUNIX}" to LiveQuote(
                        price = formatRepositoryPrice(t.lastPrice),
                        priceChangePercent = String.format(java.util.Locale.US, "%.2f", pct),
                        isPositive = pct >= 0,
                        quoteVolume = t.quoteVolume,
                        priceValue = t.lastPrice,
                        receivedAtMs = now
                    )
                }.toMap()
            }
            .catch { logNonFatal("Bitunix live stream failed", it); emit(emptyMap()) }
        val bingx: Flow<Map<String, LiveQuote>> = bingxTickerStream.observe(trackedSymbolsFor(Brokers.BINGX))
            .map { batch ->
                val now = System.currentTimeMillis()
                val out = HashMap<String, LiveQuote>(batch.size)
                for ((symbol, t) in batch) {
                    if (t.lastPrice <= 0) continue
                    val open = bingxOpen24h(symbol)
                    if (open <= 0.0) continue
                    val pct = (t.lastPrice - open) / open * 100.0
                    out["$symbol-${Brokers.BINGX}"] = LiveQuote(
                        price = formatRepositoryPrice(t.lastPrice),
                        priceChangePercent = String.format(java.util.Locale.US, "%.2f", pct),
                        isPositive = pct >= 0,
                        quoteVolume = t.quoteVolume,
                        priceValue = t.lastPrice,
                        receivedAtMs = now
                    )
                }
                out
            }
            .catch { logNonFatal("BingX live stream failed", it); emit(HashMap()) }
        return combine(binance, mexc, bitunix, bingx) { b, m, bu, bg -> b + m + bu + bg }
    }

    // el push de BingX no trae el open de 24h: se toma del REST una vez cada 5 min por par.
    // secuencial y solo para pares trackeados, para no castigar el rate limit
    private suspend fun bingxOpen24h(symbol: String): Double {
        val now = System.currentTimeMillis()
        bingxOpenCache[symbol]?.let { (open, at) ->
            if (now - at <= BINGX_OPEN_TTL_MS) return open
        }
        return try {
            val open = bingxApi.getTicker(symbol).data?.openPrice.toDoubleValue()
            if (open > 0.0) bingxOpenCache[symbol] = open to now
            open
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // no cortar el socket por un fallo del REST: ese par se cae y el REST lo repinta
            logNonFatal("BingX open 24h failed for $symbol", e)
            0.0
        }
    }

    private fun trackedSymbolsFor(source: String): Flow<List<String>> =
        trackedPairDao.getAllTrackedPairs()
            .map { list -> list.filter { it.source == source }.map { it.symbol }.distinct().sorted() }
            .distinctUntilChanged()

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
                Brokers.MEXC -> getMexcFuturesKlines(symbol, interval)
                Brokers.BITUNIX -> getBitunixKlines(symbol, interval)
                Brokers.BINGX -> getBingxKlines(symbol, interval)
                Brokers.TV -> tradingViewClient.getKlines(tvTicker(symbol), tvResolution(interval), tvCountbackFor(interval))
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

    // cola liviana sin cache pa' syncTail, 1 sola llamada de 10, sin paginado
    suspend fun getLatestKlines(
        symbol: String,
        interval: String,
        source: String,
        sinceTimeMs: Long = 0L
    ): List<List<Any>> {
        val limit = 10
        return try {
            when (source) {
                Brokers.MEXC -> getMexcFuturesKlines(
                    symbol,
                    interval,
                    startSec = if (sinceTimeMs > 0L) sinceTimeMs / 1000L else null,
                    singlePage = true
                )
                Brokers.BITUNIX -> getBitunixKlines(
                    symbol,
                    interval,
                    startMs = sinceTimeMs.takeIf { it > 0L },
                    singlePage = true
                )
                Brokers.BINGX -> getBingxKlines(symbol, interval, limit = limit)
                Brokers.TV -> tradingViewClient.getKlines(tvTicker(symbol), tvResolution(interval), TAIL_SYNC_LIMIT * 5)
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

    // nota: taker siempre de Binance Futuros (klines, sin auth); MEXC se mapea quitando el "_".
    // El kline en curso si trae volumen parcial (indice 9 = taker buy base), por eso
    // la vela se forma en vivo aunque el TF sea de 1d.
    suspend fun getTakerVolumes(
        symbol: String,
        interval: String,
        source: String,
        limit: Int = 500
    ): List<TakerVolume> {
        if (interval.trim() !in TAKER_NATIVE_INTERVALS) return emptyList()
        if (source == Brokers.TV) return emptyList()
        return try {
            // MEXC usa "_" y BingX "-": el taker siempre es Binance Futuros
            val binSymbol = symbol.replace("_", "").replace("-", "")
            binanceFuturesApi.getKlines(
                symbol = binSymbol,
                interval = interval.trim(),
                limit = limit.coerceIn(2, 500)
            ).mapNotNull { row ->
                val time = row.getOrNull(0).toDoubleValue().toLong()
                if (time <= 0L) return@mapNotNull null
                val volume = row.getOrNull(5).toDoubleValue()
                val buy = row.getOrNull(9).toDoubleValue()
                TakerVolume(
                    timeMs = time,
                    buy = buy,
                    sell = (volume - buy).coerceAtLeast(0.0)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logNonFatal("Taker volume request failed for $symbol/$interval", e)
            emptyList()
        }
    }

    suspend fun getAvailableSymbols(source: String = Brokers.BINANCE): List<AvailableCryptoPair> {
        return when (source) {
            Brokers.MEXC -> getMexcFuturesSymbols()
            Brokers.BITUNIX -> getBitunixSymbols()
            Brokers.BINGX -> getBingxSymbols()
            else -> getBinanceSymbols()
        }
    }

    // solo perps USDT operativos: la API tambien lista USD/USDC y simbolos en preview
    private suspend fun getBitunixSymbols(): List<AvailableCryptoPair> {
        if (cachedBitunixSymbols.isNotEmpty()) return cachedBitunixSymbols
        return try {
            val list = bitunixApi.getTradingPairs().data.orEmpty()
                .filter { it.quote == "USDT" && it.symbolStatus == "OPEN" }
                .map {
                    AvailableCryptoPair(
                        symbol = it.symbol,
                        baseAsset = it.base,
                        quoteAsset = it.quote
                    )
                }
            if (list.isNotEmpty()) cachedBitunixSymbols = list
            list
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logNonFatal("Bitunix trading_pairs failed", e)
            emptyList()
        }
    }

    // status 25 son perps de acciones: solo status 1 y contratos abiertos por API
    private suspend fun getBingxSymbols(): List<AvailableCryptoPair> {
        if (cachedBingxSymbols.isNotEmpty()) return cachedBingxSymbols
        return try {
            val list = bingxApi.getContracts().data.orEmpty()
                .filter { it.currency == "USDT" && it.status == 1 && it.apiStateOpen == "true" }
                .map {
                    AvailableCryptoPair(
                        symbol = it.symbol,
                        baseAsset = it.asset,
                        quoteAsset = it.currency
                    )
                }
            if (list.isNotEmpty()) cachedBingxSymbols = list
            list
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logNonFatal("BingX contracts failed", e)
            emptyList()
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
        val apiKey = requireCoinStatsApiKey()
        val results = linkedMapOf<String, List<CoinStatsBalanceDto>>()

        walletBalanceChains.forEachIndexed { index, chain ->
            try {
                if (index > 0) delay(500)

                val balances = coinStatsApi.getWalletBalance(
                    address,
                    chain.id,
                    apiKey
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
        val apiKey = requireCoinStatsApiKey()
        val allTransactions = mutableListOf<EtherscanTransactionDto>()
        val normalizedLimit = limit.coerceIn(TRANSACTION_MIN_DISPLAY_LIMIT, TRANSACTION_MAX_DISPLAY_LIMIT)
        val queryWindow = TransactionQueryWindow.recent(TRANSACTION_QUERY_DAYS)

        transactionChains.forEachIndexed { index, chain ->
            try {
                if (index > 0) delay(TRANSACTION_CHAIN_DELAY_MS)

                if (forceRefresh) {
                    syncWalletTransactions(address, chain, apiKey)
                }

                val transactions = getWalletTransactionsForChain(
                    address = address,
                    chain = chain,
                    limit = normalizedLimit,
                    queryWindow = queryWindow,
                    apiKey = apiKey
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

    private suspend fun syncWalletTransactions(
        address: String,
        chain: CoinStatsChain,
        apiKey: String
    ) {
        val syncKey = "${address.lowercase()}:${chain.id}"
        if (isFreshTransactionSync(syncKey)) return

        try {
            val response = coinStatsApi.syncWalletTransactions(
                apiKey = apiKey,
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
                waitForWalletTransactionsSync(address, chain, apiKey)
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
                waitForWalletTransactionsSync(address, chain, apiKey)
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

    private suspend fun waitForWalletTransactionsSync(
        address: String,
        chain: CoinStatsChain,
        apiKey: String
    ) {
        repeat(TRANSACTION_SYNC_STATUS_ATTEMPTS) {
            delay(TRANSACTION_SYNC_STATUS_DELAY_MS)

            val status = coinStatsApi.getWalletTransactionSyncStatus(
                address = address,
                chainId = chain.id,
                apiKey = apiKey
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
        queryWindow: TransactionQueryWindow,
        apiKey: String
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
            apiKey = apiKey
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
            network = networkName
        )
    }

    private fun tvTicker(symbol: String) =
        if (symbol.startsWith("CRYPTOCAP:")) symbol else "CRYPTOCAP:$symbol"

    private fun tvCountbackFor(interval: String): Int = Timeframes.tvCountback(interval)

    private suspend fun getTvPairDetail(symbol: String): PairDetail {
        val rows = tradingViewClient.getKlines(tvTicker(symbol), "1D", 3)
        if (rows.size < 2) {
            return PairDetail(symbol, "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", true)
        }
        val last = rows.last()
        val prev = rows[rows.size - 2]
        val close = last[4].toString().toDoubleValue()
        val prevClose = prev[4].toString().toDoubleValue()
        val change = close - prevClose
        val pct = if (prevClose == 0.0) 0.0 else change / prevClose * 100.0
        return PairDetail(
            symbol = symbol,
            price = formatRepositoryPrice(close),
            priceChange = formatRepositoryPrice(change),
            priceChangePercent = String.format(java.util.Locale.US, "%.2f", pct),
            highPrice = formatRepositoryPrice(last[2].toString().toDoubleValue()),
            lowPrice = formatRepositoryPrice(last[3].toString().toDoubleValue()),
            volume = last[5].toString(),
            quoteVolume = "0.00",
            isPositive = pct >= 0
        )
    }

    private suspend fun getBinancePairDetail(symbol: String): PairDetail {
        return try {
            val stats = binanceApi.get24hStats(symbol)
            val priceDouble = stats.lastPrice.toDoubleValue()
            PairDetail(
                symbol = symbol,
                price = formatRepositoryPrice(priceDouble),
                priceChange = stats.priceChange,
                priceChangePercent = stats.priceChangePercent,
                highPrice = stats.highPrice,
                lowPrice = stats.lowPrice,
                volume = stats.volume,
                quoteVolume = stats.quoteVolume,
                isPositive = (stats.priceChangePercent.toDoubleValue()) >= 0
            )
        } catch (exception: Exception) {
            logNonFatal("24h stats request failed for $symbol, falling back to ticker price", exception)
            val fallbackPrice = binanceApi.getPrice(symbol).price.toDoubleValue()
            PairDetail(
                symbol = symbol,
                price = formatRepositoryPrice(fallbackPrice),
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
        // las paginas llegan de la mas nueva a la mas vieja: se juntan y se dan vuelta al
        // final, insertar al inicio de la lista copiaba todo en cada pagina
        val pages = ArrayList<List<List<Any>>>()
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

            pages.add(page)
            val oldestOpenTime = page.firstOrNull()?.getOrNull(0)?.toString()?.toDoubleOrNull()?.toLong()
                ?: break
            endTime = oldestOpenTime - 1
            pagesFetched++

            if (page.size < CHART_KLINE_PAGE_SIZE) break
        }

        return pages.asReversed().flatten()
    }

    // riseFallRate viene en fraccion, volumen en contratos (no en monedas)
    private suspend fun getMexcFuturesPairDetail(symbol: String): PairDetail {
        val t = mexcFuturesApi.getTicker(symbol).data
        val changePct = t.riseFallRate * 100.0
        return PairDetail(
            symbol = symbol,
            price = formatRepositoryPrice(t.lastPrice),
            priceChange = formatRepositoryPrice(t.riseFallValue),
            priceChangePercent = String.format(java.util.Locale.US, "%.2f", changePct),
            highPrice = formatRepositoryPrice(t.high24Price),
            lowPrice = formatRepositoryPrice(t.lower24Price),
            volume = t.volume24.toString(),
            quoteVolume = t.amount24.toString(),
            isPositive = changePct >= 0
        )
    }

    // el ticker no trae porcentaje: se calcula contra el open de 24h
    private suspend fun getBitunixPairDetail(symbol: String): PairDetail {
        val t = bitunixApi.getTickers(symbol).data?.firstOrNull()
            ?: throw IllegalStateException("Bitunix ticker vacio para $symbol")
        val last = t.lastPrice.toDoubleValue()
        val open = t.open.toDoubleValue()
        val pct = if (open > 0.0) (last - open) / open * 100.0 else 0.0
        return PairDetail(
            symbol = symbol,
            price = formatRepositoryPrice(last),
            priceChange = formatRepositoryPrice(last - open),
            priceChangePercent = String.format(java.util.Locale.US, "%.2f", pct),
            highPrice = formatRepositoryPrice(t.high.toDoubleValue()),
            lowPrice = formatRepositoryPrice(t.low.toDoubleValue()),
            volume = t.baseVol.orEmpty(),
            quoteVolume = t.quoteVol.orEmpty(),
            isPositive = pct >= 0
        )
    }

    private suspend fun getBingxPairDetail(symbol: String): PairDetail {
        val t = bingxApi.getTicker(symbol).data
            ?: throw IllegalStateException("BingX ticker vacio para $symbol")
        return PairDetail(
            symbol = symbol,
            price = formatRepositoryPrice(t.lastPrice.toDoubleValue()),
            priceChange = t.priceChange.orEmpty(),
            priceChangePercent = t.priceChangePercent.orEmpty(),
            highPrice = t.highPrice.orEmpty(),
            lowPrice = t.lowPrice.orEmpty(),
            volume = t.volume.orEmpty(),
            quoteVolume = t.quoteVolume.orEmpty(),
            isPositive = t.priceChangePercent.toDoubleValue() >= 0
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
        // misma idea que Binance: paginas nuevas primero, se dan vuelta al final
        val pages = ArrayList<List<List<Any>>>()
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

            val pageRows = ArrayList<List<Any>>(n)
            for (i in 0 until n) {
                pageRows.add(
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
            pages.add(pageRows)
            end = data.time.firstOrNull()?.minus(1) ?: break
            pagesFetched++

            if (singlePage || n < MEXC_KLINE_PAGE_SIZE) break
        }

        val allRows = pages.asReversed().flatten()
        if (factor <= 1) return allRows
        return aggregateRows(allRows, factor, mexcInterval)
    }

    // topea en 200 velas por request y las devuelve de la mas nueva a la mas vieja:
    // se pagina hacia atras con endTime y se ordena al final (la app espera ascendente)
    private suspend fun getBitunixKlines(
        symbol: String,
        interval: String,
        startMs: Long? = null,
        singlePage: Boolean = false
    ): List<List<Any>> {
        val pages = ArrayList<List<List<Any>>>(BITUNIX_KLINE_PAGES)
        var end: Long? = null
        var fetched = 0
        val maxPages = if (singlePage) 1 else BITUNIX_KLINE_PAGES

        while (fetched < maxPages) {
            val data = bitunixApi.getKlines(
                symbol = symbol,
                interval = interval,
                limit = BITUNIX_KLINE_PAGE_SIZE,
                startTime = startMs,
                endTime = end
            ).data.orEmpty()
            if (data.isEmpty()) break

            pages.add(data.map { it.toBinanceRow() })
            // endTime es inclusivo: reusarlo repetiria la ultima vela de la pagina
            end = data.lastOrNull()?.time?.toLongOrNull()?.minus(1) ?: break
            fetched++
            if (data.size < BITUNIX_KLINE_PAGE_SIZE) break
        }

        return pages.pagesToAscendingRows()
    }

    // hasta 1000 velas en una sola llamada, tambien descendente
    private suspend fun getBingxKlines(
        symbol: String,
        interval: String,
        limit: Int = BINGX_KLINE_LIMIT
    ): List<List<Any>> =
        bingxApi.getKlines(symbol, interval, limit.coerceIn(2, BINGX_KLINE_LIMIT))
            .data.orEmpty()
            .toAscendingRows()

    private fun aggregateRows(rows: List<List<Any>>, factor: Int, mexcInterval: String): List<List<Any>> {
        if (factor <= 1 || rows.isEmpty()) return rows
        val chunkDurMs = Timeframes.mexcBaseDurationMs(mexcInterval) * factor
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
            is String -> v.toDoubleValue()
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
    private fun mexcKlinePlan(interval: String): Pair<String, Int> = Timeframes.mexcKlinePlan(interval)

    private fun getFreshCachedKlines(cacheKey: String): List<List<Any>>? {
        val cached = klineCache[cacheKey] ?: return null
        val ageMs = System.currentTimeMillis() - cached.createdAtMs
        return cached.rows.takeIf { ageMs <= KLINE_CACHE_TTL_MS && it.isNotEmpty() }
    }

    private fun chartPageCountForInterval(interval: String): Int = Timeframes.chartPageCount(interval)

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
        // la API de Bitunix topa en 200 velas: 3 paginas = 600, suficiente y sin castigar el rate
        const val BITUNIX_KLINE_PAGE_SIZE = 200
        const val BITUNIX_KLINE_PAGES = 3
        const val BINGX_KLINE_LIMIT = 1000
        const val BINGX_OPEN_TTL_MS = 300_000L
        const val KLINE_CACHE_MAX_ENTRIES = 24
        const val SPARK_CACHE_MAX_ENTRIES = 60
        const val SPARK_CACHE_TTL_MS = 300_000L
        const val KLINE_CACHE_TTL_MS = 30_000L
        // los sockets quedan abiertos 5s despues del ultimo colector (no se conectan al salir)
        const val LIVE_PRICE_STOP_TIMEOUT_MS = 5_000L
        const val TAIL_SYNC_LIMIT = 10
        // TFs con periodo taker nativo en Binance Futuros
        val TAKER_NATIVE_INTERVALS = Timeframes.TAKER_NATIVE_KEYS
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

