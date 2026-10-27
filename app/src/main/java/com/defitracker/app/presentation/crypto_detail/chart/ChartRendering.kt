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

internal fun XAxis.applyTimeAxis(state: CryptoDetailState) {
    valueFormatter = object : ValueFormatter() {
        private val daySdf = SimpleDateFormat("MM/dd", Locale.US)
        private val yearSdf = SimpleDateFormat("yyyy/MM", Locale.US)
        private val dateSdf = SimpleDateFormat("MM/dd", Locale.US)
        private val hourSdf = SimpleDateFormat("HH:mm", Locale.US)

        override fun getFormattedValue(value: Float): String {
            if (state.candles.isEmpty()) return ""
            val idx = value.toInt().coerceIn(0, state.candles.size - 1)
            val time = state.candles[idx].time
            return when {
                state.candles.spansMultipleYears() -> yearSdf.format(Date(time))
                state.selectedInterval.isCalendarInterval() -> daySdf.format(Date(time))
                // fecha|hora, el renderer de 2 lineas lo parte
                else -> dateSdf.format(Date(time)) + "|" + hourSdf.format(Date(time))
            }
        }
    }

    val spansYears = state.candles.spansMultipleYears()
    val isCalendar = state.selectedInterval.isCalendarInterval()
    // siempre horizontal, intradia en 2 lineas via renderer
    labelRotationAngle = 0f
    textSize = if (spansYears) 9f else 10f
    yOffset = 8f
    setLabelCount(
        when {
            spansYears -> 3
            !isCalendar -> 3
            else -> 4
        },
        false
    )
}

internal class TwoLineXAxisRenderer(
    viewPortHandler: ViewPortHandler,
    xAxis: XAxis,
    trans: Transformer
) : XAxisRenderer(viewPortHandler, xAxis, trans) {
    override fun drawLabel(
        c: Canvas,
        formattedLabel: String,
        x: Float,
        y: Float,
        anchor: MPPointF,
        angleDegrees: Float
    ) {
        val sep = formattedLabel.indexOf('|')
        if (sep < 0 || angleDegrees != 0f) {
            super.drawLabel(c, formattedLabel, x, y, anchor, angleDegrees)
            return
        }
        val center = MPPointF.getInstance(0.5f, 0.5f)
        val lh = mAxisLabelPaint.textSize * 1.25f
        Utils.drawXAxisValue(c, formattedLabel.substring(0, sep), x, y - lh / 2f, mAxisLabelPaint, center, 0f)
        Utils.drawXAxisValue(c, formattedLabel.substring(sep + 1), x, y + lh / 2f, mAxisLabelPaint, center, 0f)
        MPPointF.recycleInstance(center)
    }
}

internal fun chartTypeface(context: android.content.Context): android.graphics.Typeface? {
    if (cachedChartTypeface == null) {
        cachedChartTypeface = try {
            androidx.core.content.res.ResourcesCompat.getFont(context, com.defitracker.app.R.font.geist_semibold)
        } catch (_: Exception) { null }
    }
    return cachedChartTypeface
}

internal fun BarLineChartBase<*>.setupCommonChartParams() {
    description.isEnabled = false
    legend.isEnabled = false
    // numeros del chart en Geist semibold estilo OKX, con fallback silencioso
    chartTypeface(context)?.let { tf ->
        xAxis.typeface = tf
        axisLeft.typeface = tf
        axisRight.typeface = tf
    }
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
    // auto-escala al entrar, se congela sola con el primer pan
    isAutoScaleMinMaxEnabled = true
    xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        textColor = ChartColors.TEXT_SECONDARY_HEX.toColorInt()
        gridColor = ChartColors.CARD_HEX.toColorInt()
        setDrawLabels(true)
        setAvoidFirstLastClipping(true)
        setLabelCount(5, false) // Fix 1: Limit label count to avoid crowding
        granularity = 1f
    }
    axisLeft.textColor = ChartColors.TEXT_SECONDARY_HEX.toColorInt()
    axisLeft.gridColor = ChartColors.CARD_HEX.toColorInt()
    axisLeft.setDrawAxisLine(false)
    axisRight.isEnabled = false
}

internal fun BarLineChartBase<*>.applyCompactSubAxes() {
    axisLeft.textSize = 8f
    axisLeft.spaceTop = 5f
    axisLeft.spaceBottom = 5f
    axisRight.textSize = 8f
    axisRight.spaceTop = 5f
    axisRight.spaceBottom = 5f
    xAxis.textSize = 8f
    // marco de 1dp: el aire sobre/sub era el minOffset de 15dp de MPChart
    setMinOffset(0f)
    val d1 = context.resources.displayMetrics.density
    setExtraOffsets(d1, d1, d1, d1)
}

internal fun createCandleDataSet(entries: List<CandleEntry>) = CandleDataSet(entries, "Klines").apply {
    axisDependency = YAxis.AxisDependency.RIGHT
    shadowColor = ChartColors.CANDLE_SHADOW_HEX.toColorInt()
    // mecha fina estilo OKX, el ancho fijo en px se ve grueso al alejar
    shadowWidth = 0.7f
    decreasingColor = ChartColors.CHART_RED_HEX.toColorInt()
    increasingColor = ChartColors.CHART_GREEN_HEX.toColorInt()
    neutralColor = ChartColors.TEXT_SECONDARY_HEX.toColorInt()
    decreasingPaintStyle = Paint.Style.FILL
    increasingPaintStyle = Paint.Style.FILL
    setDrawValues(false)
    shadowColorSameAsCandle = true
    // cuerpos gruesos pegaditos estilo OKX, poco gap para que la mecha no domine
    barSpace = 0.18f
    highLightColor = ChartColors.TEXT_SECONDARY_HEX.toColorInt()
    highlightLineWidth = 1f
    setDrawHorizontalHighlightIndicator(false)
    setDrawVerticalHighlightIndicator(false)
    enableDashedHighlightLine(10f, 5f, 0f)
}

internal fun createBBLineDataSet(
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
    highLightColor = ChartColors.TEXT_SECONDARY_HEX.toColorInt()
    highlightLineWidth = 1f
    enableDashedHighlightLine(10f, 5f, 0f)
    if (highlight) {
        setDrawHorizontalHighlightIndicator(false)
        setDrawVerticalHighlightIndicator(false)
    }
}

internal fun maLineDataSet(ma: MaConfig, line: List<Pair<Long, Double>>): LineDataSet? {
    if (line.isEmpty()) return null
    val entries = ArrayList<Entry>(line.size)
    line.forEach { value ->
        entries.add(Entry(value.first.toFloat(), value.second.toFloat()))
    }
    return createBBLineDataSet(
        entries,
        "MA|${ma.id}",
        ma.colorHex.toColorInt(),
        ma.width,
        axisDependency = YAxis.AxisDependency.RIGHT
    )
}

internal fun maLegendLabel(ma: MaConfig): String {
    val kind = if (ma.type == MaType.EMA) "EMA" else "MA"
    val tf = if (ma.timeframe != "chart") "·${ma.timeframe}" else ""
    return "$kind${ma.period}$tf"
}

internal const val INITIAL_VISIBLE_CANDLES = 100f
