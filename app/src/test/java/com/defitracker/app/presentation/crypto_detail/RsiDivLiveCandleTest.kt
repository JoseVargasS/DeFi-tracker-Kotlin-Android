package com.defitracker.app.presentation.crypto_detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Las divergencias confirmadas NO se pueden cachear aunque en apariencia sólo dependan
 * de velas cerradas: `fractalPivots` confirma pivotes en [lookback, n-6] y el pivote del
 * índice n-6 usa la ventana [n-11, n-1], que incluye el RSI de la vela viva. Si la vela
 * en curso empuja el RSI por encima (o debajo) del pivote, la divergencia aparece o
 * desaparece con el tick.
 *
 * Este test fija ese comportamiento: si algún día alguien cachea las confirmadas, acá
 * salta el motivo.
 */
class RsiDivLiveCandleTest {

    private fun candles(n: Int, seed: Int): List<CandleData> {
        val rnd = Random(seed)
        var price = 80_000.0
        val out = ArrayList<CandleData>(n)
        for (i in 0 until n) {
            val open = price
            val close = (open * (1.0 + (rnd.nextDouble() - 0.5) * 0.01)).coerceAtLeast(1.0)
            out.add(
                testCandle(
                    index = i,
                    open = open,
                    high = maxOf(open, close) * (1.0 + rnd.nextDouble() * 0.003),
                    low = minOf(open, close) * (1.0 - rnd.nextDouble() * 0.003),
                    close = close,
                    volume = 10.0
                )
            )
            price = close
        }
        return out
    }

    // mueve SOLO la última vela: mismo tamaño, mismas velas cerradas, mismo índice
    private fun movedLast(before: List<CandleData>, factor: Double): List<CandleData> {
        val last = before.last()
        val close = last.close * factor
        return before.dropLast(1) + last.copy(
            close = close,
            high = maxOf(last.high, close),
            low = minOf(last.low, close)
        )
    }

    @Test
    fun `mover solo la ultima vela puede cambiar las divergencias confirmadas`() {
        var tested = 0
        var changed = 0
        var firstCase = ""

        for (seed in 1..400) {
            for (factor in doubleArrayOf(1.002, 1.01, 0.998, 0.99)) {
                val before = candles(120, seed)
                val confirmedBefore = detectRsiDivergences(before, calculateRsi(before))
                val after = movedLast(before, factor)
                val confirmedAfter = detectRsiDivergences(after, calculateRsi(after))
                tested++
                if (confirmedBefore != confirmedAfter) {
                    changed++
                    if (firstCase.isEmpty()) {
                        firstCase = "seed=$seed factor=$factor antes=${confirmedBefore.size} despues=${confirmedAfter.size}"
                    }
                }
            }
        }

        println("RSI-DIV-LIVE: probados=$tested cambiaron=$changed primero=$firstCase")
        assertTrue(
            "las confirmadas deberían poder cambiar con la vela viva (si no cambia nunca, el cache sería seguro)",
            changed > 0
        )
    }

    @Test
    fun `el cambio se concentra en el pivote del indice size - 6`() {
        // el único pivote que puede cambiar es el de n-6: ningún otro usa la vela viva
        for (seed in 1..400) {
            for (factor in doubleArrayOf(1.002, 1.01, 0.998, 0.99)) {
                val before = candles(120, seed)
                val confirmedBefore = detectRsiDivergences(before, calculateRsi(before))
                val after = movedLast(before, factor)
                val confirmedAfter = detectRsiDivergences(after, calculateRsi(after))
                if (confirmedBefore == confirmedAfter) continue
                val differing = (confirmedBefore + confirmedAfter)
                    .filter { it !in confirmedBefore || it !in confirmedAfter }
                assertEquals(
                    "todo cambio debe ser el pivote de n-6 (seed=$seed factor=$factor)",
                    differing.size,
                    differing.count { it.idx2 == before.size - 6 }
                )
                return
            }
        }
    }
}
