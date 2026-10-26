package com.defitracker.app.presentation.crypto_detail

fun testCandle(
    index: Int,
    open: Double,
    high: Double,
    low: Double,
    close: Double,
    volume: Double = 0.0
): CandleData = CandleData(
    time = index * 60_000L,
    open = open,
    high = high,
    low = low,
    close = close,
    volume = volume
)

fun candlesFromCloses(closes: List<Double>): List<CandleData> = closes.mapIndexed { index, close ->
    testCandle(index, close, close, close, close)
}
