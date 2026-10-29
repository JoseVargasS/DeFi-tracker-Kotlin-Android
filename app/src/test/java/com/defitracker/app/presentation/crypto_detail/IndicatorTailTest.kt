package com.defitracker.app.presentation.crypto_detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Guardián del cálculo incremental: al mover SOLO la última vela (un tick de precio),
 * el último punto de cada serie calculado de forma incremental debe ser idéntico al
 * que produce el cálculo completo. Tolerancia 1e-9.
 *
 * Las series de referencia de este test se calculan de forma independiente (misma
 * fórmula que el chart, escrita acá) para que un error en la cola no quede tapado.
 */
class IndicatorTailTest {

    private val tolerance = 1e-9

    // ─── referencias "cálculo completo" ───

    private fun bollinger(candles: List<CandleData>, period: Int = 20, mult: Double = 2.0):
        Triple<List<Pair<Long, Double>>, List<Pair<Long, Double>>, List<Pair<Long, Double>>> {
        val up = ArrayList<Pair<Long, Double>>()
        val mid = ArrayList<Pair<Long, Double>>()
        val low = ArrayList<Pair<Long, Double>>()
        var sum = 0.0
        var sumSq = 0.0
        for (i in candles.indices) {
            val c = candles[i].close
            sum += c
            sumSq += c * c
            if (i >= period) {
                val d = candles[i - period].close
                sum -= d
                sumSq -= d * d
            }
            if (i >= period - 1) {
                val sma = sum / period
                val std = sqrt((sumSq / period - sma.pow(2.0)).coerceAtLeast(0.0))
                mid.add(i.toLong() to sma)
                up.add(i.toLong() to (sma + mult * std))
                low.add(i.toLong() to (sma - mult * std))
            }
        }
        return Triple(up, mid, low)
    }

    private fun stochRsi(candles: List<CandleData>, window: Int = 14):
        Pair<List<Pair<Long, Double>>, List<Pair<Long, Double>>> {
        val rsiValues = calculateRsi(candles)
        val k = ArrayList<Pair<Long, Double>>()
        val d = ArrayList<Pair<Long, Double>>()
        if (rsiValues.size < window) return k to d
        val raw = ArrayList<Double>(rsiValues.size)
        for (i in rsiValues.indices) {
            if (i >= window - 1) {
                val slice = rsiValues.subList(i - window + 1, i + 1)
                val lo = slice.minOrNull() ?: 0.0
                val hi = slice.maxOrNull() ?: 100.0
                val cur = rsiValues[i]
                raw.add(if (hi - lo != 0.0) (cur - lo) / (hi - lo) * 100.0 else 0.0)
            } else {
                raw.add(0.0)
            }
        }
        val smoothK = rsiMaOf(raw, 3)
        val smoothD = rsiMaOf(smoothK, 3)
        for (i in smoothK.indices) {
            val candleIndex = i + (candles.size - smoothK.size)
            if (candleIndex >= 0) {
                k.add(candleIndex.toLong() to smoothK[i])
                d.add(candleIndex.toLong() to smoothD[i])
            }
        }
        return k to d
    }

    private fun maSeries(candles: List<CandleData>, period: Int, ema: Boolean): List<Pair<Long, Double>> {
        val closes = candles.map { it.close }
        val out = ArrayList<Pair<Long, Double>>()
        if (ema) {
            if (closes.size < period) return out
            val k = 2.0 / (period + 1)
            var value = closes.take(period).average()
            out.add((period - 1).toLong() to value)
            for (i in period until closes.size) {
                value = closes[i] * k + value * (1 - k)
                out.add(i.toLong() to value)
            }
        } else {
            var sum = 0.0
            for (i in closes.indices) {
                sum += closes[i]
                if (i >= period) sum -= closes[i - period]
                if (i >= period - 1) out.add(i.toLong() to sum / period)
            }
        }
        return out
    }

    private fun referenceComputation(candles: List<CandleData>, mas: List<MaConfig>): ChartComputation {
        val (bbUp, bbMid, bbLow) = bollinger(candles)
        val (stochK, stochD) = stochRsi(candles)
        val rsiValues = calculateRsi(candles)
        val rsi = rsiValues.mapIndexed { i, v -> (i + (candles.size - rsiValues.size)).toLong() to v }
        val rsiMaValues = rsiMaOf(rsiValues)
        val rsiMa = rsiMaValues.mapIndexed { i, v -> (i + (candles.size - rsiMaValues.size)).toLong() to v }
        val macd = calculateMacd(candles)
        val maLines = mas.filter { it.visible }.associate { ma ->
            ma.id to maSeries(candles, ma.period, ma.type == MaType.EMA)
        }
        return ChartComputation(
            candles = candles,
            bbUpper = bbUp,
            bbMiddle = bbMid,
            bbLower = bbLow,
            stochK = stochK,
            stochD = stochD,
            maLines = maLines,
            rsi = rsi,
            rsiMa = rsiMa,
            macdDif = macd.dif,
            macdDea = macd.dea,
            macdHist = macd.hist
        )
    }

    // ─── velas de prueba ───

    private fun candles(n: Int, seed: Int, price: Double = 80_000.0): List<CandleData> {
        val rnd = Random(seed)
        var p = price
        val out = ArrayList<CandleData>(n)
        for (i in 0 until n) {
            val open = p
            val close = (open * (1.0 + (rnd.nextDouble() - 0.5) * 0.01)).coerceAtLeast(1.0)
            val high = maxOf(open, close) * (1.0 + rnd.nextDouble() * 0.003)
            val low = minOf(open, close) * (1.0 - rnd.nextDouble() * 0.003)
            out.add(
                CandleData(
                    time = 1_700_000_000_000L + i * 60_000L,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = 10.0 + rnd.nextDouble() * 90.0
                )
            )
            p = close
        }
        return out
    }

    // tendencia pura: lleva el RSI a 0 o 100 y el StochRSI a los extremos
    private fun trendCandles(n: Int, step: Double): List<CandleData> {
        var p = 80_000.0
        val out = ArrayList<CandleData>(n)
        for (i in 0 until n) {
            val open = p
            val close = open * (1.0 + step)
            out.add(
                CandleData(
                    time = 1_700_000_000_000L + i * 60_000L,
                    open = open,
                    high = maxOf(open, close) * 1.0005,
                    low = minOf(open, close) * 0.9995,
                    close = close,
                    volume = 10.0
                )
            )
            p = close
        }
        return out
    }

    private fun ticked(candles: List<CandleData>, newClose: Double): List<CandleData> {
        val last = candles.last()
        val updated = last.copy(
            close = newClose,
            high = maxOf(last.high, newClose),
            low = minOf(last.low, newClose)
        )
        return candles.dropLast(1) + updated
    }

    private val testMas = listOf(
        MaConfig("sma20", 20, MaType.SMA, "chart", "#FFFFFF", 1.2f, true),
        MaConfig("ema20", 20, MaType.EMA, "chart", "#3EEC15", 1.2f, true)
    )

    private fun runTailCase(n: Int, seed: Int) {
        runTailCaseWith(candles(n, seed), "n=$n seed=$seed")
    }

    private fun runTailCaseWith(before: List<CandleData>, tag: String) {
        val seedComputation = referenceComputation(before, testMas)
        val state = buildIndicatorTailState(before)
        assertNotNull("el estado debe construirse para $tag", state)

        // tick: sólo se mueve la última vela
        val after = ticked(before, before.last().close * 1.004)
        val expected = referenceComputation(after, testMas)
        val tailSeed = IndicatorTailSeed("test|$tag", seedComputation, state!!)
        val tail = recomputeIndicatorTail(
            after, tailSeed, "5m", testMas, IndicatorPrefs(rsiDivVisible = false), SmcData()
        )
        assertNotNull("la cola debe poder calcularse para $tag", tail)
        tail!!

        fun closeEnough(name: String, actual: Double, expectedValue: Double) {
            assertTrue(
                "$name: incremental=$actual completo=$expectedValue ($tag)",
                abs(actual - expectedValue) <= tolerance
            )
        }

        // Bandas
        closeEnough("BB media", tail.bbMiddle.last().second, expected.bbMiddle.last().second)
        closeEnough("BB superior", tail.bbUpper.last().second, expected.bbUpper.last().second)
        closeEnough("BB inferior", tail.bbLower.last().second, expected.bbLower.last().second)
        // RSI + signal
        closeEnough("RSI", tail.rsi.last().second, expected.rsi.last().second)
        closeEnough("RSI MA", tail.rsiMa.last().second, expected.rsiMa.last().second)
        // StochRSI
        closeEnough("Stoch K", tail.stochK.last().second, expected.stochK.last().second)
        closeEnough("Stoch D", tail.stochD.last().second, expected.stochD.last().second)
        // MACD
        closeEnough("MACD linea", tail.macdDif.last().second, expected.macdDif.last().second)
        closeEnough("MACD señal", tail.macdDea.last().second, expected.macdDea.last().second)
        closeEnough("MACD hist", tail.macdHist.last().second, expected.macdHist.last().second)
        // MAs
        closeEnough("SMA20", tail.maLines.getValue("sma20").last().second, expected.maLines.getValue("sma20").last().second)
        closeEnough("EMA20", tail.maLines.getValue("ema20").last().second, expected.maLines.getValue("ema20").last().second)

        // misma forma: mismo tamaño e índices
        assertEquals("tamaño BB", expected.bbMiddle.size, tail.bbMiddle.size)
        assertEquals("tamaño RSI", expected.rsi.size, tail.rsi.size)
        assertEquals("tamaño Stoch K", expected.stochK.size, tail.stochK.size)
        assertEquals("tamaño MACD", expected.macdDif.size, tail.macdDif.size)
        assertEquals("tamaño SMA", expected.maLines.getValue("sma20").size, tail.maLines.getValue("sma20").size)
        assertEquals("último índice BB", after.size - 1L, tail.bbMiddle.last().first)
        assertEquals("último índice RSI", after.size - 1L, tail.rsi.last().first)

        // la historia (todo menos el último punto) no cambia
        assertEquals("historia BB", expected.bbMiddle.dropLast(1), tail.bbMiddle.dropLast(1))
        assertEquals("historia RSI", expected.rsi.dropLast(1), tail.rsi.dropLast(1))
        assertEquals("historia MACD", expected.macdDif.dropLast(1), tail.macdDif.dropLast(1))
    }

    @Test
    fun `cola incremental iguala el calculo completo en 1m de datos`() {
        runTailCase(n = 120, seed = 1)
    }

    @Test
    fun `cola incremental iguala el calculo completo en series largas`() {
        runTailCase(n = 400, seed = 7)
        runTailCase(n = 1_500, seed = 21)
    }

    @Test
    fun `cola incremental iguala el calculo completo con varios precios`() {
        runTailCase(n = 300, seed = 3)
        runTailCase(n = 300, seed = 99)
    }

    @Test
    fun `cola incremental iguala el calculo completo en tendencias extremas`() {
        // RSI en 0/100 y StochRSI en los topes: cubre las divisiones por cero
        runTailCaseWith(trendCandles(200, -0.002), "bajista")
        runTailCaseWith(trendCandles(200, 0.002), "alcista")
    }
}
