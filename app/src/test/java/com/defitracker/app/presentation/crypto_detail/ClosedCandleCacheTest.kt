package com.defitracker.app.presentation.crypto_detail

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El tick del chart corre cada 2s, pero lo que solo depende de velas cerradas
 * (hoy el SMC) no cambia entre ticks. Esta cache debe devolver el valor guardado
 * mientras la clave no cambie, y recalcular en cuanto cambie.
 */
class ClosedCandleCacheTest {

    @Test
    fun `misma clave reutiliza el valor sin recalcular`() {
        var calls = 0
        val cache = ClosedCandleCache<Int>()

        val first = cache.getOrCompute("BTC|5m|prefs|1") { calls++; 10 }
        val second = cache.getOrCompute("BTC|5m|prefs|1") { calls++; 99 }

        assertEquals(10, first)
        assertEquals("la segunda lectura debe venir del cache", 10, second)
        assertEquals("no debe recalcular con la misma clave", 1, calls)
    }

    @Test
    fun `clave distinta recalcula`() {
        var calls = 0
        val cache = ClosedCandleCache<Int>()

        cache.getOrCompute("BTC|5m|prefs|1") { calls++; 10 }
        val other = cache.getOrCompute("BTC|15m|prefs|1") { calls++; 20 }

        assertEquals(20, other)
        assertEquals("una clave distinta debe recalcular", 2, calls)
    }

    @Test
    fun `version distinta con misma clave base recalcula`() {
        var calls = 0
        val cache = ClosedCandleCache<String>()
        val base = "BTC|5m|prefs"
        var version = 7

        val first = cache.getOrCompute("$base|$version") { calls++; "v7" }
        // la cola del chart puede reescribir velas cerradas sin cambiar el tamano
        version++
        val second = cache.getOrCompute("$base|$version") { calls++; "v8" }

        assertEquals("v7", first)
        assertEquals("v8", second)
        assertEquals("subir la version debe recalcular", 2, calls)
    }
}
