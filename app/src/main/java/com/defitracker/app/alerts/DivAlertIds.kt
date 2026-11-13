package com.defitracker.app.alerts

// Los ids llevan la fuente: Binance y Bitunix comparten simbolo (BTCUSDT) y sin esto una
// divergencia de uno tapaba la del otro en el dedupe.
internal fun divAlertId(
    source: String,
    symbol: String,
    interval: String,
    kind: String,
    candleTime: Long
): String = "$source|$symbol|$interval|$kind|$candleTime"

internal fun confluenceAlertId(
    source: String,
    symbol: String,
    bullish: Boolean,
    candleTime: Long
): String = "$source|$symbol|CONF|${if (bullish) "BULL" else "BEAR"}|$candleTime"
