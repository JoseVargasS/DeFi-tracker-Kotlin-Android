package com.defitracker.app.presentation.crypto_detail.chart

import android.graphics.*
import android.graphics.Color as GraphicsColor
import com.github.mikephil.charting.components.YAxis
import java.util.Locale
import kotlin.math.*

import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.RectF
import com.defitracker.app.presentation.crypto_detail.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class PosGeom(
    val leftX: Float,
    val rightX: Float,
    val entryY: Float,
    val topY: Float,
    val botY: Float,
    val topIsProfit: Boolean
)

internal fun PriceCombinedChart.positionGeom(o: PositionOverlay): PosGeom? {
    val candles = stateRef.value.candles
    if (candles.isEmpty()) return null
    val trans = getTransformer(YAxis.AxisDependency.LEFT)
    val interval = stateRef.value.selectedInterval
    val sIdx = fibTimeToIndexVirtual(candles, o.startTime, interval)
    val eIdx = fibTimeToIndexVirtual(candles, o.endTime, interval)
    val lx = floatArrayOf(min(sIdx, eIdx).toFloat(), 0f)
    trans.pointValuesToPixel(lx)
    val rx = floatArrayOf(max(sIdx, eIdx).toFloat(), 0f)
    trans.pointValuesToPixel(rx)
    val ey = floatArrayOf(0f, o.entry.toFloat())
    trans.pointValuesToPixel(ey)
    val topPrice = max(o.sl, o.tp)
    val botPrice = min(o.sl, o.tp)
    val ty = floatArrayOf(0f, topPrice.toFloat())
    trans.pointValuesToPixel(ty)
    val by = floatArrayOf(0f, botPrice.toFloat())
    trans.pointValuesToPixel(by)
    // el verde sigue al TP en ambas direcciones: en short arriba va el SL (rojo)
    val topIsProfit = topPrice == o.tp
    return PosGeom(lx[0], rx[0], ey[1], ty[1], by[1], topIsProfit)
}

internal fun PriceCombinedChart.positionLabel(o: PositionOverlay, line: Double): String {
    val priceTxt = formatPriceForChart(line, stateRef.value.daySubDollar)
    if (o.entry <= 0.0) return priceTxt
    // % del movimiento del precio en la grafica, con signo segun la direccion:
    // TP siempre +, SL siempre -. Monto final = cantidad + PnL de ese lado.
    val rawMove = (line - o.entry) / o.entry * 100.0
    val dir = if (o.long) 1.0 else -1.0
    val pct = rawMove * dir
    val pnl = o.qtyUsdt * pct * o.leverage / 100.0
    val finalQty = o.qtyUsdt + pnl
    val qtyTxt = String.format(Locale.US, "%.2f", finalQty)
    val pctTxt = "${if (pct >= 0) "+" else ""}${String.format(Locale.US, "%.2f", pct)}%"
    // sin precio: ya se ve en el eje Y
    return "$qtyTxt ($pctTxt)"
}

internal fun PriceCombinedChart.drawPositionLabel(canvas: Canvas, text: String, x: Float, y: Float, bg: Int, right: Boolean = false) {
    val w = selectionTextPaint.measureText(text) + 18f
    val h = selectionTextPaint.textSize + 12f
    val contentLeft = viewPortHandler.contentLeft()
    val contentRight = viewPortHandler.contentRight()
    val contentTop = viewPortHandler.contentTop()
    val contentBottom = viewPortHandler.contentBottom()
    // etiquetas de SL/TP al borde derecho pa' no tapar los handles del centro
    val lx = if (right) {
        (x - w - 8f).coerceIn(contentLeft + 4f, (contentRight - w - 4f).coerceAtLeast(contentLeft + 4f))
    } else {
        (x - w / 2f).coerceIn(contentLeft + 4f, (contentRight - w - 4f).coerceAtLeast(contentLeft + 4f))
    }
    val ly = (y - h / 2f).coerceIn(contentTop + 4f, (contentBottom - h - 4f).coerceAtLeast(contentTop + 4f))
    yTagRect.set(lx, ly, lx + w, ly + h)
    selectionLabelPaint.color = bg
    canvas.drawRoundRect(yTagRect, 5f, 5f, selectionLabelPaint)
    canvas.drawText(text, lx + 9f, ly + h - 8f, selectionTextPaint)
}

// chip con el valor en el eje Y, como la linea de entrada
internal fun PriceCombinedChart.drawPosYTag(canvas: Canvas, price: Double, bg: Int) {
    val txt = formatPriceForChart(price, stateRef.value.daySubDollar)
    val w = selectionTextPaint.measureText(txt) + 16f
    val h = selectionTextPaint.textSize + 10f
    val contentLeft = viewPortHandler.contentLeft()
    val contentRight = viewPortHandler.contentRight()
    val contentTop = viewPortHandler.contentTop()
    val contentBottom = viewPortHandler.contentBottom()
    val trans = getTransformer(YAxis.AxisDependency.LEFT)
    val pts = floatArrayOf(0f, price.toFloat())
    trans.pointValuesToPixel(pts)
    val rx = (contentRight - w).coerceAtLeast(contentLeft)
    val ly = (pts[1] - h / 2f).coerceIn(contentTop, (contentBottom - h).coerceAtLeast(contentTop))
    yTagRect.set(rx, ly, rx + w, ly + h)
    selectionLabelPaint.color = bg
    canvas.drawRoundRect(yTagRect, 4f, 4f, selectionLabelPaint)
    canvas.drawText(txt, rx + 8f, ly + h - 7f, selectionTextPaint)
}

internal fun PriceCombinedChart.drawSinglePosition(canvas: Canvas, _candles: List<CandleData>, o: PositionOverlay, selected: Boolean) {
    val g = positionGeom(o) ?: return
    val profitColor = GraphicsColor.parseColor(POS_PROFIT_HEX)
    val lossColor = GraphicsColor.parseColor(POS_LOSS_HEX)
    val topColor = if (g.topIsProfit) profitColor else lossColor
    val botColor = if (g.topIsProfit) lossColor else profitColor
    val topAlpha = ((if (g.topIsProfit) o.profitAlpha else o.lossAlpha).coerceIn(0f, 1f) * 255).toInt()
    val botAlpha = ((if (g.topIsProfit) o.lossAlpha else o.profitAlpha).coerceIn(0f, 1f) * 255).toInt()
    // rellenos ganancia/perdida con su transparencia
    drawFillPaint.color = topColor
    drawFillPaint.alpha = topAlpha
    canvas.drawRect(g.leftX, min(g.entryY, g.topY), g.rightX, max(g.entryY, g.topY), drawFillPaint)
    drawFillPaint.color = botColor
    drawFillPaint.alpha = botAlpha
    canvas.drawRect(g.leftX, min(g.entryY, g.botY), g.rightX, max(g.entryY, g.botY), drawFillPaint)
    // bordes TP/SL + linea de entrada
    drawLinePaint.color = topColor
    drawLinePaint.strokeWidth = 2f
    drawLinePaint.alpha = 255
    canvas.drawLine(g.leftX, g.topY, g.rightX, g.topY, drawLinePaint)
    drawLinePaint.color = botColor
    canvas.drawLine(g.leftX, g.botY, g.rightX, g.botY, drawLinePaint)
    drawLinePaint.color = GraphicsColor.WHITE
    canvas.drawLine(g.leftX, g.entryY, g.rightX, g.entryY, drawLinePaint)
    drawLinePaint.alpha = 255
    // etiquetas: solo con el dibujo seleccionado, centradas sobre la caja
    val topLine = if (g.topIsProfit) o.tp else o.sl
    val botLine = if (g.topIsProfit) o.sl else o.tp
    // handles cuadrados si esta seleccionada: arriba/abajo al centro,
    // el del medio fuera de la caja pa' no chocar con las etiquetas
    if (selected) {
        val cx = (g.leftX + g.rightX) / 2f
        drawPositionLabel(canvas, positionLabel(o, topLine), cx, g.topY, topColor)
        drawPositionLabel(canvas, positionLabel(o, botLine), cx, g.botY, botColor)
        drawPositionLabel(
            canvas,
            formatPriceForChart(o.entry, stateRef.value.daySubDollar),
            cx, g.entryY,
            GraphicsColor.argb(220, 26, 29, 35)
        )
        val contentLeft = viewPortHandler.contentLeft()
        val contentRight = viewPortHandler.contentRight()
        // punteadas sutiles de borde a borde + marca en el eje Y, como la entrada
        posDashPaint.color = topColor
        posDashPaint.alpha = 120
        canvas.drawLine(contentLeft, g.topY, contentRight, g.topY, posDashPaint)
        posDashPaint.color = botColor
        canvas.drawLine(contentLeft, g.botY, contentRight, g.botY, posDashPaint)
        posDashPaint.alpha = 255
        drawPosYTag(canvas, topLine, topColor)
        drawPosYTag(canvas, botLine, botColor)
        drawPosYTag(canvas, o.entry, GraphicsColor.argb(220, 26, 29, 35))
        val r = 18f
        // arriba/abajo al borde izq pa' no tapar los tags centrados
        val hx = g.leftX + 20f
        listOf(
            Triple(hx, g.topY, 1),
            Triple(g.rightX + 26f, g.entryY, 2),
            Triple(hx, g.botY, 3)
        ).forEach { (hx2, hy, _) ->
            yTagRect.set(hx2 - r, hy - r, hx2 + r, hy + r)
            canvas.drawRect(yTagRect, fibHandleFill)
            canvas.drawRect(yTagRect, fibHandleStroke)
        }
    }
}

internal fun PriceCombinedChart.drawPositionOverlays(canvas: Canvas, candles: List<CandleData>) {
    positionsRef.value.forEach { o ->
        drawSinglePosition(canvas, candles, o, o.id == selectedPositionRef.value)
    }
}

internal fun PriceCombinedChart.positionHitTest(x: Float, y: Float): Triple<String, Int, Boolean>? {
    if (stateRef.value.candles.isEmpty()) return null
    if (prefsRef.value.overlaysHidden) return null
    var best: Triple<String, Int, Boolean>? = null
    var bestDist = Float.MAX_VALUE
    positionsRef.value.forEach { o ->
        val g = positionGeom(o) ?: return@forEach
        // anclajes de TP/SL al borde izq: centrados tapaban sus tags
        val hx = g.leftX + 20f
        val dTop = kotlin.math.hypot(x - hx, y - g.topY)
        if (dTop <= 60f && dTop < bestDist) {
            bestDist = dTop
            best = Triple(o.id, 1, false)
        }
        val dMid = kotlin.math.hypot(x - (g.rightX + 26f), y - g.entryY)
        if (dMid <= 60f && dMid < bestDist) {
            bestDist = dMid
            best = Triple(o.id, 2, false)
        }
        val dBot = kotlin.math.hypot(x - hx, y - g.botY)
        if (dBot <= 60f && dBot < bestDist) {
            bestDist = dBot
            best = Triple(o.id, 3, false)
        }
        if (bestDist <= 60f) return@forEach
        val l = min(g.leftX, g.rightX)
        val r = max(g.leftX, g.rightX)
        val t = min(g.topY, g.botY)
        val b = max(g.topY, g.botY)
        if (x >= l && x <= r && y >= t && y <= b) {
            best = Triple(o.id, 4, false)
        }
    }
    return best
}

internal fun PriceCombinedChart.applyPositionTap(x: Float, y: Float, long: Boolean) {
    val anchor = drawAnchorFromTouch(x, y) ?: return
    onPositionTap?.invoke(anchor, long)
    invalidate()
}








