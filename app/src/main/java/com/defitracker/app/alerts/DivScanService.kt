package com.defitracker.app.alerts

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.defitracker.app.data.local.DivAlertDao
import com.defitracker.app.data.local.DivAlertEntity
import com.defitracker.app.data.local.TrackedPairDao
import com.defitracker.app.domain.repository.CryptoRepository
import com.defitracker.app.presentation.crypto_detail.CandleData
import com.defitracker.app.presentation.crypto_detail.RSI_DIV_EARLY_LOOKBACK
import com.defitracker.app.presentation.crypto_detail.RsiDiv
import com.defitracker.app.presentation.crypto_detail.calculateRsi
import com.defitracker.app.presentation.crypto_detail.detectFvgTap
import com.defitracker.app.presentation.crypto_detail.detectMaWickRejection
import com.defitracker.app.presentation.crypto_detail.detectRsiDivergences
import com.defitracker.app.presentation.crypto_detail.detectSweepReclaim
import com.defitracker.app.presentation.crypto_detail.toCandleList
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

    @Inject lateinit var repository: CryptoRepository
    @Inject lateinit var trackedPairDao: TrackedPairDao
    @Inject lateinit var divAlertDao: DivAlertDao
    @Inject lateinit var monitorPrefs: DivMonitorPrefs
    @Inject lateinit var notifier: DivAlertNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
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
                .filter { it.source == "MEXC" }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { return }
        if (pairs.isEmpty()) return

        for (pair in pairs) {
            if (!scope.isActive) return
            // divis frescas por TF para la confluencia multi-TF
            val fresh = ArrayList<Triple<String, RsiDiv, Long>>()
            for (interval in intervals) {
                if (!scope.isActive) return
                try {
                    // MEXC mapea el TF dentro del repo, force fresca pa' detectar al toque
                    val rows = repository.getKlines(pair.symbol, interval, "MEXC", forceRefresh = true)
                    if (rows.size < MIN_CANDLES) {
                        delay(PAIR_TF_DELAY_MS)
                        continue
                    }
                    val candles = rows.toCandleList()
                    if (candles.size < MIN_CANDLES) {
                        delay(PAIR_TF_DELAY_MS)
                        continue
                    }
                    val rsi = calculateRsi(candles)
                    if (rsi.size != candles.size) {
                        delay(PAIR_TF_DELAY_MS)
                        continue
                    }
                    // temprana (lookback 2) + confirmada (lookback 5) sobre las mismas velas
                    val earlyDivs = detectRsiDivergences(candles, rsi, RSI_DIV_EARLY_LOOKBACK)
                    val confDivs = detectRsiDivergences(candles, rsi)
                    processDivs(pair.symbol, interval, candles, earlyDivs, pre = true)
                    processDivs(pair.symbol, interval, candles, confDivs, pre = false)
                    // la mas reciente fresca de este TF cuenta para confluencia
                    (earlyDivs + confDivs).maxByOrNull { it.idx2 }?.let { d ->
                        val recency = candles.size - 1 - d.idx2
                        if (recency in 0..FRESH_CANDLES && d.idx2 in candles.indices) {
                            fresh.add(Triple(interval, d, candles[d.idx2].time))
                        }
                    }
                    // señales de entrada sobre las velas ya traidas, sin red extra
                    if (candles.size >= MIN_SIGNAL_CANDLES) {
                        processSignals(pair.symbol, interval, candles, config.signals)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Scan failed ${pair.symbol}/$interval", e)
                }
                // secuencial con pausa, no paralelizar terceros
                delay(PAIR_TF_DELAY_MS)
            }
            processConfluence(pair.symbol, fresh, config.confluence)
        }
    }

    // la mas reciente de la tanda; la pre crea la fila y la confirmada la actualiza a Bull/Bear
    private suspend fun processDivs(
        symbol: String,
        interval: String,
        candles: List<CandleData>,
        divs: List<RsiDiv>,
        pre: Boolean
    ) {
        val last = divs.maxByOrNull { it.idx2 } ?: return
        val recency = candles.size - 1 - last.idx2
        if (recency < 0 || recency > FRESH_CANDLES) return
        val candleTime = candles[last.idx2].time
        val displaySymbol = symbol.replace("_", "")
        val tag = (if (pre) "Pre-" else "") + (if (last.bullish) "Bull" else "Bear")
        val message = "¡$tag en $interval en $displaySymbol!"
        val baseId = "$symbol|$interval|${last.kind}|$candleTime"
        if (pre) {
            if (divAlertDao.getById(baseId) != null) return
            if (findNearby(symbol, interval, last.kind.name, candleTime, "CONFIRMED") != null) return
            divAlertDao.insert(
                DivAlertEntity(
                    id = baseId,
                    symbol = symbol,
                    source = "MEXC",
                    interval = interval,
                    kind = last.kind.name,
                    bullish = last.bullish,
                    message = message,
                    candleTime = candleTime,
                    status = "PRE"
                )
            )
            notifier.notifyDivergence(symbol, "MEXC", interval, last.bullish, kindLabelEs(last.kind.name), baseId.hashCode(), tag, pre = true)
            return
        }
        val existing = divAlertDao.getById(baseId)
        if (existing != null) {
            if (existing.status == "CONFIRMED") return
            divAlertDao.upgradeToConfirmed(existing.id, message, candleTime, System.currentTimeMillis())
            notifier.notifyDivergence(symbol, "MEXC", interval, last.bullish, kindLabelEs(last.kind.name), existing.id.hashCode(), tag)
            return
        }
        findNearby(symbol, interval, last.kind.name, candleTime, "PRE")?.let { match ->
            divAlertDao.upgradeToConfirmed(match.id, message, candleTime, System.currentTimeMillis())
            notifier.notifyDivergence(symbol, "MEXC", interval, last.bullish, kindLabelEs(last.kind.name), match.id.hashCode(), tag)
            return
        }
        divAlertDao.insert(
            DivAlertEntity(
                id = baseId,
                symbol = symbol,
                source = "MEXC",
                interval = interval,
                kind = last.kind.name,
                bullish = last.bullish,
                message = message,
                candleTime = candleTime,
                status = "CONFIRMED"
            )
        )
        notifier.notifyDivergence(symbol, "MEXC", interval, last.bullish, kindLabelEs(last.kind.name), baseId.hashCode(), tag)
    }

    // doble confirmacion: misma direccion fresca en 2+ TFs
    private suspend fun processConfluence(
        symbol: String,
        fresh: List<Triple<String, RsiDiv, Long>>,
        enabled: Boolean
    ) {
        if (!enabled || fresh.isEmpty()) return
        val now = System.currentTimeMillis()
        val displaySymbol = symbol.replace("_", "")
        listOf(true, false).forEach { bull ->
            val tfs = fresh.filter { it.second.bullish == bull }.map { it.first }.distinct()
            if (tfs.size < 2) return@forEach
            val kind = if (bull) "CONF_BULL" else "CONF_BEAR"
            try {
                if (divAlertDao.countSince(symbol, kind, now - CONF_COOLDOWN_MS) > 0) return@forEach
            } catch (_: Exception) { return@forEach }
            val best = tfs.maxByOrNull { tfDurationMs(it) } ?: tfs.first()
            val candleTime = fresh.filter { it.second.bullish == bull }.maxOfOrNull { it.third } ?: now
            val id = "$symbol|CONF|${if (bull) "BULL" else "BEAR"}|$candleTime"
            try {
                if (divAlertDao.getById(id) != null) return@forEach
                val dir = if (bull) "alcista" else "bajista"
                val tfLabel = tfs.sortedBy { tfDurationMs(it) }.joinToString("+")
                val message = "¡🔥 Doble confirmación $dir $tfLabel en $displaySymbol!"
                divAlertDao.insert(
                    DivAlertEntity(
                        id = id,
                        symbol = symbol,
                        source = "MEXC",
                        interval = best,
                        kind = kind,
                        bullish = bull,
                        message = message,
                        candleTime = candleTime,
                        status = "CONFIRMED"
                    )
                )
                notifier.notifySignal(
                    symbol, "MEXC", best, bull, message,
                    "Doble confirmación $dir · $tfLabel · MEXC Futuros · Toca para ver la gráfica",
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
        interval: String,
        candles: List<CandleData>,
        enabled: Set<String>
    ) {
        if (enabled.isEmpty()) return
        val now = System.currentTimeMillis()
        val displaySymbol = symbol.replace("_", "")
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
                if (divAlertDao.countSince(symbol, kind, now - SIGNAL_COOLDOWN_MS) > 0) continue
                val candleTime = candles[candles.size - 2].time
                val id = "$symbol|$interval|$kind|$candleTime"
                if (divAlertDao.getById(id) != null) continue
                val message = "¡$title · $interval en $displaySymbol!"
                divAlertDao.insert(
                    DivAlertEntity(
                        id = id,
                        symbol = symbol,
                        source = "MEXC",
                        interval = interval,
                        kind = kind,
                        bullish = bull,
                        message = message,
                        candleTime = candleTime,
                        status = "CONFIRMED"
                    )
                )
                notifier.notifySignal(
                    symbol, "MEXC", interval, bull, message,
                    "$title · $interval · MEXC Futuros · Toca para ver la gráfica",
                    id.hashCode()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    // pres/confirmadas del mismo evento con el pivote hasta 2 velas corrido
    private suspend fun findNearby(symbol: String, interval: String, kind: String, candleTime: Long, status: String): DivAlertEntity? {
        val step = tfDurationMs(interval)
        if (step <= 0L) return null
        val span = 2 * step
        return divAlertDao.findNearby(symbol, interval, kind, candleTime - span, candleTime + span, status)
    }

    private fun tfDurationMs(interval: String): Long = when (interval.trim()) {
        "1m" -> 60_000L
        "5m" -> 300_000L
        "15m" -> 900_000L
        "30m" -> 1_800_000L
        "1h" -> 3_600_000L
        "2h" -> 7_200_000L
        "4h" -> 14_400_000L
        "6h" -> 21_600_000L
        "12h" -> 43_200_000L
        "1d" -> 86_400_000L
        "3d" -> 259_200_000L
        "5d" -> 432_000_000L
        "1w" -> 604_800_000L
        "2w" -> 1_209_600_000L
        "1mo" -> 2_592_000_000L
        else -> 0L
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
        private const val MIN_CANDLES = 60
        // solo avisa si el pivote cerro hace <= 3 velas, si no es historia vieja
        private const val FRESH_CANDLES = 3
        private const val PRUNE_MS = 30L * 24 * 60 * 60 * 1000
        private const val MIN_SIGNAL_CANDLES = 55
        private const val SIGNAL_COOLDOWN_MS = 4L * 60 * 60 * 1000
        private const val CONF_COOLDOWN_MS = 24L * 60 * 60 * 1000

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
