package com.defitracker.app.presentation.crypto_detail.chart

import android.graphics.Matrix
import com.github.mikephil.charting.components.YAxis
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt


internal fun PriceCombinedChart.isOnYAxis(x: Float): Boolean =
    x >= width - 56f * density

internal fun PriceCombinedChart.matrixPivotX(x: Float): Float = x - viewPortHandler.offsetLeft()

internal fun PriceCombinedChart.matrixPivotY(y: Float): Float =
    -(height - y - viewPortHandler.offsetBottom())

internal fun PriceCombinedChart.refreshTouchMatrix(matrix: Matrix) {
    // MPAndroidChart's refresh() clamps translation and scale
    // to its configured viewport limits. Touch gestures on
    // this chart intentionally have no such bounds.
    viewPortHandler.matrixTouch.set(matrix)
    invalidate()
    syncSubCharts(this, stochChartRef.value, rsiChartRef.value, macdChartRef.value, takerChartRef.value)
}

// el resize (divisor de altos) recalcula ejes con toda la
// data; si el Y esta congelado se guarda y restaura para no aplanar
internal fun PriceCombinedChart.handleSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    val keepY = oldw > 0 && oldh > 0 && !isAutoScaleMinMaxEnabled && data != null
    val savedMatrix = if (keepY) Matrix(viewPortHandler.matrixTouch) else null
    val lMin = if (keepY) axisLeft.axisMinimum else 0f
    val lMax = if (keepY) axisLeft.axisMaximum else 0f
    val rMin = if (keepY) axisRight.axisMinimum else 0f
    val rMax = if (keepY) axisRight.axisMaximum else 0f
    callSuperOnSizeChanged(w, h, oldw, oldh)
    if (keepY && savedMatrix != null) {
        axisLeft.axisMinimum = lMin
        axisLeft.axisMaximum = lMax
        axisRight.axisMinimum = rMin
        axisRight.axisMaximum = rMax
        viewPortHandler.matrixTouch.set(savedMatrix)
    }
}

internal fun PriceCombinedChart.applyPan(dx: Float, dy: Float) {
    val matrix = Matrix(viewPortHandler.matrixTouch)
    matrix.postTranslate(dx, dy)
    // al mover libre se congela el Y, al entrar se reajusta solo
    isAutoScaleMinMaxEnabled = false
    refreshTouchMatrix(matrix)
}

internal fun PriceCombinedChart.applyYZoom(dy: Float) {
    // Moving down on the Y axis reduces the visible Y range;
    // moving up expands it.
    val scaleY = exp((-dy / (180f * density)).toDouble()).toFloat()
    val matrix = Matrix(viewPortHandler.matrixTouch)
    matrix.postScale(1f, scaleY, zoomPivotX, zoomPivotY)
    refreshTouchMatrix(matrix)
}

internal fun PriceCombinedChart.applyHorizontalPinchZoom(scaleX: Float, pivotX: Float, pivotY: Float) {
    if (!scaleX.isFinite() || scaleX <= 0f) return
    val matrix = Matrix(viewPortHandler.matrixTouch)
    matrix.postScale(scaleX, 1f, pivotX, pivotY)
    refreshTouchMatrix(matrix)
}

internal fun PriceCombinedChart.touchToCandleIndex(x: Float, y: Float, size: Int, maxExtra: Int = 0): Int? {
    val contentLeft = viewPortHandler.contentLeft()
    val contentRight = viewPortHandler.contentRight()
    val contentTop = viewPortHandler.contentTop()
    val contentBottom = viewPortHandler.contentBottom()
    if (x < contentLeft || x > contentRight || y < contentTop || y > contentBottom) return null
    val values = getTransformer(YAxis.AxisDependency.LEFT).getValuesByTouchPoint(x, y)
    // Medir usa velas virtuales a la derecha como el fibo, el resto queda igual
    return values.x.roundToInt().coerceIn(0, size - 1 + maxExtra)
}

internal fun PriceCombinedChart.measureAnchor(x: Float, y: Float): Pair<Int, Double>? {
    val candles = stateRef.value.candles
    if (candles.isEmpty()) return null
    val index = touchToCandleIndex(x, y, candles.size, FIB_VIRTUAL_EXTEND) ?: return null
    var price = getTransformer(YAxis.AxisDependency.LEFT).getValuesByTouchPoint(x, y).y.toDouble()
    // iman pega Medir al high/low/close mas cercano, en zona virtual no hay vela
    if (magnetRef.value && index < candles.size) {
        val trans = getTransformer(YAxis.AxisDependency.LEFT)
        val c = candles[index]
        var best = price
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
        price = best
    }
    return index to price
}

internal fun PriceCombinedChart.applyRangeTap(x: Float, y: Float): Boolean {
    val (index, price) = measureAnchor(x, y) ?: return false
    val current = rangeSelection.value
    // zona completa: tap fuera del rectangulo la borra, dentro no hace nada
    if (current != null && current.isComplete) {
        if (measureHitTest(x, y) == 0) {
            setRangeSelection(null)
            invalidate()
        }
        return true
    }
    setRangeSelection(
        if (current == null) {
            MeasureZone(startIdx = index, startPrice = price)
        } else {
            current.copy(endIdx = index, endPrice = price)
        }
    )
    highlightValue(null)
    lastTouchYPx = -1f
    lastTouchXPx = -1f
    syncHighlights(this, stochChartRef.value, rsiChartRef.value, macdChartRef.value, takerChartRef.value)
    invalidate()
    return true
}

