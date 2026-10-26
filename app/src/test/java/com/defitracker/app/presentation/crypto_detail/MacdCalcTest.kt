package com.defitracker.app.presentation.crypto_detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacdCalcTest {
    @Test
    fun `macd is empty before the slow and signal warmup`() {
        assertEquals(MacdData(), calculateMacd(emptyList()))
        assertEquals(MacdData(), calculateMacd(candlesFromCloses(List(33) { 10.0 })))
    }

    @Test
    fun `constant closes produce zero dif dea and histogram`() {
        val result = calculateMacd(candlesFromCloses(List(34) { 10.0 }))

        assertEquals(1, result.dif.size)
        assertEquals(1, result.dea.size)
        assertEquals(1, result.hist.size)
        assertTrue(result.dif.all { it.second == 0.0 })
        assertTrue(result.dea.all { it.second == 0.0 })
        assertTrue(result.hist.all { it.second == 0.0 })
        assertEquals(33L, result.dif.single().first)
    }
}
