package com.defitracker.app.presentation.crypto_detail

/**
 * Caché de un solo valor para cálculos que sólo dependen de las velas cerradas.
 *
 * El tick del chart corre cada 2s, pero el SMC se calcula sobre `dropLast(1)`, así que su
 * entrada es idéntica entre ticks. Guardando el resultado y comparando la clave
 * (símbolo + TF + prefs + versión de velas cerradas) se evita repetir el cálculo.
 */
internal class ClosedCandleCache<T> {
    private var key: Any? = null
    private var value: T? = null
    private var hasValue = false

    @Synchronized
    fun getOrCompute(newKey: Any, compute: () -> T): T {
        val cached = value
        if (hasValue && key == newKey && cached != null) return cached
        val fresh = compute()
        key = newKey
        value = fresh
        hasValue = true
        return fresh
    }

    @Synchronized
    fun clear() {
        key = null
        value = null
        hasValue = false
    }
}
