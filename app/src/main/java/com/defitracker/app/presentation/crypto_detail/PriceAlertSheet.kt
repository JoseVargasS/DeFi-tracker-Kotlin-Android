package com.defitracker.app.presentation.crypto_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.defitracker.app.data.local.PriceAlertEntity
import com.defitracker.app.presentation.components.SheetSectionTitle
import com.defitracker.app.ui.theme.Geist

// campanita del chart: alerta puntual o rango, LONG/SHORT.
// Si el precio toca la zona suena la noti; si la quitaste, vuelve a sonar cada 5 min.
@Composable
fun PriceAlertSheet(
    symbol: String,
    currentPrice: Double,
    alerts: List<PriceAlertEntity>,
    onAdd: (direction: String, target: Double, low: Double, high: Double) -> Unit,
    onDelete: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit
) {
    var direction by remember { mutableStateOf("LONG") }
    var modeRange by remember { mutableStateOf(false) }
    var priceTxt by remember(currentPrice) {
        mutableStateOf(if (currentPrice > 0) currentPrice.toString() else "")
    }
    var lowTxt by remember { mutableStateOf("") }
    var highTxt by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        SheetSectionTitle("Alertas de precio · $symbol")
        Text(
            "Te avisamos cuando toque tu zona. LONG verde, SHORT rojo.",
            color = Color.Gray,
            fontSize = 12.sp,
            fontFamily = Geist
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("LONG", "SHORT").forEach { d ->
                val on = direction == d
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (on) (if (d == "LONG") Color(0xFF1ECB81) else Color(0xFFF6465D))
                            else Color(0xFF1A1D23),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { direction = d }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        d,
                        color = if (on) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Rango", color = Color.White, fontSize = 13.sp, fontFamily = Geist)
            Spacer(Modifier.width(8.dp))
            Switch(checked = modeRange, onCheckedChange = { modeRange = it })
            if (!modeRange && currentPrice > 0) {
                Spacer(Modifier.width(8.dp))
                Text("Actual: $currentPrice", color = Color.Gray, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        if (modeRange) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lowTxt,
                    onValueChange = { lowTxt = it },
                    label = { Text("Desde", fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = highTxt,
                    onValueChange = { highTxt = it },
                    label = { Text("Hasta", fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = fieldColors()
                )
            }
        } else {
            OutlinedTextField(
                value = priceTxt,
                onValueChange = { priceTxt = it },
                label = { Text("Precio objetivo", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
        }
        if (error.isNotBlank()) {
            Text(error, color = Color(0xFFF6465D), fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (modeRange) {
                    val lo = lowTxt.toDoubleOrNull() ?: 0.0
                    val hi = highTxt.toDoubleOrNull() ?: 0.0
                    if (lo <= 0 || hi <= lo) {
                        error = "Pon un rango válido (hasta mayor que desde)"
                        return@Button
                    }
                    error = ""
                    onAdd(direction, 0.0, lo, hi)
                    lowTxt = ""
                    highTxt = ""
                } else {
                    val t = priceTxt.toDoubleOrNull() ?: 0.0
                    if (t <= 0) {
                        error = "Pon un precio válido"
                        return@Button
                    }
                    error = ""
                    onAdd(direction, t, 0.0, 0.0)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1ECB81), contentColor = Color.Black)
        ) {
            Text("Crear alerta $direction", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        if (alerts.isEmpty()) {
            Text("Sin alertas en este par todavía", color = Color.Gray, fontSize = 12.sp)
        } else {
            LazyColumn(
                modifier = Modifier.height(220.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(alerts, key = { it.id }) { a ->
                    val label = if (a.low > 0 && a.high > 0) "${a.low} – ${a.high}" else "${a.targetPrice}"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A1D23), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (a.direction == "SHORT") Color(0xFFF6465D).copy(alpha = 0.2f)
                                    else Color(0xFF1ECB81).copy(alpha = 0.2f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                a.direction,
                                color = if (a.direction == "SHORT") Color(0xFFF6465D) else Color(0xFF1ECB81),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = a.enabled, onCheckedChange = { onToggle(a.id, it) })
                        IconButton(onClick = { onDelete(a.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = Color.Gray)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Color(0xFF1ECB81),
    focusedBorderColor = Color(0xFF1ECB81),
    unfocusedBorderColor = Color.Gray,
    focusedLabelColor = Color(0xFF1ECB81),
    unfocusedLabelColor = Color.Gray
)
