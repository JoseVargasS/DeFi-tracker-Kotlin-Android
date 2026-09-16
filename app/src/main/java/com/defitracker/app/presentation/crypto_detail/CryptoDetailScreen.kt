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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.defitracker.app.ui.theme.Lato
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
    val maSheetPeriod = remember { mutableStateOf<Int?>(null) }
    // ponytail: modo expandir, el chart manda y los TFs bajan
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
                            fontFamily = Lato
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
            // Stats Header (compacto en expandir, el chart manda)
            state.detail?.let { detail ->
                val detailPair = splitTradingPair(detail.symbol, state.source)
                if (chartExpanded.value) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = detail.price,
                            color = if (detail.isPositive) Color(0xFF1ECB81) else Color(0xFFF6465D),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Lato
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${if (detail.isPositive) "+" else ""}${detail.priceChangePercent}%",
                            color = if (detail.isPositive) Color(0xFF1ECB81) else Color(0xFFF6465D),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Lato
                        )
                    }
                } else {
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
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Lato
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = detailPair.displayName, color = Color.Gray, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${if (detail.isPositive) "+" else ""}${detail.priceChangePercent}%",
                                color = if (detail.isPositive) Color(0xFF1ECB81) else Color(0xFFF6465D),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = Lato
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

            Spacer(modifier = Modifier.height(8.dp))

            // Stacked Charts Area (subs compactos, el protagonista es el chart)
            Column(modifier = Modifier.fillMaxSize()) {
                when {
                    state.candles.isNotEmpty() -> {
                        // Shared chart references for sync
                        val priceChartRef = remember { mutableStateOf<CombinedChart?>(null) }
                        val stochChartRef = remember { mutableStateOf<LineChart?>(null) }
                        val rsiChartRef = remember { mutableStateOf<LineChart?>(null) }

                        Box(modifier = Modifier.weight(if (chartExpanded.value) 4f else 2.5f)) {
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
                        if (chartExpanded.value) {
                            IntervalRow(
                                selected = state.selectedInterval,
                                onPick = { viewModel.loadChartData(it) }
                            )
                        }
                        if (prefs.stochVisible) {
                            Box(modifier = Modifier.weight(if (chartExpanded.value) 0.55f else 0.7f)) {
                                StochRSIChart(state, stochChartRef)
                            }
                        }
                        if (prefs.rsiVisible) {
                            Box(modifier = Modifier.weight(if (chartExpanded.value) 0.55f else 0.7f)) {
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
                        onConfigureMA = { maSheetPeriod.value = it },
                        onToggleSmcStructure = { viewModel.toggleSmcStructure() },
                        onToggleSmcOB = { viewModel.toggleSmcOrderBlocks() },
                        onToggleSmcFvg = { viewModel.toggleSmcFvg() },
                        onToggleSmcPremium = { viewModel.toggleSmcPremium() },
                        onToggleSmcEqhl = { viewModel.toggleSmcEqhl() },
                        onToggleSmcLiq = { viewModel.toggleSmcLiquidity() }
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

// ponytail: TFs arriba en normal, debajo del chart en expandir
@Composable
fun IntervalRow(selected: String, onPick: (String) -> Unit) {
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
            val isSelected = selected == interval.second
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(36.dp)
                    .clickable { onPick(interval.second) },
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

private enum class ChartTouchMode {
    UNDECIDED,
    PAN,
    Y_ZOOM,
    PINCH_X_ZOOM
}

// ponytail: un solo boton Dibujo, adentro se elige Medir o Fibo
enum class DrawingTool {
    NONE,
    MEASURE,
    FIBO
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
    // ponytail: onDraw del chart es closure de fabrica, lee prefs via ref o queda stale
    val prefsRef = remember { mutableStateOf(prefs) }
    val lastRenderedDataKey = remember { mutableStateOf<String?>(null) }
    // ponytail: cambio de ajustes reconstruye datos sin resetear el zoom
    val lastPrefsKey = remember { mutableStateOf<String?>(null) }
    val drawingTool = remember { mutableStateOf(DrawingTool.NONE) }
    val showDrawingSheet = remember { mutableStateOf(false) }
    val showFibLevels = remember { mutableStateOf(false) }
    val rangeSelection = remember { mutableStateOf<MeasureZone?>(null) }
    val fibPendingStart = remember { mutableStateOf<FibAnchor?>(null) }
    // ponytail: varios fibos, cada uno con su estilo; el seleccionado muestra su barrita
    val fibOverlays = viewModel.fibOverlays.value
    val selectedFibId = viewModel.selectedFibId.value
    val overlaysRef = remember { mutableStateOf<List<FibOverlay>>(emptyList()) }
    val selectedFibRef = remember { mutableStateOf<String?>(null) }
    val toolRef = remember { mutableStateOf(DrawingTool.NONE) }
    val pendingStartRef = remember { mutableStateOf<FibAnchor?>(null) }
    val currentViewportKey = state.viewportKey()

    LaunchedEffect(currentViewportKey) {
        rangeSelection.value = null
        // ponytail: los fibos NO se borran, viven por tiempo y se remapean solos
        fibPendingStart.value = null
        pendingStartRef.value = null
    }
    LaunchedEffect(fibOverlays) { overlaysRef.value = fibOverlays }
    LaunchedEffect(selectedFibId) { selectedFibRef.value = selectedFibId }
    LaunchedEffect(drawingTool.value) { toolRef.value = drawingTool.value }
    LaunchedEffect(fibPendingStart.value) { pendingStartRef.value = fibPendingStart.value }

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
                // ponytail: fibo estilo OKX, color/grosor editables desde la barra flotante
                private val fibLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.WHITE
                    strokeWidth = 2f
                    style = Paint.Style.STROKE
                }
                val fibLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.WHITE
                    textSize = 24f
                    textAlign = Paint.Align.LEFT
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
                // ponytail: SMC tenue, las velas mandan (alfas bajos, labels chicos)
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
                // ponytail: bordes punteados del color del lado para leer las cajas
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
                    color = GraphicsColor.argb(190, 38, 166, 154)
                    strokeWidth = 2f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(0.5f, 6f), 0f)
                }
                val smcBearLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(190, 239, 83, 80)
                    strokeWidth = 2f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(0.5f, 6f), 0f)
                }
                val smcBullLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(235, 38, 166, 154)
                    textSize = 24f
                    textAlign = Paint.Align.LEFT
                }
                val smcBearLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(235, 239, 83, 80)
                    textSize = 24f
                    textAlign = Paint.Align.LEFT
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
                // ponytail: gris donde el FVG de otro TF pisa tu zona
                val smcConfluencePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(60, 158, 158, 158)
                    style = Paint.Style.FILL
                }
                val smcConfluenceTagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(200, 189, 189, 189)
                    textSize = 20f
                    textAlign = Paint.Align.LEFT
                }
                // ponytail: liquidez tenue, punteada como el resto del SMC
                val smcBslPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(150, 33, 150, 243)
                    strokeWidth = 1.6f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(0.5f, 6f), 0f)
                }
                val smcSslPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(150, 255, 152, 0)
                    strokeWidth = 1.6f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(0.5f, 6f), 0f)
                }
                val smcSweptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(70, 158, 158, 158)
                    strokeWidth = 1.4f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(0.5f, 6f), 0f)
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
                // ponytail: linea + tag del precio actual estilo OKX
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
                // ponytail: volumen taker apilado transparente detras de las velas
                private val takerBuyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(110, 38, 166, 154)
                    style = Paint.Style.FILL
                }
                private val takerSellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(110, 239, 83, 80)
                    style = Paint.Style.FILL
                }
                val takerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(200, 173, 177, 184)
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val takerTotalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.WHITE
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val takerBuyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor("#26A69A")
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val takerSellTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor("#EF5350")
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
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

                private fun applyPan(dx: Float, dy: Float) {
                    val matrix = Matrix(viewPortHandler.matrixTouch)
                    matrix.postTranslate(dx, dy)
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
                    val price = getTransformer(YAxis.AxisDependency.LEFT).getValuesByTouchPoint(x, y).y.toDouble()
                    return index to price
                }

                private fun applyRangeTap(x: Float, y: Float): Boolean {
                    val (index, price) = measureAnchor(x, y) ?: return false
                    val current = rangeSelection.value
                    // ponytail: fija al completar, como el fibo; re-entrar a Medir reinicia
                    if (current != null && current.isComplete) return true
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

                // ponytail: leyenda C/V tomador abajo a la izq, nunca pisa las MA
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
                    var x = viewPortHandler.contentLeft() + density * 5f
                    val y = viewPortHandler.contentBottom() - density * 8f

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

                    // ponytail: esquinas de la diagonal, el precio es del toque (como el fibo)
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

                    // ponytail: handles en las esquinas de la diagonal solo en modo Medir
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

                // ponytail: 1/2 = esquinas de la diagonal, 3 = dentro de la zona
                private fun measureHitTest(x: Float, y: Float): Int {
                    val sel = rangeSelection.value ?: return 0
                    if (!sel.isComplete) return 0
                    val candles = stateRef.value.candles
                    if (candles.isEmpty()) return 0
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val lIdx = min(sel.startIdx, sel.endIdx!!).coerceIn(0, candles.size - 1)
                    val rIdx = max(sel.startIdx, sel.endIdx!!).coerceIn(0, candles.size - 1)
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
                // ponytail: handle=resize, cuerpo=mover, tap=seleccionar; candado bloquea gestos
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
                    val index = touchToCandleIndex(x, y, candles.size) ?: return null
                    val price = getTransformer(YAxis.AxisDependency.LEFT).getValuesByTouchPoint(x, y).y.toDouble()
                    return FibAnchor(candles[index].time, price)
                }

                private fun overlayPixels(o: FibOverlay): Pair<android.graphics.PointF?, android.graphics.PointF?> {
                    val candles = stateRef.value.candles
                    if (candles.isEmpty()) return null to null
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val sIdx = timeToIndex(candles, o.start.time)
                    val eIdx = timeToIndex(candles, o.end.time)
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

                // ponytail: devuelve (id, zona, locked) del fibo mas cercano al toque, o null
                private fun fibHitTest(x: Float, y: Float): Triple<String, Int, Boolean>? {
                    val candles = stateRef.value.candles
                    if (candles.isEmpty()) return null
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val contentRight = viewPortHandler.contentRight()
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
                        // ponytail: cuerpo = lineas de nivel + guia diagonal (mover)
                        val leftX = min(s.x, e.x)
                        val rightX = max(s.x, e.x)
                        var bodyDist = distToSegment(x, y, s.x, s.y, e.x, e.y)
                        o.levelsSorted().forEach { ratio ->
                            val price = fibLevelPrice(o.start.price, o.end.price, ratio)
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
                    val anchor = anchorFromTouch(x, y) ?: return
                    onFibTap?.invoke(anchor)
                    invalidate()
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

                    val sIdx = timeToIndex(candles, o.start.time)
                    val eIdx = timeToIndex(candles, o.end.time)
                    val sPts = floatArrayOf(sIdx.toFloat(), o.start.price.toFloat())
                    trans.pointValuesToPixel(sPts)
                    val ePts = floatArrayOf(eIdx.toFloat(), o.end.price.toFloat())
                    trans.pointValuesToPixel(ePts)
                    val leftX = min(sPts[0], ePts[0]).coerceIn(contentLeft, contentRight)
                    // ponytail: lineas acotadas al rango inicio-fin, no hasta el borde
                    val rightX = max(sPts[0], ePts[0]).coerceIn(contentLeft, contentRight)

                    val lineColor = try {
                        GraphicsColor.parseColor(o.colorHex)
                    } catch (_: Exception) {
                        GraphicsColor.WHITE
                    }
                    fibLinePaint.color = lineColor
                    fibLinePaint.strokeWidth = (o.width * context.resources.displayMetrics.density).coerceAtLeast(1f)
                    // ponytail: seleccionado a full, los demas tenues
                    fibLinePaint.alpha = if (selected || selectedFibRef.value == null) 255 else 140
                    fibLabelPaint.color = lineColor

                    // ponytail: linea guia entre anclas para ver la tendencia del trazo
                    canvas.drawLine(sPts[0], sPts[1], ePts[0], ePts[1], fibLinePaint)

                    levels.forEach { ratio ->
                        val price = fibLevelPrice(o.start.price, o.end.price, ratio)
                        val pts = floatArrayOf(0f, price.toFloat())
                        trans.pointValuesToPixel(pts)
                        val py = pts[1].coerceIn(contentTop, contentBottom)
                        canvas.drawLine(leftX, py, rightX, py, fibLinePaint)
                        // ponytail: izq solo ratio, el precio ya esta a la derecha
                        if (selected) {
                            val leftLabel = trimRatio(ratio)
                            val lw = fibLabelPaint.measureText(leftLabel) + 16f
                            val lh = fibLabelPaint.textSize + 10f
                            val lx = (leftX + 4f).coerceIn(contentLeft, (contentRight - lw).coerceAtLeast(contentLeft))
                            val ly = (py - lh / 2f).coerceIn(contentTop, (contentBottom - lh).coerceAtLeast(contentTop))
                            yTagRect.set(lx, ly, lx + lw, ly + lh)
                            canvas.drawRoundRect(yTagRect, 4f, 4f, fibChipPaint)
                            canvas.drawText(leftLabel, lx + 8f, ly + lh - 7f, fibLabelPaint)
                            // ponytail: tag derecho al final de la linea (rango acotado)
                            val rightLabel = formatPriceForChart(price)
                            val rw = fibLabelPaint.measureText(rightLabel) + 16f
                            val rx = (rightX - rw).coerceIn(contentLeft, (contentRight - rw).coerceAtLeast(contentLeft))
                            yTagRect.set(rx, ly, rx + rw, ly + lh)
                            canvas.drawRoundRect(yTagRect, 4f, 4f, fibChipPaint)
                            canvas.drawText(rightLabel, rx + 8f, ly + lh - 7f, fibLabelPaint)
                        }
                    }

                    // ponytail: handles solo en el seleccionado, grises si esta candadeado
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
                    val pIdx = timeToIndex(candles, pending.time)
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
                // ponytail: fondo tenue detras de las velas (premium/discount)
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

                // ponytail: cajas y estructura sobre el chart pero tenues, las velas mandan
                private fun drawSmcForeground(
                    canvas: Canvas,
                    candles: List<CandleData>,
                    visibleStart: Int,
                    visibleEnd: Int
                ) {
                    val smc = stateRef.value.smc
                    if (candles.isEmpty()) return
                    val prefs = prefsRef.value
                    if (!prefs.smcOrderBlocks && !prefs.smcFvg && !prefs.smcStructure && !prefs.smcEqhl && !prefs.smcLiquidity) return
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()

                    if (prefs.smcOrderBlocks || prefs.smcFvg) {
                        smc.zones.forEach { z ->
                            val wantOb = prefs.smcOrderBlocks && z.kind == SmcZoneKind.ORDER_BLOCK
                            val wantFvg = prefs.smcFvg && z.kind == SmcZoneKind.FVG
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
                            // ponytail: borde punteado del lado + tag FVG solo si sigue vigente
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

                    if (prefs.smcStructure) {
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
                            drawSmcChip(canvas, label, labelPaint, xb[0], py, ev.bullish, contentLeft, contentRight, contentTop, contentBottom)
                        }
                    }

                    if (prefs.smcEqhl) {
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
                            drawSmcChip(canvas, label, smcEqLabelPaint, x2[0], py, false, contentLeft, contentRight, contentTop, contentBottom)
                        }
                    }

                    // ponytail: pools BSL/SSL + barridos, tags al borde derecho con stagger
                    if (prefs.smcLiquidity) {
                        // ponytail: SWEEP solo el mas reciente por lado, el resto linea pelada
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
                                // ponytail: tag al borde derecho, desplazado si choca con otro
                                val w = labelPaint.measureText(label) + 14f
                                val h = labelPaint.textSize + 8f
                                val lx = (contentRight - w).coerceAtLeast(contentLeft)
                                var ly = if (liq.isBuySide) {
                                    (py - h - 4f).coerceIn(contentTop, (contentBottom - h).coerceAtLeast(contentTop))
                                } else {
                                    (py + 4f).coerceIn(contentTop, (contentBottom - h).coerceAtLeast(contentTop))
                                }
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

                    // ponytail: bandas grises donde el FVG de otro TF pisa tu zona
                    if (prefs.smcFvg) {
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

                // ponytail: chip oscuro para que el tag se lea sobre las velas
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
                    contentBottom: Float
                ) {
                    val w = labelPaint.measureText(label) + 14f
                    val h = labelPaint.textSize + 8f
                    val lx = (anchorX + 4f).coerceIn(contentLeft, (contentRight - w).coerceAtLeast(contentLeft))
                    val ly = if (above) {
                        (anchorY - h - 4f).coerceIn(contentTop, (contentBottom - h).coerceAtLeast(contentTop))
                    } else {
                        (anchorY + 4f).coerceIn(contentTop, (contentBottom - h).coerceAtLeast(contentTop))
                    }
                    yTagRect.set(lx, ly, lx + w, ly + h)
                    canvas.drawRoundRect(yTagRect, 4f, 4f, smcChipPaint)
                    canvas.drawText(label, lx + 7f, ly + h - 6f, labelPaint)
                }

                private fun trimRatio(r: Float): String {
                    val s = String.format(Locale.US, "%.3f", r).trimEnd('0').trimEnd('.')
                    return if (s.isEmpty()) "0" else s
                }

                override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
                    if (event == null) return false

                    // ponytail: fibo primero en cualquier modo (menos Medir); vacio = sigue al flujo normal
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            if (toolRef.value == DrawingTool.MEASURE) {
                                // ponytail: en Medir manda la cinta, el fibo no intercepta
                            } else {
                                val hit = fibHitTest(event.x, event.y)
                            if (hit != null) {
                                val (id, zone, locked) = hit
                                onFibSelect?.invoke(id)
                                selectedFibRef.value = id
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
                                            ?: o?.let { timeToIndex(candles, it.start.time) } ?: 0
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
                                            // ponytail: mover cuerpo conserva tamaño (delta indice+precio)
                                            val candles = stateRef.value.candles
                                            val newIdx = touchToCandleIndex(event.x, event.y, candles.size)
                                            val newPrice = getTransformer(YAxis.AxisDependency.LEFT)
                                                .getValuesByTouchPoint(event.x, event.y).y.toDouble()
                                            if (newIdx != null && fibOrigStart != null && fibOrigEnd != null) {
                                                val dIdx = newIdx - fibGrabIdx
                                                val dPrice = newPrice - fibGrabPrice
                                                val s0 = timeToIndex(candles, fibOrigStart!!.time)
                                                val e0 = timeToIndex(candles, fibOrigEnd!!.time)
                                                val s1 = (s0 + dIdx).coerceIn(0, candles.size - 1)
                                                val e1 = (e0 + dIdx).coerceIn(0, candles.size - 1)
                                                val next = cur.copy(
                                                    start = FibAnchor(candles[s1].time, fibOrigStart!!.price + dPrice),
                                                    end = FibAnchor(candles[e1].time, fibOrigEnd!!.price + dPrice)
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
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            if (fibDragId != null) {
                                if (fibDragMoved) onFibCommit?.invoke(fibDragId!!)
                                else invalidate()
                                fibDragId = null
                                fibDragZone = 0
                                return true
                            }
                        }
                        android.view.MotionEvent.ACTION_CANCEL -> {
                            fibDragId = null
                            fibDragZone = 0
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
                                // ponytail: esquinas redimensionan, dentro mueve la zona
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
                                                // ponytail: mover conserva tamaño
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

                    // ponytail: modo colocacion, el tap crea puntos; arrastrar = pan para no atascarse
                    if (toolRef.value == DrawingTool.FIBO) {
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
                                    applyFibTap(event.x, event.y)
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
                            // ponytail: con cruz activa se ignora el segundo dedo
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
                                // ponytail: con cruz activa todo el grafico se bloquea, ni pinch
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

                            // ponytail: cruz activa sigue al dedo, pan/zoom bloqueados
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
                    drawFibOverlays(canvas, entries)
                    drawSmcForeground(canvas, entries, visibleStart, visibleEnd)
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
                // ponytail: etiquetas del canvas con la condensada, numeros mas definidos
                try {
                    androidx.core.content.res.ResourcesCompat.getFont(
                        context, com.defitracker.app.R.font.lato_semibold
                    )?.let { tf ->
                        labelPaint.typeface = tf
                        selectionTextPaint.typeface = tf
                        tagTextPaint.typeface = tf
                        lastPriceTextPaint.typeface = tf
                        fibLabelPaint.typeface = tf
                        takerLabelPaint.typeface = tf
                        takerTotalPaint.typeface = tf
                        takerBuyTextPaint.typeface = tf
                        takerSellTextPaint.typeface = tf
                        smcBullLabelPaint.typeface = tf
                        smcBearLabelPaint.typeface = tf
                        smcEqLabelPaint.typeface = tf
                        smcFvgTagPaint.typeface = tf
                        smcConfluenceTagPaint.typeface = tf
                        smcBslLabelPaint.typeface = tf
                        smcSslLabelPaint.typeface = tf
                        smcSweepLabelPaint.typeface = tf
                    }
                } catch (_: Exception) {}
                
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
                // ponytail: al completar el trazo se sale del modo colocacion solo
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
                // Rebuild CombinedChart renderer buffers after replacing the
                // CandleData/LineData objects from Compose.
                chart.notifyDataSetChanged()
                chart.invalidate()
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

        // ponytail: una sola fila arriba (leyendas que envuelven + botones) sin pisar el eje
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = 8.dp, start = 8.dp, end = 64.dp),
            verticalAlignment = Alignment.Top
        ) {
            FlowRow(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                prefs.mas.filter { it.visible }.forEach { ma ->
                    val last = state.maLines[ma.period]?.lastOrNull()?.second
                    if (last != null) {
                        Text(
                            text = "MA${ma.period} ${formatPriceForChart(last)}  ",
                            color = Color(ma.colorHex.toColorInt()),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Lato
                        )
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
            // ponytail: un solo boton Dibujo, adentro se elige Medir o Fibo
            val dibujoLabel = when (drawingTool.value) {
                DrawingTool.MEASURE -> "Medir ON"
                DrawingTool.FIBO -> "Fibo ON"
                DrawingTool.NONE -> "Dibujo"
            }
            val dibujoActive = drawingTool.value != DrawingTool.NONE
            Box(
                modifier = Modifier
                    .background(
                        color = if (dibujoActive) Color(0xCC1ECB81) else Color(0xCC1A1D23),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { showDrawingSheet.value = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dibujoLabel,
                    color = if (dibujoActive) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            }
        }

        // ponytail: barra del fibo seleccionado, visible aunque no estes en modo dibujo
        val selectedFib = fibOverlays.firstOrNull { it.id == selectedFibId }
        if (selectedFib != null) {
            FibEditBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 44.dp),
                fib = selectedFib,
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

        if (showDrawingSheet.value) {
            ModalBottomSheet(
                onDismissRequest = { showDrawingSheet.value = false },
                containerColor = Color(0xFF141518)
            ) {
                DrawingToolsSheet(
                    active = drawingTool.value,
                    fibCount = fibOverlays.size,
                    hasFib = selectedFib != null,
                    onPickMeasure = {
                        drawingTool.value = if (drawingTool.value == DrawingTool.MEASURE) DrawingTool.NONE else DrawingTool.MEASURE
                        rangeSelection.value = null
                        fibPendingStart.value = null
                        pendingStartRef.value = null
                        priceChartRef.value?.apply {
                            highlightValue(null)
                            syncHighlights(this, stochChartRef.value, rsiChartRef.value)
                            invalidate()
                        }
                        showDrawingSheet.value = false
                    },
                    onPickFibo = {
                        // ponytail: entrar a Fibo siempre arma uno nuevo, aunque ya existan
                        drawingTool.value = DrawingTool.FIBO
                        fibPendingStart.value = null
                        pendingStartRef.value = null
                        showDrawingSheet.value = false
                        priceChartRef.value?.invalidate()
                    },
                    onHideFib = {
                        selectedFib?.let { viewModel.toggleFibHidden(it.id) }
                        showDrawingSheet.value = false
                        priceChartRef.value?.invalidate()
                    },
                    onDeleteFib = {
                        selectedFib?.let {
                            viewModel.deleteFib(it.id)
                            overlaysRef.value = viewModel.fibOverlays.value
                            selectedFibRef.value = viewModel.selectedFibId.value
                        }
                        fibPendingStart.value = null
                        pendingStartRef.value = null
                        showDrawingSheet.value = false
                        priceChartRef.value?.invalidate()
                    },
                    onClose = { showDrawingSheet.value = false }
                )
            }
        }

        if (showFibLevels.value && selectedFib != null) {
            ModalBottomSheet(
                onDismissRequest = { showFibLevels.value = false },
                containerColor = Color(0xFF141518)
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
                    },
                    onClose = {
                        showFibLevels.value = false
                        priceChartRef.value?.invalidate()
                    }
                )
            }
        }

        // ponytail: expandir a lo OKX, esquina inferior izquierda del grafico
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
    // ponytail: numeros del chart condensados estilo OKX, con fallback silencioso
    try {
        androidx.core.content.res.ResourcesCompat.getFont(
            context, com.defitracker.app.R.font.lato_semibold
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

// ponytail: una MA = un LineDataSet con su color/grosor, etiqueta MA<periodo>
private fun maLineDataSet(ma: MaConfig, line: List<Pair<Long, Double>>): LineDataSet? {
    if (line.isEmpty()) return null
    val entries = ArrayList<Entry>(line.size)
    line.forEach { value ->
        entries.add(Entry(value.first.toFloat(), value.second.toFloat()))
    }
    return createBBLineDataSet(
        entries,
        "MA${ma.period}",
        ma.colorHex.toColorInt(),
        ma.width,
        axisDependency = YAxis.AxisDependency.RIGHT
    )
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
    onConfigureMA: (Int) -> Unit,
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

// ─── DIBUJO: SHEET + BARRA FIBO ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingToolsSheet(
    active: DrawingTool,
    hasFib: Boolean,
    fibCount: Int = 0,
    onPickMeasure: () -> Unit,
    onPickFibo: () -> Unit,
    onHideFib: () -> Unit,
    onDeleteFib: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Herramientas de dibujo", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrawingTopAction("Ocultar", active != DrawingTool.NONE, onClose)
            DrawingTopAction("Eliminar fibo", hasFib, onDeleteFib)
            DrawingTopAction(if (hasFib && active == DrawingTool.FIBO) "Fibo ON" else "Fibo", true, onPickFibo)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Líneas de tendencia", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrawingDisabledCell("Segmento", Modifier.weight(1f))
            DrawingDisabledCell("Línea", Modifier.weight(1f))
            DrawingDisabledCell("Recta", Modifier.weight(1f))
            DrawingDisabledCell("Flecha", Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Más herramientas", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrawingPickCell(
                label = if (active == DrawingTool.MEASURE) "Medir ON" else "Medir",
                selected = active == DrawingTool.MEASURE,
                modifier = Modifier.weight(1f),
                onClick = onPickMeasure
            )
            DrawingPickCell(
                label = "Retroceso de Fib",
                selected = active == DrawingTool.FIBO,
                modifier = Modifier.weight(1f),
                onClick = onPickFibo
            )
            DrawingDisabledCell("Línea de precio", Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        // ponytail: ocultar no cierra el modo, solo esconde el trazo
        if (hasFib) {
            Text(
                text = "Trazados: $fibCount — toca Fibo para agregar otro",
                color = Color.Gray,
                fontSize = 12.sp
            )
            TextButton(onClick = onHideFib) { Text("Ocultar / mostrar fibo", color = Color.Gray) }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DrawingTopAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color(0xFF1E2026), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) Color.White else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DrawingPickCell(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(
                if (selected) Color(0xFF1ECB81).copy(alpha = 0.25f) else Color(0xFF1E2026),
                RoundedCornerShape(8.dp)
            )
            .border(
                if (selected) 1.dp else 0.dp,
                if (selected) Color(0xFF1ECB81) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DrawingDisabledCell(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF1E2026).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.Gray.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

@Composable
fun FibEditBar(
    modifier: Modifier = Modifier,
    fib: FibOverlay,
    onColor: (String) -> Unit,
    onWidth: (Float) -> Unit,
    onLevels: () -> Unit,
    onToggleHide: () -> Unit,
    onToggleLock: () -> Unit,
    onDelete: () -> Unit
) {
    var showColors by remember { mutableStateOf(false) }
    var showWidths by remember { mutableStateOf(false) }
    val fibTint = try {
        Color(fib.colorHex.toColorInt())
    } catch (_: Exception) {
        Color.White
    }
    Row(
        modifier = modifier
            .background(Color(0xEE141518), RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // ponytail: lapiz abre paleta de colores
        Box {
            FibBarIconBtn(
                icon = Icons.Filled.Edit,
                tint = fibTint,
                desc = "Color",
                onClick = { showColors = true }
            )
            DropdownMenu(
                expanded = showColors,
                onDismissRequest = { showColors = false },
                modifier = Modifier.background(Color(0xFF1E2026))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    IndicatorPrefs.PRESET_COLORS.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { hex ->
                                val selected = hex.equals(fib.colorHex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(hex.toColorInt()), CircleShape)
                                        .border(
                                            width = if (selected) 2.dp else 0.dp,
                                            color = if (selected) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            onColor(hex)
                                            showColors = false
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
        // ponytail: 1px abre selector de grosor con preview de linea
        Box {
            Box(
                modifier = Modifier
                    .clickable { showWidths = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    fibWidthLabel(fib.width),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            DropdownMenu(
                expanded = showWidths,
                onDismissRequest = { showWidths = false },
                modifier = Modifier.background(Color(0xFF1E2026))
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
                                    .background(fibTint, RoundedCornerShape(1.dp))
                            )
                            Text(
                                fibWidthLabel(w),
                                color = if (w == fib.width) Color.White else Color.Gray,
                                fontSize = 13.sp,
                                fontWeight = if (w == fib.width) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
        // ponytail: tuerca = niveles y estilo del fibo seleccionado
        FibBarIconBtn(icon = Icons.Filled.Settings, tint = Color.White, desc = "Niveles", onClick = onLevels)
        // ponytail: ojo con raya cuando esta oculto
        FibBarIconBtn(
            icon = if (fib.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            tint = Color.Gray,
            desc = "Visible",
            onClick = onToggleHide
        )
        // ponytail: candado azul cerrado bloquea mover/resize
        FibBarIconBtn(
            icon = if (fib.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            tint = if (fib.locked) Color(0xFF2196F3) else Color.Gray,
            desc = "Bloquear",
            onClick = onToggleLock
        )
        FibBarIconBtn(icon = Icons.Filled.Delete, tint = Color.Gray, desc = "Eliminar", onClick = onDelete)
    }
}

@Composable
private fun FibBarIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    desc: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = desc, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FibLevelsSheet(
    fib: FibOverlay,
    onToggle: (Float) -> Unit,
    onColor: (String) -> Unit,
    onWidth: (Float) -> Unit,
    onClose: () -> Unit
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IndicatorPrefs.PRESET_COLORS.forEach { hex ->
                val selected = hex.equals(fib.colorHex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(hex.toColorInt()), CircleShape)
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = if (selected) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { onColor(hex) }
                )
            }
        }
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
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}
