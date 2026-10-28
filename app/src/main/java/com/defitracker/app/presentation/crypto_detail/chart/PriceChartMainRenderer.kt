package com.defitracker.app.presentation.crypto_detail.chart

import android.graphics.*
import android.graphics.Color as GraphicsColor
import com.github.mikephil.charting.components.YAxis
import java.util.Locale
import kotlin.math.*
import com.defitracker.app.ui.theme.ChartColors

import android.graphics.Canvas
import com.defitracker.app.presentation.crypto_detail.*
import kotlin.math.max
import kotlin.math.roundToInt

internal fun PriceCombinedChart.drawPinnedYLabels(canvas: Canvas, candles: List<CandleData>) {
    if (candles.isEmpty()) return
    val trans = getTransformer(YAxis.AxisDependency.LEFT)
    val contentRight = viewPortHandler.contentRight()
    val contentTop = viewPortHandler.contentTop()
    val contentBottom = viewPortHandler.contentBottom()
    val refClose = candles.lastOrNull()?.close ?: 0.0
    val n = 6
    for (i in 0 until n) {
        val py = contentTop + (contentBottom - contentTop) * i / (n - 1)
        val v = trans.getValuesByTouchPoint(0f, py).y
        if (!v.isFinite()) continue
        canvas.drawText(
            formatAxisPrice(v, refClose, stateRef.value.daySubDollar),
            contentRight - 6f,
            py + pinnedYLabelPaint.textSize * 0.35f,
            pinnedYLabelPaint
        )
    }
}

// linea punteada + tag con precio actual y countdown al cierre
internal fun PriceCombinedChart.drawLastPriceTag(canvas: Canvas, candles: List<CandleData>) {
    val current = stateRef.value
    val last = candles.lastOrNull() ?: return
    val trans = getTransformer(YAxis.AxisDependency.LEFT)
    val contentLeft = viewPortHandler.contentLeft()
    val contentRight = viewPortHandler.contentRight()
    val contentTop = viewPortHandler.contentTop()
    val contentBottom = viewPortHandler.contentBottom()

    val pts = floatArrayOf((candles.size - 1).toFloat(), last.close.toFloat())
    trans.pointValuesToPixel(pts)
    val py = pts[1].coerceIn(contentTop, contentBottom)

    val tagColor = if (last.close >= last.open) {
        GraphicsColor.parseColor(ChartColors.CHART_GREEN_HEX)
    } else {
        GraphicsColor.parseColor(ChartColors.CHART_RED_HEX)
    }
    lastPriceLinePaint.color = tagColor
    lastPriceTagPaint.color = tagColor
    canvas.drawLine(contentLeft, py, contentRight, py, lastPriceLinePaint)

    val priceText = formatPriceForChart(last.close, stateRef.value.daySubDollar)
    val countdown = formatCandleCountdown(current.selectedInterval, last.time)
    val textSize = lastPriceTextPaint.textSize
    val padX = 12f
    val padY = 6f
    val tagW = max(
        lastPriceTextPaint.measureText(priceText),
        lastPriceTextPaint.measureText(countdown)
    ) + padX * 2f
    val tagH = textSize * 2f + padY * 2f + 6f
    var tagRight = contentRight - 2f + tagW
    val chartW = width.toFloat()
    if (tagRight > chartW - 2f) {
        tagRight = chartW - 2f
    }
    val tagLeft = tagRight - tagW
    val tagTop = (py - tagH / 2f).coerceIn(
        contentTop,
        max(contentTop, contentBottom - tagH)
    )
    yTagRect.set(tagLeft, tagTop, tagRight, tagTop + tagH)
    lastPriceCountdown = countdown
    canvas.drawRoundRect(yTagRect, 6f, 6f, lastPriceTagPaint)
    canvas.drawText(priceText, yTagRect.centerX(), tagTop + padY + textSize, lastPriceTextPaint)
    canvas.drawText(countdown, yTagRect.centerX(), tagTop + padY + textSize * 2f + 4f, lastPriceTextPaint)
}

// tags de MAs de otro TF: codo punteado 45° + etiqueta al borde (1h.SMA20)
internal fun PriceCombinedChart.drawMaTfTags(canvas: Canvas, candles: List<CandleData>) {
    if (candles.isEmpty()) return
    val interval = stateRef.value.selectedInterval
    val mas = prefsRef.value.mas.filter { it.visible && it.timeframe != "chart" && it.timeframe != interval }
    if (mas.isEmpty()) return
    val lines = stateRef.value.maLines
    val trans = getTransformer(YAxis.AxisDependency.LEFT)
    val contentLeft = viewPortHandler.contentLeft()
    val contentRight = viewPortHandler.contentRight()
    val contentTop = viewPortHandler.contentTop()
    val contentBottom = viewPortHandler.contentBottom()
    data class Tag(val py: Float, val px: Float, val text: String, val color: Int)
    val tags = ArrayList<Tag>()
    mas.forEach { ma ->
        val line = lines[ma.id] ?: return@forEach
        if (line.isEmpty()) return@forEach
        val last = line.last()
        val pts = floatArrayOf(last.first.toFloat(), last.second.toFloat())
        trans.pointValuesToPixel(pts)
        val px = pts[0].coerceIn(contentLeft, contentRight)
        val py = pts[1].coerceIn(contentTop, contentBottom)
        val color = try {
            GraphicsColor.parseColor(ma.colorHex)
        } catch (_: Exception) {
            GraphicsColor.WHITE
        }
        val kind = if (ma.type == MaType.EMA) "EMA" else "SMA"
        tags.add(Tag(py, px, "${ma.timeframe}.$kind${ma.period}", color))
    }
    if (tags.isEmpty()) return
    tags.sortBy { it.py }
    val th = maTagTextPaint.textSize + 10f
    val minGap = th + 6f
    // codo 45° + horizontal a la altura del chip, apilados sin encimarse
    var cursor = Float.NEGATIVE_INFINITY
    tags.forEach { t ->
        // prefiere arriba del punto, abajo si no hay aire
        var cy = t.py - (th + minGap)
        if (cy < contentTop + th / 2f) cy = t.py + (th + minGap)
        cy = cy.coerceIn(contentTop + th / 2f, (contentBottom - th / 2f).coerceAtLeast(contentTop + th / 2f))
        if (cy < cursor) cy = cursor
        cy = min(cy, contentBottom - th / 2f)
        cursor = cy + minGap
        val tw = maTagTextPaint.measureText(t.text)
        val tagRight = contentRight - 4f
        val tagLeft = (tagRight - tw - 16f).coerceAtLeast(contentLeft)
        // 45°: avance horizontal = subida; luego recto hasta el tag
        var kx = (t.px + abs(cy - t.py)).coerceIn(contentLeft, contentRight)
        if (kx > tagLeft - 4f) kx = (tagLeft - 4f).coerceAtLeast(contentLeft)
        maTagDashPaint.color = t.color
        canvas.drawLine(t.px, t.py, kx, cy, maTagDashPaint)
        canvas.drawLine(kx, cy, tagLeft, cy, maTagDashPaint)
        val ty = cy - th / 2f
        yTagRect.set(tagLeft, ty, tagRight, ty + th)
        canvas.drawRoundRect(yTagRect, 4f, 4f, drawChipPaint)
        maTagTextPaint.color = t.color
        canvas.drawText(t.text, tagLeft + 8f, ty + th - 7f, maTagTextPaint)
    }
    maTagTextPaint.color = GraphicsColor.WHITE
}

// volumen taker apilado (buy abajo, sell arriba) al fondo del chart
internal fun PriceCombinedChart.drawPriceChart(canvas: Canvas) {
    val entries = stateRef.value.candles
    if (entries.isEmpty()) {
        drawBaseChart(canvas)
        return
    }

    val visibleStart = lowestVisibleX.toInt().coerceIn(0, entries.size - 1)
    val visibleEnd = highestVisibleX.toInt().coerceIn(0, entries.size - 1)
    if (visibleStart >= visibleEnd) {
        drawBaseChart(canvas)
        return
    }
    // rect degenerado (ancho/alto cero en layout o cambio de TF):
    // ningun coerceIn de etiquetas abajo puede reventar
    if (viewPortHandler.contentRight() <= viewPortHandler.contentLeft() ||
        viewPortHandler.contentBottom() <= viewPortHandler.contentTop()
    ) {
        drawBaseChart(canvas)
        return
    }
    if (prefsRef.value.profileVisible) {
        drawFixedRangeVolumeProfile(canvas, entries, visibleStart, visibleEnd)
    }
    drawVolumeOverlay(canvas, entries, visibleStart, visibleEnd)
    if (!prefsRef.value.overlaysHidden) {
        drawSmcBackground(canvas, entries, visibleStart, visibleEnd)
    }
    drawBaseChart(canvas)

    var maxIndex = visibleStart
    var minIndex = visibleStart
    for (index in visibleStart..visibleEnd) {
        val entry = entries[index]
        if (entry.high > entries[maxIndex].high) {
            maxIndex = index
        }
        if (entry.low < entries[minIndex].low) {
            minIndex = index
        }
    }
    val maxEntry = entries[maxIndex]
    val minEntry = entries[minIndex]

    val trans = getTransformer(YAxis.AxisDependency.LEFT)
    val contentLeft = viewPortHandler.contentLeft()
    val contentRight = viewPortHandler.contentRight()
    val contentTop = viewPortHandler.contentTop()
    val contentBottom = viewPortHandler.contentBottom()

    // --- Draw Max/Min Refined ---
    // Draw Max Label
    run {
        val pts = floatArrayOf(maxIndex.toFloat(), maxEntry.high.toFloat())
        trans.pointValuesToPixel(pts)
        val px = pts[0]
        val py = pts[1]
        val label = formatPriceForChart(maxEntry.high, stateRef.value.daySubDollar)
        val labelWidth = labelPaint.measureText(label)
        val lineEndX = if (px + labelWidth + 16f < contentRight) px + 20f else px - 20f
        canvas.drawLine(px, py, lineEndX, py, linePaint)
        val textX = if (px + labelWidth + 16f < contentRight) lineEndX + 4f else lineEndX - labelWidth - 4f
        canvas.drawText(label, textX, py + 10f, labelPaint)
    }

    // Draw Min Label
    run {
        val pts = floatArrayOf(minIndex.toFloat(), minEntry.low.toFloat())
        trans.pointValuesToPixel(pts)
        val px = pts[0]
        val py = pts[1]
        val label = formatPriceForChart(minEntry.low, stateRef.value.daySubDollar)
        val labelWidth = labelPaint.measureText(label)
        val lineEndX = if (px + labelWidth + 16f < contentRight) px + 20f else px - 20f
        canvas.drawLine(px, py, lineEndX, py, linePaint)
        val textX = if (px + labelWidth + 16f < contentRight) lineEndX + 4f else lineEndX - labelWidth - 4f
        canvas.drawText(label, textX, py + 10f, labelPaint)
    }

    // la medicion tambien se recorta: sus etiquetas no deben invadir los ejes
    canvas.save()
    canvas.clipRect(contentLeft, contentTop, contentRight, contentBottom)
    try {
        drawPriceRangeSelection(canvas, entries)
    } finally {
        canvas.restore()
    }
    if (!prefsRef.value.overlaysHidden) {
        canvas.save()
        canvas.clipRect(contentLeft, contentTop, contentRight, contentBottom)
        try {
            drawFibOverlays(canvas, entries)
            drawDrawOverlays(canvas, entries)
            drawPositionOverlays(canvas, entries)
            drawSmcForeground(canvas, entries, visibleStart, visibleEnd)
            drawMaTfTags(canvas, entries)
        } finally {
            canvas.restore()
        }
    }
    drawPinnedYLabels(canvas, entries)
    drawLastPriceTag(canvas, entries)

    // --- Draw Crosshair Lines Manually (Exact Y, Snapped X) ---
    val h = highlighted?.getOrNull(0)
    if (h == null) {
        crossX.floatValue = -1f
        return
    }

    val xPts = floatArrayOf(h.x, h.y)
    trans.pointValuesToPixel(xPts)
    val snappedPy = xPts[1]

    // X snapeada a la vela, Y exacto del toque
    val px = xPts[0].coerceIn(contentLeft, contentRight)
    val py = if (lastTouchYPx >= 0) lastTouchYPx.coerceIn(contentTop, contentBottom) else snappedPy

    // la vertical la dibuja el overlay de Compose sobre todo el stack (OKX)
    crossX.floatValue = px
    // Horizontal Line
    canvas.drawLine(contentLeft, py, contentRight, py, linePaint)
    // Intersection Dot
    canvas.drawCircle(px, py, 6f, dotPaint)
    canvas.drawCircle(px, py, 6f, dotOutlinePaint)

    // Y-Axis Tag (Price + %) - Use Exact Y
    val priceAtTouch = trans.getValuesByTouchPoint(0f, py).y
    val currentPrice = stateRef.value.detail?.price?.toDoubleOrNull() ?: entries.lastOrNull()?.close ?: 0.0
    val pctFromCurrent = if (currentPrice != 0.0) (priceAtTouch - currentPrice) / currentPrice * 100 else 0.0

    // mismos decimales que el tag de precio actual
    val refClose = entries.lastOrNull()?.close ?: currentPrice
    val priceText = formatAxisPrice(priceAtTouch, refClose, stateRef.value.daySubDollar)
    val pctText = String.format(Locale.US, "%+.2f%%", pctFromCurrent)

    val twPrice = tagTextPaint.measureText(priceText)
    val twPct = tagTextPaint.measureText(pctText)
    val maxWidth = maxOf(twPrice, twPct)
    val thY = tagTextPaint.textSize
    val lineHeight = thY + 8f

    val tagRight = contentRight + maxWidth + 20f
    val chartWidth = width.toFloat()
    val offsetRight = if (tagRight > chartWidth) tagRight - chartWidth + 4f else 0f

    val tagGap = 6f
    yTagRect.set(
        contentRight - offsetRight,
        py + tagGap,
        contentRight + maxWidth + 20f - offsetRight,
        py + tagGap + (lineHeight * 2) + 4f
    )
    canvas.drawRoundRect(yTagRect, 4f, 4f, tagBackgroundPaint)
    canvas.drawText(priceText, yTagRect.centerX(), py + tagGap + thY + 2f, tagTextPaint)
    canvas.drawText(pctText, yTagRect.centerX(), py + tagGap + (thY * 2) + 10f, tagTextPaint)

    // X-Axis Tag (Date): en zona virtual hora proyectada, no la ultima vela
    val freeIdx = trans.getValuesByTouchPoint(px, py).x.roundToInt()
    val dateText = if (freeIdx >= entries.size && entries.isNotEmpty()) {
        crossTimeSdf.format(java.util.Date(fibIndexToTime(entries, freeIdx, stateRef.value.selectedInterval)))
    } else {
        xAxis.valueFormatter.getFormattedValue(h.x).replace("|", " ")
    }
    val twX = tagTextPaint.measureText(dateText)
    xTagRect.set(px - twX/2 - 10f, contentBottom, px + twX/2 + 10f, contentBottom + thY + 16f)
    canvas.drawRoundRect(xTagRect, 4f, 4f, tagBackgroundPaint)
    canvas.drawText(dateText, xTagRect.centerX(), xTagRect.centerY() + thY/3, tagTextPaint)
}







