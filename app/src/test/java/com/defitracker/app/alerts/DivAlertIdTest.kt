package com.defitracker.app.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

// El id de la alerta lleva la fuente: Binance y Bitunix comparten simbolo (BTCUSDT) y sin
// esto el dedupe de uno tapaba al del otro.
class DivAlertIdTest {

    @Test
    fun `el id incluye la fuente`() {
        val binance = divAlertId("Binance", "BTCUSDT", "5m", "REG_BULL", 1000L)
        val bitunix = divAlertId("Bitunix", "BTCUSDT", "5m", "REG_BULL", 1000L)
        assertNotEquals(binance, bitunix)
        assertEquals("Binance|BTCUSDT|5m|REG_BULL|1000", binance)
        assertEquals("Bitunix|BTCUSDT|5m|REG_BULL|1000", bitunix)
    }

    @Test
    fun `el mismo broker y evento da el mismo id`() {
        assertEquals(
            divAlertId("MEXC", "BTC_USDT", "15m", "HID_BEAR", 42L),
            divAlertId("MEXC", "BTC_USDT", "15m", "HID_BEAR", 42L)
        )
    }

    @Test
    fun `la confluencia tambien va por fuente`() {
        assertEquals(
            "Bitunix|BTCUSDT|CONF|BULL|77",
            confluenceAlertId("Bitunix", "BTCUSDT", bullish = true, candleTime = 77L)
        )
        assertNotEquals(
            confluenceAlertId("BingX", "BTC-USDT", bullish = true, candleTime = 77L),
            confluenceAlertId("Binance", "BTCUSDT", bullish = true, candleTime = 77L)
        )
    }
}
