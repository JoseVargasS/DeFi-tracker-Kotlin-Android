package com.defitracker.app.core

// catalogo unico de brokers: strings de `source`, orden de chips y etiquetas de la UI
object Brokers {
    const val BITUNIX = "Bitunix"
    const val BINGX = "BingX"
    const val BINANCE = "Binance"
    const val MEXC = "MEXC"
    const val TV = "TV"

    // orden pedido para los chips del resultado de busqueda
    val CHIP_ORDER: List<String> = listOf(BITUNIX, BINGX, BINANCE, MEXC)

    // futuros: el simbolo se muestra sin slash, como en MEXC
    fun isFutures(source: String?): Boolean =
        source == BITUNIX || source == BINGX || source == MEXC

    fun label(source: String?): String = when (source) {
        BITUNIX -> "Bitunix · Futuros"
        BINGX -> "BingX · Futuros"
        MEXC -> "MEXC · Futuros"
        TV -> "TradingView · CRYPTOCAP"
        else -> "Binance · Spot"
    }

    // posicion del chip; las fuentes desconocidas van al final
    fun rank(source: String?): Int =
        CHIP_ORDER.indexOf(source).let { if (it < 0) CHIP_ORDER.size else it }

    // cada broker escribe el simbolo a su modo: BTCUSDT, BTC_USDT, BTC-USDT.
    // en pantalla va siempre sin separador.
    fun displaySymbol(symbol: String): String = symbol.replace("_", "").replace("-", "")
}
