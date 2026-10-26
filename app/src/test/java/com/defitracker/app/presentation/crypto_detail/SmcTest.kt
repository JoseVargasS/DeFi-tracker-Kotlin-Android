package com.defitracker.app.presentation.crypto_detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmcTest {
    @Test
    fun `smc functions handle empty and undersized input`() {
        assertTrue(detectSwings(emptyList()).isEmpty())
        assertTrue(computeSmc(List(12) { testCandle(it, 1.0, 1.0, 1.0, 1.0) }).swings.isEmpty())
        assertNull(detectPremium(emptyList()))
        assertTrue(detectFvg(emptyList()).isEmpty())
    }

    @Test
    fun `swing detection finds a centered high`() {
        val highs = listOf(2.0, 3.0, 5.0, 3.0, 2.0)
        val swings = detectSwings(
            highs.mapIndexed { index, high -> testCandle(index, 2.0, high, 1.0, 2.0) },
            lookback = 1
        )

        assertTrue(swings.contains(SmcSwing(2, 5.0, true)))
    }

    @Test
    fun `fvg detects a bullish three candle imbalance`() {
        val candles = MutableList(4) { testCandle(it, 9.5, 10.0, 9.0, 9.5) }
        candles[2] = testCandle(2, 12.5, 13.0, 12.0, 12.5)
        candles[3] = testCandle(3, 13.0, 14.0, 13.0, 13.5)

        val zone = detectFvg(candles).single { it.startIdx == 0 }

        assertEquals(SmcZoneKind.FVG, zone.kind)
        assertTrue(zone.bullish)
        assertEquals(12.0, zone.top, 0.0)
        assertEquals(10.0, zone.bottom, 0.0)
        assertTrue(!zone.mitigated)
    }

    @Test
    fun `premium range and aggregation preserve hand calculable values`() {
        val candles = listOf(
            testCandle(0, 1.0, 4.0, 1.0, 3.0, 2.0),
            testCandle(1, 3.0, 5.0, 2.0, 4.0, 3.0),
            testCandle(2, 4.0, 6.0, 3.0, 5.0, 4.0)
        )
        val range = detectPremium(candles)
        val aggregated = aggregateByCount(candles, 2)

        assertEquals(6.0, range!!.high, 0.0)
        assertEquals(1.0, range.low, 0.0)
        assertEquals(2, aggregated.size)
        assertEquals(5.0, aggregated[0].high, 0.0)
        assertEquals(1.0, aggregated[0].low, 0.0)
        assertEquals(5.0, aggregated[0].volume, 0.0)
        assertEquals(4.0, aggregated[0].close, 0.0)
    }

    @Test
    fun `timeframe helpers keep canonical order and fallback`() {
        assertEquals(listOf("15m", "30m", "1h", "4h", "1d"), higherConfluenceTfs("5m"))
        assertTrue(higherConfluenceTfs("unknown").isEmpty())
        assertEquals("HTF", higherTfLabel("unknown", 4))
    }
}
