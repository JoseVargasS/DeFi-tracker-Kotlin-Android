package com.defitracker.app.data.repository

import com.defitracker.app.data.remote.dto.BingxKlineDto
import com.defitracker.app.data.remote.dto.BitunixKlineDto
import com.defitracker.app.presentation.crypto_detail.toDoubleValue

// fila estilo Binance: [openTime, open, high, low, close, volume, closeTime, quoteVol, trades, takerBuy]
// Las dos APIs devuelven descendente y con `time` de distinto tipo (Bitunix String, BingX Long).

internal fun BitunixKlineDto.toBinanceRow(): List<Any> = listOf<Any>(
    time?.toLongOrNull() ?: 0L,
    open.toDoubleValue(),
    high.toDoubleValue(),
    low.toDoubleValue(),
    close.toDoubleValue(),
    baseVol.toDoubleValue(),
    0L, 0.0, 0, 0.0
)

internal fun BingxKlineDto.toBinanceRow(): List<Any> = listOf<Any>(
    time,
    open.toDoubleValue(),
    high.toDoubleValue(),
    low.toDoubleValue(),
    close.toDoubleValue(),
    volume.toDoubleValue(),
    0L, 0.0, 0, 0.0
)

// el chart, el SMC y las MAs asumen velas en orden cronologico: la API las da al reves
internal fun List<List<Any>>.ascendingRows(): List<List<Any>> =
    sortedBy { (it.getOrNull(0) as? Number)?.toLong() ?: 0L }

// Bitunix y BingX paginan hacia atras y cada pagina llega descendente: juntarlas y
// ordenarlas por tiempo. Dar vuelta solo el orden de las paginas NO alcanza: deja la
// lista sin orden cronologico
internal fun List<List<List<Any>>>.pagesToAscendingRows(): List<List<Any>> =
    flatten().ascendingRows()

// BingX cabe en una sola llamada pero llega descendente: la app espera ascendente
internal fun List<BingxKlineDto>.toAscendingRows(): List<List<Any>> =
    map { it.toBinanceRow() }.ascendingRows()
