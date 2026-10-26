package com.defitracker.app.presentation.crypto_detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignalsTest {
    @Test
    fun `signals return null when there are too few candles`() {
        val short = candlesFromCloses(List(11) { 10.0 })

        assertNull(detectMaWickRejection(short))
        assertNull(detectFvgTap(short.take(9)))
        assertNull(detectSweepReclaim(short))
    }

    @Test
    fun `equal candles do not produce a ma rejection`() {
        assertNull(detectMaWickRejection(candlesFromCloses(List(52) { 100.0 })))
    }

    @Test
    fun `long lower wick crossing moving average produces bullish rejection`() {
        val candles = MutableList(52) { testCandle(it, 100.0, 101.0, 99.0, 100.0) }
        candles[50] = testCandle(50, 100.5, 101.0, 90.0, 100.5)

        val signal = detectMaWickRejection(candles)

        assertEquals(TradeSignal("MA_REJECT_BULL", true, "EMA50"), signal)
    }

    @Test
    fun `unmitigated bullish fvg tap produces bullish signal`() {
        val candles = MutableList(10) { testCandle(it, 9.5, 10.0, 9.0, 9.5) }
        candles[8] = testCandle(8, 12.5, 13.0, 12.0, 12.5)
        candles[9] = testCandle(9, 13.0, 14.0, 13.0, 13.5)

        assertEquals(
            TradeSignal("FVG_TAP_BULL", true, "FVG"),
            detectFvgTap(candles)
        )
    }

    @Test
    fun `sweep above previous swing that closes back below is bearish`() {
        val highs = listOf(2.0, 3.0, 4.0, 5.0, 6.0, 10.0, 8.0, 7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 11.0, 2.0)
        val candles = highs.mapIndexed { index, high ->
            if (index == 13) testCandle(index, 9.5, high, 9.0, 9.5)
            else testCandle(index, high - 0.5, high, 1.0, high - 0.25)
        }

        assertEquals(
            TradeSignal("SWEEP_BEAR", false, "Barrido"),
            detectSweepReclaim(candles)
        )
    }
}
