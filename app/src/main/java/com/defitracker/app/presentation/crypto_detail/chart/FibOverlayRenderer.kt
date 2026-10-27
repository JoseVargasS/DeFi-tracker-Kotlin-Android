package com.defitracker.app.presentation.crypto_detail.chart

import android.graphics.*
import android.graphics.Color as GraphicsColor
import com.github.mikephil.charting.components.YAxis
import java.util.Locale
import kotlin.math.*
import com.defitracker.app.ui.theme.ChartColors

import android.graphics.Canvas
import com.defitracker.app.presentation.crypto_detail.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal fun PriceCombinedChart.anchorFromTouch(x: Float, y: Float): FibAnchor? {
    val candles = stateRef.value.candles
    if (candles.isEmpty()) return null
    val contentLeft = viewPortHandler.contentLeft()
    val contentRight = viewPortHandler.contentRight()
    val contentTop = viewPortHandler.contentTop()
    val contentBottom = viewPortHandler.contentBottom()
    if (x < contentLeft || x > contentRight || y < contentTop || y > contentBottom) return null
    // indice virtual para anclar mas alla de la ultima vela
    val rawX = getTransformer(YAxis.AxisDependency.LEFT).getValuesByTouchPoint(x, y).x.roundToInt()
    val index = rawX.coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
    val price = getTransformer(YAxis.AxisDependency.LEFT).getValuesByTouchPoint(x, y).y.toDouble()
    return FibAnchor(fibIndexToTime(candles, index, stateRef.value.selectedInterval), price)
}

internal fun PriceCombinedChart.overlayPixels(o: FibOverlay): Pair<android.graphics.PointF?, android.graphics.PointF?> {
    val candles = stateRef.value.candles
    if (candles.isEmpty()) return null to null
    val trans = getTransformer(YAxis.AxisDependency.LEFT)
    val interval = stateRef.value.selectedInterval
    val sIdx = fibTimeToIndexVirtual(candles, o.start.time, interval)
    val eIdx = fibTimeToIndexVirtual(candles, o.end.time, interval)
    val s = floatArrayOf(sIdx.toFloat(), o.start.price.toFloat())
    trans.pointValuesToPixel(s)
    val e = floatArrayOf(eIdx.toFloat(), o.end.price.toFloat())
    trans.pointValuesToPixel(e)
    return android.graphics.PointF(s[0], s[1]) to android.graphics.PointF(e[0], e[1])
}

internal fun PriceCombinedChart.distToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val len2 = dx * dx + dy * dy
    if (len2 <= 0f) return kotlin.math.hypot(px - ax, py - ay)
    val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
    return kotlin.math.hypot(px - (ax + t * dx), py - (ay + t * dy))
}

// devuelve (id, zona, locked) del fibo mas cercano al toque, o null
internal fun PriceCombinedChart.fibHitTest(x: Float, y: Float): Triple<String, Int, Boolean>? {
    val candles = stateRef.value.candles
    if (candles.isEmpty()) return null
    if (prefsRef.value.overlaysHidden) return null
    val trans = getTransformer(YAxis.AxisDependency.LEFT)
    var best: Triple<String, Int, Boolean>? = null
    var bestDist = Float.MAX_VALUE
    overlaysRef.value.forEach { o ->
        if (o.hidden) return@forEach
        val (s, e) = overlayPixels(o)
        if (s == null || e == null) return@forEach
        val ds = kotlin.math.hypot(x - s.x, y - s.y)
        if (ds <= 60f && ds < bestDist) {
            bestDist = ds
            best = Triple(o.id, 1, o.locked)
        }
        val de = kotlin.math.hypot(x - e.x, y - e.y)
        if (de <= 60f && de < bestDist) {
            bestDist = de
            best = Triple(o.id, 2, o.locked)
        }
        if (bestDist <= 60f) return@forEach
        // cuerpo = lineas de nivel + guia diagonal (mover)
        val leftX = min(s.x, e.x)
        val rightX = max(s.x, e.x)
        var bodyDist = distToSegment(x, y, s.x, s.y, e.x, e.y)
        o.levelsSorted().forEach { ratio ->
            val price = fibLevelPrice(o.end.price, o.start.price, ratio)
            val pts = floatArrayOf(0f, price.toFloat())
            trans.pointValuesToPixel(pts)
            if (x >= leftX - 20f && x <= rightX + 20f && abs(y - pts[1]) <= 28f) {
                bodyDist = 0f
            }
        }
        if (bodyDist <= 28f && bodyDist < bestDist) {
            bestDist = bodyDist
            best = Triple(o.id, 3, o.locked)
        }
    }
    return best
}

internal fun PriceCombinedChart.applyFibTap(x: Float, y: Float) {
    // fibo tambien respeta el iman (high/low/close)
    val anchor = drawAnchorFromTouch(x, y) ?: return
    onFibTap?.invoke(anchor)
    invalidate()
}

// ─── DIBUJOS OKX ───
internal fun PriceCombinedChart.drawFibOverlays(canvas: Canvas, candles: List<CandleData>) {
    val overlays = overlaysRef.value
    if (overlays.isEmpty() || candles.isEmpty()) {
        drawFibPendingHint(canvas, candles)
        return
    }
    overlays.forEach { o ->
        if (!o.hidden) drawSingleFib(canvas, candles, o, o.id == selectedFibRef.value)
    }
    drawFibPendingHint(canvas, candles)
}

internal fun PriceCombinedChart.drawSingleFib(canvas: Canvas, candles: List<CandleData>, o: FibOverlay, selected: Boolean) {
    val levels = o.levelsSorted()
    if (levels.isEmpty()) return
    val trans = getTransformer(YAxis.AxisDependency.LEFT)
    val contentLeft = viewPortHandler.contentLeft()
    val contentRight = viewPortHandler.contentRight()
    val contentTop = viewPortHandler.contentTop()
    val contentBottom = viewPortHandler.contentBottom()

    val sIdx = fibTimeToIndexVirtual(candles, o.start.time, stateRef.value.selectedInterval)
    val eIdx = fibTimeToIndexVirtual(candles, o.end.time, stateRef.value.selectedInterval)
    val sPts = floatArrayOf(sIdx.toFloat(), o.start.price.toFloat())
    trans.pointValuesToPixel(sPts)
    val ePts = floatArrayOf(eIdx.toFloat(), o.end.price.toFloat())
    trans.pointValuesToPixel(ePts)
    val leftX = min(sPts[0], ePts[0]).coerceIn(contentLeft, contentRight)
    // lineas acotadas al rango inicio-fin, no hasta el borde
    val rightX = max(sPts[0], ePts[0]).coerceIn(contentLeft, contentRight)

    val lineColor = try {
        GraphicsColor.parseColor(o.colorHex)
    } catch (_: Exception) {
        GraphicsColor.WHITE
    }
    fibLinePaint.color = lineColor
    fibLinePaint.strokeWidth = (o.width * context.resources.displayMetrics.density).coerceAtLeast(1f)
    // seleccionado a full, los demas tenues
    fibLinePaint.alpha = if (selected || selectedFibRef.value == null) 255 else 140
    fibLabelPaint.color = GraphicsColor.WHITE

    // tags siempre visibles (tenues si no es el seleccionado), como las lineas
    fibLabelPaint.alpha = if (selected || selectedFibRef.value == null) 255 else 140
    levels.forEach { ratio ->
        // punto inicial siempre 1, final siempre 0
        val price = fibLevelPrice(o.end.price, o.start.price, ratio)
        val pts = floatArrayOf(0f, price.toFloat())
        trans.pointValuesToPixel(pts)
        val py = pts[1].coerceIn(contentTop, contentBottom)
        canvas.drawLine(leftX, py, rightX, py, fibLinePaint)
        // nivel + precio entre parentesis a la derecha de la linea, sin fondo.
        // Si el final queda fuera de pantalla (fibo gigante en TF menor),
        // el tag se clava al borde derecho visible sobre su linea
        val label = "${trimRatio(ratio)}(${formatAxisPrice(price, candles.lastOrNull()?.close ?: price, stateRef.value.daySubDollar)})"
        val w = fibLabelPaint.measureText(label)
        val lx = (rightX + 6f).coerceIn(contentLeft, (contentRight - w).coerceAtLeast(contentLeft))
        canvas.drawText(label, lx, py - 6f, fibLabelPaint)
    }
    fibLabelPaint.alpha = 255

    // handles solo en el seleccionado, grises si esta candadeado
    if (selected) {
        val r = 22f
        fibHandleStroke.color = if (o.locked) GraphicsColor.GRAY
        else GraphicsColor.parseColor(ChartColors.BLUE_HEX)
        canvas.drawCircle(sPts[0], sPts[1], r, fibHandleFill)
        canvas.drawCircle(sPts[0], sPts[1], r, fibHandleStroke)
        canvas.drawCircle(ePts[0], ePts[1], r, fibHandleFill)
        canvas.drawCircle(ePts[0], ePts[1], r, fibHandleStroke)
    }
    fibLinePaint.alpha = 255
}

internal fun PriceCombinedChart.drawFibPendingHint(canvas: Canvas, candles: List<CandleData>) {
    val pending = pendingStartRef.value ?: return
    val trans = getTransformer(YAxis.AxisDependency.LEFT)
    val contentLeft = viewPortHandler.contentLeft()
    val contentRight = viewPortHandler.contentRight()
    val contentTop = viewPortHandler.contentTop()
    val contentBottom = viewPortHandler.contentBottom()
    val pIdx = fibTimeToIndexVirtual(candles, pending.time, stateRef.value.selectedInterval)
    val p = floatArrayOf(pIdx.toFloat(), pending.price.toFloat())
    trans.pointValuesToPixel(p)
    canvas.drawCircle(p[0], p[1], 9f, dotPaint)
    canvas.drawCircle(p[0], p[1], 9f, dotOutlinePaint)
    val label = "Toca fin"
    val lw2 = selectionTextPaint.measureText(label) + 18f
    val lh2 = selectionTextPaint.textSize + 12f
    val ll = (p[0] + 10f).coerceIn(contentLeft + 4f, (contentRight - lw2 - 4f).coerceAtLeast(contentLeft + 4f))
    val lt = (p[1] - lh2 - 10f).coerceIn(contentTop + 4f, (contentBottom - lh2 - 4f).coerceAtLeast(contentTop + 4f))
    yTagRect.set(ll, lt, ll + lw2, lt + lh2)
    canvas.drawRoundRect(yTagRect, 5f, 5f, selectionLabelPaint)
    canvas.drawText(label, ll + 9f, lt + lh2 - 8f, selectionTextPaint)
}

// ─── SMC ───
// fondo tenue detras de las velas (premium/discount)







