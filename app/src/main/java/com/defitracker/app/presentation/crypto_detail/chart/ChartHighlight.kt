package com.defitracker.app.presentation.crypto_detail.chart

import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.highlight.Highlight

/**
 * MPAndroidChart separa el bloque de datos (dataIndex) del dataset (dataSetIndex).
 * El constructor Highlight(x, dataSetIndex, stackIndex) NO llena dataIndex: lo deja en -1,
 * y CombinedData.getEntryForHighlight() revienta con IndexOutOfBoundsException al buscar
 * el bloque -1. La unica forma valida de usarlo es setDataIndex().
 */
internal fun Highlight.withDataIndex(index: Int): Highlight = apply { setDataIndex(index) }

/**
 * Indice real del bloque dentro de CombinedData (orden: Line, Bar, Scatter, Candle, Bubble).
 * Prioriza las velas: en el grafico de precio la cruz debe apuntar a la vela, no a las lineas.
 * Sin velas (MACD) cae al bloque de lineas.
 */
internal fun combinedDataIndex(data: CombinedData?): Int {
    val all = data?.getAllData() ?: return 0
    val candle = data.candleData?.let { all.indexOf(it) } ?: -1
    if (candle >= 0) return candle
    val line = data.lineData?.let { all.indexOf(it) } ?: -1
    return if (line >= 0) line else 0
}

/**
 * Highlight de la cruz: dataIndex real (si no, CombinedData revienta) y y = NaN.
 *
 * El y va como NaN a proposito: CombinedData.getEntryForHighlight() exige que el y del
 * Highlight coincida EXACTO con el y de la entrada y, si no coincide, devuelve null y la
 * cruz nunca se dibuja. Con NaN la libreria devuelve la primera entrada de esa vela.
 */
internal fun safeHighlight(x: Float, data: CombinedData?): Highlight =
    Highlight(x, Float.NaN, 0).withDataIndex(combinedDataIndex(data))

/**
 * Cruz segura para CombinedChart: si algo falla, limpia la cruz en vez de tumbar la app.
 */
internal fun CombinedChart.highlightSafe(x: Float, notify: Boolean = true) {
    try {
        highlightValue(safeHighlight(x, data), notify)
    } catch (_: Exception) {
        highlightValues(null)
    }
}
