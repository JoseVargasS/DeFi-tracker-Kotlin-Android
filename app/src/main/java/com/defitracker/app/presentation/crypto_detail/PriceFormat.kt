package com.defitracker.app.presentation.crypto_detail

import kotlin.math.abs

// Tres salidas conviven a proposito: chart adapta precision y compacta B/T,
// Pulse recorta ceros para frases y el repositorio conserva decimales fijos para DTOs.

// bandas compartidas: 0, sub-$1, $1-10, miles y billones/trillones estilo TV (796.75B).
// Solo B/T son compactos: ningun precio normal llega a 1M, asi los pares quedan intactos.
private fun formatAdaptive(value: Double, extended: Boolean): String? = when {
    value == 0.0 -> "0.00"
    abs(value) >= 1_000_000_000_000.0 -> String.format(java.util.Locale.US, "%.2fT", value / 1_000_000_000_000.0)
    abs(value) >= 1_000_000_000.0 -> String.format(java.util.Locale.US, "%.2fB", value / 1_000_000_000.0)
    extended && abs(value) < 1.0 -> String.format(java.util.Locale.US, "%.6f", value)
    extended && value >= 1.0 && value < 10.0 -> String.format(java.util.Locale.US, "%.4f", value)
    abs(value) >= 1000.0 -> String.format(java.util.Locale.US, "%,.2f", value)
    else -> null
}

// precision de costumbre: pares que tocaron sub-$1 conservan los ceros de su banda
fun formatPriceForChart(value: Double, extended: Boolean = false): String {
    formatAdaptive(value, extended)?.let { return it }
    // hasta 8 decimales recortando ceros, pa' seguir precios chicos sin ruido
    var s = String.format(java.util.Locale.US, "%.8f", value)
    s = s.trimEnd('0').trimEnd('.')
    val frac = s.substringAfter('.', "")
    if (frac.length < 2) {
        s = String.format(java.util.Locale.US, "%.2f", value)
    }
    return s
}

// eje/tags/fibo con los mismos decimales del precio actual, sin colas largas
fun formatAxisPrice(value: Double, refClose: Double, extended: Boolean = false): String {
    formatAdaptive(value, extended)?.let { return it }
    val n = formatPriceForChart(refClose, extended).substringAfter('.', "").length.coerceIn(2, 8)
    return String.format(java.util.Locale.US, "%.${n}f", value)
}

// volumen 24h compacto pa'l header (1.24B, 850.50M, 12.30K)
fun formatCompactVol(value: Double): String {
    val v = abs(value)
    return when {
        v >= 1_000_000_000_000.0 -> String.format(java.util.Locale.US, "%.2fT", value / 1_000_000_000_000.0)
        v >= 1_000_000_000.0 -> String.format(java.util.Locale.US, "%.2fB", value / 1_000_000_000.0)
        v >= 1_000_000.0 -> String.format(java.util.Locale.US, "%.2fM", value / 1_000_000.0)
        v >= 1_000.0 -> String.format(java.util.Locale.US, "%.2fK", value / 1_000.0)
        else -> String.format(java.util.Locale.US, "%.2f", value)
    }
}

fun fmtPulsePrice(value: Double): String {
    if (!value.isFinite()) return "--"
    val formatted = when {
        value >= 1000.0 -> String.format(java.util.Locale.US, "%,.1f", value)
        value >= 1.0 -> String.format(java.util.Locale.US, "%.4f", value)
        else -> String.format(java.util.Locale.US, "%.6f", value)
    }
    return if (formatted.contains('.')) formatted.trimEnd('0').trimEnd('.') else formatted
}

fun formatRepositoryPrice(price: Double): String = when {
    price < 1.0 -> String.format(java.util.Locale.US, "%.6f", price)
    price < 10.0 -> String.format(java.util.Locale.US, "%.4f", price)
    else -> String.format(java.util.Locale.US, "%.2f", price)
}
