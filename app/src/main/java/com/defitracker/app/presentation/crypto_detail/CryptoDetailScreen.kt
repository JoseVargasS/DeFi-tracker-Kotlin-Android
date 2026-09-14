package com.defitracker.app.presentation.crypto_detail

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color as GraphicsColor
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import com.defitracker.app.R
import com.defitracker.app.ui.theme.Rajdhani
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
import com.github.mikephil.charting.components.MarkerView
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
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
    val maSheetPeriod = remember { mutableStateOf<Int?>(null) }
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
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // ponytail: badge de fuente siempre visible
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
            // Stats Header
            state.detail?.let { detail ->
                val detailPair = splitTradingPair(detail.symbol, state.source)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text(text = "Last price", color = Color.Gray, fontSize = 14.sp)
                        Text(
                            text = detail.price,
                            color = if (detail.isPositive) Color(0xFF1ECB81) else Color(0xFFF6465D),
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Rajdhani
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = detailPair.displayName, color = Color.Gray, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${if (detail.isPositive) "+" else ""}${detail.priceChangePercent}%",
                                color = if (detail.isPositive) Color(0xFF1ECB81) else Color(0xFFF6465D),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Rajdhani
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

            // Interval Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ponytail: orden ascendente estilo OKX, cortos a la izq
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
                    val isSelected = state.selectedInterval == interval.second
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(36.dp)
                            .clickable { viewModel.loadChartData(interval.second) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = interval.first,
                            color = if (isSelected) Color.White else Color.Gray,
                            fontSize = 13.sp,
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

            Spacer(modifier = Modifier.height(8.dp))

            // Stacked Charts Area
            Column(modifier = Modifier.fillMaxSize()) {
                when {
                    state.candles.isNotEmpty() -> {
                        // Shared chart references for sync
                        val priceChartRef = remember { mutableStateOf<CombinedChart?>(null) }
                        val stochChartRef = remember { mutableStateOf<LineChart?>(null) }
                        val rsiChartRef = remember { mutableStateOf<LineChart?>(null) }

                        Box(modifier = Modifier.weight(2.5f)) {
                            PriceChart(
                                state = state,
                                prefs = prefs,
                                onOpenIndicators = { showIndicators.value = true },
                                priceChartRef = priceChartRef,
                                stochChartRef = stochChartRef,
                                rsiChartRef = rsiChartRef
                            )
                        }
                        if (prefs.stochVisible) {
                            Box(modifier = Modifier.weight(1f)) {
                                StochRSIChart(state, stochChartRef)
                            }
                        }
                        if (prefs.rsiVisible) {
                            Box(modifier = Modifier.weight(1f)) {
                                RsiChart(state, rsiChartRef)
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

            if (showIndicators.value) {
                ModalBottomSheet(
                    onDismissRequest = { showIndicators.value = false },
                    containerColor = Color(0xFF141518)
                ) {
                    IndicatorsSheet(
                        prefs = prefs,
                        onToggleBB = { viewModel.toggleBB() },
                        onToggleProfile = { viewModel.toggleProfile() },
                        onToggleVolume = { viewModel.toggleVolumeSub() },
                        onToggleStoch = { viewModel.toggleStochSub() },
                        onToggleRsi = { viewModel.toggleRsiSub() },
                        onToggleMA = { viewModel.toggleMA(it) },
                        onConfigureMA = { maSheetPeriod.value = it }
                    )
                }
            }

            // ponytail: config de una MA en su propio sheet encima del de indicadores
            maSheetPeriod.value?.let { period ->
                prefs.mas.firstOrNull { it.period == period }?.let { ma ->
                    ModalBottomSheet(
                        onDismissRequest = { maSheetPeriod.value = null },
                        containerColor = Color(0xFF141518)
                    ) {
                        MaConfigSheet(
                            ma = ma,
                            onToggleMA = { viewModel.toggleMA(ma.period) },
                            onMAColor = { hex -> viewModel.setMAColor(ma.period, hex) },
                            onMAWidth = { w -> viewModel.setMAWidth(ma.period, w) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
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

    // ponytail: futuros MEXC usan BTC_USDT con guion bajo
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
    return if (abs(value) < 1.0) {
        String.format(Locale.US, "%.4f", value)
    } else {
        String.format(Locale.US, "%.2f", value)
    }
}

// ponytail: duracion de cada vela para el countdown al cierre
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

private fun formatCandleCountdown(interval: String, candleTime: Long): String {
    val dur = intervalDurationMs(interval)
    if (dur <= 0L) return "--:--"
    val totalSec = ((candleTime + dur - System.currentTimeMillis()).coerceAtLeast(0L)) / 1000L
    return when {
        totalSec >= 86_400L -> String.format(
            Locale.US,
            "%dd %02dh",
            totalSec / 86_400L,
            (totalSec % 86_400L) / 3600L
        )
        totalSec >= 3600L -> String.format(
            Locale.US,
            "%02d:%02d",
            totalSec / 3600L,
            (totalSec % 3600L) / 60L
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

private data class PriceRangeSelection(
    val startIndex: Int,
    val endIndex: Int? = null
) {
    val isComplete: Boolean = endIndex != null
}

// ─── PRICE CHART ────────────────────────────────────────────────────────────
@Composable
fun PriceChart(
    state: CryptoDetailState,
    prefs: IndicatorPrefs,
    onOpenIndicators: () -> Unit,
    priceChartRef: MutableState<CombinedChart?>,
    stochChartRef: MutableState<LineChart?>,
    rsiChartRef: MutableState<LineChart?>
) {
    val stateRef = remember { mutableStateOf(state) }
    // ponytail: onDraw del chart es closure de fabrica, lee prefs via ref o queda stale
    val prefsRef = remember { mutableStateOf(prefs) }
    val lastRenderedDataKey = remember { mutableStateOf<String?>(null) }
    // ponytail: cambio de ajustes reconstruye datos sin resetear el zoom
    val lastPrefsKey = remember { mutableStateOf<String?>(null) }
    val rangeToolEnabled = remember { mutableStateOf(false) }
    val rangeSelection = remember { mutableStateOf<PriceRangeSelection?>(null) }
    val currentViewportKey = state.viewportKey()

    LaunchedEffect(currentViewportKey) {
        rangeSelection.value = null
    }

    // ponytail: retickea el countdown del precio actual cada segundo
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
                private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
                private val selectionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.WHITE
                    textSize = 24f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val selectionLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(220, 26, 29, 35)
                    style = Paint.Style.FILL
                }

                private val tagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
                // ponytail: linea + tag del precio actual estilo OKX
                private val lastPriceLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    strokeWidth = 2f
                    pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
                }
                private val lastPriceTagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                }
                private val lastPriceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.WHITE
                    textSize = 24f
                    textAlign = Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                // ponytail: volumen taker apilado transparente detras de las velas
                private val takerBuyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(110, 38, 166, 154)
                    style = Paint.Style.FILL
                }
                private val takerSellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(110, 239, 83, 80)
                    style = Paint.Style.FILL
                }
                private val takerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(200, 173, 177, 184)
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val takerTotalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.WHITE
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val takerBuyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor("#26A69A")
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val takerSellTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor("#EF5350")
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val yTagRect = RectF()
                private val xTagRect = RectF()

                private var lastTouchYPx = -1f
                private var downX = 0f
                private var downY = 0f

                private fun touchToCandleIndex(x: Float, y: Float, size: Int): Int? {
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()
                    if (x < contentLeft || x > contentRight || y < contentTop || y > contentBottom) return null
                    val values = getTransformer(YAxis.AxisDependency.LEFT).getValuesByTouchPoint(x, y)
                    return values.x.roundToInt().coerceIn(0, size - 1)
                }

                private fun applyRangeTap(x: Float, y: Float): Boolean {
                    val candles = stateRef.value.candles
                    if (candles.isEmpty()) return false
                    val index = touchToCandleIndex(x, y, candles.size) ?: return false
                    val current = rangeSelection.value
                    rangeSelection.value = if (current == null || current.isComplete) {
                        PriceRangeSelection(startIndex = index)
                    } else {
                        current.copy(endIndex = index)
                    }
                    highlightValue(null)
                    lastTouchYPx = -1f
                    syncHighlights(this, stochChartRef.value, rsiChartRef.value)
                    invalidate()
                    return true
                }

                // ponytail: linea punteada + tag con precio actual y countdown al cierre
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
                        GraphicsColor.parseColor("#26A69A")
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

                // ponytail: volumen taker apilado (buy abajo, sell arriba) al fondo del chart
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
                            // ponytail: sin dato taker, barra simple del color de la vela
                            val h = (areaH * (c.volume / maxVol)).toFloat()
                            if (h > 0f) {
                                val paint = if (c.close >= c.open) takerBuyPaint else takerSellPaint
                                canvas.drawRect(cx - barW / 2f, base - h, cx + barW / 2f, base, paint)
                            }
                        }
                    }
                }

                // ponytail: leyenda C/V tomador estilo OKX arriba a la izq
                private fun drawTakerLegend(canvas: Canvas, candles: List<CandleData>) {
                    if (!prefsRef.value.volumeVisible) return
                    if (candles.isEmpty()) return
                    val current = stateRef.value
                    val h = highlighted?.getOrNull(0)
                    val idx = h?.x?.toInt()?.coerceIn(0, candles.size - 1) ?: (candles.size - 1)
                    val c = candles[idx]
                    val buy = c.takerBuyVol.coerceIn(0.0, c.volume)
                    val sell = (c.volume - buy).coerceAtLeast(0.0)
                    val baseAsset = splitTradingPair(current.detail?.symbol ?: current.symbol, current.source).baseAsset

                    val density = context.resources.displayMetrics.density
                    val textSize = density * 10f
                    takerLabelPaint.textSize = textSize
                    takerTotalPaint.textSize = textSize
                    takerBuyTextPaint.textSize = textSize
                    takerSellTextPaint.textSize = textSize
                    val hasMA = prefsRef.value.mas.any { it.visible }
                    var x = viewPortHandler.contentLeft() + density * 5f
                    val y = viewPortHandler.contentTop() + density * (if (hasMA) 40f else 16f)

                    x += drawLegendSegment(canvas, "C/V tomador  ", x, y, takerLabelPaint)
                    x += drawLegendSegment(canvas, "Total($baseAsset) ${formatVol(c.volume.toString())}   ", x, y, takerTotalPaint)
                    x += drawLegendSegment(canvas, "Buy($baseAsset) ${formatVol(buy.toString())}   ", x, y, takerBuyTextPaint)
                    drawLegendSegment(canvas, "Sell($baseAsset) ${formatVol(sell.toString())}", x, y, takerSellTextPaint)
                }

                private fun drawLegendSegment(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint): Float {
                    canvas.drawText(text, x, y, paint)
                    return paint.measureText(text)
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
                    val startIndex = selection.startIndex.coerceIn(0, candles.size - 1)
                    val endIndex = selection.endIndex?.coerceIn(0, candles.size - 1)
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()

                    if (endIndex == null) {
                        val start = candles[startIndex]
                        val pts = floatArrayOf(startIndex.toFloat(), start.close.toFloat())
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

                    val leftIndex = min(startIndex, endIndex)
                    val rightIndex = max(startIndex, endIndex)
                    val leftCandle = candles[leftIndex]
                    val rightCandle = candles[rightIndex]
                    val pts = floatArrayOf(
                        leftIndex.toFloat(),
                        leftCandle.close.toFloat(),
                        rightIndex.toFloat(),
                        rightCandle.close.toFloat()
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

                    val change = rightCandle.close - leftCandle.close
                    val changePct = if (leftCandle.close != 0.0) change / leftCandle.close * 100.0 else 0.0
                    val isUp = changePct >= 0.0
                    selectionStrokePaint.color = if (isUp) GraphicsColor.argb(230, 30, 203, 129) else GraphicsColor.argb(230, 246, 70, 93)
                    selectionFillPaint.color = if (isUp) GraphicsColor.argb(42, 30, 203, 129) else GraphicsColor.argb(42, 246, 70, 93)

                    yTagRect.set(leftX, adjustedTop, rightX, adjustedBottom)
                    canvas.drawRect(yTagRect, selectionFillPaint)
                    canvas.drawRect(yTagRect, selectionStrokePaint)
                    canvas.drawLine(leftX, contentTop, leftX, contentBottom, selectionStrokePaint)
                    canvas.drawLine(rightX, contentTop, rightX, contentBottom, selectionStrokePaint)
                    canvas.drawCircle(leftX, firstY, 6f, dotPaint)
                    canvas.drawCircle(rightX, lastY, 6f, dotPaint)

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

                override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
                    if (event == null) return false

                    if (rangeToolEnabled.value) {
                        parent.requestDisallowInterceptTouchEvent(true)
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                downX = event.x
                                downY = event.y
                                return true
                            }
                            android.view.MotionEvent.ACTION_UP -> {
                                val dx = abs(event.x - downX)
                                val dy = abs(event.y - downY)
                                if (dx < 14f && dy < 14f) {
                                    performClick()
                                    applyRangeTap(event.x, event.y)
                                }
                                return true
                            }
                            android.view.MotionEvent.ACTION_CANCEL -> return true
                        }
                        return true
                    }

                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val dx = abs(event.x - downX)
                            val dy = abs(event.y - downY)

                            // If highlight is shown and we move, update it and BLOCK chart panning
                            if (event.pointerCount == 1 && (dx > 10f || dy > 10f) && highlighted != null && highlighted.isNotEmpty()) {
                                isDragEnabled = false // Lock chart motion
                                parent.requestDisallowInterceptTouchEvent(true)

                                lastTouchYPx = event.y
                                val h = getHighlightByTouchPoint(event.x, event.y)
                                if (h != null) {
                                    highlightValue(h, true)
                                }
                                invalidate()
                                return true // Consume movement to prevent chart panning
                            }
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            val dx = abs(event.x - downX)
                            val dy = abs(event.y - downY)

                            isDragEnabled = true // Restore for next potential gesture

                            // Detect a TAP
                            if (dx < 10f && dy < 10f) {
                                performClick()
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
                                return true
                            }
                        }
                    }
                    return super.onTouchEvent(event)
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
                    super.onDraw(canvas)
                    drawTakerLegend(canvas, entries)
                    
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
                    drawLastPriceTag(canvas, entries)

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
                    
                    val priceText = formatPriceForChart(priceAtTouch)
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
                    val dateText = xAxis.valueFormatter.getFormattedValue(h.x)
                    val twX = tagTextPaint.measureText(dateText)
                    xTagRect.set(px - twX/2 - 10f, contentBottom, px + twX/2 + 10f, contentBottom + thY + 16f)
                    canvas.drawRoundRect(xTagRect, 4f, 4f, tagBackgroundPaint)
                    canvas.drawText(dateText, xTagRect.centerX(), xTagRect.centerY() + thY/3, tagTextPaint)
                }
            }.apply {
                setupCommonChartParams()
                marker = OKXChartMarker(context) { stateRef.value }
                
                // ponytail: eje a la derecha SOBRE el grafico, las velas pasan por detras
                axisRight.apply {
                    isEnabled = true
                    setDrawLabels(true)
                    setDrawGridLines(false)
                    setDrawAxisLine(false)
                    setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
                    setLabelCount(6, false)
                    textColor = "#ADB1B8".toColorInt()
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return formatPriceForChart(value.toDouble())
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
            }
        },
        update = { chart ->
            stateRef.value = state
            prefsRef.value = prefs
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
                        axisDependency = YAxis.AxisDependency.LEFT
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
                    lineData.addDataSet(createBBLineDataSet(upperEntries, "Upper", bbColor))
                    lineData.addDataSet(createBBLineDataSet(lowerEntries, "Lower", bbColor))
                    lineData.addDataSet(createBBLineDataSet(middleEntries, "Middle", middleColor, 1.2f))
                    prefs.mas.filter { it.visible }.forEach { ma ->
                        maLineDataSet(ma, state.maLines[ma.period] ?: emptyList())?.let {
                            lineData.addDataSet(it)
                        }
                    }
                    combinedData.setData(lineData)
                } else {
                    combinedData.setData(CandleData(createCandleDataSet(candleEntries)))
                    val maOnly = LineData()
                    prefs.mas.filter { it.visible }.forEach { ma ->
                        maLineDataSet(ma, state.maLines[ma.period] ?: emptyList())?.let {
                            maOnly.addDataSet(it)
                        }
                    }
                    if (maOnly.dataSetCount > 0) {
                        combinedData.setData(maOnly)
                    }
                }
                chart.data = combinedData
                if (isNewDataset) {
                    chart.applySyncAndInitialZoom(state.candles, resetViewport = isNewDataset, onPositioned = {
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
            }
        }
    )

        // ponytail: leyenda MA estilo OKX arriba a la izq, cada una en su color
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 8.dp, start = 8.dp)
        ) {
            prefs.mas.filter { it.visible }.forEach { ma ->
                val last = state.maLines[ma.period]?.lastOrNull()?.second
                if (last != null) {
                    Text(
                        text = "MA${ma.period} ${formatPriceForChart(last)}   ",
                        color = Color(ma.colorHex.toColorInt()),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = Color(0xCC1A1D23),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onOpenIndicators() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Indicadores",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .background(
                        color = if (rangeToolEnabled.value) Color(0xCC1ECB81) else Color(0xCC1A1D23),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable {
                        rangeToolEnabled.value = !rangeToolEnabled.value
                        rangeSelection.value = null
                        priceChartRef.value?.apply {
                            highlightValue(null)
                            syncHighlights(this, stochChartRef.value, rsiChartRef.value)
                            invalidate()
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (rangeToolEnabled.value) "Medir ON" else "Medir",
                    color = if (rangeToolEnabled.value) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─── STOCHRSI CHART ──────────────────────────────────────────────────────────
@Composable
fun StochRSIChart(
    state: CryptoDetailState,
    chartRef: MutableState<LineChart?>
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
                    axisMaximum = 135f
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
                lastRenderedDataKey.value = viewportKey
            }
        }
    )
}

@Composable
fun RsiChart(
    state: CryptoDetailState,
    chartRef: MutableState<LineChart?>
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
                private val rsiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor("#B39DDB")
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
    onPositioned: (() -> Unit)? = null
) {
    if (data.isEmpty()) return
    if (resetViewport) {
        notifyDataSetChanged()
        viewPortHandler.setMinimumScaleX(1f)
        viewPortHandler.setMaximumScaleX(1_000_000f)
        viewPortHandler.setMinimumScaleY(1f)
        viewPortHandler.setMaximumScaleY(1_000_000f)
        if (!skipPositioning) {
            post {
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
    xAxis.axisMinimum = source.xAxis.axisMinimum
    xAxis.axisMaximum = source.xAxis.axisMaximum

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

    viewPortHandler.refresh(targetMatrix, this, true)
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
        private val hourSdf = SimpleDateFormat("MM/dd HH:mm", Locale.US)

        override fun getFormattedValue(value: Float): String {
            if (state.candles.isEmpty()) return ""
            val idx = value.toInt().coerceIn(0, state.candles.size - 1)
            val sdf = when {
                state.candles.spansMultipleYears() -> yearSdf
                state.selectedInterval.isCalendarInterval() -> daySdf
                else -> hourSdf
            }
            return sdf.format(Date(state.candles[idx].time))
        }
    }

    val spansYears = state.candles.spansMultipleYears()
    val isCalendar = state.selectedInterval.isCalendarInterval()
    labelRotationAngle = when {
        spansYears -> -30f
        !isCalendar -> -18f
        else -> 0f
    }
    textSize = if (spansYears) 9f else 10f
    yOffset = if (labelRotationAngle == 0f) 4f else 8f
    setLabelCount(
        when {
            spansYears -> 3
            !isCalendar -> 3
            else -> 4
        },
        false
    )
}

private fun BarLineChartBase<*>.setupCommonChartParams() {
    description.isEnabled = false
    legend.isEnabled = false
    setTouchEnabled(true)
    isDragEnabled = true
    setScaleEnabled(true)
    setPinchZoom(false)
    isScaleXEnabled = true
    isScaleYEnabled = true
    setBackgroundColor(GraphicsColor.BLACK)
    isHighlightPerDragEnabled = true
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
    shadowColor = "#F4F4F4".toColorInt()
    // ponytail: mecha fina estilo OKX, el ancho fijo en px se ve grueso al alejar
    shadowWidth = 0.7f
    decreasingColor = "#EF5350".toColorInt()
    increasingColor = "#26A69A".toColorInt()
    neutralColor = "#ADB1B8".toColorInt()
    decreasingPaintStyle = Paint.Style.FILL
    increasingPaintStyle = Paint.Style.FILL
    setDrawValues(false)
    shadowColorSameAsCandle = true
    // ponytail: cuerpos gruesos pegaditos estilo OKX, poco gap para que la mecha no domine
    barSpace = 0.18f
    highLightColor = "#ADB1B8".toColorInt()
    highlightLineWidth = 1f
    setDrawHorizontalHighlightIndicator(false)
    setDrawVerticalHighlightIndicator(false)
    enableDashedHighlightLine(10f, 5f, 0f)
}

private fun createBBLineDataSet(entries: List<Entry>, label: String, color: Int, width: Float = 1f, highlight: Boolean = false) = LineDataSet(entries, label).apply {
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

// ponytail: una MA = un LineDataSet con su color/grosor, etiqueta MA<periodo>
private fun maLineDataSet(ma: MaConfig, line: List<Pair<Long, Double>>): LineDataSet? {
    if (line.isEmpty()) return null
    val entries = ArrayList<Entry>(line.size)
    line.forEach { value ->
        entries.add(Entry(value.first.toFloat(), value.second.toFloat()))
    }
    return createBBLineDataSet(entries, "MA${ma.period}", ma.colorHex.toColorInt(), ma.width)
}

// ponytail: tick en vivo actualiza el ultimo punto de cada MA sin reconstruir
private fun updateLastMAInPlace(chart: CombinedChart, state: CryptoDetailState) {
    val lineData = chart.data?.lineData ?: return
    for (i in 0 until lineData.dataSetCount) {
        val ds = lineData.getDataSetByIndex(i)
        if (!ds.label.startsWith("MA")) continue
        val period = ds.label.removePrefix("MA").toIntOrNull() ?: continue
        val last = state.maLines[period]?.lastOrNull() ?: continue
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

// ponytail: tick en vivo actualiza el ultimo punto del RSI sin reconstruir
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
    onToggleMA: (Int) -> Unit,
    onConfigureMA: (Int) -> Unit
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
        // ponytail: MAs en subseccion colapsable con resumen de activas
        val maExpanded = remember { mutableStateOf(false) }
        val maActive = prefs.mas.count { it.visible }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { maExpanded.value = !maExpanded.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Medias móviles ($maActive/5)",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (maExpanded.value) "Toca para ocultar" else "MA20 · MA55 · MA75 · MA100 · MA200",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            Text(
                text = if (maExpanded.value) "▾" else "▸",
                color = Color.Gray,
                fontSize = 16.sp
            )
        }
        if (maExpanded.value) {
            prefs.mas.forEach { ma ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onConfigureMA(ma.period) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ponytail: puntito con el color actual de la MA
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(Color(ma.colorHex.toColorInt()), CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MA${ma.period}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Toca para configurar",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                Switch(checked = ma.visible, onCheckedChange = { onToggleMA(ma.period) })
            }
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
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ponytail: config de una sola MA en su propio bottom sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaConfigSheet(
    ma: MaConfig,
    onToggleMA: () -> Unit,
    onMAColor: (String) -> Unit,
    onMAWidth: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color(ma.colorHex.toColorInt()), CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MA${ma.period}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Media móvil simple",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            Switch(checked = ma.visible, onCheckedChange = { onToggleMA() })
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Color",
            color = Color.Gray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IndicatorPrefs.PRESET_COLORS.forEach { hex ->
                val selected = hex.equals(ma.colorHex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(hex.toColorInt()), CircleShape)
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = if (selected) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { onMAColor(hex) }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Grosor de línea",
            color = Color.Gray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        // ponytail: slider local, guarda solo al soltar para no spamear DataStore
        val sliderPos = remember(ma.width) { mutableStateOf(ma.width) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = sliderPos.value,
                onValueChange = { sliderPos.value = it },
                onValueChangeFinished = { onMAWidth(sliderPos.value) },
                valueRange = 0.5f..3f,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = String.format(Locale.US, "%.1f", sliderPos.value),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
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
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}
