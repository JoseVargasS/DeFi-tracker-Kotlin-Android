package com.defitracker.app.presentation.crypto_detail.chart

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
import com.defitracker.app.presentation.crypto_detail.*
import com.defitracker.app.presentation.crypto_detail.chart.*
import com.defitracker.app.presentation.crypto_detail.sheets.*

@Composable
fun RsiChart(
    state: CryptoDetailState,
    chartRef: MutableState<LineChart?>,
    priceChartRef: MutableState<CombinedChart?> = mutableStateOf(null),
    prefs: IndicatorPrefs = IndicatorPrefs.DEFAULT,
    peers: SubChartRefs? = null
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
                    color = GraphicsColor.parseColor(ChartColors.PURPLE_HEX)
                    textSize = context.resources.displayMetrics.density * 8f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val rsiMaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor(ChartColors.GOLD_HEX)
                    textSize = context.resources.displayMetrics.density * 8f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                // divergencias estilo TV, linea + chip Bull/Bear
                private val divBullLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor(ChartColors.CHART_GREEN_HEX)
                    strokeWidth = 3f
                }
                private val divBearLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor(ChartColors.CHART_RED_HEX)
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
                    color = GraphicsColor.parseColor(ChartColors.CHART_GREEN_HEX)
                    textSize = 24f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val divBearLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor(ChartColors.CHART_RED_HEX)
                    textSize = 24f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val divChipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.argb(220, 20, 21, 24)
                }
                private val divRect = RectF()
                private val lvlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor(ChartColors.TEXT_SECONDARY_HEX)
                    textSize = context.resources.displayMetrics.density * 8f
                    textAlign = Paint.Align.RIGHT
                }
                private val panForwarder = SubPanForwarder()

                override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
                    return panForwarder.onTouch(this, event, priceChartRef, peers)
                }

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val h = highlighted?.getOrNull(0)
                    val currentState = stateRef.value
                    // rect degenerado: sin overlays esta pasada
                    if (viewPortHandler.contentRight() <= viewPortHandler.contentLeft() ||
                        viewPortHandler.contentBottom() <= viewPortHandler.contentTop()
                    ) return

                    val density = context.resources.displayMetrics.density
                    val textX = viewPortHandler.contentLeft() + (density * 5f)
                    val textY = viewPortHandler.contentTop() + (density * 9f)

                    var rsiVal = 0.0
                    var maVal = 0.0
                    var hasVal = false
                    var hasMa = false

                    if (h != null) {
                        // la vertical la dibuja el overlay de Compose sobre todo el stack (OKX)
                        currentState.rsi.getValueAtCandleIndex(h.x.toInt())?.let {
                            rsiVal = it
                            hasVal = true
                        }
                        currentState.rsiMa.getValueAtCandleIndex(h.x.toInt())?.let {
                            maVal = it
                            hasMa = true
                        }
                    } else {
                        currentState.rsi.lastOrNull()?.let {
                            rsiVal = it.second
                            hasVal = true
                        }
                        currentState.rsiMa.lastOrNull()?.let {
                            maVal = it.second
                            hasMa = true
                        }
                    }

                    if (hasVal) {
                        val rsiText = "RSI: ${String.format(Locale.US, "%.2f", rsiVal)}"
                        canvas.drawText(
                            rsiText,
                            textX,
                            textY,
                            rsiPaint
                        )
                        if (hasMa) {
                            canvas.drawText(
                                "  MA: ${String.format(Locale.US, "%.2f", maVal)}",
                                textX + rsiPaint.measureText(rsiText),
                                textY,
                                rsiMaPaint
                            )
                        }
                    }
                    // niveles 70/30 a la derecha estilo OKX, sin etiquetas 100/0
                    val lvlPts = floatArrayOf(0f, 70f)
                    getTransformer(YAxis.AxisDependency.LEFT).pointValuesToPixel(lvlPts)
                    val lvlX = viewPortHandler.contentRight() - (density * 2f)
                    canvas.drawText("70", lvlX, lvlPts[1] - (density * 2f), lvlPaint)
                    lvlPts[1] = 30f
                    getTransformer(YAxis.AxisDependency.LEFT).pointValuesToPixel(lvlPts)
                    canvas.drawText("30", lvlX, lvlPts[1] + lvlPaint.textSize, lvlPaint)

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
                    setDrawLabels(false)
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
                applyCompactSubAxes()
                chartRef.value = this
            }
        },
        update = { chart ->
            stateRef.value = state
            prefsRef.value = prefs
            val viewportKey = state.viewportKey()
            val isNewDataset = lastRenderedDataKey.value != viewportKey

            if (isNewDataset || (chart.data?.dataSets?.size ?: 0) != 2) {
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
                            GraphicsColor.parseColor(ChartColors.PURPLE_HEX),
                            1.2f,
                            highlight = true
                        )
                    )
                }
                if (state.rsiMa.isNotEmpty()) {
                    val maEntries = ArrayList<Entry>(state.rsiMa.size)
                    state.rsiMa.forEach { value ->
                        maEntries.add(Entry(value.first.toFloat(), value.second.toFloat()))
                    }
                    lineData.addDataSet(
                        createBBLineDataSet(
                            maEntries,
                            "RSI MA",
                            GraphicsColor.parseColor(ChartColors.GOLD_HEX),
                            1.2f,
                            highlight = true
                        )
                    )
                }
                chart.data = lineData
            } else if (state.rsi.isNotEmpty()) {
                updateLastRsiInPlace(chart, state)
            }

            chart.syncSubFrame(state, priceChartRef, lastRenderedDataKey, viewportKey, isNewDataset)
        }
    )
}
