package com.defitracker.app.presentation.crypto_detail

import com.defitracker.app.core.Timeframes
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// SMC estilo LuxAlgo, funciones puras sobre velas (testeables sin Android)

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
    val endIdx: Int, // mitigacion o ultima vela (se extiende solo)
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

// banda gris donde el FVG de un TF mayor pisa tu zona del TF actual
data class SmcConfluenceBand(
    val top: Double,
    val bottom: Double,
    val startIdx: Int,
    val endIdx: Int,
    val tfs: Set<String> = emptySet(),
    val hasOb: Boolean = false
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

// fractal N por lado, confirma con lag como en LuxAlgo
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

// rompe a favor = BOS, en contra = CHoCH con flip de sesgo
fun detectStructure(candles: List<CandleData>, swings: List<SmcSwing>): List<SmcEvent> {
    if (candles.isEmpty() || swings.isEmpty()) return emptyList()
    val events = ArrayList<SmcEvent>()
    var trend = 0 // 1 alcista, -1 bajista
    var refHigh: SmcSwing? = null
    var refLow: SmcSwing? = null
    var s = 0
    // arranca con el primer par alto/bajo para tener referencias
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
        // avanza referencias con swings ya confirmados a esta altura.
        // un swing que el precio ya supero no es nivel rompible: si no, salen BOS repetidos
        while (s < swings.size && swings[s].idx <= i) {
            val sw = swings[s]
            if (sw.isHigh && sw.price > close) refHigh = sw
            if (!sw.isHigh && sw.price < close) refLow = sw
            s++
        }
        val rh = refHigh
        val rl = refLow
        if (rh != null && close > rh.price) {
            val kind = if (trend == 1) SmcEventKind.BOS else SmcEventKind.CHOCH
            events.add(SmcEvent(i, rh.idx, rh.price, kind, true))
            trend = 1
            // Buscamos el Higher Low que origino el quiebre para proteger la estructura alcista
            var minLow = candles[rh.idx].low
            var minIdx = rh.idx
            for (k in rh.idx..i) {
                if (candles[k].low < minLow) {
                    minLow = candles[k].low
                    minIdx = k
                }
            }
            refLow = SmcSwing(minIdx, minLow, false)
            // El nivel quebrado queda consumido; se espera al siguiente swing high confirmado
            refHigh = null
            i++
            continue
        }
        if (rl != null && close < rl.price) {
            val kind = if (trend == -1) SmcEventKind.BOS else SmcEventKind.CHOCH
            events.add(SmcEvent(i, rl.idx, rl.price, kind, false))
            trend = -1
            // Buscamos el Lower High que origino el quiebre para proteger la estructura bajista
            var maxHigh = candles[rl.idx].high
            var maxIdx = rl.idx
            for (k in rl.idx..i) {
                if (candles[k].high > maxHigh) {
                    maxHigh = candles[k].high
                    maxIdx = k
                }
            }
            refHigh = SmcSwing(maxIdx, maxHigh, true)
            // El nivel quebrado queda consumido; se espera al siguiente swing low confirmado
            refLow = null
            i++
            continue
        }
        i++
    }
    return events
}

// ultima vela opuesta antes del impulso que rompio
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
        // mitigacion evaluada a partir de que se completa el rompimiento
        var end = candles.size - 1
        var mitigated = false
        var m = ev.breakIdx + 1
        while (m < candles.size) {
            val cl = candles[m].close
            // un OB se invalida con cierre fuera del bloque, no con una mecha que asusta
            val violated = if (ev.bullish) cl < c.low else cl > c.high
            if (violated) {
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
    // priorizamos las zonas activas y las mitigadas mas recientes
    val bullActive = out.filter { it.bullish && !it.mitigated }
    val bullMitigated = out.filter { it.bullish && it.mitigated }
    val bull = (bullMitigated.takeLast(max(0, SMC_MAX_ZONES_PER_SIDE - bullActive.size)) + bullActive).takeLast(SMC_MAX_ZONES_PER_SIDE)

    val bearActive = out.filter { !it.bullish && !it.mitigated }
    val bearMitigated = out.filter { !it.bullish && it.mitigated }
    val bear = (bearMitigated.takeLast(max(0, SMC_MAX_ZONES_PER_SIDE - bearActive.size)) + bearActive).takeLast(SMC_MAX_ZONES_PER_SIDE)

    return (bull + bear).sortedBy { it.startIdx }
}

// imbalance de 3 velas con umbral ATR (sin threshold todo nace mitigado)
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
                // FVG alcista: se mitiga cuando el precio cubre todo el gap
                if (candles[m].low <= a.high || candles[m].close < a.high) {
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
                // FVG bajista: se mitiga cuando el precio cubre todo el gap
                if (candles[m].high >= a.low || candles[m].close > a.low) {
                    end = m
                    mitigated = true
                    break
                }
                m++
            }
            out.add(SmcZone(i - 2, end, a.low, c.high, false, SmcZoneKind.FVG, mitigated))
        }
    }
    // priorizamos las zonas activas y las mitigadas mas recientes
    val bullActive = out.filter { it.bullish && !it.mitigated }
    val bullMitigated = out.filter { it.bullish && it.mitigated }
    val bull = (bullMitigated.takeLast(max(0, SMC_MAX_ZONES_PER_SIDE - bullActive.size)) + bullActive).takeLast(SMC_MAX_ZONES_PER_SIDE)

    val bearActive = out.filter { !it.bullish && !it.mitigated }
    val bearMitigated = out.filter { !it.bullish && it.mitigated }
    val bear = (bearMitigated.takeLast(max(0, SMC_MAX_ZONES_PER_SIDE - bearActive.size)) + bearActive).takeLast(SMC_MAX_ZONES_PER_SIDE)

    return (bull + bear).sortedBy { it.startIdx }
}

// dos swings casi iguales = liquidez
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
        confluence = detectFvgConfluence(candles, fvgZones, interval, obZones),
        liquidity = detectLiquidity(candles, swings)
    )
}

// pool BSL sobre maximos, SSL bajo minimos; sweep = SFP estricto misma vela
data class SmcLiqLevel(
    val price: Double,
    val idx: Int,
    val isBuySide: Boolean,
    val swept: Boolean,
    val sweepIdx: Int? = null
)

fun detectLiquidity(candles: List<CandleData>, swings: List<SmcSwing>): List<SmcLiqLevel> {
    if (candles.isEmpty() || swings.isEmpty()) return emptyList()
    // 3 por lado y fusiona casi-iguales para no sobre-taguear
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
                // cierre aceptado arriba = breakout real, el pool se consume
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

// agrega N velas en una para simular TFs mayores sin red
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

// 15m x4 -> 1h, etiqueta corta para el chip gris
fun higherTfLabel(interval: String, factor: Int): String {
    val baseMs = Timeframes.higherTfBaseDurationMs(interval)
    if (baseMs <= 0L) return "HTF"
    val ms = baseMs * factor
    val mins = ms / 60_000L
    // redondea al TF estandar cercano (80m -> 1h) para chips limpios
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

// FVG de TFs mayores (x4, x16) solapados con tus zonas = bandas grises
fun detectFvgConfluence(
    candles: List<CandleData>,
    ownFvg: List<SmcZone>,
    interval: String,
    ownOb: List<SmcZone> = emptyList()
): List<SmcConfluenceBand> {
    val mine = ownFvg.filter { it.kind == SmcZoneKind.FVG && !it.mitigated }
    if (mine.isEmpty() || candles.size < 32) return emptyList()
    val bands = ArrayList<SmcConfluenceBand>()
    listOf(4, 16).forEach { factor ->
        val agg = aggregateByCount(candles, factor)
        if (agg.size < 8) return@forEach
        val label = higherTfLabel(interval, factor)
        detectFvg(agg).forEach { z ->
            // el precio es absoluto, no hay que mapear tiempo
            mine.forEach { m ->
                val top = min(z.top, m.top)
                val bottom = max(z.bottom, m.bottom)
                if (top > bottom) {
                    bands.add(SmcConfluenceBand(top, bottom, m.startIdx, m.endIdx, setOf(label)))
                }
            }
        }
    }
    return mergeConfluenceBands(markObOverlap(bands, ownOb))
}
// FVG reales de TFs superiores (mismo exchange, por red) solapados con tus FVG = bandas grises.
// TFs importantes; bajo 1h se salta 12h para que entre 1d en el tope de 5.
private val SMC_CONFLUENCE_CANON = Timeframes.SMC_CONFLUENCE_KEYS

fun higherConfluenceTfs(current: String): List<String> {
    val idx = SMC_CONFLUENCE_CANON.indexOf(current.trim())
    if (idx < 0) return emptyList()
    val skip12 = idx < SMC_CONFLUENCE_CANON.indexOf("1h")
    return SMC_CONFLUENCE_CANON.subList(idx + 1, SMC_CONFLUENCE_CANON.size)
        .filter { !(skip12 && it == "12h") }
        .take(5)
}

fun detectFvgConfluenceMultiTF(
    ownFvg: List<SmcZone>,
    higher: Map<String, List<CandleData>>
): List<SmcConfluenceBand> {
    val mine = ownFvg.filter { it.kind == SmcZoneKind.FVG && !it.mitigated }
    if (mine.isEmpty() || higher.isEmpty()) return emptyList()
    val bands = ArrayList<SmcConfluenceBand>()
    higher.forEach { (label, candles) ->
        if (candles.size < 8) return@forEach
        detectFvg(candles).filter { !it.mitigated }.forEach { z ->
            // el precio es absoluto, no hay que mapear tiempo
            mine.forEach { m ->
                val top = min(z.top, m.top)
                val bottom = max(z.bottom, m.bottom)
                if (top > bottom) {
                    bands.add(SmcConfluenceBand(top, bottom, m.startIdx, m.endIdx, setOf(label)))
                }
            }
        }
    }
    return mergeConfluenceBands(markObOverlap(bands, ownFvg))
}

// marca las bandas que ademas pisan un OB vigente
private fun markObOverlap(bands: List<SmcConfluenceBand>, zones: List<SmcZone>): List<SmcConfluenceBand> {
    val obs = zones.filter { it.kind == SmcZoneKind.ORDER_BLOCK && !it.mitigated }
    if (obs.isEmpty()) return bands
    return bands.map { b ->
        if (!b.hasOb && obs.any { o -> min(o.top, b.top) > max(o.bottom, b.bottom) }) b.copy(hasOb = true) else b
    }
}

// fusiona bandas que se pisan sin importar el TF, uniendo etiquetas
private fun mergeConfluenceBands(bands: List<SmcConfluenceBand>): List<SmcConfluenceBand> {
    val sorted = bands.sortedWith(compareBy({ it.bottom }, { it.top }))
    val merged = ArrayList<SmcConfluenceBand>()
    sorted.forEach { b ->
        val last = merged.lastOrNull()
        if (last != null && b.bottom <= last.top) {
            merged[merged.size - 1] = last.copy(
                top = max(last.top, b.top),
                bottom = min(last.bottom, b.bottom),
                startIdx = min(last.startIdx, b.startIdx),
                endIdx = max(last.endIdx, b.endIdx),
                tfs = last.tfs + b.tfs,
                hasOb = last.hasOb || b.hasOb
            )
        } else {
            merged.add(b)
        }
    }
    return merged.take(10)
}

// ordena TFs de menor a mayor para el tag compuesto
private val SMC_TF_ORDER = Timeframes.KEYS

fun sortTfsForTag(tfs: Set<String>): List<String> =
    tfs.sortedBy { SMC_TF_ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
