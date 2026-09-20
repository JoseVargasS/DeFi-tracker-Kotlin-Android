package com.defitracker.app.presentation.crypto_detail

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color as GraphicsColor
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import com.defitracker.app.R
import com.defitracker.app.ui.theme.Geist
import com.defitracker.app.ui.theme.InputBg
import com.defitracker.app.ui.theme.LineDiv
import com.defitracker.app.ui.theme.SheetBg
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight

import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.renderer.XAxisRenderer
import com.github.mikephil.charting.utils.MPPointF
import com.github.mikephil.charting.utils.Transformer
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.utils.ViewPortHandler
import com.github.mikephil.charting.components.MarkerView
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoDetailScreen(
    onBack: () -> Unit,
    viewModel: CryptoDetailViewModel = hiltViewModel()
) {
    val state = viewModel.state.value
    val prefs = viewModel.prefs.value
    val showIndicators = remember { mutableStateOf(false) }
    val showMaList = remember { mutableStateOf(false) }
    // modo expandir, el chart manda y los TFs bajan
    val chartExpanded = remember { mutableStateOf(false) }
    val tradingPair = splitTradingPair(state.detail?.symbol ?: state.symbol, state.source)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tradingPair.displayName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            fontFamily = Geist
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // badge de fuente siempre visible
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (state.source == "MEXC") Color(0xFF1ECB81).copy(alpha = 0.2f)
                                    else Color(0xFF2196F3).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sourceLabel(state.source),
                                color = if (state.source == "MEXC") Color(0xFF1ECB81) else Color(0xFF64B5F6),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF000000),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF000000)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF000000))
        ) {
            // Stats Header (compacto en expandir, el chart manda)
            state.detail?.let { detail ->
                val detailPair = splitTradingPair(detail.symbol, state.source)
                if (chartExpanded.value) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF000000))
                            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = detail.price,
                            color = if (detail.isPositive) Color(0xFF1ECB81) else Color(0xFFF6465D),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Geist
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${if (detail.isPositive) "+" else ""}${detail.priceChangePercent}%",
                            color = if (detail.isPositive) Color(0xFF1ECB81) else Color(0xFFF6465D),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Geist
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "H ${formatDecimal(detail.highPrice)}  L ${formatDecimal(detail.lowPrice)}",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = Geist
                        )
                    }
                } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text(text = "Last price", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = detail.price,
                            color = if (detail.isPositive) Color(0xFF1ECB81) else Color(0xFFF6465D),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Geist
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = detailPair.displayName, color = Color.Gray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${if (detail.isPositive) "+" else ""}${detail.priceChangePercent}%",
                                color = if (detail.isPositive) Color(0xFF1ECB81) else Color(0xFFF6465D),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = Geist
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        StatRow("24h high", formatDecimal(detail.highPrice))
                        StatRow("24h low", formatDecimal(detail.lowPrice))
                        StatRow("24h vol (${detailPair.baseAsset})", formatVol(detail.volume))
                        StatRow("24h turnover (${detailPair.quoteAsset})", formatVol(detail.quoteVolume))
                    }
                }
                }
            }

            // Interval Selector arriba solo en normal; en expandir baja del chart
            if (!chartExpanded.value) {
                IntervalRow(
                    selected = state.selectedInterval,
                    onPick = { viewModel.loadChartData(it) }
                )
            }

            Spacer(modifier = Modifier.height(if (chartExpanded.value) 2.dp else 4.dp))

            // Stacked Charts Area (subs compactos, el protagonista es el chart)
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val areaHpx = with(LocalDensity.current) { maxHeight.toPx() }
            Column(modifier = Modifier.fillMaxSize()) {
                when {
                    state.candles.isNotEmpty() -> {
                        // Shared chart references for sync
                        val priceChartRef = remember { mutableStateOf<CombinedChart?>(null) }
                        val stochChartRef = remember { mutableStateOf<LineChart?>(null) }
                        val rsiChartRef = remember { mutableStateOf<LineChart?>(null) }
                        // altos arrastrables, subs mas altos por defecto
                        var priceW by remember { mutableFloatStateOf(if (chartExpanded.value) 4f else 2.5f) }
                        var stochW by remember { mutableFloatStateOf(if (chartExpanded.value) 0.7f else 1f) }
                        var rsiW by remember { mutableFloatStateOf(if (chartExpanded.value) 0.7f else 1f) }
                        LaunchedEffect(chartExpanded.value) {
                            priceW = if (chartExpanded.value) 4f else 2.5f
                            stochW = if (chartExpanded.value) 0.7f else 1f
                            rsiW = stochW
                        }

                        Box(modifier = Modifier.weight(priceW)) {
                            PriceChart(
                                state = state,
                                prefs = prefs,
                                viewModel = viewModel,
                                chartExpanded = chartExpanded,
                                onOpenIndicators = { showIndicators.value = true },
                                priceChartRef = priceChartRef,
                                stochChartRef = stochChartRef,
                                rsiChartRef = rsiChartRef
                            )
                        }
                        if (prefs.stochVisible || prefs.rsiVisible) {
                            // divisor precio/subs
                            ChartResizeDivider { dy ->
                                var d = dy / areaHpx.coerceAtLeast(1f) * (priceW + stochW + rsiW)
                                val np = priceW + d
                                if (np < 1f) d = 1f - priceW
                                if (np > 6f) d = 6f - priceW
                                if (prefs.stochVisible) {
                                    var ns = stochW - d
                                    if (ns < 0.35f) { d = stochW - 0.35f; ns = 0.35f }
                                    if (ns > 3f) { d = stochW - 3f; ns = 3f }
                                    priceW += d
                                    stochW = ns
                                } else {
                                    var ns = rsiW - d
                                    if (ns < 0.35f) { d = rsiW - 0.35f; ns = 0.35f }
                                    if (ns > 3f) { d = rsiW - 3f; ns = 3f }
                                    priceW += d
                                    rsiW = ns
                                }
                            }
                        }
                        if (chartExpanded.value) {
                            IntervalRow(
                                selected = state.selectedInterval,
                                onPick = { viewModel.loadChartData(it) }
                            )
                        }
                        if (prefs.stochVisible) {
                            Box(modifier = Modifier.weight(stochW)) {
                                StochRSIChart(state, stochChartRef, priceChartRef)
                            }
                        }
                        if (prefs.stochVisible && prefs.rsiVisible) {
                            // divisor stoch/rsi
                            ChartResizeDivider { dy ->
                                var d = dy / areaHpx.coerceAtLeast(1f) * (stochW + rsiW)
                                val ns = stochW + d
                                if (ns < 0.35f) d = 0.35f - stochW
                                if (ns > 3f) d = 3f - stochW
                                var nr = rsiW - d
                                if (nr < 0.35f) { d = rsiW - 0.35f; nr = 0.35f }
                                if (nr > 3f) { d = rsiW - 3f; nr = 3f }
                                stochW += d
                                rsiW = nr
                            }
                        }
                        if (prefs.rsiVisible) {
                            Box(modifier = Modifier.weight(rsiW)) {
                                RsiChart(state, rsiChartRef, priceChartRef, prefs)
                            }
                        }
                    }
                    state.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF1ECB81))
                        }
                    }
                    state.error.isNotBlank() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(state.error, color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No chart data available", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }
            }
            }

            if (showIndicators.value) {
                ModalBottomSheet(
                    onDismissRequest = { showIndicators.value = false },
                    containerColor = SheetBg
                ) {
                    IndicatorsSheet(
                        prefs = prefs,
                        onToggleBB = { viewModel.toggleBB() },
                        onToggleProfile = { viewModel.toggleProfile() },
                        onToggleVolume = { viewModel.toggleVolumeSub() },
                        onToggleStoch = { viewModel.toggleStochSub() },
                        onToggleRsi = { viewModel.toggleRsiSub() },
                        onToggleRsiDiv = { viewModel.toggleRsiDiv() },
                        onToggleRsiDivHidden = { viewModel.toggleRsiDivHidden() },
                        // medias en su propio bottom sheet encima del de indicadores
                        onOpenMAs = { showMaList.value = true },
                        onToggleSmcStructure = { viewModel.toggleSmcStructure() },
                        onToggleSmcOB = { viewModel.toggleSmcOrderBlocks() },
                        onToggleSmcFvg = { viewModel.toggleSmcFvg() },
                        onToggleSmcPremium = { viewModel.toggleSmcPremium() },
                        onToggleSmcEqhl = { viewModel.toggleSmcEqhl() },
                        onToggleSmcLiq = { viewModel.toggleSmcLiquidity() }
                    )
                }
            }

            // lista de MAs en su propio sheet encima del de indicadores
            if (showMaList.value) {
                ModalBottomSheet(
                    onDismissRequest = { showMaList.value = false },
                    containerColor = SheetBg
                ) {
                    MaListSheet(
                        mas = prefs.mas,
                        onToggle = { id -> viewModel.toggleMAById(id) },
                        onAdd = { viewModel.addMA() },
                        onDelete = { id -> viewModel.deleteMA(id) },
                        onType = { id, t -> viewModel.setMAType(id, t) },
                        onPeriod = { id, p -> viewModel.setMAPeriod(id, p) },
                        onTimeframe = { id, tf -> viewModel.setMATimeframe(id, tf) },
                        onColor = { id, hex -> viewModel.setMAColorById(id, hex) },
                        onWidth = { id, w -> viewModel.setMAWidthById(id, w) }
                    )
                }
            }
        }
    }
}

// TFs arriba en normal, debajo del chart en expandir
@Composable
fun IntervalRow(selected: String, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // orden ascendente estilo OKX, cortos a la izq
        val intervals = listOf(
            "1m" to "1m",
            "5m" to "5m",
            "15m" to "15m",
            "30m" to "30m",
            "1h" to "1h",
            "2h" to "2h",
            "4h" to "4h",
            "6h" to "6h",
            "12h" to "12h",
            "1d" to "1d",
            "3d" to "3d",
            "5d" to "5d",
            "1w" to "1w",
            "2w" to "2w",
            "1mo" to "1mo"
        )
        intervals.forEach { interval ->
            val isSelected = selected == interval.second
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(28.dp)
                    .clickable { onPick(interval.second) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = interval.first,
                    color = if (isSelected) Color.White else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .width(20.dp)
                            .height(2.dp)
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontSize = 10.sp)
        Text(text = value, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

private data class TradingPairParts(
    val baseAsset: String,
    val quoteAsset: String
) {
    val displayName: String = if (quoteAsset.isBlank()) baseAsset else "$baseAsset/$quoteAsset"
}

private fun sourceLabel(source: String): String = when (source) {
    "MEXC" -> "MEXC · Futuros"
    else -> "Binance · Spot"
}

private fun splitTradingPair(symbol: String, source: String = "Binance"): TradingPairParts {
    if (symbol.isBlank()) return TradingPairParts("", "")

    // futuros MEXC usan BTC_USDT con guion bajo
    if (source == "MEXC" && "_" in symbol) {
        val base = symbol.substringBefore("_")
        val quote = symbol.substringAfter("_")
        return TradingPairParts(base, quote)
    }

    val quoteAsset = knownQuoteAssets.firstOrNull { symbol.endsWith(it) }.orEmpty()
    return if (quoteAsset.isNotBlank() && symbol.length > quoteAsset.length) {
        TradingPairParts(
            baseAsset = symbol.removeSuffix(quoteAsset),
            quoteAsset = quoteAsset
        )
    } else {
        TradingPairParts(symbol, "")
    }
}

private val knownQuoteAssets = listOf(
    "USDT",
    "FDUSD",
    "USDC",
    "TUSD",
    "BUSD",
    "BTC",
    "ETH",
    "BNB",
    "BRL",
    "EUR",
    "TRY",
    "DAI"
)

private fun formatDecimal(value: String): String {
    val d = value.toDoubleOrNull() ?: return value
    return formatPriceForChart(d)
}

private fun formatPriceForChart(value: Double): String {
    if (value == 0.0) return "0.00"
    if (abs(value) >= 1000.0) {
        return String.format(Locale.US, "%,.2f", value)
    }
    // hasta 8 decimales recortando ceros, pa' seguir precios chicos sin ruido
    var s = String.format(Locale.US, "%.8f", value)
    s = s.trimEnd('0').trimEnd('.')
    val frac = s.substringAfter('.', "")
    if (frac.length < 2) {
        s = String.format(Locale.US, "%.2f", value)
    }
    return s
}

// eje/tags/fibo con los mismos decimales del precio actual, sin colas largas
private fun formatAxisPrice(value: Double, refClose: Double): String {
    if (value == 0.0) return "0.00"
    if (abs(value) >= 1000.0) {
        return String.format(Locale.US, "%,.2f", value)
    }
    val n = formatPriceForChart(refClose).substringAfter('.', "").length.coerceIn(2, 8)
    return String.format(Locale.US, "%.${n}f", value)
}

private enum class ChartTouchMode {
    UNDECIDED,
    PAN,
    Y_ZOOM,
    PINCH_X_ZOOM
}

// un solo boton Dibujo, adentro se elige Medir, Fibo o dibujo OKX
enum class DrawingTool {
    NONE,
    MEASURE,
    FIBO,
    DRAW
}

// duracion de cada vela para el countdown al cierre
private fun intervalDurationMs(interval: String): Long = when (interval) {
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

// velas virtuales a la derecha para que el fibo pase la ultima vela
private const val FIB_VIRTUAL_EXTEND = 50

private fun fibStepMs(candles: List<CandleData>, interval: String): Long {
    if (candles.size >= 2) {
        val s = candles.last().time - candles[candles.size - 2].time
        if (s > 0L) return s
    }
    return intervalDurationMs(interval)
}

private fun fibIndexToTime(candles: List<CandleData>, idx: Int, interval: String): Long {
    if (candles.isEmpty()) return 0L
    if (idx < candles.size) return candles[idx.coerceIn(0, candles.size - 1)].time
    val step = fibStepMs(candles, interval)
    if (step <= 0L) return candles.last().time
    return candles.last().time + (idx - (candles.size - 1)) * step
}

// tiempos futuros mapean a indice virtual, el pasado usa timeToIndex
private fun fibTimeToIndexVirtual(candles: List<CandleData>, time: Long, interval: String): Int {
    if (candles.isEmpty()) return 0
    if (time <= candles.last().time) return timeToIndex(candles, time)
    val step = fibStepMs(candles, interval)
    if (step <= 0L) return candles.size - 1
    val k = ((time - candles.last().time + step - 1) / step).toInt()
    return candles.size - 1 + k.coerceIn(1, FIB_VIRTUAL_EXTEND)
}

private fun formatCandleCountdown(interval: String, candleTime: Long): String {
    val dur = intervalDurationMs(interval)
    if (dur <= 0L) return "--:--"
    val now = System.currentTimeMillis()
    // al cerrar la vela se rola al siguiente borde, nunca se clava en 00:00
    val elapsed = now - candleTime
    val remainingMs = if (elapsed < 0L) {
        candleTime + dur - now
    } else {
        dur - (elapsed % dur)
    }
    val totalSec = (remainingMs.coerceAtLeast(0L)) / 1000L
    return when {
        totalSec >= 86_400L -> String.format(
            Locale.US,
            "%dd %02d:%02d:%02d",
            totalSec / 86_400L,
            (totalSec % 86_400L) / 3600L,
            (totalSec % 3600L) / 60L,
            totalSec % 60L
        )
        totalSec >= 3600L -> String.format(
            Locale.US,
            "%02d:%02d:%02d",
            totalSec / 3600L,
            (totalSec % 3600L) / 60L,
            totalSec % 60L
        )
        else -> String.format(Locale.US, "%02d:%02d", totalSec / 60L, totalSec % 60L)
    }
}

private fun String.isCalendarInterval(): Boolean {
    return this in setOf("1d", "3d", "5d", "1w", "2w", "1mo")
}

private fun List<CandleData>.spansMultipleYears(): Boolean {
    val first = firstOrNull()?.time ?: return false
    val last = lastOrNull()?.time ?: return false
    return last - first >= 365L * 24L * 60L * 60L * 1000L
}

private fun CryptoDetailState.viewportKey(): String {
    val first = candles.firstOrNull()?.time ?: 0L
    val last = candles.lastOrNull()?.time ?: 0L
    return "$symbol-${candles.size}-$first-$last"
}

private fun formatVol(vol: String): String {
    val v = vol.toDoubleOrNull() ?: return vol
    return when {
        v >= 1_000_000 -> String.format(Locale.US, "%.2fM", v / 1_000_000)
        v >= 1_000 -> String.format(Locale.US, "%.2fK", v / 1_000)
        else -> String.format(Locale.US, "%.2f", v)
    }
}

private fun List<Pair<Long, Double>>.getValueAtCandleIndex(index: Int): Double? {
    val direct = getOrNull(index)
    if (direct != null && direct.first.toInt() == index) return direct.second
    return firstOrNull { it.first.toInt() == index }?.second
}

private data class MeasureZone(
    val startIdx: Int,
    val startPrice: Double,
    val endIdx: Int? = null,
    val endPrice: Double? = null
) {
    val isComplete: Boolean = endIdx != null && endPrice != null
}

// ─── PRICE CHART ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PriceChart(
    state: CryptoDetailState,
    prefs: IndicatorPrefs,
    viewModel: CryptoDetailViewModel,
    chartExpanded: MutableState<Boolean>,
    onOpenIndicators: () -> Unit,
    priceChartRef: MutableState<CombinedChart?>,
    stochChartRef: MutableState<LineChart?>,
    rsiChartRef: MutableState<LineChart?>
) {
    val stateRef = remember { mutableStateOf(state) }
    // onDraw del chart es closure de fabrica, lee prefs via ref o queda stale
    val prefsRef = remember { mutableStateOf(prefs) }
    val lastRenderedDataKey = remember { mutableStateOf<String?>(null) }
    // cambio de ajustes reconstruye datos sin resetear el zoom
    val lastPrefsKey = remember { mutableStateOf<String?>(null) }
    // pa' saber si es primera carga o cambio de TF (lo unico que recentra)
    val lastCandleCount = remember { mutableStateOf(0) }
    val lastCandleInterval = remember { mutableStateOf("") }
    val drawingTool = remember { mutableStateOf(DrawingTool.NONE) }
    val showDrawingSheet = remember { mutableStateOf(false) }
    val showFibLevels = remember { mutableStateOf(false) }
    val showDeleteAllDrawings = remember { mutableStateOf(false) }
    // pulso: analisis del momento en bottom sheet
    val showAnalysis = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (viewModel.openAnalysisInitially) showAnalysis.value = true
    }
    val rangeSelection = remember { mutableStateOf<MeasureZone?>(null) }
    val fibPendingStart = remember { mutableStateOf<FibAnchor?>(null) }
    val drawPendingStart = remember { mutableStateOf<FibAnchor?>(null) }
    // varios fibos, cada uno con su estilo; el seleccionado muestra su barrita
    val fibOverlays = viewModel.fibOverlays.value
    val selectedFibId = viewModel.selectedFibId.value
    val overlaysRef = remember { mutableStateOf<List<FibOverlay>>(emptyList()) }
    val selectedFibRef = remember { mutableStateOf<String?>(null) }
    // dibujos OKX por simbolo, mismo esquema que los fibos
    val drawOverlays = viewModel.drawOverlays.value
    val selectedDrawId = viewModel.selectedDrawId.value
    val drawsRef = remember { mutableStateOf<List<DrawOverlay>>(emptyList()) }
    val selectedDrawRef = remember { mutableStateOf<String?>(null) }
    val drawKindRef = remember { mutableStateOf<DrawKind?>(null) }
    val pendingDrawStartRef = remember { mutableStateOf<FibAnchor?>(null) }
    val magnetRef = remember { mutableStateOf(false) }
    val toolRef = remember { mutableStateOf(DrawingTool.NONE) }
    val pendingStartRef = remember { mutableStateOf<FibAnchor?>(null) }
    val currentViewportKey = state.viewportKey()

    LaunchedEffect(currentViewportKey) {
        rangeSelection.value = null
        // los fibos NO se borran, viven por tiempo y se remapean solos
        fibPendingStart.value = null
        pendingStartRef.value = null
        drawPendingStart.value = null
        pendingDrawStartRef.value = null
    }
    LaunchedEffect(fibOverlays) { overlaysRef.value = fibOverlays }
    LaunchedEffect(selectedFibId) { selectedFibRef.value = selectedFibId }
    LaunchedEffect(drawOverlays) { drawsRef.value = drawOverlays }
    LaunchedEffect(selectedDrawId) { selectedDrawRef.value = selectedDrawId }
    LaunchedEffect(drawingTool.value) { toolRef.value = drawingTool.value }
    LaunchedEffect(fibPendingStart.value) { pendingStartRef.value = fibPendingStart.value }
    LaunchedEffect(drawPendingStart.value) { pendingDrawStartRef.value = drawPendingStart.value }

    // retickea el countdown del precio actual cada segundo
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            priceChartRef.value?.invalidate()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            // Subclass CombinedChart to override onDraw for max/min labels
            object : CombinedChart(context) {
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.WHITE
                    textSize = 28f
                    textAlign = Paint.Align.LEFT
                }
                private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(220, 255, 255, 255)
                    strokeWidth = 1.5f
                    pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
                }
                private val profileUpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(92, 70, 222, 226)
                    style = Paint.Style.FILL
                }
                private val profileDownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(92, 255, 88, 142)
                    style = Paint.Style.FILL
                }
                private val profilePocPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(145, 255, 193, 7)
                    style = Paint.Style.FILL
                }
                private val profileBackdropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(105, 0, 0, 0)
                    style = Paint.Style.FILL
                }
                private val profileBoundaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(90, 173, 177, 184)
                    strokeWidth = 1f
                    style = Paint.Style.STROKE
                }
                private val profilePocLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(150, 255, 193, 7)
                    strokeWidth = 1.4f
                    pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
                }
                private val selectionFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(38, 30, 203, 129)
                    style = Paint.Style.FILL
                }
                private val selectionStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(230, 30, 203, 129)
                    strokeWidth = 2.2f
                    style = Paint.Style.STROKE
                }
                val selectionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.WHITE
                    textSize = 24f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val selectionLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(220, 26, 29, 35)
                    style = Paint.Style.FILL
                }
                // fibo estilo OKX, color/grosor editables desde la barra flotante
                private val fibLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.WHITE
                    strokeWidth = 2f
                    style = Paint.Style.STROKE
                }
                val fibLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.WHITE
                    textSize = 24f
                    textAlign = Paint.Align.LEFT
                    // sombra sutil para leer sin fondo que tape el grafico
                    setShadowLayer(4f, 0f, 0f, GraphicsColor.argb(200, 0, 0, 0))
                }
                private val fibChipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(220, 26, 29, 35)
                    style = Paint.Style.FILL
                }
                private val fibHandleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(60, 33, 150, 243)
                    style = Paint.Style.FILL
                }
                private val fibHandleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor("#2196F3")
                    strokeWidth = 3f
                    style = Paint.Style.STROKE
                }
                // dibujos OKX, color por overlay
                private val drawLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor("#FFD60A")
                    strokeWidth = 2f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                private val drawDashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor("#FFD60A")
                    strokeWidth = 2f
                    style = Paint.Style.STROKE
                    pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
                }
                private val drawLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.WHITE
                    textSize = 24f
                    textAlign = Paint.Align.LEFT
                }
                private val drawChipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(220, 26, 29, 35)
                    style = Paint.Style.FILL
                }
                // conector punteado + tag de MAs de otro TF (1h.SMA20)
                val maTagDashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.WHITE
                    strokeWidth = 2f
                    style = Paint.Style.STROKE
                    pathEffect = DashPathEffect(floatArrayOf(6f, 5f), 0f)
                }
                val maTagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.WHITE
                    textSize = 22f
                    textAlign = Paint.Align.LEFT
                }
                private val drawHandleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(60, 255, 214, 10)
                    style = Paint.Style.FILL
                }
                private val drawHandleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor("#FFD60A")
                    strokeWidth = 3f
                    style = Paint.Style.STROKE
                }
                private val drawTriPath = android.graphics.Path()
                // SMC tenue, las velas mandan (alfas bajos, labels chicos)
                val smcObBullPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(42, 38, 166, 154)
                    style = Paint.Style.FILL
                }
                val smcObBearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(42, 239, 83, 80)
                    style = Paint.Style.FILL
                }
                val smcObMitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(16, 173, 177, 184)
                    style = Paint.Style.FILL
                }
                val smcFvgBullPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(32, 38, 166, 154)
                    style = Paint.Style.FILL
                }
                val smcFvgBearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(32, 239, 83, 80)
                    style = Paint.Style.FILL
                }
                // bordes punteados del color del lado para leer las cajas
                val smcObBullEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(120, 38, 166, 154)
                    strokeWidth = 1.4f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(0.5f, 6f), 0f)
                }
                val smcObBearEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(120, 239, 83, 80)
                    strokeWidth = 1.4f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(0.5f, 6f), 0f)
                }
                val smcBullLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(255, 14, 203, 129)
                    strokeWidth = 3f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                val smcBearLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(255, 239, 83, 80)
                    strokeWidth = 3f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                val smcBullLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(255, 14, 203, 129)
                    textSize = 26f
                    textAlign = Paint.Align.LEFT
                    setShadowLayer(3f, 0f, 1f, GraphicsColor.argb(220, 0, 0, 0))
                }
                val smcBearLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(255, 239, 83, 80)
                    textSize = 26f
                    textAlign = Paint.Align.LEFT
                    setShadowLayer(3f, 0f, 1f, GraphicsColor.argb(220, 0, 0, 0))
                }
                val smcFvgTagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(170, 173, 177, 184)
                    textSize = 18f
                    textAlign = Paint.Align.LEFT
                }
                val smcChipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(215, 20, 21, 24)
                    style = Paint.Style.FILL
                }
                val smcEqPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(170, 255, 193, 7)
                    strokeWidth = 1.8f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(0.5f, 6f), 0f)
                }
                val smcEqLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(210, 255, 193, 7)
                    textSize = 22f
                    textAlign = Paint.Align.LEFT
                    setShadowLayer(3f, 0f, 1f, GraphicsColor.argb(220, 0, 0, 0))
                }
                val smcPremPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(12, 239, 83, 80)
                    style = Paint.Style.FILL
                }
                val smcDiscPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(12, 38, 166, 154)
                    style = Paint.Style.FILL
                }
                val smcEqLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(110, 173, 177, 184)
                    strokeWidth = 1.2f
                    style = Paint.Style.STROKE
                    pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
                }
                // gris donde el FVG de otro TF pisa tu zona
                val smcConfluencePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(60, 158, 158, 158)
                    style = Paint.Style.FILL
                }
                val smcConfluenceTagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(200, 189, 189, 189)
                    textSize = 20f
                    textAlign = Paint.Align.LEFT
                    setShadowLayer(3f, 0f, 1f, GraphicsColor.argb(220, 0, 0, 0))
                }
                // liquidez visible pero sin robarle a las velas
                val smcBslPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(200, 33, 150, 243)
                    strokeWidth = 1.6f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f)
                }
                val smcSslPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(200, 255, 152, 0)
                    strokeWidth = 1.6f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f)
                }
                val smcSweptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(120, 158, 158, 158)
                    strokeWidth = 1.6f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f)
                }
                val smcBslLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(200, 33, 150, 243)
                    textSize = 22f
                    textAlign = Paint.Align.LEFT
                }
                val smcSslLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(200, 255, 152, 0)
                    textSize = 22f
                    textAlign = Paint.Align.LEFT
                }
                val smcSweepLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(190, 158, 158, 158)
                    textSize = 22f
                    textAlign = Paint.Align.LEFT
                }

                val tagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.BLACK
                    textSize = 24f
                    textAlign = Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }

                private val tagBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.WHITE
                }
                private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.WHITE
                    style = Paint.Style.FILL
                }
                private val dotOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
                // linea + tag del precio actual estilo OKX
                private val lastPriceLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    strokeWidth = 2f
                    pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
                }
                private val lastPriceTagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                }
                val lastPriceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.WHITE
                    textSize = 24f
                    textAlign = Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                // escala Y fija a pantalla como OKX
                private val pinnedYLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor("#ADB1B8")
                    textSize = context.resources.displayMetrics.density * 10f
                    textAlign = Paint.Align.RIGHT
                }
                // volumen taker apilado transparente detras de las velas
                private val takerBuyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(110, 14, 203, 129)
                    style = Paint.Style.FILL
                }
                private val takerSellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(110, 239, 83, 80)
                    style = Paint.Style.FILL
                }
                private val yTagRect = RectF()
                private val xTagRect = RectF()

                private var lastTouchYPx = -1f
                private var downX = 0f
                private var downY = 0f
                private var lastGestureX = 0f
                private var lastGestureY = 0f
                private var zoomPivotX = 0f
                private var zoomPivotY = 0f
                private var lastPinchSpanX = 0f
                private var touchMode = ChartTouchMode.UNDECIDED
                private var gestureMoved = false

                private val density: Float
                    get() = context.resources.displayMetrics.density

                private fun isOnYAxis(x: Float): Boolean =
                    x >= width - 56f * density

                private fun matrixPivotX(x: Float): Float = x - viewPortHandler.offsetLeft()

                private fun matrixPivotY(y: Float): Float =
                    -(height - y - viewPortHandler.offsetBottom())

                private fun refreshTouchMatrix(matrix: Matrix) {
                    // MPAndroidChart's refresh() clamps translation and scale
                    // to its configured viewport limits. Touch gestures on
                    // this chart intentionally have no such bounds.
                    viewPortHandler.matrixTouch.set(matrix)
                    invalidate()
                    syncSubCharts(this, stochChartRef.value, rsiChartRef.value)
                }

                // el resize (divisor de altos) recalcula ejes con toda la
                // data; si el Y esta congelado se guarda y restaura para no aplanar
                override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                    val keepY = oldw > 0 && oldh > 0 && !isAutoScaleMinMaxEnabled && data != null
                    val savedMatrix = if (keepY) Matrix(viewPortHandler.matrixTouch) else null
                    val lMin = if (keepY) axisLeft.axisMinimum else 0f
                    val lMax = if (keepY) axisLeft.axisMaximum else 0f
                    val rMin = if (keepY) axisRight.axisMinimum else 0f
                    val rMax = if (keepY) axisRight.axisMaximum else 0f
                    super.onSizeChanged(w, h, oldw, oldh)
                    if (keepY && savedMatrix != null) {
                        axisLeft.axisMinimum = lMin
                        axisLeft.axisMaximum = lMax
                        axisRight.axisMinimum = rMin
                        axisRight.axisMaximum = rMax
                        viewPortHandler.matrixTouch.set(savedMatrix)
                    }
                }

                private fun applyPan(dx: Float, dy: Float) {
                    val matrix = Matrix(viewPortHandler.matrixTouch)
                    matrix.postTranslate(dx, dy)
                    // al mover libre se congela el Y, al entrar se reajusta solo
                    isAutoScaleMinMaxEnabled = false
                    refreshTouchMatrix(matrix)
                }

                private fun applyYZoom(dy: Float) {
                    // Moving down on the Y axis reduces the visible Y range;
                    // moving up expands it.
                    val scaleY = exp((-dy / (180f * density)).toDouble()).toFloat()
                    val matrix = Matrix(viewPortHandler.matrixTouch)
                    matrix.postScale(1f, scaleY, zoomPivotX, zoomPivotY)
                    refreshTouchMatrix(matrix)
                }

                private fun applyHorizontalPinchZoom(scaleX: Float, pivotX: Float, pivotY: Float) {
                    if (!scaleX.isFinite() || scaleX <= 0f) return
                    val matrix = Matrix(viewPortHandler.matrixTouch)
                    matrix.postScale(scaleX, 1f, pivotX, pivotY)
                    refreshTouchMatrix(matrix)
                }

                private fun touchToCandleIndex(x: Float, y: Float, size: Int): Int? {
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()
                    if (x < contentLeft || x > contentRight || y < contentTop || y > contentBottom) return null
                    val values = getTransformer(YAxis.AxisDependency.LEFT).getValuesByTouchPoint(x, y)
                    return values.x.roundToInt().coerceIn(0, size - 1)
                }

                private fun measureAnchor(x: Float, y: Float): Pair<Int, Double>? {
                    val candles = stateRef.value.candles
                    if (candles.isEmpty()) return null
                    val index = touchToCandleIndex(x, y, candles.size) ?: return null
                    var price = getTransformer(YAxis.AxisDependency.LEFT).getValuesByTouchPoint(x, y).y.toDouble()
                    // iman pega Medir al high/low/close mas cercano
                    if (magnetRef.value) {
                        val trans = getTransformer(YAxis.AxisDependency.LEFT)
                        val c = candles[index]
                        var best = price
                        var bestDist = 28f
                        listOf(c.high, c.low, c.close).forEach { p ->
                            val pts = floatArrayOf(0f, p.toFloat())
                            trans.pointValuesToPixel(pts)
                            val d = abs(pts[1] - y)
                            if (d < bestDist) {
                                bestDist = d
                                best = p
                            }
                        }
                        price = best
                    }
                    return index to price
                }

                private fun applyRangeTap(x: Float, y: Float): Boolean {
                    val (index, price) = measureAnchor(x, y) ?: return false
                    val current = rangeSelection.value
                    // zona completa: tap fuera del rectangulo la borra, dentro no hace nada
                    if (current != null && current.isComplete) {
                        if (measureHitTest(x, y) == 0) {
                            rangeSelection.value = null
                            invalidate()
                        }
                        return true
                    }
                    rangeSelection.value = if (current == null) {
                        MeasureZone(startIdx = index, startPrice = price)
                    } else {
                        current.copy(endIdx = index, endPrice = price)
                    }
                    highlightValue(null)
                    lastTouchYPx = -1f
                    syncHighlights(this, stochChartRef.value, rsiChartRef.value)
                    invalidate()
                    return true
                }

                // escala Y fija a la pantalla, no sigue al pan como los nativos
                private fun drawPinnedYLabels(canvas: Canvas, candles: List<CandleData>) {
                    if (candles.isEmpty()) return
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val contentRight = viewPortHandler.contentRight()
                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()
                    val refClose = candles.lastOrNull()?.close ?: 0.0
                    val n = 6
                    for (i in 0 until n) {
                        val py = contentTop + (contentBottom - contentTop) * i / (n - 1)
                        val v = trans.getValuesByTouchPoint(0f, py).y
                        if (!v.isFinite()) continue
                        canvas.drawText(
                            formatAxisPrice(v, refClose),
                            contentRight - 6f,
                            py + pinnedYLabelPaint.textSize * 0.35f,
                            pinnedYLabelPaint
                        )
                    }
                }

                // linea punteada + tag con precio actual y countdown al cierre
                private fun drawLastPriceTag(canvas: Canvas, candles: List<CandleData>) {
                    val current = stateRef.value
                    val last = candles.lastOrNull() ?: return
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()

                    val pts = floatArrayOf((candles.size - 1).toFloat(), last.close.toFloat())
                    trans.pointValuesToPixel(pts)
                    val py = pts[1].coerceIn(contentTop, contentBottom)

                    val tagColor = if (last.close >= last.open) {
                        GraphicsColor.parseColor("#0ECB81")
                    } else {
                        GraphicsColor.parseColor("#EF5350")
                    }
                    lastPriceLinePaint.color = tagColor
                    lastPriceTagPaint.color = tagColor
                    canvas.drawLine(contentLeft, py, contentRight, py, lastPriceLinePaint)

                    val priceText = formatPriceForChart(last.close)
                    val countdown = formatCandleCountdown(current.selectedInterval, last.time)
                    val textSize = lastPriceTextPaint.textSize
                    val padX = 12f
                    val padY = 6f
                    val tagW = max(
                        lastPriceTextPaint.measureText(priceText),
                        lastPriceTextPaint.measureText(countdown)
                    ) + padX * 2f
                    val tagH = textSize * 2f + padY * 2f + 6f
                    var tagRight = contentRight - 2f + tagW
                    val chartW = width.toFloat()
                    if (tagRight > chartW - 2f) {
                        tagRight = chartW - 2f
                    }
                    val tagLeft = tagRight - tagW
                    val tagTop = (py - tagH / 2f).coerceIn(
                        contentTop,
                        max(contentTop, contentBottom - tagH)
                    )
                    yTagRect.set(tagLeft, tagTop, tagRight, tagTop + tagH)
                    canvas.drawRoundRect(yTagRect, 6f, 6f, lastPriceTagPaint)
                    canvas.drawText(priceText, yTagRect.centerX(), tagTop + padY + textSize, lastPriceTextPaint)
                    canvas.drawText(countdown, yTagRect.centerX(), tagTop + padY + textSize * 2f + 4f, lastPriceTextPaint)
                }

                // tags de MAs de otro TF: codo punteado 45° + etiqueta al borde (1h.SMA20)
                private fun drawMaTfTags(canvas: Canvas, candles: List<CandleData>) {
                    if (candles.isEmpty()) return
                    val interval = stateRef.value.selectedInterval
                    val mas = prefsRef.value.mas.filter { it.visible && it.timeframe != "chart" && it.timeframe != interval }
                    if (mas.isEmpty()) return
                    val lines = stateRef.value.maLines
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()
                    data class Tag(val py: Float, val px: Float, val text: String, val color: Int)
                    val tags = ArrayList<Tag>()
                    mas.forEach { ma ->
                        val line = lines[ma.id] ?: return@forEach
                        if (line.isEmpty()) return@forEach
                        val last = line.last()
                        val pts = floatArrayOf(last.first.toFloat(), last.second.toFloat())
                        trans.pointValuesToPixel(pts)
                        val px = pts[0].coerceIn(contentLeft, contentRight)
                        val py = pts[1].coerceIn(contentTop, contentBottom)
                        val color = try {
                            GraphicsColor.parseColor(ma.colorHex)
                        } catch (_: Exception) {
                            GraphicsColor.WHITE
                        }
                        val kind = if (ma.type == MaType.EMA) "EMA" else "SMA"
                        tags.add(Tag(py, px, "${ma.timeframe}.$kind${ma.period}", color))
                    }
                    if (tags.isEmpty()) return
                    tags.sortBy { it.py }
                    val th = maTagTextPaint.textSize + 10f
                    val minGap = th + 6f
                    // codo 45° + horizontal a la altura del chip, apilados sin encimarse
                    var cursor = Float.NEGATIVE_INFINITY
                    tags.forEach { t ->
                        // prefiere arriba del punto, abajo si no hay aire
                        var cy = t.py - (th + minGap)
                        if (cy < contentTop + th / 2f) cy = t.py + (th + minGap)
                        cy = cy.coerceIn(contentTop + th / 2f, contentBottom - th / 2f)
                        if (cy < cursor) cy = cursor
                        cy = min(cy, contentBottom - th / 2f)
                        cursor = cy + minGap
                        val tw = maTagTextPaint.measureText(t.text)
                        val tagRight = contentRight - 4f
                        val tagLeft = (tagRight - tw - 16f).coerceAtLeast(contentLeft)
                        // 45°: avance horizontal = subida; luego recto hasta el tag
                        var kx = (t.px + abs(cy - t.py)).coerceIn(contentLeft, contentRight)
                        if (kx > tagLeft - 4f) kx = (tagLeft - 4f).coerceAtLeast(contentLeft)
                        maTagDashPaint.color = t.color
                        canvas.drawLine(t.px, t.py, kx, cy, maTagDashPaint)
                        canvas.drawLine(kx, cy, tagLeft, cy, maTagDashPaint)
                        val ty = cy - th / 2f
                        yTagRect.set(tagLeft, ty, tagRight, ty + th)
                        canvas.drawRoundRect(yTagRect, 4f, 4f, drawChipPaint)
                        maTagTextPaint.color = t.color
                        canvas.drawText(t.text, tagLeft + 8f, ty + th - 7f, maTagTextPaint)
                    }
                    maTagTextPaint.color = GraphicsColor.WHITE
                }

                // volumen taker apilado (buy abajo, sell arriba) al fondo del chart
                private fun drawVolumeOverlay(canvas: Canvas, candles: List<CandleData>, visibleStart: Int, visibleEnd: Int) {
                    if (!prefsRef.value.volumeVisible) return
                    if (visibleStart >= visibleEnd) return
                    var maxVol = 0.0
                    var hasTaker = false
                    for (i in visibleStart..visibleEnd) {
                        maxVol = max(maxVol, candles[i].volume)
                        if (candles[i].takerBuyVol > 0.0) hasTaker = true
                    }
                    if (maxVol <= 0.0) return

                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()
                    val areaH = (contentBottom - contentTop) * 0.18f
                    val base = contentBottom
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val pts = FloatArray(2)
                    pts[0] = visibleStart.toFloat()
                    pts[1] = 0f
                    trans.pointValuesToPixel(pts)
                    val x0 = pts[0]
                    pts[0] = (visibleStart + 1).toFloat()
                    trans.pointValuesToPixel(pts)
                    val barW = (((pts[0] - x0) * 0.65f).coerceAtLeast(1f))

                    for (i in visibleStart..visibleEnd) {
                        val c = candles[i]
                        pts[0] = i.toFloat()
                        pts[1] = 0f
                        trans.pointValuesToPixel(pts)
                        val cx = pts[0]
                        if (hasTaker) {
                            val buy = c.takerBuyVol.coerceIn(0.0, c.volume)
                            val sell = (c.volume - buy).coerceAtLeast(0.0)
                            val buyH = (areaH * (buy / maxVol)).toFloat()
                            val sellH = (areaH * (sell / maxVol)).toFloat()
                            if (buyH > 0f) {
                                canvas.drawRect(cx - barW / 2f, base - buyH, cx + barW / 2f, base, takerBuyPaint)
                            }
                            if (sellH > 0f) {
                                canvas.drawRect(cx - barW / 2f, base - buyH - sellH, cx + barW / 2f, base - buyH, takerSellPaint)
                            }
                        } else {
                            // sin dato taker, barra simple del color de la vela
                            val h = (areaH * (c.volume / maxVol)).toFloat()
                            if (h > 0f) {
                                val paint = if (c.close >= c.open) takerBuyPaint else takerSellPaint
                                canvas.drawRect(cx - barW / 2f, base - h, cx + barW / 2f, base, paint)
                            }
                        }
                    }
                }

                private fun drawFixedRangeVolumeProfile(
                    canvas: Canvas,
                    candles: List<CandleData>,
                    visibleStart: Int,
                    visibleEnd: Int
                ) {
                    if (visibleStart >= visibleEnd) return
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()
                    val contentHeight = contentBottom - contentTop
                    val contentWidth = contentRight - contentLeft
                    if (contentHeight <= 0f || contentWidth <= 0f) return

                    var minPrice = Double.POSITIVE_INFINITY
                    var maxPrice = Double.NEGATIVE_INFINITY
                    for (index in visibleStart..visibleEnd) {
                        val candle = candles[index]
                        minPrice = min(minPrice, candle.low)
                        maxPrice = max(maxPrice, candle.high)
                    }
                    if (!minPrice.isFinite() || !maxPrice.isFinite() || maxPrice <= minPrice) return

                    val binCount = (contentHeight / 8f).roundToInt().coerceIn(34, 64)
                    val upVolume = DoubleArray(binCount)
                    val downVolume = DoubleArray(binCount)
                    val binSize = (maxPrice - minPrice) / binCount
                    if (binSize <= 0.0) return

                    for (index in visibleStart..visibleEnd) {
                        val candle = candles[index]
                        val firstBin = (((candle.low - minPrice) / binSize).toInt()).coerceIn(0, binCount - 1)
                        val lastBin = (((candle.high - minPrice) / binSize).toInt()).coerceIn(0, binCount - 1)
                        val touchedBins = (lastBin - firstBin + 1).coerceAtLeast(1)
                        val volumePerBin = candle.volume / touchedBins
                        for (bin in firstBin..lastBin) {
                            if (candle.close >= candle.open) {
                                upVolume[bin] += volumePerBin
                            } else {
                                downVolume[bin] += volumePerBin
                            }
                        }
                    }

                    var maxVolume = 0.0
                    var pocBin = 0
                    for (bin in 0 until binCount) {
                        val total = upVolume[bin] + downVolume[bin]
                        if (total > maxVolume) {
                            maxVolume = total
                            pocBin = bin
                        }
                    }
                    if (maxVolume <= 0.0) return

                    val profileMaxWidth = (contentWidth * 0.26f).coerceIn(72f, 160f)
                    val profileRight = contentRight - 8f
                    val profileLeft = profileRight - profileMaxWidth - 6f
                    val barGap = 1.4f
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val pricePts = FloatArray(2)

                    yTagRect.set(
                        profileLeft.coerceAtLeast(contentLeft),
                        contentTop,
                        contentRight,
                        contentBottom
                    )
                    canvas.drawRect(yTagRect, profileBackdropPaint)
                    canvas.drawLine(yTagRect.left, contentTop, yTagRect.left, contentBottom, profileBoundaryPaint)

                    for (bin in 0 until binCount) {
                        val total = upVolume[bin] + downVolume[bin]
                        if (total <= 0.0) continue
                        val low = minPrice + binSize * bin
                        val high = low + binSize
                        pricePts[0] = 0f
                        pricePts[1] = high.toFloat()
                        trans.pointValuesToPixel(pricePts)
                        val top = pricePts[1].coerceIn(contentTop, contentBottom)
                        pricePts[1] = low.toFloat()
                        trans.pointValuesToPixel(pricePts)
                        val bottom = pricePts[1].coerceIn(contentTop, contentBottom)
                        val barTop = min(top, bottom) + barGap
                        val barBottom = max(top, bottom) - barGap
                        if (barBottom <= barTop) continue

                        val totalWidth = (profileMaxWidth * (total / maxVolume)).toFloat()
                        val downWidth = if (total > 0.0) (totalWidth * (downVolume[bin] / total)).toFloat() else 0f
                        val upWidth = totalWidth - downWidth
                        val right = profileRight
                        val left = right - totalWidth
                        val split = left + upWidth
                        if (bin == pocBin) {
                            canvas.drawRect(left, barTop, right, barBottom, profilePocPaint)
                        } else {
                            if (upWidth > 0f) canvas.drawRect(left, barTop, split, barBottom, profileUpPaint)
                            if (downWidth > 0f) canvas.drawRect(split, barTop, right, barBottom, profileDownPaint)
                        }
                    }

                    val pocPrice = minPrice + binSize * (pocBin + 0.5)
                    val pocPts = floatArrayOf(0f, pocPrice.toFloat())
                    trans.pointValuesToPixel(pocPts)
                    val pocY = pocPts[1].coerceIn(contentTop, contentBottom)
                    canvas.drawLine(contentLeft, pocY, contentRight, pocY, profilePocLinePaint)

                    val label = "POC ${formatPriceForChart(pocPrice)}"
                    val paddingX = 8f
                    val paddingY = 5f
                    val labelWidth = selectionTextPaint.measureText(label) + paddingX * 2f
                    val labelHeight = selectionTextPaint.textSize + paddingY * 2f
                    val labelLeft = (contentRight - labelWidth - 6f).coerceAtLeast(contentLeft + 6f)
                    val labelTop = (pocY - labelHeight - 4f).coerceIn(contentTop + 4f, contentBottom - labelHeight - 4f)
                    yTagRect.set(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight)
                    canvas.drawRoundRect(yTagRect, 5f, 5f, selectionLabelPaint)
                    canvas.drawText(label, labelLeft + paddingX, labelTop + labelHeight - paddingY - 4f, selectionTextPaint)
                }

                private fun drawPriceRangeSelection(canvas: Canvas, candles: List<CandleData>) {
                    val selection = rangeSelection.value ?: return
                    if (candles.isEmpty()) return
                    val sIdx = selection.startIdx.coerceIn(0, candles.size - 1)
                    val eIdx = selection.endIdx?.coerceIn(0, candles.size - 1)
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()

                    if (eIdx == null || selection.endPrice == null) {
                        val pts = floatArrayOf(sIdx.toFloat(), selection.startPrice.toFloat())
                        trans.pointValuesToPixel(pts)
                        val x = pts[0].coerceIn(contentLeft, contentRight)
                        val y = pts[1].coerceIn(contentTop, contentBottom)
                        selectionStrokePaint.color = GraphicsColor.argb(230, 30, 203, 129)
                        canvas.drawLine(x, contentTop, x, contentBottom, selectionStrokePaint)
                        canvas.drawCircle(x, y, 7f, dotPaint)
                        canvas.drawCircle(x, y, 7f, dotOutlinePaint)
                        val label = "Toca fin"
                        val labelWidth = selectionTextPaint.measureText(label) + 18f
                        val labelHeight = selectionTextPaint.textSize + 12f
                        val labelLeft = (x + 8f).coerceIn(contentLeft + 4f, contentRight - labelWidth - 4f)
                        val labelTop = (y - labelHeight - 8f).coerceIn(contentTop + 4f, contentBottom - labelHeight - 4f)
                        yTagRect.set(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight)
                        canvas.drawRoundRect(yTagRect, 5f, 5f, selectionLabelPaint)
                        canvas.drawText(label, labelLeft + 9f, labelTop + labelHeight - 8f, selectionTextPaint)
                        return
                    }

                    // esquinas de la diagonal, el precio es del toque (como el fibo)
                    val leftIndex = min(sIdx, eIdx)
                    val rightIndex = max(sIdx, eIdx)
                    val firstPrice = if (sIdx <= eIdx) selection.startPrice else selection.endPrice
                    val lastPrice = if (sIdx <= eIdx) selection.endPrice else selection.startPrice
                    val pts = floatArrayOf(
                        leftIndex.toFloat(),
                        firstPrice.toFloat(),
                        rightIndex.toFloat(),
                        lastPrice.toFloat()
                    )
                    trans.pointValuesToPixel(pts)
                    val leftX = pts[0].coerceIn(contentLeft, contentRight)
                    val rightX = pts[2].coerceIn(contentLeft, contentRight)
                    val firstY = pts[1].coerceIn(contentTop, contentBottom)
                    val lastY = pts[3].coerceIn(contentTop, contentBottom)
                    val visualTop = min(firstY, lastY)
                    val visualBottom = max(firstY, lastY)
                    val minHeight = context.resources.displayMetrics.density * 18f
                    val adjustedTop = if (visualBottom - visualTop < minHeight) {
                        (visualTop - minHeight / 2f).coerceAtLeast(contentTop)
                    } else {
                        visualTop
                    }
                    val adjustedBottom = if (visualBottom - visualTop < minHeight) {
                        (adjustedTop + minHeight).coerceAtMost(contentBottom)
                    } else {
                        visualBottom
                    }

                    val change = lastPrice - firstPrice
                    val changePct = if (firstPrice != 0.0) change / firstPrice * 100.0 else 0.0
                    val isUp = changePct >= 0.0
                    selectionStrokePaint.color = if (isUp) GraphicsColor.argb(230, 30, 203, 129) else GraphicsColor.argb(230, 246, 70, 93)
                    selectionFillPaint.color = if (isUp) GraphicsColor.argb(42, 30, 203, 129) else GraphicsColor.argb(42, 246, 70, 93)

                    yTagRect.set(leftX, adjustedTop, rightX, adjustedBottom)
                    canvas.drawRect(yTagRect, selectionFillPaint)
                    canvas.drawRect(yTagRect, selectionStrokePaint)
                    canvas.drawLine(leftX, contentTop, leftX, contentBottom, selectionStrokePaint)
                    canvas.drawLine(rightX, contentTop, rightX, contentBottom, selectionStrokePaint)

                    // handles en las esquinas de la diagonal solo en modo Medir
                    if (toolRef.value == DrawingTool.MEASURE) {
                        val r = 22f
                        canvas.drawCircle(leftX, firstY, r, fibHandleFill)
                        canvas.drawCircle(leftX, firstY, r, fibHandleStroke)
                        canvas.drawCircle(rightX, lastY, r, fibHandleFill)
                        canvas.drawCircle(rightX, lastY, r, fibHandleStroke)
                    } else {
                        canvas.drawCircle(leftX, firstY, 6f, dotPaint)
                        canvas.drawCircle(rightX, lastY, 6f, dotPaint)
                    }

                    val bars = rightIndex - leftIndex
                    val label = "${if (changePct >= 0.0) "+" else ""}${String.format(Locale.US, "%.2f", changePct)}%  $bars velas"
                    val labelWidth = selectionTextPaint.measureText(label) + 18f
                    val labelHeight = selectionTextPaint.textSize + 12f
                    val labelLeft = (rightX - labelWidth).coerceIn(contentLeft + 4f, contentRight - labelWidth - 4f)
                    val labelTop = (adjustedTop - labelHeight - 6f).coerceIn(contentTop + 4f, contentBottom - labelHeight - 4f)
                    yTagRect.set(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight)
                    canvas.drawRoundRect(yTagRect, 5f, 5f, selectionLabelPaint)
                    canvas.drawText(label, labelLeft + 9f, labelTop + labelHeight - 8f, selectionTextPaint)
                }

                // ─── MEDIR: drag por esquinas + mover por dentro ───
                private var measureDragZone = 0 // 1 primera esquina, 2 segunda, 3 dentro
                private var measureDragMoved = false
                private var measureGrabIdx = 0
                private var measureGrabPrice = 0.0
                private var measureOrig: MeasureZone? = null

                // 1/2 = esquinas de la diagonal, 3 = dentro de la zona
                private fun measureHitTest(x: Float, y: Float): Int {
                    val sel = rangeSelection.value ?: return 0
                    if (!sel.isComplete) return 0
                    val candles = stateRef.value.candles
                    if (candles.isEmpty()) return 0
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val lIdx = min(sel.startIdx, sel.endIdx!!).coerceIn(0, candles.size - 1)
                    val rIdx = max(sel.startIdx, sel.endIdx).coerceIn(0, candles.size - 1)
                    val firstPrice = if (sel.startIdx <= sel.endIdx) sel.startPrice else sel.endPrice!!
                    val lastPrice = if (sel.startIdx <= sel.endIdx) sel.endPrice!! else sel.startPrice
                    val pts = floatArrayOf(lIdx.toFloat(), firstPrice.toFloat(), rIdx.toFloat(), lastPrice.toFloat())
                    trans.pointValuesToPixel(pts)
                    if (kotlin.math.hypot(x - pts[0], y - pts[1]) <= 60f) return 1
                    if (kotlin.math.hypot(x - pts[2], y - pts[3]) <= 60f) return 2
                    val leftX = min(pts[0], pts[2])
                    val rightX = max(pts[0], pts[2])
                    val top = min(pts[1], pts[3])
                    val bottom = max(pts[1], pts[3])
                    if (x >= leftX && x <= rightX && y >= top && y <= bottom) return 3
                    return 0
                }

                // ─── FIBO multi-overlay ───
                // handle=resize, cuerpo=mover, tap=seleccionar; candado bloquea gestos
                private var fibDragId: String? = null
                private var fibDragZone = 0 // 1 inicio, 2 fin, 3 cuerpo
                private var fibDragMoved = false
                private var fibGrabIdx = 0
                private var fibGrabPrice = 0.0
                private var fibOrigStart: FibAnchor? = null
                private var fibOrigEnd: FibAnchor? = null
                var onFibLive: ((String, FibOverlay) -> Unit)? = null
                var onFibCommit: ((String) -> Unit)? = null
                var onFibTap: ((FibAnchor) -> Unit)? = null
                var onFibSelect: ((String?) -> Unit)? = null

                private fun anchorFromTouch(x: Float, y: Float): FibAnchor? {
                    val candles = stateRef.value.candles
                    if (candles.isEmpty()) return null
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()
                    if (x < contentLeft || x > contentRight || y < contentTop || y > contentBottom) return null
                    // indice virtual para anclar mas alla de la ultima vela
                    val rawX = getTransformer(YAxis.AxisDependency.LEFT).getValuesByTouchPoint(x, y).x.roundToInt()
                    val index = rawX.coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                    val price = getTransformer(YAxis.AxisDependency.LEFT).getValuesByTouchPoint(x, y).y.toDouble()
                    return FibAnchor(fibIndexToTime(candles, index, stateRef.value.selectedInterval), price)
                }

                private fun overlayPixels(o: FibOverlay): Pair<android.graphics.PointF?, android.graphics.PointF?> {
                    val candles = stateRef.value.candles
                    if (candles.isEmpty()) return null to null
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val interval = stateRef.value.selectedInterval
                    val sIdx = fibTimeToIndexVirtual(candles, o.start.time, interval)
                    val eIdx = fibTimeToIndexVirtual(candles, o.end.time, interval)
                    val s = floatArrayOf(sIdx.toFloat(), o.start.price.toFloat())
                    trans.pointValuesToPixel(s)
                    val e = floatArrayOf(eIdx.toFloat(), o.end.price.toFloat())
                    trans.pointValuesToPixel(e)
                    return android.graphics.PointF(s[0], s[1]) to android.graphics.PointF(e[0], e[1])
                }

                private fun distToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
                    val dx = bx - ax
                    val dy = by - ay
                    val len2 = dx * dx + dy * dy
                    if (len2 <= 0f) return kotlin.math.hypot(px - ax, py - ay)
                    val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
                    return kotlin.math.hypot(px - (ax + t * dx), py - (ay + t * dy))
                }

                // devuelve (id, zona, locked) del fibo mas cercano al toque, o null
                private fun fibHitTest(x: Float, y: Float): Triple<String, Int, Boolean>? {
                    val candles = stateRef.value.candles
                    if (candles.isEmpty()) return null
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    var best: Triple<String, Int, Boolean>? = null
                    var bestDist = Float.MAX_VALUE
                    overlaysRef.value.forEach { o ->
                        if (o.hidden) return@forEach
                        val (s, e) = overlayPixels(o)
                        if (s == null || e == null) return@forEach
                        val ds = kotlin.math.hypot(x - s.x, y - s.y)
                        if (ds <= 60f && ds < bestDist) {
                            bestDist = ds
                            best = Triple(o.id, 1, o.locked)
                        }
                        val de = kotlin.math.hypot(x - e.x, y - e.y)
                        if (de <= 60f && de < bestDist) {
                            bestDist = de
                            best = Triple(o.id, 2, o.locked)
                        }
                        if (bestDist <= 60f) return@forEach
                        // cuerpo = lineas de nivel + guia diagonal (mover)
                        val leftX = min(s.x, e.x)
                        val rightX = max(s.x, e.x)
                        var bodyDist = distToSegment(x, y, s.x, s.y, e.x, e.y)
                        o.levelsSorted().forEach { ratio ->
                            val price = fibLevelPrice(o.end.price, o.start.price, ratio)
                            val pts = floatArrayOf(0f, price.toFloat())
                            trans.pointValuesToPixel(pts)
                            if (x >= leftX - 20f && x <= rightX + 20f && abs(y - pts[1]) <= 28f) {
                                bodyDist = 0f
                            }
                        }
                        if (bodyDist <= 28f && bodyDist < bestDist) {
                            bestDist = bodyDist
                            best = Triple(o.id, 3, o.locked)
                        }
                    }
                    return best
                }

                private fun applyFibTap(x: Float, y: Float) {
                    // fibo tambien respeta el iman (high/low/close)
                    val anchor = drawAnchorFromTouch(x, y) ?: return
                    onFibTap?.invoke(anchor)
                    invalidate()
                }

                // ─── DIBUJOS OKX ───
                private var drawDragId: String? = null
                private var drawDragZone: Int = 0
                private var drawDragMoved: Boolean = false
                private var drawOrigStart: FibAnchor? = null
                private var drawOrigEnd: FibAnchor? = null
                private var drawGrabIdx: Int = 0
                private var drawGrabPrice: Double = 0.0
                var onDrawLive: ((String, DrawOverlay) -> Unit)? = null
                var onDrawCommit: ((String) -> Unit)? = null
                var onDrawTap: ((FibAnchor) -> Unit)? = null
                var onDrawSelect: ((String?) -> Unit)? = null

                // con iman pega el precio al high/low/close mas cercano
                private fun drawAnchorFromTouch(x: Float, y: Float): FibAnchor? {
                    val base = anchorFromTouch(x, y) ?: return null
                    if (!magnetRef.value) return base
                    val candles = stateRef.value.candles
                    if (candles.isEmpty()) return base
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val rawX = trans.getValuesByTouchPoint(x, y).x.roundToInt()
                    // mas alla de la ultima vela no hay high/low, se deja tal cual (fibo virtual)
                    if (rawX < 0 || rawX >= candles.size) return base
                    val c = candles[rawX]
                    // pointValuesToPixel necesita pares x,y; evalua por separado
                    var best = base.price
                    var bestDist = 28f
                    listOf(c.high, c.low, c.close).forEach { p ->
                        val pts = floatArrayOf(0f, p.toFloat())
                        trans.pointValuesToPixel(pts)
                        val d = abs(pts[1] - y)
                        if (d < bestDist) {
                            bestDist = d
                            best = p
                        }
                    }
                    return base.copy(price = best)
                }

                private fun drawPixels(o: DrawOverlay): Pair<android.graphics.PointF?, android.graphics.PointF?> {
                    val candles = stateRef.value.candles
                    if (candles.isEmpty()) return null to null
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val interval = stateRef.value.selectedInterval
                    val sIdx = fibTimeToIndexVirtual(candles, o.start.time, interval)
                    val eIdx = fibTimeToIndexVirtual(candles, o.end.time, interval)
                    val s = floatArrayOf(sIdx.toFloat(), o.start.price.toFloat())
                    trans.pointValuesToPixel(s)
                    val e = floatArrayOf(eIdx.toFloat(), o.end.price.toFloat())
                    trans.pointValuesToPixel(e)
                    return android.graphics.PointF(s[0], s[1]) to android.graphics.PointF(e[0], e[1])
                }

                // 1 = handle inicio, 2 = handle fin, 3 = cuerpo
                private fun drawHitTest(x: Float, y: Float): Triple<String, Int, Boolean>? {
                    if (stateRef.value.candles.isEmpty()) return null
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    var best: Triple<String, Int, Boolean>? = null
                    var bestDist = Float.MAX_VALUE
                    drawsRef.value.forEach { o ->
                        if (o.hidden) return@forEach
                        val (s, e) = drawPixels(o)
                        if (s == null || e == null) return@forEach
                        val ds = kotlin.math.hypot(x - s.x, y - s.y)
                        if (ds <= 60f && ds < bestDist) {
                            bestDist = ds
                            best = Triple(o.id, 1, o.locked)
                        }
                        val de = kotlin.math.hypot(x - e.x, y - e.y)
                        if (de <= 60f && de < bestDist) {
                            bestDist = de
                            best = Triple(o.id, 2, o.locked)
                        }
                        if (bestDist <= 60f) return@forEach
                        var bodyDist = Float.MAX_VALUE
                        when (o.kind) {
                            DrawKind.SEGMENT, DrawKind.ARROW ->
                                bodyDist = distToSegment(x, y, s.x, s.y, e.x, e.y)
                            DrawKind.LINE -> {
                                val dx = e.x - s.x
                                val dy = e.y - s.y
                                val len = kotlin.math.hypot(dx, dy)
                                if (len > 1f && x >= contentLeft - 20f && x <= contentRight + 20f) {
                                    bodyDist = abs(dy * x - dx * y + e.x * s.y - e.y * s.x) / len
                                }
                            }
                            DrawKind.RAY -> {
                                val dx = e.x - s.x
                                val dy = e.y - s.y
                                val len2 = dx * dx + dy * dy
                                if (len2 > 1f) {
                                    val t = ((x - s.x) * dx + (y - s.y) * dy) / len2
                                    if (t >= 0f) {
                                        bodyDist = kotlin.math.hypot(x - (s.x + t * dx), y - (s.y + t * dy))
                                    }
                                }
                            }
                            DrawKind.H_SEGMENT, DrawKind.H_LINE, DrawKind.H_RAY, DrawKind.PRICE_LINE -> {
                                val inX = when (o.kind) {
                                    DrawKind.H_LINE, DrawKind.PRICE_LINE -> x >= contentLeft - 20f && x <= contentRight + 20f
                                    DrawKind.H_RAY -> if (e.x >= s.x) x >= s.x - 20f else x <= s.x + 20f
                                    else -> x >= min(s.x, e.x) - 20f && x <= max(s.x, e.x) + 20f
                                }
                                if (inX) bodyDist = abs(y - s.y)
                            }
                            DrawKind.RECT -> {
                                val l = min(s.x, e.x)
                                val r = max(s.x, e.x)
                                val t = min(s.y, e.y)
                                val b = max(s.y, e.y)
                                bodyDist = minOf(
                                    distToSegment(x, y, l, t, r, t),
                                    distToSegment(x, y, r, t, r, b),
                                    distToSegment(x, y, r, b, l, b),
                                    distToSegment(x, y, l, b, l, t)
                                )
                            }
                            DrawKind.CIRCLE -> {
                                val cx = (s.x + e.x) / 2f
                                val cy = (s.y + e.y) / 2f
                                val rx = abs(e.x - s.x) / 2f
                                val ry = abs(e.y - s.y) / 2f
                                if (rx > 4f && ry > 4f) {
                                    val nx = (x - cx) / rx
                                    val ny = (y - cy) / ry
                                    val d = kotlin.math.hypot(nx, ny)
                                    bodyDist = abs(d - 1f) * min(rx, ry)
                                }
                            }
                            DrawKind.TRIANGLE -> {
                                val mx = (s.x + e.x) / 2f
                                bodyDist = minOf(
                                    distToSegment(x, y, mx, s.y, s.x, e.y),
                                    distToSegment(x, y, s.x, e.y, e.x, e.y),
                                    distToSegment(x, y, e.x, e.y, mx, s.y)
                                )
                            }
                        }
                        if (bodyDist <= 28f && bodyDist < bestDist) {
                            bestDist = bodyDist
                            best = Triple(o.id, 3, o.locked)
                        }
                    }
                    return best
                }

                private fun applyDrawTap(x: Float, y: Float) {
                    val anchor = drawAnchorFromTouch(x, y) ?: return
                    onDrawTap?.invoke(anchor)
                    invalidate()
                }

                private fun drawSingleDraw(canvas: Canvas, candles: List<CandleData>, o: DrawOverlay, selected: Boolean) {
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()
                    val (s, e) = drawPixels(o)
                    if (s == null || e == null) return
                    val lineColor = try {
                        GraphicsColor.parseColor(o.colorHex)
                    } catch (_: Exception) {
                        GraphicsColor.WHITE
                    }
                    drawLinePaint.color = lineColor
                    drawLinePaint.strokeWidth = (o.width * context.resources.displayMetrics.density).coerceAtLeast(1f)
                    drawLinePaint.alpha = if (selected || selectedDrawRef.value == null) 255 else 140
                    drawDashPaint.color = lineColor
                    drawDashPaint.strokeWidth = (o.width * context.resources.displayMetrics.density).coerceAtLeast(1f)
                    drawLabelPaint.color = lineColor

                    fun hLine(py: Float, fromX: Float, toX: Float, dashed: Boolean = false) {
                        val p = if (dashed) drawDashPaint else drawLinePaint
                        canvas.drawLine(fromX.coerceIn(contentLeft, contentRight), py, toX.coerceIn(contentLeft, contentRight), py, p)
                    }
                    fun seg(ax: Float, ay: Float, bx: Float, by: Float) {
                        canvas.drawLine(ax, ay, bx, by, drawLinePaint)
                    }
                    // infinita por dos puntos recortada a los bordes
                    fun infinite(sx: Float, sy: Float, ex: Float, ey: Float) {
                        val dx = ex - sx
                        if (abs(dx) < 1f) {
                            canvas.drawLine(sx.coerceIn(contentLeft, contentRight), contentTop, sx.coerceIn(contentLeft, contentRight), contentBottom, drawLinePaint)
                            return
                        }
                        val m = (ey - sy) / dx
                        seg(contentLeft, (sy + m * (contentLeft - sx)).coerceIn(contentTop, contentBottom), contentRight, (sy + m * (contentRight - sx)).coerceIn(contentTop, contentBottom))
                    }
                    // semirrecta desde s pasando por e hasta el borde
                    fun ray(sx: Float, sy: Float, ex: Float, ey: Float) {
                        val edgeX = if (ex >= sx) contentRight else contentLeft
                        val dx = ex - sx
                        if (abs(dx) < 1f) {
                            val edgeY = if (ey >= sy) contentBottom else contentTop
                            seg(sx, sy, sx, edgeY)
                            return
                        }
                        val m = (ey - sy) / dx
                        seg(sx, sy, edgeX, (sy + m * (edgeX - sx)).coerceIn(contentTop, contentBottom))
                    }
                    // punta de flecha en e
                    fun arrowHead(ex: Float, ey: Float, fromX: Float, fromY: Float) {
                        val ang = kotlin.math.atan2((ey - fromY).toDouble(), (ex - fromX).toDouble())
                        val len = 26f
                        listOf(ang + 2.6, ang - 2.6).forEach { a ->
                            seg(ex, ey, (ex + len * kotlin.math.cos(a)).toFloat(), (ey + len * kotlin.math.sin(a)).toFloat())
                        }
                    }

                    when (o.kind) {
                        DrawKind.SEGMENT -> seg(s.x, s.y, e.x, e.y)
                        DrawKind.LINE -> infinite(s.x, s.y, e.x, e.y)
                        DrawKind.RAY -> ray(s.x, s.y, e.x, e.y)
                        DrawKind.ARROW -> {
                            seg(s.x, s.y, e.x, e.y)
                            arrowHead(e.x, e.y, s.x, s.y)
                        }
                        DrawKind.H_SEGMENT -> hLine(s.y, s.x, e.x)
                        DrawKind.H_LINE -> hLine(s.y, contentLeft, contentRight)
                        DrawKind.H_RAY -> {
                            if (e.x >= s.x) hLine(s.y, s.x, contentRight) else hLine(s.y, contentLeft, s.x)
                        }
                        DrawKind.RECT -> {
                            yTagRect.set(min(s.x, e.x), min(s.y, e.y), max(s.x, e.x), max(s.y, e.y))
                            canvas.drawRect(yTagRect, drawLinePaint)
                        }
                        DrawKind.CIRCLE -> {
                            yTagRect.set(min(s.x, e.x), min(s.y, e.y), max(s.x, e.x), max(s.y, e.y))
                            canvas.drawOval(yTagRect, drawLinePaint)
                        }
                        DrawKind.TRIANGLE -> {
                            val mx = (s.x + e.x) / 2f
                            drawTriPath.reset()
                            drawTriPath.moveTo(mx, s.y)
                            drawTriPath.lineTo(s.x, e.y)
                            drawTriPath.lineTo(e.x, e.y)
                            drawTriPath.close()
                            canvas.drawPath(drawTriPath, drawLinePaint)
                        }
                        DrawKind.PRICE_LINE -> {
                            hLine(s.y, contentLeft, contentRight, dashed = true)
                            val label = formatAxisPrice(o.start.price, candles.lastOrNull()?.close ?: o.start.price)
                            val w = drawLabelPaint.measureText(label) + 16f
                            val h = drawLabelPaint.textSize + 10f
                            val rx = (contentRight - w).coerceAtLeast(contentLeft)
                            val ly = (s.y - h / 2f).coerceIn(contentTop, (contentBottom - h).coerceAtLeast(contentTop))
                            yTagRect.set(rx, ly, rx + w, ly + h)
                            canvas.drawRoundRect(yTagRect, 4f, 4f, drawChipPaint)
                            canvas.drawText(label, rx + 8f, ly + h - 7f, drawLabelPaint)
                        }
                    }
                    if (selected) {
                        val r = 20f
                        drawHandleStroke.color = if (o.locked) GraphicsColor.GRAY
                        else GraphicsColor.parseColor("#FFD60A")
                        if (o.kind != DrawKind.H_LINE && o.kind != DrawKind.PRICE_LINE) {
                            canvas.drawCircle(s.x, s.y, r, drawHandleFill)
                            canvas.drawCircle(s.x, s.y, r, drawHandleStroke)
                        }
                        canvas.drawCircle(e.x, e.y, r, drawHandleFill)
                        canvas.drawCircle(e.x, e.y, r, drawHandleStroke)
                    }
                    drawLinePaint.alpha = 255
                }

                private fun drawDrawOverlays(canvas: Canvas, candles: List<CandleData>) {
                    drawsRef.value.forEach { o ->
                        if (!o.hidden) drawSingleDraw(canvas, candles, o, o.id == selectedDrawRef.value)
                    }
                    drawPendingHint(canvas, candles)
                }

                private fun drawPendingHint(canvas: Canvas, candles: List<CandleData>) {
                    val pending = pendingDrawStartRef.value ?: return
                    if (candles.isEmpty()) return
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val pIdx = fibTimeToIndexVirtual(candles, pending.time, stateRef.value.selectedInterval)
                    val p = floatArrayOf(pIdx.toFloat(), pending.price.toFloat())
                    trans.pointValuesToPixel(p)
                    canvas.drawCircle(p[0], p[1], 9f, dotPaint)
                    canvas.drawCircle(p[0], p[1], 9f, dotOutlinePaint)
                }

                private fun drawFibOverlays(canvas: Canvas, candles: List<CandleData>) {
                    val overlays = overlaysRef.value
                    if (overlays.isEmpty() || candles.isEmpty()) {
                        drawFibPendingHint(canvas, candles)
                        return
                    }
                    overlays.forEach { o ->
                        if (!o.hidden) drawSingleFib(canvas, candles, o, o.id == selectedFibRef.value)
                    }
                    drawFibPendingHint(canvas, candles)
                }

                private fun drawSingleFib(canvas: Canvas, candles: List<CandleData>, o: FibOverlay, selected: Boolean) {
                    val levels = o.levelsSorted()
                    if (levels.isEmpty()) return
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()

                    val sIdx = fibTimeToIndexVirtual(candles, o.start.time, stateRef.value.selectedInterval)
                    val eIdx = fibTimeToIndexVirtual(candles, o.end.time, stateRef.value.selectedInterval)
                    val sPts = floatArrayOf(sIdx.toFloat(), o.start.price.toFloat())
                    trans.pointValuesToPixel(sPts)
                    val ePts = floatArrayOf(eIdx.toFloat(), o.end.price.toFloat())
                    trans.pointValuesToPixel(ePts)
                    val leftX = min(sPts[0], ePts[0]).coerceIn(contentLeft, contentRight)
                    // lineas acotadas al rango inicio-fin, no hasta el borde
                    val rightX = max(sPts[0], ePts[0]).coerceIn(contentLeft, contentRight)

                    val lineColor = try {
                        GraphicsColor.parseColor(o.colorHex)
                    } catch (_: Exception) {
                        GraphicsColor.WHITE
                    }
                    fibLinePaint.color = lineColor
                    fibLinePaint.strokeWidth = (o.width * context.resources.displayMetrics.density).coerceAtLeast(1f)
                    // seleccionado a full, los demas tenues
                    fibLinePaint.alpha = if (selected || selectedFibRef.value == null) 255 else 140
                    fibLabelPaint.color = GraphicsColor.WHITE

                    levels.forEach { ratio ->
                        // punto inicial siempre 1, final siempre 0
                        val price = fibLevelPrice(o.end.price, o.start.price, ratio)
                        val pts = floatArrayOf(0f, price.toFloat())
                        trans.pointValuesToPixel(pts)
                        val py = pts[1].coerceIn(contentTop, contentBottom)
                        canvas.drawLine(leftX, py, rightX, py, fibLinePaint)
                        // nivel + precio entre parentesis a la izquierda de la linea, sin fondo
                        if (selected) {
                            val label = "${trimRatio(ratio)}(${formatAxisPrice(price, candles.lastOrNull()?.close ?: price)})"
                            val w = fibLabelPaint.measureText(label)
                            val lx = (leftX - 6f - w).coerceIn(contentLeft, (contentRight - w).coerceAtLeast(contentLeft))
                            canvas.drawText(label, lx, py + fibLabelPaint.textSize * 0.35f, fibLabelPaint)
                        }
                    }

                    // handles solo en el seleccionado, grises si esta candadeado
                    if (selected) {
                        val r = 22f
                        fibHandleStroke.color = if (o.locked) GraphicsColor.GRAY
                        else GraphicsColor.parseColor("#2196F3")
                        canvas.drawCircle(sPts[0], sPts[1], r, fibHandleFill)
                        canvas.drawCircle(sPts[0], sPts[1], r, fibHandleStroke)
                        canvas.drawCircle(ePts[0], ePts[1], r, fibHandleFill)
                        canvas.drawCircle(ePts[0], ePts[1], r, fibHandleStroke)
                    }
                    fibLinePaint.alpha = 255
                }

                private fun drawFibPendingHint(canvas: Canvas, candles: List<CandleData>) {
                    val pending = pendingStartRef.value ?: return
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()
                    val pIdx = fibTimeToIndexVirtual(candles, pending.time, stateRef.value.selectedInterval)
                    val p = floatArrayOf(pIdx.toFloat(), pending.price.toFloat())
                    trans.pointValuesToPixel(p)
                    canvas.drawCircle(p[0], p[1], 9f, dotPaint)
                    canvas.drawCircle(p[0], p[1], 9f, dotOutlinePaint)
                    val label = "Toca fin"
                    val lw2 = selectionTextPaint.measureText(label) + 18f
                    val lh2 = selectionTextPaint.textSize + 12f
                    val ll = (p[0] + 10f).coerceIn(contentLeft + 4f, contentRight - lw2 - 4f)
                    val lt = (p[1] - lh2 - 10f).coerceIn(contentTop + 4f, contentBottom - lh2 - 4f)
                    yTagRect.set(ll, lt, ll + lw2, lt + lh2)
                    canvas.drawRoundRect(yTagRect, 5f, 5f, selectionLabelPaint)
                    canvas.drawText(label, ll + 9f, lt + lh2 - 8f, selectionTextPaint)
                }

                // ─── SMC ───
                // fondo tenue detras de las velas (premium/discount)
                private fun drawSmcBackground(
                    canvas: Canvas,
                    candles: List<CandleData>,
                    visibleStart: Int,
                    visibleEnd: Int
                ) {
                    if (!prefsRef.value.smcPremium) return
                    val range = stateRef.value.smc.premium ?: return
                    if (candles.isEmpty()) return
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    val x0 = floatArrayOf(visibleStart.toFloat(), 0f)
                    trans.pointValuesToPixel(x0)
                    val x1 = floatArrayOf(visibleEnd.toFloat(), 0f)
                    trans.pointValuesToPixel(x1)
                    val leftX = min(x0[0], x1[0]).coerceIn(contentLeft, contentRight)
                    val rightX = max(x0[0], x1[0]).coerceIn(contentLeft, contentRight)
                    val hi = floatArrayOf(0f, range.high.toFloat())
                    trans.pointValuesToPixel(hi)
                    val eq = floatArrayOf(0f, range.equilibrium.toFloat())
                    trans.pointValuesToPixel(eq)
                    val lo = floatArrayOf(0f, range.low.toFloat())
                    trans.pointValuesToPixel(lo)
                    yTagRect.set(leftX, min(hi[1], eq[1]), rightX, max(hi[1], eq[1]))
                    canvas.drawRect(yTagRect, smcPremPaint)
                    yTagRect.set(leftX, min(eq[1], lo[1]), rightX, max(eq[1], lo[1]))
                    canvas.drawRect(yTagRect, smcDiscPaint)
                    canvas.drawLine(leftX, eq[1], rightX, eq[1], smcEqLinePaint)
                }

                // cajas y estructura sobre el chart pero tenues, las velas mandan
                private fun drawSmcForeground(
                    canvas: Canvas,
                    candles: List<CandleData>,
                    visibleStart: Int,
                    visibleEnd: Int
                ) {
                    val smc = stateRef.value.smc
                    if (candles.isEmpty()) return
                    val smcPrefs = prefsRef.value
                    if (!smcPrefs.smcOrderBlocks && !smcPrefs.smcFvg && !smcPrefs.smcStructure && !smcPrefs.smcEqhl && !smcPrefs.smcLiquidity) return
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()

                    if (smcPrefs.smcOrderBlocks || smcPrefs.smcFvg) {
                        smc.zones.forEach { z ->
                            val wantOb = smcPrefs.smcOrderBlocks && z.kind == SmcZoneKind.ORDER_BLOCK
                            val wantFvg = smcPrefs.smcFvg && z.kind == SmcZoneKind.FVG
                            if (!wantOb && !wantFvg) return@forEach
                            if (z.endIdx < visibleStart || z.startIdx > visibleEnd) return@forEach
                            val s = max(z.startIdx, 0)
                            val e = min(z.endIdx, candles.size - 1)
                            if (e <= s) return@forEach
                            val xs = floatArrayOf(s.toFloat(), 0f)
                            trans.pointValuesToPixel(xs)
                            val xe = floatArrayOf(e.toFloat(), 0f)
                            trans.pointValuesToPixel(xe)
                            val top = floatArrayOf(0f, z.top.toFloat())
                            trans.pointValuesToPixel(top)
                            val bot = floatArrayOf(0f, z.bottom.toFloat())
                            trans.pointValuesToPixel(bot)
                            yTagRect.set(
                                min(xs[0], xe[0]).coerceIn(contentLeft, contentRight),
                                min(top[1], bot[1]),
                                max(xs[0], xe[0]).coerceIn(contentLeft, contentRight),
                                max(top[1], bot[1])
                            )
                            val paint = when {
                                z.mitigated -> smcObMitPaint
                                z.kind == SmcZoneKind.ORDER_BLOCK && z.bullish -> smcObBullPaint
                                z.kind == SmcZoneKind.ORDER_BLOCK -> smcObBearPaint
                                z.bullish -> smcFvgBullPaint
                                else -> smcFvgBearPaint
                            }
                            canvas.drawRect(yTagRect, paint)
                            // borde punteado del lado + tag FVG solo si sigue vigente
                            if (!z.mitigated) {
                                val edge = if (z.bullish) smcObBullEdge else smcObBearEdge
                                canvas.drawRect(yTagRect, edge)
                                if (z.kind == SmcZoneKind.FVG) {
                                    canvas.drawText(
                                        "FVG·${stateRef.value.selectedInterval}",
                                        yTagRect.left + 4f,
                                        (yTagRect.top + 20f).coerceIn(contentTop + 16f, contentBottom),
                                        smcFvgTagPaint
                                    )
                                }
                            }
                        }
                    }

                    if (smcPrefs.smcStructure) {
                        smc.events.forEach { ev ->
                            if (ev.breakIdx < visibleStart || ev.swingIdx > visibleEnd) return@forEach
                            val lvl = floatArrayOf(0f, ev.levelPrice.toFloat())
                            trans.pointValuesToPixel(lvl)
                            val py = lvl[1].coerceIn(contentTop, contentBottom)
                            val xs = floatArrayOf(ev.swingIdx.toFloat(), 0f)
                            trans.pointValuesToPixel(xs)
                            val xb = floatArrayOf(ev.breakIdx.toFloat(), 0f)
                            trans.pointValuesToPixel(xb)
                            val linePaint = if (ev.bullish) smcBullLinePaint else smcBearLinePaint
                            val labelPaint = if (ev.bullish) smcBullLabelPaint else smcBearLabelPaint
                            canvas.drawLine(
                                xs[0].coerceIn(contentLeft, contentRight), py,
                                xb[0].coerceIn(contentLeft, contentRight), py, linePaint
                            )
                            val label = if (ev.kind == SmcEventKind.BOS) "BOS" else "CHoCH"
                            drawSmcChip(canvas, label, labelPaint, xs[0], py, ev.bullish, contentLeft, contentRight, contentTop, contentBottom, placeLeft = true)
                        }
                    }

                    if (smcPrefs.smcEqhl) {
                        smc.eqLevels.forEach { eq ->
                            if (eq.idx2 < visibleStart || eq.idx1 > visibleEnd) return@forEach
                            val pts = floatArrayOf(0f, eq.price.toFloat())
                            trans.pointValuesToPixel(pts)
                            val py = pts[1].coerceIn(contentTop, contentBottom)
                            val x1 = floatArrayOf(eq.idx1.toFloat(), 0f)
                            trans.pointValuesToPixel(x1)
                            val x2 = floatArrayOf(eq.idx2.toFloat(), 0f)
                            trans.pointValuesToPixel(x2)
                            canvas.drawLine(
                                x1[0].coerceIn(contentLeft, contentRight), py,
                                x2[0].coerceIn(contentLeft, contentRight), py, smcEqPaint
                            )
                            val label = if (eq.isHigh) "EQH" else "EQL"
                            drawSmcChip(canvas, label, smcEqLabelPaint, x1[0], py, eq.isHigh, contentLeft, contentRight, contentTop, contentBottom, placeLeft = true)
                        }
                    }

                    // pools BSL/SSL + barridos, tags al borde derecho con stagger
                    if (smcPrefs.smcLiquidity) {
                        // SWEEP solo el mas reciente por lado, el resto linea pelada
                        val latestSweepBuy = smc.liquidity.filter { it.isBuySide && it.swept }.maxByOrNull { it.sweepIdx ?: -1 }
                        val latestSweepSell = smc.liquidity.filter { !it.isBuySide && it.swept }.maxByOrNull { it.sweepIdx ?: -1 }
                        var lastTagY = Float.NEGATIVE_INFINITY
                        smc.liquidity.sortedByDescending { it.idx }.forEach { liq ->
                            if (liq.idx > visibleEnd) return@forEach
                            val pts = floatArrayOf(0f, liq.price.toFloat())
                            trans.pointValuesToPixel(pts)
                            val py = pts[1].coerceIn(contentTop, contentBottom)
                            val x0 = floatArrayOf(liq.idx.toFloat(), 0f)
                            trans.pointValuesToPixel(x0)
                            val endIdx = liq.sweepIdx ?: (candles.size - 1)
                            if (endIdx < visibleStart) return@forEach
                            val x1 = floatArrayOf(endIdx.toFloat(), 0f)
                            trans.pointValuesToPixel(x1)
                            val linePaint = when {
                                liq.swept -> smcSweptPaint
                                liq.isBuySide -> smcBslPaint
                                else -> smcSslPaint
                            }
                            canvas.drawLine(
                                x0[0].coerceIn(contentLeft, contentRight), py,
                                x1[0].coerceIn(contentLeft, contentRight), py, linePaint
                            )
                            val showTag = !liq.swept || liq === latestSweepBuy || liq === latestSweepSell
                            if (showTag) {
                                val label = when {
                                    liq.swept -> "SWEEP"
                                    liq.isBuySide -> "BSL"
                                    else -> "SSL"
                                }
                                val labelPaint = when {
                                    liq.swept -> smcSweepLabelPaint
                                    liq.isBuySide -> smcBslLabelPaint
                                    else -> smcSslLabelPaint
                                }
                                // tag al final de su linea, encima, desplazado si choca con otro
                                val w = labelPaint.measureText(label) + 14f
                                val h = labelPaint.textSize + 8f
                                val lineEndX = x1[0].coerceIn(contentLeft, contentRight)
                                val lx = (lineEndX - w - 4f).coerceIn(contentLeft, (contentRight - w).coerceAtLeast(contentLeft))
                                var ly = (py - h - 4f).coerceIn(contentTop, (contentBottom - h).coerceAtLeast(contentTop))
                                if (abs(ly - lastTagY) < h + 4f) {
                                    ly = (lastTagY + h + 4f).coerceAtMost(contentBottom - h)
                                }
                                lastTagY = ly
                                yTagRect.set(lx, ly, lx + w, ly + h)
                                canvas.drawRoundRect(yTagRect, 4f, 4f, smcChipPaint)
                                canvas.drawText(label, lx + 7f, ly + h - 6f, labelPaint)
                            }
                        }
                    }

                    // bandas grises donde el FVG de otro TF pisa tu zona
                    if (smcPrefs.smcFvg) {
                        smc.confluence.forEach { band ->
                            val top = floatArrayOf(0f, band.top.toFloat())
                            trans.pointValuesToPixel(top)
                            val bot = floatArrayOf(0f, band.bottom.toFloat())
                            trans.pointValuesToPixel(bot)
                            val xs = floatArrayOf(band.startIdx.toFloat(), 0f)
                            trans.pointValuesToPixel(xs)
                            val xe = floatArrayOf(band.endIdx.toFloat(), 0f)
                            trans.pointValuesToPixel(xe)
                            if (xe[0] < contentLeft || xs[0] > contentRight) return@forEach
                            yTagRect.set(
                                xs[0].coerceIn(contentLeft, contentRight),
                                min(top[1], bot[1]),
                                xe[0].coerceIn(contentLeft, contentRight),
                                max(top[1], bot[1])
                            )
                            canvas.drawRect(yTagRect, smcConfluencePaint)
                            drawSmcChip(canvas, band.tfLabel, smcConfluenceTagPaint, xs[0], min(top[1], bot[1]), true, contentLeft, contentRight, contentTop, contentBottom)
                        }
                    }
                }

                // chip oscuro para que el tag se lea sobre las velas
                private fun drawSmcChip(
                    canvas: Canvas,
                    label: String,
                    labelPaint: Paint,
                    anchorX: Float,
                    anchorY: Float,
                    above: Boolean,
                    contentLeft: Float,
                    contentRight: Float,
                    contentTop: Float,
                    contentBottom: Float,
                    placeLeft: Boolean = false
                ) {
                    val w = labelPaint.measureText(label) + 4f
                    val h = labelPaint.textSize + 4f
                    val lx = if (placeLeft) {
                        (anchorX - w - 4f).coerceIn(contentLeft, (contentRight - w).coerceAtLeast(contentLeft))
                    } else {
                        (anchorX + 4f).coerceIn(contentLeft, (contentRight - w).coerceAtLeast(contentLeft))
                    }
                    val ly = if (above) {
                        (anchorY - h - 4f).coerceIn(contentTop, (contentBottom - h).coerceAtLeast(contentTop))
                    } else {
                        (anchorY + 4f).coerceIn(contentTop, (contentBottom - h).coerceAtLeast(contentTop))
                    }
                    canvas.drawText(label, lx, ly + h - 4f, labelPaint)
                }

                private fun trimRatio(r: Float): String {
                    val s = String.format(Locale.US, "%.3f", r).trimEnd('0').trimEnd('.')
                    if (s.isEmpty()) return "0"
                    // estilo tradingview: sin cero inicial (.618 en vez de 0.618)
                    return if (s.startsWith("0.")) s.substring(1) else s
                }

                override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
                    if (event == null) return false

                    // fibo primero en cualquier modo (menos Medir); vacio = sigue al flujo normal
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            if (toolRef.value == DrawingTool.MEASURE) {
                                // en Medir manda la cinta, los overlays no interceptan
                            } else {
                                val dh = drawHitTest(event.x, event.y)
                                if (dh != null) {
                                    val (did, dzone, dlocked) = dh
                                    onDrawSelect?.invoke(did)
                                    selectedDrawRef.value = did
                                    selectedFibRef.value = null
                                    downX = event.x
                                    downY = event.y
                                    drawDragMoved = false
                                    if (!dlocked) {
                                        drawDragId = did
                                        drawDragZone = dzone
                                        if (dzone == 3) {
                                            val o = drawsRef.value.firstOrNull { it.id == did }
                                            val candles = stateRef.value.candles
                                            drawOrigStart = o?.start
                                            drawOrigEnd = o?.end
                                            drawGrabIdx = touchToCandleIndex(event.x, event.y, candles.size)
                                                ?: o?.let { fibTimeToIndexVirtual(candles, it.start.time, stateRef.value.selectedInterval) } ?: 0
                                            drawGrabPrice = getTransformer(YAxis.AxisDependency.LEFT)
                                                .getValuesByTouchPoint(event.x, event.y).y.toDouble()
                                        }
                                    }
                                    parent.requestDisallowInterceptTouchEvent(true)
                                    return true
                                }
                                val hit = fibHitTest(event.x, event.y)
                            if (hit != null) {
                                val (id, zone, locked) = hit
                                onFibSelect?.invoke(id)
                                selectedFibRef.value = id
                                selectedDrawRef.value = null
                                downX = event.x
                                downY = event.y
                                fibDragMoved = false
                                if (!locked) {
                                    fibDragId = id
                                    fibDragZone = zone
                                    if (zone == 3) {
                                        val o = overlaysRef.value.firstOrNull { it.id == id }
                                        val candles = stateRef.value.candles
                                        fibOrigStart = o?.start
                                        fibOrigEnd = o?.end
                                        fibGrabIdx = touchToCandleIndex(event.x, event.y, candles.size)
                                            ?: o?.let { fibTimeToIndexVirtual(candles, it.start.time, stateRef.value.selectedInterval) } ?: 0
                                        fibGrabPrice = getTransformer(YAxis.AxisDependency.LEFT)
                                            .getValuesByTouchPoint(event.x, event.y).y.toDouble()
                                    }
                                }
                                parent.requestDisallowInterceptTouchEvent(true)
                                return true
                                }
                            }
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            if (fibDragId != null) {
                                val dx = abs(event.x - downX)
                                val dy = abs(event.y - downY)
                                if (dx > 10f || dy > 10f) fibDragMoved = true
                                if (fibDragMoved) {
                                    val id = fibDragId!!
                                    val cur = overlaysRef.value.firstOrNull { it.id == id }
                                    if (cur != null) {
                                        if (fibDragZone == 3) {
                                            // mover cuerpo conserva tamaño (delta indice+precio)
                                            val candles = stateRef.value.candles
                                            val rawMoveX = getTransformer(YAxis.AxisDependency.LEFT)
                                                .getValuesByTouchPoint(event.x, event.y).x.roundToInt()
                                            val newIdx = rawMoveX.coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                            val newPrice = getTransformer(YAxis.AxisDependency.LEFT)
                                                .getValuesByTouchPoint(event.x, event.y).y.toDouble()
                                            if (fibOrigStart != null && fibOrigEnd != null) {
                                                val dIdx = newIdx - fibGrabIdx
                                                val dPrice = newPrice - fibGrabPrice
                                                val interval = stateRef.value.selectedInterval
                                                val s0 = fibTimeToIndexVirtual(candles, fibOrigStart!!.time, interval)
                                                val e0 = fibTimeToIndexVirtual(candles, fibOrigEnd!!.time, interval)
                                                val s1 = (s0 + dIdx).coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                                val e1 = (e0 + dIdx).coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                                val next = cur.copy(
                                                    start = FibAnchor(fibIndexToTime(candles, s1, interval), fibOrigStart!!.price + dPrice),
                                                    end = FibAnchor(fibIndexToTime(candles, e1, interval), fibOrigEnd!!.price + dPrice)
                                                )
                                                overlaysRef.value = overlaysRef.value.map { if (it.id == id) next else it }
                                                onFibLive?.invoke(id, next)
                                                invalidate()
                                            }
                                        } else {
                                            anchorFromTouch(event.x, event.y)?.let { anchor ->
                                                val next = if (fibDragZone == 1) cur.copy(start = anchor)
                                                else cur.copy(end = anchor)
                                                overlaysRef.value = overlaysRef.value.map { if (it.id == id) next else it }
                                                onFibLive?.invoke(id, next)
                                                invalidate()
                                            }
                                        }
                                    }
                                }
                                return true
                            }
                            if (drawDragId != null) {
                                val dx = abs(event.x - downX)
                                val dy = abs(event.y - downY)
                                if (dx > 10f || dy > 10f) drawDragMoved = true
                                if (drawDragMoved) {
                                    val id = drawDragId!!
                                    val cur = drawsRef.value.firstOrNull { it.id == id }
                                    if (cur != null) {
                                        if (drawDragZone == 3) {
                                            // mover cuerpo conserva tamaño (delta indice+precio)
                                            val candles = stateRef.value.candles
                                            val rawMoveX = getTransformer(YAxis.AxisDependency.LEFT)
                                                .getValuesByTouchPoint(event.x, event.y).x.roundToInt()
                                            val newIdx = rawMoveX.coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                            val newPrice = getTransformer(YAxis.AxisDependency.LEFT)
                                                .getValuesByTouchPoint(event.x, event.y).y.toDouble()
                                            if (drawOrigStart != null && drawOrigEnd != null) {
                                                val dIdx = newIdx - drawGrabIdx
                                                val dPrice = newPrice - drawGrabPrice
                                                val interval = stateRef.value.selectedInterval
                                                val s0 = fibTimeToIndexVirtual(candles, drawOrigStart!!.time, interval)
                                                val e0 = fibTimeToIndexVirtual(candles, drawOrigEnd!!.time, interval)
                                                val s1 = (s0 + dIdx).coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                                val e1 = (e0 + dIdx).coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                                val next = cur.copy(
                                                    start = FibAnchor(fibIndexToTime(candles, s1, interval), drawOrigStart!!.price + dPrice),
                                                    end = FibAnchor(fibIndexToTime(candles, e1, interval), drawOrigEnd!!.price + dPrice)
                                                )
                                                drawsRef.value = drawsRef.value.map { if (it.id == id) next else it }
                                                onDrawLive?.invoke(id, next)
                                                invalidate()
                                            }
                                        } else {
                                            drawAnchorFromTouch(event.x, event.y)?.let { anchor ->
                                                val next = if (drawDragZone == 1) cur.copy(start = anchor)
                                                else cur.copy(end = anchor)
                                                drawsRef.value = drawsRef.value.map { if (it.id == id) next else it }
                                                onDrawLive?.invoke(id, next)
                                                invalidate()
                                            }
                                        }
                                    }
                                }
                                return true
                            }
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            if (fibDragId != null) {
                                if (fibDragMoved) onFibCommit?.invoke(fibDragId!!)
                                else invalidate()
                                fibDragId = null
                                fibDragZone = 0
                                return true
                            }
                            if (drawDragId != null) {
                                if (drawDragMoved) onDrawCommit?.invoke(drawDragId!!)
                                else invalidate()
                                drawDragId = null
                                drawDragZone = 0
                                return true
                            }
                        }
                        android.view.MotionEvent.ACTION_CANCEL -> {
                            fibDragId = null
                            fibDragZone = 0
                            drawDragId = null
                            drawDragZone = 0
                        }
                    }

                    if (toolRef.value == DrawingTool.MEASURE) {
                        parent.requestDisallowInterceptTouchEvent(true)
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                downX = event.x
                                downY = event.y
                                measureDragZone = 0
                                measureDragMoved = false
                                // esquinas redimensionan, dentro mueve la zona
                                val hit = measureHitTest(event.x, event.y)
                                if (hit != 0) {
                                    measureDragZone = hit
                                    measureOrig = rangeSelection.value
                                    val candles = stateRef.value.candles
                                    measureGrabIdx = touchToCandleIndex(event.x, event.y, candles.size) ?: 0
                                    measureGrabPrice = getTransformer(YAxis.AxisDependency.LEFT)
                                        .getValuesByTouchPoint(event.x, event.y).y.toDouble()
                                }
                                return true
                            }
                            android.view.MotionEvent.ACTION_MOVE -> {
                                if (measureDragZone != 0) {
                                    val dx = abs(event.x - downX)
                                    val dy = abs(event.y - downY)
                                    if (dx > 10f || dy > 10f) measureDragMoved = true
                                    if (measureDragMoved) {
                                        val anchor = measureAnchor(event.x, event.y)
                                        val orig = measureOrig
                                        if (anchor != null && orig != null && orig.isComplete) {
                                            rangeSelection.value = if (measureDragZone == 3) {
                                                // mover conserva tamaño
                                                val candles = stateRef.value.candles
                                                val dIdx = anchor.first - measureGrabIdx
                                                val dPrice = anchor.second - measureGrabPrice
                                                val s1 = (orig.startIdx + dIdx).coerceIn(0, candles.size - 1)
                                                val e1 = (orig.endIdx!! + dIdx).coerceIn(0, candles.size - 1)
                                                orig.copy(
                                                    startIdx = s1,
                                                    startPrice = orig.startPrice + dPrice,
                                                    endIdx = e1,
                                                    endPrice = orig.endPrice!! + dPrice
                                                )
                                            } else if (measureDragZone == 1) {
                                                orig.copy(startIdx = anchor.first, startPrice = anchor.second)
                                            } else {
                                                orig.copy(endIdx = anchor.first, endPrice = anchor.second)
                                            }
                                            invalidate()
                                        }
                                    }
                                }
                                return true
                            }
                            android.view.MotionEvent.ACTION_UP -> {
                                if (measureDragZone != 0) {
                                    measureDragZone = 0
                                    measureOrig = null
                                    invalidate()
                                    return true
                                }
                                val dx = abs(event.x - downX)
                                val dy = abs(event.y - downY)
                                if (dx < 14f && dy < 14f) {
                                    performClick()
                                    applyRangeTap(event.x, event.y)
                                }
                                return true
                            }
                            android.view.MotionEvent.ACTION_CANCEL -> {
                                measureDragZone = 0
                                measureOrig = null
                                return true
                            }
                        }
                        return true
                    }

                    // modo colocacion, el tap crea puntos; arrastrar = pan para no atascarse
                    if (toolRef.value == DrawingTool.FIBO || toolRef.value == DrawingTool.DRAW) {
                        parent.requestDisallowInterceptTouchEvent(true)
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                downX = event.x
                                downY = event.y
                                lastGestureX = event.x
                                lastGestureY = event.y
                                return true
                            }
                            android.view.MotionEvent.ACTION_MOVE -> {
                                val dx = event.x - lastGestureX
                                val dy = event.y - lastGestureY
                                if (abs(event.x - downX) > 14f || abs(event.y - downY) > 14f) {
                                    applyPan(dx, dy)
                                    lastGestureX = event.x
                                    lastGestureY = event.y
                                    lastTouchYPx = -1f
                                    invalidate()
                                }
                                return true
                            }
                            android.view.MotionEvent.ACTION_UP -> {
                                val dx = abs(event.x - downX)
                                val dy = abs(event.y - downY)
                                if (dx < 14f && dy < 14f) {
                                    performClick()
                                    if (toolRef.value == DrawingTool.FIBO) applyFibTap(event.x, event.y)
                                    else applyDrawTap(event.x, event.y)
                                }
                                return true
                            }
                            android.view.MotionEvent.ACTION_CANCEL -> return true
                        }
                        return true
                    }

                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                            lastGestureX = event.x
                            lastGestureY = event.y
                            zoomPivotX = matrixPivotX(event.x)
                            zoomPivotY = matrixPivotY(event.y)
                            touchMode = ChartTouchMode.UNDECIDED
                            gestureMoved = false
                            parent.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                        android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                            // con cruz activa se ignora el segundo dedo
                            if (highlighted != null && highlighted.isNotEmpty()) {
                                gestureMoved = true
                                return true
                            }
                            if (event.pointerCount >= 2) {
                                val firstX = event.getX(0)
                                val secondX = event.getX(1)
                                lastPinchSpanX = abs(secondX - firstX).coerceAtLeast(1f)
                                zoomPivotX = matrixPivotX((firstX + secondX) / 2f)
                                zoomPivotY = matrixPivotY((event.getY(0) + event.getY(1)) / 2f)
                                touchMode = ChartTouchMode.PINCH_X_ZOOM
                                gestureMoved = true
                                parent.requestDisallowInterceptTouchEvent(true)
                            }
                            return true
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            if (event.pointerCount >= 2) {
                                // con cruz activa todo el grafico se bloquea, ni pinch
                                if (highlighted != null && highlighted.isNotEmpty()) {
                                    gestureMoved = true
                                    return true
                                }
                                val firstX = event.getX(0)
                                val secondX = event.getX(1)
                                val currentSpanX = abs(secondX - firstX).coerceAtLeast(1f)
                                if (lastPinchSpanX > 0f) {
                                    applyHorizontalPinchZoom(
                                        currentSpanX / lastPinchSpanX,
                                        matrixPivotX((firstX + secondX) / 2f),
                                        matrixPivotY((event.getY(0) + event.getY(1)) / 2f)
                                    )
                                }
                                lastPinchSpanX = currentSpanX
                                gestureMoved = true
                                touchMode = ChartTouchMode.PINCH_X_ZOOM
                                return true
                            }
                            if (event.pointerCount != 1) return true

                            // cruz activa sigue al dedo, pan/zoom bloqueados
                            if (highlighted != null && highlighted.isNotEmpty()) {
                                gestureMoved = true
                                lastTouchYPx = event.y
                                highlightValue(getHighlightByTouchPoint(event.x, event.y), true)
                                syncHighlights(this, stochChartRef.value, rsiChartRef.value)
                                lastGestureX = event.x
                                lastGestureY = event.y
                                invalidate()
                                return true
                            }

                            val totalDx = event.x - downX
                            val totalDy = event.y - downY
                            val threshold = 4f * density
                            if (touchMode == ChartTouchMode.UNDECIDED &&
                                (abs(totalDx) > threshold || abs(totalDy) > threshold)
                            ) {
                                touchMode = when {
                                    isOnYAxis(downX) && abs(totalDy) >= abs(totalDx) -> ChartTouchMode.Y_ZOOM
                                    else -> ChartTouchMode.PAN
                                }
                            }

                            if (touchMode != ChartTouchMode.UNDECIDED) {
                                gestureMoved = true
                                val dx = event.x - lastGestureX
                                val dy = event.y - lastGestureY
                                if (touchMode == ChartTouchMode.PAN) {
                                    applyPan(dx, dy)
                                } else if (touchMode == ChartTouchMode.Y_ZOOM) {
                                    applyYZoom(dy)
                                }
                                lastGestureX = event.x
                                lastGestureY = event.y
                                lastTouchYPx = -1f
                                invalidate()
                                return true
                            }
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            val dx = abs(event.x - downX)
                            val dy = abs(event.y - downY)

                            // Detect a TAP
                            if (!gestureMoved && dx < 10f && dy < 10f) {
                                performClick()
                                // tap al vacio quita el foco: sin seleccion no hay boton flotante
                                if (fibHitTest(event.x, event.y) == null && drawHitTest(event.x, event.y) == null) {
                                    onFibSelect?.invoke(null)
                                    onDrawSelect?.invoke(null)
                                    selectedFibRef.value = null
                                    selectedDrawRef.value = null
                                    invalidate()
                                }
                                if (highlighted != null && highlighted.isNotEmpty()) {
                                    // Toggle OFF
                                    highlightValue(null)
                                    lastTouchYPx = -1f
                                    syncHighlights(this, stochChartRef.value, rsiChartRef.value)
                                } else {
                                    // Toggle ON
                                    lastTouchYPx = event.y
                                    val h = getHighlightByTouchPoint(event.x, event.y)
                                    highlightValue(h, true)
                                }
                                invalidate()
                                touchMode = ChartTouchMode.UNDECIDED
                                lastPinchSpanX = 0f
                                parent.requestDisallowInterceptTouchEvent(false)
                                return true
                            }
                            touchMode = ChartTouchMode.UNDECIDED
                            lastPinchSpanX = 0f
                            parent.requestDisallowInterceptTouchEvent(false)
                            return true
                        }
                        android.view.MotionEvent.ACTION_POINTER_UP -> {
                            if (event.pointerCount >= 2) {
                                val remainingIndex = if (event.actionIndex == 0) 1 else 0
                                downX = event.getX(remainingIndex)
                                downY = event.getY(remainingIndex)
                                lastGestureX = downX
                                lastGestureY = downY
                            }
                            touchMode = ChartTouchMode.UNDECIDED
                            lastPinchSpanX = 0f
                            parent.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                        android.view.MotionEvent.ACTION_CANCEL -> {
                            touchMode = ChartTouchMode.UNDECIDED
                            gestureMoved = false
                            lastPinchSpanX = 0f
                            parent.requestDisallowInterceptTouchEvent(false)
                            return true
                        }
                    }
                    return true
                }

                override fun performClick(): Boolean {
                    super.performClick()
                    return true
                }

                override fun onDraw(canvas: Canvas) {
                    val entries = stateRef.value.candles
                    if (entries.isEmpty()) {
                        super.onDraw(canvas)
                        return
                    }

                    val visibleStart = lowestVisibleX.toInt().coerceIn(0, entries.size - 1)
                    val visibleEnd = highestVisibleX.toInt().coerceIn(0, entries.size - 1)
                    if (visibleStart >= visibleEnd) {
                        super.onDraw(canvas)
                        return
                    }
                    if (prefsRef.value.profileVisible) {
                        drawFixedRangeVolumeProfile(canvas, entries, visibleStart, visibleEnd)
                    }
                    drawVolumeOverlay(canvas, entries, visibleStart, visibleEnd)
                    drawSmcBackground(canvas, entries, visibleStart, visibleEnd)
                    super.onDraw(canvas)

                    var maxIndex = visibleStart
                    var minIndex = visibleStart
                    for (index in visibleStart..visibleEnd) {
                        val entry = entries[index]
                        if (entry.high > entries[maxIndex].high) {
                            maxIndex = index
                        }
                        if (entry.low < entries[minIndex].low) {
                            minIndex = index
                        }
                    }
                    val maxEntry = entries[maxIndex]
                    val minEntry = entries[minIndex]

                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()

                    // --- Draw Max/Min Refined ---
                    // Draw Max Label
                    run {
                        val pts = floatArrayOf(maxIndex.toFloat(), maxEntry.high.toFloat())
                        trans.pointValuesToPixel(pts)
                        val px = pts[0]
                        val py = pts[1]
                        val label = formatPriceForChart(maxEntry.high)
                        val labelWidth = labelPaint.measureText(label)
                        val lineEndX = if (px + labelWidth + 16f < contentRight) px + 20f else px - 20f
                        canvas.drawLine(px, py, lineEndX, py, linePaint)
                        val textX = if (px + labelWidth + 16f < contentRight) lineEndX + 4f else lineEndX - labelWidth - 4f
                        canvas.drawText(label, textX, py + 10f, labelPaint)
                    }

                    // Draw Min Label
                    run {
                        val pts = floatArrayOf(minIndex.toFloat(), minEntry.low.toFloat())
                        trans.pointValuesToPixel(pts)
                        val px = pts[0]
                        val py = pts[1]
                        val label = formatPriceForChart(minEntry.low)
                        val labelWidth = labelPaint.measureText(label)
                        val lineEndX = if (px + labelWidth + 16f < contentRight) px + 20f else px - 20f
                        canvas.drawLine(px, py, lineEndX, py, linePaint)
                        val textX = if (px + labelWidth + 16f < contentRight) lineEndX + 4f else lineEndX - labelWidth - 4f
                        canvas.drawText(label, textX, py + 10f, labelPaint)
                    }

                    drawPriceRangeSelection(canvas, entries)
                    drawFibOverlays(canvas, entries)
                    drawDrawOverlays(canvas, entries)
                    drawSmcForeground(canvas, entries, visibleStart, visibleEnd)
                    drawPinnedYLabels(canvas, entries)
                    drawLastPriceTag(canvas, entries)
                    drawMaTfTags(canvas, entries)

                    // --- Draw Crosshair Lines Manually (Exact Y, Snapped X) ---
                    val h = highlighted?.getOrNull(0) ?: return

                    val xPts = floatArrayOf(h.x, h.y)
                    trans.pointValuesToPixel(xPts)
                    val px = xPts[0]
                    val snappedPy = xPts[1]

                    val py = if (lastTouchYPx >= 0) lastTouchYPx.coerceIn(contentTop, contentBottom) else snappedPy

                    // Vertical Line
                    canvas.drawLine(px, contentTop, px, contentBottom, linePaint)
                    // Horizontal Line
                    canvas.drawLine(contentLeft, py, contentRight, py, linePaint)
                    // Intersection Dot
                    canvas.drawCircle(px, py, 6f, dotPaint)
                    canvas.drawCircle(px, py, 6f, dotOutlinePaint)

                    // Y-Axis Tag (Price + %) - Use Exact Y
                    val priceAtTouch = trans.getValuesByTouchPoint(0f, py).y
                    val currentPrice = stateRef.value.detail?.price?.toDoubleOrNull() ?: entries.lastOrNull()?.close ?: 0.0
                    val pctFromCurrent = if (currentPrice != 0.0) (priceAtTouch - currentPrice) / currentPrice * 100 else 0.0

                    // mismos decimales que el tag de precio actual
                    val refClose = entries.lastOrNull()?.close ?: currentPrice
                    val priceText = formatAxisPrice(priceAtTouch, refClose)
                    val pctText = String.format(Locale.US, "%+.2f%%", pctFromCurrent)

                    val twPrice = tagTextPaint.measureText(priceText)
                    val twPct = tagTextPaint.measureText(pctText)
                    val maxWidth = maxOf(twPrice, twPct)
                    val thY = tagTextPaint.textSize
                    val lineHeight = thY + 8f

                    val tagRight = contentRight + maxWidth + 20f
                    val chartWidth = width.toFloat()
                    val offsetRight = if (tagRight > chartWidth) tagRight - chartWidth + 4f else 0f

                    val tagGap = 6f
                    yTagRect.set(
                        contentRight - offsetRight,
                        py + tagGap,
                        contentRight + maxWidth + 20f - offsetRight,
                        py + tagGap + (lineHeight * 2) + 4f
                    )
                    canvas.drawRoundRect(yTagRect, 4f, 4f, tagBackgroundPaint)
                    canvas.drawText(priceText, yTagRect.centerX(), py + tagGap + thY + 2f, tagTextPaint)
                    canvas.drawText(pctText, yTagRect.centerX(), py + tagGap + (thY * 2) + 10f, tagTextPaint)

                    // X-Axis Tag (Date)
                    val dateText = xAxis.valueFormatter.getFormattedValue(h.x).replace("|", " ")
                    val twX = tagTextPaint.measureText(dateText)
                    xTagRect.set(px - twX/2 - 10f, contentBottom, px + twX/2 + 10f, contentBottom + thY + 16f)
                    canvas.drawRoundRect(xTagRect, 4f, 4f, tagBackgroundPaint)
                    canvas.drawText(dateText, xTagRect.centerX(), xTagRect.centerY() + thY/3, tagTextPaint)
                }
            }.apply {
                setupCommonChartParams()
                // eje X en 2 lineas (fecha|hora) + aire abajo
                setXAxisRenderer(TwoLineXAxisRenderer(viewPortHandler, xAxis, getTransformer(YAxis.AxisDependency.LEFT)))
                extraBottomOffset = 18f
                marker = OKXChartMarker(context) { stateRef.value }
                // etiquetas del canvas con Geist semibold, numeros mas definidos
                try {
                    androidx.core.content.res.ResourcesCompat.getFont(
                        context, com.defitracker.app.R.font.geist_semibold
                    )?.let { tf ->
                        labelPaint.typeface = tf
                        selectionTextPaint.typeface = tf
                        tagTextPaint.typeface = tf
                        lastPriceTextPaint.typeface = tf
                        fibLabelPaint.typeface = tf
                        smcBullLabelPaint.typeface = tf
                        smcBearLabelPaint.typeface = tf
                        smcEqLabelPaint.typeface = tf
                        smcFvgTagPaint.typeface = tf
                        smcConfluenceTagPaint.typeface = tf
                        smcBslLabelPaint.typeface = tf
                        smcSslLabelPaint.typeface = tf
                        smcSweepLabelPaint.typeface = tf
                        maTagTextPaint.typeface = tf
                    }
                } catch (_: Exception) {}

                // eje a la derecha SOBRE el grafico, las velas pasan por detras
                // labels nativos off, se dibujan fijos a pantalla (drawPinnedYLabels)
                axisRight.apply {
                    isEnabled = true
                    setDrawLabels(false)
                    setDrawGridLines(false)
                    setDrawAxisLine(false)
                    setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
                    setLabelCount(6, false)
                    textColor = "#ADB1B8".toColorInt()
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return formatAxisPrice(
                                value.toDouble(),
                                stateRef.value.candles.lastOrNull()?.close ?: 0.0
                            )
                        }
                    }
                }

                axisLeft.apply {
                    setDrawLabels(false)
                    setDrawGridLines(true)
                    gridColor = "#1A1D23".toColorInt()
                    setDrawAxisLine(false)
                    setLabelCount(6, false)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return formatPriceForChart(value.toDouble())
                        }
                    }
                }

                setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
                    override fun onValueSelected(e: Entry?, h: Highlight?) {
                        syncHighlights(this@apply, stochChartRef.value, rsiChartRef.value)
                    }
                    override fun onNothingSelected() {
                        syncHighlights(this@apply, stochChartRef.value, rsiChartRef.value)
                    }
                })

                onChartGestureListener = object : OnChartGestureListener {
                    override fun onChartGestureStart(me: android.view.MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
                    override fun onChartGestureEnd(me: android.view.MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
                    override fun onChartLongPressed(me: android.view.MotionEvent?) {}
                    override fun onChartDoubleTapped(me: android.view.MotionEvent?) {}
                    override fun onChartSingleTapped(me: android.view.MotionEvent?) {}
                    override fun onChartFling(me1: android.view.MotionEvent?, me2: android.view.MotionEvent?, velocityX: Float, velocityY: Float) {}

                    override fun onChartScale(me: android.view.MotionEvent?, scaleX: Float, scaleY: Float) {
                        syncSubCharts(this@apply, stochChartRef.value, rsiChartRef.value)
                    }
                    override fun onChartTranslate(me: android.view.MotionEvent?, dX: Float, dY: Float) {
                        syncSubCharts(this@apply, stochChartRef.value, rsiChartRef.value)
                    }
                }

                priceChartRef.value = this
                // al completar el trazo se sale del modo colocacion solo
                onFibLive = { id, next ->
                    viewModel.setFibLive(id, next)
                    overlaysRef.value = overlaysRef.value.map { if (it.id == id) next else it }
                }
                onFibCommit = { id ->
                    viewModel.commitFib(id)
                }
                onFibSelect = { id ->
                    viewModel.selectFib(id)
                    selectedFibRef.value = id
                }
                onDrawLive = { id, next ->
                    viewModel.setDrawLive(id, next)
                    drawsRef.value = drawsRef.value.map { if (it.id == id) next else it }
                }
                onDrawCommit = { id ->
                    viewModel.commitDraw(id)
                }
                onDrawSelect = { id ->
                    viewModel.selectDraw(id)
                    selectedDrawRef.value = id
                }
                onDrawTap = { anchor ->
                    val kind = drawKindRef.value ?: DrawKind.SEGMENT
                    if (kind == DrawKind.PRICE_LINE) {
                        viewModel.addDraw(kind, anchor, anchor)?.let { created ->
                            drawsRef.value = viewModel.drawOverlays.value
                            selectedDrawRef.value = created.id
                        }
                        drawingTool.value = DrawingTool.NONE
                        toolRef.value = DrawingTool.NONE
                    } else {
                        val pending = pendingDrawStartRef.value
                        if (pending == null) {
                            drawPendingStart.value = anchor
                            pendingDrawStartRef.value = anchor
                        } else {
                            viewModel.addDraw(kind, FibAnchor(pending.time, pending.price), anchor)?.let { created ->
                                drawsRef.value = viewModel.drawOverlays.value
                                selectedDrawRef.value = created.id
                            }
                            drawPendingStart.value = null
                            pendingDrawStartRef.value = null
                            drawingTool.value = DrawingTool.NONE
                            toolRef.value = DrawingTool.NONE
                        }
                    }
                    invalidate()
                }
                onFibTap = { anchor ->
                    val pending = pendingStartRef.value
                    if (pending == null) {
                        fibPendingStart.value = anchor
                        pendingStartRef.value = anchor
                    } else {
                        viewModel.addFib(FibAnchor(pending.time, pending.price), anchor)?.let { created ->
                            overlaysRef.value = viewModel.fibOverlays.value
                            selectedFibRef.value = created.id
                        }
                        fibPendingStart.value = null
                        pendingStartRef.value = null
                        drawingTool.value = DrawingTool.NONE
                        toolRef.value = DrawingTool.NONE
                    }
                    invalidate()
                }
            }
        },
        update = { chart ->
            stateRef.value = state
            prefsRef.value = prefs
            overlaysRef.value = viewModel.fibOverlays.value
            selectedFibRef.value = viewModel.selectedFibId.value
            drawsRef.value = viewModel.drawOverlays.value
            selectedDrawRef.value = viewModel.selectedDrawId.value
            chart.onFibLive = { id, next ->
                viewModel.setFibLive(id, next)
                overlaysRef.value = overlaysRef.value.map { if (it.id == id) next else it }
            }
            chart.onFibCommit = { id ->
                viewModel.commitFib(id)
            }
            chart.onFibSelect = { id ->
                viewModel.selectFib(id)
                selectedFibRef.value = id
            }
            chart.onDrawLive = { id, next ->
                viewModel.setDrawLive(id, next)
                drawsRef.value = drawsRef.value.map { if (it.id == id) next else it }
            }
            chart.onDrawCommit = { id ->
                viewModel.commitDraw(id)
            }
            chart.onDrawSelect = { id ->
                viewModel.selectDraw(id)
                selectedDrawRef.value = id
            }
            chart.onDrawTap = { anchor ->
                val kind = drawKindRef.value ?: DrawKind.SEGMENT
                if (kind == DrawKind.PRICE_LINE) {
                    viewModel.addDraw(kind, anchor, anchor)?.let { created ->
                        drawsRef.value = viewModel.drawOverlays.value
                        selectedDrawRef.value = created.id
                    }
                    drawingTool.value = DrawingTool.NONE
                    toolRef.value = DrawingTool.NONE
                } else {
                    val pending = pendingDrawStartRef.value
                    if (pending == null) {
                        drawPendingStart.value = anchor
                        pendingDrawStartRef.value = anchor
                    } else {
                        viewModel.addDraw(kind, FibAnchor(pending.time, pending.price), anchor)?.let { created ->
                            drawsRef.value = viewModel.drawOverlays.value
                            selectedDrawRef.value = created.id
                        }
                        drawPendingStart.value = null
                        pendingDrawStartRef.value = null
                        drawingTool.value = DrawingTool.NONE
                        toolRef.value = DrawingTool.NONE
                    }
                }
                chart.invalidate()
            }
            chart.onFibTap = { anchor ->
                val pending = pendingStartRef.value
                if (pending == null) {
                    fibPendingStart.value = anchor
                    pendingStartRef.value = anchor
                } else {
                    viewModel.addFib(FibAnchor(pending.time, pending.price), anchor)?.let { created ->
                        overlaysRef.value = viewModel.fibOverlays.value
                        selectedFibRef.value = created.id
                    }
                    fibPendingStart.value = null
                    pendingStartRef.value = null
                    drawingTool.value = DrawingTool.NONE
                    toolRef.value = DrawingTool.NONE
                }
                chart.invalidate()
            }
            val viewportKey = state.viewportKey()
            val isNewDataset = lastRenderedDataKey.value != viewportKey
            val prefsKey = prefs.prefsKey()
            val rebuildData = isNewDataset || lastPrefsKey.value != prefsKey

            chart.xAxis.applyTimeAxis(state)

            if (rebuildData) {
                val candleEntries = ArrayList<CandleEntry>(state.candles.size)
                state.candles.forEachIndexed { index, candle ->
                    candleEntries.add(
                        CandleEntry(
                            index.toFloat(),
                            candle.high.toFloat(),
                            candle.low.toFloat(),
                            candle.open.toFloat(),
                            candle.close.toFloat()
                        )
                    )
                }
                val combinedData = CombinedData()
                if (prefs.bbVisible && state.bbUpper.isNotEmpty()) {
                    val upperEntries = ArrayList<Entry>(state.bbUpper.size)
                    state.bbUpper.forEachIndexed { index, value ->
                        val offsetIndex = index + (state.candles.size - state.bbUpper.size)
                        upperEntries.add(Entry(offsetIndex.toFloat(), value.second.toFloat()))
                    }
                    val middleEntries = ArrayList<Entry>(state.bbMiddle.size)
                    state.bbMiddle.forEachIndexed { index, value ->
                        val offsetIndex = index + (state.candles.size - state.bbMiddle.size)
                        middleEntries.add(Entry(offsetIndex.toFloat(), value.second.toFloat()))
                    }
                    val lowerEntries = ArrayList<Entry>(state.bbLower.size)
                    state.bbLower.forEachIndexed { index, value ->
                        val offsetIndex = index + (state.candles.size - state.bbLower.size)
                        lowerEntries.add(Entry(offsetIndex.toFloat(), value.second.toFloat()))
                    }
                    val bbColor = GraphicsColor.parseColor("#FF9800")
                    val middleColor = GraphicsColor.parseColor("#E91E63")
                    val areaEntries = ArrayList<CandleEntry>(state.bbUpper.size)
                    state.bbUpper.forEachIndexed { index, value ->
                        val offsetIndex = index + (state.candles.size - state.bbUpper.size)
                        val lower = state.bbLower.getOrNull(index)?.second ?: value.second
                        areaEntries.add(
                            CandleEntry(
                                offsetIndex.toFloat(),
                                value.second.toFloat(),
                                lower.toFloat(),
                                lower.toFloat(),
                                value.second.toFloat()
                            )
                        )
                    }
                    val areaDs = CandleDataSet(areaEntries, "BBArea").apply {
                        axisDependency = YAxis.AxisDependency.RIGHT
                        setDrawValues(false)
                        shadowWidth = 0f
                        barSpace = 0f
                        val fillColor = GraphicsColor.argb(15, 255, 152, 0)
                        decreasingColor = fillColor
                        increasingColor = fillColor
                        decreasingPaintStyle = Paint.Style.FILL
                        increasingPaintStyle = Paint.Style.FILL
                        isHighlightEnabled = false
                        setDrawHorizontalHighlightIndicator(false)
                        setDrawVerticalHighlightIndicator(false)
                    }
                    val candleDataSet = createCandleDataSet(candleEntries)
                    val candleData = CandleData(candleDataSet)
                    candleData.addDataSet(areaDs)
                    combinedData.setData(candleData)
                    val lineData = LineData()
                    lineData.addDataSet(createBBLineDataSet(upperEntries, "Upper", bbColor, axisDependency = YAxis.AxisDependency.RIGHT))
                    lineData.addDataSet(createBBLineDataSet(lowerEntries, "Lower", bbColor, axisDependency = YAxis.AxisDependency.RIGHT))
                    lineData.addDataSet(createBBLineDataSet(middleEntries, "Middle", middleColor, 1.2f, axisDependency = YAxis.AxisDependency.RIGHT))
                    prefs.mas.filter { it.visible }.forEach { ma ->
                        maLineDataSet(ma, state.maLines[ma.id] ?: emptyList())?.let {
                            lineData.addDataSet(it)
                        }
                    }
                    combinedData.setData(lineData)
                } else {
                    combinedData.setData(CandleData(createCandleDataSet(candleEntries)))
                    val maOnly = LineData()
                    prefs.mas.filter { it.visible }.forEach { ma ->
                        maLineDataSet(ma, state.maLines[ma.id] ?: emptyList())?.let {
                            maOnly.addDataSet(it)
                        }
                    }
                    if (maOnly.dataSetCount > 0) {
                        combinedData.setData(maOnly)
                    }
                }
                chart.data = combinedData
                // Rebuild CombinedChart renderer buffers after replacing the
                // CandleData/LineData objects from Compose.
                // tu zoom/posicion mandan; solo TF nuevo o primera carga recentran
                val keepZoom = lastCandleCount.value > 0 &&
                    state.selectedInterval == lastCandleInterval.value
                val delta = state.candles.size - lastCandleCount.value
                val savedMatrix = if (keepZoom) Matrix(chart.viewPortHandler.matrixTouch) else null
                val wasAtRight = keepZoom && delta > 0 &&
                    chart.highestVisibleX >= (lastCandleCount.value - 1f) - 1f
                chart.notifyDataSetChanged()
                chart.invalidate()
                if (keepZoom && savedMatrix != null) {
                    if (wasAtRight) {
                        // siguiendo el vivo, corre a la izquierda las que entraron
                        val vals = FloatArray(9)
                        savedMatrix.getValues(vals)
                        vals[Matrix.MTRANS_X] = vals[Matrix.MTRANS_X] - delta * vals[Matrix.MSCALE_X]
                        val shifted = Matrix()
                        shifted.setValues(vals)
                        chart.viewPortHandler.matrixTouch.set(shifted)
                    } else {
                        chart.viewPortHandler.matrixTouch.set(savedMatrix)
                    }
                    chart.invalidate()
                    syncSubCharts(chart, stochChartRef.value, rsiChartRef.value)
                    syncHighlights(chart, stochChartRef.value, rsiChartRef.value)
                } else if (isNewDataset) {
                    // primera carga o TF nuevo: Y solo con velas, las MAs no mandan
                    chart.applySyncAndInitialZoom(state.candles, resetViewport = true, resetCustomY = false, fitYToCandles = true, onPositioned = {
                        syncSubCharts(chart, stochChartRef.value, rsiChartRef.value)
                    })
                    syncHighlights(chart, stochChartRef.value, rsiChartRef.value)
                }
            } else if (state.candles.isNotEmpty()) {
                updateLastCandleInPlace(chart, state)
                updateLastMAInPlace(chart, state)
            }
            if (rebuildData) {
                lastRenderedDataKey.value = viewportKey
                lastPrefsKey.value = prefsKey
                lastCandleCount.value = state.candles.size
                lastCandleInterval.value = state.selectedInterval
            }
        }
    )

        // una sola fila arriba (leyendas que envuelven + botones) sin pisar el eje
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = 4.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // pill oscuro pa' que las MAs no se pierdan sobre las velas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .background(Color(0xB3000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                prefs.mas.filter { it.visible }.forEach { ma ->
                    val last = state.maLines[ma.id]?.lastOrNull()?.second
                    if (last != null) {
                        Text(
                            text = "${maLegendLabel(ma)} ${formatAxisPrice(last, state.candles.lastOrNull()?.close ?: last)}  ",
                            color = Color(ma.colorHex.toColorInt()),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Geist
                        )
                    }
                }
            }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .background(
                        color = Color(0xCC1A1D23),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onOpenIndicators() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Indicadores",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // un solo boton Dibujo (pincel), adentro se elige Medir o Fibo
            val dibujoActive = drawingTool.value != DrawingTool.NONE
            Box(
                modifier = Modifier
                    .background(
                        color = if (dibujoActive) Color(0xCC1ECB81) else Color(0xCC1A1D23),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { showDrawingSheet.value = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Brush,
                    contentDescription = "Dibujo",
                    tint = if (dibujoActive) Color.Black else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            }
        }

        // barra del fibo seleccionado, visible aunque no estes en modo dibujo
        val selectedFib = fibOverlays.firstOrNull { it.id == selectedFibId }
        if (selectedFib != null) {
            OverlayEditBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 44.dp),
                selectionKey = selectedFib.id,
                colorHex = selectedFib.colorHex,
                width = selectedFib.width,
                hidden = selectedFib.hidden,
                locked = selectedFib.locked,
                showLevels = true,
                onColor = { viewModel.setFibColor(selectedFib.id, it) },
                onWidth = { viewModel.setFibWidth(selectedFib.id, it) },
                onLevels = { showFibLevels.value = true },
                onToggleHide = {
                    viewModel.toggleFibHidden(selectedFib.id)
                    priceChartRef.value?.invalidate()
                },
                onToggleLock = {
                    viewModel.toggleFibLocked(selectedFib.id)
                    priceChartRef.value?.invalidate()
                },
                onDelete = {
                    viewModel.deleteFib(selectedFib.id)
                    overlaysRef.value = viewModel.fibOverlays.value
                    selectedFibRef.value = viewModel.selectedFibId.value
                    fibPendingStart.value = null
                    pendingStartRef.value = null
                    priceChartRef.value?.invalidate()
                }
            )
        }

        // barra del dibujo seleccionado, sin niveles
        val selectedDraw = drawOverlays.firstOrNull { it.id == selectedDrawId }
        if (selectedDraw != null) {
            OverlayEditBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 44.dp),
                selectionKey = selectedDraw.id,
                colorHex = selectedDraw.colorHex,
                width = selectedDraw.width,
                hidden = selectedDraw.hidden,
                locked = selectedDraw.locked,
                showLevels = false,
                onColor = {
                    viewModel.setDrawColor(selectedDraw.id, it)
                    priceChartRef.value?.invalidate()
                },
                onWidth = {
                    viewModel.setDrawWidth(selectedDraw.id, it)
                    priceChartRef.value?.invalidate()
                },
                onLevels = {},
                onToggleHide = {
                    viewModel.toggleDrawHidden(selectedDraw.id)
                    priceChartRef.value?.invalidate()
                },
                onToggleLock = {
                    viewModel.toggleDrawLocked(selectedDraw.id)
                    priceChartRef.value?.invalidate()
                },
                onDelete = {
                    viewModel.deleteDraw(selectedDraw.id)
                    drawsRef.value = viewModel.drawOverlays.value
                    selectedDrawRef.value = viewModel.selectedDrawId.value
                    drawPendingStart.value = null
                    pendingDrawStartRef.value = null
                    priceChartRef.value?.invalidate()
                }
            )
        }

        if (showDrawingSheet.value) {
            ModalBottomSheet(
                onDismissRequest = { showDrawingSheet.value = false },
                containerColor = SheetBg
            ) {
                DrawingToolsSheet(
                    active = drawingTool.value,
                    drawKind = drawKindRef.value,
                    magnetOn = magnetRef.value,
                    fibCount = fibOverlays.size + drawOverlays.size,
                    // ocultar/eliminar funcionan con seleccion o con todos
                    hasFib = fibOverlays.isNotEmpty() || drawOverlays.isNotEmpty(),
                    onPickMeasure = {
                        drawingTool.value = if (drawingTool.value == DrawingTool.MEASURE) DrawingTool.NONE else DrawingTool.MEASURE
                        rangeSelection.value = null
                        fibPendingStart.value = null
                        pendingStartRef.value = null
                        drawPendingStart.value = null
                        pendingDrawStartRef.value = null
                        priceChartRef.value?.apply {
                            highlightValue(null)
                            syncHighlights(this, stochChartRef.value, rsiChartRef.value)
                            invalidate()
                        }
                        showDrawingSheet.value = false
                    },
                    onPickFibo = {
                        // entrar a Fibo siempre arma uno nuevo, aunque ya existan
                        drawingTool.value = DrawingTool.FIBO
                        fibPendingStart.value = null
                        pendingStartRef.value = null
                        showDrawingSheet.value = false
                        priceChartRef.value?.invalidate()
                    },
                    onPickDraw = { kind ->
                        drawingTool.value = DrawingTool.DRAW
                        drawKindRef.value = kind
                        drawPendingStart.value = null
                        pendingDrawStartRef.value = null
                        showDrawingSheet.value = false
                        priceChartRef.value?.invalidate()
                    },
                    onToggleMagnet = {
                        magnetRef.value = !magnetRef.value
                    },
                    onHideSelected = {
                        // con seleccion oculta esa, sin seleccion alterna todos
                        if (selectedDraw != null) viewModel.toggleDrawHidden(selectedDraw.id)
                        else if (selectedFib != null) viewModel.toggleFibHidden(selectedFib.id)
                        else {
                            if (drawOverlays.any { !it.hidden }) viewModel.toggleAllDrawsHidden()
                            else if (fibOverlays.any { !it.hidden }) viewModel.toggleAllFibsHidden()
                            else {
                                viewModel.toggleAllDrawsHidden()
                                viewModel.toggleAllFibsHidden()
                            }
                            drawsRef.value = viewModel.drawOverlays.value
                            overlaysRef.value = viewModel.fibOverlays.value
                        }
                        showDrawingSheet.value = false
                        priceChartRef.value?.invalidate()
                    },
                    onDeleteSelected = {
                        // con seleccion borra esa, sin seleccion pide borrar todos
                        if (selectedDraw != null) {
                            viewModel.deleteDraw(selectedDraw.id)
                            drawsRef.value = viewModel.drawOverlays.value
                            selectedDrawRef.value = viewModel.selectedDrawId.value
                            fibPendingStart.value = null
                            pendingStartRef.value = null
                            drawPendingStart.value = null
                            pendingDrawStartRef.value = null
                            showDrawingSheet.value = false
                            priceChartRef.value?.invalidate()
                        } else if (selectedFib != null) {
                            viewModel.deleteFib(selectedFib.id)
                            overlaysRef.value = viewModel.fibOverlays.value
                            selectedFibRef.value = viewModel.selectedFibId.value
                            fibPendingStart.value = null
                            pendingStartRef.value = null
                            drawPendingStart.value = null
                            pendingDrawStartRef.value = null
                            showDrawingSheet.value = false
                            priceChartRef.value?.invalidate()
                        } else {
                            showDeleteAllDrawings.value = true
                        }
                    }
                )
            }
        }

        // borrar todos los trazados con confirmacion
        if (showDeleteAllDrawings.value) {
            AlertDialog(
                onDismissRequest = { showDeleteAllDrawings.value = false },
                containerColor = SheetBg,
                titleContentColor = Color.White,
                textContentColor = Color.Gray,
                title = { Text("Eliminar trazados") },
                text = { Text("¿Borrar los ${fibOverlays.size + drawOverlays.size} trazados de este símbolo?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteAllFibs()
                        viewModel.deleteAllDraws()
                        overlaysRef.value = emptyList()
                        drawsRef.value = emptyList()
                        selectedFibRef.value = null
                        selectedDrawRef.value = null
                        fibPendingStart.value = null
                        pendingStartRef.value = null
                        drawPendingStart.value = null
                        pendingDrawStartRef.value = null
                        showDeleteAllDrawings.value = false
                        showDrawingSheet.value = false
                        priceChartRef.value?.invalidate()
                    }) { Text("Eliminar", color = Color(0xFFF6465D)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllDrawings.value = false }) { Text("Cancelar", color = Color.White) }
                }
            )
        }

        if (showFibLevels.value && selectedFib != null) {
            ModalBottomSheet(
                onDismissRequest = { showFibLevels.value = false },
                containerColor = SheetBg
            ) {
                FibLevelsSheet(
                    fib = selectedFib,
                    onToggle = {
                        viewModel.toggleFibLevel(selectedFib.id, it)
                        priceChartRef.value?.invalidate()
                    },
                    onColor = { viewModel.setFibColor(selectedFib.id, it) },
                    onWidth = {
                        viewModel.setFibWidth(selectedFib.id, it)
                        priceChartRef.value?.invalidate()
                    }
                )
            }
        }

        // expandir a lo OKX, esquina inferior izquierda del grafico
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 28.dp)
                .background(Color(0xCC1A1D23), CircleShape)
                .clickable { chartExpanded.value = !chartExpanded.value }
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (chartExpanded.value) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                contentDescription = "Expandir",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // boton fantasma del pulso, esquina inferior derecha sin tapar precio ni volumen
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 28.dp)
                .background(Color(0x66000000), CircleShape)
                .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                .clickable {
                    viewModel.refreshAnalysis(force = true)
                    showAnalysis.value = true
                }
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = "Pulso del momento",
                tint = Color(0xFF1ECB81),
                modifier = Modifier.size(20.dp)
            )
        }

        // sheet de analisis del momento
        if (showAnalysis.value) {
            ModalBottomSheet(
                onDismissRequest = { showAnalysis.value = false },
                containerColor = SheetBg
            ) {
                AnalysisSheet(
                    analysis = viewModel.analysis.value,
                    onRefresh = { viewModel.refreshAnalysis(force = true) }
                )
            }
        }
    }
}

// ─── STOCHRSI CHART ──────────────────────────────────────────────────────────
@Composable
fun StochRSIChart(
    state: CryptoDetailState,
    chartRef: MutableState<LineChart?>,
    priceChartRef: MutableState<CombinedChart?> = mutableStateOf(null)
) {
    val stateRef = remember { mutableStateOf(state) }
    val lastRenderedDataKey = remember { mutableStateOf<String?>(null) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            object : LineChart(context) {
                private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(220, 255, 255, 255)
                    strokeWidth = 1.5f
                    pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
                }
                private val kPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor("#FF9800")
                    textSize = context.resources.displayMetrics.density * 10f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val dPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor("#E91E63")
                    textSize = context.resources.displayMetrics.density * 10f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val h = highlighted?.getOrNull(0)
                    val currentState = stateRef.value

                    val density = context.resources.displayMetrics.density
                    val textX = viewPortHandler.contentLeft() + (density * 5f)
                    val textY = viewPortHandler.contentTop() + (density * 14f)

                    var kVal = 0.0
                    var dVal = 0.0
                    var hasVal = false

                    if (h != null) {
                        val xPts = floatArrayOf(h.x, 0f)
                        getTransformer(YAxis.AxisDependency.LEFT).pointValuesToPixel(xPts)
                        val px = xPts[0]
                        canvas.drawLine(px, viewPortHandler.contentTop(), px, viewPortHandler.contentBottom(), linePaint)

                        val idx = h.x.toInt()
                        val k = currentState.stochK.getValueAtCandleIndex(idx)
                        val d = currentState.stochD.getValueAtCandleIndex(idx)
                        if (k != null && d != null) {
                            kVal = k
                            dVal = d
                            hasVal = true
                        }
                    } else {
                        val lastK = currentState.stochK.lastOrNull()
                        val lastD = currentState.stochD.lastOrNull()
                        if (lastK != null && lastD != null) {
                            kVal = lastK.second
                            dVal = lastD.second
                            hasVal = true
                        }
                    }

                    if (hasVal) {
                        val kText = "K: ${String.format(Locale.US, "%.2f", kVal)}  "
                        canvas.drawText(kText, textX, textY, kPaint)
                        val kWidth = kPaint.measureText(kText)
                        val dText = "D: ${String.format(Locale.US, "%.2f", dVal)}"
                        canvas.drawText(dText, textX + kWidth, textY, dPaint)
                    }
                }
            }.apply {
                setupCommonChartParams()
                setTouchEnabled(false)
                isDragEnabled = false
                setScaleEnabled(true)
                isScaleYEnabled = false
                setPinchZoom(false)
                axisLeft.apply {
                    axisMinimum = 0f
                    axisMaximum = 100f
                    setLabelCount(3, true)
                    setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
                }
                chartRef.value = this
            }
        },
        update = { chart ->
            stateRef.value = state
            val viewportKey = state.viewportKey()
            val isNewDataset = lastRenderedDataKey.value != viewportKey

            if (isNewDataset) {
                val lineData = LineData()
                if (state.stochK.isNotEmpty()) {
                    val kEntries = ArrayList<Entry>(state.stochK.size)
                    state.stochK.forEach { value ->
                        kEntries.add(Entry(value.first.toFloat(), value.second.toFloat()))
                    }
                    val dEntries = ArrayList<Entry>(state.stochD.size)
                    state.stochD.forEach { value ->
                        dEntries.add(Entry(value.first.toFloat(), value.second.toFloat()))
                    }
                    lineData.addDataSet(createBBLineDataSet(kEntries, "K", GraphicsColor.parseColor("#FF9800"), 1f, highlight = true))
                    lineData.addDataSet(createBBLineDataSet(dEntries, "D", GraphicsColor.parseColor("#E91E63"), 1f, highlight = true))
                }
                chart.data = lineData
            } else if (state.stochK.isNotEmpty()) {
                updateLastStochInPlace(chart, state)
            }

            chart.xAxis.applyTimeAxis(state)
            chart.xAxis.setDrawLabels(false)
            chart.applySyncAndInitialZoom(state.candles, resetViewport = isNewDataset, skipPositioning = isNewDataset)
            if (isNewDataset) {
                // al montar, hereda el rango visible del principal
                priceChartRef.value?.let { chart.syncViewportFrom(it) }
                lastRenderedDataKey.value = viewportKey
            }
        }
    )
}

@Composable
fun RsiChart(
    state: CryptoDetailState,
    chartRef: MutableState<LineChart?>,
    priceChartRef: MutableState<CombinedChart?> = mutableStateOf(null),
    prefs: IndicatorPrefs = IndicatorPrefs.DEFAULT
) {
    val stateRef = remember { mutableStateOf(state) }
    val prefsRef = remember { mutableStateOf(prefs) }
    val lastRenderedDataKey = remember { mutableStateOf<String?>(null) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            object : LineChart(context) {
                private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(220, 255, 255, 255)
                    strokeWidth = 1.5f
                    pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
                }
                private val rsiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor("#B39DDB")
                    textSize = context.resources.displayMetrics.density * 10f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                // divergencias estilo TV, linea + chip Bull/Bear
                private val divBullLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor("#0ECB81")
                    strokeWidth = 3f
                }
                private val divBearLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor("#EF5350")
                    strokeWidth = 3f
                }
                // ocultas un poco mas suaves para diferenciarlas, como el Pine
                private val divHiddenBullLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(150, 14, 203, 129)
                    strokeWidth = 2.5f
                }
                private val divHiddenBearLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(150, 239, 83, 80)
                    strokeWidth = 2.5f
                }
                private val divBullLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor("#0ECB81")
                    textSize = 24f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val divBearLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor("#EF5350")
                    textSize = 24f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val divChipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(220, 20, 21, 24)
                }
                private val divRect = RectF()

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val h = highlighted?.getOrNull(0)
                    val currentState = stateRef.value

                    val density = context.resources.displayMetrics.density
                    val textX = viewPortHandler.contentLeft() + (density * 5f)
                    val textY = viewPortHandler.contentTop() + (density * 14f)

                    var rsiVal = 0.0
                    var hasVal = false

                    if (h != null) {
                        val xPts = floatArrayOf(h.x, 0f)
                        getTransformer(YAxis.AxisDependency.LEFT).pointValuesToPixel(xPts)
                        val px = xPts[0]
                        canvas.drawLine(px, viewPortHandler.contentTop(), px, viewPortHandler.contentBottom(), linePaint)

                        currentState.rsi.getValueAtCandleIndex(h.x.toInt())?.let {
                            rsiVal = it
                            hasVal = true
                        }
                    } else {
                        currentState.rsi.lastOrNull()?.let {
                            rsiVal = it.second
                            hasVal = true
                        }
                    }

                    if (hasVal) {
                        canvas.drawText(
                            "RSI: ${String.format(Locale.US, "%.2f", rsiVal)}",
                            textX,
                            textY,
                            rsiPaint
                        )
                    }

                    // divergencias sobre pivotes confirmados, solo el rango visible
                    if (prefsRef.value.rsiDivVisible) {
                        val divs = currentState.rsiDiv
                        if (divs.isNotEmpty()) {
                            val trans = getTransformer(YAxis.AxisDependency.LEFT)
                            val contentLeft = viewPortHandler.contentLeft()
                            val contentRight = viewPortHandler.contentRight()
                            val contentTop = viewPortHandler.contentTop()
                            val contentBottom = viewPortHandler.contentBottom()
                            val visStart = lowestVisibleX.toInt()
                            val visEnd = highestVisibleX.toInt()
                            // como el Pine, ocultas solo si el toggle va ON
                            val showHidden = prefsRef.value.rsiDivHidden
                            divs.forEach { div ->
                                if (!showHidden && (div.kind == RsiDivKind.HID_BULL || div.kind == RsiDivKind.HID_BEAR)) return@forEach
                                if (div.idx2 < visStart || div.idx1 > visEnd) return@forEach
                                val p1 = floatArrayOf(div.idx1.toFloat(), div.rsi1.toFloat())
                                trans.pointValuesToPixel(p1)
                                val p2 = floatArrayOf(div.idx2.toFloat(), div.rsi2.toFloat())
                                trans.pointValuesToPixel(p2)
                                val linePaint = when (div.kind) {
                                    RsiDivKind.REG_BULL -> divBullLinePaint
                                    RsiDivKind.REG_BEAR -> divBearLinePaint
                                    RsiDivKind.HID_BULL -> divHiddenBullLinePaint
                                    RsiDivKind.HID_BEAR -> divHiddenBearLinePaint
                                }
                                val labelPaint = if (div.bullish) divBullLabelPaint else divBearLabelPaint
                                canvas.drawLine(
                                    p1[0].coerceIn(contentLeft, contentRight), p1[1].coerceIn(contentTop, contentBottom),
                                    p2[0].coerceIn(contentLeft, contentRight), p2[1].coerceIn(contentTop, contentBottom),
                                    linePaint
                                )
                                // ocultas llevan la H como en TV
                                val baseLabel = when (div.kind) {
                                    RsiDivKind.REG_BULL -> "Bull"
                                    RsiDivKind.REG_BEAR -> "Bear"
                                    RsiDivKind.HID_BULL -> "H Bull"
                                    RsiDivKind.HID_BEAR -> "H Bear"
                                }
                                val label = if (div.early) "Pre-$baseLabel" else baseLabel
                                val w = labelPaint.measureText(label) + 14f
                                val chipH = labelPaint.textSize + 8f
                                val lx = (p2[0] + 4f).coerceIn(contentLeft, (contentRight - w).coerceAtLeast(contentLeft))
                                val ly = if (div.bullish) {
                                    (p2[1] + 4f).coerceIn(contentTop, (contentBottom - chipH).coerceAtLeast(contentTop))
                                } else {
                                    (p2[1] - chipH - 4f).coerceIn(contentTop, (contentBottom - chipH).coerceAtLeast(contentTop))
                                }
                                divRect.set(lx, ly, lx + w, ly + chipH)
                                canvas.drawRoundRect(divRect, 4f, 4f, divChipPaint)
                                canvas.drawText(label, lx + 7f, ly + chipH - 6f, labelPaint)
                            }
                        }
                    }
                }
            }.apply {
                setupCommonChartParams()
                setTouchEnabled(false)
                isDragEnabled = false
                setScaleEnabled(true)
                isScaleYEnabled = false
                setPinchZoom(false)
                axisLeft.apply {
                    axisMinimum = 0f
                    axisMaximum = 100f
                    setLabelCount(3, true)
                    setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
                    removeAllLimitLines()
                    addLimitLine(LimitLine(70f, "").apply {
                        lineColor = GraphicsColor.argb(120, 173, 177, 184)
                        lineWidth = 1f
                        enableDashedLine(10f, 6f, 0f)
                    })
                    addLimitLine(LimitLine(30f, "").apply {
                        lineColor = GraphicsColor.argb(120, 173, 177, 184)
                        lineWidth = 1f
                        enableDashedLine(10f, 6f, 0f)
                    })
                }
                chartRef.value = this
            }
        },
        update = { chart ->
            stateRef.value = state
            prefsRef.value = prefs
            val viewportKey = state.viewportKey()
            val isNewDataset = lastRenderedDataKey.value != viewportKey

            if (isNewDataset) {
                val lineData = LineData()
                if (state.rsi.isNotEmpty()) {
                    val rsiEntries = ArrayList<Entry>(state.rsi.size)
                    state.rsi.forEach { value ->
                        rsiEntries.add(Entry(value.first.toFloat(), value.second.toFloat()))
                    }
                    lineData.addDataSet(
                        createBBLineDataSet(
                            rsiEntries,
                            "RSI",
                            GraphicsColor.parseColor("#B39DDB"),
                            1.2f,
                            highlight = true
                        )
                    )
                }
                chart.data = lineData
            } else if (state.rsi.isNotEmpty()) {
                updateLastRsiInPlace(chart, state)
            }

            chart.xAxis.applyTimeAxis(state)
            chart.xAxis.setDrawLabels(false)
            chart.applySyncAndInitialZoom(state.candles, resetViewport = isNewDataset, skipPositioning = isNewDataset)
            if (isNewDataset) {
                // al montar, hereda el rango visible del principal
                priceChartRef.value?.let { chart.syncViewportFrom(it) }
                lastRenderedDataKey.value = viewportKey
            }
        }
    )
}

// ─── INITIAL ZOOM / SCROLL ───────────────────────────────────────────────────
private fun BarLineChartBase<*>.applySyncAndInitialZoom(
    data: List<*>,
    resetViewport: Boolean = false,
    skipPositioning: Boolean = false,
    resetCustomY: Boolean = false,
    // encuadre inicial solo con velas: las MAs de otro TF no aplanan el chart
    fitYToCandles: Boolean = false,
    onPositioned: (() -> Unit)? = null
) {
    if (data.isEmpty()) return
    if (resetViewport) {
        notifyDataSetChanged()
        viewPortHandler.setMinimumScaleX(1f)
        viewPortHandler.setMaximumScaleX(1_000_000f)
        viewPortHandler.setMinimumScaleY(1f)
        viewPortHandler.setMaximumScaleY(1_000_000f)
        if (fitYToCandles) {
            val win = data.filterIsInstance<CandleData>()
                .takeLast(INITIAL_VISIBLE_CANDLES.toInt().coerceAtLeast(1))
            val lo = win.minOfOrNull { it.low } ?: 0.0
            val hi = win.maxOfOrNull { it.high } ?: 0.0
            if (hi > lo) {
                val pad = (hi - lo) * 0.08
                axisLeft.axisMinimum = (lo - pad).toFloat()
                axisLeft.axisMaximum = (hi + pad).toFloat()
                axisRight.axisMinimum = (lo - pad).toFloat()
                axisRight.axisMaximum = (hi + pad).toFloat()
                isAutoScaleMinMaxEnabled = false
            }
        }
        if (!skipPositioning) {
            post {
                // vista fresca con Y ajustado, se congela con el primer pan
                if (resetCustomY && !fitYToCandles) {
                    axisLeft.resetAxisMinimum()
                    axisLeft.resetAxisMaximum()
                    axisRight.resetAxisMinimum()
                    axisRight.resetAxisMaximum()
                }
                isAutoScaleMinMaxEnabled = !fitYToCandles
                val scaleX = data.size.toFloat() / INITIAL_VISIBLE_CANDLES
                val lastX = (data.size - 1).toFloat().coerceAtLeast(0f)
                val pts = floatArrayOf(lastX, 0f)
                getTransformer(YAxis.AxisDependency.LEFT).pointValuesToPixel(pts)
                val matrix = Matrix()
                matrix.postScale(scaleX, 1f, pts[0], 0f)
                viewPortHandler.refresh(matrix, this, true)
                onPositioned?.invoke()
            }
        } else {
            invalidate()
        }
    }
}

private fun syncSubCharts(priceChart: CombinedChart, stochChart: LineChart?, rsiChart: LineChart?) {
    listOf(stochChart, rsiChart).forEach { sub ->
        sub ?: return@forEach
        sub.syncViewportFrom(priceChart)
    }
    syncHighlights(priceChart, stochChart, rsiChart)
}

private fun BarLineChartBase<*>.syncViewportFrom(source: BarLineChartBase<*>) {
    val sourceValues = FloatArray(9)
    source.viewPortHandler.matrixTouch.getValues(sourceValues)

    val targetMatrix = Matrix(viewPortHandler.matrixTouch)
    val targetValues = FloatArray(9)
    targetMatrix.getValues(targetValues)
    targetValues[Matrix.MSCALE_X] = sourceValues[Matrix.MSCALE_X]
    targetValues[Matrix.MTRANS_X] = sourceValues[Matrix.MTRANS_X]
    targetValues[Matrix.MSKEW_X] = 0f
    targetValues[Matrix.MSKEW_Y] = 0f
    targetValues[Matrix.MSCALE_Y] = 1f
    targetValues[Matrix.MTRANS_Y] = 0f
    targetMatrix.setValues(targetValues)

    // Keep the indicator charts on the same X matrix without reintroducing
    // MPAndroidChart's translation/scale clamp.
    viewPortHandler.matrixTouch.set(targetMatrix)
    invalidate()
}

private fun syncHighlights(priceChart: CombinedChart, stochChart: LineChart?, rsiChart: LineChart? = null) {
    val h = priceChart.highlighted?.getOrNull(0)
    listOf(stochChart, rsiChart).forEach { sub ->
        if (h != null) {
            sub?.highlightValue(h.x, 0, false)
        } else {
            sub?.highlightValues(null)
        }
    }
}


// ─── COMMON CHART SETUP ──────────────────────────────────────────────────────
private fun XAxis.applyTimeAxis(state: CryptoDetailState) {
    valueFormatter = object : ValueFormatter() {
        private val daySdf = SimpleDateFormat("MM/dd", Locale.US)
        private val yearSdf = SimpleDateFormat("yyyy/MM", Locale.US)
        private val dateSdf = SimpleDateFormat("MM/dd", Locale.US)
        private val hourSdf = SimpleDateFormat("HH:mm", Locale.US)

        override fun getFormattedValue(value: Float): String {
            if (state.candles.isEmpty()) return ""
            val idx = value.toInt().coerceIn(0, state.candles.size - 1)
            val time = state.candles[idx].time
            return when {
                state.candles.spansMultipleYears() -> yearSdf.format(Date(time))
                state.selectedInterval.isCalendarInterval() -> daySdf.format(Date(time))
                // fecha|hora, el renderer de 2 lineas lo parte
                else -> dateSdf.format(Date(time)) + "|" + hourSdf.format(Date(time))
            }
        }
    }

    val spansYears = state.candles.spansMultipleYears()
    val isCalendar = state.selectedInterval.isCalendarInterval()
    // siempre horizontal, intradia en 2 lineas via renderer
    labelRotationAngle = 0f
    textSize = if (spansYears) 9f else 10f
    yOffset = 8f
    setLabelCount(
        when {
            spansYears -> 3
            !isCalendar -> 3
            else -> 4
        },
        false
    )
}

// el XAxis de MPChart dibuja 1 linea; este parte fecha|hora en 2
private class TwoLineXAxisRenderer(
    viewPortHandler: ViewPortHandler,
    xAxis: XAxis,
    trans: Transformer
) : XAxisRenderer(viewPortHandler, xAxis, trans) {
    override fun drawLabel(
        c: Canvas,
        formattedLabel: String,
        x: Float,
        y: Float,
        anchor: MPPointF,
        angleDegrees: Float
    ) {
        val sep = formattedLabel.indexOf('|')
        if (sep < 0 || angleDegrees != 0f) {
            super.drawLabel(c, formattedLabel, x, y, anchor, angleDegrees)
            return
        }
        val center = MPPointF.getInstance(0.5f, 0.5f)
        val lh = mAxisLabelPaint.textSize * 1.25f
        Utils.drawXAxisValue(c, formattedLabel.substring(0, sep), x, y - lh / 2f, mAxisLabelPaint, center, 0f)
        Utils.drawXAxisValue(c, formattedLabel.substring(sep + 1), x, y + lh / 2f, mAxisLabelPaint, center, 0f)
        MPPointF.recycleInstance(center)
    }
}

private fun BarLineChartBase<*>.setupCommonChartParams() {
    description.isEnabled = false
    legend.isEnabled = false
    // numeros del chart en Geist semibold estilo OKX, con fallback silencioso
    try {
        androidx.core.content.res.ResourcesCompat.getFont(
            context, com.defitracker.app.R.font.geist_semibold
        )?.let { tf ->
            xAxis.typeface = tf
            axisLeft.typeface = tf
            axisRight.typeface = tf
        }
    } catch (_: Exception) {}
    setTouchEnabled(true)
    isDragEnabled = true
    setScaleEnabled(true)
    setPinchZoom(false)
    isScaleXEnabled = true
    isScaleYEnabled = true
    setBackgroundColor(GraphicsColor.BLACK)
    isHighlightPerDragEnabled = true
    // Keep the existing data-range calculation so the initial candles remain
    // visible; manual Y gestures still operate on the viewport matrix.
    // auto-escala al entrar, se congela sola con el primer pan
    isAutoScaleMinMaxEnabled = true
    xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        textColor = "#ADB1B8".toColorInt()
        gridColor = "#1A1D23".toColorInt()
        setDrawLabels(true)
        setAvoidFirstLastClipping(true)
        setLabelCount(5, false) // Fix 1: Limit label count to avoid crowding
        granularity = 1f
    }
    axisLeft.textColor = "#ADB1B8".toColorInt()
    axisLeft.gridColor = "#1A1D23".toColorInt()
    axisLeft.setDrawAxisLine(false)
    axisRight.isEnabled = false
}

// ─── DATASET HELPERS ─────────────────────────────────────────────────────────
private fun createCandleDataSet(entries: List<CandleEntry>) = CandleDataSet(entries, "Klines").apply {
    axisDependency = YAxis.AxisDependency.RIGHT
    shadowColor = "#F4F4F4".toColorInt()
    // mecha fina estilo OKX, el ancho fijo en px se ve grueso al alejar
    shadowWidth = 0.7f
    decreasingColor = "#EF5350".toColorInt()
    increasingColor = "#0ECB81".toColorInt()
    neutralColor = "#ADB1B8".toColorInt()
    decreasingPaintStyle = Paint.Style.FILL
    increasingPaintStyle = Paint.Style.FILL
    setDrawValues(false)
    shadowColorSameAsCandle = true
    // cuerpos gruesos pegaditos estilo OKX, poco gap para que la mecha no domine
    barSpace = 0.18f
    highLightColor = "#ADB1B8".toColorInt()
    highlightLineWidth = 1f
    setDrawHorizontalHighlightIndicator(false)
    setDrawVerticalHighlightIndicator(false)
    enableDashedHighlightLine(10f, 5f, 0f)
}

private fun createBBLineDataSet(
    entries: List<Entry>,
    label: String,
    color: Int,
    width: Float = 1f,
    highlight: Boolean = false,
    axisDependency: YAxis.AxisDependency = YAxis.AxisDependency.LEFT
) = LineDataSet(entries, label).apply {
    this.axisDependency = axisDependency
    this.color = color
    setDrawCircles(false)
    lineWidth = width
    setDrawValues(false)
    isHighlightEnabled = highlight // Fix: Disable highlight for BB lines, but allow for indicators
    mode = LineDataSet.Mode.CUBIC_BEZIER
    highLightColor = "#ADB1B8".toColorInt()
    highlightLineWidth = 1f
    enableDashedHighlightLine(10f, 5f, 0f)
    if (highlight) {
        setDrawHorizontalHighlightIndicator(false)
        setDrawVerticalHighlightIndicator(false)
    }
}

// una MA = un LineDataSet con su color/grosor, etiqueta MA|<id> para el tick en vivo
private fun maLineDataSet(ma: MaConfig, line: List<Pair<Long, Double>>): LineDataSet? {
    if (line.isEmpty()) return null
    val entries = ArrayList<Entry>(line.size)
    line.forEach { value ->
        entries.add(Entry(value.first.toFloat(), value.second.toFloat()))
    }
    return createBBLineDataSet(
        entries,
        "MA|${ma.id}",
        ma.colorHex.toColorInt(),
        ma.width,
        axisDependency = YAxis.AxisDependency.RIGHT
    )
}

// etiqueta corta de leyenda con tipo/TF
private fun maLegendLabel(ma: MaConfig): String {
    val kind = if (ma.type == MaType.EMA) "EMA" else "MA"
    val tf = if (ma.timeframe != "chart") "·${ma.timeframe}" else ""
    return "$kind${ma.period}$tf"
}

// tick en vivo actualiza el ultimo punto de cada MA sin reconstruir
private fun updateLastMAInPlace(chart: CombinedChart, state: CryptoDetailState) {
    val lineData = chart.data?.lineData ?: return
    for (i in 0 until lineData.dataSetCount) {
        val ds = lineData.getDataSetByIndex(i)
        if (!ds.label.startsWith("MA|")) continue
        val id = ds.label.removePrefix("MA|")
        val last = state.maLines[id]?.lastOrNull() ?: continue
        if (ds.entryCount > 0) {
            ds.getEntryForIndex(ds.entryCount - 1).y = last.second.toFloat()
        }
    }
    chart.invalidate()
}

// ─── IN-PLACE UPDATE HELPERS ─────────────────────────────────────────────────
private fun updateLastCandleInPlace(chart: CombinedChart, state: CryptoDetailState) {
    val cd = chart.data ?: return
    val last = state.candles.lastOrNull() ?: return
    val lastIdx = state.candles.size - 1
    cd.candleData?.dataSets?.forEach { ds ->
        if (ds.entryCount > lastIdx) {
            val e = ds.getEntryForIndex(lastIdx)
            e.open = last.open.toFloat()
            e.close = last.close.toFloat()
            e.high = last.high.toFloat()
            e.low = last.low.toFloat()
        }
    }
    val lineData = cd.lineData
    if (lineData != null && state.bbUpper.isNotEmpty()) {
        val bbSize = state.bbUpper.size
        val bbLastIdx = bbSize - 1
        val lastUpper = state.bbUpper.lastOrNull()?.second
        val lastMiddle = state.bbMiddle.lastOrNull()?.second
        val lastLower = state.bbLower.lastOrNull()?.second
        for (i in 0 until lineData.dataSetCount) {
            val ds = lineData.getDataSetByIndex(i)
            if (ds.entryCount > bbLastIdx && bbLastIdx >= 0) {
                val e = ds.getEntryForIndex(bbLastIdx)
                when (ds.label) {
                    "Upper" -> lastUpper?.let { e.y = it.toFloat() }
                    "Middle" -> lastMiddle?.let { e.y = it.toFloat() }
                    "Lower" -> lastLower?.let { e.y = it.toFloat() }
                }
            }
        }
    }
    chart.invalidate()
}

private fun updateLastStochInPlace(chart: LineChart, state: CryptoDetailState) {
    val ld = chart.data ?: return
    val lastK = state.stochK.lastOrNull()
    val lastD = state.stochD.lastOrNull()
    if (lastK != null && ld.dataSets.isNotEmpty()) {
        val ds = ld.getDataSetByIndex(0)
        if (ds.entryCount > 0) {
            val e = ds.getEntryForIndex(ds.entryCount - 1)
            e.y = lastK.second.toFloat()
        }
    }
    if (lastD != null && ld.dataSets.size > 1) {
        val ds = ld.getDataSetByIndex(1)
        if (ds.entryCount > 0) {
            val e = ds.getEntryForIndex(ds.entryCount - 1)
            e.y = lastD.second.toFloat()
        }
    }
    chart.invalidate()
}

// tick en vivo actualiza el ultimo punto del RSI sin reconstruir
private fun updateLastRsiInPlace(chart: LineChart, state: CryptoDetailState) {
    val ld = chart.data ?: return
    val lastRsi = state.rsi.lastOrNull() ?: return
    if (ld.dataSets.isNotEmpty()) {
        val ds = ld.getDataSetByIndex(0)
        if (ds.entryCount > 0) {
            ds.getEntryForIndex(ds.entryCount - 1).y = lastRsi.second.toFloat()
        }
    }
    chart.invalidate()
}

// ─── INITIAL ZOOM / SCROLL ───────────────────────────────────────────────────
private const val INITIAL_VISIBLE_CANDLES = 100f

// ─── FIX 2 & 3: OKX MARKER ───────────────────────────────────────────────────
/**
 * Full OKX-style marker: shows OHLCV card next to the candle.
 * Fix 3: accepts a lambda so it always reads the latest [CryptoDetailState].
 */
@SuppressLint("ViewConstructor", "SetTextI18n")
class OKXChartMarker(
    context: android.content.Context,
    private val stateProvider: () -> CryptoDetailState
) : MarkerView(context, R.layout.chart_marker) {

    private val tvTime: TextView = findViewById(R.id.tvTime)
    private val tvOpen: TextView = findViewById(R.id.tvOpen)
    private val tvHigh: TextView = findViewById(R.id.tvHigh)
    private val tvLow: TextView = findViewById(R.id.tvLow)
    private val tvClose: TextView = findViewById(R.id.tvClose)
    private val tvChange: TextView = findViewById(R.id.tvChange)
    private val tvChangePct: TextView = findViewById(R.id.tvChangePct)
    private val tvVolume: TextView = findViewById(R.id.tvVolume)

    private val daySdf = SimpleDateFormat("MM/dd", Locale.US)
    private val yearSdf = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    private val hourSdf = SimpleDateFormat("MM/dd, HH:mm", Locale.US)

    private var lastHighlight: Highlight? = null
    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        lastHighlight = highlight
        e?.let {
            val state = stateProvider()
            val index = it.x.toInt()
            if (index >= 0 && index < state.candles.size) {
                val candle = state.candles[index]
                val change = candle.close - candle.open
                val changePct = if (candle.open != 0.0) change / candle.open * 100 else 0.0
                val isUp = change >= 0
                val changeColor = if (isUp)
                    GraphicsColor.parseColor("#1ECB81")
                else
                    GraphicsColor.parseColor("#F6465D")

                val isDaily = state.selectedInterval.isCalendarInterval()
                val sdf = when {
                    state.candles.spansMultipleYears() -> yearSdf
                    isDaily -> daySdf
                    else -> hourSdf
                }
                tvTime.text = sdf.format(Date(candle.time))
                tvOpen.text = formatPriceForChart(candle.open)
                tvHigh.text = formatPriceForChart(candle.high)
                tvLow.text = formatPriceForChart(candle.low)
                tvClose.text = formatPriceForChart(candle.close)
                tvChange.text = (if (isUp) "+" else "") + formatPriceForChart(change)
                tvChange.setTextColor(changeColor)
                tvChangePct.text = (if (isUp) "+" else "") + String.format(Locale.US, "%.2f", changePct) + "%"
                tvChangePct.setTextColor(changeColor)
                tvVolume.text = formatVolDouble(candle.volume)
            }
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): com.github.mikephil.charting.utils.MPPointF {
        val chart = chartView as? BarLineChartBase<*>
        val x = lastHighlight?.x ?: 0f
        val midX = if (chart != null) (chart.lowestVisibleX + chart.highestVisibleX) / 2f else 0f

        // If selection is in right half of screen, show marker on left, else on right
        val xOffset = if (x > midX) {
            -width.toFloat() - 40f
        } else {
            40f
        }

        return com.github.mikephil.charting.utils.MPPointF(xOffset, -height.toFloat() / 2f)
    }

    private fun formatVolDouble(v: Double): String = when {
        v >= 1_000_000 -> String.format(Locale.US, "%.2fM", v / 1_000_000)
        v >= 1_000 -> String.format(Locale.US, "%.2fK", v / 1_000)
        else -> String.format(Locale.US, "%.2f", v)
    }
}

// ─── INDICATORS SHEET ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndicatorsSheet(
    prefs: IndicatorPrefs,
    onToggleBB: () -> Unit,
    onToggleProfile: () -> Unit,
    onToggleVolume: () -> Unit,
    onToggleStoch: () -> Unit,
    onToggleRsi: () -> Unit,
    onToggleRsiDiv: () -> Unit,
    onToggleRsiDivHidden: () -> Unit,
    onOpenMAs: () -> Unit = {},
    onToggleSmcStructure: () -> Unit,
    onToggleSmcOB: () -> Unit,
    onToggleSmcFvg: () -> Unit,
    onToggleSmcPremium: () -> Unit,
    onToggleSmcEqhl: () -> Unit,
    onToggleSmcLiq: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Indicadores",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Principales",
            color = Color.Gray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        IndicatorSwitchRow("BOLL", "Bandas de Bollinger", prefs.bbVisible, onToggleBB)
        IndicatorSwitchRow("Perfil", "Perfil de volumen", prefs.profileVisible, onToggleProfile)
        // medias en su propio bottom sheet, resumen de activas
        run {
            val maActive = prefs.mas.count { it.visible }
            val summary = prefs.mas.take(5).joinToString(" · ") { maLegendLabel(it) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenMAs() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Medias móviles ($maActive/${prefs.mas.size})",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (summary.isNotBlank()) summary else "Toca para configurar",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                Text(text = "▸", color = Color.Gray, fontSize = 16.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Subindicadores",
            color = Color.Gray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        IndicatorSwitchRow("VOL", "Volumen", prefs.volumeVisible, onToggleVolume)
        IndicatorSwitchRow("StochRSI", "Stoch RSI", prefs.stochVisible, onToggleStoch)
        IndicatorSwitchRow("RSI", "RSI 14", prefs.rsiVisible, onToggleRsi)
        IndicatorSwitchRow("DIV", "Divergencias RSI", prefs.rsiDivVisible, onToggleRsiDiv)
        IndicatorSwitchRow("HDIV", "Divergencias ocultas", prefs.rsiDivHidden, onToggleRsiDivHidden)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Smart Money",
            color = Color.Gray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        IndicatorSwitchRow("BOS / CHoCH", "Estructura de mercado", prefs.smcStructure, onToggleSmcStructure)
        IndicatorSwitchRow("Order Blocks", "Últimos 5 por lado", prefs.smcOrderBlocks, onToggleSmcOB)
        IndicatorSwitchRow("FVG", "Gaps de valor justo", prefs.smcFvg, onToggleSmcFvg)
        IndicatorSwitchRow("Premium/Discount", "Rango 120 velas + equilibrio", prefs.smcPremium, onToggleSmcPremium)
        IndicatorSwitchRow("EQH / EQL", "Máximos/mínimos iguales", prefs.smcEqhl, onToggleSmcEqhl)
        IndicatorSwitchRow("Liquidez BSL/SSL", "Pools + barridos", prefs.smcLiquidity, onToggleSmcLiq)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// lista de MAs en su propio bottom sheet (se abre desde Indicadores)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaListSheet(
    mas: List<MaConfig>,
    onToggle: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onType: (String, MaType) -> Unit = { _, _ -> },
    onPeriod: (String, Int) -> Unit = { _, _ -> },
    onTimeframe: (String, String) -> Unit = { _, _ -> },
    onColor: (String, String) -> Unit = { _, _ -> },
    onWidth: (String, Float) -> Unit = { _, _ -> }
) {
    var expandedColorId by remember { mutableStateOf<String?>(null) }
    var tfMenuId by remember { mutableStateOf<String?>(null) }
    var widthMenuId by remember { mutableStateOf<String?>(null) }
    var periodDialogId by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Medias móviles",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (mas.size < IndicatorPrefs.MAX_MAS) {
                Text(
                    text = "+ Agregar",
                    color = Color(0xFF1ECB81),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onAdd() }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        mas.forEach { ma ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // circulo visible como en tradingview
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .border(
                                1.5.dp,
                                if (ma.visible) Color(0xFF1ECB81) else Color.Gray,
                                CircleShape
                            )
                            .background(
                                if (ma.visible) Color(0xFF1ECB81).copy(alpha = 0.2f) else Color.Transparent,
                                CircleShape
                            )
                            .clickable { onToggle(ma.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (ma.visible) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Visible",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // titulo solo detalle, sin config externa: todo es inline
                    Text(
                        text = maLegendLabel(ma),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // rejilla pegada: tipo, periodo, grosor, color, TF
                    Row(
                        modifier = Modifier
                            .background(InputBg, RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF3A3F47), RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // tipo SMA/EMA
                        MaSegText(
                            text = if (ma.type == MaType.EMA) "EMA" else "SMA",
                            width = 50.dp,
                            onClick = {
                                onType(ma.id, if (ma.type == MaType.EMA) MaType.SMA else MaType.EMA)
                            }
                        )
                        MaSegDiv()
                        // periodo
                        MaSegText(
                            text = ma.period.toString(),
                            width = 44.dp,
                            onClick = { periodDialogId = ma.id }
                        )
                        MaSegDiv()
                        // grosor con preview de linea
                        Box {
                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(36.dp)
                                    .clickable { widthMenuId = ma.id },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(30.dp)
                                        .height(ma.width.dp.coerceAtLeast(1.dp))
                                        .background(Color(ma.colorHex.toColorInt()), RoundedCornerShape(1.dp))
                                )
                            }
                            DropdownMenu(
                                expanded = widthMenuId == ma.id,
                                onDismissRequest = { widthMenuId = null },
                                modifier = Modifier.background(InputBg)
                            ) {
                                FIB_WIDTH_OPTIONS.forEach { w ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(36.dp)
                                                        .height(w.dp.coerceAtLeast(1.dp))
                                                        .background(Color(ma.colorHex.toColorInt()), RoundedCornerShape(1.dp))
                                                )
                                                Text(
                                                    maWidthLabel(w),
                                                    color = if (w == ma.width) Color.White else Color.Gray,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (w == ma.width) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        },
                                        onClick = {
                                            onWidth(ma.id, w)
                                            widthMenuId = null
                                        }
                                    )
                                }
                            }
                        }
                        MaSegDiv()
                        // color redondo, despliega el picker debajo
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(36.dp)
                                .clickable {
                                    expandedColorId = if (expandedColorId == ma.id) null else ma.id
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(Color(ma.colorHex.toColorInt()), CircleShape)
                                    .border(
                                        if (expandedColorId == ma.id) 2.dp else 1.dp,
                                        if (expandedColorId == ma.id) Color.White else Color(0xFF3A3F47),
                                        CircleShape
                                    )
                            )
                        }
                        MaSegDiv()
                        // temporalidad
                        Box {
                            MaSegText(
                                text = if (ma.timeframe == "chart") "GRAF" else ma.timeframe,
                                width = 54.dp,
                                onClick = { tfMenuId = ma.id }
                            )
                            DropdownMenu(
                                expanded = tfMenuId == ma.id,
                                onDismissRequest = { tfMenuId = null },
                                modifier = Modifier.background(InputBg)
                            ) {
                                IndicatorPrefs.MA_TFS.forEach { tf ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (tf == "chart") "GRAF" else tf,
                                                color = Color.White,
                                                fontSize = 13.sp
                                            )
                                        },
                                        onClick = {
                                            onTimeframe(ma.id, tf)
                                            tfMenuId = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (mas.size > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar ${maLegendLabel(ma)}",
                            tint = Color.Gray,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onDelete(ma.id) }
                        )
                    }
                }
                // picker inline debajo de la fila
                AnimatedVisibility(visible = expandedColorId == ma.id) {
                    Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                        WideColorPicker(
                            currentHex = ma.colorHex,
                            onPick = { onColor(ma.id, it) },
                            key = ma.id
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
        // dialogo compacto para el periodo
        periodDialogId?.let { dialogId ->
            mas.firstOrNull { it.id == dialogId }?.let { ma ->
                var text by remember(dialogId) { mutableStateOf(ma.period.toString()) }
                AlertDialog(
                    onDismissRequest = { periodDialogId = null },
                    containerColor = SheetBg,
                    titleContentColor = Color.White,
                    textContentColor = Color.Gray,
                    title = { Text("Periodo ${maLegendLabel(ma)}") },
                    text = {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { v -> text = v.filter { it.isDigit() }.take(3) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1ECB81),
                                unfocusedBorderColor = LineDiv,
                                cursorColor = Color.White
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            text.toIntOrNull()?.let { p ->
                                if (p in 2..500) onPeriod(ma.id, p)
                            }
                            periodDialogId = null
                        }) { Text("Listo", color = Color(0xFF1ECB81)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { periodDialogId = null }) { Text("Cancelar", color = Color.White) }
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MaSegText(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(36.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun MaSegDiv() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(20.dp)
            .background(LineDiv)
    )
}

// picker simple: presets + cuadrado saturacion/valor + barra de tonos, sin librerias
private fun hexToHsv(hex: String): FloatArray {
    return try {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(hex), hsv)
        hsv
    } catch (_: Exception) {
        floatArrayOf(160f, 0.9f, 0.9f)
    }
}

private fun hsvToHex(h: Float, s: Float, v: Float): String =
    String.format("#%06X", 0xFFFFFF and android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))

@Composable
fun WideColorPicker(
    currentHex: String,
    onPick: (String) -> Unit,
    key: String = currentHex
) {
    // claveado por key (id de la MA / "fib") para que el drag no se reinicie al recomponer
    val init = remember(key) { hexToHsv(currentHex) }
    var hue by remember(key) { mutableStateOf(init[0]) }
    var sat by remember(key) { mutableStateOf(init[1]) }
    var valV by remember(key) { mutableStateOf(init[2]) }
    fun emit(h: Float = hue, s: Float = sat, v: Float = valV) {
        onPick(hsvToHex(h, s, v))
    }
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val density = LocalDensity.current
    // tamaño real medido (sin BoxWithConstraints: rompe los DropdownMenu con intrinsics)
    var svSize by remember(key) { mutableStateOf(IntSize.Zero) }
    var hueBarW by remember(key) { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // presets compactos en una fila
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IndicatorPrefs.PRESET_COLORS.forEach { hex ->
                val selected = hex.equals(currentHex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color(hex.toColorInt()), CircleShape)
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = if (selected) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable {
                            val hsv = hexToHsv(hex)
                            hue = hsv[0]
                            sat = hsv[1]
                            valV = hsv[2]
                            onPick(hex)
                        }
                )
            }
        }
        // cuadrado saturacion (x) / valor (y) del tono actual
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                .onSizeChanged { svSize = it }
        ) {
            fun updateSv(offset: androidx.compose.ui.geometry.Offset) {
                val w = svSize.width
                val h = svSize.height
                if (w <= 0 || h <= 0) return
                sat = (offset.x / w).coerceIn(0f, 1f)
                valV = 1f - (offset.y / h).coerceIn(0f, 1f)
                emit()
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    .pointerInput(hue) {
                        detectTapGestures(onTap = { updateSv(it) })
                    }
                    .pointerInput(hue) {
                        detectDragGestures(
                            onDragStart = { updateSv(it) },
                            onDrag = { change, _ -> updateSv(change.position) }
                        )
                    }
            )
            // selector sobre el color elegido
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { (svSize.width * sat).toDp() } - 11.dp,
                        y = with(density) { (svSize.height * (1f - valV)).toDp() } - 11.dp
                    )
                    .size(22.dp)
                    .border(2.dp, Color.White, CircleShape)
                    .background(Color.Transparent, CircleShape)
            )
        }
        // barra inferior de tonos
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { h ->
                            Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 1f, 1f)))
                        }
                    )
                )
                .onSizeChanged { hueBarW = it.width }
        ) {
            fun updateHue(x: Float) {
                if (hueBarW <= 0) return
                hue = (x / hueBarW * 360f).coerceIn(0f, 360f)
                emit()
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { updateHue(it.x) })
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { updateHue(it.x) },
                            onDrag = { change, _ -> updateHue(change.position.x) }
                        )
                    }
            )
            // thumb sobre el tono actual
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { (hueBarW * (hue / 360f)).toDp() } - 11.dp,
                        y = (-1).dp
                    )
                    .size(22.dp)
                    .border(2.dp, Color.White, CircleShape)
                    .background(Color.Transparent, CircleShape)
            )
        }
    }
}

// ─── PULSO: SHEET DE ANALISIS DEL MOMENTO ───────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisSheet(
    analysis: PulseAnalysis?,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = Color(0xFF1ECB81),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Pulso del momento",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Actualizar",
                color = Color(0xFF1ECB81),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onRefresh() }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (analysis == null) {
            Text("Calculando con las velas en pantalla…", color = Color.Gray, fontSize = 13.sp)
        } else {
            val biasColor = when (analysis.bias) {
                PulseBias.BULLISH -> Color(0xFF1ECB81)
                PulseBias.BEARISH -> Color(0xFFF6465D)
                PulseBias.NEUTRAL -> Color.Gray
            }
            val biasText = when (analysis.bias) {
                PulseBias.BULLISH -> "ALCISTA"
                PulseBias.BEARISH -> "BAJISTA"
                PulseBias.NEUTRAL -> "NEUTRO"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = biasText,
                    color = biasColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${analysis.score}/5",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (analysis.contraTrend) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "contratendencia",
                        color = Color(0xFFFFD60A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            analysis.items.forEach { item ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .border(
                                    1.5.dp,
                                    if (item.hit) {
                                        if (item.bullish == false) Color(0xFFF6465D) else Color(0xFF1ECB81)
                                    } else Color.Gray,
                                    CircleShape
                                )
                                .background(
                                    if (item.hit) {
                                        if (item.bullish == false) Color(0xFFF6465D).copy(alpha = 0.2f)
                                        else Color(0xFF1ECB81).copy(alpha = 0.2f)
                                    } else Color.Transparent,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.hit) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = item.label,
                            color = if (item.hit) Color.White else Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = if (item.hit) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    item.detail?.let { detail ->
                        Text(
                            text = detail,
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 32.dp, bottom = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Soporte ${analysis.support?.let { fmtPulsePrice(it) } ?: "--"}",
                    color = Color(0xFF1ECB81),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Resistencia ${analysis.resistance?.let { fmtPulsePrice(it) } ?: "--"}",
                    color = Color(0xFFF6465D),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(analysis.headline, color = Color.White, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Actualizado ${formatPulseTime(analysis.updatedAt)} · no es recomendación financiera",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun formatPulseTime(ms: Long): String {
    return try {
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(ms))
    } catch (_: Exception) {
        ""
    }
}

// ─── DIBUJO: SHEET + BARRA FIBO ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingToolsSheet(
    active: DrawingTool,
    drawKind: DrawKind?,
    magnetOn: Boolean,
    hasFib: Boolean,
    fibCount: Int = 0,
    onPickMeasure: () -> Unit,
    onPickFibo: () -> Unit,
    onPickDraw: (DrawKind) -> Unit,
    onToggleMagnet: () -> Unit,
    onHideSelected: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Herramientas de dibujo", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrawingTopAction(if (magnetOn) "Imán ON" else "Imán", true, onToggleMagnet)
            DrawingTopAction("Ocultar", hasFib, onHideSelected)
            DrawingTopAction("Eliminar", hasFib, onDeleteSelected)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Medir y niveles", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrawingToolCell(
                Icons.Filled.Straighten,
                if (active == DrawingTool.MEASURE) "Medir ON" else "Medir",
                active == DrawingTool.MEASURE,
                Modifier.weight(1f),
                onClick = onPickMeasure
            )
            DrawingToolCell(
                Icons.Filled.BarChart,
                "Retroceso de Fib",
                active == DrawingTool.FIBO,
                Modifier.weight(1f),
                onClick = onPickFibo
            )
            DrawingToolCell(Icons.Filled.DragHandle, "Línea de precio", active == DrawingTool.DRAW && drawKind == DrawKind.PRICE_LINE, Modifier.weight(1f)) { onPickDraw(DrawKind.PRICE_LINE) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Líneas de tendencia", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrawingToolCell(Icons.Filled.Timeline, "Segmento", active == DrawingTool.DRAW && drawKind == DrawKind.SEGMENT, Modifier.weight(1f)) { onPickDraw(DrawKind.SEGMENT) }
            DrawingToolCell(Icons.Filled.ShowChart, "Línea", active == DrawingTool.DRAW && drawKind == DrawKind.LINE, Modifier.weight(1f)) { onPickDraw(DrawKind.LINE) }
            DrawingToolCell(Icons.Filled.ArrowForward, "Recta", active == DrawingTool.DRAW && drawKind == DrawKind.RAY, Modifier.weight(1f)) { onPickDraw(DrawKind.RAY) }
            DrawingToolCell(Icons.Filled.TrendingUp, "Flecha", active == DrawingTool.DRAW && drawKind == DrawKind.ARROW, Modifier.weight(1f)) { onPickDraw(DrawKind.ARROW) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Líneas horizontales", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrawingToolCell(Icons.Filled.HorizontalRule, "Segmento H", active == DrawingTool.DRAW && drawKind == DrawKind.H_SEGMENT, Modifier.weight(1f)) { onPickDraw(DrawKind.H_SEGMENT) }
            DrawingToolCell(Icons.Filled.Remove, "Línea H", active == DrawingTool.DRAW && drawKind == DrawKind.H_LINE, Modifier.weight(1f)) { onPickDraw(DrawKind.H_LINE) }
            DrawingToolCell(Icons.Filled.ArrowForward, "Recta H", active == DrawingTool.DRAW && drawKind == DrawKind.H_RAY, Modifier.weight(1f)) { onPickDraw(DrawKind.H_RAY) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Figuras geométricas", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrawingToolCell(Icons.Filled.CheckBoxOutlineBlank, "Rectángulo", active == DrawingTool.DRAW && drawKind == DrawKind.RECT, Modifier.weight(1f)) { onPickDraw(DrawKind.RECT) }
            DrawingToolCell(Icons.Filled.RadioButtonUnchecked, "Círculo", active == DrawingTool.DRAW && drawKind == DrawKind.CIRCLE, Modifier.weight(1f)) { onPickDraw(DrawKind.CIRCLE) }
            DrawingToolCell(Icons.Filled.ChangeHistory, "Triángulo", active == DrawingTool.DRAW && drawKind == DrawKind.TRIANGLE, Modifier.weight(1f)) { onPickDraw(DrawKind.TRIANGLE) }
        }
        Spacer(modifier = Modifier.height(6.dp))
        // ocultar no cierra el modo, solo esconde el trazo
        if (hasFib) {
            Text(
                text = "Trazados: $fibCount — toca Fibo para agregar otro",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DrawingTopAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(InputBg, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) Color.White else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChartResizeDivider(onDrag: (Float) -> Unit) {
    var active by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // visual delgado, toque amplio en toda la linea
            .height(18.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { active = true },
                    onDragEnd = { active = false },
                    onDragCancel = { active = false }
                ) { change, dragAmount ->
                    change.consume()
                    // ganancia para no arrastrar tanto el dedo
                    onDrag(dragAmount.y * 2.2f)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // linea full ancho como OKX + pestaña que se ilumina al jalar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LineDiv)
        )
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(3.dp)
                .background(
                    if (active) Color(0xFF1ECB81) else Color(0xFF3A3F47),
                    RoundedCornerShape(1.dp)
                )
        )
    }
}

@Composable
private fun DrawingToolCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                if (selected) Color(0xFF1ECB81).copy(alpha = 0.25f) else InputBg,
                RoundedCornerShape(8.dp)
            )
            .border(
                if (selected) 1.dp else 0.dp,
                if (selected) Color(0xFF1ECB81) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverlayEditBar(
    modifier: Modifier = Modifier,
    selectionKey: String,
    colorHex: String,
    width: Float,
    hidden: Boolean,
    locked: Boolean,
    showLevels: Boolean,
    onColor: (String) -> Unit,
    onWidth: (Float) -> Unit,
    onLevels: () -> Unit,
    onToggleHide: () -> Unit,
    onToggleLock: () -> Unit,
    onDelete: () -> Unit
) {
    var showColors by remember { mutableStateOf(false) }
    var showWidths by remember { mutableStateOf(false) }
    // toque en la pestaña contrae/expande, arrastrar la mueve igual que antes
    var collapsed by remember(selectionKey) { mutableStateOf(false) }
    // pestaña para mover la barra a cualquier lado
    var drag by remember { mutableStateOf(Offset.Zero) }
    val tintColor = try {
        Color(colorHex.toColorInt())
    } catch (_: Exception) {
        Color.White
    }
    Row(
        modifier = modifier
            .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
            .background(Color(0xEE141518), RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // pestaña de arrastre: toque contrae/expande, mover arrastra
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 40.dp)
                .combinedClickable(onClick = {
                    collapsed = !collapsed
                    if (collapsed) {
                        showColors = false
                        showWidths = false
                    }
                })
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        drag += dragAmount
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (collapsed) Icons.Filled.KeyboardArrowRight else Icons.Default.DragHandle,
                contentDescription = if (collapsed) "Expandir" else "Mover",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
        if (!collapsed) {
        GridDivider()
        // lapiz abre el mismo picker de las medias
        Box {
            GridCell(onClick = { showColors = true }) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Color",
                    tint = tintColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            DropdownMenu(
                expanded = showColors,
                onDismissRequest = { showColors = false },
                modifier = Modifier.background(InputBg)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    WideColorPicker(
                        currentHex = colorHex,
                        onPick = onColor,
                        key = "fib"
                    )
                }
            }
        }
        GridDivider()
        // grosor con preview de linea
        Box {
            GridCellText(text = fibWidthLabel(width), onClick = { showWidths = true })
            DropdownMenu(
                expanded = showWidths,
                onDismissRequest = { showWidths = false },
                modifier = Modifier.background(InputBg)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    FIB_WIDTH_OPTIONS.forEach { w ->
                        Row(
                            modifier = Modifier
                                .clickable {
                                    onWidth(w)
                                    showWidths = false
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(w.dp.coerceAtLeast(1.dp))
                                    .background(tintColor, RoundedCornerShape(1.dp))
                            )
                            Text(
                                fibWidthLabel(w),
                                color = if (w == width) Color.White else Color.Gray,
                                fontSize = 13.sp,
                                fontWeight = if (w == width) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
        GridDivider()
        // tuerca = niveles (solo fibo) y estilo del seleccionado
        if (showLevels) {
            GridCell(onClick = onLevels) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Niveles",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            GridDivider()
        }
        // ojo con raya cuando esta oculto
        GridCell(onClick = onToggleHide) {
            Icon(
                imageVector = if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = "Visible",
                tint = Color.Gray,
                modifier = Modifier.size(22.dp)
            )
        }
        GridDivider()
        // candado azul cerrado bloquea mover/resize
        GridCell(onClick = onToggleLock) {
            Icon(
                imageVector = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = "Bloquear",
                tint = if (locked) Color(0xFF2196F3) else Color.Gray,
                modifier = Modifier.size(22.dp)
            )
        }
        GridDivider()
        GridCell(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Eliminar",
                tint = Color.Gray,
                modifier = Modifier.size(22.dp)
            )
        }
        }
    }
}

@Composable
private fun GridDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(LineDiv)
    )
}

@Composable
private fun GridCell(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 40.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun GridCellText(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 40.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FibLevelsSheet(
    fib: FibOverlay,
    onToggle: (Float) -> Unit,
    onColor: (String) -> Unit,
    onWidth: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Retroceso de Fib", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Color de línea", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        WideColorPicker(currentHex = fib.colorHex, onPick = onColor, key = "fib")
        Spacer(modifier = Modifier.height(12.dp))
        Text("Grosor de línea", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        val sliderPos = remember(fib.width) { mutableStateOf(fib.width) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = sliderPos.value,
                onValueChange = { sliderPos.value = it },
                onValueChangeFinished = { onWidth(sliderPos.value) },
                valueRange = 0.5f..3f,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(String.format(Locale.US, "%.1f", sliderPos.value), color = Color.White, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Niveles", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        ALL_FIB_LEVELS.forEach { ratio ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(ratio) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ratio.toString().trimEnd('0').trimEnd('.'),
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = ratio in fib.enabledLevels, onCheckedChange = { onToggle(ratio) })
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun IndicatorSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF1ECB81)
            )
        )
    }
}
