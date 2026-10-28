package com.defitracker.app.presentation.crypto_detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoDetailTimeUtilsTest {
    private val candles = listOf(
        testCandle(0, 1.0, 1.0, 1.0, 1.0),
        testCandle(1, 1.0, 1.0, 1.0, 1.0),
        testCandle(2, 1.0, 1.0, 1.0, 1.0)
    )

    @Test
    fun `fib step uses observed candle spacing and interval fallback`() {
        assertEquals(60_000L, fibStepMs(candles, "1h"))
        assertEquals(300_000L, fibStepMs(candles.take(1), "5m"))
        assertEquals(3_600_000L, fibStepMs(emptyList(), "1h"))
    }

    @Test
    fun `fib index and virtual time mapping extend beyond the last candle`() {
        assertEquals(120_000L, fibIndexToTime(candles, 2, "1m"))
        assertEquals(240_000L, fibIndexToTime(candles, 4, "1m"))
        assertEquals(1, fibTimeToIndexVirtual(candles, 70_000L, "1m"))
        assertEquals(3, fibTimeToIndexVirtual(candles, 180_000L, "1m"))
        assertEquals(52, fibTimeToIndexVirtual(candles, 100_000_000L, "1m"))
    }

    @Test
    fun `candle countdown returns clear fallback for unsupported interval`() {
        assertEquals("--:--", formatCandleCountdown("unknown", 0L))
        assertTrue(formatCandleCountdown("1m", System.currentTimeMillis()).matches(Regex("\\d{2}:\\d{2}")))
    }

    @Test
    fun `candle duration and nearest candle mapping cover empty and equal boundaries`() {
        assertEquals(0L, candleDurationMs("unknown"))
        assertEquals(86_400_000L, candleDurationMs("1d"))
        assertEquals(0, timeToIndex(emptyList(), 123L))
        assertEquals(1, timeToIndex(candles, 70_000L))
        assertEquals(2, timeToIndex(candles, 90_000L))
    }

    @Test
    fun `taker refresh cadence grows with the timeframe`() {
        assertEquals(2_000L, com.defitracker.app.core.Timeframes.takerRefreshMs("5m"))
        assertEquals(15_000L, com.defitracker.app.core.Timeframes.takerRefreshMs("15m"))
        assertEquals(30_000L, com.defitracker.app.core.Timeframes.takerRefreshMs("1h"))
        assertEquals(60_000L, com.defitracker.app.core.Timeframes.takerRefreshMs("1d"))
    }
}
