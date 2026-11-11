package com.defitracker.app.presentation.crypto_detail

import com.defitracker.app.presentation.crypto_detail.chart.measureContextKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * La medicion se guarda por indice de vela. Si la ventana se corre, los indices ya no
 * apuntan a las mismas velas y la medicion se invalida; si solo entra una vela nueva al
 * final, los indices siguen valiendo y la medicion sobrevive al cierre de vela.
 */
class MeasureContextKeyTest {

    private fun candles(n: Int): List<CandleData> =
        List(n) { testCandle(it, 100.0, 101.0, 99.0, 100.0) }

    @Test
    fun `una vela nueva al final no invalida la medicion`() {
        val antes = candles(10)
        val despues = candles(11) // misma ventana + una vela al final

        assertEquals(
            measureContextKey("BTCUSDT", "5m", antes),
            measureContextKey("BTCUSDT", "5m", despues)
        )
    }

    @Test
    fun `cuando la ventana se corre la medicion se invalida`() {
        val ventana = candles(10)
        val corrida = ventana.drop(1) // la primera vela ya no es la misma

        assertNotEquals(
            measureContextKey("BTCUSDT", "5m", ventana),
            measureContextKey("BTCUSDT", "5m", corrida)
        )
    }

    @Test
    fun `cambiar el par o la temporalidad invalida la medicion`() {
        val velas = candles(10)
        val base = measureContextKey("BTCUSDT", "5m", velas)

        assertNotEquals(base, measureContextKey("ETHUSDT", "5m", velas))
        assertNotEquals(base, measureContextKey("BTCUSDT", "15m", velas))
    }
}
