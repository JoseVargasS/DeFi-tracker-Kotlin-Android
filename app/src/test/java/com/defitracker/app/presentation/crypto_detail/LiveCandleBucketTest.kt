package com.defitracker.app.presentation.crypto_detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

// Bitunix publica solo velas cerradas: el bucket en curso se sintetiza en la app.
// Sin el, el tick la toma por cerrada y el chart se recarga en loop.
class LiveCandleBucketTest {

    private fun candle(time: Long, close: Double = 100.0) = CandleData(
        time = time,
        open = close,
        high = close,
        low = close,
        close = close,
        volume = 7.0,
        takerBuyVol = 3.0
    )

    @Test
    fun `bitunix closed-only history gets the current minute flat`() {
        val last = 1_700_000_040_000L
        val rows = listOf(candle(last - 60_000, 99.0), candle(last, 101.0))

        val out = rows.withLiveCandle("1m", nowMs = last + 60_000 + 5_000)

        assertEquals(3, out.size)
        val live = out.last()
        assertEquals(last + 60_000, live.time)
        assertEquals(101.0, live.close, 0.0)
        assertEquals(101.0, live.open, 0.0)
        assertEquals(0.0, live.volume, 0.0)
    }

    @Test
    fun `binance live candle already in the list is left untouched`() {
        val last = 1_700_000_040_000L
        val rows = listOf(candle(last - 60_000), candle(last))

        val out = rows.withLiveCandle("1m", nowMs = last + 5_000)

        assertSame(rows, out)
    }

    @Test
    fun `weekly series keeps its monday phase`() {
        val last = 1_793_577_600_000L // lunes
        val rows = listOf(candle(last - 604_800_000), candle(last))

        val out = rows.withLiveCandle("1w", nowMs = last + 604_800_000 + 1_000)

        val live = out.last()
        assertEquals(last + 604_800_000, live.time)
        assertEquals(0L, live.time % 86_400_000L)
    }

    @Test
    fun `month uses the utc month start instead of 30 days`() {
        val oct = 1_790_812_800_000L
        val sep = 1_788_220_800_000L
        val rows = listOf(candle(sep), candle(oct))
        // 11 nov 2026: el bucket en curso empieza el 1 nov, no 30 dias despues del 1 oct
        val now = 1_794_355_200_000L

        val live = rows.withLiveCandle("1mo", nowMs = now).last()

        assertEquals(1_793_491_200_000L, live.time)
    }

    @Test
    fun `aggregated bucket already current is a no-op`() {
        val last = 1_700_000_040_000L - 1_700_000_040_000L % 432_000_000L
        val rows = listOf(candle(last - 432_000_000), candle(last))

        val out = rows.withLiveCandle("5d", nowMs = last + 60_000)

        assertSame(rows, out)
    }
}
