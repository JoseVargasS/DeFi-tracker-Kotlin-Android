package com.defitracker.app.presentation.crypto_detail.chart

import com.defitracker.app.presentation.crypto_detail.CandleData

/**
 * Contexto de la medicion (herramienta de medir).
 *
 * La medicion se guarda por indice de vela, asi que solo se invalida si cambia la
 * ventana (la primera vela se corre al recargar el grafico), el par o la temporalidad.
 * Una vela nueva al final NO la invalida: por eso el dibujo sobrevive a los ticks de
 * precio y al cierre de vela.
 */
internal fun measureContextKey(
    symbol: String,
    interval: String,
    candles: List<CandleData>
): String = "$symbol|$interval|${candles.firstOrNull()?.time ?: 0L}"
