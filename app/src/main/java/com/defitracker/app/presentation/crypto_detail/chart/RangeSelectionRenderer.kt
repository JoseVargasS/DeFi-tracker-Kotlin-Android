package com.defitracker.app.presentation.crypto_detail.chart

import android.graphics.*
import android.graphics.Color as GraphicsColor
import com.github.mikephil.charting.components.YAxis
import java.util.Locale
import kotlin.math.*

import android.graphics.Canvas
import com.defitracker.app.presentation.crypto_detail.*

internal fun PriceCombinedChart.drawPriceRangeSelection(canvas: Canvas, candles: List<CandleData>) {
    val selection = rangeSelection.value ?: return
    if (candles.isEmpty()) return
    val sIdx = selection.startIdx.coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
    val eIdx = selection.endIdx?.coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
    val trans = getTransformer(YAxis.AxisDependency.LEFT)
    val contentLeft = viewPortHandler.contentLeft()
    val contentRight = viewPortHandler.contentRight()
    val contentTop = viewPortHandler.contentTop()
    val contentBottom = viewPortHandler.contentBottom()

    if (eIdx == null || selection.endPrice == null) {
        val pts = floatArrayOf(sIdx.toFloat(), selection.startPrice.toFloat())
        trans.pointValuesToPixel(pts)
        val x = pts[0].coerceIn(contentLeft, contentRight)
        val y = pts[1].coerceIn(contentTop, contentBottom)
        selectionStrokePaint.color = GraphicsColor.argb(230, 30, 203, 129)
        canvas.drawLine(x, contentTop, x, contentBottom, selectionStrokePaint)
        canvas.drawCircle(x, y, 7f, dotPaint)
        canvas.drawCircle(x, y, 7f, dotOutlinePaint)
        val label = "Toca fin"
        val labelWidth = selectionTextPaint.measureText(label) + 18f
        val labelHeight = selectionTextPaint.textSize + 12f
        val labelLeft = (x + 8f).coerceIn(contentLeft + 4f, (contentRight - labelWidth - 4f).coerceAtLeast(contentLeft + 4f))
        val labelTop = (y - labelHeight - 8f).coerceIn(contentTop + 4f, (contentBottom - labelHeight - 4f).coerceAtLeast(contentTop + 4f))
        yTagRect.set(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight)
        canvas.drawRoundRect(yTagRect, 5f, 5f, selectionLabelPaint)
        canvas.drawText(label, labelLeft + 9f, labelTop + labelHeight - 8f, selectionTextPaint)
        return
    }

    // esquinas de la diagonal, el precio es del toque (como el fibo)
    val leftIndex = min(sIdx, eIdx)
    val rightIndex = max(sIdx, eIdx)
    val firstPrice = if (sIdx <= eIdx) selection.startPrice else selection.endPrice
    val lastPrice = if (sIdx <= eIdx) selection.endPrice else selection.startPrice
    val pts = floatArrayOf(
        leftIndex.toFloat(),
        firstPrice.toFloat(),
        rightIndex.toFloat(),
        lastPrice.toFloat()
    )
    trans.pointValuesToPixel(pts)
    val leftX = pts[0].coerceIn(contentLeft, contentRight)
    val rightX = pts[2].coerceIn(contentLeft, contentRight)
    val firstY = pts[1].coerceIn(contentTop, contentBottom)
    val lastY = pts[3].coerceIn(contentTop, contentBottom)
    val visualTop = min(firstY, lastY)
    val visualBottom = max(firstY, lastY)
    val minHeight = context.resources.displayMetrics.density * 18f
    val adjustedTop = if (visualBottom - visualTop < minHeight) {
        (visualTop - minHeight / 2f).coerceAtLeast(contentTop)
    } else {
        visualTop
    }
    val adjustedBottom = if (visualBottom - visualTop < minHeight) {
        (adjustedTop + minHeight).coerceAtMost(contentBottom)
    } else {
        visualBottom
    }

    val change = lastPrice - firstPrice
    val changePct = if (firstPrice != 0.0) change / firstPrice * 100.0 else 0.0
    val isUp = changePct >= 0.0
    selectionStrokePaint.color = if (isUp) GraphicsColor.argb(230, 30, 203, 129) else GraphicsColor.argb(230, 246, 70, 93)
    selectionFillPaint.color = if (isUp) GraphicsColor.argb(42, 30, 203, 129) else GraphicsColor.argb(42, 246, 70, 93)

    yTagRect.set(leftX, adjustedTop, rightX, adjustedBottom)
    canvas.drawRect(yTagRect, selectionFillPaint)
    canvas.drawRect(yTagRect, selectionStrokePaint)
    canvas.drawLine(leftX, contentTop, leftX, contentBottom, selectionStrokePaint)
    canvas.drawLine(rightX, contentTop, rightX, contentBottom, selectionStrokePaint)

    // handles en las esquinas de la diagonal solo en modo Medir
    if (toolRef.value == DrawingTool.MEASURE) {
        val r = 22f
        canvas.drawCircle(leftX, firstY, r, fibHandleFill)
        canvas.drawCircle(leftX, firstY, r, fibHandleStroke)
        canvas.drawCircle(rightX, lastY, r, fibHandleFill)
        canvas.drawCircle(rightX, lastY, r, fibHandleStroke)
    } else {
        canvas.drawCircle(leftX, firstY, 6f, dotPaint)
        canvas.drawCircle(rightX, lastY, 6f, dotPaint)
    }

    val bars = rightIndex - leftIndex
    val label = "${if (changePct >= 0.0) "+" else ""}${String.format(Locale.US, "%.2f", changePct)}%  $bars velas"
    val labelWidth = selectionTextPaint.measureText(label) + 18f
    val labelHeight = selectionTextPaint.textSize + 12f
    val labelLeft = (rightX - labelWidth).coerceIn(contentLeft + 4f, (contentRight - labelWidth - 4f).coerceAtLeast(contentLeft + 4f))
    val labelTop = (adjustedTop - labelHeight - 6f).coerceIn(contentTop + 4f, (contentBottom - labelHeight - 4f).coerceAtLeast(contentTop + 4f))
    yTagRect.set(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight)
    canvas.drawRoundRect(yTagRect, 5f, 5f, selectionLabelPaint)
    canvas.drawText(label, labelLeft + 9f, labelTop + labelHeight - 8f, selectionTextPaint)
}

internal fun PriceCombinedChart.measureHitTest(x: Float, y: Float): Int {
    val sel = rangeSelection.value ?: return 0
    if (!sel.isComplete) return 0
    val candles = stateRef.value.candles
    if (candles.isEmpty()) return 0
    val trans = getTransformer(YAxis.AxisDependency.LEFT)
    val lIdx = min(sel.startIdx, sel.endIdx!!).coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
    val rIdx = max(sel.startIdx, sel.endIdx).coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
    val firstPrice = if (sel.startIdx <= sel.endIdx) sel.startPrice else sel.endPrice!!
    val lastPrice = if (sel.startIdx <= sel.endIdx) sel.endPrice!! else sel.startPrice
    val pts = floatArrayOf(lIdx.toFloat(), firstPrice.toFloat(), rIdx.toFloat(), lastPrice.toFloat())
    trans.pointValuesToPixel(pts)
    if (kotlin.math.hypot(x - pts[0], y - pts[1]) <= 60f) return 1
    if (kotlin.math.hypot(x - pts[2], y - pts[3]) <= 60f) return 2
    val leftX = min(pts[0], pts[2])
    val rightX = max(pts[0], pts[2])
    val top = min(pts[1], pts[3])
    val bottom = max(pts[1], pts[3])
    if (x >= leftX && x <= rightX && y >= top && y <= bottom) return 3
    return 0
}

// ─── FIBO multi-overlay ───
// handle=resize, cuerpo=mover, tap=seleccionar; candado bloquea gestos







