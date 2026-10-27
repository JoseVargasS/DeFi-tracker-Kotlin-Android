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
    DRAW,
    LONG_POS,
    SHORT_POS
}

// duracion de cada vela para el countdown al cierre
// velas virtuales a la derecha para que el fibo pase la ultima vela
private const val FIB_VIRTUAL_EXTEND = 50

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

private data class MeasureZone(
    val startIdx: Int,
    val startPrice: Double,
    val endIdx: Int? = null,
    val endPrice: Double? = null
) {
    val isComplete: Boolean = endIdx != null && endPrice != null
}

// geometria en pixeles de una posicion LONG/SHORT ya resuelta
private data class PosGeom(
    val leftX: Float,
    val rightX: Float,
    val entryY: Float,
    val topY: Float,
    val botY: Float,
    val topIsProfit: Boolean
)

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
                    color = GraphicsColor.parseColor(ChartColors.BLUE_HEX)
                    strokeWidth = 3f
                    style = Paint.Style.STROKE
                }
                // dibujos OKX, color por overlay
                private val drawLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor(ChartColors.GOLD_HEX)
                    strokeWidth = 2f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                private val drawDashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor(ChartColors.GOLD_HEX)
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
                // relleno de figuras con su transparencia, estilo TV
                private val drawFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor(ChartColors.GOLD_HEX)
                    style = Paint.Style.FILL
                }
                // punteada sutil pa' niveles TP/SL de posiciones
                private val posDashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.WHITE
                    strokeWidth = 2f
                    style = Paint.Style.STROKE
                    pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
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
                    color = GraphicsColor.parseColor(ChartColors.GOLD_HEX)
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
                // hora proyectada pa'l tag de la cruz en zona virtual
                private val crossTimeSdf = java.text.SimpleDateFormat("MM/dd HH:mm", Locale.US)
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
                    color = GraphicsColor.parseColor(ChartColors.TEXT_SECONDARY_HEX)
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
                // X libre del dedo pa' que la cruz pase la ultima vela (lienzo infinito)
                private var lastTouchXPx = -1f
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
                    syncSubCharts(this, stochChartRef.value, rsiChartRef.value, macdChartRef.value, takerChartRef.value)
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

                private fun touchToCandleIndex(x: Float, y: Float, size: Int, maxExtra: Int = 0): Int? {
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()
                    if (x < contentLeft || x > contentRight || y < contentTop || y > contentBottom) return null
                    val values = getTransformer(YAxis.AxisDependency.LEFT).getValuesByTouchPoint(x, y)
                    // Medir usa velas virtuales a la derecha como el fibo, el resto queda igual
                    return values.x.roundToInt().coerceIn(0, size - 1 + maxExtra)
                }

                private fun measureAnchor(x: Float, y: Float): Pair<Int, Double>? {
                    val candles = stateRef.value.candles
                    if (candles.isEmpty()) return null
                    val index = touchToCandleIndex(x, y, candles.size, FIB_VIRTUAL_EXTEND) ?: return null
                    var price = getTransformer(YAxis.AxisDependency.LEFT).getValuesByTouchPoint(x, y).y.toDouble()
                    // iman pega Medir al high/low/close mas cercano, en zona virtual no hay vela
                    if (magnetRef.value && index < candles.size) {
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
                    lastTouchXPx = -1f
                    syncHighlights(this, stochChartRef.value, rsiChartRef.value, macdChartRef.value, takerChartRef.value)
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
                            formatAxisPrice(v, refClose, stateRef.value.daySubDollar),
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
                        GraphicsColor.parseColor(ChartColors.CHART_GREEN_HEX)
                    } else {
                        GraphicsColor.parseColor(ChartColors.CHART_RED_HEX)
                    }
                    lastPriceLinePaint.color = tagColor
                    lastPriceTagPaint.color = tagColor
                    canvas.drawLine(contentLeft, py, contentRight, py, lastPriceLinePaint)

                    val priceText = formatPriceForChart(last.close, stateRef.value.daySubDollar)
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
                        cy = cy.coerceIn(contentTop + th / 2f, (contentBottom - th / 2f).coerceAtLeast(contentTop + th / 2f))
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

                    val label = "POC ${formatPriceForChart(pocPrice, stateRef.value.daySubDollar)}"
                    val paddingX = 8f
                    val paddingY = 5f
                    val labelWidth = selectionTextPaint.measureText(label) + paddingX * 2f
                    val labelHeight = selectionTextPaint.textSize + paddingY * 2f
                    val labelLeft = (contentRight - labelWidth - 6f).coerceAtLeast(contentLeft + 6f)
                    val labelTop = (pocY - labelHeight - 4f).coerceIn(contentTop + 4f, (contentBottom - labelHeight - 4f).coerceAtLeast(contentTop + 4f))
                    yTagRect.set(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight)
                    canvas.drawRoundRect(yTagRect, 5f, 5f, selectionLabelPaint)
                    canvas.drawText(label, labelLeft + paddingX, labelTop + labelHeight - paddingY - 4f, selectionTextPaint)
                }

                private fun drawPriceRangeSelection(canvas: Canvas, candles: List<CandleData>) {
                    val selection = rangeSelection.value ?: return
                    if (candles.isEmpty()) return
                    val sIdx = selection.startIdx.coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                    val eIdx = selection.endIdx?.coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
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
                        val labelLeft = (x + 8f).coerceIn(contentLeft + 4f, (contentRight - labelWidth - 4f).coerceAtLeast(contentLeft + 4f))
                        val labelTop = (y - labelHeight - 8f).coerceIn(contentTop + 4f, (contentBottom - labelHeight - 4f).coerceAtLeast(contentTop + 4f))
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
                    val labelLeft = (rightX - labelWidth).coerceIn(contentLeft + 4f, (contentRight - labelWidth - 4f).coerceAtLeast(contentLeft + 4f))
                    val labelTop = (adjustedTop - labelHeight - 6f).coerceIn(contentTop + 4f, (contentBottom - labelHeight - 4f).coerceAtLeast(contentTop + 4f))
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
                    val lIdx = min(sel.startIdx, sel.endIdx!!).coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                    val rIdx = max(sel.startIdx, sel.endIdx).coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
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
                    if (prefsRef.value.overlaysHidden) return null
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
                    if (prefsRef.value.overlaysHidden) return null
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
                        // rectangulo: 4 puntos medios, 4=izq 5=der 6=arriba 7=abajo
                        if (o.kind == DrawKind.RECT) {
                            val l = min(s.x, e.x)
                            val rr = max(s.x, e.x)
                            val t = min(s.y, e.y)
                            val b = max(s.y, e.y)
                            listOf(
                                4 to (l to (t + b) / 2f),
                                5 to (rr to (t + b) / 2f),
                                6 to ((l + rr) / 2f to t),
                                7 to ((l + rr) / 2f to b)
                            ).forEach { (zone, p) ->
                                val d = kotlin.math.hypot(x - p.first, y - p.second)
                                if (d <= 45f && d < bestDist) {
                                    bestDist = d
                                    best = Triple(o.id, zone, o.locked)
                                }
                            }
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
                            if (o.fill) {
                                drawFillPaint.color = lineColor
                                drawFillPaint.alpha = (o.fillAlpha.coerceIn(0f, 1f) * 255).toInt()
                                canvas.drawRect(yTagRect, drawFillPaint)
                            }
                            canvas.drawRect(yTagRect, drawLinePaint)
                            // precios de los bordes pegados al eje Y, como el tag de la cruz
                            val rectTop = yTagRect.top
                            val rectBottom = yTagRect.bottom
                            val rectLeft = yTagRect.left
                            val rectRight = yTagRect.right
                            val chipH = drawLabelPaint.textSize + 8f
                            val refClose = candles.lastOrNull()?.close ?: o.start.price
                            fun chipTop(centerY: Float) = (centerY - chipH / 2f)
                                .coerceIn(contentTop, (contentBottom - chipH).coerceAtLeast(contentTop))
                            fun priceTag(price: Double, top: Float) {
                                val txt = formatAxisPrice(price, refClose, stateRef.value.daySubDollar)
                                val w = drawLabelPaint.measureText(txt) + 12f
                                val leftRaw = contentRight + 2f
                                val offRight = (leftRaw + w - (width.toFloat() - 2f)).coerceAtLeast(0f)
                                val left = leftRaw - offRight
                                yTagRect.set(left, top, left + w, top + chipH)
                                canvas.drawRoundRect(yTagRect, 3f, 3f, drawChipPaint)
                                canvas.drawText(txt, left + 6f, top + chipH - 6f, drawLabelPaint)
                            }
                            val hi = maxOf(o.start.price, o.end.price)
                            val lo = minOf(o.start.price, o.end.price)
                            // rectangulo bajo: separar los tags pa' que no se encimen
                            val topHi = if (chipTop(rectBottom) - chipTop(rectTop) < chipH) {
                                (chipTop(rectBottom) - chipH).coerceAtLeast(contentTop)
                            } else {
                                chipTop(rectTop)
                            }
                            val topLo = if (chipTop(rectBottom) - topHi < chipH) {
                                (topHi + chipH).coerceAtMost((contentBottom - chipH).coerceAtLeast(contentTop))
                            } else {
                                chipTop(rectBottom)
                            }
                            priceTag(hi, topHi)
                            priceTag(lo, topLo)
                            // proyeccion punteada a los ejes, solo al construir o seleccionar
                            if (selected) {
                                drawDashPaint.color = lineColor
                                canvas.drawLine(contentLeft, rectTop, contentRight, rectTop, drawDashPaint)
                                canvas.drawLine(contentLeft, rectBottom, contentRight, rectBottom, drawDashPaint)
                                canvas.drawLine(rectLeft, contentTop, rectLeft, contentBottom, drawDashPaint)
                                canvas.drawLine(rectRight, contentTop, rectRight, contentBottom, drawDashPaint)
                            }
                        }
                        DrawKind.CIRCLE -> {
                            yTagRect.set(min(s.x, e.x), min(s.y, e.y), max(s.x, e.x), max(s.y, e.y))
                            if (o.fill) {
                                drawFillPaint.color = lineColor
                                drawFillPaint.alpha = (o.fillAlpha.coerceIn(0f, 1f) * 255).toInt()
                                canvas.drawOval(yTagRect, drawFillPaint)
                            }
                            canvas.drawOval(yTagRect, drawLinePaint)
                        }
                        DrawKind.TRIANGLE -> {
                            val mx = (s.x + e.x) / 2f
                            drawTriPath.reset()
                            drawTriPath.moveTo(mx, s.y)
                            drawTriPath.lineTo(s.x, e.y)
                            drawTriPath.lineTo(e.x, e.y)
                            drawTriPath.close()
                            if (o.fill) {
                                drawFillPaint.color = lineColor
                                drawFillPaint.alpha = (o.fillAlpha.coerceIn(0f, 1f) * 255).toInt()
                                canvas.drawPath(drawTriPath, drawFillPaint)
                            }
                            canvas.drawPath(drawTriPath, drawLinePaint)
                        }
                        DrawKind.PRICE_LINE -> {
                            hLine(s.y, contentLeft, contentRight, dashed = true)
                            val label = formatAxisPrice(o.start.price, candles.lastOrNull()?.close ?: o.start.price, stateRef.value.daySubDollar)
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
                        else GraphicsColor.parseColor(ChartColors.GOLD_HEX)
                        if (o.kind != DrawKind.H_LINE && o.kind != DrawKind.PRICE_LINE) {
                            canvas.drawCircle(s.x, s.y, r, drawHandleFill)
                            canvas.drawCircle(s.x, s.y, r, drawHandleStroke)
                        }
                        canvas.drawCircle(e.x, e.y, r, drawHandleFill)
                        canvas.drawCircle(e.x, e.y, r, drawHandleStroke)
                        // rectangulo: puntos al medio de cada lado, estiran un solo eje
                        if (o.kind == DrawKind.RECT) {
                            val l = min(s.x, e.x)
                            val rr = max(s.x, e.x)
                            val t = min(s.y, e.y)
                            val b = max(s.y, e.y)
                            val hr = 9f
                            listOf(
                                l to (t + b) / 2f,
                                rr to (t + b) / 2f,
                                (l + rr) / 2f to t,
                                (l + rr) / 2f to b
                            ).forEach { (hx, hy) ->
                                yTagRect.set(hx - hr, hy - hr, hx + hr, hy + hr)
                                canvas.drawRect(yTagRect, drawHandleFill)
                                canvas.drawRect(yTagRect, drawHandleStroke)
                            }
                        }
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

                // ─── POSICIONES LONG/SHORT estilo TV ───
                // 1 arriba (precio), 2 medio (ancho), 3 abajo (precio), 4 cuerpo (mover)
                private var posDragId: String? = null
                private var posDragZone: Int = 0
                private var posDragMoved: Boolean = false
                // campo fijado al agarrar (true = TP): asi SL y TP no se pegan al cruzarse
                private var posDragTopIsTp: Boolean = true
                private var posOrigStart: Long = 0L
                private var posOrigEnd: Long = 0L
                private var posOrigEntry: Double = 0.0
                private var posOrigSl: Double = 0.0
                private var posOrigTp: Double = 0.0
                private var posGrabIdx: Int = 0
                private var posGrabPrice: Double = 0.0
                var onPositionLive: ((String, PositionOverlay) -> Unit)? = null
                var onPositionCommit: ((String) -> Unit)? = null
                var onPositionTap: ((FibAnchor, Boolean) -> Unit)? = null
                var onPositionSelect: ((String?) -> Unit)? = null

                private fun positionGeom(o: PositionOverlay): PosGeom? {
                    val candles = stateRef.value.candles
                    if (candles.isEmpty()) return null
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val interval = stateRef.value.selectedInterval
                    val sIdx = fibTimeToIndexVirtual(candles, o.startTime, interval)
                    val eIdx = fibTimeToIndexVirtual(candles, o.endTime, interval)
                    val lx = floatArrayOf(min(sIdx, eIdx).toFloat(), 0f)
                    trans.pointValuesToPixel(lx)
                    val rx = floatArrayOf(max(sIdx, eIdx).toFloat(), 0f)
                    trans.pointValuesToPixel(rx)
                    val ey = floatArrayOf(0f, o.entry.toFloat())
                    trans.pointValuesToPixel(ey)
                    val topPrice = max(o.sl, o.tp)
                    val botPrice = min(o.sl, o.tp)
                    val ty = floatArrayOf(0f, topPrice.toFloat())
                    trans.pointValuesToPixel(ty)
                    val by = floatArrayOf(0f, botPrice.toFloat())
                    trans.pointValuesToPixel(by)
                    // el verde sigue al TP en ambas direcciones: en short arriba va el SL (rojo)
                    val topIsProfit = topPrice == o.tp
                    return PosGeom(lx[0], rx[0], ey[1], ty[1], by[1], topIsProfit)
                }

                private fun positionLabel(o: PositionOverlay, line: Double): String {
                    val priceTxt = formatPriceForChart(line, stateRef.value.daySubDollar)
                    if (o.entry <= 0.0) return priceTxt
                    // % del movimiento del precio en la grafica, con signo segun la direccion:
                    // TP siempre +, SL siempre -. Monto final = cantidad + PnL de ese lado.
                    val rawMove = (line - o.entry) / o.entry * 100.0
                    val dir = if (o.long) 1.0 else -1.0
                    val pct = rawMove * dir
                    val pnl = o.qtyUsdt * pct * o.leverage / 100.0
                    val finalQty = o.qtyUsdt + pnl
                    val qtyTxt = String.format(Locale.US, "%.2f", finalQty)
                    val pctTxt = "${if (pct >= 0) "+" else ""}${String.format(Locale.US, "%.2f", pct)}%"
                    // sin precio: ya se ve en el eje Y
                    return "$qtyTxt ($pctTxt)"
                }

                private fun drawPositionLabel(canvas: Canvas, text: String, x: Float, y: Float, bg: Int, right: Boolean = false) {
                    val w = selectionTextPaint.measureText(text) + 18f
                    val h = selectionTextPaint.textSize + 12f
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()
                    // etiquetas de SL/TP al borde derecho pa' no tapar los handles del centro
                    val lx = if (right) {
                        (x - w - 8f).coerceIn(contentLeft + 4f, (contentRight - w - 4f).coerceAtLeast(contentLeft + 4f))
                    } else {
                        (x - w / 2f).coerceIn(contentLeft + 4f, (contentRight - w - 4f).coerceAtLeast(contentLeft + 4f))
                    }
                    val ly = (y - h / 2f).coerceIn(contentTop + 4f, (contentBottom - h - 4f).coerceAtLeast(contentTop + 4f))
                    yTagRect.set(lx, ly, lx + w, ly + h)
                    selectionLabelPaint.color = bg
                    canvas.drawRoundRect(yTagRect, 5f, 5f, selectionLabelPaint)
                    canvas.drawText(text, lx + 9f, ly + h - 8f, selectionTextPaint)
                }

                // chip con el valor en el eje Y, como la linea de entrada
                private fun drawPosYTag(canvas: Canvas, price: Double, bg: Int) {
                    val txt = formatPriceForChart(price, stateRef.value.daySubDollar)
                    val w = selectionTextPaint.measureText(txt) + 16f
                    val h = selectionTextPaint.textSize + 10f
                    val contentLeft = viewPortHandler.contentLeft()
                    val contentRight = viewPortHandler.contentRight()
                    val contentTop = viewPortHandler.contentTop()
                    val contentBottom = viewPortHandler.contentBottom()
                    val trans = getTransformer(YAxis.AxisDependency.LEFT)
                    val pts = floatArrayOf(0f, price.toFloat())
                    trans.pointValuesToPixel(pts)
                    val rx = (contentRight - w).coerceAtLeast(contentLeft)
                    val ly = (pts[1] - h / 2f).coerceIn(contentTop, (contentBottom - h).coerceAtLeast(contentTop))
                    yTagRect.set(rx, ly, rx + w, ly + h)
                    selectionLabelPaint.color = bg
                    canvas.drawRoundRect(yTagRect, 4f, 4f, selectionLabelPaint)
                    canvas.drawText(txt, rx + 8f, ly + h - 7f, selectionTextPaint)
                }

                private fun drawSinglePosition(canvas: Canvas, _candles: List<CandleData>, o: PositionOverlay, selected: Boolean) {
                    val g = positionGeom(o) ?: return
                    val profitColor = GraphicsColor.parseColor(POS_PROFIT_HEX)
                    val lossColor = GraphicsColor.parseColor(POS_LOSS_HEX)
                    val topColor = if (g.topIsProfit) profitColor else lossColor
                    val botColor = if (g.topIsProfit) lossColor else profitColor
                    val topAlpha = ((if (g.topIsProfit) o.profitAlpha else o.lossAlpha).coerceIn(0f, 1f) * 255).toInt()
                    val botAlpha = ((if (g.topIsProfit) o.lossAlpha else o.profitAlpha).coerceIn(0f, 1f) * 255).toInt()
                    // rellenos ganancia/perdida con su transparencia
                    drawFillPaint.color = topColor
                    drawFillPaint.alpha = topAlpha
                    canvas.drawRect(g.leftX, min(g.entryY, g.topY), g.rightX, max(g.entryY, g.topY), drawFillPaint)
                    drawFillPaint.color = botColor
                    drawFillPaint.alpha = botAlpha
                    canvas.drawRect(g.leftX, min(g.entryY, g.botY), g.rightX, max(g.entryY, g.botY), drawFillPaint)
                    // bordes TP/SL + linea de entrada
                    drawLinePaint.color = topColor
                    drawLinePaint.strokeWidth = 2f
                    drawLinePaint.alpha = 255
                    canvas.drawLine(g.leftX, g.topY, g.rightX, g.topY, drawLinePaint)
                    drawLinePaint.color = botColor
                    canvas.drawLine(g.leftX, g.botY, g.rightX, g.botY, drawLinePaint)
                    drawLinePaint.color = GraphicsColor.WHITE
                    canvas.drawLine(g.leftX, g.entryY, g.rightX, g.entryY, drawLinePaint)
                    drawLinePaint.alpha = 255
                    // etiquetas: solo con el dibujo seleccionado, centradas sobre la caja
                    val topLine = if (g.topIsProfit) o.tp else o.sl
                    val botLine = if (g.topIsProfit) o.sl else o.tp
                    // handles cuadrados si esta seleccionada: arriba/abajo al centro,
                    // el del medio fuera de la caja pa' no chocar con las etiquetas
                    if (selected) {
                        val cx = (g.leftX + g.rightX) / 2f
                        drawPositionLabel(canvas, positionLabel(o, topLine), cx, g.topY, topColor)
                        drawPositionLabel(canvas, positionLabel(o, botLine), cx, g.botY, botColor)
                        drawPositionLabel(
                            canvas,
                            formatPriceForChart(o.entry, stateRef.value.daySubDollar),
                            cx, g.entryY,
                            GraphicsColor.argb(220, 26, 29, 35)
                        )
                        val contentLeft = viewPortHandler.contentLeft()
                        val contentRight = viewPortHandler.contentRight()
                        // punteadas sutiles de borde a borde + marca en el eje Y, como la entrada
                        posDashPaint.color = topColor
                        posDashPaint.alpha = 120
                        canvas.drawLine(contentLeft, g.topY, contentRight, g.topY, posDashPaint)
                        posDashPaint.color = botColor
                        canvas.drawLine(contentLeft, g.botY, contentRight, g.botY, posDashPaint)
                        posDashPaint.alpha = 255
                        drawPosYTag(canvas, topLine, topColor)
                        drawPosYTag(canvas, botLine, botColor)
                        drawPosYTag(canvas, o.entry, GraphicsColor.argb(220, 26, 29, 35))
                        val r = 18f
                        // arriba/abajo al borde izq pa' no tapar los tags centrados
                        val hx = g.leftX + 20f
                        listOf(
                            Triple(hx, g.topY, 1),
                            Triple(g.rightX + 26f, g.entryY, 2),
                            Triple(hx, g.botY, 3)
                        ).forEach { (hx2, hy, _) ->
                            yTagRect.set(hx2 - r, hy - r, hx2 + r, hy + r)
                            canvas.drawRect(yTagRect, fibHandleFill)
                            canvas.drawRect(yTagRect, fibHandleStroke)
                        }
                    }
                }

                private fun drawPositionOverlays(canvas: Canvas, candles: List<CandleData>) {
                    positionsRef.value.forEach { o ->
                        drawSinglePosition(canvas, candles, o, o.id == selectedPositionRef.value)
                    }
                }

                private fun positionHitTest(x: Float, y: Float): Triple<String, Int, Boolean>? {
                    if (stateRef.value.candles.isEmpty()) return null
                    if (prefsRef.value.overlaysHidden) return null
                    var best: Triple<String, Int, Boolean>? = null
                    var bestDist = Float.MAX_VALUE
                    positionsRef.value.forEach { o ->
                        val g = positionGeom(o) ?: return@forEach
                        // anclajes de TP/SL al borde izq: centrados tapaban sus tags
                        val hx = g.leftX + 20f
                        val dTop = kotlin.math.hypot(x - hx, y - g.topY)
                        if (dTop <= 60f && dTop < bestDist) {
                            bestDist = dTop
                            best = Triple(o.id, 1, false)
                        }
                        val dMid = kotlin.math.hypot(x - (g.rightX + 26f), y - g.entryY)
                        if (dMid <= 60f && dMid < bestDist) {
                            bestDist = dMid
                            best = Triple(o.id, 2, false)
                        }
                        val dBot = kotlin.math.hypot(x - hx, y - g.botY)
                        if (dBot <= 60f && dBot < bestDist) {
                            bestDist = dBot
                            best = Triple(o.id, 3, false)
                        }
                        if (bestDist <= 60f) return@forEach
                        val l = min(g.leftX, g.rightX)
                        val r = max(g.leftX, g.rightX)
                        val t = min(g.topY, g.botY)
                        val b = max(g.topY, g.botY)
                        if (x >= l && x <= r && y >= t && y <= b) {
                            best = Triple(o.id, 4, false)
                        }
                    }
                    return best
                }

                private fun applyPositionTap(x: Float, y: Float, long: Boolean) {
                    val anchor = drawAnchorFromTouch(x, y) ?: return
                    onPositionTap?.invoke(anchor, long)
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

                    // tags siempre visibles (tenues si no es el seleccionado), como las lineas
                    fibLabelPaint.alpha = if (selected || selectedFibRef.value == null) 255 else 140
                    levels.forEach { ratio ->
                        // punto inicial siempre 1, final siempre 0
                        val price = fibLevelPrice(o.end.price, o.start.price, ratio)
                        val pts = floatArrayOf(0f, price.toFloat())
                        trans.pointValuesToPixel(pts)
                        val py = pts[1].coerceIn(contentTop, contentBottom)
                        canvas.drawLine(leftX, py, rightX, py, fibLinePaint)
                        // nivel + precio entre parentesis a la derecha de la linea, sin fondo.
                        // Si el final queda fuera de pantalla (fibo gigante en TF menor),
                        // el tag se clava al borde derecho visible sobre su linea
                        val label = "${trimRatio(ratio)}(${formatAxisPrice(price, candles.lastOrNull()?.close ?: price, stateRef.value.daySubDollar)})"
                        val w = fibLabelPaint.measureText(label)
                        val lx = (rightX + 6f).coerceIn(contentLeft, (contentRight - w).coerceAtLeast(contentLeft))
                        canvas.drawText(label, lx, py - 6f, fibLabelPaint)
                    }
                    fibLabelPaint.alpha = 255

                    // handles solo en el seleccionado, grises si esta candadeado
                    if (selected) {
                        val r = 22f
                        fibHandleStroke.color = if (o.locked) GraphicsColor.GRAY
                        else GraphicsColor.parseColor(ChartColors.BLUE_HEX)
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
                    val ll = (p[0] + 10f).coerceIn(contentLeft + 4f, (contentRight - lw2 - 4f).coerceAtLeast(contentLeft + 4f))
                    val lt = (p[1] - lh2 - 10f).coerceIn(contentTop + 4f, (contentBottom - lh2 - 4f).coerceAtLeast(contentTop + 4f))
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
                        var lastZoneTagY = Float.NEGATIVE_INFINITY
                        smc.zones.forEach { z ->
                            val wantOb = smcPrefs.smcOrderBlocks && z.kind == SmcZoneKind.ORDER_BLOCK
                            val wantFvg = smcPrefs.smcFvg && z.kind == SmcZoneKind.FVG
                            if (!wantOb && !wantFvg) return@forEach
                            if (z.mitigated) return@forEach
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
                                z.kind == SmcZoneKind.ORDER_BLOCK && z.bullish -> smcObBullPaint
                                z.kind == SmcZoneKind.ORDER_BLOCK -> smcObBearPaint
                                z.bullish -> smcFvgBullPaint
                                else -> smcFvgBearPaint
                            }
                            canvas.drawRect(yTagRect, paint)
                            // borde punteado del lado + tag a la derecha, texto pelado
                            val edge = if (z.bullish) smcObBullEdge else smcObBearEdge
                            canvas.drawRect(yTagRect, edge)
                            val tagPrefix = if (z.kind == SmcZoneKind.FVG) "FVG" else "OB"
                            val tag = "$tagPrefix·${stateRef.value.selectedInterval}"
                            val tw = smcFvgTagPaint.measureText(tag)
                            val lx = (contentRight - tw - 4f).coerceIn(contentLeft, (contentRight - tw).coerceAtLeast(contentLeft))
                            var ly = (yTagRect.top + 20f).coerceIn(contentTop + 16f, contentBottom.coerceAtLeast(contentTop + 16f))
                            if (abs(ly - lastZoneTagY) < 24f) {
                                ly = (lastZoneTagY + 24f).coerceAtMost(contentBottom - 4f)
                            }
                            lastZoneTagY = ly
                            canvas.drawText(tag, lx, ly, smcFvgTagPaint)
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
                                    liq.isBuySide -> "LIQ↓"
                                    else -> "LIQ↑"
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
                        // pasada 1: rectangulos (el orden da igual, misma pintura)
                        val confTags = ArrayList<Triple<Float, Float, List<String>>>(smc.confluence.size)
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
                            // tag compuesto: una linea si entra, dos si es largo, texto pelado
                            val ownTf = stateRef.value.selectedInterval
                            val confLine1 = "FVG·$ownTf"
                            val confLine2 = buildString {
                                sortTfsForTag(band.tfs).forEach { append("+").append(it) }
                                if (band.hasOb) append(" +OB")
                            }.toString()
                            val fullTag = "$confLine1$confLine2"
                            val availRight = (contentRight - yTagRect.right - 8f).coerceAtLeast(0f)
                            val confLines = if (confLine2.isBlank() ||
                                smcConfluenceTagPaint.measureText(fullTag) <= availRight
                            ) {
                                listOf(fullTag)
                            } else {
                                listOf(confLine1, confLine2)
                            }
                            val centerY = (min(top[1], bot[1]) + max(top[1], bot[1])) / 2f
                            confTags.add(Triple(centerY, yTagRect.right, confLines))
                        }
                        // pasada 2: tags de arriba hacia abajo para que el stagger no haga cascada
                        var lastConfBottom = Float.NEGATIVE_INFINITY
                        confTags.sortedBy { it.first }.forEach { (center, rectRight, confLines) ->
                            val confH = smcConfluenceTagPaint.textSize + 4f
                            val blockH = confLines.size * confH
                            // centrado vertical al costado de su propio rectangulo + stagger por bloque
                            var centerY = center
                            if (centerY - blockH / 2f < lastConfBottom + 4f) {
                                centerY = lastConfBottom + 4f + blockH / 2f
                            }
                            lastConfBottom = centerY + blockH / 2f
                            val maxW = confLines.maxOf { smcConfluenceTagPaint.measureText(it) }
                            val confLx = (rectRight + 4f).coerceIn(contentLeft, (contentRight - maxW).coerceAtLeast(contentLeft))
                            var confTy = (centerY - blockH / 2f).coerceIn(contentTop, (contentBottom - blockH).coerceAtLeast(contentTop))
                            confLines.forEach { ln ->
                                canvas.drawText(ln, confLx, confTy + confH - 4f, smcConfluenceTagPaint)
                                confTy += confH
                            }
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
                                val ph = positionHitTest(event.x, event.y)
                                if (ph != null) {
                                    val (pid, pzone, _) = ph
                                    onPositionSelect?.invoke(pid)
                                    selectedPositionRef.value = pid
                                    selectedFibRef.value = null
                                    selectedDrawRef.value = null
                                    downX = event.x
                                    downY = event.y
                                    posDragMoved = false
                                    posDragId = pid
                                    posDragZone = pzone
                                    if (pzone == 1 || pzone == 3) {
                                        // se fija el campo agarrado (TP o SL) pa' todo el gesto
                                        val o = positionsRef.value.firstOrNull { it.id == pid }
                                        val topIsProfit = o?.let { positionGeom(it)?.topIsProfit } ?: true
                                        posDragTopIsTp = if (pzone == 1) topIsProfit else !topIsProfit
                                    }
                                    if (pzone == 4) {
                                        val o = positionsRef.value.firstOrNull { it.id == pid }
                                        val candles = stateRef.value.candles
                                        posOrigStart = o?.startTime ?: 0L
                                        posOrigEnd = o?.endTime ?: 0L
                                        posOrigEntry = o?.entry ?: 0.0
                                        posOrigSl = o?.sl ?: 0.0
                                        posOrigTp = o?.tp ?: 0.0
                                        posGrabIdx = touchToCandleIndex(event.x, event.y, candles.size, FIB_VIRTUAL_EXTEND)
                                            ?: o?.let { fibTimeToIndexVirtual(candles, it.startTime, stateRef.value.selectedInterval) } ?: 0
                                        posGrabPrice = getTransformer(YAxis.AxisDependency.LEFT)
                                            .getValuesByTouchPoint(event.x, event.y).y.toDouble()
                                    }
                                    parent.requestDisallowInterceptTouchEvent(true)
                                    return true
                                }
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
                                                val next = when (drawDragZone) {
                                                    1 -> cur.copy(start = anchor)
                                                    2 -> cur.copy(end = anchor)
                                                    // lados: solo el eje agarrado, sin cruzarse al opuesto
                                                    4 -> if (cur.start.time <= cur.end.time) cur.copy(start = cur.start.copy(time = minOf(anchor.time, cur.end.time)))
                                                    else cur.copy(end = cur.end.copy(time = minOf(anchor.time, cur.start.time)))
                                                    5 -> if (cur.start.time >= cur.end.time) cur.copy(start = cur.start.copy(time = maxOf(anchor.time, cur.end.time)))
                                                    else cur.copy(end = cur.end.copy(time = maxOf(anchor.time, cur.start.time)))
                                                    6 -> if (cur.start.price >= cur.end.price) cur.copy(start = cur.start.copy(price = maxOf(anchor.price, cur.end.price)))
                                                    else cur.copy(end = cur.end.copy(price = maxOf(anchor.price, cur.start.price)))
                                                    else -> if (cur.start.price <= cur.end.price) cur.copy(start = cur.start.copy(price = minOf(anchor.price, cur.end.price)))
                                                    else cur.copy(end = cur.end.copy(price = minOf(anchor.price, cur.start.price)))
                                                }
                                                drawsRef.value = drawsRef.value.map { if (it.id == id) next else it }
                                                onDrawLive?.invoke(id, next)
                                                invalidate()
                                            }
                                        }
                                    }
                                }
                                return true
                            }
                            if (posDragId != null) {
                                val dx = abs(event.x - downX)
                                val dy = abs(event.y - downY)
                                if (dx > 10f || dy > 10f) posDragMoved = true
                                if (posDragMoved) {
                                    val id = posDragId!!
                                    val cur = positionsRef.value.firstOrNull { it.id == id }
                                    if (cur != null) {
                                        val candles = stateRef.value.candles
                                        val interval = stateRef.value.selectedInterval
                                        if (posDragZone == 4) {
                                            // mover cuerpo conserva tamaño (delta indice+precio)
                                            val rawMoveX = getTransformer(YAxis.AxisDependency.LEFT)
                                                .getValuesByTouchPoint(event.x, event.y).x.roundToInt()
                                            val newIdx = rawMoveX.coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                            val newPrice = getTransformer(YAxis.AxisDependency.LEFT)
                                                .getValuesByTouchPoint(event.x, event.y).y.toDouble()
                                            val dIdx = newIdx - posGrabIdx
                                            val dPrice = newPrice - posGrabPrice
                                            val s0 = fibTimeToIndexVirtual(candles, posOrigStart, interval)
                                            val e0 = fibTimeToIndexVirtual(candles, posOrigEnd, interval)
                                            // se recorta el desplazamiento, no los extremos: el ancho no cambia
                                            val hi = candles.size - 1 + FIB_VIRTUAL_EXTEND
                                            val shift = dIdx.coerceIn(0 - minOf(s0, e0), hi - maxOf(s0, e0))
                                            val s1 = s0 + shift
                                            val e1 = e0 + shift
                                            val next = cur.copy(
                                                startTime = fibIndexToTime(candles, s1, interval),
                                                endTime = fibIndexToTime(candles, e1, interval),
                                                entry = posOrigEntry + dPrice,
                                                sl = posOrigSl + dPrice,
                                                tp = posOrigTp + dPrice
                                            )
                                            positionsRef.value = positionsRef.value.map { if (it.id == id) next else it }
                                            onPositionLive?.invoke(id, next)
                                            invalidate()
                                        } else if (posDragZone == 2) {
                                            // handle del medio: solo ancho (tiempo final)
                                            val rawX = getTransformer(YAxis.AxisDependency.LEFT)
                                                .getValuesByTouchPoint(event.x, event.y).x.roundToInt()
                                            val idx = rawX.coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                            val t = fibIndexToTime(candles, idx, interval)
                                            val next = cur.copy(endTime = maxOf(t, cur.startTime))
                                            positionsRef.value = positionsRef.value.map { if (it.id == id) next else it }
                                            onPositionLive?.invoke(id, next)
                                            invalidate()
                                        } else {
                                            // handles arriba/abajo: solo el campo agarrado, aunque se crucen
                                            val price = getTransformer(YAxis.AxisDependency.LEFT)
                                                .getValuesByTouchPoint(event.x, event.y).y.toDouble()
                                            if (price <= 0) return true
                                            val next = if (posDragTopIsTp) cur.copy(tp = price) else cur.copy(sl = price)
                                            positionsRef.value = positionsRef.value.map { if (it.id == id) next else it }
                                            onPositionLive?.invoke(id, next)
                                            invalidate()
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
                            if (posDragId != null) {
                                if (posDragMoved) onPositionCommit?.invoke(posDragId!!)
                                else invalidate()
                                posDragId = null
                                posDragZone = 0
                                return true
                            }
                        }
                        android.view.MotionEvent.ACTION_CANCEL -> {
                            fibDragId = null
                            fibDragZone = 0
                            drawDragId = null
                            drawDragZone = 0
                            posDragId = null
                            posDragZone = 0
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
                                    measureGrabIdx = touchToCandleIndex(event.x, event.y, candles.size, FIB_VIRTUAL_EXTEND) ?: 0
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
                                                val s1 = (orig.startIdx + dIdx).coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                                val e1 = (orig.endIdx!! + dIdx).coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
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
                    // LONG/SHORT crean la posicion con un tap (defaults 10x, SL/TP 1%)
                    val tool = toolRef.value
                    if (tool == DrawingTool.FIBO || tool == DrawingTool.DRAW || tool == DrawingTool.LONG_POS || tool == DrawingTool.SHORT_POS) {
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
                                    lastTouchXPx = -1f
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
                                    else if (toolRef.value == DrawingTool.LONG_POS) applyPositionTap(event.x, event.y, true)
                                    else if (toolRef.value == DrawingTool.SHORT_POS) applyPositionTap(event.x, event.y, false)
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
                                lastTouchXPx = event.x
                                // fuera de datos no hay highlight: se guarda el ultimo valido
                                getHighlightByTouchPoint(event.x, event.y)?.let { highlightValue(it, true) }
                                syncHighlights(this, stochChartRef.value, rsiChartRef.value, macdChartRef.value, takerChartRef.value)
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
                                lastTouchXPx = -1f
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
                                if (fibHitTest(event.x, event.y) == null && drawHitTest(event.x, event.y) == null && positionHitTest(event.x, event.y) == null) {
                                    onFibSelect?.invoke(null)
                                    onDrawSelect?.invoke(null)
                                    onPositionSelect?.invoke(null)
                                    selectedFibRef.value = null
                                    selectedDrawRef.value = null
                                    selectedPositionRef.value = null
                                    invalidate()
                                }
                                if (highlighted != null && highlighted.isNotEmpty()) {
                                    // Toggle OFF
                                    highlightValue(null)
                                    lastTouchYPx = -1f
                                    lastTouchXPx = -1f
                                    syncHighlights(this, stochChartRef.value, rsiChartRef.value, macdChartRef.value, takerChartRef.value)
                                } else {
                                    // Toggle ON
                                    lastTouchYPx = event.y
                                    lastTouchXPx = event.x
                                    val h = getHighlightByTouchPoint(event.x, event.y)
                                    if (h != null) highlightValue(h, true)
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
                    // rect degenerado (ancho/alto cero en layout o cambio de TF):
                    // ningun coerceIn de etiquetas abajo puede reventar
                    if (viewPortHandler.contentRight() <= viewPortHandler.contentLeft() ||
                        viewPortHandler.contentBottom() <= viewPortHandler.contentTop()
                    ) {
                        super.onDraw(canvas)
                        return
                    }
                    if (prefsRef.value.profileVisible) {
                        drawFixedRangeVolumeProfile(canvas, entries, visibleStart, visibleEnd)
                    }
                    drawVolumeOverlay(canvas, entries, visibleStart, visibleEnd)
                    if (!prefsRef.value.overlaysHidden) {
                        drawSmcBackground(canvas, entries, visibleStart, visibleEnd)
                    }
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
                        val label = formatPriceForChart(maxEntry.high, stateRef.value.daySubDollar)
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
                        val label = formatPriceForChart(minEntry.low, stateRef.value.daySubDollar)
                        val labelWidth = labelPaint.measureText(label)
                        val lineEndX = if (px + labelWidth + 16f < contentRight) px + 20f else px - 20f
                        canvas.drawLine(px, py, lineEndX, py, linePaint)
                        val textX = if (px + labelWidth + 16f < contentRight) lineEndX + 4f else lineEndX - labelWidth - 4f
                        canvas.drawText(label, textX, py + 10f, labelPaint)
                    }

                    drawPriceRangeSelection(canvas, entries)
                    if (!prefsRef.value.overlaysHidden) {
                        drawFibOverlays(canvas, entries)
                        drawDrawOverlays(canvas, entries)
                        drawPositionOverlays(canvas, entries)
                        drawSmcForeground(canvas, entries, visibleStart, visibleEnd)
                        drawMaTfTags(canvas, entries)
                    }
                    drawPinnedYLabels(canvas, entries)
                    drawLastPriceTag(canvas, entries)

                    // --- Draw Crosshair Lines Manually (Exact Y, Snapped X) ---
                    val h = highlighted?.getOrNull(0)
                    if (h == null) {
                        crossX.floatValue = -1f
                        return
                    }

                    val xPts = floatArrayOf(h.x, h.y)
                    trans.pointValuesToPixel(xPts)
                    val snappedPy = xPts[1]

                    // X snapeada a la vela, Y exacto del toque
                    val px = xPts[0].coerceIn(contentLeft, contentRight)
                    val py = if (lastTouchYPx >= 0) lastTouchYPx.coerceIn(contentTop, contentBottom) else snappedPy

                    // la vertical la dibuja el overlay de Compose sobre todo el stack (OKX)
                    crossX.floatValue = px
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
                    val priceText = formatAxisPrice(priceAtTouch, refClose, stateRef.value.daySubDollar)
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

                    // X-Axis Tag (Date): en zona virtual hora proyectada, no la ultima vela
                    val freeIdx = trans.getValuesByTouchPoint(px, py).x.roundToInt()
                    val dateText = if (freeIdx >= entries.size && entries.isNotEmpty()) {
                        crossTimeSdf.format(java.util.Date(fibIndexToTime(entries, freeIdx, stateRef.value.selectedInterval)))
                    } else {
                        xAxis.valueFormatter.getFormattedValue(h.x).replace("|", " ")
                    }
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
