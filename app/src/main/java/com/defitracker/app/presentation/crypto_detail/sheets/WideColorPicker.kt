package com.defitracker.app.presentation.crypto_detail.sheets

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

private fun hexToHsv(hex: String): FloatArray {
    return try {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(hex), hsv)
        hsv
    } catch (_: Exception) {
        floatArrayOf(160f, 0.9f, 0.9f)
    }
}

private fun hsvToHex(h: Float, s: Float, v: Float): String =
    String.format("#%06X", 0xFFFFFF and android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))

@Composable
fun WideColorPicker(
    currentHex: String,
    onPick: (String) -> Unit,
    key: String = currentHex
) {
    // claveado por key (id de la MA / "fib") para que el drag no se reinicie al recomponer
    val init = remember(key) { hexToHsv(currentHex) }
    var hue by remember(key) { mutableStateOf(init[0]) }
    var sat by remember(key) { mutableStateOf(init[1]) }
    var valV by remember(key) { mutableStateOf(init[2]) }
    fun emit(h: Float = hue, s: Float = sat, v: Float = valV) {
        onPick(hsvToHex(h, s, v))
    }
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val density = LocalDensity.current
    // tamaño real medido (sin BoxWithConstraints: rompe los DropdownMenu con intrinsics)
    var svSize by remember(key) { mutableStateOf(IntSize.Zero) }
    var hueBarW by remember(key) { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // presets compactos en una fila
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IndicatorPrefs.PRESET_COLORS.forEach { hex ->
                val selected = hex.equals(currentHex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color(hex.toColorInt()), CircleShape)
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = if (selected) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable {
                            val hsv = hexToHsv(hex)
                            hue = hsv[0]
                            sat = hsv[1]
                            valV = hsv[2]
                            onPick(hex)
                        }
                )
            }
        }
        // cuadrado saturacion (x) / valor (y) del tono actual
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                .onSizeChanged { svSize = it }
        ) {
            fun updateSv(offset: androidx.compose.ui.geometry.Offset) {
                val w = svSize.width
                val h = svSize.height
                if (w <= 0 || h <= 0) return
                sat = (offset.x / w).coerceIn(0f, 1f)
                valV = 1f - (offset.y / h).coerceIn(0f, 1f)
                emit()
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    .pointerInput(hue) {
                        detectTapGestures(onTap = { updateSv(it) })
                    }
                    .pointerInput(hue) {
                        detectDragGestures(
                            onDragStart = { updateSv(it) },
                            onDrag = { change, _ -> updateSv(change.position) }
                        )
                    }
            )
            // selector sobre el color elegido
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { (svSize.width * sat).toDp() } - 11.dp,
                        y = with(density) { (svSize.height * (1f - valV)).toDp() } - 11.dp
                    )
                    .size(22.dp)
                    .border(2.dp, Color.White, CircleShape)
                    .background(Color.Transparent, CircleShape)
            )
        }
        // barra inferior de tonos
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { h ->
                            Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 1f, 1f)))
                        }
                    )
                )
                .onSizeChanged { hueBarW = it.width }
        ) {
            fun updateHue(x: Float) {
                if (hueBarW <= 0) return
                hue = (x / hueBarW * 360f).coerceIn(0f, 360f)
                emit()
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { updateHue(it.x) })
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { updateHue(it.x) },
                            onDrag = { change, _ -> updateHue(change.position.x) }
                        )
                    }
            )
            // thumb sobre el tono actual
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { (hueBarW * (hue / 360f)).toDp() } - 11.dp,
                        y = (-1).dp
                    )
                    .size(22.dp)
                    .border(2.dp, Color.White, CircleShape)
                    .background(Color.Transparent, CircleShape)
            )
        }
    }
}
