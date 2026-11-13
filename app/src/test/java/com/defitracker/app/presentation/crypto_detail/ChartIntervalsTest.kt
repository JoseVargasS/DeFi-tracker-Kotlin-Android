package com.defitracker.app.presentation.crypto_detail

import com.defitracker.app.core.Brokers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY_MS = 86_400_000L
private const val FIVE_MIN_MS = 300_000L
// 2024-03-01T00:00:00Z: alineado a dia y a bloque de 5 dias
private const val BASE = 1_699_920_000_000L

// El chart y el escaner de alertas piden las mismas velas: el mapeo de TF vive en un solo lugar.
class ChartIntervalsTest {

    @Test
    fun `cada fuente pide el intervalo en su formato`() {
        assertEquals("5m", intervalForSource(Brokers.MEXC, "5m"))
        assertEquals("60", intervalForSource(Brokers.TV, "1h"))
        assertEquals("5m", intervalForSource(Brokers.BINANCE, "5m"))
        assertEquals("5m", intervalForSource(Brokers.BITUNIX, "5m"))
        assertEquals("5m", intervalForSource(Brokers.BINGX, "5m"))
    }

    @Test
    fun `los TFs de calendario se piden como los de Binance`() {
        assertEquals("1d", intervalForSource(Brokers.BINANCE, "5d"))
        assertEquals("1w", intervalForSource(Brokers.BITUNIX, "2w"))
        assertEquals("1M", intervalForSource(Brokers.BINGX, "1mo"))
    }

    @Test
    fun `MEXC no agrega aca y el resto si`() {
        val candles = (0 until 10).map {
            CandleData(time = BASE + it * DAY_MS, open = 1.0, high = 2.0, low = 0.5, close = 1.5, volume = 10.0)
        }
        assertEquals(10, candles.forSourceInterval(Brokers.MEXC, "5d").size)
        assertEquals(2, candles.forSourceInterval(Brokers.BITUNIX, "5d").size)
        assertEquals(2, candles.forSourceInterval(Brokers.BINGX, "5d").size)
    }

    @Test
    fun `el agregado de 5 dias junta open, high, low, close y volumen`() {
        val candles = (0 until 10).map { i ->
            CandleData(
                time = BASE + i * DAY_MS,
                open = i.toDouble(),
                high = 100.0 + i,
                low = -i.toDouble(),
                close = i + 0.5,
                volume = 3.0
            )
        }
        val buckets = candles.forSourceInterval(Brokers.BINANCE, "5d")
        assertEquals(2, buckets.size)
        val first = buckets.first()
        assertEquals(BASE, first.time)
        assertEquals(0.0, first.open, 1e-9)
        assertEquals(104.0, first.high, 1e-9)
        assertEquals(-4.0, first.low, 1e-9)
        assertEquals(4.5, first.close, 1e-9)
        assertEquals(15.0, first.volume, 1e-9)
    }

    @Test
    fun `la vela en curso se llena con el precio cuando la fuente no la publica`() {
        val candles = (0 until 3).map {
            CandleData(time = BASE + it * FIVE_MIN_MS, open = 10.0, high = 10.0, low = 10.0, close = 10.0)
        }
        val now = candles.last().time + FIVE_MIN_MS + 60_000L
        val withLive = candles.withLiveCandlePrice("5m", price = 12.0, nowMs = now)
        assertEquals(4, withLive.size)
        assertEquals(candles.last().time + FIVE_MIN_MS, withLive.last().time)
        assertEquals(12.0, withLive.last().close, 1e-9)
        assertEquals(12.0, withLive.last().high, 1e-9)
        assertEquals(10.0, withLive.last().low, 1e-9)
    }

    @Test
    fun `si la vela en curso ya viene publicada no se toca`() {
        val candles = (0 until 3).map {
            CandleData(time = BASE + it * FIVE_MIN_MS, open = 10.0, high = 10.0, low = 10.0, close = 10.0)
        }
        val now = candles.last().time + 60_000L
        val withLive = candles.withLiveCandlePrice("5m", price = 12.0, nowMs = now)
        assertEquals(3, withLive.size)
        assertTrue(withLive.last().close == 10.0)
    }
}
