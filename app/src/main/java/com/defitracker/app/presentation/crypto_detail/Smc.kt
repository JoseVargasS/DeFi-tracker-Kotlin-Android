package com.defitracker.app.presentation.crypto_detail

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ponytail: SMC estilo LuxAlgo, funciones puras sobre velas (testeables sin Android)

const val SMC_SWING_LOOKBACK = 5
const val SMC_OB_LOOKBACK = 10
const val SMC_MAX_ZONES_PER_SIDE = 5
const val SMC_PREMIUM_LOOKBACK = 120
const val SMC_LIQ_SWINGS_PER_SIDE = 3

data class SmcSwing(val idx: Int, val price: Double, val isHigh: Boolean)

enum class SmcEventKind { BOS, CHOCH }

data class SmcEvent(
    val breakIdx: Int,
    val swingIdx: Int,
    val levelPrice: Double,
    val kind: SmcEventKind,
    val bullish: Boolean
)

enum class SmcZoneKind { ORDER_BLOCK, FVG }

data class SmcZone(
    val startIdx: Int,
    val endIdx: Int, // ponytail: mitigacion o ultima vela (se extiende solo)
    val top: Double,
    val bottom: Double,
    val bullish: Boolean,
    val kind: SmcZoneKind,
    val mitigated: Boolean
)

data class SmcEqLevel(val price: Double, val idx1: Int, val idx2: Int, val isHigh: Boolean)

data class SmcPremiumRange(val high: Double, val low: Double) {
    val equilibrium: Double = (high + low) / 2.0
}

// ponytail: banda gris donde el FVG de un TF mayor pisa tu zona del TF actual
data class SmcConfluenceBand(
    val top: Double,
    val bottom: Double,
    val startIdx: Int,
    val endIdx: Int,
    val tfLabel: String
)

data class SmcData(
    val swings: List<SmcSwing> = emptyList(),
    val events: List<SmcEvent> = emptyList(),
    val zones: List<SmcZone> = emptyList(),
    val eqLevels: List<SmcEqLevel> = emptyList(),
    val premium: SmcPremiumRange? = null,
    val confluence: List<SmcConfluenceBand> = emptyList(),
    val liquidity: List<SmcLiqLevel> = emptyList()
)

// ponytail: fractal N por lado, confirma con lag como en LuxAlgo
fun detectSwings(candles: List<CandleData>, lookback: Int = SMC_SWING_LOOKBACK): List<SmcSwing> {
    if (candles.size < lookback * 2 + 1) return emptyList()
    val out = ArrayList<SmcSwing>()
    for (i in lookback until candles.size - lookback) {
        val h = candles[i].high
        var isHigh = true
        var j = i - lookback
        while (j <= i + lookback) {
            if (j != i && candles[j].high > h) {
                isHigh = false
                break
            }
            j++
        }
        if (isHigh) out.add(SmcSwing(i, h, true))
        val l = candles[i].low
        var isLow = true
        j = i - lookback
        while (j <= i + lookback) {
            if (j != i && candles[j].low < l) {
                isLow = false
                break
            }
            j++
        }
        if (isLow) out.add(SmcSwing(i, l, false))
    }
    return out
}

private fun atr(candles: List<CandleData>, period: Int = 14): Double {
    if (candles.size < 2) return 0.0
    var sum = 0.0
    var n = 0
    var i = max(1, candles.size - period)
    while (i < candles.size) {
        val prev = candles[i - 1].close
        sum += maxOf(candles[i].high - candles[i].low, abs(candles[i].high - prev), abs(candles[i].low - prev))
        n++
        i++
    }
    return if (n > 0) sum / n else 0.0
}

// ponytail: rompe a favor = BOS, en contra = CHoCH con flip de sesgo
fun detectStructure(candles: List<CandleData>, swings: List<SmcSwing>): List<SmcEvent> {
    if (candles.isEmpty() || swings.isEmpty()) return emptyList()
    val events = ArrayList<SmcEvent>()
    var trend = 0 // 1 alcista, -1 bajista
    var refHigh: SmcSwing? = null
    var refLow: SmcSwing? = null
    var s = 0
    // ponytail: arranca con el primer par alto/bajo para tener referencias
    while (s < swings.size && (refHigh == null || refLow == null)) {
        val sw = swings[s]
        if (sw.isHigh && refHigh == null) refHigh = sw
        if (!sw.isHigh && refLow == null) refLow = sw
        s++
    }
    if (refHigh == null || refLow == null) return events
    var i = max(refHigh.idx, refLow.idx) + 1
    while (i < candles.size) {
        val close = candles[i].close
        // ponytail: avanza referencias con swings ya confirmados a esta altura
        while (s < swings.size && swings[s].idx <= i) {
            val sw = swings[s]
            if (sw.isHigh && sw.idx > (refHigh?.idx ?: -1)) refHigh = sw
            if (!sw.isHigh && sw.idx > (refLow?.idx ?: -1)) refLow = sw
            s++
        }
        val rh = refHigh
        val rl = refLow
        if (rh != null && close > rh.price) {
            val kind = if (trend == 1) SmcEventKind.BOS else SmcEventKind.CHOCH
            events.add(SmcEvent(i, rh.idx, rh.price, kind, true))
            trend = 1
            // ponytail: la referencia avanza al maximo del quiebre o se repite el BOS
            refHigh = SmcSwing(i, candles[i].high, true)
            i++
            continue
        }
        if (rl != null && close < rl.price) {
            val kind = if (trend == -1) SmcEventKind.BOS else SmcEventKind.CHOCH
            events.add(SmcEvent(i, rl.idx, rl.price, kind, false))
            trend = -1
            refLow = SmcSwing(i, candles[i].low, false)
            i++
            continue
        }
        i++
    }
    return events
}

// ponytail: ultima vela opuesta antes del impulso que rompio
fun detectOrderBlocks(
    candles: List<CandleData>,
    events: List<SmcEvent>
): List<SmcZone> {
    if (candles.isEmpty() || events.isEmpty()) return emptyList()
    val out = ArrayList<SmcZone>()
    events.forEach { ev ->
        var k = ev.breakIdx - 1
        val from = max(0, ev.breakIdx - SMC_OB_LOOKBACK)
        var found: Int? = null
        while (k >= from) {
            val c = candles[k]
            val opposite = if (ev.bullish) c.close < c.open else c.close > c.open
            if (opposite) {
                found = k
                break
            }
            k--
        }
        val f = found ?: return@forEach
        val c = candles[f]
        // ponytail: mitigacion por cierre dentro de la zona
        var end = candles.size - 1
        var mitigated = false
        var m = f + 1
        while (m < candles.size) {
            val cl = candles[m].close
            if (cl >= c.low && cl <= c.high) {
                end = m
                mitigated = true
                break
            }
            m++
        }
        out.add(
            SmcZone(
                startIdx = f,
                endIdx = end,
                top = c.high,
                bottom = c.low,
                bullish = ev.bullish,
                kind = SmcZoneKind.ORDER_BLOCK,
                mitigated = mitigated
            )
        )
    }
    // ponytail: solo los recientes por lado, el chart manda no las cajas
    val bull = out.filter { it.bullish }.takeLast(SMC_MAX_ZONES_PER_SIDE)
    val bear = out.filter { !it.bullish }.takeLast(SMC_MAX_ZONES_PER_SIDE)
    return (bull + bear).sortedBy { it.startIdx }
}

// ponytail: imbalance de 3 velas con umbral ATR (sin threshold todo nace mitigado)
fun detectFvg(candles: List<CandleData>): List<SmcZone> {
    if (candles.size < 3) return emptyList()
    val minGap = atr(candles) * 0.3
    val out = ArrayList<SmcZone>()
    for (i in 2 until candles.size) {
        val a = candles[i - 2]
        val c = candles[i]
        if (c.low > a.high && c.low - a.high >= minGap) {
            var end = candles.size - 1
            var mitigated = false
            var m = i + 1
            while (m < candles.size) {
                if (candles[m].low <= c.low) {
                    end = m
                    mitigated = true
                    break
                }
                m++
            }
            out.add(SmcZone(i - 2, end, c.low, a.high, true, SmcZoneKind.FVG, mitigated))
        } else if (c.high < a.low && a.low - c.high >= minGap) {
            var end = candles.size - 1
            var mitigated = false
            var m = i + 1
            while (m < candles.size) {
                if (candles[m].high >= c.high) {
                    end = m
                    mitigated = true
                    break
                }
                m++
            }
            out.add(SmcZone(i - 2, end, a.low, c.high, false, SmcZoneKind.FVG, mitigated))
        }
    }
    val bull = out.filter { it.bullish }.takeLast(SMC_MAX_ZONES_PER_SIDE)
    val bear = out.filter { !it.bullish }.takeLast(SMC_MAX_ZONES_PER_SIDE)
    return (bull + bear).sortedBy { it.startIdx }
}

// ponytail: dos swings casi iguales = liquidez
fun detectEqLevels(candles: List<CandleData>, swings: List<SmcSwing>): List<SmcEqLevel> {
    if (swings.size < 2) return emptyList()
    val tol = atr(candles) * 0.25
    if (tol <= 0.0) return emptyList()
    val out = ArrayList<SmcEqLevel>()
    val highs = swings.filter { it.isHigh }
    val lows = swings.filter { !it.isHigh }
    fun pair(list: List<SmcSwing>, isHigh: Boolean) {
        var a = 0
        while (a < list.size) {
            var b = a + 1
            while (b < list.size) {
                if (abs(list[a].price - list[b].price) <= tol) {
                    out.add(SmcEqLevel((list[a].price + list[b].price) / 2.0, list[a].idx, list[b].idx, isHigh))
                    break
                }
                b++
            }
            a++
        }
    }
    pair(highs, true)
    pair(lows, false)
    return out.takeLast(6)
}

fun detectPremium(candles: List<CandleData>): SmcPremiumRange? {
    if (candles.isEmpty()) return null
    val from = max(0, candles.size - SMC_PREMIUM_LOOKBACK)
    var hi = Double.NEGATIVE_INFINITY
    var lo = Double.POSITIVE_INFINITY
    for (i in from until candles.size) {
        hi = max(hi, candles[i].high)
        lo = min(lo, candles[i].low)
    }
    if (!hi.isFinite() || !lo.isFinite() || hi <= lo) return null
    return SmcPremiumRange(hi, lo)
}

fun computeSmc(candles: List<CandleData>, interval: String = ""): SmcData {
    if (candles.size < SMC_SWING_LOOKBACK * 2 + 3) return SmcData()
    val swings = detectSwings(candles)
    val events = detectStructure(candles, swings)
    val obZones = detectOrderBlocks(candles, events)
    val fvgZones = detectFvg(candles)
    val zones = (obZones + fvgZones).sortedBy { it.startIdx }
    return SmcData(
        swings = swings,
        events = events.takeLast(12),
        zones = zones,
        eqLevels = detectEqLevels(candles, swings),
        premium = detectPremium(candles),
        confluence = detectFvgConfluence(candles, fvgZones, interval),
        liquidity = detectLiquidity(candles, swings)
    )
}

// ponytail: pool BSL sobre maximos, SSL bajo minimos; sweep = SFP estricto misma vela
data class SmcLiqLevel(
    val price: Double,
    val idx: Int,
    val isBuySide: Boolean,
    val swept: Boolean,
    val sweepIdx: Int? = null
)

fun detectLiquidity(candles: List<CandleData>, swings: List<SmcSwing>): List<SmcLiqLevel> {
    if (candles.isEmpty() || swings.isEmpty()) return emptyList()
    // ponytail: 3 por lado y fusiona casi-iguales para no sobre-taguear
    val tol = atr(candles) * 0.25
    fun dedupe(list: List<SmcSwing>): List<SmcSwing> {
        val recent = list.takeLast(SMC_LIQ_SWINGS_PER_SIDE * 2)
        val kept = ArrayList<SmcSwing>()
        recent.forEach { sw ->
            if (kept.none { abs(it.price - sw.price) <= tol }) kept.add(sw)
            else {
                kept.removeAll { abs(it.price - sw.price) <= tol }
                kept.add(sw)
            }
        }
        return kept.takeLast(SMC_LIQ_SWINGS_PER_SIDE)
    }
    val out = ArrayList<SmcLiqLevel>()
    val highs = dedupe(swings.filter { it.isHigh })
    val lows = dedupe(swings.filter { !it.isHigh })
    (highs.map { it to true } + lows.map { it to false }).forEach { (sw, buySide) ->
        var swept: Int? = null
        var consumed = false
        var m = sw.idx + 1
        while (m < candles.size) {
            val c = candles[m]
            if (buySide) {
                if (c.high > sw.price && c.close < sw.price) {
                    swept = m
                    break
                }
                // ponytail: cierre aceptado arriba = breakout real, el pool se consume
                if (c.close > sw.price) {
                    consumed = true
                    break
                }
            } else {
                if (c.low < sw.price && c.close > sw.price) {
                    swept = m
                    break
                }
                if (c.close < sw.price) {
                    consumed = true
                    break
                }
            }
            m++
        }
        if (!consumed) out.add(SmcLiqLevel(sw.price, sw.idx, buySide, swept != null, swept))
    }
    return out.sortedBy { it.idx }
}

// ponytail: agrega N velas en una para simular TFs mayores sin red
fun aggregateByCount(candles: List<CandleData>, n: Int): List<CandleData> {
    if (n <= 1 || candles.isEmpty()) return candles
    val out = ArrayList<CandleData>((candles.size + n - 1) / n)
    var i = 0
    while (i < candles.size) {
        val end = min(i + n, candles.size)
        val first = candles[i]
        val last = candles[end - 1]
        var hi = first.high
        var lo = first.low
        var vol = 0.0
        var k = i
        while (k < end) {
            hi = max(hi, candles[k].high)
            lo = min(lo, candles[k].low)
            vol += candles[k].volume
            k++
        }
        out.add(
            CandleData(
                time = first.time,
                open = first.open,
                high = hi,
                low = lo,
                close = last.close,
                volume = vol
            )
        )
        i = end
    }
    return out
}

// ponytail: 15m x4 -> 1h, etiqueta corta para el chip gris
fun higherTfLabel(interval: String, factor: Int): String {
    val baseMs = when (interval.trim()) {
        "1m" -> 60_000L
        "5m" -> 300_000L
        "15m" -> 900_000L
        "30m" -> 1_800_000L
        "1h" -> 3_600_000L
        "2h" -> 7_200_000L
        "4h" -> 14_400_000L
        "6h" -> 21_600_000L
        "12h" -> 43_200_000L
        "1d", "3d", "5d" -> 86_400_000L
        "1w", "2w" -> 604_800_000L
        "1mo", "1M" -> 2_592_000_000L
        else -> 0L
    }
    if (baseMs <= 0L) return "HTF"
    val ms = baseMs * factor
    val mins = ms / 60_000L
    // ponytail: redondea al TF estandar cercano (80m -> 1h) para chips limpios
    val standards = listOf(1L, 5L, 15L, 30L, 60L, 120L, 240L, 360L, 720L, 1440L, 4320L, 10080L, 43200L)
    val near = standards.minByOrNull { abs(it - mins) } ?: mins
    return when {
        near < 60 -> "${near}m"
        near % 43200L == 0L -> "${near / 43200}mo"
        near % 10080L == 0L -> "${near / 10080}w"
        near % 1440L == 0L -> "${near / 1440}d"
        else -> "${near / 60}h"
    }
}

// ponytail: FVG de TFs mayores (x4, x16) solapados con tus zonas = bandas grises
fun detectFvgConfluence(candles: List<CandleData>, ownFvg: List<SmcZone>, interval: String): List<SmcConfluenceBand> {
    val mine = ownFvg.filter { it.kind == SmcZoneKind.FVG && !it.mitigated }
    if (mine.isEmpty() || candles.size < 32) return emptyList()
    val bands = ArrayList<SmcConfluenceBand>()
    listOf(4, 16).forEach { factor ->
        val agg = aggregateByCount(candles, factor)
        if (agg.size < 8) return@forEach
        val label = higherTfLabel(interval, factor)
        detectFvg(agg).forEach { z ->
            // ponytail: el precio es absoluto, no hay que mapear tiempo
            mine.forEach { m ->
                val top = min(z.top, m.top)
                val bottom = max(z.bottom, m.bottom)
                if (top > bottom) {
                    bands.add(SmcConfluenceBand(top, bottom, m.startIdx, m.endIdx, label))
                }
            }
        }
    }
    // ponytail: fusiona bandas que se pisan del mismo TF para no apilar grises
    val sorted = bands.sortedWith(compareBy({ it.tfLabel }, { it.bottom }, { it.top }))
    val merged = ArrayList<SmcConfluenceBand>()
    sorted.forEach { b ->
        val last = merged.lastOrNull()
        if (last != null && last.tfLabel == b.tfLabel && b.bottom <= last.top) {
            merged[merged.size - 1] = last.copy(
                top = max(last.top, b.top),
                bottom = min(last.bottom, b.bottom),
                startIdx = min(last.startIdx, b.startIdx),
                endIdx = max(last.endIdx, b.endIdx)
            )
        } else {
            merged.add(b)
        }
    }
    return merged.take(10)
}
