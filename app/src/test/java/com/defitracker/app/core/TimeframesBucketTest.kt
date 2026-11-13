package com.defitracker.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

// la vela en curso de Bitunix tiene que caer en el mismo bucket que las velas del
// exchange: si la fase no coincide, el chart no la puede pintar
class TimeframesBucketTest {

    // 2026-11-12T21:50:28Z
    private val now = 1_794_520_228_000L

    @Test
    fun `intraday buckets are aligned to the epoch`() {
        assertEquals(1_794_520_200_000L, Timeframes.bucketStartMs("1m", now))
        assertEquals(1_794_520_200_000L, Timeframes.bucketStartMs("5m", now))
        assertEquals(1_794_519_000_000L, Timeframes.bucketStartMs("30m", now))
        assertEquals(1_794_517_200_000L, Timeframes.bucketStartMs("1h", now))
        assertEquals(1_794_441_600_000L, Timeframes.bucketStartMs("1d", now))
    }

    @Test
    fun `the week bucket opens on monday`() {
        // las velas 1w de Binance y Bitunix abren en lunes (2026-11-02T00:00Z)
        val monday = 1_793_577_600_000L

        assertEquals(monday, Timeframes.bucketStartMs("1w", monday))
        assertEquals(monday, Timeframes.bucketStartMs("1w", monday + 3 * 86_400_000L))
    }

    @Test
    fun `the month bucket opens on the first day of the month in utc`() {
        assertEquals(1_793_491_200_000L, Timeframes.bucketStartMs("1mo", now))
    }
}
