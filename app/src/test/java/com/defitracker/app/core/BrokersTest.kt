package com.defitracker.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

// Cada broker escribe el simbolo a su modo; en pantalla va sin separador.
class BrokersTest {

    @Test
    fun `el simbolo se muestra sin separador en todas las fuentes`() {
        assertEquals("BTCUSDT", Brokers.displaySymbol("BTCUSDT"))
        assertEquals("BTCUSDT", Brokers.displaySymbol("BTC_USDT"))
        assertEquals("BTCUSDT", Brokers.displaySymbol("BTC-USDT"))
    }

    @Test
    fun `los chips mantienen el orden pedido`() {
        assertEquals(
            listOf(Brokers.BITUNIX, Brokers.BINGX, Brokers.BINANCE, Brokers.MEXC),
            Brokers.CHIP_ORDER
        )
    }

    @Test
    fun `las fuentes desconocidas van al final del ranking`() {
        assertEquals(Brokers.CHIP_ORDER.size, Brokers.rank("LoQueSea"))
        assertEquals(0, Brokers.rank(Brokers.BITUNIX))
    }
}
