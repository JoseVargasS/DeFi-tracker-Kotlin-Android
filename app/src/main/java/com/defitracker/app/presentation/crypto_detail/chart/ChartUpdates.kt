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

internal fun updateLastMacdInPlace(chart: CombinedChart, state: CryptoDetailState) {
    val cd = chart.data ?: return
    val lastDif = state.macdDif.lastOrNull()
    val lastDea = state.macdDea.lastOrNull()
    val lastHist = state.macdHist.lastOrNull()
    cd.lineData?.let { ld ->
        for (i in 0 until ld.dataSetCount) {
            val ds = ld.getDataSetByIndex(i)
            val v = when (ds.label) {
                "DIF" -> lastDif
                "DEA" -> lastDea
                else -> null
            } ?: continue
            if (ds.entryCount > 0) {
                ds.getEntryForIndex(ds.entryCount - 1).y = v.second.toFloat()
            }
        }
    }
    if (lastHist != null) {
        cd.barData?.let { bd ->
            for (i in 0 until bd.dataSetCount) {
                val ds = bd.getDataSetByIndex(i)
                if (ds.entryCount > 0) {
                    ds.getEntryForIndex(ds.entryCount - 1)?.y = lastHist.second.toFloat()
                }
            }
        }
    }
    chart.invalidate()
}

internal fun updateLastMAInPlace(chart: CombinedChart, state: CryptoDetailState) {
    val lineData = chart.data?.lineData ?: return
    for (i in 0 until lineData.dataSetCount) {
        val ds = lineData.getDataSetByIndex(i)
        if (!ds.label.startsWith("MA|")) continue
        val id = ds.label.removePrefix("MA|")
        val last = state.maLines[id]?.lastOrNull() ?: continue
        if (ds.entryCount > 0) {
            ds.getEntryForIndex(ds.entryCount - 1).y = last.second.toFloat()
        }
    }
    chart.invalidate()
}

internal fun updateLastCandleInPlace(chart: CombinedChart, state: CryptoDetailState) {
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

internal fun updateLastStochInPlace(chart: LineChart, state: CryptoDetailState) {
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

internal fun updateLastRsiInPlace(chart: LineChart, state: CryptoDetailState) {
    val ld = chart.data ?: return
    val lastRsi = state.rsi.lastOrNull() ?: return
    if (ld.dataSets.isNotEmpty()) {
        val ds = ld.getDataSetByIndex(0)
        if (ds.entryCount > 0) {
            ds.getEntryForIndex(ds.entryCount - 1).y = lastRsi.second.toFloat()
        }
    }
    if (ld.dataSets.size > 1) {
        state.rsiMa.lastOrNull()?.let { lastMa ->
            val ds = ld.getDataSetByIndex(1)
            if (ds.entryCount > 0) {
                ds.getEntryForIndex(ds.entryCount - 1).y = lastMa.second.toFloat()
            }
        }
    }
    chart.invalidate()
}
