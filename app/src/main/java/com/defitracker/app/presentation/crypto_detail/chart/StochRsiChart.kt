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
fun StochRSIChart(
    state: CryptoDetailState,
    chartRef: MutableState<LineChart?>,
    priceChartRef: MutableState<CombinedChart?> = mutableStateOf(null),
    peers: SubChartRefs? = null
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
                    color = GraphicsColor.parseColor(ChartColors.ORANGE_HEX)
                    textSize = context.resources.displayMetrics.density * 8f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val dPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor(ChartColors.PINK_HEX)
                    textSize = context.resources.displayMetrics.density * 8f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
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

                    var kVal = 0.0
                    var dVal = 0.0
                    var hasVal = false

                    if (h != null) {
                        // la vertical la dibuja el overlay de Compose sobre todo el stack (OKX)
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
                    // niveles 80/20 a la derecha estilo OKX, sin etiquetas 100/0
                    val lvlPts = floatArrayOf(0f, 80f)
                    getTransformer(YAxis.AxisDependency.LEFT).pointValuesToPixel(lvlPts)
                    val lvlX = viewPortHandler.contentRight() - (density * 2f)
                    canvas.drawText("80", lvlX, lvlPts[1] - (density * 2f), lvlPaint)
                    lvlPts[1] = 20f
                    getTransformer(YAxis.AxisDependency.LEFT).pointValuesToPixel(lvlPts)
                    canvas.drawText("20", lvlX, lvlPts[1] + lvlPaint.textSize, lvlPaint)
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
                    addLimitLine(LimitLine(80f, "").apply {
                        lineColor = GraphicsColor.argb(120, 173, 177, 184)
                        lineWidth = 1f
                        enableDashedLine(10f, 6f, 0f)
                    })
                    addLimitLine(LimitLine(20f, "").apply {
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
                    lineData.addDataSet(createBBLineDataSet(kEntries, "K", GraphicsColor.parseColor(ChartColors.ORANGE_HEX), 1f, highlight = true))
                    lineData.addDataSet(createBBLineDataSet(dEntries, "D", GraphicsColor.parseColor(ChartColors.PINK_HEX), 1f, highlight = true))
                }
                chart.data = lineData
            } else if (state.stochK.isNotEmpty()) {
                updateLastStochInPlace(chart, state)
            }

            chart.syncSubFrame(state, priceChartRef, lastRenderedDataKey, viewportKey, isNewDataset)
        }
    )
}
