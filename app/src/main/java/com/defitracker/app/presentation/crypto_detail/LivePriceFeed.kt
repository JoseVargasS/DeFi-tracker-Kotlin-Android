package com.defitracker.app.presentation.crypto_detail

import com.defitracker.app.domain.model.LiveQuote

// ventana de frescura del socket: si no entra un tick en este rato, el precio queda congelado
const val LIVE_FRESH_WINDOW_MS = 15_000L

/**
 * El socket manda en el precio, pero solo si su ultimo tick es reciente: un socket
 * muerto dejaria la vela congelada en silencio y nadie se enteraria.
 */
fun isSocketPriceFresh(
    quote: LiveQuote?,
    nowMs: Long,
    freshWindowMs: Long = LIVE_FRESH_WINDOW_MS
): Boolean {
    if (quote == null) return false
    val price = quote.priceValue
    if (!price.isFinite() || price <= 0.0) return false
    val receivedAt = quote.receivedAtMs
    if (receivedAt <= 0L) return false
    val age = nowMs - receivedAt
    return age in 0..freshWindowMs
}

/**
 * True si ya toca pegarle al REST (H/L/%, volumen de 24h y el respaldo si el socket muere).
 */
fun shouldFetchRest(lastRestAtMs: Long, nowMs: Long, gapMs: Long): Boolean =
    lastRestAtMs <= 0L || nowMs - lastRestAtMs >= gapMs

/**
 * El socket trae el volumen de 24h en moneda quote; el chart usa volumen en moneda base:
 * el delta se divide por el precio del tick.
 */
fun socketBaseVolumeDelta(quoteVolumeNow: Double, quoteVolumePrev: Double, price: Double): Double {
    if (!price.isFinite() || price <= 0.0) return 0.0
    if (!quoteVolumeNow.isFinite() || !quoteVolumePrev.isFinite()) return 0.0
    val delta = quoteVolumeNow - quoteVolumePrev
    return if (delta > 0.0) delta / price else 0.0
}
