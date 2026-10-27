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
fun DrawingToolsSheet(
    active: DrawingTool,
    drawKind: DrawKind?,
    magnetOn: Boolean,
    hasFib: Boolean,
    fibCount: Int = 0,
    onPickDraw: (DrawKind) -> Unit,
    onToggleMagnet: () -> Unit,
    onHideSelected: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Herramientas de dibujo", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrawingTopAction(if (magnetOn) "Imán ON" else "Imán", true, onToggleMagnet)
            DrawingTopAction("Ocultar", hasFib, onHideSelected)
            DrawingTopAction("Eliminar", hasFib, onDeleteSelected)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Líneas de tendencia", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrawingToolCell(Icons.Filled.Timeline, "Segmento", active == DrawingTool.DRAW && drawKind == DrawKind.SEGMENT, Modifier.weight(1f)) { onPickDraw(DrawKind.SEGMENT) }
            DrawingToolCell(Icons.Filled.ShowChart, "Línea", active == DrawingTool.DRAW && drawKind == DrawKind.LINE, Modifier.weight(1f)) { onPickDraw(DrawKind.LINE) }
            DrawingToolCell(Icons.Filled.ArrowForward, "Recta", active == DrawingTool.DRAW && drawKind == DrawKind.RAY, Modifier.weight(1f)) { onPickDraw(DrawKind.RAY) }
            DrawingToolCell(Icons.Filled.TrendingUp, "Flecha", active == DrawingTool.DRAW && drawKind == DrawKind.ARROW, Modifier.weight(1f)) { onPickDraw(DrawKind.ARROW) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Líneas horizontales", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrawingToolCell(Icons.Filled.HorizontalRule, "Segmento H", active == DrawingTool.DRAW && drawKind == DrawKind.H_SEGMENT, Modifier.weight(1f)) { onPickDraw(DrawKind.H_SEGMENT) }
            DrawingToolCell(Icons.Filled.Remove, "Línea H", active == DrawingTool.DRAW && drawKind == DrawKind.H_LINE, Modifier.weight(1f)) { onPickDraw(DrawKind.H_LINE) }
            DrawingToolCell(Icons.Filled.ArrowForward, "Recta H", active == DrawingTool.DRAW && drawKind == DrawKind.H_RAY, Modifier.weight(1f)) { onPickDraw(DrawKind.H_RAY) }
            DrawingToolCell(Icons.Filled.DragHandle, "Precio", active == DrawingTool.DRAW && drawKind == DrawKind.PRICE_LINE, Modifier.weight(1f)) { onPickDraw(DrawKind.PRICE_LINE) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Figuras geométricas", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrawingToolCell(Icons.Filled.CheckBoxOutlineBlank, "Rectángulo", active == DrawingTool.DRAW && drawKind == DrawKind.RECT, Modifier.weight(1f)) { onPickDraw(DrawKind.RECT) }
            DrawingToolCell(Icons.Filled.RadioButtonUnchecked, "Círculo", active == DrawingTool.DRAW && drawKind == DrawKind.CIRCLE, Modifier.weight(1f)) { onPickDraw(DrawKind.CIRCLE) }
            DrawingToolCell(Icons.Filled.ChangeHistory, "Triángulo", active == DrawingTool.DRAW && drawKind == DrawKind.TRIANGLE, Modifier.weight(1f)) { onPickDraw(DrawKind.TRIANGLE) }
        }
        Spacer(modifier = Modifier.height(6.dp))
        // ocultar no cierra el modo, solo esconde el trazo
        if (hasFib) {
            Text(
                text = "Trazados: $fibCount — toca Fibo para agregar otro",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DrawingTopAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(InputBg, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) Color.White else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DrawingToolCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                if (selected) AppGreen.copy(alpha = 0.25f) else InputBg,
                RoundedCornerShape(8.dp)
            )
            .border(
                if (selected) 1.dp else 0.dp,
                if (selected) AppGreen else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverlayEditBar(
    modifier: Modifier = Modifier,
    selectionKey: String,
    colorHex: String,
    width: Float,
    hidden: Boolean,
    locked: Boolean,
    showLevels: Boolean,
    onColor: (String) -> Unit,
    onWidth: (Float) -> Unit,
    onLevels: () -> Unit,
    onToggleHide: () -> Unit,
    onToggleLock: () -> Unit,
    onDelete: () -> Unit,
    // relleno de figuras con transparencia, estilo TV (solo dibujos que lo admiten)
    showFill: Boolean = false,
    fillOn: Boolean = false,
    fillAlpha: Float = 0.2f,
    onToggleFill: () -> Unit = {},
    onFillAlpha: (Float) -> Unit = {}
) {
    var showColors by remember { mutableStateOf(false) }
    var showWidths by remember { mutableStateOf(false) }
    var showFillMenu by remember { mutableStateOf(false) }
    // toque en la pestaña contrae/expande, arrastrar la mueve igual que antes
    var collapsed by remember(selectionKey) { mutableStateOf(false) }
    // pestaña para mover la barra a cualquier lado
    var drag by remember { mutableStateOf(Offset.Zero) }
    val tintColor = try {
        Color(colorHex.toColorInt())
    } catch (_: Exception) {
        Color.White
    }
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
                    if (collapsed) {
                        showColors = false
                        showWidths = false
                        showFillMenu = false
                    }
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
        // lapiz abre el mismo picker de las medias
        Box {
            GridCell(onClick = { showColors = true }) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Color",
                    tint = tintColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            DropdownMenu(
                expanded = showColors,
                onDismissRequest = { showColors = false },
                modifier = Modifier.background(InputBg)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    WideColorPicker(
                        currentHex = colorHex,
                        onPick = onColor,
                        key = "fib"
                    )
                }
            }
        }
        GridDivider()
        // grosor con preview de linea
        Box {
            GridCellText(text = fibWidthLabel(width), onClick = { showWidths = true })
            DropdownMenu(
                expanded = showWidths,
                onDismissRequest = { showWidths = false },
                modifier = Modifier.background(InputBg)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    FIB_WIDTH_OPTIONS.forEach { w ->
                        Row(
                            modifier = Modifier
                                .clickable {
                                    onWidth(w)
                                    showWidths = false
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(w.dp.coerceAtLeast(1.dp))
                                    .background(tintColor, RoundedCornerShape(1.dp))
                            )
                            Text(
                                fibWidthLabel(w),
                                color = if (w == width) Color.White else Color.Gray,
                                fontSize = 13.sp,
                                fontWeight = if (w == width) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
        GridDivider()
        // bote = relleno de figuras con transparencia, estilo TV (solo dibujos)
        if (showFill) {
            Box {
                GridCell(onClick = { showFillMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.FormatColorFill,
                        contentDescription = "Relleno",
                        tint = if (fillOn) AppGreen else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                }
                DropdownMenu(
                    expanded = showFillMenu,
                    onDismissRequest = { showFillMenu = false },
                    modifier = Modifier.background(InputBg)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Relleno",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = fillOn, onCheckedChange = { onToggleFill() })
                        }
                        val sliderPos = remember(fillAlpha) { mutableStateOf(fillAlpha) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = sliderPos.value,
                                onValueChange = { sliderPos.value = it },
                                onValueChangeFinished = { onFillAlpha(sliderPos.value) },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${(sliderPos.value * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
            GridDivider()
        }
        // tuerca = niveles (solo fibo) y estilo del seleccionado
        if (showLevels) {
            GridCell(onClick = onLevels) {
                Icon(
                    painter = painterResource(R.drawable.ic_nut_tv),
                    contentDescription = "Niveles",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            GridDivider()
        }
        // ojo con raya cuando esta oculto
        GridCell(onClick = onToggleHide) {
            Icon(
                imageVector = if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = "Visible",
                tint = Color.Gray,
                modifier = Modifier.size(22.dp)
            )
        }
        GridDivider()
        // candado azul cerrado bloquea mover/resize
        GridCell(onClick = onToggleLock) {
            Icon(
                imageVector = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = "Bloquear",
                tint = if (locked) ChartColors.blue else Color.Gray,
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
