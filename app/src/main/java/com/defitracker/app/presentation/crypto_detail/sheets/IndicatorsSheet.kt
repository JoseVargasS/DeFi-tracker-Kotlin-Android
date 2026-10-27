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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndicatorsSheet(
    prefs: IndicatorPrefs,
    onToggleBB: () -> Unit,
    onToggleProfile: () -> Unit,
    onToggleVolume: () -> Unit,
    onToggleStoch: () -> Unit,
    onToggleRsi: () -> Unit,
    onToggleMacd: () -> Unit,
    onToggleTaker: () -> Unit,
    onToggleRsiDiv: () -> Unit,
    onToggleRsiDivHidden: () -> Unit,
    onOpenMAs: () -> Unit = {},
    onToggleSmcStructure: () -> Unit,
    onToggleSmcOB: () -> Unit,
    onToggleSmcFvg: () -> Unit,
    onToggleSmcPremium: () -> Unit,
    onToggleSmcEqhl: () -> Unit,
    onToggleSmcLiq: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Indicadores",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        SheetSectionTitle("Principales")
        Spacer(modifier = Modifier.height(8.dp))
        PillGrid(
            options = listOf(
                PillOption("BOLL", prefs.bbVisible, onToggleBB),
                PillOption("Perfil", prefs.profileVisible, onToggleProfile)
            )
        )
        // medias en su propio bottom sheet, resumen de activas
        run {
            val maActive = prefs.mas.count { it.visible }
            val summary = prefs.mas.take(5).joinToString(" · ") { maLegendLabel(it) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenMAs() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Medias móviles ($maActive/${prefs.mas.size})",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (summary.isNotBlank()) summary else "Toca para configurar",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                Text(text = "▸", color = Color.Gray, fontSize = 16.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        SheetSectionTitle("Subindicadores")
        Spacer(modifier = Modifier.height(8.dp))
        PillGrid(
            options = listOf(
                PillOption("VOL", prefs.volumeVisible, onToggleVolume),
                PillOption("StochRSI", prefs.stochVisible, onToggleStoch),
                PillOption("RSI", prefs.rsiVisible, onToggleRsi),
                PillOption("MACD", prefs.macdVisible, onToggleMacd),
                PillOption("C/V Taker", prefs.takerVisible, onToggleTaker)
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        SheetSectionTitle("Divergencias")
        Spacer(modifier = Modifier.height(8.dp))
        PillGrid(
            options = listOf(
                PillOption("DIV", prefs.rsiDivVisible, onToggleRsiDiv),
                PillOption("HDIV", prefs.rsiDivHidden, onToggleRsiDivHidden)
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        SheetSectionTitle("Smart Money")
        Spacer(modifier = Modifier.height(8.dp))
        PillGrid(
            options = listOf(
                PillOption("BOS / CHoCH", prefs.smcStructure, onToggleSmcStructure),
                PillOption("Order Blocks", prefs.smcOrderBlocks, onToggleSmcOB),
                PillOption("FVG", prefs.smcFvg, onToggleSmcFvg),
                PillOption("Premium/Discount", prefs.smcPremium, onToggleSmcPremium),
                PillOption("EQH / EQL", prefs.smcEqhl, onToggleSmcEqhl),
                PillOption("Liquidez BSL/SSL", prefs.smcLiquidity, onToggleSmcLiq)
            ),
            columns = 2
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}
