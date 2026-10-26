package com.defitracker.app.core

data class TimeframeDefinition(
    val key: String,
    val label: String,
    val durationMs: Long,
    val binanceInterval: String,
    val mexcInterval: String,
    val mexcAggregationFactor: Int,
    val mexcBaseDurationMs: Long,
    val tradingViewResolution: String,
    val chartPageCount: Int,
    val tvCountback: Int,
    val takerNative: Boolean,
    val calendarAggregationMs: Long? = null,
    val weekAnchored: Boolean = false,
    val higherTfBaseDurationMs: Long = durationMs
)

object Timeframes {
    const val CHART_KEY = "chart"
    const val DEFAULT_CHART_KEY = "5m"

    val ALL: List<TimeframeDefinition> = listOf(
        definition("1m", 60_000L, "Min1", 60_000L, "1", 2, 3000, false),
        definition("5m", 300_000L, "Min5", 300_000L, "5", 2, 3000, true),
        definition("15m", 900_000L, "Min15", 900_000L, "15", 2, 3000, true),
        definition("30m", 1_800_000L, "Min30", 1_800_000L, "30", 2, 2000, true),
        definition("1h", 3_600_000L, "Min60", 3_600_000L, "60", 2, 2000, true),
        definition("2h", 7_200_000L, "Min60", 3_600_000L, "120", 1, 1000, true),
        definition("4h", 14_400_000L, "Hour4", 14_400_000L, "240", 1, 1000, true),
        definition("6h", 21_600_000L, "Min60", 3_600_000L, "360", 1, 1000, true),
        definition("12h", 43_200_000L, "Hour4", 14_400_000L, "720", 1, 1000, true),
        definition("1d", 86_400_000L, "Day1", 86_400_000L, "1D", 1, 1000, true),
        definition("3d", 259_200_000L, "Day1", 86_400_000L, "3D", 1, 1000, false, higherTfBaseDurationMs = 86_400_000L),
        definition("5d", 432_000_000L, "Day1", 86_400_000L, "1D", 1, 1000, false, binanceInterval = "1d", calendarAggregationMs = 432_000_000L, higherTfBaseDurationMs = 86_400_000L),
        definition("1w", 604_800_000L, "Week1", 604_800_000L, "1W", 1, 1000, false),
        definition("2w", 1_209_600_000L, "Week1", 604_800_000L, "1W", 1, 1000, false, binanceInterval = "1w", calendarAggregationMs = 1_209_600_000L, weekAnchored = true, higherTfBaseDurationMs = 604_800_000L),
        definition("1mo", 2_592_000_000L, "Month1", 0L, "1M", 1, 1000, false, binanceInterval = "1M")
    )

    val KEYS: List<String> = ALL.map { it.key }
    val MA_OPTIONS: List<String> = listOf(CHART_KEY) + KEYS
    val DEFAULT_ALERT_INTERVALS: Set<String> = setOf("5m", "15m", "30m", "1h")
    val PULSE_KEYS: List<String> = listOf("15m", "1h", "4h")
    val SMC_CONFLUENCE_KEYS: List<String> = listOf("5m", "15m", "30m", "1h", "4h", "12h", "1d", "1w")
    val CALENDAR_KEYS: Set<String> = setOf("1d", "3d", "5d", "1w", "2w", "1mo")
    val TV_RESOLUTIONS: Set<String> = setOf("1", "3", "5", "15", "30", "60", "120", "240", "360", "720", "1D", "3D", "1W", "1M")
    val TV_ALIASES: Map<String, String> = mapOf("3m" to "3")

    fun find(key: String): TimeframeDefinition? = ALL.firstOrNull { it.key == key }

    fun durationMs(key: String): Long = find(key.trim())?.durationMs ?: 0L

    fun calendarAggregation(key: String): Long? = find(key)?.calendarAggregationMs

    fun binanceInterval(key: String): String = find(key)?.binanceInterval ?: key

    fun mexcKlinePlan(key: String): Pair<String, Int> =
        find(key)?.let { it.mexcInterval to it.mexcAggregationFactor } ?: ("Min60" to 1)

    fun mexcBaseDurationMs(mexcInterval: String): Long =
        ALL.firstOrNull { it.mexcInterval == mexcInterval }?.mexcBaseDurationMs ?: 0L

    fun tradingViewResolution(interval: String): String {
        val value = interval.trim()
        if (value in TV_RESOLUTIONS) return value
        TV_ALIASES[value]?.let { return it }
        return find(value)?.tradingViewResolution ?: "60"
    }

    fun chartPageCount(interval: String): Int = find(interval)?.chartPageCount ?: 1

    fun tvCountback(interval: String): Int = find(interval)?.tvCountback ?: 1000

    fun higherTfBaseDurationMs(interval: String): Long = when (interval.trim()) {
        "1M" -> find("1mo")?.higherTfBaseDurationMs ?: 0L
        else -> find(interval.trim())?.higherTfBaseDurationMs ?: 0L
    }

    val TAKER_NATIVE_KEYS: Set<String> = ALL.filter { it.takerNative }.map { it.key }.toSet()

    private fun definition(
        key: String,
        durationMs: Long,
        mexcInterval: String,
        mexcBaseDurationMs: Long,
        tradingViewResolution: String,
        chartPageCount: Int,
        tvCountback: Int,
        takerNative: Boolean,
        binanceInterval: String = key,
        calendarAggregationMs: Long? = null,
        weekAnchored: Boolean = false,
        higherTfBaseDurationMs: Long = durationMs
    ) = TimeframeDefinition(
        key = key,
        label = key,
        durationMs = durationMs,
        binanceInterval = binanceInterval,
        mexcInterval = mexcInterval,
        mexcAggregationFactor = when (key) {
            "2h" -> 2
            "6h" -> 6
            "12h" -> 3
            "3d" -> 3
            "5d" -> 5
            "2w" -> 2
            else -> 1
        },
        mexcBaseDurationMs = mexcBaseDurationMs,
        tradingViewResolution = tradingViewResolution,
        chartPageCount = chartPageCount,
        tvCountback = tvCountback,
        takerNative = takerNative,
        calendarAggregationMs = calendarAggregationMs,
        weekAnchored = weekAnchored,
        higherTfBaseDurationMs = higherTfBaseDurationMs
    )
}
