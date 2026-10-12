package com.defitracker.app.presentation.crypto_detail

// dibujos estilo OKX, anclados por tiempo como los fibos

enum class DrawKind {
    SEGMENT, LINE, RAY, ARROW,
    H_SEGMENT, H_LINE, H_RAY,
    RECT, CIRCLE, TRIANGLE,
    PRICE_LINE
}

data class DrawOverlay(
    val id: String,
    val kind: DrawKind,
    val start: FibAnchor,
    val end: FibAnchor,
    val colorHex: String = "#FFD60A",
    val width: Float = 1f,
    val hidden: Boolean = false,
    val locked: Boolean = false
)

const val MAX_DRAWS_PER_SYMBOL = 20

// encoding manual sin librerias: id;kind;sTime;sPrice;eTime;ePrice;color;width;hidden;locked
fun encodeDrawOverlays(overlays: List<DrawOverlay>): String = overlays.joinToString("|") { o ->
    listOf(
        o.id,
        o.kind.name,
        o.start.time.toString(),
        o.start.price.toString(),
        o.end.time.toString(),
        o.end.price.toString(),
        o.colorHex,
        o.width.toString(),
        bool01(o.hidden),
        bool01(o.locked)
    ).joinToString(";")
}

fun decodeDrawOverlays(raw: String?): List<DrawOverlay> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split("|").mapNotNull { entry ->
        val p = entry.split(";")
        if (p.size < 10) return@mapNotNull null
        val kind = try {
            DrawKind.valueOf(p[1])
        } catch (_: Exception) {
            return@mapNotNull null
        }
        val sTime = p.reqLong(2) ?: return@mapNotNull null
        val sPrice = p.reqDouble(3) ?: return@mapNotNull null
        val eTime = p.reqLong(4) ?: return@mapNotNull null
        val ePrice = p.reqDouble(5) ?: return@mapNotNull null
        DrawOverlay(
            id = p[0].ifBlank { return@mapNotNull null },
            kind = kind,
            start = FibAnchor(sTime, sPrice),
            end = FibAnchor(eTime, ePrice),
            colorHex = p[6].ifBlank { "#FFD60A" },
            width = p.reqWidth(7),
            hidden = p.flag(8),
            locked = p.flag(9)
        )
    }.take(MAX_DRAWS_PER_SYMBOL)
}

fun drawKindLabel(kind: DrawKind): String = when (kind) {
    DrawKind.SEGMENT -> "Segmento"
    DrawKind.LINE -> "Línea"
    DrawKind.RAY -> "Recta"
    DrawKind.ARROW -> "Flecha"
    DrawKind.H_SEGMENT -> "Segmento H"
    DrawKind.H_LINE -> "Línea H"
    DrawKind.H_RAY -> "Recta H"
    DrawKind.RECT -> "Rectángulo"
    DrawKind.CIRCLE -> "Círculo"
    DrawKind.TRIANGLE -> "Triángulo"
    DrawKind.PRICE_LINE -> "Línea de precio"
}
