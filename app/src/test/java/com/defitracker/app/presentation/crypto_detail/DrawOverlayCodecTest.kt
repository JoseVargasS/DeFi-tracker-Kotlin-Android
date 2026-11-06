package com.defitracker.app.presentation.crypto_detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawOverlayCodecTest {

    private fun overlay() = DrawOverlay(
        id = "d1",
        kind = DrawKind.PRICE_LINE,
        start = FibAnchor(1_700_000_000_000L, 1.23),
        end = FibAnchor(1_700_000_000_000L, 1.23),
        label = "soporte",
        labelH = DrawLabelH.LEFT,
        labelV = DrawLabelV.TOP,
        labelColorHex = "#FFEE58",
        labelSize = 15f
    )

    @Test
    fun `text settings survive a round trip`() {
        val decoded = decodeDrawOverlays(encodeDrawOverlays(listOf(overlay()))).single()
        assertEquals("soporte", decoded.label)
        assertEquals(DrawLabelH.LEFT, decoded.labelH)
        assertEquals(DrawLabelV.TOP, decoded.labelV)
        assertEquals("#FFEE58", decoded.labelColorHex)
        assertEquals(15f, decoded.labelSize, 0.001f)
        assertEquals(DrawKind.PRICE_LINE, decoded.kind)
        assertEquals(1.23, decoded.start.price, 0.0001)
    }

    @Test
    fun `codec separators inside the label are sanitized`() {
        val raw = encodeDrawOverlays(listOf(overlay().copy(label = "a;b|c")))
        // 4 separadores de campo + 5 puntos y coma de campos base... basta con que
        // el registro siga teniendo un solo bloque y el label sobreviva
        assertEquals(1, raw.split("|").size)
        assertEquals("a b c", decodeDrawOverlays(raw).single().label)
    }

    @Test
    fun `legacy records without text fields decode with defaults`() {
        val old12 = "d2;H_LINE;1700000000000;2.5;1700000000000;2.5;#FFFFFF;1.5;0;0;1;0.3"
        val decoded = decodeDrawOverlays(old12).single()
        assertEquals("", decoded.label)
        assertEquals(DrawLabelH.CENTER, decoded.labelH)
        assertEquals(DrawLabelV.MIDDLE, decoded.labelV)
        assertEquals("", decoded.labelColorHex)
        assertEquals(12f, decoded.labelSize, 0.001f)
        assertEquals(0.3f, decoded.fillAlpha, 0.001f)
    }

    @Test
    fun `label size is clamped to the drawable range`() {
        val raw = "d3;LINE;1;1;2;2;#FFF;1;0;0;0;0.2;t;CENTER;MIDDLE;;99"
        assertEquals(28f, decodeDrawOverlays(raw).single().labelSize, 0.001f)
    }
}
