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
fun MacdChart(
    state: CryptoDetailState,
    chartRef: MutableState<CombinedChart?>,
    priceChartRef: MutableState<CombinedChart?> = mutableStateOf(null),
    peers: SubChartRefs? = null
) {
    val stateRef = remember { mutableStateOf(state) }
    val lastRenderedDataKey = remember { mutableStateOf<String?>(null) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            object : CombinedChart(context) {
                private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor(ChartColors.TEXT_SECONDARY_HEX)
                    textSize = context.resources.displayMetrics.density * 8f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val difPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor(ChartColors.GOLD_HEX)
                    textSize = context.resources.displayMetrics.density * 8f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val deaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor(ChartColors.PINK_HEX)
                    textSize = context.resources.displayMetrics.density * 8f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val histPosPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor(ChartColors.CHART_GREEN_HEX)
                    textSize = context.resources.displayMetrics.density * 8f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val histNegPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor(ChartColors.APP_RED_HEX)
                    textSize = context.resources.displayMetrics.density * 8f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
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
                    var textX = viewPortHandler.contentLeft() + (density * 5f)
                    val textY = viewPortHandler.contentTop() + (density * 11f)

                    var difVal = 0.0
                    var deaVal = 0.0
                    var histVal = 0.0
                    var hasVal = false

                    if (h != null) {
                        // la vertical la dibuja el overlay de Compose sobre todo el stack (OKX)
                        val idx = h.x.toInt()
                        val dif = currentState.macdDif.getValueAtCandleIndex(idx)
                        val dea = currentState.macdDea.getValueAtCandleIndex(idx)
                        val hist = currentState.macdHist.getValueAtCandleIndex(idx)
                        if (dif != null && dea != null && hist != null) {
                            difVal = dif
                            deaVal = dea
                            histVal = hist
                            hasVal = true
                        }
                    } else {
                        val lastDif = currentState.macdDif.lastOrNull()
                        val lastDea = currentState.macdDea.lastOrNull()
                        val lastHist = currentState.macdHist.lastOrNull()
                        if (lastDif != null && lastDea != null && lastHist != null) {
                            difVal = lastDif.second
                            deaVal = lastDea.second
                            histVal = lastHist.second
                            hasVal = true
                        }
                    }

                    if (hasVal) {
                        canvas.drawText("MACD(12,26,9)  ", textX, textY, titlePaint)
                        textX += titlePaint.measureText("MACD(12,26,9)  ")
                        val histText = "MACD ${formatMacdValue(histVal)}  "
                        canvas.drawText(histText, textX, textY, if (histVal >= 0) histPosPaint else histNegPaint)
                        textX += histPosPaint.measureText(histText)
                        val difText = "DIF ${formatMacdValue(difVal)}  "
                        canvas.drawText(difText, textX, textY, difPaint)
                        textX += difPaint.measureText(difText)
                        canvas.drawText("DEA ${formatMacdValue(deaVal)}", textX, textY, deaPaint)
                    }
                }
            }.apply {
                setupCommonChartParams()
                setTouchEnabled(false)
                isDragEnabled = false
                setScaleEnabled(true)
                isScaleYEnabled = false
                setPinchZoom(false)
                drawOrder = arrayOf(CombinedChart.DrawOrder.BAR, CombinedChart.DrawOrder.LINE)
                axisLeft.apply {
                    setLabelCount(3, true)
                    setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
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
                val cd = CombinedData()
                if (state.macdDif.isNotEmpty()) {
                    val difEntries = ArrayList<Entry>(state.macdDif.size)
                    state.macdDif.forEach { value ->
                        difEntries.add(Entry(value.first.toFloat(), value.second.toFloat()))
                    }
                    val deaEntries = ArrayList<Entry>(state.macdDea.size)
                    state.macdDea.forEach { value ->
                        deaEntries.add(Entry(value.first.toFloat(), value.second.toFloat()))
                    }
                    val lineData = LineData()
                    lineData.addDataSet(createBBLineDataSet(difEntries, "DIF", GraphicsColor.parseColor(ChartColors.GOLD_HEX), 1f, highlight = true))
                    lineData.addDataSet(createBBLineDataSet(deaEntries, "DEA", GraphicsColor.parseColor(ChartColors.PINK_HEX), 1f, highlight = true))
                    cd.setData(lineData)
                    val posEntries = ArrayList<BarEntry>(state.macdHist.size)
                    val negEntries = ArrayList<BarEntry>(state.macdHist.size)
                    state.macdHist.forEach { value ->
                        val x = value.first.toFloat()
                        val y = value.second.toFloat()
                        posEntries.add(BarEntry(x, if (y >= 0f) y else 0f))
                        negEntries.add(BarEntry(x, if (y < 0f) y else 0f))
                    }
                    val barData = BarData(
                        BarDataSet(posEntries, "HIST+").apply {
                            color = GraphicsColor.parseColor(ChartColors.CHART_GREEN_HEX)
                            setDrawValues(false)
                            isHighlightEnabled = true
                        },
                        BarDataSet(negEntries, "HIST-").apply {
                            color = GraphicsColor.parseColor(ChartColors.APP_RED_HEX)
                            setDrawValues(false)
                            isHighlightEnabled = true
                        }
                    )
                    cd.setData(barData)
                }
                chart.data = cd
            } else if (state.macdDif.isNotEmpty()) {
                updateLastMacdInPlace(chart, state)
            }

            chart.syncSubFrame(state, priceChartRef, lastRenderedDataKey, viewportKey, isNewDataset)
        }
    )
}

private fun formatMacdValue(v: Double): String =
    if (abs(v) >= 100.0) String.format(Locale.US, "%.2f", v)
    else String.format(Locale.US, "%.4f", v)
