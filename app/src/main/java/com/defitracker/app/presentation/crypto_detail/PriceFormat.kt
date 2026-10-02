package com.defitracker.app.presentation.crypto_detail

import kotlin.math.abs

// precision de costumbre: pares que tocaron sub-$1 conservan los ceros de su banda
fun formatPriceForChart(value: Double, extended: Boolean = false): String {
    if (value == 0.0) return "0.00"
    if (extended && abs(value) < 1.0) {
        return String.format(java.util.Locale.US, "%.6f", value)
    }
    if (extended && value >= 1.0 && value < 10.0) {
        return String.format(java.util.Locale.US, "%.4f", value)
    }
    if (abs(value) >= 1000.0) {
        return String.format(java.util.Locale.US, "%,.2f", value)
    }
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
    if (value == 0.0) return "0.00"
    if (extended && abs(value) < 1.0) {
        return String.format(java.util.Locale.US, "%.6f", value)
    }
    if (extended && value >= 1.0 && value < 10.0) {
        return String.format(java.util.Locale.US, "%.4f", value)
    }
    if (abs(value) >= 1000.0) {
        return String.format(java.util.Locale.US, "%,.2f", value)
    }
    val n = formatPriceForChart(refClose, extended).substringAfter('.', "").length.coerceIn(2, 8)
    return String.format(java.util.Locale.US, "%.${n}f", value)
}
