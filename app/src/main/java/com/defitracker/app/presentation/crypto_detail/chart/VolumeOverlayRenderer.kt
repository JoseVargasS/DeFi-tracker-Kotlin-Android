package com.defitracker.app.presentation.crypto_detail.chart

import android.graphics.*
import com.github.mikephil.charting.components.YAxis
import java.util.Locale
import kotlin.math.*

import android.graphics.Canvas
import com.defitracker.app.presentation.crypto_detail.*

internal fun PriceCombinedChart.drawVolumeOverlay(canvas: Canvas, candles: List<CandleData>, visibleStart: Int, visibleEnd: Int) {
    if (!prefsRef.value.volumeVisible) return
    if (visibleStart >= visibleEnd) return
    var maxVol = 0.0
    var hasTaker = false
    for (i in visibleStart..visibleEnd) {
        maxVol = max(maxVol, candles[i].volume)
        if (candles[i].takerBuyVol > 0.0) hasTaker = true
    }
    if (maxVol <= 0.0) return

    val contentTop = viewPortHandler.contentTop()
    val contentBottom = viewPortHandler.contentBottom()
    val areaH = (contentBottom - contentTop) * 0.18f
    val base = contentBottom
    val trans = getTransformer(YAxis.AxisDependency.LEFT)
    val pts = FloatArray(2)
    pts[0] = visibleStart.toFloat()
    pts[1] = 0f
    trans.pointValuesToPixel(pts)
    val x0 = pts[0]
    pts[0] = (visibleStart + 1).toFloat()
    trans.pointValuesToPixel(pts)
    val barW = (((pts[0] - x0) * 0.65f).coerceAtLeast(1f))

    for (i in visibleStart..visibleEnd) {
        val c = candles[i]
        pts[0] = i.toFloat()
        pts[1] = 0f
        trans.pointValuesToPixel(pts)
        val cx = pts[0]
        if (hasTaker) {
            val buy = c.takerBuyVol.coerceIn(0.0, c.volume)
            val sell = (c.volume - buy).coerceAtLeast(0.0)
            val buyH = (areaH * (buy / maxVol)).toFloat()
            val sellH = (areaH * (sell / maxVol)).toFloat()
            if (buyH > 0f) {
                canvas.drawRect(cx - barW / 2f, base - buyH, cx + barW / 2f, base, takerBuyPaint)
            }
            if (sellH > 0f) {
                canvas.drawRect(cx - barW / 2f, base - buyH - sellH, cx + barW / 2f, base - buyH, takerSellPaint)
            }
        } else {
            // sin dato taker, barra simple del color de la vela
            val h = (areaH * (c.volume / maxVol)).toFloat()
            if (h > 0f) {
                val paint = if (c.close >= c.open) takerBuyPaint else takerSellPaint
                canvas.drawRect(cx - barW / 2f, base - h, cx + barW / 2f, base, paint)
            }
        }
    }
}

internal fun PriceCombinedChart.drawFixedRangeVolumeProfile(
    canvas: Canvas,
    candles: List<CandleData>,
    visibleStart: Int,
    visibleEnd: Int
) {
    if (visibleStart >= visibleEnd) return
    val contentLeft = viewPortHandler.contentLeft()
    val contentRight = viewPortHandler.contentRight()
    val contentTop = viewPortHandler.contentTop()
    val contentBottom = viewPortHandler.contentBottom()
    val contentHeight = contentBottom - contentTop
    val contentWidth = contentRight - contentLeft
    if (contentHeight <= 0f || contentWidth <= 0f) return

    var minPrice = Double.POSITIVE_INFINITY
    var maxPrice = Double.NEGATIVE_INFINITY
    for (index in visibleStart..visibleEnd) {
        val candle = candles[index]
        minPrice = min(minPrice, candle.low)
        maxPrice = max(maxPrice, candle.high)
    }
    if (!minPrice.isFinite() || !maxPrice.isFinite() || maxPrice <= minPrice) return

    val binCount = (contentHeight / 8f).roundToInt().coerceIn(34, 64)
    val upVolume = DoubleArray(binCount)
    val downVolume = DoubleArray(binCount)
    val binSize = (maxPrice - minPrice) / binCount
    if (binSize <= 0.0) return

    for (index in visibleStart..visibleEnd) {
        val candle = candles[index]
        val firstBin = (((candle.low - minPrice) / binSize).toInt()).coerceIn(0, binCount - 1)
        val lastBin = (((candle.high - minPrice) / binSize).toInt()).coerceIn(0, binCount - 1)
        val touchedBins = (lastBin - firstBin + 1).coerceAtLeast(1)
        val volumePerBin = candle.volume / touchedBins
        for (bin in firstBin..lastBin) {
            if (candle.close >= candle.open) {
                upVolume[bin] += volumePerBin
            } else {
                downVolume[bin] += volumePerBin
            }
        }
    }

    var maxVolume = 0.0
    var pocBin = 0
    for (bin in 0 until binCount) {
        val total = upVolume[bin] + downVolume[bin]
        if (total > maxVolume) {
            maxVolume = total
            pocBin = bin
        }
    }
    if (maxVolume <= 0.0) return

    val profileMaxWidth = (contentWidth * 0.26f).coerceIn(72f, 160f)
    val profileRight = contentRight - 8f
    val profileLeft = profileRight - profileMaxWidth - 6f
    val barGap = 1.4f
    val trans = getTransformer(YAxis.AxisDependency.LEFT)
    val pricePts = FloatArray(2)

    yTagRect.set(
        profileLeft.coerceAtLeast(contentLeft),
        contentTop,
        contentRight,
        contentBottom
    )
    canvas.drawRect(yTagRect, profileBackdropPaint)
    canvas.drawLine(yTagRect.left, contentTop, yTagRect.left, contentBottom, profileBoundaryPaint)

    for (bin in 0 until binCount) {
        val total = upVolume[bin] + downVolume[bin]
        if (total <= 0.0) continue
        val low = minPrice + binSize * bin
        val high = low + binSize
        pricePts[0] = 0f
        pricePts[1] = high.toFloat()
        trans.pointValuesToPixel(pricePts)
        val top = pricePts[1].coerceIn(contentTop, contentBottom)
        pricePts[1] = low.toFloat()
        trans.pointValuesToPixel(pricePts)
        val bottom = pricePts[1].coerceIn(contentTop, contentBottom)
        val barTop = min(top, bottom) + barGap
        val barBottom = max(top, bottom) - barGap
        if (barBottom <= barTop) continue

        val totalWidth = (profileMaxWidth * (total / maxVolume)).toFloat()
        val downWidth = if (total > 0.0) (totalWidth * (downVolume[bin] / total)).toFloat() else 0f
        val upWidth = totalWidth - downWidth
        val right = profileRight
        val left = right - totalWidth
        val split = left + upWidth
        if (bin == pocBin) {
            canvas.drawRect(left, barTop, right, barBottom, profilePocPaint)
        } else {
            if (upWidth > 0f) canvas.drawRect(left, barTop, split, barBottom, profileUpPaint)
            if (downWidth > 0f) canvas.drawRect(split, barTop, right, barBottom, profileDownPaint)
        }
    }

    val pocPrice = minPrice + binSize * (pocBin + 0.5)
    val pocPts = floatArrayOf(0f, pocPrice.toFloat())
    trans.pointValuesToPixel(pocPts)
    val pocY = pocPts[1].coerceIn(contentTop, contentBottom)
    canvas.drawLine(contentLeft, pocY, contentRight, pocY, profilePocLinePaint)

    val label = "POC ${formatPriceForChart(pocPrice, stateRef.value.daySubDollar)}"
    val paddingX = 8f
    val paddingY = 5f
    val labelWidth = selectionTextPaint.measureText(label) + paddingX * 2f
    val labelHeight = selectionTextPaint.textSize + paddingY * 2f
    val labelLeft = (contentRight - labelWidth - 6f).coerceAtLeast(contentLeft + 6f)
    val labelTop = (pocY - labelHeight - 4f).coerceIn(contentTop + 4f, (contentBottom - labelHeight - 4f).coerceAtLeast(contentTop + 4f))
    yTagRect.set(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight)
    canvas.drawRoundRect(yTagRect, 5f, 5f, selectionLabelPaint)
    canvas.drawText(label, labelLeft + paddingX, labelTop + labelHeight - paddingY - 4f, selectionTextPaint)
}






