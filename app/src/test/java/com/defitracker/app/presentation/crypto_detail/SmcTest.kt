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

    @Test
    fun `fvg remains unmitigated on tap and mitigates on fill`() {
        // Vela 0: high 10. Vela 2: low 12. Imbalance de 10 a 12.
        val candles = MutableList(4) { testCandle(it, 9.5, 10.0, 9.0, 9.5) }
        candles[2] = testCandle(2, 12.5, 13.0, 12.0, 12.5)
        // Vela 3: Tap dentro del FVG (low 11.0, close 12.2) -> NO debe mitigar
        candles[3] = testCandle(3, 12.4, 12.6, 11.0, 12.2)

        val tappedZone = detectFvg(candles).single { it.startIdx == 0 }
        assertTrue("FVG tapped debe seguir activo/no mitigado", !tappedZone.mitigated)

        // Vela 4: Llena el gap cayendo por debajo de 10.0 (low 9.5, close 9.8) -> MITIGADO
        candles.add(testCandle(4, 12.0, 12.1, 9.5, 9.8))
        val filledZone = detectFvg(candles).single { it.startIdx == 0 }
        assertTrue("FVG que perfora la base debe marcarse como mitigado", filledZone.mitigated)
    }

    @Test
    fun `structure detection does not repeat events on consecutive impulse candles`() {
        // Swings con lookback 1: High en idx 2 (precio 100), Low en idx 4 (precio 90)
        val swings = listOf(
            SmcSwing(2, 100.0, true),
            SmcSwing(4, 90.0, false)
        )
        val candles = mutableListOf(
            testCandle(0, 95.0, 96.0, 94.0, 95.0),
            testCandle(1, 95.0, 97.0, 94.0, 96.0),
            testCandle(2, 96.0, 100.0, 95.0, 99.0), // Swing High
            testCandle(3, 98.0, 98.0, 92.0, 93.0),
            testCandle(4, 93.0, 94.0, 90.0, 91.0), // Swing Low
            testCandle(5, 92.0, 103.0, 91.0, 102.0), // Quiebre de 100.0
            testCandle(6, 102.0, 106.0, 101.0, 105.0), // Continuacion sin nuevo swing
            testCandle(7, 105.0, 109.0, 104.0, 108.0)  // Continuacion sin nuevo swing
        )

        val events = detectStructure(candles, swings)
        assertEquals("Debe emitir exactamente un solo evento de quiebre en vez de repetir por cada vela", 1, events.size)
        assertEquals(5, events.first().breakIdx)
        assertEquals(100.0, events.first().levelPrice, 0.0)
    }

    @Test
    fun `confirmed uptrend emits BOS without repeating on impulse`() {
        // Swings:
        // Swing High en 2 (100.0), Swing Low en 4 (92.0)
        // Vela 5 quiebra 100.0 (establece tendencia alcista trend = 1)
        // Swing High en 6 (106.0), Swing Low en 8 (101.0)
        // Vela 9 quiebra 106.0 (rompimiento a favor = BOS)
        // Vela 10 y 11 continuan impulso sin generar BOS duplicados
        val swings = listOf(
            SmcSwing(2, 100.0, true),
            SmcSwing(4, 92.0, false),
            SmcSwing(6, 106.0, true),
            SmcSwing(8, 101.0, false)
        )
        val candles = mutableListOf(
            testCandle(0, 86.0, 87.0, 85.0, 86.0),
            testCandle(1, 87.0, 95.0, 86.0, 94.0),
            testCandle(2, 94.0, 100.0, 93.0, 99.0),
            testCandle(3, 99.0, 99.0, 93.0, 94.0),
            testCandle(4, 94.0, 95.0, 92.0, 93.0),
            testCandle(5, 93.0, 103.0, 92.0, 102.0), // Quiebre inicial a 100.0 -> trend = 1
            testCandle(6, 102.0, 106.0, 101.0, 105.0), // Swing High 106.0
            testCandle(7, 105.0, 105.0, 102.0, 103.0),
            testCandle(8, 103.0, 104.0, 101.0, 102.0), // Swing Low 101.0
            testCandle(9, 102.0, 108.0, 102.0, 107.0), // Quiebre a favor (BOS) de 106.0
            testCandle(10, 107.0, 110.0, 106.0, 109.0), // Continuacion sin nuevo swing
            testCandle(11, 109.0, 112.0, 108.0, 111.0)  // Continuacion sin nuevo swing
        )

        val events = detectStructure(candles, swings)
        val bosEvents = events.filter { it.kind == SmcEventKind.BOS }
        assertEquals("Debe emitir exactamente un BOS", 1, bosEvents.size)
        assertEquals(9, bosEvents.first().breakIdx)
        assertEquals(106.0, bosEvents.first().levelPrice, 0.0)
    }

    @Test
    fun `order block survives retest inside its range and mitigates when breached`() {
        val candles = mutableListOf(
            testCandle(0, 90.0, 91.0, 89.0, 90.0),
            testCandle(1, 90.0, 92.0, 89.0, 91.0),
            testCandle(2, 91.0, 93.0, 88.0, 89.0), // Vela bajista previa (Order Block): low 88, high 93
            testCandle(3, 90.0, 98.0, 89.5, 97.0), // Impulso alcista
            testCandle(4, 97.0, 102.0, 96.0, 101.0) // Breakout en idx 4
        )
        val event = SmcEvent(breakIdx = 4, swingIdx = 1, levelPrice = 92.0, kind = SmcEventKind.BOS, bullish = true)

        // Vela 5: Retestea entrando a 90.0 (entre 88 y 93) -> NO debe mitigar
        candles.add(testCandle(5, 101.0, 101.0, 90.0, 92.0))
        val activeOb = detectOrderBlocks(candles, listOf(event)).single { it.startIdx == 2 }
        assertTrue("OB testeado dentro de su rango no debe marcarse mitigado", !activeOb.mitigated)

        // Vela 6: Cierra por debajo de 88.0 (low 85.0, close 86.0) -> Mitigado/Invalidado
        candles.add(testCandle(6, 91.0, 91.0, 85.0, 86.0))
        val breachedOb = detectOrderBlocks(candles, listOf(event)).single { it.startIdx == 2 }
        assertTrue("OB vulnerado por debajo del minimo debe marcarse mitigado", breachedOb.mitigated)
    }

    @Test
    fun `order block survives a wick below its low`() {
        val candles = mutableListOf(
            testCandle(0, 90.0, 91.0, 89.0, 90.0),
            testCandle(1, 90.0, 92.0, 89.0, 91.0),
            testCandle(2, 91.0, 93.0, 88.0, 89.0), // OB: low 88, high 93
            testCandle(3, 90.0, 98.0, 89.5, 97.0),
            testCandle(4, 97.0, 102.0, 96.0, 101.0) // Breakout
        )
        val event = SmcEvent(breakIdx = 4, swingIdx = 1, levelPrice = 92.0, kind = SmcEventKind.BOS, bullish = true)

        // Vela 5: mecha hasta 87.0 pero cierra en 91.0 (dentro del OB) -> sigue vivo
        candles.add(testCandle(5, 93.0, 94.0, 87.0, 91.0))

        val ob = detectOrderBlocks(candles, listOf(event)).single { it.startIdx == 2 }
        assertTrue("una mecha bajo el minimo no debe invalidar el OB", !ob.mitigated)
    }

    @Test
    fun `already broken swing does not emit a late bos`() {
        // H2 (104) es un fractal chico del impulso: cuando se confirma, el precio (105) ya
        // lo paso. No debe contar como nivel rompible ni disparar un BOS tardio.
        val swings = listOf(
            SmcSwing(2, 100.0, true),
            SmcSwing(4, 90.0, false),
            SmcSwing(6, 104.0, true)
        )
        val candles = mutableListOf(
            testCandle(0, 95.0, 96.0, 94.0, 95.0),
            testCandle(1, 95.0, 97.0, 94.0, 96.0),
            testCandle(2, 96.0, 100.0, 95.0, 99.0),
            testCandle(3, 98.0, 98.0, 92.0, 93.0),
            testCandle(4, 93.0, 94.0, 90.0, 91.0),
            testCandle(5, 92.0, 103.0, 91.0, 102.0), // quiebra 100.0
            testCandle(6, 102.0, 106.0, 101.0, 105.0), // el 104 queda debajo del close
            testCandle(7, 105.0, 108.0, 104.0, 107.0)
        )

        val events = detectStructure(candles, swings)
        assertEquals("un nivel ya superado no debe generar otro BOS", 1, events.size)
        assertEquals(5, events.first().breakIdx)
    }
}
