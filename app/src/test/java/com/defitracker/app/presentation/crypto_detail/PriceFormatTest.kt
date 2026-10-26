package com.defitracker.app.presentation.crypto_detail

import org.junit.Assert.assertEquals
import org.junit.Test

class PriceFormatTest {
    @Test
    fun `price formatter handles zero small large and compact bands`() {
        assertEquals("0.00", formatPriceForChart(0.0))
        assertEquals("0.12345679", formatPriceForChart(0.123456789))
        assertEquals("0.123456", formatPriceForChart(0.123456, extended = true))
        assertEquals("1.20", formatPriceForChart(1.2))
        assertEquals("1,000.00", formatPriceForChart(1_000.0))
        assertEquals("1.00B", formatPriceForChart(1_000_000_000.0))
        assertEquals("1.00T", formatPriceForChart(1_000_000_000_000.0))
    }

    @Test
    fun `axis formatter follows reference precision and volume formatter uses suffixes`() {
        assertEquals("1.23", formatAxisPrice(1.234, 2.5))
        assertEquals("0.123457", formatAxisPrice(0.1234567, 0.123456))
        assertEquals("1.25K", formatCompactVol(1_250.0))
        assertEquals("2.00M", formatCompactVol(2_000_000.0))
        assertEquals("-2.00B", formatCompactVol(-2_000_000_000.0))
        assertEquals("999.00", formatCompactVol(999.0))
    }

    @Test
    fun `pulse and repository formatters preserve their existing policies`() {
        assertEquals("--", fmtPulsePrice(Double.NaN))
        assertEquals("1.2", fmtPulsePrice(1.2))
        assertEquals("1,000.1", fmtPulsePrice(1_000.1))
        assertEquals("0.100000", formatRepositoryPrice(0.1))
        assertEquals("1.2000", formatRepositoryPrice(1.2))
        assertEquals("10.00", formatRepositoryPrice(10.0))
    }
}
