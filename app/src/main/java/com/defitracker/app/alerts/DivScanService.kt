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
import com.defitracker.app.presentation.crypto_detail.detectRsiDivergences
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
                    scanOnce(config.intervals.toList())
                    // poda notis de mas de 7 dias
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

    private suspend fun scanOnce(intervals: List<String>) {
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
                    processDivs(pair.symbol, interval, candles, detectRsiDivergences(candles, rsi, RSI_DIV_EARLY_LOOKBACK), pre = true)
                    processDivs(pair.symbol, interval, candles, detectRsiDivergences(candles, rsi), pre = false)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Scan failed ${pair.symbol}/$interval", e)
                }
                // secuencial con pausa, no paralelizar terceros
                delay(PAIR_TF_DELAY_MS)
            }
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
        private const val PRUNE_MS = 7L * 24 * 60 * 60 * 1000

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
