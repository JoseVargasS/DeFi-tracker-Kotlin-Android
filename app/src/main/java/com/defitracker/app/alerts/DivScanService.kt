package com.defitracker.app.alerts

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.defitracker.app.core.Brokers
import com.defitracker.app.data.local.DivAlertDao
import com.defitracker.app.data.local.DivAlertEntity
import com.defitracker.app.data.local.TrackedPairDao
import com.defitracker.app.data.repository.CryptoRepositoryImpl
import com.defitracker.app.presentation.crypto_detail.CandleData
import com.defitracker.app.presentation.crypto_detail.RSI_DIV_EARLY_LOOKBACK
import com.defitracker.app.presentation.crypto_detail.RsiDiv
import com.defitracker.app.presentation.crypto_detail.calculateRsi
import com.defitracker.app.presentation.crypto_detail.candleDurationMs
import com.defitracker.app.presentation.crypto_detail.detectFvgTap
import com.defitracker.app.presentation.crypto_detail.detectMaWickRejection
import com.defitracker.app.presentation.crypto_detail.detectRsiDivergences
import com.defitracker.app.presentation.crypto_detail.detectSweepReclaim
import com.defitracker.app.presentation.crypto_detail.forSourceInterval
import com.defitracker.app.presentation.crypto_detail.formatPriceForChart
import com.defitracker.app.presentation.crypto_detail.intervalForSource
import com.defitracker.app.presentation.crypto_detail.toCandleList
import com.defitracker.app.presentation.crypto_detail.toDoubleValue
import com.defitracker.app.presentation.crypto_detail.withLiveCandlePrice
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

// foreground casi-instantaneo, barrido secuencial con pausas pa' no comer rate-limit MEXC
@AndroidEntryPoint
class DivScanService : Service() {

    @Inject lateinit var repository: CryptoRepositoryImpl
    @Inject lateinit var trackedPairDao: TrackedPairDao
    @Inject lateinit var divAlertDao: DivAlertDao
    @Inject lateinit var priceAlertDao: com.defitracker.app.data.local.PriceAlertDao
    @Inject lateinit var monitorPrefs: DivMonitorPrefs
    @Inject lateinit var notifier: DivAlertNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
        // el canal de precio ya queda con tu tono antes del primer hiteo
        scope.launch {
            try {
                notifier.ensurePriceChannel(monitorPrefs.current().priceSoundUri)
            } catch (_: Exception) {}
        }
        startForeground(DivAlertNotifier.SERVICE_NOTI_ID, notifier.serviceNotification())
        scope.launch { scanLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun scanLoop() {
        while (scope.isActive) {
            try {
                val config = monitorPrefs.current()
                if (config.enabled) {
                    scanOnce(config)
                    checkPriceAlerts()
                    // poda notis de mas de 30 dias
                    try {
                        divAlertDao.pruneOlderThan(System.currentTimeMillis() - PRUNE_MS)
                    } catch (_: Exception) {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Div scan cycle failed", e)
            }
            delay(SCAN_INTERVAL_MS)
        }
    }

    private suspend fun scanOnce(config: DivMonitorConfig) {
        val intervals = config.intervals.toList()
        if (intervals.isEmpty()) return
        val pairs = try {
            trackedPairDao.getAllTrackedPairs().first()
                .filter { it.source in SCANNED_SOURCES }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { return }
        if (pairs.isEmpty()) return

        for (pair in pairs) {
            if (!scope.isActive) return
            // Bitunix no publica la vela en curso: el precio REST la llena igual que el socket la
            // llena en el chart, para que el escaner vea lo mismo que la pantalla.
            val livePrice = if (pair.source == Brokers.BITUNIX) pairLivePrice(pair.symbol, pair.source) else 0.0
            // divis frescas por TF para la confluencia multi-TF
            val fresh = ArrayList<Triple<String, RsiDiv, Long>>()
            for (interval in intervals) {
                if (!scope.isActive) return
                try {
                    // el repo mapea el TF por fuente; el scanner usa el mismo mapeo que el chart
                    val rows = repository.getKlines(
                        pair.symbol,
                        intervalForSource(pair.source, interval),
                        pair.source,
                        forceRefresh = true
                    )
                    if (rows.size < MIN_CANDLES) {
                        delay(pairDelayMs(pair.source))
                        continue
                    }
                    val candles = rows.toCandleList()
                        .forSourceInterval(pair.source, interval)
                        .withLiveCandlePrice(interval, livePrice)
                    if (candles.size < MIN_CANDLES) {
                        delay(pairDelayMs(pair.source))
                        continue
                    }
                    val rsi = calculateRsi(candles)
                    if (rsi.size != candles.size) {
                        delay(pairDelayMs(pair.source))
                        continue
                    }
                    // temprana (lookback 2) + confirmada (lookback 5) sobre las mismas velas
                    val earlyDivs = detectRsiDivergences(candles, rsi, RSI_DIV_EARLY_LOOKBACK)
                    val confDivs = detectRsiDivergences(candles, rsi)
                    processDivs(pair.symbol, pair.source, interval, candles, earlyDivs, pre = true)
                    processDivs(pair.symbol, pair.source, interval, candles, confDivs, pre = false)
                    // la mas reciente fresca de este TF cuenta para confluencia
                    (earlyDivs + confDivs).maxByOrNull { it.idx2 }?.let { d ->
                        val recency = candles.size - 1 - d.idx2
                        if (recency in 0..FRESH_CANDLES && d.idx2 in candles.indices) {
                            fresh.add(Triple(interval, d, candles[d.idx2].time))
                        }
                    }
                    // señales de entrada sobre las velas ya traidas, sin red extra
                    if (candles.size >= MIN_SIGNAL_CANDLES) {
                        processSignals(pair.symbol, pair.source, interval, candles, config.signals)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Scan failed ${pair.symbol}/${pair.source}/$interval", e)
                }
                // secuencial con pausa, no paralelizar terceros
                delay(pairDelayMs(pair.source))
            }
            processConfluence(pair.symbol, pair.source, fresh, config.confluence)
        }
    }

    // precio actual del par: solo Bitunix lo necesita (las otras fuentes ya traen la vela viva)
    private suspend fun pairLivePrice(symbol: String, source: String): Double = try {
        repository.getPairDetail(symbol, source).price.toDoubleValue()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        0.0
    }

    // alertas de precio LONG/SHORT: secuencial con pausa, renotifica cada 5 min.
    // Mismo id de noti por alerta: si la anterior sigue en la barra solo se actualiza.
    private suspend fun checkPriceAlerts() {
        val alerts = try {
            priceAlertDao.getEnabled()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { return }
        if (alerts.isEmpty()) return
        val soundUri = try { monitorPrefs.current().priceSoundUri } catch (_: Exception) { "" }
        val now = System.currentTimeMillis()
        for (a in alerts) {
            if (!scope.isActive) return
            if (now - a.lastNotifiedAt < PRICE_RENOTIFY_MS) continue
            try {
                val detail = repository.getPairDetail(a.symbol, a.source)
                val price = detail.price.toDoubleValue()
                if (price <= 0.0) continue
                // hiteo exacto, sin banda de tolerancia: en SUI 0.01 ya es bastante.
                // LONG = el precio bajo hasta tu objetivo, SHORT = subio hasta el
                val dir = a.direction.uppercase()
                val hit = if (a.low > 0 && a.high > 0 && a.high > a.low) {
                    price in a.low..a.high
                } else if (a.targetPrice > 0) {
                    if (dir == "SHORT") price >= a.targetPrice else price <= a.targetPrice
                } else false
                if (hit) {
                    val targetTxt = if (a.low > 0 && a.high > 0 && a.high > a.low) {
                        "${formatPriceForChart(a.low)}–${formatPriceForChart(a.high)}"
                    } else {
                        formatPriceForChart(a.targetPrice)
                    }
                    notifier.notifyPriceAlert(a.symbol, a.source, a.direction, price, targetTxt, a.id.hashCode(), soundUri)
                    try { priceAlertDao.markNotified(a.id, now) } catch (_: Exception) {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Price alert check failed ${a.symbol}", e)
            }
            delay(PRICE_ALERT_DELAY_MS)
        }
    }

    // la mas reciente de la tanda; la pre crea la fila y la confirmada la actualiza a Bull/Bear
    private suspend fun processDivs(
        symbol: String,
        source: String,
        interval: String,
        candles: List<CandleData>,
        divs: List<RsiDiv>,
        pre: Boolean
    ) {
        val last = divs.maxByOrNull { it.idx2 } ?: return
        val recency = candles.size - 1 - last.idx2
        if (recency < 0 || recency > FRESH_CANDLES) return
        val candleTime = candles[last.idx2].time
        val displaySymbol = Brokers.displaySymbol(symbol)
        val tag = (if (pre) "Pre-" else "") + (if (last.bullish) "Bull" else "Bear")
        val message = "¡$tag en $interval en $displaySymbol!"
        val baseId = divAlertId(source, symbol, interval, last.kind.name, candleTime)
        if (pre) {
            if (divAlertDao.getById(baseId) != null) return
            if (findNearby(symbol, source, interval, last.kind.name, candleTime, "CONFIRMED") != null) return
            divAlertDao.insert(
                DivAlertEntity(
                    id = baseId,
                    symbol = symbol,
                    source = source,
                    interval = interval,
                    kind = last.kind.name,
                    bullish = last.bullish,
                    message = message,
                    candleTime = candleTime,
                    status = "PRE"
                )
            )
            notifier.notifyDivergence(symbol, source, interval, last.bullish, kindLabelEs(last.kind.name), baseId.hashCode(), tag, pre = true)
            return
        }
        val existing = divAlertDao.getById(baseId)
        if (existing != null) {
            if (existing.status == "CONFIRMED") return
            divAlertDao.upgradeToConfirmed(existing.id, message, candleTime, System.currentTimeMillis())
            notifier.notifyDivergence(symbol, source, interval, last.bullish, kindLabelEs(last.kind.name), existing.id.hashCode(), tag)
            return
        }
        findNearby(symbol, source, interval, last.kind.name, candleTime, "PRE")?.let { match ->
            divAlertDao.upgradeToConfirmed(match.id, message, candleTime, System.currentTimeMillis())
            notifier.notifyDivergence(symbol, source, interval, last.bullish, kindLabelEs(last.kind.name), match.id.hashCode(), tag)
            return
        }
        divAlertDao.insert(
            DivAlertEntity(
                id = baseId,
                symbol = symbol,
                source = source,
                interval = interval,
                kind = last.kind.name,
                bullish = last.bullish,
                message = message,
                candleTime = candleTime,
                status = "CONFIRMED"
            )
        )
        notifier.notifyDivergence(symbol, source, interval, last.bullish, kindLabelEs(last.kind.name), baseId.hashCode(), tag)
    }

    // doble confirmacion: misma direccion fresca en 2+ TFs
    private suspend fun processConfluence(
        symbol: String,
        source: String,
        fresh: List<Triple<String, RsiDiv, Long>>,
        enabled: Boolean
    ) {
        if (!enabled || fresh.isEmpty()) return
        val now = System.currentTimeMillis()
        val displaySymbol = Brokers.displaySymbol(symbol)
        listOf(true, false).forEach { bull ->
            val tfs = fresh.filter { it.second.bullish == bull }.map { it.first }.distinct()
            if (tfs.size < 2) return@forEach
            val kind = if (bull) "CONF_BULL" else "CONF_BEAR"
            try {
                if (divAlertDao.countSince(symbol, source, kind, now - CONF_COOLDOWN_MS) > 0) return@forEach
            } catch (_: Exception) { return@forEach }
            val best = tfs.maxByOrNull { candleDurationMs(it) } ?: tfs.first()
            val candleTime = fresh.filter { it.second.bullish == bull }.maxOfOrNull { it.third } ?: now
            val id = confluenceAlertId(source, symbol, bull, candleTime)
            try {
                if (divAlertDao.getById(id) != null) return@forEach
                val dir = if (bull) "alcista" else "bajista"
                val tfLabel = tfs.sortedBy { candleDurationMs(it) }.joinToString("+")
                val message = "¡🔥 Doble confirmación $dir $tfLabel en $displaySymbol!"
                divAlertDao.insert(
                    DivAlertEntity(
                        id = id,
                        symbol = symbol,
                        source = source,
                        interval = best,
                        kind = kind,
                        bullish = bull,
                        message = message,
                        candleTime = candleTime,
                        status = "CONFIRMED"
                    )
                )
                notifier.notifySignal(
                    symbol, source, best, bull, message,
                    "Doble confirmación $dir · $tfLabel · Toca para ver la gráfica",
                    id.hashCode()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Confluence failed $symbol", e)
            }
        }
    }

    // gatillos de entrada sobre las velas ya traidas, con cooldown 4h por señal
    private suspend fun processSignals(
        symbol: String,
        source: String,
        interval: String,
        candles: List<CandleData>,
        enabled: Set<String>
    ) {
        if (enabled.isEmpty()) return
        val now = System.currentTimeMillis()
        val displaySymbol = Brokers.displaySymbol(symbol)
        val found = ArrayList<Triple<String, Boolean, String>>()
        if ("MA_REJECT" in enabled) {
            try {
                detectMaWickRejection(candles)?.let { s ->
                    found.add(Triple("MA_REJECT_${if (s.bullish) "BULL" else "BEAR"}", s.bullish, "Mechazo ${if (s.bullish) "alcista" else "bajista"} en ${s.label}"))
                }
            } catch (_: Exception) {}
        }
        if ("FVG_TAP" in enabled) {
            try {
                detectFvgTap(candles)?.let { s ->
                    found.add(Triple("FVG_TAP_${if (s.bullish) "BULL" else "BEAR"}", s.bullish, "Toque en FVG ${if (s.bullish) "alcista" else "bajista"}"))
                }
            } catch (_: Exception) {}
        }
        if ("SWEEP" in enabled) {
            try {
                detectSweepReclaim(candles)?.let { s ->
                    found.add(Triple("SWEEP_${if (s.bullish) "BULL" else "BEAR"}", s.bullish, "Barrido de ${if (s.bullish) "mínimos" else "máximos"} + reclaim"))
                }
            } catch (_: Exception) {}
        }
        for ((kind, bull, title) in found) {
            try {
                if (divAlertDao.countSince(symbol, source, kind, now - SIGNAL_COOLDOWN_MS) > 0) continue
                val candleTime = candles[candles.size - 2].time
                val id = divAlertId(source, symbol, interval, kind, candleTime)
                if (divAlertDao.getById(id) != null) continue
                val message = "¡$title · $interval en $displaySymbol!"
                divAlertDao.insert(
                    DivAlertEntity(
                        id = id,
                        symbol = symbol,
                        source = source,
                        interval = interval,
                        kind = kind,
                        bullish = bull,
                        message = message,
                        candleTime = candleTime,
                        status = "CONFIRMED"
                    )
                )
                notifier.notifySignal(
                    symbol, source, interval, bull, message,
                    "$title · $interval · Toca para ver la gráfica",
                    id.hashCode()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    // pres/confirmadas del mismo evento con el pivote hasta 2 velas corrido
    private suspend fun findNearby(symbol: String, source: String, interval: String, kind: String, candleTime: Long, status: String): DivAlertEntity? {
        val step = candleDurationMs(interval)
        if (step <= 0L) return null
        val span = 2 * step
        return divAlertDao.findNearby(symbol, source, interval, kind, candleTime - span, candleTime + span, status)
    }

    private fun kindLabelEs(kind: String): String = when (kind) {
        "REG_BULL" -> "Alcista regular"
        "HID_BULL" -> "Alcista oculta"
        "REG_BEAR" -> "Bajista regular"
        "HID_BEAR" -> "Bajista oculta"
        else -> kind
    }

    companion object {
        private const val TAG = "DivScanService"
        private const val SCAN_INTERVAL_MS = 60_000L
        private const val PAIR_TF_DELAY_MS = 400L
        // MEXC es la fuente con el limite mas ajustado: se separa un poco mas.
        private const val MEXC_PAIR_TF_DELAY_MS = 500L
        // los 4 brokers se escanean igual; TV no (no es broker y su socket es carisimo)
        private val SCANNED_SOURCES = setOf(
            Brokers.BINANCE,
            Brokers.MEXC,
            Brokers.BITUNIX,
            Brokers.BINGX
        )
        private const val MIN_CANDLES = 60
        // solo avisa si el pivote cerro hace <= 3 velas, si no es historia vieja
        private const val FRESH_CANDLES = 3
        private const val PRUNE_MS = 30L * 24 * 60 * 60 * 1000
        private const val MIN_SIGNAL_CANDLES = 55
        private const val SIGNAL_COOLDOWN_MS = 4L * 60 * 60 * 1000
        private const val CONF_COOLDOWN_MS = 24L * 60 * 60 * 1000
        // precio: retoca cada 5 min si ya quitaste la noti de la barra
        private const val PRICE_RENOTIFY_MS = 5L * 60 * 1000
        private const val PRICE_ALERT_DELAY_MS = 800L

        // pausa entre par y TF: mas larga en MEXC, que es la que se respeta primero
        private fun pairDelayMs(source: String): Long =
            if (source == Brokers.MEXC) MEXC_PAIR_TF_DELAY_MS else PAIR_TF_DELAY_MS

        fun start(context: Context) {
            val intent = Intent(context, DivScanService::class.java)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not start div scan service", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, DivScanService::class.java))
            } catch (_: Exception) {}
        }
    }
}
