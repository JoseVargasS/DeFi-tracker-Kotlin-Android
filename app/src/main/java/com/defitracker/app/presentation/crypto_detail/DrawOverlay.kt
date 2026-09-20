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
        if (o.hidden) "1" else "0",
        if (o.locked) "1" else "0"
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
        val sTime = p[2].toLongOrNull() ?: return@mapNotNull null
        val sPrice = p[3].toDoubleOrNull() ?: return@mapNotNull null
        val eTime = p[4].toLongOrNull() ?: return@mapNotNull null
        val ePrice = p[5].toDoubleOrNull() ?: return@mapNotNull null
        DrawOverlay(
            id = p[0].ifBlank { return@mapNotNull null },
            kind = kind,
            start = FibAnchor(sTime, sPrice),
            end = FibAnchor(eTime, ePrice),
            colorHex = p[6].ifBlank { "#FFD60A" },
            width = p[7].toFloatOrNull()?.coerceIn(0.5f, 3f) ?: 1f,
            hidden = p[8] == "1",
            locked = p[9] == "1"
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
