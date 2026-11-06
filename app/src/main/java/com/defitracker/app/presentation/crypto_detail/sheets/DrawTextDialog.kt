package com.defitracker.app.presentation.crypto_detail.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.defitracker.app.presentation.components.PillGrid
import com.defitracker.app.presentation.components.PillOption
import com.defitracker.app.presentation.crypto_detail.DrawLabelH
import com.defitracker.app.presentation.crypto_detail.DrawLabelV
import com.defitracker.app.ui.theme.SheetBg

// tamanos de texto del dibujo en px base (se escalan por densidad al dibujar)
val DRAW_LABEL_SIZES = listOf(10f, 12f, 15f, 18f)

// texto + color + tamano + alineacion de cualquier dibujo (lineas y figuras)
@Composable
fun DrawTextDialog(
    label: String,
    colorHex: String,
    size: Float,
    alignH: DrawLabelH,
    alignV: DrawLabelV,
    lineColorHex: String,
    onLabel: (String) -> Unit,
    onColor: (String) -> Unit,
    onSize: (Float) -> Unit,
    onAlign: (DrawLabelH, DrawLabelV) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Listo", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = SheetBg,
        title = {
            Text("Texto del dibujo", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = label,
                    onValueChange = onLabel,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Escribe el texto", color = Color.Gray) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp)
                )
                Spacer(Modifier.height(12.dp))
                SectionLabel("Tamaño")
                PillGrid(
                    options = DRAW_LABEL_SIZES.map { s ->
                        PillOption(
                            title = s.toInt().toString(),
                            selected = size == s,
                            onToggle = { onSize(s) }
                        )
                    }
                )
                Spacer(Modifier.height(12.dp))
                SectionLabel("Horizontal")
                PillGrid(
                    options = listOf(
                        "Izq" to DrawLabelH.LEFT,
                        "Centro" to DrawLabelH.CENTER,
                        "Der" to DrawLabelH.RIGHT
                    ).map { (title, h) ->
                        PillOption(title, alignH == h, { onAlign(h, alignV) })
                    },
                    columns = 3
                )
                Spacer(Modifier.height(12.dp))
                SectionLabel("Vertical")
                PillGrid(
                    options = listOf(
                        "Arriba" to DrawLabelV.TOP,
                        "Medio" to DrawLabelV.MIDDLE,
                        "Abajo" to DrawLabelV.BOTTOM
                    ).map { (title, v) ->
                        PillOption(title, alignV == v, { onAlign(alignH, v) })
                    },
                    columns = 3
                )
                Spacer(Modifier.height(12.dp))
                SectionLabel("Color del texto")
                Spacer(Modifier.height(4.dp))
                WideColorPicker(
                    currentHex = colorHex.ifBlank { lineColorHex },
                    onPick = onColor,
                    key = "drawText"
                )
            }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
