package com.defitracker.app.presentation.crypto_detail

import com.defitracker.app.domain.model.LiveQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El socket manda en el precio del detalle, pero solo si su ultimo tick es reciente:
 * sin este guardian, un socket caido congelaria la vela sin avisar nada.
 */
class LivePriceFeedTest {

    private fun quote(price: Double = 100.0, at: Long = 100_000L) = LiveQuote(
        price = "100.00",
        priceChangePercent = "1.00",
        isPositive = true,
        quoteVolume = 1_000.0,
        priceValue = price,
        receivedAtMs = at
    )

    @Test
    fun `un tick reciente viene del socket`() {
        assertTrue(isSocketPriceFresh(quote(at = 100_000L), nowMs = 105_000L))
    }

    @Test
    fun `un tick viejo ya no sirve y el precio se congela`() {
        assertFalse(
            isSocketPriceFresh(
                quote(at = 100_000L),
                nowMs = 100_000L + LIVE_FRESH_WINDOW_MS + 1L
            )
        )
    }

    @Test
    fun `en el borde de la ventana todavia vale`() {
        assertTrue(
            isSocketPriceFresh(
                quote(at = 100_000L),
                nowMs = 100_000L + LIVE_FRESH_WINDOW_MS
            )
        )
    }

    @Test
    fun `una cotizacion vacia o invalida no pasa el guardian`() {
        assertFalse(isSocketPriceFresh(null, nowMs = 100_000L))
        assertFalse(isSocketPriceFresh(quote(price = 0.0, at = 100_000L), nowMs = 100_000L))
        assertFalse(isSocketPriceFresh(quote(at = 0L), nowMs = 100_000L))
    }

    @Test
    fun `el REST se pega al principio y despues de que pase el gap`() {
        assertTrue(shouldFetchRest(0L, nowMs = 1_000L, gapMs = 30_000L))
        assertFalse(shouldFetchRest(1_000L, nowMs = 10_000L, gapMs = 30_000L))
        assertTrue(shouldFetchRest(1_000L, nowMs = 31_000L, gapMs = 30_000L))
    }

    @Test
    fun `el volumen del socket se pasa de moneda quote a moneda base`() {
        val delta = socketBaseVolumeDelta(
            quoteVolumeNow = 1_100.0,
            quoteVolumePrev = 1_000.0,
            price = 50.0
        )
        assertEquals(2.0, delta, 1e-9)
    }

    @Test
    fun `un volumen que baja o no tiene base no cuenta delta`() {
        assertEquals(0.0, socketBaseVolumeDelta(900.0, 1_000.0, 50.0), 1e-9)
        assertEquals(0.0, socketBaseVolumeDelta(1_100.0, Double.NaN, 50.0), 1e-9)
        assertEquals(0.0, socketBaseVolumeDelta(1_100.0, 1_000.0, 0.0), 1e-9)
    }
}
