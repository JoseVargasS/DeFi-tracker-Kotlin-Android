package com.defitracker.app.presentation.crypto_detail

import org.junit.Assert.assertEquals
import org.junit.Test

// el header del detalle muestra base/quote: BTC-USDT de BingX no debe salir como
// "BTC-US" + "DT", y BTCUSDT de Bitunix/Binance debe conservar su slash
class TradingPairSplitTest {

    @Test
    fun `mexc symbol splits on the underscore`() {
        val parts = splitTradingPair("AVAX_USDT")

        assertEquals("AVAX", parts.baseAsset)
        assertEquals("USDT", parts.quoteAsset)
        assertEquals("AVAX/USDT", parts.displayName)
    }

    @Test
    fun `bingx symbol splits on the dash`() {
        val parts = splitTradingPair("AVAX-USDT")

        assertEquals("AVAX", parts.baseAsset)
        assertEquals("USDT", parts.quoteAsset)
        assertEquals("AVAX/USDT", parts.displayName)
    }

    @Test
    fun `plain symbol falls back to the known quote list`() {
        val parts = splitTradingPair("AVAXUSDT")

        assertEquals("AVAX", parts.baseAsset)
        assertEquals("USDT", parts.quoteAsset)
    }

    @Test
    fun `tradingview index keeps the whole symbol`() {
        val parts = splitTradingPair("BTC.D")

        assertEquals("BTC.D", parts.baseAsset)
        assertEquals("", parts.quoteAsset)
        assertEquals("BTC.D", parts.displayName)
    }

    @Test
    fun `blank symbol splits into empty parts`() {
        val parts = splitTradingPair("   ")

        assertEquals("", parts.baseAsset)
        assertEquals("", parts.quoteAsset)
    }
}
