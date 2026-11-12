package com.defitracker.app.data.repository

import com.defitracker.app.data.remote.dto.BingxEnvelope
import com.defitracker.app.data.remote.dto.BingxKlineDto
import com.defitracker.app.data.remote.dto.BitunixEnvelope
import com.defitracker.app.data.remote.dto.BitunixKlineDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// los payloads son respuestas reales recortadas: un nombre de campo mal escrito compila
// igual y devuelve 0.0, que es el bug que este test evita
class FuturesKlineMappingTest {

    private val gson = Gson()

    // Bitunix: `time` es String y las velas llegan de la mas nueva a la mas vieja
    private val bitunixJson = """
        {"code":0,"msg":"ok","data":[
          {"open":"74470.1","high":"74500","low":"74419.5","close":"74433.4","quoteVol":"3590050.81967","baseVol":"48.2113","time":"1794421500000"},
          {"open":"74430.0","high":"74480","low":"74400.0","close":"74470.1","quoteVol":"1000.5","baseVol":"13.4","time":"1794421200000"}
        ]}
    """.trimIndent()

    // BingX: `time` es numerico y tambien llega descendente
    private val bingxJson = """
        {"code":0,"msg":"","data":[
          {"open":"74433.0","close":"74449.0","high":"74449.1","low":"74431.6","volume":"0.2888","time":1794421800000},
          {"open":"74430.0","close":"74433.0","high":"74440.0","low":"74425.0","volume":"0.5000","time":1794421500000}
        ]}
    """.trimIndent()

    private fun bitunixRows(): List<BitunixKlineDto> {
        val type = object : TypeToken<BitunixEnvelope<List<BitunixKlineDto>>>() {}.type
        return gson.fromJson<BitunixEnvelope<List<BitunixKlineDto>>>(bitunixJson, type).data.orEmpty()
    }

    private fun bingxRows(): List<BingxKlineDto> {
        val type = object : TypeToken<BingxEnvelope<List<BingxKlineDto>>>() {}.type
        return gson.fromJson<BingxEnvelope<List<BingxKlineDto>>>(bingxJson, type).data.orEmpty()
    }

    @Test
    fun `bitunix row parses the string time and fills the binance columns`() {
        val row = bitunixRows().first().toBinanceRow()

        assertEquals(1_794_421_500_000L, row[0])
        assertEquals(74470.1, row[1] as Double, 0.0)
        assertEquals(74500.0, row[2] as Double, 0.0)
        assertEquals(74419.5, row[3] as Double, 0.0)
        assertEquals(74433.4, row[4] as Double, 0.0)
        assertEquals(48.2113, row[5] as Double, 0.0)
    }

    @Test
    fun `bingx envelope resolves the generic and maps ascending`() {
        val rows = bingxRows().toAscendingRows()

        assertEquals(2, rows.size)
        // la app espera ascendente: el JSON venia al reves
        assertEquals(1_794_421_500_000L, rows[0][0])
        assertEquals(1_794_421_800_000L, rows[1][0])
        assertTrue((rows[0][0] as Long) < (rows[1][0] as Long))

        assertEquals(74449.0, rows[1][4] as Double, 0.0)
        assertEquals(0.2888, rows[1][5] as Double, 0.0)
        assertEquals(74431.6, rows[1][3] as Double, 0.0)
    }

    // el bug: Bitunix pagina hacia atras y cada pagina llega descendente. El repo juntaba
    // las paginas pero no las velas, asi que la lista final no era cronologica: el eje
    // salia al reves, el SMC se saturaba y cada refresco appendeaba duplicados que
    // corrían el chart a la izquierda
    @Test
    fun `bitunix pages come out ascending even though every page arrives backwards`() {
        val newest = 1_794_427_500_000L
        val step = 300_000L
        // paginas en orden (la mas nueva primero) y cada una al reves, como llega la API
        val pages = listOf(
            listOf(newest, newest - step, newest - 2 * step),
            listOf(newest - 3 * step, newest - 4 * step),
            listOf(newest - 5 * step, newest - 6 * step)
        )
        val rows = pages.flatten().map { time ->
            BitunixKlineDto(
                open = "2050.0", high = "2060.0", low = "2040.0", close = "2055.0",
                baseVol = "12.5", time = time.toString()
            ).toBinanceRow()
        }

        val times = rows.ascendingRows().map { it[0] as Long }

        assertEquals((6 downTo 0).map { newest - it * step }, times)
        // estrictamente ascendente: el SMC, las MAs y el rebuild lo asumen
        assertTrue(times.zipWithNext().all { (a, b) -> a < b })
        // la vela viva es la ultima: si no, el precio se pinta sobre una vela vieja
        assertEquals(newest, times.last())
    }

    // la ruta que usa el repo de verdad: paginas en orden (mas nueva primero) con cada
    // pagina al reves. Antes de ordenarlas por tiempo la lista salia no-cronologica
    @Test
    fun `bitunix pages joined by the repository come out chronological`() {
        val newest = 1_794_427_500_000L
        val step = 300_000L
        val pages = listOf(
            listOf(newest, newest - step, newest - 2 * step),
            listOf(newest - 3 * step, newest - 4 * step),
            listOf(newest - 5 * step, newest - 6 * step)
        ).map { page ->
            page.map { time ->
                BitunixKlineDto(
                    open = "2050.0", high = "2060.0", low = "2040.0", close = "2055.0",
                    baseVol = "12.5", time = time.toString()
                ).toBinanceRow()
            }
        }

        val times = pages.pagesToAscendingRows().map { it[0] as Long }

        assertEquals((6 downTo 0).map { newest - it * step }, times)
        assertTrue(times.zipWithNext().all { (a, b) -> a < b })
    }
}
