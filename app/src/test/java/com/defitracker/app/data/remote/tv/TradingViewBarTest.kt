package com.defitracker.app.data.remote.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// TradingView manda las series de dominancia (BTC.D, USDT.D) sin volumen: 5 campos.
// Con la regla vieja (6 campos) todas esas barras se descartaban y el par se quedaba
// en 0.00 sin minigrafica, mientras las de market cap (6 campos) si funcionaban.
class TradingViewBarTest {

    // payload real de CRYPTOCAP:BTC.D
    private val dominanceBar = listOf(
        1794355200.0, 60.94681844147436, 61.02161241311311, 60.85441327238097, 61.02161241311311
    )

    // payload real de CRYPTOCAP:TOTAL3
    private val marketCapBar = listOf(
        1794355200.0, 699166221842.0, 709345788525.0, 693384970635.0, 701841279281.0, 136349015521.35646
    )

    @Test
    fun `una barra de dominancia sin volumen se acepta con volumen cero`() {
        val bar = tvBar(dominanceBar)

        assertNotNull(bar)
        assertEquals(1794355200000L, bar!![0])
        assertEquals(60.94681844147436, bar[1] as Double, 1e-9)
        assertEquals(61.02161241311311, bar[2] as Double, 1e-9)
        assertEquals(60.85441327238097, bar[3] as Double, 1e-9)
        assertEquals(61.02161241311311, bar[4] as Double, 1e-9)
        assertEquals(0.0, bar[5] as Double, 1e-9)
    }

    @Test
    fun `una barra con volumen lo conserva`() {
        val bar = tvBar(marketCapBar)

        assertNotNull(bar)
        assertEquals(136349015521.35646, bar!![5] as Double, 1e-6)
    }

    @Test
    fun `una barra incompleta se descarta`() {
        assertNull(tvBar(listOf(1.0, 2.0, 3.0, 4.0)))
        assertNull(tvBar(emptyList()))
    }

    @Test
    fun `la barra mantiene la forma que espera el chart`() {
        val bar = tvBar(dominanceBar)!!

        assertEquals(10, bar.size)
        assertEquals(0L, bar[6])
        assertEquals(0.0, bar[7] as Double, 1e-9)
        assertEquals(0, bar[8])
        assertEquals(0.0, bar[9] as Double, 1e-9)
    }
}
