package com.defitracker.app.presentation.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.defitracker.app.alerts.DivMonitorPrefs
import com.defitracker.app.data.local.DivAlertEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsBottomSheet(
    onDismiss: () -> Unit,
    onOpenAlert: (symbol: String, source: String, interval: String) -> Unit,
    viewModel: AlertsViewModel = hiltViewModel()
) {
    val state = viewModel.state.value
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF12141A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Alertas RSI",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (state.monitoring) "Vigilando" else "Pausado",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = state.monitoring,
                        onCheckedChange = { viewModel.toggleMonitoring(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF1ECB81)
                        )
                    )
                    // escobita = marcar todas como vistas
                    IconButton(onClick = { viewModel.markAllSeen() }) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "Marcar todas como vistas",
                            tint = Color.White
                        )
                    }
                }
            }

            Text(
                text = "Futuros · toca una alerta para ver su gráfica",
                color = Color.Gray,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))

            // TFs configurables, por defecto 5m/15m/30m/1h
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DivMonitorPrefs.ALL_INTERVALS.take(7).forEach { tf ->
                    val selected = tf in state.intervals
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.toggleInterval(tf) },
                        label = { Text(tf, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1ECB81).copy(alpha = 0.25f),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1A1D23),
                            labelColor = Color.Gray
                        )
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DivMonitorPrefs.ALL_INTERVALS.drop(7).forEach { tf ->
                    val selected = tf in state.intervals
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.toggleInterval(tf) },
                        label = { Text(tf, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1ECB81).copy(alpha = 0.25f),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1A1D23),
                            labelColor = Color.Gray
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (state.alerts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color.Gray.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Sin divergencias por ahora", color = Color.Gray, fontSize = 13.sp)
                    Text("Te aviso al toque cuando caiga una", color = Color.Gray, fontSize = 11.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = state.alerts,
                        key = { it.id }
                    ) { alert ->
                        AlertRow(
                            alert = alert,
                            onClick = {
                                viewModel.markSeenAndOpen(alert) { a ->
                                    onOpenAlert(a.symbol, a.source, a.interval)
                                }
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AlertRow(
    alert: DivAlertEntity,
    onClick: () -> Unit
) {
    val accent = when {
        alert.status == "PRE" -> Color(0xFFFFD60A)
        alert.bullish -> Color(0xFF1ECB81)
        else -> Color(0xFFF6465D)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (alert.seen) Color(0xFF1A1D23)
                else Color(0xFF1A1D23).copy(alpha = 0.6f)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // flecha arriba/abajo bien notoria segun direccion
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (alert.bullish) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = (if (alert.status == "PRE") "Pre-" else "") + (if (alert.bullish) "Alcista" else "Bajista"),
                tint = accent,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = alert.message,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = if (alert.seen) FontWeight.Normal else FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${alert.kindLabel()} · ${formatTime(alert.createdAt)}${if (alert.seen) "" else " · nuevo"}",
                color = if (alert.seen) Color.Gray else accent,
                fontSize = 11.sp
            )
        }
        if (!alert.seen) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
    }
}

private fun DivAlertEntity.kindLabel(): String = when (kind) {
    "REG_BULL" -> "Alcista regular"
    "HID_BULL" -> "Alcista oculta"
    "REG_BEAR" -> "Bajista regular"
    "HID_BEAR" -> "Bajista oculta"
    else -> kind
}

private fun formatTime(ms: Long): String {
    return try {
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(ms))
    } catch (_: Exception) {
        ""
    }
}
