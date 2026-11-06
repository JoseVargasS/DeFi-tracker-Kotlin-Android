package com.defitracker.app.presentation.crypto_detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsiDivTest {
    @Test
    fun `divergence requires matching non-empty candle and rsi series`() {
        assertTrue(detectRsiDivergences(emptyList(), emptyList()).isEmpty())
        assertTrue(
            detectRsiDivergences(
                candlesFromCloses(List(10) { 10.0 }),
                List(9) { 50.0 }
            ).isEmpty()
        )
    }

    @Test
    fun `lower price low with higher rsi low is regular bullish divergence`() {
        val rsi = listOf(60.0, 55.0, 40.0, 30.0, 40.0, 45.0, 50.0, 45.0, 40.0, 45.0, 50.0, 55.0, 60.0, 65.0, 70.0)
        val candles = rsi.mapIndexed { index, _ ->
            val low = when (index) {
                3 -> 10.0
                8 -> 8.0
                else -> 20.0
            }
            testCandle(index, 50.0, 55.0, low, 50.0)
        }

        val divergences = detectRsiDivergences(candles, rsi, lookback = 2)
        val bullish = divergences.single { it.kind == RsiDivKind.REG_BULL }

        assertEquals(3, bullish.idx1)
        assertEquals(8, bullish.idx2)
        assertTrue(bullish.bullish)
    }

    @Test
    fun `equal price and rsi values do not create divergence`() {
        val candles = List(15) { testCandle(it, 10.0, 10.0, 10.0, 10.0) }
        assertTrue(detectRsiDivergences(candles, List(15) { 50.0 }, lookback = 2).isEmpty())
    }

    @Test
    fun `early divergence disappears when the confirmation window closes without confirming`() {
        // pivote temprano en la vela 10: su confirmacion (lookback 5) se evalua en la 15
        val early = RsiDiv(idx1 = 1, idx2 = 10, rsi1 = 40.0, rsi2 = 45.0, kind = RsiDivKind.REG_BULL)

        assertEquals(
            "mientras la ventana sigue abierta, la temprana se muestra como Pre",
            1,
            mergeConfirmedAndEarly(emptyList(), listOf(early), lastIndex = 14).size
        )
        assertTrue(
            "con la ventana cerrada y sin confirmar, la temprana ya no debe quedar pintada",
            mergeConfirmedAndEarly(emptyList(), listOf(early), lastIndex = 15).isEmpty()
        )
    }

    @Test
    fun `early divergence gives way to the confirmed one`() {
        val early = RsiDiv(idx1 = 1, idx2 = 10, rsi1 = 40.0, rsi2 = 45.0, kind = RsiDivKind.REG_BULL)
        val confirmed = RsiDiv(idx1 = 1, idx2 = 11, rsi1 = 40.0, rsi2 = 45.0, kind = RsiDivKind.REG_BULL)

        val merged = mergeConfirmedAndEarly(listOf(confirmed), listOf(early), lastIndex = 12)

        assertEquals(1, merged.size)
        assertTrue(merged.none { it.early })
    }
}
