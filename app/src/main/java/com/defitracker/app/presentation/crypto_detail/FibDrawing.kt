package com.defitracker.app.presentation.crypto_detail

import com.defitracker.app.core.Timeframes
import com.defitracker.app.ui.theme.ChartColors

// anclas por tiempo para que los dibujos sobrevivan al cambio de intervalo
data class FibAnchor(
    val time: Long,
    val price: Double
)

data class FibConfig(
    val colorHex: String = ChartColors.WHITE_HEX,
    val width: Float = 1f,
    val enabledLevels: Set<Float> = DEFAULT_FIB_LEVELS.toSet(),
    val hidden: Boolean = false
)

// cada fibo con su propio estilo+estado, un overlay = dibujo + config
data class FibOverlay(
    val id: String,
    val start: FibAnchor,
    val end: FibAnchor,
    val colorHex: String = ChartColors.WHITE_HEX,
    val width: Float = 1f,
    val enabledLevels: Set<Float> = DEFAULT_FIB_LEVELS.toSet(),
    val hidden: Boolean = false,
    val locked: Boolean = false
) {
    fun levelsSorted(): List<Float> = enabledLevels.sortedLevels()
}

const val MAX_FIBS_PER_SYMBOL = 10

val DEFAULT_FIB_LEVELS = listOf(0f, 0.236f, 0.382f, 0.5f, 0.618f, 0.786f, 1f)
val EXTRA_FIB_LEVELS = listOf(1.272f, 1.414f, 1.618f)
val ALL_FIB_LEVELS = DEFAULT_FIB_LEVELS + EXTRA_FIB_LEVELS
val FIB_WIDTH_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f)

fun maWidthLabel(w: Float): String =
    if (w == 0.5f) "0.5x" else fibWidthLabel(w).removeSuffix("px")

// agnostic a direccion, sirve alcista y bajista
fun fibLevelPrice(start: Double, end: Double, ratio: Float): Double =
    start + (end - start) * ratio

// duracion de cada vela en ms, compartida por chart, ViewModel y worker de alertas
fun candleDurationMs(interval: String): Long = Timeframes.durationMs(interval)

// timestamp -> indice mas cercano, asi el fibo se reubica solo al agregar temporalidades
fun timeToIndex(candles: List<CandleData>, time: Long): Int {
    if (candles.isEmpty()) return 0
    var lo = 0
    var hi = candles.size - 1
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (candles[mid].time < time) lo = mid + 1 else hi = mid
    }
    // quedate con el vecino mas cercano, no solo el techo
    if (lo > 0 && kotlin.math.abs(candles[lo].time - time) > kotlin.math.abs(candles[lo - 1].time - time)) {
        return lo - 1
    }
    return lo.coerceIn(0, candles.size - 1)
}

private fun Set<Float>.sortedLevels(): List<Float> =
    filter { it in ALL_FIB_LEVELS }.sorted()

fun FibConfig.levelsSorted(): List<Float> = enabledLevels.sortedLevels()

fun fibWidthLabel(w: Float): String {
    val s = w.toString().trimEnd('0').trimEnd('.')
    return "${if (s.isEmpty()) "0" else s}px"
}

// pedacitos que los 3 codecs (fibos, dibujos, MAs) repiten igualito
internal fun bool01(b: Boolean) = if (b) "1" else "0"
internal fun List<String>.flag(i: Int) = getOrNull(i) == "1"
internal fun List<String>.reqLong(i: Int) = getOrNull(i)?.takeIf { it.isNotBlank() }?.toLongOrNull()
internal fun List<String>.reqDouble(i: Int) = getOrNull(i)?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
internal fun List<String>.reqWidth(i: Int) = getOrNull(i)?.toFloatOrNull()?.coerceIn(0.5f, 3f) ?: 1f

// encoding manual sin librerias: id;sTime;sPrice;eTime;ePrice;color;width;lv1+lv2;hidden;locked
fun encodeFibOverlays(overlays: List<FibOverlay>): String = overlays.joinToString("|") { o ->
    listOf(
        o.id,
        o.start.time.toString(),
        o.start.price.toString(),
        o.end.time.toString(),
        o.end.price.toString(),
        o.colorHex,
        o.width.toString(),
        o.enabledLevels.sorted().joinToString("+"),
        bool01(o.hidden),
        bool01(o.locked)
    ).joinToString(";")
}

fun decodeFibOverlays(raw: String?): List<FibOverlay> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split("|").mapNotNull { entry ->
        val p = entry.split(";")
        if (p.size < 10) return@mapNotNull null
        val sTime = p.reqLong(1) ?: return@mapNotNull null
        val sPrice = p.reqDouble(2) ?: return@mapNotNull null
        val eTime = p.reqLong(3) ?: return@mapNotNull null
        val ePrice = p.reqDouble(4) ?: return@mapNotNull null
        FibOverlay(
            id = p[0].ifBlank { return@mapNotNull null },
            start = FibAnchor(sTime, sPrice),
            end = FibAnchor(eTime, ePrice),
            colorHex = p[5].ifBlank { ChartColors.WHITE_HEX },
            width = p.reqWidth(6),
            enabledLevels = p[7].split("+").mapNotNull { it.toFloatOrNull() }
                .filter { it in ALL_FIB_LEVELS }.toSet().ifEmpty { DEFAULT_FIB_LEVELS.toSet() },
            hidden = p.flag(8),
            locked = p.flag(9)
        )
    }.take(MAX_FIBS_PER_SYMBOL)
}
