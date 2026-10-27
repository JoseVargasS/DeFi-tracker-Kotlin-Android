package com.defitracker.app.presentation.crypto_detail

import com.defitracker.app.presentation.crypto_detail.chart.*
import com.defitracker.app.presentation.crypto_detail.sheets.*

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Color as GraphicsColor
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.defitracker.app.presentation.components.PillGrid
import com.defitracker.app.presentation.components.PillOption
import com.defitracker.app.presentation.components.SheetSectionTitle
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import com.defitracker.app.R
import com.defitracker.app.core.Timeframes
import com.defitracker.app.ui.theme.AppGold
import com.defitracker.app.ui.theme.AppGreen
import com.defitracker.app.ui.theme.AppRed
import com.defitracker.app.ui.theme.Black
import com.defitracker.app.ui.theme.CardBg
import com.defitracker.app.ui.theme.ChartColors
import com.defitracker.app.ui.theme.Geist
import com.defitracker.app.ui.theme.InputBg
import com.defitracker.app.ui.theme.LineDiv
import com.defitracker.app.ui.theme.SheetBg
import com.github.mikephil.charting.charts.BarChart
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
    // giro bloqueado en vertical solo aqui: sin recreate no hay parpadeo al rotar
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val prev = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            if (activity != null && prev != null) activity.requestedOrientation = prev
        }
    }
    val showIndicators = remember { mutableStateOf(false) }
    val showMaList = remember { mutableStateOf(false) }
    val showPriceAlerts = remember { mutableStateOf(false) }
    val priceAlerts by viewModel.priceAlertsFlow().collectAsState(initial = emptyList())
    val priceSound by viewModel.priceSoundFlow().collectAsState(initial = "")
    // velo liviano: mientras carga se atenua el chart viejo + barrita, sin desmontar nada.
    // El cooldown cubre el reencuadre (post) que llega 1-2 frames despues de la data
    var cooling by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            cooling = true
            kotlinx.coroutines.delay(150)
            cooling = false
        }
    }
    val chartDim = state.candles.isNotEmpty() && (state.isLoading || cooling)
    // revelado del primer encuadre: el chart nace invisible y aparece ya posicionado.
    // El watchdog evita invisible eterno si la señal no llegara
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(state.candles.isNotEmpty()) {
        if (state.candles.isNotEmpty()) {
            kotlinx.coroutines.delay(1500)
            revealed = true
        } else {
            revealed = false
        }
    }
    val revealAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(250)
    )
    // modo expandir, el chart manda y los TFs bajan
    val chartExpanded = remember { mutableStateOf(true) }
    val tradingPair = splitTradingPair(state.detail?.symbol ?: state.symbol, state.source)

    Scaffold(
        topBar = {
            // header delgado en 2 filas pa' darle aire al chart
            Column(
                modifier = Modifier
                    .background(Black)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = tradingPair.displayName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        fontFamily = Geist
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // badge de fuente siempre visible
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (state.source == "MEXC") AppGreen.copy(alpha = 0.2f)
                                else ChartColors.blue.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sourceLabel(state.source),
                            color = if (state.source == "MEXC") AppGreen else ChartColors.lightBlue,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // campanita de alertas LONG/SHORT de este par
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = { showPriceAlerts.value = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Alertas de precio",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        val activeCount = priceAlerts.count { it.enabled }
                        if (activeCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .background(AppGreen, CircleShape)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = activeCount.toString(),
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                // precio + % + H/L/Vol en una sola linea delgada
                state.detail?.let { detail ->
                    val stickyExt = state.daySubDollar
                    val quoteAsset = tradingPair.quoteAsset.ifBlank { "USDT" }
                    val vol = detail.quoteVolume.toDoubleValue()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatPriceForChart(detail.price.toDoubleValue(), stickyExt),
                            color = if (detail.isPositive) AppGreen else AppRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Geist
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${if (detail.isPositive) "+" else ""}${detail.priceChangePercent}%",
                            color = if (detail.isPositive) AppGreen else AppRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Geist
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = buildString {
                                append("H ${formatPriceForChart(detail.highPrice.toDoubleValue(), stickyExt)}")
                                append("  L ${formatPriceForChart(detail.lowPrice.toDoubleValue(), stickyExt)}")
                                if (vol > 0) append("  Vol ${formatCompactVol(vol)} $quoteAsset")
                            },
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontFamily = Geist,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        containerColor = Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Black)
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Stacked Charts Area (subs compactos, el protagonista es el chart)
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val areaHpx = with(LocalDensity.current) { maxHeight.toPx() }
                // X de la cruz en px de columna, la publica el onDraw del chart de precio (OKX)
                val crossX = remember { mutableFloatStateOf(-1f) }
            Column(
                modifier = Modifier.fillMaxSize()
                    // cruz continua sobre todo el stack, arriba de los charts sin comerse toques (OKX)
                    .drawWithContent {
                        drawContent()
                        val x = crossX.floatValue
                        if (x >= 0f && x <= size.width) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.86f),
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                            )
                        }
                    }
            ) {
                // refs y altos viven aqui: sobreviven al cambio de rama en cada carga de TF
                // Shared chart references for sync
                val priceChartRef = remember { mutableStateOf<CombinedChart?>(null) }
                val stochChartRef = remember { mutableStateOf<LineChart?>(null) }
                val rsiChartRef = remember { mutableStateOf<LineChart?>(null) }
                val macdChartRef = remember { mutableStateOf<CombinedChart?>(null) }
                val takerChartRef = remember { mutableStateOf<BarChart?>(null) }
                val subRefs = remember { SubChartRefs(stochChartRef, rsiChartRef, macdChartRef, takerChartRef) }
                // altos arrastrables, subs mas altos por defecto
                var priceW by remember { mutableFloatStateOf(4f) }
                var stochW by remember { mutableFloatStateOf(0.7f) }
                var rsiW by remember { mutableFloatStateOf(0.7f) }
                var macdW by remember { mutableFloatStateOf(0.7f) }
                var takerW by remember { mutableFloatStateOf(0.7f) }
                val anySub = prefs.stochVisible || prefs.rsiVisible || prefs.macdVisible || prefs.takerVisible
                val subsTotalW = (if (prefs.stochVisible) stochW else 0f) +
                    (if (prefs.rsiVisible) rsiW else 0f) +
                    (if (prefs.macdVisible) macdW else 0f) +
                    (if (prefs.takerVisible) takerW else 0f)
                when {
                    state.candles.isNotEmpty() -> {
                        // fade al montar: suaviza la entrada sin tapar el layout.
                        // En carga no se desmonta (cero churn de vistas): solo se atenua + barrita
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(300)),
                            modifier = Modifier
                                .weight(1f)
                                .alpha((if (chartDim) 0.35f else 1f) * revealAlpha)
                        ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                        if (state.isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = AppGreen,
                                trackColor = CardBg
                            )
                        }
                        Box(modifier = Modifier.weight(priceW).heightIn(min = 240.dp)) {
                            PriceChart(
                                state = state,
                                prefs = prefs,
                                viewModel = viewModel,
                                chartExpanded = chartExpanded,
                                onOpenIndicators = { showIndicators.value = true },
                                priceChartRef = priceChartRef,
                                stochChartRef = stochChartRef,
                                rsiChartRef = rsiChartRef,
                                macdChartRef = macdChartRef,
                                takerChartRef = takerChartRef,
                                crossX = crossX,
                                onPositioned = { revealed = true }
                            )
                        }
                        if (anySub) {
                            // divisor precio/subs
                            ChartResizeDivider { dy ->
                                var d = dy / areaHpx.coerceAtLeast(1f) * (priceW + stochW + rsiW + macdW + takerW)
                                val np = priceW + d
                                if (np < 1f) d = 1f - priceW
                                if (np > 6f) d = 6f - priceW
                                if (prefs.stochVisible) {
                                    var ns = stochW - d
                                    if (ns < 0.35f) { d = stochW - 0.35f; ns = 0.35f }
                                    if (ns > 3f) { d = stochW - 3f; ns = 3f }
                                    priceW += d
                                    stochW = ns
                                } else if (prefs.rsiVisible) {
                                    var ns = rsiW - d
                                    if (ns < 0.35f) { d = rsiW - 0.35f; ns = 0.35f }
                                    if (ns > 3f) { d = rsiW - 3f; ns = 3f }
                                    priceW += d
                                    rsiW = ns
                                } else if (prefs.macdVisible) {
                                    var ns = macdW - d
                                    if (ns < 0.35f) { d = macdW - 0.35f; ns = 0.35f }
                                    if (ns > 3f) { d = macdW - 3f; ns = 3f }
                                    priceW += d
                                    macdW = ns
                                } else {
                                    var ns = takerW - d
                                    if (ns < 0.35f) { d = takerW - 0.35f; ns = 0.35f }
                                    if (ns > 3f) { d = takerW - 3f; ns = 3f }
                                    priceW += d
                                    takerW = ns
                                }
                            }
                        }
                        // subs en contenedor propio: precio siempre visible, subs con scroll invisible si no entran
                        if (anySub) {
                            Box(modifier = Modifier.weight(subsTotalW)) {
                                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                    val subN = (if (prefs.stochVisible) 1 else 0) +
                                        (if (prefs.rsiVisible) 1 else 0) +
                                        (if (prefs.macdVisible) 1 else 0) +
                                        (if (prefs.takerVisible) 1 else 0)
                                    val subDivN = (if (prefs.stochVisible && prefs.rsiVisible) 1 else 0) +
                                        (if (prefs.macdVisible && prefs.rsiVisible) 1 else 0) +
                                        (if (prefs.macdVisible && prefs.stochVisible && !prefs.rsiVisible) 1 else 0) +
                                        (if (prefs.takerVisible && (prefs.stochVisible || prefs.rsiVisible || prefs.macdVisible)) 1 else 0)
                                    val tight = maxHeight < 72.dp * subN + 10.dp * subDivN
                                    val sh = { w: Float -> (maxHeight * (w / subsTotalW)).coerceAtLeast(72.dp) }
                                    fun androidx.compose.foundation.layout.ColumnScope.subMod(w: Float): Modifier =
                                        if (tight) Modifier.fillMaxWidth().height(sh(w)) else Modifier.weight(w)
                                    Column(
                                        modifier = Modifier.fillMaxSize()
                                            .then(if (tight) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                                    ) {
                        if (prefs.stochVisible) {
                            Box(modifier = subMod(stochW)) {
                                StochRSIChart(state, stochChartRef, priceChartRef, subRefs)
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
                            Box(modifier = subMod(rsiW)) {
                                RsiChart(state, rsiChartRef, priceChartRef, prefs, subRefs)
                            }
                        }
                        if (prefs.macdVisible && prefs.rsiVisible) {
                            // divisor rsi/macd
                            ChartResizeDivider { dy ->
                                var d = dy / areaHpx.coerceAtLeast(1f) * (rsiW + macdW)
                                val nr = rsiW + d
                                if (nr < 0.35f) d = 0.35f - rsiW
                                if (nr > 3f) d = 3f - rsiW
                                var nm = macdW - d
                                if (nm < 0.35f) { d = macdW - 0.35f; nm = 0.35f }
                                if (nm > 3f) { d = macdW - 3f; nm = 3f }
                                rsiW += d
                                macdW = nm
                            }
                        }
                        if (prefs.macdVisible && prefs.stochVisible && !prefs.rsiVisible) {
                            // divisor stoch/macd cuando el rsi va apagado
                            ChartResizeDivider { dy ->
                                var d = dy / areaHpx.coerceAtLeast(1f) * (stochW + macdW)
                                val ns = stochW + d
                                if (ns < 0.35f) d = 0.35f - stochW
                                if (ns > 3f) d = 3f - stochW
                                var nm = macdW - d
                                if (nm < 0.35f) { d = macdW - 0.35f; nm = 0.35f }
                                if (nm > 3f) { d = macdW - 3f; nm = 3f }
                                stochW += d
                                macdW = nm
                            }
                        }
                        if (prefs.macdVisible) {
                            Box(modifier = subMod(macdW)) {
                                MacdChart(state, macdChartRef, priceChartRef, subRefs)
                            }
                        }
                        if (prefs.takerVisible && (prefs.stochVisible || prefs.rsiVisible || prefs.macdVisible)) {
                            // divisor ultimo-sub/taker
                            ChartResizeDivider { dy ->
                                var d = dy / areaHpx.coerceAtLeast(1f) * (stochW + rsiW + macdW + takerW)
                                if (prefs.macdVisible) {
                                    val nm = macdW + d
                                    if (nm < 0.35f) d = 0.35f - macdW
                                    if (nm > 3f) d = 3f - macdW
                                    var nt = takerW - d
                                    if (nt < 0.35f) { d = takerW - 0.35f; nt = 0.35f }
                                    if (nt > 3f) { d = takerW - 3f; nt = 3f }
                                    macdW += d
                                    takerW = nt
                                } else if (prefs.rsiVisible) {
                                    val nr = rsiW + d
                                    if (nr < 0.35f) d = 0.35f - rsiW
                                    if (nr > 3f) d = 3f - rsiW
                                    var nt = takerW - d
                                    if (nt < 0.35f) { d = takerW - 0.35f; nt = 0.35f }
                                    if (nt > 3f) { d = takerW - 3f; nt = 3f }
                                    rsiW += d
                                    takerW = nt
                                } else {
                                    val ns = stochW + d
                                    if (ns < 0.35f) d = 0.35f - stochW
                                    if (ns > 3f) d = 3f - stochW
                                    var nt = takerW - d
                                    if (nt < 0.35f) { d = takerW - 0.35f; nt = 0.35f }
                                    if (nt > 3f) { d = takerW - 3f; nt = 3f }
                                    stochW += d
                                    takerW = nt
                                }
                            }
                        }
                        if (prefs.takerVisible) {
                            Box(modifier = subMod(takerW)) {
                                TakerChart(state, takerChartRef, priceChartRef, subRefs)
                            }
                        }
                                    }
                                }
                            }
                        }
                        // TFs al fondo, stack pegado estilo OKX
                        } // inner Column
                        } // AnimatedVisibility
                    }
                    state.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            com.defitracker.app.presentation.components.OctopusLoader(message = "Cargando…")
                        }
                    }
                    state.error.isNotBlank() && state.candles.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(state.error, color = Color.Gray, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.refreshAll() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppGreen,
                                    contentColor = Color.Black
                                )
                            ) { Text("Reintentar", fontWeight = FontWeight.Bold) }
                        }
                    }
                    // sin velas y sin error = todavia cargando, nunca el mensaje en ingles
                    else -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            com.defitracker.app.presentation.components.OctopusLoader(message = "Cargando…")
                        }
                    }
                }
                // TFs al fondo, stack pegado estilo OKX (siempre visible, ni en carga se esconde)
                IntervalRow(
                    selected = state.selectedInterval,
                    onPick = { viewModel.loadChartData(it) }
                )
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
                        onToggleMacd = { viewModel.toggleMacdSub() },
                        onToggleTaker = { viewModel.toggleTakerSub() },
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

            // campanita: alertas LONG/SHORT de este par
            if (showPriceAlerts.value) {
                ModalBottomSheet(
                    onDismissRequest = { showPriceAlerts.value = false },
                    containerColor = SheetBg
                ) {
                    PriceAlertSheet(
                        symbol = state.detail?.symbol ?: state.symbol,
                        currentPrice = (state.detail?.price).toDoubleValue(),
                        alerts = priceAlerts,
                        soundUri = priceSound,
                        onAdd = { dir, target, lo, hi -> viewModel.addPriceAlert(dir, target, lo, hi) },
                        onDelete = { viewModel.deletePriceAlert(it) },
                        onToggle = { id, on -> viewModel.togglePriceAlert(id, on) },
                        onSoundPicked = { viewModel.setPriceSound(it) }
                    )
                }
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
        // orden ascendente estilo OKX, cortos a la izq (misma lista del monitor)
        Timeframes.KEYS.forEach { tf ->
            val isSelected = selected == tf
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(28.dp)
                    .clickable { onPick(tf) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tf,
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

internal data class TradingPairParts(
    val baseAsset: String,
    val quoteAsset: String
) {
    val displayName: String = if (quoteAsset.isBlank()) baseAsset else "$baseAsset/$quoteAsset"
}

private fun sourceLabel(source: String): String = when (source) {
    "MEXC" -> "MEXC · Futuros"
    "TV" -> "TradingView · CRYPTOCAP"
    else -> "Binance · Spot"
}

internal fun splitTradingPair(symbol: String, source: String = "Binance"): TradingPairParts {
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

// duracion de cada vela para el countdown al cierre

internal fun fibStepMs(candles: List<CandleData>, interval: String): Long {
    if (candles.size >= 2) {
        val s = candles.last().time - candles[candles.size - 2].time
        if (s > 0L) return s
    }
    return candleDurationMs(interval)
}

internal fun fibIndexToTime(candles: List<CandleData>, idx: Int, interval: String): Long {
    if (candles.isEmpty()) return 0L
    if (idx < candles.size) return candles[idx.coerceIn(0, candles.size - 1)].time
    val step = fibStepMs(candles, interval)
    if (step <= 0L) return candles.last().time
    return candles.last().time + (idx - (candles.size - 1)) * step
}

// tiempos futuros mapean a indice virtual, el pasado usa timeToIndex
internal fun fibTimeToIndexVirtual(candles: List<CandleData>, time: Long, interval: String): Int {
    if (candles.isEmpty()) return 0
    if (time <= candles.last().time) return timeToIndex(candles, time)
    val step = fibStepMs(candles, interval)
    if (step <= 0L) return candles.size - 1
    val k = ((time - candles.last().time + step - 1) / step).toInt()
    return candles.size - 1 + k.coerceIn(1, FIB_VIRTUAL_EXTEND)
}

internal fun formatCandleCountdown(interval: String, candleTime: Long): String {
    val dur = candleDurationMs(interval)
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

internal fun String.isCalendarInterval(): Boolean {
    return trim() in Timeframes.CALENDAR_KEYS
}

internal fun List<CandleData>.spansMultipleYears(): Boolean {
    val first = firstOrNull()?.time ?: return false
    val last = lastOrNull()?.time ?: return false
    return last - first >= 365L * 24L * 60L * 60L * 1000L
}

internal fun CryptoDetailState.viewportKey(): String {
    val first = candles.firstOrNull()?.time ?: 0L
    val last = candles.lastOrNull()?.time ?: 0L
    return "$symbol-${candles.size}-$first-$last"
}

internal fun List<Pair<Long, Double>>.getValueAtCandleIndex(index: Int): Double? {
    val direct = getOrNull(index)
    if (direct != null && direct.first.toInt() == index) return direct.second
    return firstOrNull { it.first.toInt() == index }?.second
}

// ─── PRICE CHART ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun PriceChart(
    state: CryptoDetailState,
    prefs: IndicatorPrefs,
    viewModel: CryptoDetailViewModel,
    chartExpanded: MutableState<Boolean>,
    onOpenIndicators: () -> Unit,
    priceChartRef: MutableState<CombinedChart?>,
    stochChartRef: MutableState<LineChart?>,
    rsiChartRef: MutableState<LineChart?>,
    macdChartRef: MutableState<CombinedChart?> = mutableStateOf(null),
    takerChartRef: MutableState<BarChart?> = mutableStateOf(null),
    crossX: MutableFloatState = mutableFloatStateOf(-1f),
    onPositioned: () -> Unit = {}
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
        if (viewModel.openAnalysisInitially) {
            viewModel.setAnalysisOpen(true)
            showAnalysis.value = true
        }
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
    // posiciones LONG/SHORT por simbolo, mismo esquema
    val positionOverlays = viewModel.positionOverlays.value
    val selectedPositionId = viewModel.selectedPositionId.value
    val positionsRef = remember { mutableStateOf<List<PositionOverlay>>(emptyList()) }
    val selectedPositionRef = remember { mutableStateOf<String?>(null) }
    val showPositionConfig = remember { mutableStateOf(false) }
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
    LaunchedEffect(positionOverlays) { positionsRef.value = positionOverlays }
    LaunchedEffect(selectedPositionId) { selectedPositionRef.value = selectedPositionId }
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
            PriceCombinedChart(
                context = context,
                initialState = state,
                initialPrefs = prefs,
                crossX = crossX,
                stochChartRef = stochChartRef,
                rsiChartRef = rsiChartRef,
                macdChartRef = macdChartRef,
                takerChartRef = takerChartRef
            ).apply {
                setupCommonChartParams()
                // eje X en 2 lineas (fecha|hora) + aire abajo
                setXAxisRenderer(TwoLineXAxisRenderer(viewPortHandler, xAxis, getTransformer(YAxis.AxisDependency.LEFT)))
                extraBottomOffset = 18f
                marker = OKXChartMarker(context) { stateRef.value }
                // etiquetas del canvas con Geist semibold, numeros mas definidos
                chartTypeface(context)?.let { tf ->
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

                // eje a la derecha SOBRE el grafico, las velas pasan por detras
                // labels nativos off, se dibujan fijos a pantalla (drawPinnedYLabels)
                axisRight.apply {
                    isEnabled = true
                    setDrawLabels(false)
                    setDrawGridLines(false)
                    setDrawAxisLine(false)
                    setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
                    setLabelCount(6, false)
                    textColor = ChartColors.TEXT_SECONDARY_HEX.toColorInt()
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return formatAxisPrice(
                                value.toDouble(),
                                stateRef.value.candles.lastOrNull()?.close ?: 0.0,
                                stateRef.value.daySubDollar
                            )
                        }
                    }
                }

                axisLeft.apply {
                    setDrawLabels(false)
                    setDrawGridLines(true)
                    gridColor = ChartColors.CARD_HEX.toColorInt()
                    setDrawAxisLine(false)
                    setLabelCount(6, false)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return formatPriceForChart(value.toDouble(), stateRef.value.daySubDollar)
                        }
                    }
                }

                setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
                    override fun onValueSelected(e: Entry?, h: Highlight?) {
                        syncHighlights(this@apply, stochChartRef.value, rsiChartRef.value, macdChartRef.value, takerChartRef.value)
                    }
                    override fun onNothingSelected() {
                        syncHighlights(this@apply, stochChartRef.value, rsiChartRef.value, macdChartRef.value, takerChartRef.value)
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
                        syncSubCharts(this@apply, stochChartRef.value, rsiChartRef.value, macdChartRef.value, takerChartRef.value)
                    }
                    override fun onChartTranslate(me: android.view.MotionEvent?, dX: Float, dY: Float) {
                        syncSubCharts(this@apply, stochChartRef.value, rsiChartRef.value, macdChartRef.value, takerChartRef.value)
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
                onPositionLive = { id, next ->
                    viewModel.setPositionLive(id, next)
                    positionsRef.value = positionsRef.value.map { if (it.id == id) next else it }
                }
                onPositionCommit = { id ->
                    viewModel.commitPosition(id)
                }
                onPositionSelect = { id ->
                    viewModel.selectPosition(id)
                    selectedPositionRef.value = id
                }
                onPositionTap = { anchor, long ->
                    val candles = stateRef.value.candles
                    if (candles.isNotEmpty() && anchor.price > 0) {
                        val half = fibStepMs(candles, stateRef.value.selectedInterval) * 10
                        viewModel.addPosition(long, anchor.time, anchor.price, half)?.let { created ->
                            positionsRef.value = viewModel.positionOverlays.value
                            selectedPositionRef.value = created.id
                        }
                        drawingTool.value = DrawingTool.NONE
                    }
                    invalidate()
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
            chart.updateState(
                state = state,
                prefs = prefs,
                fibOverlays = viewModel.fibOverlays.value,
                selectedFibId = viewModel.selectedFibId.value,
                drawOverlays = viewModel.drawOverlays.value,
                selectedDrawId = viewModel.selectedDrawId.value,
                positionOverlays = viewModel.positionOverlays.value,
                selectedPositionId = viewModel.selectedPositionId.value,
                drawingTool = drawingTool.value,
                rangeSelection = rangeSelection.value,
                fibPendingStart = fibPendingStart.value,
                drawPendingStart = drawPendingStart.value,
                drawKind = drawKindRef.value,
                magnetOn = magnetRef.value
            )
            stateRef.value = state
            prefsRef.value = prefs
            overlaysRef.value = viewModel.fibOverlays.value
            selectedFibRef.value = viewModel.selectedFibId.value
            drawsRef.value = viewModel.drawOverlays.value
            selectedDrawRef.value = viewModel.selectedDrawId.value
            positionsRef.value = viewModel.positionOverlays.value
            selectedPositionRef.value = viewModel.selectedPositionId.value
            chart.onPositionLive = { id, next ->
                viewModel.setPositionLive(id, next)
                positionsRef.value = positionsRef.value.map { if (it.id == id) next else it }
            }
            chart.onPositionCommit = { id ->
                viewModel.commitPosition(id)
            }
            chart.onPositionSelect = { id ->
                viewModel.selectPosition(id)
                selectedPositionRef.value = id
            }
            chart.onPositionTap = { anchor, long ->
                val candles = state.candles
                if (candles.isNotEmpty() && anchor.price > 0) {
                    val half = fibStepMs(candles, state.selectedInterval) * 10
                    viewModel.addPosition(long, anchor.time, anchor.price, half)?.let { created ->
                        positionsRef.value = viewModel.positionOverlays.value
                        selectedPositionRef.value = created.id
                    }
                    drawingTool.value = DrawingTool.NONE
                }
                chart.invalidate()
            }
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
                val showLines = !prefs.overlaysHidden
                if (showLines && prefs.bbVisible && state.bbUpper.isNotEmpty()) {
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
                    val bbColor = GraphicsColor.parseColor(ChartColors.ORANGE_HEX)
                    val middleColor = GraphicsColor.parseColor(ChartColors.PINK_HEX)
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
                    if (showLines) prefs.mas.filter { it.visible }.forEach { ma ->
                        maLineDataSet(ma, state.maLines[ma.id] ?: emptyList())?.let {
                            lineData.addDataSet(it)
                        }
                    }
                    combinedData.setData(lineData)
                } else {
                    combinedData.setData(CandleData(createCandleDataSet(candleEntries)))
                    val maOnly = LineData()
                    if (showLines) prefs.mas.filter { it.visible }.forEach { ma ->
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
                    syncSubCharts(chart, stochChartRef.value, rsiChartRef.value, macdChartRef.value, takerChartRef.value)
                    syncHighlights(chart, stochChartRef.value, rsiChartRef.value, macdChartRef.value, takerChartRef.value)
                } else if (isNewDataset) {
                    // primera carga o TF nuevo: Y solo con velas, las MAs no mandan
                    chart.applySyncAndInitialZoom(state.candles, resetViewport = true, resetCustomY = false, fitYToCandles = true, onPositioned = {
                        syncSubCharts(chart, stochChartRef.value, rsiChartRef.value, macdChartRef.value, takerChartRef.value)
                        onPositioned()
                    })
                    syncHighlights(chart, stochChartRef.value, rsiChartRef.value, macdChartRef.value, takerChartRef.value)
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
            // panes con el mismo rect X para que la cruz del overlay calce en todos (OKX)
            chart.alignViewportXWith(isNewDataset, stochChartRef.value, rsiChartRef.value, macdChartRef.value, takerChartRef.value)
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
                    .background(ChartColors.darkOverlay, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!prefs.overlaysHidden) prefs.mas.filter { it.visible }.forEach { ma ->
                    val last = state.maLines[ma.id]?.lastOrNull()?.second
                    if (last != null) {
                        Text(
                            text = "${maLegendLabel(ma)} ${formatAxisPrice(last, state.candles.lastOrNull()?.close ?: last, state.daySubDollar)}  ",
                            color = Color(ma.colorHex.toColorInt()),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Geist
                        )
                    }
                }
            }
            }
            // atajos Medir/Fibo estilo TV justo encima de la fila de botones
            Column(horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val measureActive = drawingTool.value == DrawingTool.MEASURE
            Box(
                modifier = Modifier
                    .background(
                        color = if (measureActive) ChartColors.activeGreen else ChartColors.activeSurface,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable {
                        drawingTool.value = if (drawingTool.value == DrawingTool.MEASURE) DrawingTool.NONE else DrawingTool.MEASURE
                        rangeSelection.value = null
                        fibPendingStart.value = null
                        pendingStartRef.value = null
                        drawPendingStart.value = null
                        pendingDrawStartRef.value = null
                        priceChartRef.value?.apply {
                            highlightValue(null)
                            syncHighlights(this, stochChartRef.value, rsiChartRef.value, macdChartRef.value, takerChartRef.value)
                            invalidate()
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_measure),
                    contentDescription = "Medir",
                    tint = if (measureActive) Color.Black else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            val fiboActive = drawingTool.value == DrawingTool.FIBO
            Box(
                modifier = Modifier
                    .background(
                        color = if (fiboActive) ChartColors.activeGreen else ChartColors.activeSurface,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable {
                        drawingTool.value = DrawingTool.FIBO
                        fibPendingStart.value = null
                        pendingStartRef.value = null
                        priceChartRef.value?.invalidate()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_fib),
                    contentDescription = "Fibonacci",
                    tint = if (fiboActive) Color.Black else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            val longActive = drawingTool.value == DrawingTool.LONG_POS
            Box(
                modifier = Modifier
                    .background(
                        color = if (longActive) ChartColors.activeGreen else ChartColors.activeSurface,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable {
                        drawingTool.value = if (drawingTool.value == DrawingTool.LONG_POS) DrawingTool.NONE else DrawingTool.LONG_POS
                        fibPendingStart.value = null
                        pendingStartRef.value = null
                        drawPendingStart.value = null
                        pendingDrawStartRef.value = null
                        priceChartRef.value?.invalidate()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_long_tv),
                    contentDescription = "Posición Long",
                    tint = if (longActive) Color.Black else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            val shortActive = drawingTool.value == DrawingTool.SHORT_POS
            Box(
                modifier = Modifier
                    .background(
                        color = if (shortActive) ChartColors.activeRed else ChartColors.activeSurface,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable {
                        drawingTool.value = if (drawingTool.value == DrawingTool.SHORT_POS) DrawingTool.NONE else DrawingTool.SHORT_POS
                        fibPendingStart.value = null
                        pendingStartRef.value = null
                        drawPendingStart.value = null
                        pendingDrawStartRef.value = null
                        priceChartRef.value?.invalidate()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_short_tv),
                    contentDescription = "Posición Short",
                    tint = if (shortActive) Color.Black else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // ojo: oculta lineas/medias/dibujos sobre el precio, velas y volumen intactos
            Box(
                modifier = Modifier
                    .background(
                        color = if (prefs.overlaysHidden) ChartColors.activeGreen else ChartColors.activeSurface,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { viewModel.toggleOverlaysHidden() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (prefs.overlaysHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Ocultar dibujos",
                    tint = if (prefs.overlaysHidden) Color.Black else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            Box(
                modifier = Modifier
                    .background(
                        color = ChartColors.activeSurface,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onOpenIndicators() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_indicators_tv),
                    contentDescription = "Indicadores",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            // un solo boton Dibujo (pincel), adentro se elige Medir o Fibo
            val dibujoActive = drawingTool.value != DrawingTool.NONE
            Box(
                modifier = Modifier
                    .background(
                        color = if (dibujoActive) ChartColors.activeGreen else ChartColors.activeSurface,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { showDrawingSheet.value = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_draw_tv),
                    contentDescription = "Dibujo",
                    tint = if (dibujoActive) Color.Black else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            }
            }
        }

        // barra del fibo seleccionado, solo cuando tocas uno (al entrar no hay seleccion)
        val selectedFib = fibOverlays.firstOrNull { it.id == selectedFibId }
        if (selectedFib != null && !prefs.overlaysHidden) {
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
        if (selectedDraw != null && !prefs.overlaysHidden) {
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
                showFill = selectedDraw.kind.supportsFill(),
                fillOn = selectedDraw.fill,
                fillAlpha = selectedDraw.fillAlpha,
                onToggleFill = {
                    viewModel.setDrawFill(selectedDraw.id, !selectedDraw.fill)
                    priceChartRef.value?.invalidate()
                },
                onFillAlpha = {
                    viewModel.setDrawFillAlpha(selectedDraw.id, it)
                    priceChartRef.value?.invalidate()
                },
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

        // barra de la posicion seleccionada: pincel (transparencias), tuerca, tacho
        val selectedPos = positionOverlays.firstOrNull { it.id == selectedPositionId }
        if (selectedPos != null && !prefs.overlaysHidden) {
            PositionBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 44.dp),
                selectionKey = selectedPos.id,
                profitAlpha = selectedPos.profitAlpha,
                lossAlpha = selectedPos.lossAlpha,
                onProfitAlpha = {
                    viewModel.setPositionProfitAlpha(selectedPos.id, it)
                    priceChartRef.value?.invalidate()
                },
                onLossAlpha = {
                    viewModel.setPositionLossAlpha(selectedPos.id, it)
                    priceChartRef.value?.invalidate()
                },
                onConfig = { showPositionConfig.value = true },
                onDelete = {
                    viewModel.deletePosition(selectedPos.id)
                    positionsRef.value = viewModel.positionOverlays.value
                    selectedPositionRef.value = viewModel.selectedPositionId.value
                    priceChartRef.value?.invalidate()
                }
            )
        }

        // tuerca de la posicion: entrada, SL, TP, leverage, cantidad
        if (showPositionConfig.value) {
            val cfgPos = positionOverlays.firstOrNull { it.id == selectedPositionId }
            if (cfgPos != null) {
                ModalBottomSheet(
                    onDismissRequest = { showPositionConfig.value = false },
                    containerColor = SheetBg
                ) {
                    PositionConfigSheet(
                        pos = cfgPos,
                        currentPrice = state.detail?.price?.toDoubleOrNull() ?: state.candles.lastOrNull()?.close ?: cfgPos.entry,
                        extended = state.daySubDollar,
                        onLeverage = {
                            viewModel.setPositionLeverage(cfgPos.id, it)
                            priceChartRef.value?.invalidate()
                        },
                        onApply = { entry, sl, tp, qty ->
                            viewModel.setPositionLevels(cfgPos.id, entry, sl, tp)
                            viewModel.setPositionQty(cfgPos.id, qty)
                            priceChartRef.value?.invalidate()
                            showPositionConfig.value = false
                        }
                    )
                }
            } else {
                showPositionConfig.value = false
            }
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
                    }) { Text("Eliminar", color = AppRed) }
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

        // boton fantasma del pulso, esquina inferior derecha sin tapar precio ni volumen
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 28.dp)
                .background(ChartColors.overlayBlack, CircleShape)
                .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                .clickable {
                    viewModel.setAnalysisOpen(true)
                    viewModel.refreshAnalysis(force = true)
                    showAnalysis.value = true
                }
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = "Pulso del momento",
                tint = AppGreen,
                modifier = Modifier.size(20.dp)
            )
        }

        // sheet de analisis del momento
        if (showAnalysis.value) {
            ModalBottomSheet(
                onDismissRequest = {
                    viewModel.setAnalysisOpen(false)
                    showAnalysis.value = false
                },
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
// nota: refs compartidas para que cada sub pueda panear al precio y resincronizar a sus hermanos




// 4 decimales como en tradingview, 2 si la escala es grande

// tick en vivo actualiza el ultimo punto del MACD sin reconstruir

// ─── TAKER C/V ───────────────────────────────────────────────────────────────

// miles/millones compactos como en OKX (504.204K)

// ─── INITIAL ZOOM / SCROLL ───────────────────────────────────────────────────

// cola identica de los 4 updates de subs: eje, zoom inicial, clavado al precio y cruz OKX



// panes con el mismo rect X para que la cruz del overlay calce en todos (OKX)


// estandar subs: LineChart acepta highlightValue(x, 0); CombinedChart revienta con
// IndexOutOfBounds en CombinedData.getDataByIndex (dataIndex -1) si no se fija dataIndex = 0 (su LineData)


// ─── COMMON CHART SETUP ──────────────────────────────────────────────────────

// el XAxis de MPChart dibuja 1 linea; este parte fecha|hora en 2

// la fuente se lee de disco una sola vez por proceso, no en cada montaje de chart
internal var cachedChartTypeface: android.graphics.Typeface? = null


// subs compactos estilo OKX: numeros chicos y poco aire vertical

// ─── DATASET HELPERS ─────────────────────────────────────────────────────────


// una MA = un LineDataSet con su color/grosor, etiqueta MA|<id> para el tick en vivo

// etiqueta corta de leyenda con tipo/TF

// tick en vivo actualiza el ultimo punto de cada MA sin reconstruir

// ─── IN-PLACE UPDATE HELPERS ─────────────────────────────────────────────────


// tick en vivo actualiza el ultimo punto del RSI sin reconstruir

// ─── INITIAL ZOOM / SCROLL ───────────────────────────────────────────────────

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
                    GraphicsColor.parseColor(ChartColors.APP_GREEN_HEX)
                else
                    GraphicsColor.parseColor(ChartColors.APP_RED_HEX)

                val isDaily = state.selectedInterval.isCalendarInterval()
                val sdf = when {
                    state.candles.spansMultipleYears() -> yearSdf
                    isDaily -> daySdf
                    else -> hourSdf
                }
                tvTime.text = sdf.format(Date(candle.time))
                val markerExt = state.daySubDollar
                tvOpen.text = formatPriceForChart(candle.open, markerExt)
                tvHigh.text = formatPriceForChart(candle.high, markerExt)
                tvLow.text = formatPriceForChart(candle.low, markerExt)
                tvClose.text = formatPriceForChart(candle.close, markerExt)
                tvChange.text = (if (isUp) "+" else "") + formatPriceForChart(change, markerExt)
                tvChange.setTextColor(changeColor)
                tvChangePct.text = (if (isUp) "+" else "") + String.format(Locale.US, "%.2f", changePct) + "%"
                tvChangePct.setTextColor(changeColor)
                tvVolume.text = formatCompactVol(candle.volume)
            } else if (index >= state.candles.size && state.candles.isNotEmpty()) {
                // zona virtual: hora proyectada, sin OHLC
                val t = fibIndexToTime(state.candles, index, state.selectedInterval)
                tvTime.text = hourSdf.format(Date(t))
                tvOpen.text = "—"
                tvHigh.text = "—"
                tvLow.text = "—"
                tvClose.text = "—"
                tvChange.text = "—"
                tvChangePct.text = "—"
                tvVolume.text = "—"
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
}

// ─── INDICATORS SHEET ─────────────────────────────────────────────────────────

// lista de MAs en su propio bottom sheet (se abre desde Indicadores)



// picker simple: presets + cuadrado saturacion/valor + barra de tonos, sin librerias



// ─── PULSO: SHEET DE ANALISIS DEL MOMENTO ───────────────────────────────────


// ─── DIBUJO: SHEET + BARRA FIBO ─────────────────────────────────────────────





// ─── POSICION: BARRA FLOTANTE (pincel, tuerca, tacho) ────────────────────────







// Extracted chart and sheet components remain in their focused files.
