package com.defitracker.app.presentation.crypto_list.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Pares sin logo en ninguna fuente (commodities, indices de TradingView): el badge muestra un
// monograma, no una letra pelada.
class PairIconTest {

    @Test
    fun `los commodities y los indices TV tienen monograma`() {
        assertEquals("Au", pairMonogram("XAU"))
        assertEquals("Oil", pairMonogram("USOIL"))
        assertEquals("B%", pairMonogram("BTC.D"))
        assertEquals("E%", pairMonogram("ETH.D"))
        assertEquals("U%", pairMonogram("USDT.D"))
        assertEquals("T3", pairMonogram("TOTAL3"))
        assertEquals("T2", pairMonogram("TOTAL2"))
        assertEquals("OT", pairMonogram("OTHERS"))
    }

    @Test
    fun `una moneda sin logo usa las dos primeras letras`() {
        assertEquals("SO", pairMonogram("SOL"))
        assertEquals("VE", pairMonogram("VELODROME"))
    }

    @Test
    fun `el prefijo 1000 usa el nombre pelado`() {
        assertEquals("PE", pairMonogram("1000PEPE"))
    }

    @Test
    fun `las URLs prueban CoinCap y Binance`() {
        val urls = pairIconUrls("BTC")
        assertEquals(2, urls.size)
        assertTrue(urls[0].contains("assets.coincap.io"))
        assertTrue(urls[1].contains("bin.bnbstatic.com"))
    }

    @Test
    fun `1000PEPE tambien prueba el nombre pelado`() {
        val urls = pairIconUrls("1000PEPE")
        assertEquals(4, urls.size)
        assertTrue(urls.any { it.contains("1000pepe") })
        assertTrue(urls.any { it.contains("PEPE") })
    }
}
