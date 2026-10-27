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
fun TakerChart(
    state: CryptoDetailState,
    chartRef: MutableState<BarChart?>,
    priceChartRef: MutableState<CombinedChart?> = mutableStateOf(null),
    peers: SubChartRefs? = null
) {
    val stateRef = remember { mutableStateOf(state) }
    val lastRenderedDataKey = remember { mutableStateOf<String?>(null) }
    val lastTakerKey = remember { mutableStateOf<String?>(null) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            object : BarChart(context) {
                private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor(ChartColors.TEXT_SECONDARY_HEX)
                    textSize = context.resources.displayMetrics.density * 8f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.WHITE
                    textSize = context.resources.displayMetrics.density * 8f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val buyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = GraphicsColor.parseColor(ChartColors.CHART_GREEN_HEX)
                    textSize = context.resources.displayMetrics.density * 8f
                    textAlign = Paint.Align.LEFT
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                private val sellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
                    val base = splitTradingPair(currentState.symbol, currentState.source).baseAsset

                    canvas.drawText("C/V del tomador  ", textX, textY, titlePaint)
                    textX += titlePaint.measureText("C/V del tomador  ")

                    if (currentState.takerBuy.isEmpty()) {
                        canvas.drawText("Sin datos taker en este TF/par", textX, textY, titlePaint)
                        return
                    }

                    if (h != null) {
                        // la vertical la dibuja el overlay de Compose sobre todo el stack (OKX)
                        val idx = h.x.toInt()
                        val buy = currentState.takerBuy.getValueAtCandleIndex(idx)
                        val sell = currentState.takerSell.getValueAtCandleIndex(idx)
                        if (buy != null && sell != null) {
                            val buyText = "Buy($base) ${formatTakerCompact(buy)}  "
                            canvas.drawText(buyText, textX, textY, buyPaint)
                            textX += buyPaint.measureText(buyText)
                            canvas.drawText("Sell($base) ${formatTakerCompact(sell)}", textX, textY, sellPaint)
                        }
                    } else {
                        // sin cruz: C/V de la ultima vela, cambia solo cuando entra vela/tick nuevo
                        val lastBuy = currentState.takerBuy.lastOrNull()?.second
                        val lastSell = currentState.takerSell.lastOrNull()?.second
                        if (lastBuy != null && lastSell != null) {
                            val cvText = "C/V($base) ${formatTakerCompact(lastBuy + lastSell)}  "
                            canvas.drawText(cvText, textX, textY, totalPaint)
                            textX += totalPaint.measureText(cvText)
                            val buyText = "Buy($base) ${formatTakerCompact(lastBuy)}  "
                            canvas.drawText(buyText, textX, textY, buyPaint)
                            textX += buyPaint.measureText(buyText)
                            canvas.drawText("Sell($base) ${formatTakerCompact(lastSell)}", textX, textY, sellPaint)
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
                    setLabelCount(3, true)
                    setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return formatTakerCompact(value.toDouble())
                        }
                    }
                }
                applyCompactSubAxes()
                chartRef.value = this
            }
        },
        update = { chart ->
            stateRef.value = state
            val viewportKey = state.viewportKey()
            val isNewDataset = lastRenderedDataKey.value != viewportKey

            // nota: el taker cambia cada tick por el merge; se reconstruye solo cuando cambio
            val takerKey = "${state.takerBuy.size}-${state.takerBuy.lastOrNull()?.second}-${state.takerSell.lastOrNull()?.second}"
            val needsBuild = isNewDataset || (chart.data?.dataSetCount ?: 0) == 0 || lastTakerKey.value != takerKey
            if (needsBuild) {
                val barData = BarData()
                barData.barWidth = 0.7f
                if (state.takerBuy.isNotEmpty()) {
                    // nota: una entrada apilada por cada vela (sin match = [0,0] invisible)
                    // para calzar 1:1 con velas/volumen en cualquier zoom
                    val buyByX = state.takerBuy.associate { it.first to it.second }
                    val sellByX = state.takerSell.associate { it.first to it.second }
                    val entries = ArrayList<BarEntry>(state.candles.size)
                    for (i in state.candles.indices) {
                        val k = i.toLong()
                        entries.add(
                            BarEntry(
                                i.toFloat(),
                                floatArrayOf(
                                    (buyByX[k] ?: 0.0).toFloat(),
                                    (sellByX[k] ?: 0.0).toFloat()
                                )
                            )
                        )
                    }
                    barData.addDataSet(
                        BarDataSet(entries, "T/S").apply {
                            colors = listOf(
                                GraphicsColor.parseColor(ChartColors.CHART_GREEN_HEX),
                                GraphicsColor.parseColor(ChartColors.APP_RED_HEX)
                            )
                            setDrawValues(false)
                            isHighlightEnabled = true
                        }
                    )
                }
                chart.data = barData
                lastTakerKey.value = takerKey
            }

            chart.syncSubFrame(
                state, priceChartRef, lastRenderedDataKey, viewportKey, isNewDataset,
                firstMount = lastTakerKey.value == null, storeKey = needsBuild
            )
        }
    )
}

private fun formatTakerCompact(v: Double): String = when {
    v >= 1_000_000 -> String.format(Locale.US, "%.3fM", v / 1_000_000)
    v >= 1_000 -> String.format(Locale.US, "%.3fK", v / 1_000)
    else -> String.format(Locale.US, "%.1f", v)
}
