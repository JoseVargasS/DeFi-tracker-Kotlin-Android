package com.defitracker.app.presentation.crypto_detail

// posicion LONG/SHORT estilo TV: entrada + SL + TP con cantidad y leverage.
// Anclas por tiempo como fibos/dibujos: sobreviven al cambio de intervalo.

// verde/rojo los que ya usa la app
const val POS_PROFIT_HEX = "#1ECB81"
const val POS_LOSS_HEX = "#F6465D"

const val MAX_POSITIONS_PER_SYMBOL = 20
const val DEFAULT_POSITION_QTY_USDT = 750.0
const val DEFAULT_POSITION_LEVERAGE = 5
const val DEFAULT_POSITION_SL_PCT = 0.015
const val DEFAULT_POSITION_TP_PCT = 0.015
const val DEFAULT_POSITION_PROFIT_ALPHA = 0.26f
const val DEFAULT_POSITION_LOSS_ALPHA = 0.37f

data class PositionOverlay(
    val id: String,
    val long: Boolean,
    val startTime: Long,
    val endTime: Long,
    val entry: Double,
    val sl: Double,
    val tp: Double,
    val qtyUsdt: Double = DEFAULT_POSITION_QTY_USDT,
    val leverage: Int = DEFAULT_POSITION_LEVERAGE,
    // transparencia por color, estilo TV
    val profitAlpha: Float = DEFAULT_POSITION_PROFIT_ALPHA,
    val lossAlpha: Float = DEFAULT_POSITION_LOSS_ALPHA
) {
    // linea ganadora arriba en LONG, abajo en SHORT
    val profitPrice: Double get() = tp
    val lossPrice: Double get() = sl
}

// % del movimiento apalancado + monto en USDT, pa' las etiquetas
fun positionLineStats(entry: Double, line: Double, qtyUsdt: Double, leverage: Int): Pair<Double, Double> {
    if (entry <= 0) return 0.0 to 0.0
    val movePct = (line - entry) / entry * 100.0
    val levPct = movePct * leverage
    return levPct to qtyUsdt * levPct / 100.0
}

// parsea lo que muestra el sheet: decimales, notacion E y sufijos B/T/M/K
fun parsePriceInput(txt: String): Double? {
    val clean = txt.trim().uppercase(java.util.Locale.US)
    if (clean.isEmpty()) return null
    val mult = when {
        clean.endsWith("B") -> 1_000_000_000.0
        clean.endsWith("T") -> 1_000_000_000_000.0
        clean.endsWith("M") -> 1_000_000.0
        clean.endsWith("K") -> 1_000.0
        else -> 1.0
    }
    val num = if (mult == 1.0) clean else clean.dropLast(1)
    return num.toDoubleOrNull()?.times(mult)
}

// encoding manual sin librerias:
// id;long01;startTime;endTime;entry;sl;tp;qty;leverage;profitAlpha;lossAlpha
fun encodePositionOverlays(overlays: List<PositionOverlay>): String = overlays.joinToString("|") { o ->
    listOf(
        o.id,
        bool01(o.long),
        o.startTime.toString(),
        o.endTime.toString(),
        o.entry.toString(),
        o.sl.toString(),
        o.tp.toString(),
        o.qtyUsdt.toString(),
        o.leverage.toString(),
        o.profitAlpha.toString(),
        o.lossAlpha.toString()
    ).joinToString(";")
}

fun decodePositionOverlays(raw: String?): List<PositionOverlay> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split("|").mapNotNull { entry ->
        val p = entry.split(";")
        if (p.size < 11) return@mapNotNull null
        PositionOverlay(
            id = p[0].ifBlank { return@mapNotNull null },
            long = p.flag(1),
            startTime = p.reqLong(2) ?: return@mapNotNull null,
            endTime = p.reqLong(3) ?: return@mapNotNull null,
            entry = p.reqDouble(4) ?: return@mapNotNull null,
            sl = p.reqDouble(5) ?: return@mapNotNull null,
            tp = p.reqDouble(6) ?: return@mapNotNull null,
            qtyUsdt = p.reqDouble(7) ?: DEFAULT_POSITION_QTY_USDT,
            leverage = p[8].toIntOrNull()?.coerceIn(1, 200) ?: DEFAULT_POSITION_LEVERAGE,
            profitAlpha = p[9].toFloatOrNull()?.coerceIn(0f, 1f) ?: DEFAULT_POSITION_PROFIT_ALPHA,
            lossAlpha = p[10].toFloatOrNull()?.coerceIn(0f, 1f) ?: DEFAULT_POSITION_LOSS_ALPHA
        )
    }.take(MAX_POSITIONS_PER_SYMBOL)
}
