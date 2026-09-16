package com.defitracker.app.presentation.crypto_detail

// ponytail: un solo fibo activo por simbolo, anclado por tiempo para que sobreviva al cambio de intervalo
data class FibAnchor(
    val time: Long,
    val price: Double
)

data class FibDrawing(
    val start: FibAnchor,
    val end: FibAnchor
)

data class FibConfig(
    val colorHex: String = "#FFFFFF",
    val width: Float = 1f,
    val enabledLevels: Set<Float> = DEFAULT_FIB_LEVELS.toSet(),
    val hidden: Boolean = false
)

// ponytail: cada fibo con su propio estilo+estado, un overlay = dibujo + config
data class FibOverlay(
    val id: String,
    val start: FibAnchor,
    val end: FibAnchor,
    val colorHex: String = "#FFFFFF",
    val width: Float = 1f,
    val enabledLevels: Set<Float> = DEFAULT_FIB_LEVELS.toSet(),
    val hidden: Boolean = false,
    val locked: Boolean = false
) {
    fun levelsSorted(): List<Float> =
        enabledLevels.filter { it in ALL_FIB_LEVELS }.sorted()
}

const val MAX_FIBS_PER_SYMBOL = 10

val DEFAULT_FIB_LEVELS = listOf(0f, 0.236f, 0.382f, 0.5f, 0.618f, 0.786f, 1f)
val EXTRA_FIB_LEVELS = listOf(1.272f, 1.414f, 1.618f)
val ALL_FIB_LEVELS = DEFAULT_FIB_LEVELS + EXTRA_FIB_LEVELS
val FIB_WIDTH_OPTIONS = listOf(0.5f, 1f, 1.5f, 2f, 2.5f, 3f)

// ponytail: agnostic a direccion, sirve alcista y bajista
fun fibLevelPrice(start: Double, end: Double, ratio: Float): Double =
    start + (end - start) * ratio

// ponytail: timestamp -> indice mas cercano, asi el fibo se reubica solo al agregar temporalidades
fun timeToIndex(candles: List<CandleData>, time: Long): Int {
    if (candles.isEmpty()) return 0
    var lo = 0
    var hi = candles.size - 1
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (candles[mid].time < time) lo = mid + 1 else hi = mid
    }
    // ponytail: quedate con el vecino mas cercano, no solo el techo
    if (lo > 0 && kotlin.math.abs(candles[lo].time - time) > kotlin.math.abs(candles[lo - 1].time - time)) {
        return lo - 1
    }
    return lo.coerceIn(0, candles.size - 1)
}

fun FibConfig.levelsSorted(): List<Float> =
    enabledLevels.filter { it in ALL_FIB_LEVELS }.sorted()

fun fibWidthLabel(w: Float): String {
    val s = w.toString().trimEnd('0').trimEnd('.')
    return "${if (s.isEmpty()) "0" else s}px"
}

// ponytail: encoding manual sin librerias: id;sTime;sPrice;eTime;ePrice;color;width;lv1+lv2;hidden;locked
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
        if (o.hidden) "1" else "0",
        if (o.locked) "1" else "0"
    ).joinToString(";")
}

fun decodeFibOverlays(raw: String?): List<FibOverlay> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split("|").mapNotNull { entry ->
        val p = entry.split(";")
        if (p.size < 10) return@mapNotNull null
        val sTime = p[1].toLongOrNull() ?: return@mapNotNull null
        val sPrice = p[2].toDoubleOrNull() ?: return@mapNotNull null
        val eTime = p[3].toLongOrNull() ?: return@mapNotNull null
        val ePrice = p[4].toDoubleOrNull() ?: return@mapNotNull null
        FibOverlay(
            id = p[0].ifBlank { return@mapNotNull null },
            start = FibAnchor(sTime, sPrice),
            end = FibAnchor(eTime, ePrice),
            colorHex = p[5].ifBlank { "#FFFFFF" },
            width = p[6].toFloatOrNull()?.coerceIn(0.5f, 3f) ?: 1f,
            enabledLevels = p[7].split("+").mapNotNull { it.toFloatOrNull() }
                .filter { it in ALL_FIB_LEVELS }.toSet().ifEmpty { DEFAULT_FIB_LEVELS.toSet() },
            hidden = p[8] == "1",
            locked = p[9] == "1"
        )
    }.take(MAX_FIBS_PER_SYMBOL)
}
