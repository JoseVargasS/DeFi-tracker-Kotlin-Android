package com.defitracker.app.presentation.crypto_detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsiCalcTest {
    @Test
    fun `rsi is empty with no candles or without a complete period`() {
        assertTrue(calculateRsi(emptyList()).isEmpty())
        assertTrue(calculateRsi(candlesFromCloses(List(14) { 100.0 })).isEmpty())
    }

    @Test
    fun `rsi reaches 100 for equal closes and all gains`() {
        val equal = calculateRsi(candlesFromCloses(List(15) { 10.0 }))
        val rising = calculateRsi(candlesFromCloses((1..15).map { it.toDouble() }))

        assertEquals(15, equal.size)
        assertEquals(15, rising.size)
        assertTrue(equal.drop(14).all { it == 100.0 })
        assertTrue(rising.drop(14).all { it == 100.0 })
    }

    @Test
    fun `rsi moving average preserves short input and calculates rolling average`() {
        assertEquals(listOf(1.0, 2.0), rsiMaOf(listOf(1.0, 2.0), period = 3))
        assertEquals(listOf(1.0, 1.5, 2.5, 3.5), rsiMaOf(listOf(1.0, 2.0, 3.0, 4.0), period = 2))
    }

    @Test
    fun `ema uses sma seed and nan warmup`() {
        assertTrue(emaSeries(listOf(1.0, 2.0), period = 3).isEmpty())
        val ema = emaSeries(listOf(1.0, 2.0, 3.0, 5.0), period = 2)

        assertTrue(ema[0].isNaN())
        assertEquals(1.5, ema[1], 0.0)
        assertEquals(2.5, ema[2], 0.0)
        assertEquals(25.0 / 6.0, ema[3], 0.0000001)
    }

    @Test
    fun `kline parser skips invalid timestamps and parses numeric values`() {
        val candles = listOf(
            listOf<Any>("0", "1", "2", "0.5", "1.5", "4"),
            listOf<Any>(60_000L, 1, 2.0, 0.5, "1.5", "4", "", "", "", 1.25)
        ).toCandleList()

        assertEquals(1, candles.size)
        assertEquals(60_000L, candles.single().time)
        assertEquals(1.25, candles.single().takerBuyVol, 0.0)
    }
}
