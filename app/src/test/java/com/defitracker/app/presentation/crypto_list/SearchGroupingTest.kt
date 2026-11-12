package com.defitracker.app.presentation.crypto_list

import com.defitracker.app.core.Brokers
import com.defitracker.app.domain.model.AvailableCryptoPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// el buscador muestra una fila por par con los brokers como chips: si el agrupado o el
// orden se rompen, el usuario ve AVAX duplicado 4 veces y no sabe cual tocar
class SearchGroupingTest {

    private fun pair(base: String, quote: String, source: String, symbol: String) =
        AvailableCryptoPair(symbol = symbol, baseAsset = base, quoteAsset = quote, source = source)

    private val avaxAll = listOf(
        pair("AVAX", "USDT", "Binance", "AVAXUSDT"),
        pair("AVAX", "USDT", "Bitunix", "AVAXUSDT"),
        pair("AVAX", "USDT", "BingX", "AVAX-USDT"),
        pair("AVAX", "USDT", "MEXC", "AVAX_USDT")
    )

    @Test
    fun `same pair collapses in one row with the chip order`() {
        val groups = groupSearchPairs(avaxAll)

        assertEquals(1, groups.size)
        assertEquals("AVAX/USDT", groups[0].displayName)
        assertEquals(
            listOf(Brokers.BITUNIX, Brokers.BINGX, Brokers.BINANCE, Brokers.MEXC),
            groups[0].entries.map { it.source }
        )
    }

    @Test
    fun `pair with more brokers sorts first`() {
        val matches = avaxAll + pair("AVAX", "BTC", "Binance", "AVAXBTC")

        val groups = groupSearchPairs(matches)

        assertEquals("AVAX/USDT", groups[0].displayName)
        assertEquals(4, groups[0].entries.size)
        assertEquals("AVAX/BTC", groups[1].displayName)
    }

    @Test
    fun `usdt quote sorts above other quotes`() {
        val matches = listOf(
            pair("AVAX", "BTC", "Binance", "AVAXBTC"),
            pair("AVAX", "USDC", "Binance", "AVAXUSDC"),
            pair("AVAX", "USDT", "Binance", "AVAXUSDT"),
            pair("AVAX", "EUR", "Binance", "AVAXEUR")
        )

        assertEquals(
            listOf("USDT", "USDC", "BTC", "EUR"),
            groupSearchPairs(matches).map { it.quoteAsset }
        )
    }

    @Test
    fun `tradingview indexes land at the end on their own row`() {
        val tv = pair("BTC.D", "", "TV", "BTC.D")
        val groups = groupSearchPairs(avaxAll + tv)

        assertEquals(2, groups.size)
        assertEquals(Brokers.TV, groups[1].entries.single().source)
        assertEquals("BTC.D", groups[1].displayName)
    }

    @Test
    fun `duplicated source does not duplicate the chip`() {
        val groups = groupSearchPairs(
            avaxAll + pair("AVAX", "USDT", "Binance", "AVAXUSDT")
        )

        assertEquals(1, groups.size)
        assertEquals(4, groups[0].entries.size)
    }

    @Test
    fun `the list is capped at the max groups`() {
        val matches = (1..MAX_SEARCH_GROUPS + 8).map {
            pair("COIN$it", "USDT", "Binance", "COIN${it}USDT")
        }

        assertEquals(MAX_SEARCH_GROUPS, groupSearchPairs(matches).size)
    }

    @Test
    fun `query matches symbol base and keywords`() {
        val p = pair("AVAX", "USDT", "Binance", "AVAXUSDT")

        assertTrue(avaxAll.first().matchesQuery("avax"))
        assertTrue(avaxAll.first().matchesQuery("USDT"))
        assertTrue(avaxAll.first().matchesQuery("AVAXUSDT"))
        assertTrue(AvailableCryptoPair("BTC.D", "BTC.D", "", "TV", "btc dominance dominancia").matchesQuery("dominancia"))
        assertTrue(!avaxAll.first().matchesQuery("solana"))
    }
}
