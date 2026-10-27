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
fun MaListSheet(
    mas: List<MaConfig>,
    onToggle: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onType: (String, MaType) -> Unit = { _, _ -> },
    onPeriod: (String, Int) -> Unit = { _, _ -> },
    onTimeframe: (String, String) -> Unit = { _, _ -> },
    onColor: (String, String) -> Unit = { _, _ -> },
    onWidth: (String, Float) -> Unit = { _, _ -> }
) {
    var expandedColorId by remember { mutableStateOf<String?>(null) }
    var tfMenuId by remember { mutableStateOf<String?>(null) }
    var widthMenuId by remember { mutableStateOf<String?>(null) }
    var periodDialogId by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Medias móviles",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "+ Agregar",
                color = AppGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onAdd() }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        mas.forEach { ma ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // circulo visible como en tradingview
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .border(
                                1.5.dp,
                                if (ma.visible) AppGreen else Color.Gray,
                                CircleShape
                            )
                            .background(
                                if (ma.visible) AppGreen.copy(alpha = 0.2f) else Color.Transparent,
                                CircleShape
                            )
                            .clickable { onToggle(ma.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (ma.visible) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Visible",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // titulo solo detalle, sin config externa: todo es inline
                    Text(
                        text = maLegendLabel(ma),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // rejilla pegada: tipo, periodo, grosor, color, TF
                    Row(
                        modifier = Modifier
                            .background(InputBg, RoundedCornerShape(8.dp))
                            .border(1.dp, ChartColors.border, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // tipo SMA/EMA
                        MaSegText(
                            text = if (ma.type == MaType.EMA) "EMA" else "SMA",
                            width = 50.dp,
                            onClick = {
                                onType(ma.id, if (ma.type == MaType.EMA) MaType.SMA else MaType.EMA)
                            }
                        )
                        MaSegDiv()
                        // periodo
                        MaSegText(
                            text = ma.period.toString(),
                            width = 44.dp,
                            onClick = { periodDialogId = ma.id }
                        )
                        MaSegDiv()
                        // grosor con preview de linea
                        Box {
                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(36.dp)
                                    .clickable { widthMenuId = ma.id },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(30.dp)
                                        .height(ma.width.dp.coerceAtLeast(1.dp))
                                        .background(Color(ma.colorHex.toColorInt()), RoundedCornerShape(1.dp))
                                )
                            }
                            DropdownMenu(
                                expanded = widthMenuId == ma.id,
                                onDismissRequest = { widthMenuId = null },
                                modifier = Modifier.background(InputBg)
                            ) {
                                FIB_WIDTH_OPTIONS.forEach { w ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(36.dp)
                                                        .height(w.dp.coerceAtLeast(1.dp))
                                                        .background(Color(ma.colorHex.toColorInt()), RoundedCornerShape(1.dp))
                                                )
                                                Text(
                                                    maWidthLabel(w),
                                                    color = if (w == ma.width) Color.White else Color.Gray,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (w == ma.width) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        },
                                        onClick = {
                                            onWidth(ma.id, w)
                                            widthMenuId = null
                                        }
                                    )
                                }
                            }
                        }
                        MaSegDiv()
                        // color redondo, despliega el picker debajo
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(36.dp)
                                .clickable {
                                    expandedColorId = if (expandedColorId == ma.id) null else ma.id
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(Color(ma.colorHex.toColorInt()), CircleShape)
                                    .border(
                                        if (expandedColorId == ma.id) 2.dp else 1.dp,
                                        if (expandedColorId == ma.id) Color.White else ChartColors.border,
                                        CircleShape
                                    )
                            )
                        }
                        MaSegDiv()
                        // temporalidad
                        Box {
                            MaSegText(
                                text = if (ma.timeframe == "chart") "GRAF" else ma.timeframe,
                                width = 54.dp,
                                onClick = { tfMenuId = ma.id }
                            )
                            DropdownMenu(
                                expanded = tfMenuId == ma.id,
                                onDismissRequest = { tfMenuId = null },
                                modifier = Modifier.background(InputBg)
                            ) {
                                IndicatorPrefs.MA_TFS.forEach { tf ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (tf == "chart") "GRAF" else tf,
                                                color = Color.White,
                                                fontSize = 13.sp
                                            )
                                        },
                                        onClick = {
                                            onTimeframe(ma.id, tf)
                                            tfMenuId = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (mas.size > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_trash_tv),
                            contentDescription = "Eliminar ${maLegendLabel(ma)}",
                            tint = Color.Gray,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onDelete(ma.id) }
                        )
                    }
                }
                // picker inline debajo de la fila
                AnimatedVisibility(visible = expandedColorId == ma.id) {
                    Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                        WideColorPicker(
                            currentHex = ma.colorHex,
                            onPick = { onColor(ma.id, it) },
                            key = ma.id
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
        // dialogo compacto para el periodo
        periodDialogId?.let { dialogId ->
            mas.firstOrNull { it.id == dialogId }?.let { ma ->
                var text by remember(dialogId) { mutableStateOf(ma.period.toString()) }
                AlertDialog(
                    onDismissRequest = { periodDialogId = null },
                    containerColor = SheetBg,
                    titleContentColor = Color.White,
                    textContentColor = Color.Gray,
                    title = { Text("Periodo ${maLegendLabel(ma)}") },
                    text = {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { v -> text = v.filter { it.isDigit() }.take(3) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppGreen,
                                unfocusedBorderColor = LineDiv,
                                cursorColor = Color.White
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            text.toIntOrNull()?.let { p ->
                                if (p in 2..500) onPeriod(ma.id, p)
                            }
                            periodDialogId = null
                        }) { Text("Listo", color = AppGreen) }
                    },
                    dismissButton = {
                        TextButton(onClick = { periodDialogId = null }) { Text("Cancelar", color = Color.White) }
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MaSegText(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(36.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun MaSegDiv() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(20.dp)
            .background(LineDiv)
    )
}
