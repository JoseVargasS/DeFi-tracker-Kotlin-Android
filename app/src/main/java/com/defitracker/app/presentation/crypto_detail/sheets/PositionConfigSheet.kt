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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PositionBar(
    modifier: Modifier = Modifier,
    selectionKey: String,
    profitAlpha: Float,
    lossAlpha: Float,
    onProfitAlpha: (Float) -> Unit,
    onLossAlpha: (Float) -> Unit,
    onConfig: () -> Unit,
    onDelete: () -> Unit
) {
    var showPaint by remember { mutableStateOf(false) }
    var collapsed by remember(selectionKey) { mutableStateOf(false) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    Row(
        modifier = modifier
            .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
            .background(ChartColors.sheetOverlay, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // pestaña de arrastre: toque contrae/expande, mover arrastra
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 40.dp)
                .combinedClickable(onClick = {
                    collapsed = !collapsed
                    if (collapsed) showPaint = false
                })
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        drag += dragAmount
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (collapsed) Icons.Filled.KeyboardArrowRight else Icons.Default.DragHandle,
                contentDescription = if (collapsed) "Expandir" else "Mover",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
        if (!collapsed) {
            GridDivider()
            // pincel = transparencia de cada color
            Box {
                GridCell(onClick = { showPaint = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_draw_tv),
                        contentDescription = "Transparencias",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                DropdownMenu(
                    expanded = showPaint,
                    onDismissRequest = { showPaint = false },
                    modifier = Modifier.background(InputBg)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Ganancia", color = AppGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        val profitPos = remember(profitAlpha) { mutableStateOf(profitAlpha) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = profitPos.value,
                                onValueChange = { profitPos.value = it },
                                onValueChangeFinished = { onProfitAlpha(profitPos.value) },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${(profitPos.value * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
                        }
                        Text("Pérdida", color = AppRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        val lossPos = remember(lossAlpha) { mutableStateOf(lossAlpha) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = lossPos.value,
                                onValueChange = { lossPos.value = it },
                                onValueChangeFinished = { onLossAlpha(lossPos.value) },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${(lossPos.value * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
            GridDivider()
            // tuerca = entrada, SL, TP, leverage, cantidad
            GridCell(onClick = onConfig) {
                Icon(
                    painter = painterResource(R.drawable.ic_nut_tv),
                    contentDescription = "Configurar",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            GridDivider()
            GridCell(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_trash_tv),
                    contentDescription = "Eliminar",
                    tint = Color.Gray,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private val POSITION_LEVERAGES = listOf(1, 2, 3, 5, 10, 15, 20, 50, 100)


@Composable
internal fun PositionConfigSheet(
    pos: PositionOverlay,
    currentPrice: Double,
    extended: Boolean,
    onLeverage: (Int) -> Unit,
    onApply: (entry: Double, sl: Double, tp: Double, qty: Double) -> Unit
) {
    var entryTxt by remember(pos.id) { mutableStateOf(formatAxisPrice(pos.entry, currentPrice, extended)) }
    var slTxt by remember(pos.id) { mutableStateOf(formatAxisPrice(pos.sl, currentPrice, extended)) }
    var tpTxt by remember(pos.id) { mutableStateOf(formatAxisPrice(pos.tp, currentPrice, extended)) }
    var qtyTxt by remember(pos.id) { mutableStateOf(pos.qtyUsdt.toString()) }
    var error by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            if (pos.long) "Posición LONG" else "Posición SHORT",
            color = if (pos.long) AppGreen else AppRed,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PosNumberField("Entrada", entryTxt, { entryTxt = it }, Modifier.weight(1f))
            PosNumberField("Stop loss", slTxt, { slTxt = it }, Modifier.weight(1f))
            PosNumberField("Take profit", tpTxt, { tpTxt = it }, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        PosNumberField("Cantidad (USDT)", qtyTxt, { qtyTxt = it }, Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Text("Leverage · ${pos.leverage}x", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            POSITION_LEVERAGES.forEach { lev ->
                val on = pos.leverage == lev
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (on) AppGreen else InputBg,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onLeverage(lev) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${lev}x",
                        color = if (on) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // vista previa ganancia/perdida estimada en USDT
        val (tpPct, tpAmt) = positionLineStats(pos.entry, pos.tp, pos.qtyUsdt, pos.leverage)
        val (slPct, slAmt) = positionLineStats(pos.entry, pos.sl, pos.qtyUsdt, pos.leverage)
        Text(
            "TP ${String.format(Locale.US, "%+.2f%%", tpPct)} · ${String.format(Locale.US, "%+.2f USDT", tpAmt)}   |   " +
                "SL ${String.format(Locale.US, "%+.2f%%", slPct)} · ${String.format(Locale.US, "%+.2f USDT", slAmt)}",
            color = Color.Gray,
            fontSize = 12.sp
        )
        if (error.isNotBlank()) {
            Text(error, color = AppRed, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val e = parsePriceInput(entryTxt) ?: 0.0
                val s = parsePriceInput(slTxt) ?: 0.0
                val t = parsePriceInput(tpTxt) ?: 0.0
                val q = qtyTxt.toDoubleValue()
                if (e <= 0 || s <= 0 || t <= 0 || q <= 0) {
                    error = "Revisa los valores (todos mayores a 0)"
                    return@Button
                }
                error = ""
                onApply(e, s, t, q)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (pos.long) AppGreen else AppRed,
                contentColor = Color.Black
            )
        ) {
            Text("Aplicar", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PosNumberField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = AppGreen,
            focusedBorderColor = AppGreen,
            unfocusedBorderColor = Color.Gray,
            focusedLabelColor = AppGreen,
            unfocusedLabelColor = Color.Gray
        )
    )
}
