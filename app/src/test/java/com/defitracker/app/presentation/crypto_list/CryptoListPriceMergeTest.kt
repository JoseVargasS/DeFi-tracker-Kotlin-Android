package com.defitracker.app.presentation.crypto_list

import com.defitracker.app.domain.model.CryptoPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// tres reglas de la lista: el par borrado no vuelve (el REST volaba con la lista vieja),
// la DB no pisa los precios vivos (vienen en 0.00) y el REST solo va a lo que el socket
// no cubre.
class CryptoListPriceMergeTest {

    private fun pair(
        symbol: String,
        source: String,
        price: String = "0.00",
        pct: String = "0.00",
        positive: Boolean = true
    ) = CryptoPair(
        symbol = symbol,
        baseAsset = symbol.removeSuffix("USDT"),
        quoteAsset = "USDT",
        price = price,
        priceChangePercent = pct,
        isPositive = positive,
        source = source
    )

    @Test
    fun `un par borrado mientras volaba el REST no resucita`() {
        val current = listOf(pair("BTCUSDT", "Binance", "100.00"))
        // el REST traia el par que el usuario ya borro
        val updates = mapOf(
            "BTCUSDT-Binance" to pair("BTCUSDT", "Binance", "100.00"),
            "AVAXUSDT-MEXC" to pair("AVAXUSDT", "MEXC", "8.60")
        )

        val out = applyPrices(current, updates)

        assertEquals(1, out.size)
        assertEquals("BTCUSDT", out[0].symbol)
    }

    @Test
    fun `el precio nuevo se aplica sobre el par que sigue en la lista`() {
        val current = listOf(pair("BTCUSDT", "Binance", "100.00", "1.00"))
        val updates = mapOf(
            "BTCUSDT-Binance" to pair("BTCUSDT", "Binance", "105.50", "2.50", true)
        )

        val out = applyPrices(current, updates)

        assertEquals("105.50", out[0].price)
        assertEquals("2.50", out[0].priceChangePercent)
    }

    @Test
    fun `sin cambios devuelve la misma lista`() {
        val current = listOf(pair("BTCUSDT", "Binance", "100.00", "1.00"))
        val updates = mapOf(
            "BTCUSDT-Binance" to pair("BTCUSDT", "Binance", "100.00", "1.00")
        )

        assertSame(current, applyPrices(current, updates))
        assertSame(current, applyPrices(current, emptyMap()))
    }

    @Test
    fun `la DB con precio 0 no pisa el precio vivo`() {
        val current = listOf(pair("BTCUSDT", "Binance", "100.00", "1.00", true))
        val fromDb = listOf(pair("BTCUSDT", "Binance"))

        val out = mergeTrackedPairs(fromDb, current)

        assertEquals("100.00", out[0].price)
        assertEquals("1.00", out[0].priceChangePercent)
        assertTrue(out[0].isPositive)
    }

    @Test
    fun `un par nuevo entra con el precio de la DB`() {
        val out = mergeTrackedPairs(
            listOf(pair("AVAXUSDT", "MEXC")),
            listOf(pair("BTCUSDT", "Binance", "100.00"))
        )

        // la DB manda en quien esta: el par viejo que no esta en la DB no vuelve
        assertEquals(1, out.size)
        assertEquals("AVAXUSDT", out[0].symbol)
        assertEquals("0.00", out[0].price)
    }

    @Test
    fun `un par borrado no vuelve con la merger`() {
        val out = mergeTrackedPairs(
            listOf(pair("BTCUSDT", "Binance")),
            listOf(
                pair("BTCUSDT", "Binance", "100.00"),
                pair("AVAXUSDT", "MEXC", "8.60")
            )
        )

        assertEquals(1, out.size)
        assertEquals("BTCUSDT", out[0].symbol)
    }

    @Test
    fun `el REST solo va a los pares sin tick fresco o sin precio`() {
        val pairs = listOf(
            pair("BTCUSDT", "Binance", "100.00"),
            pair("AVAXUSDT", "MEXC", "8.60"),
            pair("BTC.D", "TV", "0.00")
        )
        val ticks = mapOf(
            "BTCUSDT-Binance" to 100_000L,
            "AVAXUSDT-MEXC" to 100_000L
        )

        val out = selectForRest(pairs, ticks, nowMs = 120_000L, staleAfterMs = 45_000L)

        // BTCUSDT y AVAXUSDT tienen tick fresco: no les hace falta el REST.
        // BTC.D es de TradingView y nunca tiene tick: siempre por REST.
        assertEquals(listOf("BTC.D"), out.map { it.symbol })
    }

    @Test
    fun `un tick viejo devuelve el par al REST`() {
        val pairs = listOf(pair("AVAXUSDT", "BingX", "8.60"))
        val ticks = mapOf("AVAXUSDT-BingX" to 100_000L)

        assertTrue(selectForRest(pairs, ticks, 120_000L, 45_000L).isEmpty())
        assertEquals(1, selectForRest(pairs, ticks, 146_000L, 45_000L).size)
    }

    @Test
    fun `un par sin tick nunca se queda sin REST`() {
        val pairs = listOf(pair("AVAXUSDT", "Bitunix", "8.60"))

        assertEquals(1, selectForRest(pairs, emptyMap(), 100_000L, 45_000L).size)
    }

    @Test
    fun `un par con precio cero se refresca aunque tenga tick`() {
        val pairs = listOf(pair("AVAXUSDT", "Binance", "0.00"))
        val ticks = mapOf("AVAXUSDT-Binance" to 100_000L)

        assertEquals(1, selectForRest(pairs, ticks, 110_000L, 45_000L).size)
    }
}
