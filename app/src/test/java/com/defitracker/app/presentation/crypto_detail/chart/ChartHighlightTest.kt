package com.defitracker.app.presentation.crypto_detail.chart

import com.github.mikephil.charting.data.CandleData
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.highlight.Highlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MPAndroidChart deja dataIndex en -1 cuando se usa el constructor Highlight(x, dataSet, stack).
 * Con datos combinados eso termina en IndexOutOfBoundsException al tocar el grafico.
 * Ademas CombinedData exige que el y del Highlight coincida EXACTO con el de la entrada:
 * si no coincide devuelve null y la cruz nunca se dibuja (y = NaN significa "me da igual").
 * Estos tests fijan el crash exacto y el contrato que lo evita.
 *
 * Nota: no se puede construir un dataset real en este test (Color.rgb es de Android y los
 * tests JVM no lo mockean), asi que la regla del y se fija sobre el Highlight que arma
 * la app, no sobre la libreria.
 */
class ChartHighlightTest {

    /** precio: velas + lineas, el caso que reportaba "Index -1 out of bounds for length 2" */
    private fun priceLikeData(): CombinedData {
        val data = CombinedData()
        data.setData(CandleData())
        data.setData(LineData())
        return data
    }

    @Test
    fun `el constructor Highlight no fija dataIndex`() {
        val h = Highlight(1f, 0, 0)
        assertEquals(-1, h.dataIndex)
    }

    @Test
    fun `dataIndex sin fijar revienta con IndexOutOfBounds`() {
        val data = priceLikeData()
        val error = assertThrows(IndexOutOfBoundsException::class.java) {
            data.getEntryForHighlight(Highlight(1f, 0, 0))
        }
        assertTrue(error.message.orEmpty().contains("Index -1 out of bounds for length 2"))
    }

    @Test
    fun `combinedDataIndex apunta al bloque de velas`() {
        // getAllData() ordena Line, Bar, Scatter, Candle, Bubble -> velas en 1
        assertEquals(1, combinedDataIndex(priceLikeData()))
    }

    @Test
    fun `combinedDataIndex sin velas usa el bloque de lineas`() {
        val macdLike = CombinedData()
        macdLike.setData(LineData())
        assertEquals(0, combinedDataIndex(macdLike))
    }

    @Test
    fun `la cruz fija el bloque de velas`() {
        val h = safeHighlight(4f, priceLikeData())
        assertEquals(1, h.dataIndex)
    }

    @Test
    fun `la cruz va con y NaN para que la libreria no descarte la entrada`() {
        // con un y que no coincide exactamente, CombinedData devuelve null y no hay cruz
        assertTrue(safeHighlight(4f, priceLikeData()).y.isNaN())
    }
}
