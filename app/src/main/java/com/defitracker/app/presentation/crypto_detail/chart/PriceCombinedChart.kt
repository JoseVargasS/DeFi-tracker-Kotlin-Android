package com.defitracker.app.presentation.crypto_detail.chart

import android.graphics.*
import java.util.Locale
import kotlin.math.*
import com.defitracker.app.ui.theme.ChartColors

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as GraphicsColor
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.ViewCompat
import com.defitracker.app.presentation.crypto_detail.*
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.LineChart
import java.text.SimpleDateFormat

/**
 * The price chart view owns the Android chart lifecycle and exposes one explicit
 * state update boundary to Compose.
 *
 * Rendering, gestures and overlays live in focused files; this class only keeps
 * their shared view state and delegates Android callbacks.
 */
internal class PriceCombinedChart(
    context: Context,
    initialState: CryptoDetailState,
    initialPrefs: IndicatorPrefs,
    internal val crossX: MutableFloatState,
    internal val stochChartRef: MutableState<LineChart?>,
    internal val rsiChartRef: MutableState<LineChart?>,
    internal val macdChartRef: MutableState<CombinedChart?>,
    internal val takerChartRef: MutableState<BarChart?>
) : CombinedChart(context) {
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.WHITE
        textSize = 28f
        textAlign = Paint.Align.LEFT
    }
    internal val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.argb(220, 255, 255, 255)
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }
    internal val profileUpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.argb(92, 70, 222, 226)
        style = Paint.Style.FILL
    }
    internal val profileDownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.argb(92, 255, 88, 142)
        style = Paint.Style.FILL
    }
    internal val profilePocPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.argb(145, 255, 193, 7)
        style = Paint.Style.FILL
    }
    internal val profileBackdropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.argb(105, 0, 0, 0)
        style = Paint.Style.FILL
    }
    internal val profileBoundaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.argb(90, 173, 177, 184)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    internal val profilePocLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.argb(150, 255, 193, 7)
        strokeWidth = 1.4f
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }
    internal val selectionFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.argb(38, 30, 203, 129)
        style = Paint.Style.FILL
    }
    internal val selectionStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
    internal val selectionLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.argb(220, 26, 29, 35)
        style = Paint.Style.FILL
    }
    // fibo estilo OKX, color/grosor editables desde la barra flotante
    internal val fibLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
    internal val fibHandleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.argb(60, 33, 150, 243)
        style = Paint.Style.FILL
    }
    internal val fibHandleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.parseColor(ChartColors.BLUE_HEX)
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    // dibujos OKX, color por overlay
    internal val drawLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.parseColor(ChartColors.GOLD_HEX)
        strokeWidth = 2f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    internal val drawDashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.parseColor(ChartColors.GOLD_HEX)
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }
    internal val drawLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.WHITE
        textSize = 24f
        textAlign = Paint.Align.LEFT
    }
    internal val drawChipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.argb(220, 26, 29, 35)
        style = Paint.Style.FILL
    }
    // relleno de figuras con su transparencia, estilo TV
    internal val drawFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.parseColor(ChartColors.GOLD_HEX)
        style = Paint.Style.FILL
    }
    // punteada sutil pa' niveles TP/SL de posiciones
    internal val posDashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
    internal val drawHandleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.argb(60, 255, 214, 10)
        style = Paint.Style.FILL
    }
    internal val drawHandleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.parseColor(ChartColors.GOLD_HEX)
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    internal val drawTriPath = android.graphics.Path()
    // SMC tenue, las velas mandan (alfas bajos, labels chicos)
    val smcObBullPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.argb(42, 38, 166, 154)
        style = Paint.Style.FILL
    }
    val smcObBearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.argb(42, 239, 83, 80)
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
    
    internal val tagBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.WHITE
    }
    // hora proyectada pa'l tag de la cruz en zona virtual
    internal val crossTimeSdf = java.text.SimpleDateFormat("MM/dd HH:mm", Locale.US)
    internal val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.WHITE
        style = Paint.Style.FILL
    }
    internal val dotOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    // linea + tag del precio actual estilo OKX
    internal val lastPriceLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }
    internal val lastPriceTagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    val lastPriceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    // escala Y fija a pantalla como OKX
    internal val pinnedYLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.parseColor(ChartColors.TEXT_SECONDARY_HEX)
        textSize = context.resources.displayMetrics.density * 10f
        textAlign = Paint.Align.RIGHT
    }
    // volumen taker apilado transparente detras de las velas
    internal val takerBuyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.argb(110, 14, 203, 129)
        style = Paint.Style.FILL
    }
    internal val takerSellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GraphicsColor.argb(110, 239, 83, 80)
        style = Paint.Style.FILL
    }
    internal val yTagRect = RectF()
    internal val xTagRect = RectF()
    internal var lastPriceCountdown: String = ""
    
    internal var lastTouchYPx = -1f
    // X libre del dedo pa' que la cruz pase la ultima vela (lienzo infinito)
    internal var lastTouchXPx = -1f
    internal var downX = 0f
    internal var downY = 0f
    internal var lastGestureX = 0f
    internal var lastGestureY = 0f
    internal var zoomPivotX = 0f
    internal var zoomPivotY = 0f
    internal var lastPinchSpanX = 0f
    internal var touchMode = ChartTouchMode.UNDECIDED
    internal var gestureMoved = false
    
    internal val density: Float
        get() = context.resources.displayMetrics.density
    // ─── MEDIR: drag por esquinas + mover por dentro ───
    internal var measureDragZone = 0 // 1 primera esquina, 2 segunda, 3 dentro
    internal var measureDragMoved = false
    internal var measureGrabIdx = 0
    internal var measureGrabPrice = 0.0
    internal var measureOrig: MeasureZone? = null
    
    internal var fibDragId: String? = null
    internal var fibDragZone = 0 // 1 inicio, 2 fin, 3 cuerpo
    internal var fibDragMoved = false
    internal var fibGrabIdx = 0
    internal var fibGrabPrice = 0.0
    internal var fibOrigStart: FibAnchor? = null
    internal var fibOrigEnd: FibAnchor? = null
    var onFibLive: ((String, FibOverlay) -> Unit)? = null
    var onFibCommit: ((String) -> Unit)? = null
    var onFibTap: ((FibAnchor) -> Unit)? = null
    var onFibSelect: ((String?) -> Unit)? = null
    internal var drawDragId: String? = null
    internal var drawDragZone: Int = 0
    internal var drawDragMoved: Boolean = false
    internal var drawOrigStart: FibAnchor? = null
    internal var drawOrigEnd: FibAnchor? = null
    internal var drawGrabIdx: Int = 0
    internal var drawGrabPrice: Double = 0.0
    var onDrawLive: ((String, DrawOverlay) -> Unit)? = null
    var onDrawCommit: ((String) -> Unit)? = null
    var onDrawTap: ((FibAnchor) -> Unit)? = null
    var onDrawSelect: ((String?) -> Unit)? = null
    internal var posDragId: String? = null
    internal var posDragZone: Int = 0
    internal var posDragMoved: Boolean = false
    // campo fijado al agarrar (true = TP): asi SL y TP no se pegan al cruzarse
    internal var posDragTopIsTp: Boolean = true
    internal var posOrigStart: Long = 0L
    internal var posOrigEnd: Long = 0L
    internal var posOrigEntry: Double = 0.0
    internal var posOrigSl: Double = 0.0
    internal var posOrigTp: Double = 0.0
    internal var posGrabIdx: Int = 0
    internal var posGrabPrice: Double = 0.0
    var onPositionLive: ((String, PositionOverlay) -> Unit)? = null
    var onPositionCommit: ((String) -> Unit)? = null
    var onPositionTap: ((FibAnchor, Boolean) -> Unit)? = null
    var onPositionSelect: ((String?) -> Unit)? = null

    internal val stateRef = mutableStateOf(initialState)
    internal val prefsRef = mutableStateOf(initialPrefs)
    internal val overlaysRef = mutableStateOf<List<FibOverlay>>(emptyList())
    internal val selectedFibRef = mutableStateOf<String?>(null)
    internal val drawsRef = mutableStateOf<List<DrawOverlay>>(emptyList())
    internal val selectedDrawRef = mutableStateOf<String?>(null)
    internal val positionsRef = mutableStateOf<List<PositionOverlay>>(emptyList())
    internal val selectedPositionRef = mutableStateOf<String?>(null)
    internal val rangeSelection = mutableStateOf<MeasureZone?>(null)
    internal val fibPendingStart = mutableStateOf<FibAnchor?>(null)
    internal val drawPendingStart = mutableStateOf<FibAnchor?>(null)
    internal val drawKindRef = mutableStateOf<DrawKind?>(null)
    internal val magnetRef = mutableStateOf(false)
    internal val toolRef = mutableStateOf(DrawingTool.NONE)
    internal val pendingStartRef = mutableStateOf<FibAnchor?>(null)
    internal val pendingDrawStartRef = mutableStateOf<FibAnchor?>(null)

    private val gestureController = ChartGestureController(this)

    internal fun updateState(
        state: CryptoDetailState,
        prefs: IndicatorPrefs,
        fibOverlays: List<FibOverlay>,
        selectedFibId: String?,
        drawOverlays: List<DrawOverlay>,
        selectedDrawId: String?,
        positionOverlays: List<PositionOverlay>,
        selectedPositionId: String?,
        drawingTool: DrawingTool,
        rangeSelection: MeasureZone?,
        fibPendingStart: FibAnchor?,
        drawPendingStart: FibAnchor?,
        drawKind: DrawKind?,
        magnetOn: Boolean
    ) {
        stateRef.value = state
        prefsRef.value = prefs
        overlaysRef.value = fibOverlays
        selectedFibRef.value = selectedFibId
        drawsRef.value = drawOverlays
        selectedDrawRef.value = selectedDrawId
        positionsRef.value = positionOverlays
        selectedPositionRef.value = selectedPositionId
        toolRef.value = drawingTool
        this.rangeSelection.value = rangeSelection
        this.fibPendingStart.value = fibPendingStart
        this.drawPendingStart.value = drawPendingStart
        drawKindRef.value = drawKind
        magnetRef.value = magnetOn
        pendingStartRef.value = fibPendingStart
        pendingDrawStartRef.value = drawPendingStart
    }

    internal fun callSuperOnSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
    }

    internal fun drawBaseChart(canvas: Canvas) {
        super.onDraw(canvas)
    }

    internal fun invalidateCountdownIfChanged() {
        val lastCandle = stateRef.value.candles.lastOrNull() ?: return
        val countdown = formatCandleCountdown(stateRef.value.selectedInterval, lastCandle.time)
        if (countdown == lastPriceCountdown) return
        lastPriceCountdown = countdown
        if (yTagRect.isEmpty) {
            invalidate()
            return
        }
        val top = (yTagRect.top - 8f).toInt().coerceAtLeast(0)
        val bottom = (yTagRect.bottom + 8f).toInt().coerceAtMost(height)
        ViewCompat.postInvalidateOnAnimation(this, 0, top, width, bottom)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        handleSizeChanged(w, h, oldw, oldh)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean =
        gestureController.onTouchEvent(event)

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        drawPriceChart(canvas)
    }
}



