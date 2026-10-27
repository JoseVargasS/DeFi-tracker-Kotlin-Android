package com.defitracker.app.presentation.crypto_detail.chart

import android.graphics.*
import android.graphics.Color as GraphicsColor
import com.github.mikephil.charting.components.YAxis
import java.util.Locale
import kotlin.math.*
import com.defitracker.app.ui.theme.ChartColors

import android.graphics.Canvas
import com.defitracker.app.presentation.crypto_detail.*

internal fun PriceCombinedChart.drawSmcBackground(
    canvas: Canvas,
    candles: List<CandleData>,
    visibleStart: Int,
    visibleEnd: Int
) {
    if (!prefsRef.value.smcPremium) return
    val range = stateRef.value.smc.premium ?: return
    if (candles.isEmpty()) return
    val contentLeft = viewPortHandler.contentLeft()
    val contentRight = viewPortHandler.contentRight()
    val contentTop = viewPortHandler.contentTop()
    val contentBottom = viewPortHandler.contentBottom()
    if (contentRight <= contentLeft || contentBottom <= contentTop) return

    val rangeStart = max(0, candles.size - SMC_PREMIUM_LOOKBACK)
    if (rangeStart > visibleEnd) return
    val startX = max(visibleStart, rangeStart)

    val trans = getTransformer(YAxis.AxisDependency.LEFT)
    val x0 = floatArrayOf(startX.toFloat(), 0f)
    trans.pointValuesToPixel(x0)
    val x1 = floatArrayOf(visibleEnd.toFloat(), 0f)
    trans.pointValuesToPixel(x1)
    val leftX = min(x0[0], x1[0]).coerceIn(contentLeft, contentRight)
    val rightX = max(x0[0], x1[0]).coerceIn(contentLeft, contentRight)
    val hi = floatArrayOf(0f, range.high.toFloat())
    trans.pointValuesToPixel(hi)
    val eq = floatArrayOf(0f, range.equilibrium.toFloat())
    trans.pointValuesToPixel(eq)
    val lo = floatArrayOf(0f, range.low.toFloat())
    trans.pointValuesToPixel(lo)

    canvas.save()
    canvas.clipRect(contentLeft, contentTop, contentRight, contentBottom)
    try {
        yTagRect.set(leftX, min(hi[1], eq[1]), rightX, max(hi[1], eq[1]))
        canvas.drawRect(yTagRect, smcPremPaint)
        yTagRect.set(leftX, min(eq[1], lo[1]), rightX, max(eq[1], lo[1]))
        canvas.drawRect(yTagRect, smcDiscPaint)
        canvas.drawLine(leftX, eq[1], rightX, eq[1], smcEqLinePaint)
    } finally {
        canvas.restore()
    }
}

// cajas y estructura sobre el chart pero tenues, las velas mandan
internal fun PriceCombinedChart.drawSmcForeground(
    canvas: Canvas,
    candles: List<CandleData>,
    visibleStart: Int,
    visibleEnd: Int
) {
    val smc = stateRef.value.smc
    if (candles.isEmpty()) return
    val smcPrefs = prefsRef.value
    if (!smcPrefs.smcOrderBlocks && !smcPrefs.smcFvg && !smcPrefs.smcStructure && !smcPrefs.smcEqhl && !smcPrefs.smcLiquidity) return
    val trans = getTransformer(YAxis.AxisDependency.LEFT)
    val contentLeft = viewPortHandler.contentLeft()
    val contentRight = viewPortHandler.contentRight()
    val contentTop = viewPortHandler.contentTop()
    val contentBottom = viewPortHandler.contentBottom()
    if (contentRight <= contentLeft || contentBottom <= contentTop) return

    canvas.save()
    canvas.clipRect(contentLeft, contentTop, contentRight, contentBottom)
    try {
    if (smcPrefs.smcOrderBlocks || smcPrefs.smcFvg) {
        var lastZoneTagY = Float.NEGATIVE_INFINITY
        smc.zones.forEach { z ->
            val wantOb = smcPrefs.smcOrderBlocks && z.kind == SmcZoneKind.ORDER_BLOCK
            val wantFvg = smcPrefs.smcFvg && z.kind == SmcZoneKind.FVG
            if (!wantOb && !wantFvg) return@forEach
            if (z.mitigated) return@forEach
            if (z.endIdx < visibleStart || z.startIdx > visibleEnd) return@forEach
            val s = max(z.startIdx, 0)
            val e = min(z.endIdx, candles.size - 1)
            if (e <= s) return@forEach
            val xs = floatArrayOf(s.toFloat(), 0f)
            trans.pointValuesToPixel(xs)
            val xe = floatArrayOf(e.toFloat(), 0f)
            trans.pointValuesToPixel(xe)
            val top = floatArrayOf(0f, z.top.toFloat())
            trans.pointValuesToPixel(top)
            val bot = floatArrayOf(0f, z.bottom.toFloat())
            trans.pointValuesToPixel(bot)
            yTagRect.set(
                min(xs[0], xe[0]).coerceIn(contentLeft, contentRight),
                min(top[1], bot[1]),
                max(xs[0], xe[0]).coerceIn(contentLeft, contentRight),
                max(top[1], bot[1])
            )
            val paint = when {
                z.kind == SmcZoneKind.ORDER_BLOCK && z.bullish -> smcObBullPaint
                z.kind == SmcZoneKind.ORDER_BLOCK -> smcObBearPaint
                z.bullish -> smcFvgBullPaint
                else -> smcFvgBearPaint
            }
            canvas.drawRect(yTagRect, paint)
            // borde punteado del lado + tag a la derecha, texto pelado
            val edge = if (z.bullish) smcObBullEdge else smcObBearEdge
            canvas.drawRect(yTagRect, edge)
            val tagPrefix = if (z.kind == SmcZoneKind.FVG) "FVG" else "OB"
            val tag = "$tagPrefix·${stateRef.value.selectedInterval}"
            val tw = smcFvgTagPaint.measureText(tag)
            val lx = (contentRight - tw - 4f).coerceIn(contentLeft, (contentRight - tw).coerceAtLeast(contentLeft))
            var ly = (yTagRect.top + 20f).coerceIn(contentTop + 16f, contentBottom.coerceAtLeast(contentTop + 16f))
            if (abs(ly - lastZoneTagY) < 24f) {
                ly = (lastZoneTagY + 24f).coerceAtMost(contentBottom - 4f)
            }
            lastZoneTagY = ly
            canvas.drawText(tag, lx, ly, smcFvgTagPaint)
        }
    }

    if (smcPrefs.smcStructure) {
        smc.events.forEach { ev ->
            if (ev.breakIdx < visibleStart || ev.swingIdx > visibleEnd) return@forEach
            val lvl = floatArrayOf(0f, ev.levelPrice.toFloat())
            trans.pointValuesToPixel(lvl)
            val py = lvl[1].coerceIn(contentTop, contentBottom)
            val xs = floatArrayOf(ev.swingIdx.toFloat(), 0f)
            trans.pointValuesToPixel(xs)
            val xb = floatArrayOf(ev.breakIdx.toFloat(), 0f)
            trans.pointValuesToPixel(xb)
            val linePaint = if (ev.bullish) smcBullLinePaint else smcBearLinePaint
            val labelPaint = if (ev.bullish) smcBullLabelPaint else smcBearLabelPaint
            canvas.drawLine(
                xs[0].coerceIn(contentLeft, contentRight), py,
                xb[0].coerceIn(contentLeft, contentRight), py, linePaint
            )
            val label = if (ev.kind == SmcEventKind.BOS) "BOS" else "CHoCH"
            drawSmcChip(canvas, label, labelPaint, xs[0], py, ev.bullish, contentLeft, contentRight, contentTop, contentBottom, placeLeft = true)
        }
    }

    if (smcPrefs.smcEqhl) {
        smc.eqLevels.forEach { eq ->
            if (eq.idx2 < visibleStart || eq.idx1 > visibleEnd) return@forEach
            val pts = floatArrayOf(0f, eq.price.toFloat())
            trans.pointValuesToPixel(pts)
            val py = pts[1].coerceIn(contentTop, contentBottom)
            val x1 = floatArrayOf(eq.idx1.toFloat(), 0f)
            trans.pointValuesToPixel(x1)
            val x2 = floatArrayOf(eq.idx2.toFloat(), 0f)
            trans.pointValuesToPixel(x2)
            canvas.drawLine(
                x1[0].coerceIn(contentLeft, contentRight), py,
                x2[0].coerceIn(contentLeft, contentRight), py, smcEqPaint
            )
            val label = if (eq.isHigh) "EQH" else "EQL"
            drawSmcChip(canvas, label, smcEqLabelPaint, x1[0], py, eq.isHigh, contentLeft, contentRight, contentTop, contentBottom, placeLeft = true)
        }
    }

    // pools BSL/SSL + barridos, tags al borde derecho con stagger
    if (smcPrefs.smcLiquidity) {
        // SWEEP solo el mas reciente por lado, el resto linea pelada
        val latestSweepBuy = smc.liquidity.filter { it.isBuySide && it.swept }.maxByOrNull { it.sweepIdx ?: -1 }
        val latestSweepSell = smc.liquidity.filter { !it.isBuySide && it.swept }.maxByOrNull { it.sweepIdx ?: -1 }
        var lastTagY = Float.NEGATIVE_INFINITY
        smc.liquidity.sortedByDescending { it.idx }.forEach { liq ->
            if (liq.idx > visibleEnd) return@forEach
            val pts = floatArrayOf(0f, liq.price.toFloat())
            trans.pointValuesToPixel(pts)
            val py = pts[1].coerceIn(contentTop, contentBottom)
            val x0 = floatArrayOf(liq.idx.toFloat(), 0f)
            trans.pointValuesToPixel(x0)
            val endIdx = liq.sweepIdx ?: (candles.size - 1)
            if (endIdx < visibleStart) return@forEach
            val x1 = floatArrayOf(endIdx.toFloat(), 0f)
            trans.pointValuesToPixel(x1)
            val linePaint = when {
                liq.swept -> smcSweptPaint
                liq.isBuySide -> smcBslPaint
                else -> smcSslPaint
            }
            canvas.drawLine(
                x0[0].coerceIn(contentLeft, contentRight), py,
                x1[0].coerceIn(contentLeft, contentRight), py, linePaint
            )
            val showTag = !liq.swept || liq === latestSweepBuy || liq === latestSweepSell
            if (showTag) {
                val label = when {
                    liq.swept -> "SWEEP"
                    liq.isBuySide -> "LIQ↓"
                    else -> "LIQ↑"
                }
                val labelPaint = when {
                    liq.swept -> smcSweepLabelPaint
                    liq.isBuySide -> smcBslLabelPaint
                    else -> smcSslLabelPaint
                }
                // tag al final de su linea, encima, desplazado si choca con otro
                val w = labelPaint.measureText(label) + 14f
                val h = labelPaint.textSize + 8f
                val lineEndX = x1[0].coerceIn(contentLeft, contentRight)
                val lx = (lineEndX - w - 4f).coerceIn(contentLeft, (contentRight - w).coerceAtLeast(contentLeft))
                var ly = (py - h - 4f).coerceIn(contentTop, (contentBottom - h).coerceAtLeast(contentTop))
                if (abs(ly - lastTagY) < h + 4f) {
                    ly = (lastTagY + h + 4f).coerceAtMost(contentBottom - h)
                }
                lastTagY = ly
                yTagRect.set(lx, ly, lx + w, ly + h)
                canvas.drawRoundRect(yTagRect, 4f, 4f, smcChipPaint)
                canvas.drawText(label, lx + 7f, ly + h - 6f, labelPaint)
            }
        }
    }

    // bandas grises donde el FVG de otro TF pisa tu zona
    if (smcPrefs.smcFvg) {
        // pasada 1: rectangulos (el orden da igual, misma pintura)
        val confTags = ArrayList<Triple<Float, Float, List<String>>>(smc.confluence.size)
        smc.confluence.forEach { band ->
            val top = floatArrayOf(0f, band.top.toFloat())
            trans.pointValuesToPixel(top)
            val bot = floatArrayOf(0f, band.bottom.toFloat())
            trans.pointValuesToPixel(bot)
            val xs = floatArrayOf(band.startIdx.toFloat(), 0f)
            trans.pointValuesToPixel(xs)
            val xe = floatArrayOf(band.endIdx.toFloat(), 0f)
            trans.pointValuesToPixel(xe)
            if (xe[0] < contentLeft || xs[0] > contentRight) return@forEach
            yTagRect.set(
                xs[0].coerceIn(contentLeft, contentRight),
                min(top[1], bot[1]),
                xe[0].coerceIn(contentLeft, contentRight),
                max(top[1], bot[1])
            )
            canvas.drawRect(yTagRect, smcConfluencePaint)
            // tag compuesto: una linea si entra, dos si es largo, texto pelado
            val ownTf = stateRef.value.selectedInterval
            val confLine1 = "FVG·$ownTf"
            val confLine2 = buildString {
                sortTfsForTag(band.tfs).forEach { append("+").append(it) }
                if (band.hasOb) append(" +OB")
            }.toString()
            val fullTag = "$confLine1$confLine2"
            val availRight = (contentRight - yTagRect.right - 8f).coerceAtLeast(0f)
            val confLines = if (confLine2.isBlank() ||
                smcConfluenceTagPaint.measureText(fullTag) <= availRight
            ) {
                listOf(fullTag)
            } else {
                listOf(confLine1, confLine2)
            }
            val centerY = (min(top[1], bot[1]) + max(top[1], bot[1])) / 2f
            confTags.add(Triple(centerY, yTagRect.right, confLines))
        }
        // pasada 2: tags de arriba hacia abajo para que el stagger no haga cascada
        var lastConfBottom = Float.NEGATIVE_INFINITY
        confTags.sortedBy { it.first }.forEach { (center, rectRight, confLines) ->
            val confH = smcConfluenceTagPaint.textSize + 4f
            val blockH = confLines.size * confH
            // centrado vertical al costado de su propio rectangulo + stagger por bloque
            var centerY = center
            if (centerY - blockH / 2f < lastConfBottom + 4f) {
                centerY = lastConfBottom + 4f + blockH / 2f
            }
            lastConfBottom = centerY + blockH / 2f
            val maxW = confLines.maxOf { smcConfluenceTagPaint.measureText(it) }
            val confLx = (rectRight + 4f).coerceIn(contentLeft, (contentRight - maxW).coerceAtLeast(contentLeft))
            var confTy = (centerY - blockH / 2f).coerceIn(contentTop, (contentBottom - blockH).coerceAtLeast(contentTop))
            confLines.forEach { ln ->
                canvas.drawText(ln, confLx, confTy + confH - 4f, smcConfluenceTagPaint)
                confTy += confH
            }
        }
    }
    } finally {
        canvas.restore()
    }
}

// chip oscuro para que el tag se lea sobre las velas
internal fun PriceCombinedChart.drawSmcChip(
    canvas: Canvas,
    label: String,
    labelPaint: Paint,
    anchorX: Float,
    anchorY: Float,
    above: Boolean,
    contentLeft: Float,
    contentRight: Float,
    contentTop: Float,
    contentBottom: Float,
    placeLeft: Boolean = false
) {
    val w = labelPaint.measureText(label) + 4f
    val h = labelPaint.textSize + 4f
    val lx = if (placeLeft) {
        (anchorX - w - 4f).coerceIn(contentLeft, (contentRight - w).coerceAtLeast(contentLeft))
    } else {
        (anchorX + 4f).coerceIn(contentLeft, (contentRight - w).coerceAtLeast(contentLeft))
    }
    val ly = if (above) {
        (anchorY - h - 4f).coerceIn(contentTop, (contentBottom - h).coerceAtLeast(contentTop))
    } else {
        (anchorY + 4f).coerceIn(contentTop, (contentBottom - h).coerceAtLeast(contentTop))
    }
    canvas.drawText(label, lx, ly + h - 4f, labelPaint)
}

internal fun PriceCombinedChart.trimRatio(r: Float): String {
    val s = String.format(Locale.US, "%.3f", r).trimEnd('0').trimEnd('.')
    if (s.isEmpty()) return "0"
    // estilo tradingview: sin cero inicial (.618 en vez de 0.618)
    return if (s.startsWith("0.")) s.substring(1) else s
}








