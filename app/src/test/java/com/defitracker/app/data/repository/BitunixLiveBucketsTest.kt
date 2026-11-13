package com.defitracker.app.data.repository

import com.defitracker.app.data.remote.BitunixTrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// la vela en curso de Bitunix se arma con los trades del socket: estos son los numeros
// que el chart pinta mientras la vela no cierra
class BitunixLiveBucketsTest {

    private val buckets = BitunixLiveBuckets()

    // 2026-11-12T21:50:00Z
    private val minuteStart = 1_794_520_200_000L

    @Test
    fun `trades build the open high low close and volume of the bucket`() {
        buckets.onTrades(
            "BTCUSDT",
            listOf(
                BitunixTrade(minuteStart + 1_000, 100.0, 2.0),
                BitunixTrade(minuteStart + 2_000, 105.0, 3.0),
                BitunixTrade(minuteStart + 3_000, 95.0, 1.0),
                BitunixTrade(minuteStart + 4_000, 101.0, 4.0)
            )
        )

        val bucket = buckets.bucket("BTCUSDT", "1m")!!
        assertEquals(minuteStart, bucket.startMs)
        assertEquals(100.0, bucket.open, 0.0)
        assertEquals(105.0, bucket.high, 0.0)
        assertEquals(95.0, bucket.low, 0.0)
        assertEquals(101.0, bucket.close, 0.0)
        assertEquals(10.0, bucket.volume, 1e-9)
    }

    @Test
    fun `the bucket rolls over with the first trade of the next one`() {
        buckets.onTrades("BTCUSDT", listOf(BitunixTrade(minuteStart + 1_000, 100.0, 2.0)))
        buckets.onTrades("BTCUSDT", listOf(BitunixTrade(minuteStart + 61_000, 90.0, 5.0)))

        val bucket = buckets.bucket("BTCUSDT", "1m")!!
        assertEquals(minuteStart + 60_000, bucket.startMs)
        assertEquals(90.0, bucket.open, 0.0)
        assertEquals(90.0, bucket.high, 0.0)
        assertEquals(90.0, bucket.low, 0.0)
        assertEquals(5.0, bucket.volume, 1e-9)
    }

    @Test
    fun `a late trade of the previous bucket does not overwrite the current one`() {
        buckets.onTrades("BTCUSDT", listOf(BitunixTrade(minuteStart + 61_000, 90.0, 5.0)))
        buckets.onTrades("BTCUSDT", listOf(BitunixTrade(minuteStart + 1_000, 100.0, 2.0)))

        val bucket = buckets.bucket("BTCUSDT", "1m")!!
        assertEquals(minuteStart + 60_000, bucket.startMs)
        assertEquals(90.0, bucket.close, 0.0)
        assertEquals(5.0, bucket.volume, 1e-9)
    }

    @Test
    fun `the same trade feeds every timeframe with its own bucket phase`() {
        // 2026-11-12T21:50:28Z: bucket de 1m, de 30m y de 1h son distintos
        buckets.onTrades("BTCUSDT", listOf(BitunixTrade(minuteStart + 28_000, 100.0, 2.0)))

        assertEquals(minuteStart, buckets.bucket("BTCUSDT", "1m")!!.startMs)
        assertEquals(minuteStart - 20 * 60_000, buckets.bucket("BTCUSDT", "30m")!!.startMs)
        assertEquals(minuteStart - 50 * 60_000, buckets.bucket("BTCUSDT", "1h")!!.startMs)
    }

    @Test
    fun `a symbol without trades has no bucket`() {
        assertNull(buckets.bucket("AVAXUSDT", "1m"))
    }
}
