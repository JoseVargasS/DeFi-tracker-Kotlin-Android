package com.defitracker.app.presentation.crypto_detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * La clave decide si se reusa la cola cacheada o no. Es el punto más frágil: si coincide
 * por error, un indicador queda pegado hasta el siguiente cierre de vela. Estos tests
 * fijan las dos caras: lo que la clave DEBE ignorar (la vela viva) y lo que DEBE detectar.
 */
class IndicatorTailKeyTest {

    private val basePrefs = IndicatorPrefs(rsiDivVisible = true)

    private fun candles(closes: List<Double>): List<CandleData> = closes.mapIndexed { i, c ->
        testCandle(index = i, open = c, high = c, low = c, close = c)
    }

    private fun key(
        candles: List<CandleData>,
        symbol: String = "BTC_USDT",
        interval: String = "5m",
        prefs: IndicatorPrefs = basePrefs,
        version: Int = 7
    ): String = indicatorTailKey(symbol, interval, prefs, version, candles)

    private val closing: List<Double> = List(30) { 100.0 + it.toDouble() }

    @Test
    fun `mover solo la ultima vela no cambia la clave`() {
        val before = candles(closing)
        val moved = before.dropLast(1) + before.last().copy(
            open = before.last().open,
            close = before.last().close * 1.01,
            high = before.last().high * 1.02,
            low = before.last().low * 0.99
        )
        assertEquals("el tick sólo cambia la vela viva: la clave debe ser la misma", key(before), key(moved))
    }

    @Test
    fun `cambiar de simbolo cambia la clave`() {
        assertNotEquals(key(candles(closing)), key(candles(closing), symbol = "ETH_USDT"))
    }

    @Test
    fun `cambiar de temporalidad cambia la clave`() {
        assertNotEquals(key(candles(closing)), key(candles(closing), interval = "15m"))
    }

    @Test
    fun `cambiar un indicador cambia la clave`() {
        val other = basePrefs.copy(smcFvg = !basePrefs.smcFvg)
        assertNotEquals(key(candles(closing)), key(candles(closing), prefs = other))
    }

    @Test
    fun `cambiar una media cambia la clave`() {
        val other = basePrefs.copy(mas = basePrefs.mas + MaConfig("ma9", 9, MaType.SMA, "chart", "#FFFFFF", 1.2f, true))
        assertNotEquals(key(candles(closing)), key(candles(closing), prefs = other))
    }

    @Test
    fun `subir la version de velas cerradas cambia la clave`() {
        assertNotEquals(
            key(candles(closing), version = 7),
            key(candles(closing), version = 8)
        )
    }

    @Test
    fun `cambiar el tamano de la lista cambia la clave`() {
        assertNotEquals(
            key(candles(closing)),
            key(candles(closing + 101.0))
        )
    }

    @Test
    fun `cambiar el tiempo de una vela cerrada cambia la clave`() {
        // la clave usa el tiempo de la vela n-2 (las MAs de otro TF se alinean por tiempo)
        val before = candles(closing)
        val retimed = before.toMutableList()
        val secondLast = retimed[retimed.size - 2]
        retimed[retimed.size - 2] = secondLast.copy(time = secondLast.time + 60_000L)
        assertNotEquals(key(before), key(retimed))
    }

    @Test
    fun `cambiar el cierre de una vela cerrada no cambia la clave pero si debe subir la version`() {
        // el cierre no entra en la clave: una corrección de vela cerrada llega por syncTail,
        // que sube closedCandleVersion (ver CryptoDetailViewModel.syncTail)
        val before = candles(closing)
        val corrected = before.toMutableList()
        val secondLast = corrected[corrected.size - 2]
        corrected[corrected.size - 2] = secondLast.copy(close = secondLast.close * 1.5)
        assertEquals(key(before), key(corrected))
        assertNotEquals(
            "la versión es la que cubre las correcciones de velas cerradas",
            key(before, version = 7),
            key(corrected, version = 8)
        )
    }
}
