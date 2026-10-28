package com.defitracker.app.presentation.crypto_detail.chart

import kotlin.math.*

import android.view.MotionEvent
import com.defitracker.app.presentation.crypto_detail.*
import com.github.mikephil.charting.components.YAxis

internal enum class ChartTouchMode {
    UNDECIDED,
    PAN,
    Y_ZOOM,
    PINCH_X_ZOOM
}

internal class ChartGestureController(
    private val chart: PriceCombinedChart
) {
    fun onTouchEvent(event: MotionEvent?): Boolean = with(chart) {
        if (event == null) return false

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = event.x
            downY = event.y
            lastGestureX = event.x
            lastGestureY = event.y
            zoomPivotX = matrixPivotX(event.x)
            zoomPivotY = matrixPivotY(event.y)
            touchMode = ChartTouchMode.UNDECIDED
            gestureMoved = false
        }
        
        // fibo primero en cualquier modo (menos Medir); vacio = sigue al flujo normal
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                if (toolRef.value == DrawingTool.MEASURE) {
                    // en Medir manda la cinta, los overlays no interceptan
                } else {
                    val ph = positionHitTest(event.x, event.y)
                    if (ph != null) {
                        val (pid, pzone, _) = ph
                        onPositionSelect?.invoke(pid)
                        selectedPositionRef.value = pid
                        selectedFibRef.value = null
                        selectedDrawRef.value = null
                        downX = event.x
                        downY = event.y
                        posDragMoved = false
                        posDragId = pid
                        posDragZone = pzone
                        if (pzone == 1 || pzone == 3) {
                            // se fija el campo agarrado (TP o SL) pa' todo el gesto
                            val o = positionsRef.value.firstOrNull { it.id == pid }
                            val topIsProfit = o?.let { positionGeom(it)?.topIsProfit } ?: true
                            posDragTopIsTp = if (pzone == 1) topIsProfit else !topIsProfit
                        }
                        if (pzone == 4) {
                            val o = positionsRef.value.firstOrNull { it.id == pid }
                            val candles = stateRef.value.candles
                            posOrigStart = o?.startTime ?: 0L
                            posOrigEnd = o?.endTime ?: 0L
                            posOrigEntry = o?.entry ?: 0.0
                            posOrigSl = o?.sl ?: 0.0
                            posOrigTp = o?.tp ?: 0.0
                            posGrabIdx = touchToCandleIndex(event.x, event.y, candles.size, FIB_VIRTUAL_EXTEND)
                                ?: o?.let { fibTimeToIndexVirtual(candles, it.startTime, stateRef.value.selectedInterval) } ?: 0
                            posGrabPrice = getTransformer(YAxis.AxisDependency.LEFT)
                                .getValuesByTouchPoint(event.x, event.y).y.toDouble()
                        }
                        parent.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                    val dh = drawHitTest(event.x, event.y)
                    if (dh != null) {
                        val (did, dzone, dlocked) = dh
                        onDrawSelect?.invoke(did)
                        selectedDrawRef.value = did
                        selectedFibRef.value = null
                        downX = event.x
                        downY = event.y
                        drawDragMoved = false
                        if (!dlocked) {
                            drawDragId = did
                            drawDragZone = dzone
                            if (dzone == 3) {
                                val o = drawsRef.value.firstOrNull { it.id == did }
                                val candles = stateRef.value.candles
                                drawOrigStart = o?.start
                                drawOrigEnd = o?.end
                                drawGrabIdx = touchToCandleIndex(event.x, event.y, candles.size)
                                    ?: o?.let { fibTimeToIndexVirtual(candles, it.start.time, stateRef.value.selectedInterval) } ?: 0
                                drawGrabPrice = getTransformer(YAxis.AxisDependency.LEFT)
                                    .getValuesByTouchPoint(event.x, event.y).y.toDouble()
                            }
                        }
                        parent.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                    val hit = fibHitTest(event.x, event.y)
                if (hit != null) {
                    val (id, zone, locked) = hit
                    onFibSelect?.invoke(id)
                    selectedFibRef.value = id
                    selectedDrawRef.value = null
                    downX = event.x
                    downY = event.y
                    fibDragMoved = false
                    if (!locked) {
                        fibDragId = id
                        fibDragZone = zone
                        if (zone == 3) {
                            val o = overlaysRef.value.firstOrNull { it.id == id }
                            val candles = stateRef.value.candles
                            fibOrigStart = o?.start
                            fibOrigEnd = o?.end
                            fibGrabIdx = touchToCandleIndex(event.x, event.y, candles.size)
                                ?: o?.let { fibTimeToIndexVirtual(candles, it.start.time, stateRef.value.selectedInterval) } ?: 0
                            fibGrabPrice = getTransformer(YAxis.AxisDependency.LEFT)
                                .getValuesByTouchPoint(event.x, event.y).y.toDouble()
                        }
                    }
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                    }
                }
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (fibDragId != null) {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    if (dx > 10f || dy > 10f) fibDragMoved = true
                    if (fibDragMoved) {
                        val id = fibDragId!!
                        val cur = overlaysRef.value.firstOrNull { it.id == id }
                        if (cur != null) {
                            if (fibDragZone == 3) {
                                // mover cuerpo conserva tamaño (delta indice+precio)
                                val candles = stateRef.value.candles
                                val rawMoveX = getTransformer(YAxis.AxisDependency.LEFT)
                                    .getValuesByTouchPoint(event.x, event.y).x.roundToInt()
                                val newIdx = rawMoveX.coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                val newPrice = getTransformer(YAxis.AxisDependency.LEFT)
                                    .getValuesByTouchPoint(event.x, event.y).y.toDouble()
                                if (fibOrigStart != null && fibOrigEnd != null) {
                                    val dIdx = newIdx - fibGrabIdx
                                    val dPrice = newPrice - fibGrabPrice
                                    val interval = stateRef.value.selectedInterval
                                    val s0 = fibTimeToIndexVirtual(candles, fibOrigStart!!.time, interval)
                                    val e0 = fibTimeToIndexVirtual(candles, fibOrigEnd!!.time, interval)
                                    val s1 = (s0 + dIdx).coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                    val e1 = (e0 + dIdx).coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                    val next = cur.copy(
                                        start = FibAnchor(fibIndexToTime(candles, s1, interval), fibOrigStart!!.price + dPrice),
                                        end = FibAnchor(fibIndexToTime(candles, e1, interval), fibOrigEnd!!.price + dPrice)
                                    )
                                    overlaysRef.value = overlaysRef.value.map { if (it.id == id) next else it }
                                    onFibLive?.invoke(id, next)
                                    invalidate()
                                }
                            } else {
                                anchorFromTouch(event.x, event.y)?.let { anchor ->
                                    val next = if (fibDragZone == 1) cur.copy(start = anchor)
                                    else cur.copy(end = anchor)
                                    overlaysRef.value = overlaysRef.value.map { if (it.id == id) next else it }
                                    onFibLive?.invoke(id, next)
                                    invalidate()
                                }
                            }
                        }
                    }
                    return true
                }
                if (drawDragId != null) {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    if (dx > 10f || dy > 10f) drawDragMoved = true
                    if (drawDragMoved) {
                        val id = drawDragId!!
                        val cur = drawsRef.value.firstOrNull { it.id == id }
                        if (cur != null) {
                            if (drawDragZone == 3) {
                                // mover cuerpo conserva tamaño (delta indice+precio)
                                val candles = stateRef.value.candles
                                val rawMoveX = getTransformer(YAxis.AxisDependency.LEFT)
                                    .getValuesByTouchPoint(event.x, event.y).x.roundToInt()
                                val newIdx = rawMoveX.coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                val newPrice = getTransformer(YAxis.AxisDependency.LEFT)
                                    .getValuesByTouchPoint(event.x, event.y).y.toDouble()
                                if (drawOrigStart != null && drawOrigEnd != null) {
                                    val dIdx = newIdx - drawGrabIdx
                                    val dPrice = newPrice - drawGrabPrice
                                    val interval = stateRef.value.selectedInterval
                                    val s0 = fibTimeToIndexVirtual(candles, drawOrigStart!!.time, interval)
                                    val e0 = fibTimeToIndexVirtual(candles, drawOrigEnd!!.time, interval)
                                    val s1 = (s0 + dIdx).coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                    val e1 = (e0 + dIdx).coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                    val next = cur.copy(
                                        start = FibAnchor(fibIndexToTime(candles, s1, interval), drawOrigStart!!.price + dPrice),
                                        end = FibAnchor(fibIndexToTime(candles, e1, interval), drawOrigEnd!!.price + dPrice)
                                    )
                                    drawsRef.value = drawsRef.value.map { if (it.id == id) next else it }
                                    onDrawLive?.invoke(id, next)
                                    invalidate()
                                }
                            } else {
                                drawAnchorFromTouch(event.x, event.y)?.let { anchor ->
                                    val next = when (drawDragZone) {
                                        1 -> cur.copy(start = anchor)
                                        2 -> cur.copy(end = anchor)
                                        // lados: solo el eje agarrado, sin cruzarse al opuesto
                                        4 -> if (cur.start.time <= cur.end.time) cur.copy(start = cur.start.copy(time = minOf(anchor.time, cur.end.time)))
                                        else cur.copy(end = cur.end.copy(time = minOf(anchor.time, cur.start.time)))
                                        5 -> if (cur.start.time >= cur.end.time) cur.copy(start = cur.start.copy(time = maxOf(anchor.time, cur.end.time)))
                                        else cur.copy(end = cur.end.copy(time = maxOf(anchor.time, cur.start.time)))
                                        6 -> if (cur.start.price >= cur.end.price) cur.copy(start = cur.start.copy(price = maxOf(anchor.price, cur.end.price)))
                                        else cur.copy(end = cur.end.copy(price = maxOf(anchor.price, cur.start.price)))
                                        else -> if (cur.start.price <= cur.end.price) cur.copy(start = cur.start.copy(price = minOf(anchor.price, cur.end.price)))
                                        else cur.copy(end = cur.end.copy(price = minOf(anchor.price, cur.start.price)))
                                    }
                                    drawsRef.value = drawsRef.value.map { if (it.id == id) next else it }
                                    onDrawLive?.invoke(id, next)
                                    invalidate()
                                }
                            }
                        }
                    }
                    return true
                }
                if (posDragId != null) {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    if (dx > 10f || dy > 10f) posDragMoved = true
                    if (posDragMoved) {
                        val id = posDragId!!
                        val cur = positionsRef.value.firstOrNull { it.id == id }
                        if (cur != null) {
                            val candles = stateRef.value.candles
                            val interval = stateRef.value.selectedInterval
                            if (posDragZone == 4) {
                                // mover cuerpo conserva tamaño (delta indice+precio)
                                val rawMoveX = getTransformer(YAxis.AxisDependency.LEFT)
                                    .getValuesByTouchPoint(event.x, event.y).x.roundToInt()
                                val newIdx = rawMoveX.coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                val newPrice = getTransformer(YAxis.AxisDependency.LEFT)
                                    .getValuesByTouchPoint(event.x, event.y).y.toDouble()
                                val dIdx = newIdx - posGrabIdx
                                val dPrice = newPrice - posGrabPrice
                                val s0 = fibTimeToIndexVirtual(candles, posOrigStart, interval)
                                val e0 = fibTimeToIndexVirtual(candles, posOrigEnd, interval)
                                // se recorta el desplazamiento, no los extremos: el ancho no cambia
                                val hi = candles.size - 1 + FIB_VIRTUAL_EXTEND
                                val shift = dIdx.coerceIn(0 - minOf(s0, e0), hi - maxOf(s0, e0))
                                val s1 = s0 + shift
                                val e1 = e0 + shift
                                val next = cur.copy(
                                    startTime = fibIndexToTime(candles, s1, interval),
                                    endTime = fibIndexToTime(candles, e1, interval),
                                    entry = posOrigEntry + dPrice,
                                    sl = posOrigSl + dPrice,
                                    tp = posOrigTp + dPrice
                                )
                                positionsRef.value = positionsRef.value.map { if (it.id == id) next else it }
                                onPositionLive?.invoke(id, next)
                                invalidate()
                            } else if (posDragZone == 2) {
                                // handle del medio: solo ancho (tiempo final)
                                val rawX = getTransformer(YAxis.AxisDependency.LEFT)
                                    .getValuesByTouchPoint(event.x, event.y).x.roundToInt()
                                val idx = rawX.coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                val t = fibIndexToTime(candles, idx, interval)
                                val next = cur.copy(endTime = maxOf(t, cur.startTime))
                                positionsRef.value = positionsRef.value.map { if (it.id == id) next else it }
                                onPositionLive?.invoke(id, next)
                                invalidate()
                            } else {
                                // handles arriba/abajo: solo el campo agarrado, aunque se crucen
                                val price = getTransformer(YAxis.AxisDependency.LEFT)
                                    .getValuesByTouchPoint(event.x, event.y).y.toDouble()
                                if (price <= 0) return true
                                val next = if (posDragTopIsTp) cur.copy(tp = price) else cur.copy(sl = price)
                                positionsRef.value = positionsRef.value.map { if (it.id == id) next else it }
                                onPositionLive?.invoke(id, next)
                                invalidate()
                            }
                        }
                    }
                    return true
                }
            }
            android.view.MotionEvent.ACTION_UP -> {
                if (fibDragId != null) {
                    if (fibDragMoved) onFibCommit?.invoke(fibDragId!!)
                    else invalidate()
                    fibDragId = null
                    fibDragZone = 0
                    return true
                }
                if (drawDragId != null) {
                    if (drawDragMoved) onDrawCommit?.invoke(drawDragId!!)
                    else invalidate()
                    drawDragId = null
                    drawDragZone = 0
                    return true
                }
                if (posDragId != null) {
                    if (posDragMoved) onPositionCommit?.invoke(posDragId!!)
                    else invalidate()
                    posDragId = null
                    posDragZone = 0
                    return true
                }
            }
            android.view.MotionEvent.ACTION_CANCEL -> {
                fibDragId = null
                fibDragZone = 0
                drawDragId = null
                drawDragZone = 0
                posDragId = null
                posDragZone = 0
            }
        }
        
        if (toolRef.value == DrawingTool.MEASURE) {
            parent.requestDisallowInterceptTouchEvent(true)
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    measureDragZone = 0
                    measureDragMoved = false
                    // esquinas redimensionan, dentro mueve la zona
                    val hit = measureHitTest(event.x, event.y)
                    if (hit != 0) {
                        measureDragZone = hit
                        measureOrig = rangeSelection.value
                        val candles = stateRef.value.candles
                        measureGrabIdx = touchToCandleIndex(event.x, event.y, candles.size, FIB_VIRTUAL_EXTEND) ?: 0
                        measureGrabPrice = getTransformer(YAxis.AxisDependency.LEFT)
                            .getValuesByTouchPoint(event.x, event.y).y.toDouble()
                    }
                    return true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (measureDragZone != 0) {
                        val dx = abs(event.x - downX)
                        val dy = abs(event.y - downY)
                        if (dx > 10f || dy > 10f) measureDragMoved = true
                        if (measureDragMoved) {
                            val anchor = measureAnchor(event.x, event.y)
                            val orig = measureOrig
                            if (anchor != null && orig != null && orig.isComplete) {
                                setRangeSelection(
                                    if (measureDragZone == 3) {
                                        // mover conserva tamaño
                                        val candles = stateRef.value.candles
                                        val dIdx = anchor.first - measureGrabIdx
                                        val dPrice = anchor.second - measureGrabPrice
                                        val s1 = (orig.startIdx + dIdx).coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                        val e1 = (orig.endIdx!! + dIdx).coerceIn(0, candles.size - 1 + FIB_VIRTUAL_EXTEND)
                                        orig.copy(
                                            startIdx = s1,
                                            startPrice = orig.startPrice + dPrice,
                                            endIdx = e1,
                                            endPrice = orig.endPrice!! + dPrice
                                        )
                                    } else if (measureDragZone == 1) {
                                        orig.copy(startIdx = anchor.first, startPrice = anchor.second)
                                    } else {
                                        orig.copy(endIdx = anchor.first, endPrice = anchor.second)
                                    }
                                )
                                invalidate()
                            }
                        }
                    }
                    return true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (measureDragZone != 0) {
                        measureDragZone = 0
                        measureOrig = null
                        invalidate()
                        return true
                    }
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    if (dx < 14f && dy < 14f) {
                        performClick()
                        applyRangeTap(event.x, event.y)
                    }
                    return true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    measureDragZone = 0
                    measureOrig = null
                    return true
                }
            }
            return true
        }
        
        // modo colocacion, el tap crea puntos; arrastrar = pan para no atascarse
        // LONG/SHORT crean la posicion con un tap (defaults 10x, SL/TP 1%)
        val tool = toolRef.value
        if (tool == DrawingTool.FIBO || tool == DrawingTool.DRAW || tool == DrawingTool.LONG_POS || tool == DrawingTool.SHORT_POS) {
            parent.requestDisallowInterceptTouchEvent(true)
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    lastGestureX = event.x
                    lastGestureY = event.y
                    return true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastGestureX
                    val dy = event.y - lastGestureY
                    if (abs(event.x - downX) > 14f || abs(event.y - downY) > 14f) {
                        applyPan(dx, dy)
                        lastGestureX = event.x
                        lastGestureY = event.y
                        lastTouchYPx = -1f
                        lastTouchXPx = -1f
                        invalidate()
                    }
                    return true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    if (dx < 14f && dy < 14f) {
                        performClick()
                        if (toolRef.value == DrawingTool.FIBO) applyFibTap(event.x, event.y)
                        else if (toolRef.value == DrawingTool.LONG_POS) applyPositionTap(event.x, event.y, true)
                        else if (toolRef.value == DrawingTool.SHORT_POS) applyPositionTap(event.x, event.y, false)
                        else applyDrawTap(event.x, event.y)
                    }
                    return true
                }
                android.view.MotionEvent.ACTION_CANCEL -> return true
            }
            return true
        }
        
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                // con cruz activa se ignora el segundo dedo
                if (highlighted != null && highlighted.isNotEmpty()) {
                    gestureMoved = true
                    return true
                }
                if (event.pointerCount >= 2) {
                    val firstX = event.getX(0)
                    val secondX = event.getX(1)
                    lastPinchSpanX = abs(secondX - firstX).coerceAtLeast(1f)
                    zoomPivotX = matrixPivotX((firstX + secondX) / 2f)
                    zoomPivotY = matrixPivotY((event.getY(0) + event.getY(1)) / 2f)
                    touchMode = ChartTouchMode.PINCH_X_ZOOM
                    gestureMoved = true
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                return true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    // con cruz activa todo el grafico se bloquea, ni pinch
                    if (highlighted != null && highlighted.isNotEmpty()) {
                        gestureMoved = true
                        return true
                    }
                    val firstX = event.getX(0)
                    val secondX = event.getX(1)
                    val currentSpanX = abs(secondX - firstX).coerceAtLeast(1f)
                    if (lastPinchSpanX > 0f) {
                        applyHorizontalPinchZoom(
                            currentSpanX / lastPinchSpanX,
                            matrixPivotX((firstX + secondX) / 2f),
                            matrixPivotY((event.getY(0) + event.getY(1)) / 2f)
                        )
                    }
                    lastPinchSpanX = currentSpanX
                    gestureMoved = true
                    touchMode = ChartTouchMode.PINCH_X_ZOOM
                    return true
                }
                if (event.pointerCount != 1) return true
        
                // cruz activa sigue al dedo, pan/zoom bloqueados
                if (highlighted != null && highlighted.isNotEmpty()) {
                    gestureMoved = true
                    lastTouchYPx = event.y
                    lastTouchXPx = event.x
                    // fuera de datos no hay highlight: se guarda el ultimo valido
                    getHighlightByTouchPoint(event.x, event.y)?.let { highlightValue(it, true) }
                    syncHighlights(this, stochChartRef.value, rsiChartRef.value, macdChartRef.value, takerChartRef.value)
                    lastGestureX = event.x
                    lastGestureY = event.y
                    invalidate()
                    return true
                }
        
                val totalDx = event.x - downX
                val totalDy = event.y - downY
                val threshold = 4f * density
                if (touchMode == ChartTouchMode.UNDECIDED &&
                    (abs(totalDx) > threshold || abs(totalDy) > threshold)
                ) {
                    touchMode = when {
                        isOnYAxis(downX) && abs(totalDy) >= abs(totalDx) -> ChartTouchMode.Y_ZOOM
                        else -> ChartTouchMode.PAN
                    }
                }
        
                if (touchMode != ChartTouchMode.UNDECIDED) {
                    gestureMoved = true
                    val dx = event.x - lastGestureX
                    val dy = event.y - lastGestureY
                    if (touchMode == ChartTouchMode.PAN) {
                        applyPan(dx, dy)
                    } else if (touchMode == ChartTouchMode.Y_ZOOM) {
                        applyYZoom(dy)
                    }
                    lastGestureX = event.x
                    lastGestureY = event.y
                    lastTouchYPx = -1f
                    lastTouchXPx = -1f
                    invalidate()
                    return true
                }
            }
            android.view.MotionEvent.ACTION_UP -> {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
        
                // Detect a TAP
                if (!gestureMoved && dx < 10f && dy < 10f) {
                    performClick()
                    // tap al vacio quita el foco: sin seleccion no hay boton flotante
                    if (fibHitTest(event.x, event.y) == null && drawHitTest(event.x, event.y) == null && positionHitTest(event.x, event.y) == null) {
                        onFibSelect?.invoke(null)
                        onDrawSelect?.invoke(null)
                        onPositionSelect?.invoke(null)
                        selectedFibRef.value = null
                        selectedDrawRef.value = null
                        selectedPositionRef.value = null
                        invalidate()
                    }
                    if (highlighted != null && highlighted.isNotEmpty()) {
                        // Toggle OFF
                        highlightValue(null)
                        lastTouchYPx = -1f
                        lastTouchXPx = -1f
                        syncHighlights(this, stochChartRef.value, rsiChartRef.value, macdChartRef.value, takerChartRef.value)
                    } else {
                        // Toggle ON
                        lastTouchYPx = event.y
                        lastTouchXPx = event.x
                        val h = getHighlightByTouchPoint(event.x, event.y)
                        if (h != null) highlightValue(h, true)
                    }
                    invalidate()
                    touchMode = ChartTouchMode.UNDECIDED
                    lastPinchSpanX = 0f
                    parent.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                touchMode = ChartTouchMode.UNDECIDED
                lastPinchSpanX = 0f
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
            android.view.MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount >= 2) {
                    val remainingIndex = if (event.actionIndex == 0) 1 else 0
                    downX = event.getX(remainingIndex)
                    downY = event.getY(remainingIndex)
                    lastGestureX = downX
                    lastGestureY = downY
                }
                touchMode = ChartTouchMode.UNDECIDED
                lastPinchSpanX = 0f
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            android.view.MotionEvent.ACTION_CANCEL -> {
                touchMode = ChartTouchMode.UNDECIDED
                gestureMoved = false
                lastPinchSpanX = 0f
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }
}



