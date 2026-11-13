package com.defitracker.app.data.remote

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// payloads reales recortados de los sockets: un campo mal escrito compila y devuelve
// null, que es el bug silencioso que este test evita
class TickerStreamParsingTest {

    // primer frame de Bitunix: no es un tick, se descarta solo
    private val bitunixConnect = """{"op":"connect","ts":1794422843963,"data":{"result":true}}"""

    private val bitunixTicker = """{"ch":"ticker","symbol":"BTCUSDT","ts":1794422844302,"data":{"s":"BTCUSDT","o":"73466.6","la":"74474.6","h":"74750.1","l":"73185.6","b":"25777.952","q":"1903872210.76466","r":"1.3720520618"}}"""

    // frame real del canal de trades: es lo que arma la vela en curso de Bitunix
    private val bitunixTrade = """{"ch":"trade","symbol":"BTCUSDT","ts":1794520229052,"data":[{"t":"2026-11-12T21:50:28Z","p":"71569.8","v":"0.0186","s":"sell"},{"t":"2026-11-12T21:50:28Z","p":"71572.8","v":"0.0002","s":"sell"}]}"""

    // ack de suscripcion de BingX, sin comprimir y con data en null
    private val bingxAck = """{"id":"1","code":0,"msg":"","dataType":"","data":null}"""

    // frame de datos de BingX tal como llega: binario + gzip
    private val bingxTickerJson =
        """{"code":0,"dataType":"BTC-USDT@ticker","data":{"e":"24hTicker","E":1794422845947,"s":"BTC-USDT","p":"16.6","P":"0.02","c":"74475.9","L":"0.0005","h":"74748.0","l":"73187.5","v":"10124.3500","q":"747397188.68","o":"74459.3","O":1794422663933,"C":1794422845735,"A":"74483.2","a":"1.6680","B":"74475.8","b":"44.8765"}}"""

    @Test
    fun `bitunix ignores the connect frame`() {
        assertNull(BitunixTickerStream.parseEvent(bitunixConnect))
    }

    @Test
    fun `bitunix reads last price 24h open and quote volume`() {
        val event = BitunixTickerStream.parseEvent(bitunixTicker) as BitunixEvent.Price

        assertEquals("BTCUSDT", event.symbol)
        assertEquals(74474.6, event.tick.lastPrice, 0.0)
        assertEquals(73466.6, event.tick.open24h, 0.0)
        assertEquals(1_903_872_210.76466, event.tick.quoteVolume, 0.0)
    }

    @Test
    fun `bitunix reads the trades that build the live candle`() {
        val event = BitunixTickerStream.parseEvent(bitunixTrade) as BitunixEvent.Trades

        assertEquals("BTCUSDT", event.symbol)
        assertEquals(2, event.trades.size)
        val first = event.trades[0]
        assertEquals(71569.8, first.price, 0.0)
        assertEquals(0.0186, first.volume, 0.0)
        // 2026-11-12T21:50:28Z en epoch millis
        assertEquals(1_794_520_228_000L, first.timeMs)
    }

    @Test
    fun `bitunix subscribe message carries ticker and trade in one frame`() {
        val msg = BitunixTickerStream.subscribeMessage(listOf("BTCUSDT", "AVAXUSDT"))

        assertEquals(
            """{"op":"subscribe","args":[{"symbol":"BTCUSDT","ch":"ticker"},{"symbol":"BTCUSDT","ch":"trade"},{"symbol":"AVAXUSDT","ch":"ticker"},{"symbol":"AVAXUSDT","ch":"trade"}]}""",
            msg
        )
    }

    @Test
    fun `bingx ignores the subscription ack`() {
        assertNull(BingxTickerStream.parseTick(bingxAck))
    }

    @Test
    fun `bingx unzips and reads a real data frame`() {
        val gz = ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(bingxTickerJson.toByteArray()) }
        val raw: ByteArray = gz.toByteArray()
        val text = BingxTickerStream.gunzip(ByteString.of(*raw))!!
        val (symbol, tick) = BingxTickerStream.parseTick(text)!!.entries.first()

        assertEquals("BTC-USDT", symbol)
        assertEquals(74475.9, tick.lastPrice, 0.0)
        assertEquals(747397188.68, tick.quoteVolume, 0.0)
    }

    @Test
    fun `bingx ping payload is the epoch in millis`() {
        val msg = BingxTickerStream.pingMessage()

        assert(msg.startsWith("{\"ping\":\""))
        assert(msg.substringAfter("\"ping\":\"").dropLast(2).toLong() > 1_700_000_000_000L)
    }

    // frame real del stream combinado de Binance (data es objeto)
    private val binanceCombined = """{"stream":"btcusdt@miniTicker","data":{"e":"24hrMiniTicker","E":1794439786016,"s":"BTCUSDT","c":"73780.00000000","o":"73667.99000000","h":"74811.45000000","l":"73209.12000000","v":"16183.39514000","q":"1197099066.92336990"}}"""

    // frame real del stream de todo el mercado (data es array)
    private val binanceAllMarket = """{"stream":"!miniTicker@arr","data":[{"e":"24hrMiniTicker","E":1794433205017,"s":"BTCUSDT","c":"74582.00000000","o":"73900.01000000","h":"74811.45000000","l":"73209.12000000","v":"15993.15588000","q":"1182838182.38413430"},{"e":"24hrMiniTicker","E":1794433205017,"s":"ETHUSDT","c":"2050.50000000","o":"2040.00000000","h":"2070.00000000","l":"2030.00000000","v":"1000.0","q":"2050000.0"}]}"""

    // ack de suscripcion de MEXC y frame real de un simbolo
    private val mexcAck = """{"channel":"rs.sub.ticker","data":"success","ts":1794433589246}"""

    private val mexcTicker = """{"symbol":"BTC_USDT","data":{"symbol":"BTC_USDT","lastPrice":74480,"riseFallRate":0.0070,"fairPrice":74481.1,"indexPrice":74514.9,"volume24":330763689,"amount24":2445499863.15147,"maxBidPrice":81966.3,"minAskPrice":67063.4,"lower24Price":73185.5,"high24Price":74771.3,"timestamp":1794433590127,"bid1":74479.9,"ask1":74480}}"""

    @Test
    fun `binance combined frame has the tick in a single object`() {
        val ticks = BinanceTickerStream.parseTicks(binanceCombined)

        assertEquals(1, ticks.size)
        val tick = ticks["BTCUSDT"]!!
        assertEquals(73780.0, tick.close, 0.0)
        assertEquals(73667.99, tick.open24h, 0.0)
        assertEquals(1_197_099_066.92336990, tick.quoteVolume, 0.0)
    }

    @Test
    fun `binance all market frame has every tick in an array`() {
        val ticks = BinanceTickerStream.parseTicks(binanceAllMarket)

        assertEquals(2, ticks.size)
        assertEquals(74582.0, ticks["BTCUSDT"]!!.close, 0.0)
        assertEquals(2050.5, ticks["ETHUSDT"]!!.close, 0.0)
    }

    @Test
    fun `binance url asks only for the tracked symbols`() {
        val url = BinanceTickerStream.streamUrl(listOf("BTCUSDT", "AVAXUSDT"))

        assertEquals(
            "wss://stream.binance.com:9443/stream?streams=btcusdt@miniTicker/avaxusdt@miniTicker",
            url
        )
    }

    @Test
    fun `binance falls back to the whole market when there are too many symbols`() {
        val many = (1..101).map { "SYM${it}USDT" }

        val url = BinanceTickerStream.streamUrl(many)

        assertEquals("wss://stream.binance.com:9443/stream?streams=!miniTicker@arr", url)
    }

    @Test
    fun `mexc ignores the subscription ack`() {
        assertNull(MexcTickerStream.parseTick(mexcAck))
    }

    @Test
    fun `mexc reads last price rate and the real quote amount`() {
        val (symbol, tick) = MexcTickerStream.parseTick(mexcTicker)!!

        assertEquals("BTC_USDT", symbol)
        assertEquals(74480.0, tick.lastPrice, 0.0)
        assertEquals(0.0070, tick.riseFallRate, 1e-9)
        assertEquals(330763689.0, tick.volume24, 0.0)
        assertEquals(2445499863.15147, tick.amount24, 0.0)
    }

    @Test
    fun `mexc subscribes one symbol per message`() {
        val msg = MexcTickerStream.subscribeMessage("BTC_USDT")

        assertEquals("""{"method":"sub.ticker","param":{"symbol":"BTC_USDT"}}""", msg)
    }
}
