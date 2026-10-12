package com.defitracker.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

// punto de disparo del jalon, en px (~5cm de dedo, un jalon corto normal)
const val PULL_TRIGGER_PX = 140f

// variante aislada: lee el estado adentro para que al jalar solo se recomponga
// el circulito y no toda la pantalla (asi baja fluido, sin trabones)
@Composable
fun PullSpinner(
    pull: MutableFloatState,
    refreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val px = pull.floatValue
    PullSpinner(
        fraction = px / PULL_TRIGGER_PX,
        refreshing = refreshing,
        pullPx = px,
        modifier = modifier
    )
}

// circulito como en las demas apps: baja con tu dedo creciendo y girando,
// al pasar el punto queda verde full, al recargar se clava girando en su descanso
@Composable
fun PullSpinner(
    fraction: Float,
    refreshing: Boolean,
    pullPx: Float = 0f,
    modifier: Modifier = Modifier
) {
    if (!refreshing && fraction <= 0.03f) return
    val ready = !refreshing && fraction >= 1f
    // viaja amortiguado con el dedo (menos que el dedo, tope ~80dp)
    val travelDp = with(LocalDensity.current) {
        (pullPx.coerceAtMost(PULL_TRIGGER_PX * 1.2f) * 0.55f).toDp()
    }
    val scale = if (refreshing) 1f else 0.5f + 0.5f * fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .offset(y = if (refreshing) 72.dp else travelDp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .size(if (ready) 52.dp else 48.dp)
            .alpha(1f)
            .background(
                if (ready) Color(0xFF1ECB81) else Color(0xCC1A1D23),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (refreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                color = Color(0xFF1ECB81),
                strokeWidth = 3.dp
            )
        } else {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Recargar",
                tint = if (ready) Color.Black else Color(0xFF1ECB81),
                modifier = Modifier
                    .size(28.dp)
                    .rotate(fraction.coerceIn(0f, 1f) * 360f)
            )
        }
    }
}
