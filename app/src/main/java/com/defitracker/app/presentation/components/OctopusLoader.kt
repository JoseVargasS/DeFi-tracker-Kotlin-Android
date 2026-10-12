package com.defitracker.app.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.defitracker.app.R
import com.defitracker.app.ui.theme.Geist

// loader propio de Octopus: logo latiendo + anillo girando, sin videos ni librerias
@Composable
fun OctopusLoader(
    message: String = "Cargando…",
    modifier: Modifier = Modifier
) {
    val pulse by rememberInfiniteTransition(label = "octo").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val sweep by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Restart),
        label = "sweep"
    )
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(84.dp)) {
                drawArc(
                    color = Color(0xFF1ECB81),
                    startAngle = sweep,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 6f, cap = StrokeCap.Round)
                )
            }
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = "Octopus",
                modifier = Modifier
                    .size(56.dp)
                    .scale(pulse)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            color = Color.Gray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = Geist
        )
    }
}
