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
import kotlin.math.roundToInt

internal fun PriceCombinedChart.drawAnchorFromTouch(x: Float, y: Float): FibAnchor? {
    val base = anchorFromTouch(x, y) ?: return null
    if (!magnetRef.value) return base
    val candles = stateRef.value.candles
    if (candles.isEmpty()) return base
    val trans = getTransformer(YAxis.AxisDependency.LEFT)
    val rawX = trans.getValuesByTouchPoint(x, y).x.roundToInt()
    // mas alla de la ultima vela no hay high/low, se deja tal cual (fibo virtual)
    if (rawX < 0 || rawX >= candles.size) return base
    val c = candles[rawX]
    // pointValuesToPixel necesita pares x,y; evalua por separado
    var best = base.price
    var bestDist = 28f
    listOf(c.high, c.low, c.close).forEach { p ->
        val pts = floatArrayOf(0f, p.toFloat())
        trans.pointValuesToPixel(pts)
        val d = abs(pts[1] - y)
        if (d < bestDist) {
            bestDist = d
            best = p
        }
    }
    return base.copy(price = best)
}

internal fun PriceCombinedChart.drawPixels(o: DrawOverlay): Pair<android.graphics.PointF?, android.graphics.PointF?> {
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

// 1 = handle inicio, 2 = handle fin, 3 = cuerpo
internal fun PriceCombinedChart.drawHitTest(x: Float, y: Float): Triple<String, Int, Boolean>? {
    if (stateRef.value.candles.isEmpty()) return null
    if (prefsRef.value.overlaysHidden) return null
    val contentLeft = viewPortHandler.contentLeft()
    val contentRight = viewPortHandler.contentRight()
    var best: Triple<String, Int, Boolean>? = null
    var bestDist = Float.MAX_VALUE
    drawsRef.value.forEach { o ->
        if (o.hidden) return@forEach
        val (s, e) = drawPixels(o)
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
        // rectangulo: 4 puntos medios, 4=izq 5=der 6=arriba 7=abajo
        if (o.kind == DrawKind.RECT) {
            val l = min(s.x, e.x)
            val rr = max(s.x, e.x)
            val t = min(s.y, e.y)
            val b = max(s.y, e.y)
            listOf(
                4 to (l to (t + b) / 2f),
                5 to (rr to (t + b) / 2f),
                6 to ((l + rr) / 2f to t),
                7 to ((l + rr) / 2f to b)
            ).forEach { (zone, p) ->
                val d = kotlin.math.hypot(x - p.first, y - p.second)
                if (d <= 45f && d < bestDist) {
                    bestDist = d
                    best = Triple(o.id, zone, o.locked)
                }
            }
        }
        if (bestDist <= 60f) return@forEach
        var bodyDist = Float.MAX_VALUE
        when (o.kind) {
            DrawKind.SEGMENT, DrawKind.ARROW ->
                bodyDist = distToSegment(x, y, s.x, s.y, e.x, e.y)
            DrawKind.LINE -> {
                val dx = e.x - s.x
                val dy = e.y - s.y
                val len = kotlin.math.hypot(dx, dy)
                if (len > 1f && x >= contentLeft - 20f && x <= contentRight + 20f) {
                    bodyDist = abs(dy * x - dx * y + e.x * s.y - e.y * s.x) / len
                }
            }
            DrawKind.RAY -> {
                val dx = e.x - s.x
                val dy = e.y - s.y
                val len2 = dx * dx + dy * dy
                if (len2 > 1f) {
                    val t = ((x - s.x) * dx + (y - s.y) * dy) / len2
                    if (t >= 0f) {
                        bodyDist = kotlin.math.hypot(x - (s.x + t * dx), y - (s.y + t * dy))
                    }
                }
            }
            DrawKind.H_SEGMENT, DrawKind.H_LINE, DrawKind.H_RAY, DrawKind.PRICE_LINE -> {
                val inX = when (o.kind) {
                    DrawKind.H_LINE, DrawKind.PRICE_LINE -> x >= contentLeft - 20f && x <= contentRight + 20f
                    DrawKind.H_RAY -> if (e.x >= s.x) x >= s.x - 20f else x <= s.x + 20f
                    else -> x >= min(s.x, e.x) - 20f && x <= max(s.x, e.x) + 20f
                }
                if (inX) bodyDist = abs(y - s.y)
            }
            DrawKind.RECT -> {
                val l = min(s.x, e.x)
                val r = max(s.x, e.x)
                val t = min(s.y, e.y)
                val b = max(s.y, e.y)
                bodyDist = minOf(
                    distToSegment(x, y, l, t, r, t),
                    distToSegment(x, y, r, t, r, b),
                    distToSegment(x, y, r, b, l, b),
                    distToSegment(x, y, l, b, l, t)
                )
            }
            DrawKind.CIRCLE -> {
                val cx = (s.x + e.x) / 2f
                val cy = (s.y + e.y) / 2f
                val rx = abs(e.x - s.x) / 2f
                val ry = abs(e.y - s.y) / 2f
                if (rx > 4f && ry > 4f) {
                    val nx = (x - cx) / rx
                    val ny = (y - cy) / ry
                    val d = kotlin.math.hypot(nx, ny)
                    bodyDist = abs(d - 1f) * min(rx, ry)
                }
            }
            DrawKind.TRIANGLE -> {
                val mx = (s.x + e.x) / 2f
                bodyDist = minOf(
                    distToSegment(x, y, mx, s.y, s.x, e.y),
                    distToSegment(x, y, s.x, e.y, e.x, e.y),
                    distToSegment(x, y, e.x, e.y, mx, s.y)
                )
            }
        }
        if (bodyDist <= 28f && bodyDist < bestDist) {
            bestDist = bodyDist
            best = Triple(o.id, 3, o.locked)
        }
    }
    return best
}

internal fun PriceCombinedChart.applyDrawTap(x: Float, y: Float) {
    val anchor = drawAnchorFromTouch(x, y) ?: return
    onDrawTap?.invoke(anchor)
    invalidate()
}

internal fun PriceCombinedChart.drawSingleDraw(canvas: Canvas, candles: List<CandleData>, o: DrawOverlay, selected: Boolean) {
    val contentLeft = viewPortHandler.contentLeft()
    val contentRight = viewPortHandler.contentRight()
    val contentTop = viewPortHandler.contentTop()
    val contentBottom = viewPortHandler.contentBottom()
    val (s, e) = drawPixels(o)
    if (s == null || e == null) return
    val lineColor = try {
        GraphicsColor.parseColor(o.colorHex)
    } catch (_: Exception) {
        GraphicsColor.WHITE
    }
    drawLinePaint.color = lineColor
    drawLinePaint.strokeWidth = (o.width * context.resources.displayMetrics.density).coerceAtLeast(1f)
    drawLinePaint.alpha = if (selected || selectedDrawRef.value == null) 255 else 140
    drawDashPaint.color = lineColor
    drawDashPaint.strokeWidth = (o.width * context.resources.displayMetrics.density).coerceAtLeast(1f)
    drawLabelPaint.color = lineColor

    fun hLine(py: Float, fromX: Float, toX: Float, dashed: Boolean = false) {
        val p = if (dashed) drawDashPaint else drawLinePaint
        canvas.drawLine(fromX.coerceIn(contentLeft, contentRight), py, toX.coerceIn(contentLeft, contentRight), py, p)
    }
    fun seg(ax: Float, ay: Float, bx: Float, by: Float) {
        canvas.drawLine(ax, ay, bx, by, drawLinePaint)
    }
    // infinita por dos puntos recortada a los bordes
    fun infinite(sx: Float, sy: Float, ex: Float, ey: Float) {
        val dx = ex - sx
        if (abs(dx) < 1f) {
            canvas.drawLine(sx.coerceIn(contentLeft, contentRight), contentTop, sx.coerceIn(contentLeft, contentRight), contentBottom, drawLinePaint)
            return
        }
        val m = (ey - sy) / dx
        seg(contentLeft, (sy + m * (contentLeft - sx)).coerceIn(contentTop, contentBottom), contentRight, (sy + m * (contentRight - sx)).coerceIn(contentTop, contentBottom))
    }
    // semirrecta desde s pasando por e hasta el borde
    fun ray(sx: Float, sy: Float, ex: Float, ey: Float) {
        val edgeX = if (ex >= sx) contentRight else contentLeft
        val dx = ex - sx
        if (abs(dx) < 1f) {
            val edgeY = if (ey >= sy) contentBottom else contentTop
            seg(sx, sy, sx, edgeY)
            return
        }
        val m = (ey - sy) / dx
        seg(sx, sy, edgeX, (sy + m * (edgeX - sx)).coerceIn(contentTop, contentBottom))
    }
    // punta de flecha en e
    fun arrowHead(ex: Float, ey: Float, fromX: Float, fromY: Float) {
        val ang = kotlin.math.atan2((ey - fromY).toDouble(), (ex - fromX).toDouble())
        val len = 26f
        listOf(ang + 2.6, ang - 2.6).forEach { a ->
            seg(ex, ey, (ex + len * kotlin.math.cos(a)).toFloat(), (ey + len * kotlin.math.sin(a)).toFloat())
        }
    }

    when (o.kind) {
        DrawKind.SEGMENT -> seg(s.x, s.y, e.x, e.y)
        DrawKind.LINE -> infinite(s.x, s.y, e.x, e.y)
        DrawKind.RAY -> ray(s.x, s.y, e.x, e.y)
        DrawKind.ARROW -> {
            seg(s.x, s.y, e.x, e.y)
            arrowHead(e.x, e.y, s.x, s.y)
        }
        DrawKind.H_SEGMENT -> hLine(s.y, s.x, e.x)
        DrawKind.H_LINE -> hLine(s.y, contentLeft, contentRight)
        DrawKind.H_RAY -> {
            if (e.x >= s.x) hLine(s.y, s.x, contentRight) else hLine(s.y, contentLeft, s.x)
        }
        DrawKind.RECT -> {
            yTagRect.set(min(s.x, e.x), min(s.y, e.y), max(s.x, e.x), max(s.y, e.y))
            if (o.fill) {
                drawFillPaint.color = lineColor
                drawFillPaint.alpha = (o.fillAlpha.coerceIn(0f, 1f) * 255).toInt()
                canvas.drawRect(yTagRect, drawFillPaint)
            }
            canvas.drawRect(yTagRect, drawLinePaint)
            // precios de los bordes pegados al eje Y, como el tag de la cruz
            val rectTop = yTagRect.top
            val rectBottom = yTagRect.bottom
            val rectLeft = yTagRect.left
            val rectRight = yTagRect.right
            val chipH = drawLabelPaint.textSize + 8f
            val refClose = candles.lastOrNull()?.close ?: o.start.price
            fun chipTop(centerY: Float) = (centerY - chipH / 2f)
                .coerceIn(contentTop, (contentBottom - chipH).coerceAtLeast(contentTop))
            fun priceTag(price: Double, top: Float) {
                val txt = formatAxisPrice(price, refClose, stateRef.value.daySubDollar)
                val w = drawLabelPaint.measureText(txt) + 12f
                val leftRaw = contentRight + 2f
                val offRight = (leftRaw + w - (width.toFloat() - 2f)).coerceAtLeast(0f)
                val left = leftRaw - offRight
                yTagRect.set(left, top, left + w, top + chipH)
                canvas.drawRoundRect(yTagRect, 3f, 3f, drawChipPaint)
                canvas.drawText(txt, left + 6f, top + chipH - 6f, drawLabelPaint)
            }
            val hi = maxOf(o.start.price, o.end.price)
            val lo = minOf(o.start.price, o.end.price)
            // rectangulo bajo: separar los tags pa' que no se encimen
            val topHi = if (chipTop(rectBottom) - chipTop(rectTop) < chipH) {
                (chipTop(rectBottom) - chipH).coerceAtLeast(contentTop)
            } else {
                chipTop(rectTop)
            }
            val topLo = if (chipTop(rectBottom) - topHi < chipH) {
                (topHi + chipH).coerceAtMost((contentBottom - chipH).coerceAtLeast(contentTop))
            } else {
                chipTop(rectBottom)
            }
            priceTag(hi, topHi)
            priceTag(lo, topLo)
            // proyeccion punteada a los ejes, solo al construir o seleccionar
            if (selected) {
                drawDashPaint.color = lineColor
                canvas.drawLine(contentLeft, rectTop, contentRight, rectTop, drawDashPaint)
                canvas.drawLine(contentLeft, rectBottom, contentRight, rectBottom, drawDashPaint)
                canvas.drawLine(rectLeft, contentTop, rectLeft, contentBottom, drawDashPaint)
                canvas.drawLine(rectRight, contentTop, rectRight, contentBottom, drawDashPaint)
            }
        }
        DrawKind.CIRCLE -> {
            yTagRect.set(min(s.x, e.x), min(s.y, e.y), max(s.x, e.x), max(s.y, e.y))
            if (o.fill) {
                drawFillPaint.color = lineColor
                drawFillPaint.alpha = (o.fillAlpha.coerceIn(0f, 1f) * 255).toInt()
                canvas.drawOval(yTagRect, drawFillPaint)
            }
            canvas.drawOval(yTagRect, drawLinePaint)
        }
        DrawKind.TRIANGLE -> {
            val mx = (s.x + e.x) / 2f
            drawTriPath.reset()
            drawTriPath.moveTo(mx, s.y)
            drawTriPath.lineTo(s.x, e.y)
            drawTriPath.lineTo(e.x, e.y)
            drawTriPath.close()
            if (o.fill) {
                drawFillPaint.color = lineColor
                drawFillPaint.alpha = (o.fillAlpha.coerceIn(0f, 1f) * 255).toInt()
                canvas.drawPath(drawTriPath, drawFillPaint)
            }
            canvas.drawPath(drawTriPath, drawLinePaint)
        }
        DrawKind.PRICE_LINE -> {
            hLine(s.y, contentLeft, contentRight, dashed = true)
            val label = formatAxisPrice(o.start.price, candles.lastOrNull()?.close ?: o.start.price, stateRef.value.daySubDollar)
            val w = drawLabelPaint.measureText(label) + 16f
            val h = drawLabelPaint.textSize + 10f
            val rx = (contentRight - w).coerceAtLeast(contentLeft)
            val ly = (s.y - h / 2f).coerceIn(contentTop, (contentBottom - h).coerceAtLeast(contentTop))
            yTagRect.set(rx, ly, rx + w, ly + h)
            canvas.drawRoundRect(yTagRect, 4f, 4f, drawChipPaint)
            canvas.drawText(label, rx + 8f, ly + h - 7f, drawLabelPaint)
        }
    }
    if (selected) {
        val r = 20f
        drawHandleStroke.color = if (o.locked) GraphicsColor.GRAY
        else GraphicsColor.parseColor(ChartColors.GOLD_HEX)
        if (o.kind != DrawKind.H_LINE && o.kind != DrawKind.PRICE_LINE) {
            canvas.drawCircle(s.x, s.y, r, drawHandleFill)
            canvas.drawCircle(s.x, s.y, r, drawHandleStroke)
        }
        canvas.drawCircle(e.x, e.y, r, drawHandleFill)
        canvas.drawCircle(e.x, e.y, r, drawHandleStroke)
        // rectangulo: puntos al medio de cada lado, estiran un solo eje
        if (o.kind == DrawKind.RECT) {
            val l = min(s.x, e.x)
            val rr = max(s.x, e.x)
            val t = min(s.y, e.y)
            val b = max(s.y, e.y)
            val hr = 9f
            listOf(
                l to (t + b) / 2f,
                rr to (t + b) / 2f,
                (l + rr) / 2f to t,
                (l + rr) / 2f to b
            ).forEach { (hx, hy) ->
                yTagRect.set(hx - hr, hy - hr, hx + hr, hy + hr)
                canvas.drawRect(yTagRect, drawHandleFill)
                canvas.drawRect(yTagRect, drawHandleStroke)
            }
        }
    }
    drawLinePaint.alpha = 255
}

internal fun PriceCombinedChart.drawDrawOverlays(canvas: Canvas, candles: List<CandleData>) {
    drawsRef.value.forEach { o ->
        if (!o.hidden) drawSingleDraw(canvas, candles, o, o.id == selectedDrawRef.value)
    }
    drawPendingHint(canvas, candles)
}

internal fun PriceCombinedChart.drawPendingHint(canvas: Canvas, candles: List<CandleData>) {
    val pending = pendingDrawStartRef.value ?: return
    if (candles.isEmpty()) return
    val trans = getTransformer(YAxis.AxisDependency.LEFT)
    val pIdx = fibTimeToIndexVirtual(candles, pending.time, stateRef.value.selectedInterval)
    val p = floatArrayOf(pIdx.toFloat(), pending.price.toFloat())
    trans.pointValuesToPixel(p)
    canvas.drawCircle(p[0], p[1], 9f, dotPaint)
    canvas.drawCircle(p[0], p[1], 9f, dotOutlinePaint)
}

// ─── POSICIONES LONG/SHORT estilo TV ───
// 1 arriba (precio), 2 medio (ancho), 3 abajo (precio), 4 cuerpo (mover)







