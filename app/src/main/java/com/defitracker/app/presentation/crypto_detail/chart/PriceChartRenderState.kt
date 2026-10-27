package com.defitracker.app.presentation.crypto_detail.chart

enum class DrawingTool {
    NONE,
    MEASURE,
    FIBO,
    DRAW,
    LONG_POS,
    SHORT_POS
}

internal const val FIB_VIRTUAL_EXTEND = 50

internal data class MeasureZone(
    val startIdx: Int,
    val startPrice: Double,
    val endIdx: Int? = null,
    val endPrice: Double? = null
) {
    val isComplete: Boolean = endIdx != null && endPrice != null
}

