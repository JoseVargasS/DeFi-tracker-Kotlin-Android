package com.defitracker.app.presentation.crypto_detail

import com.defitracker.app.ui.theme.ChartColors

// dibujos estilo OKX, anclados por tiempo como los fibos

enum class DrawKind {
    SEGMENT, LINE, RAY, ARROW,
    H_SEGMENT, H_LINE, H_RAY,
    RECT, CIRCLE, TRIANGLE,
    PRICE_LINE
}

// alineacion del texto del dibujo, como las etiquetas de TV
enum class DrawLabelH { LEFT, CENTER, RIGHT }
enum class DrawLabelV { TOP, MIDDLE, BOTTOM }

data class DrawOverlay(
    val id: String,
    val kind: DrawKind,
    val start: FibAnchor,
    val end: FibAnchor,
    val colorHex: String = ChartColors.GOLD_HEX,
    val width: Float = 1f,
    val hidden: Boolean = false,
    val locked: Boolean = false,
    // relleno de figuras (RECT/CIRCLE/TRIANGLE) con su transparencia, estilo TV
    val fill: Boolean = true,
    val fillAlpha: Float = 0.2f,
    val label: String = "",
    val labelH: DrawLabelH = DrawLabelH.CENTER,
    val labelV: DrawLabelV = DrawLabelV.MIDDLE,
    // vacio = usa el color de la linea
    val labelColorHex: String = "",
    // px base del texto (se multiplica por la densidad al dibujar)
    val labelSize: Float = 12f
)

const val MAX_DRAWS_PER_SYMBOL = 20

// figuras que admiten relleno
fun DrawKind.supportsFill(): Boolean = this == DrawKind.RECT || this == DrawKind.CIRCLE || this == DrawKind.TRIANGLE

// encoding manual sin librerias:
// id;kind;sTime;sPrice;eTime;ePrice;color;width;hidden;locked;fill;fillAlpha;label;labelH;labelV;labelColor;labelSize
// los viejos de 10/12 campos se leen con defaults (sin relleno / sin texto)
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
        bool01(o.locked),
        bool01(o.fill),
        o.fillAlpha.toString(),
        // ";" y "|" son separadores del codec
        o.label.replace(';', ' ').replace('|', ' '),
        o.labelH.name,
        o.labelV.name,
        o.labelColorHex,
        o.labelSize.toString()
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
            colorHex = p[6].ifBlank { ChartColors.GOLD_HEX },
            width = p.reqWidth(7),
            hidden = p.flag(8),
            locked = p.flag(9),
            // viejos sin esos campos: sin relleno, igual que hoy
            fill = if (p.size >= 11) p.flag(10) else false,
            fillAlpha = if (p.size >= 12) p[11].toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.2f else 0.2f,
            label = p.getOrNull(12).orEmpty(),
            labelH = p.getOrNull(13).toLabelH(),
            labelV = p.getOrNull(14).toLabelV(),
            labelColorHex = p.getOrNull(15).orEmpty(),
            labelSize = p.getOrNull(16)?.toFloatOrNull()?.coerceIn(8f, 28f) ?: 12f
        )
    }.take(MAX_DRAWS_PER_SYMBOL)
}

internal fun String?.toLabelH(): DrawLabelH =
    runCatching { DrawLabelH.valueOf(this.orEmpty()) }.getOrDefault(DrawLabelH.CENTER)

internal fun String?.toLabelV(): DrawLabelV =
    runCatching { DrawLabelV.valueOf(this.orEmpty()) }.getOrDefault(DrawLabelV.MIDDLE)


