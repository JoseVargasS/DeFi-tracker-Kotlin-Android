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
import com.defitracker.app.presentation.crypto_detail.CandleData
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

data class SubChartRefs(
    val stoch: MutableState<LineChart?>,
    val rsi: MutableState<LineChart?>,
    val macd: MutableState<CombinedChart?>,
    val taker: MutableState<BarChart?>
)

// nota: pan horizontal desde el sub estilo OKX. El sub nunca panea solo (su clamp nativo
// lo trababa del borde derecho); maneja el dedo y mueve al precio con su misma receta sin clamp,
// luego resincroniza a los 4. Drag vertical/tap se sueltan para no cambiar nada mas.

internal class SubPanForwarder {
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false
    private var slop = 0f

    fun onTouch(
        v: android.view.View,
        event: android.view.MotionEvent?,
        priceChartRef: MutableState<CombinedChart?>,
        peers: SubChartRefs?
    ): Boolean {
        if (event == null) return false
        if (slop == 0f) slop = android.view.ViewConfiguration.get(v.context).scaledTouchSlop.toFloat()
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                dragging = false
                return true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    if (dx > slop && dx > dy) {
                        dragging = true
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    } else if (dy > slop) {
                        return false
                    } else {
                        return true
                    }
                }
                val dx = event.x - lastX
                lastX = event.x
                if (abs(dx) >= 1f) {
                    val price = priceChartRef.value ?: return true
                    val m = Matrix(price.viewPortHandler.matrixTouch)
                    m.postTranslate(dx, 0f)
                    price.isAutoScaleMinMaxEnabled = false
                    price.viewPortHandler.matrixTouch.set(m)
                    price.invalidate()
                    if (peers != null) {
                        syncSubCharts(price, peers.stoch.value, peers.rsi.value, peers.macd.value, peers.taker.value)
                    }
                }
                return true
            }
            else -> {
                dragging = false
                return true
            }
        }
    }
}

internal fun BarLineChartBase<*>.applySyncAndInitialZoom(
    data: List<*>,
    resetViewport: Boolean = false,
    skipPositioning: Boolean = false,
    resetCustomY: Boolean = false,
    // encuadre inicial solo con velas: las MAs de otro TF no aplanan el chart
    fitYToCandles: Boolean = false,
    // subs: con dataset ya visible no reposicionar; el reseteo a zoom inicial
    // descuadra el pan al redimensionar (y en cada tick)
    preserveTouchX: Boolean = false,
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
            if (preserveTouchX && !resetViewport) {
                isAutoScaleMinMaxEnabled = !fitYToCandles
                invalidate()
                return
            }
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

internal fun BarLineChartBase<*>.syncSubFrame(
    state: CryptoDetailState,
    priceChartRef: MutableState<CombinedChart?>,
    lastRenderedDataKey: MutableState<String?>,
    viewportKey: String,
    isNewDataset: Boolean,
    firstMount: Boolean = lastRenderedDataKey.value == null,
    storeKey: Boolean = isNewDataset
) {
    xAxis.applyTimeAxis(state)
    xAxis.setDrawLabels(false)
    applySyncAndInitialZoom(state.candles, resetViewport = firstMount, skipPositioning = !firstMount, preserveTouchX = true)
    // sub clavado al precio en cada pasada: resize/ticks/vela nueva no lo mueven
    priceChartRef.value?.let { syncViewportFrom(it) }
    if (storeKey) {
        lastRenderedDataKey.value = viewportKey
    }
    // mismo rect X que el principal para que la cruz calce (OKX)
    priceChartRef.value?.let { alignViewportXWith(isNewDataset, it) }
}

internal fun syncSubCharts(priceChart: CombinedChart, stochChart: LineChart?, rsiChart: LineChart?, macdChart: BarLineChartBase<*>? = null, takerChart: BarLineChartBase<*>? = null) {
    listOf(stochChart, rsiChart, macdChart, takerChart).forEach { sub ->
        sub ?: return@forEach
        sub.syncViewportFrom(priceChart)
    }
    syncHighlights(priceChart, stochChart, rsiChart, macdChart, takerChart)
}

internal fun BarLineChartBase<*>.syncViewportFrom(source: BarLineChartBase<*>) {

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

internal fun BarLineChartBase<*>.alignViewportXWith(force: Boolean = false, vararg others: BarLineChartBase<*>?) {
    val charts = listOf(this) + others.filterNotNull()
    if (charts.size < 2 || width <= 0) return
    // recalcular marcos solo con dataset nuevo: hacerlo en cada tick muta el rect
    // a mitad de un gesto y los subs se borran/descolocan
    if (force) {
        charts.forEach { it.resetViewPortOffsets(); it.calculateOffsets() }
    }
    val left = charts.maxOf { it.viewPortHandler.contentLeft() }
    val right = charts.maxOf { it.width.toFloat() - it.viewPortHandler.contentRight() }
    charts.forEach {
        val vph = it.viewPortHandler
        val w = it.width.toFloat()
        val h = it.height.toFloat()
        if (w <= 0f || h <= 0f) return@forEach
        val curL = vph.contentLeft()
        val curR = w - vph.contentRight()
        // setViewPortOffsets pide MARGENES: arriba/abajo se conservan los actuales,
        // pasar contentBottom() (coordenada) como margen colapsa el rect a 0px
        val curT = vph.contentTop()
        val curB = h - vph.contentBottom()
        if (kotlin.math.abs(curL - left) > 0.5f || kotlin.math.abs(curR - right) > 0.5f) {
            it.setViewPortOffsets(left, curT, right, curB)
        }
    }
    charts.forEach { it.invalidate() }
}

internal fun syncHighlights(priceChart: CombinedChart, stochChart: LineChart?, rsiChart: LineChart? = null, macdChart: BarLineChartBase<*>? = null, takerChart: BarLineChartBase<*>? = null) {
    val h = priceChart.highlighted?.getOrNull(0)
    listOf(stochChart, rsiChart, macdChart, takerChart).forEach { sub ->
        if (h != null) {
            sub?.highlightXSafe(h.x)
        } else {
            sub?.highlightValues(null)
        }
    }
}

internal fun BarLineChartBase<*>.highlightXSafe(x: Float) {
    try {
        if (this is CombinedChart) {
            highlightValue(Highlight(x, 0, 0), false)
        } else {
            highlightValue(x, 0, false)
        }
    } catch (_: Exception) {
        highlightValues(null)
    }
}
