package com.defitracker.app.presentation.crypto_list.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.defitracker.app.domain.model.CryptoPair
import com.defitracker.app.ui.theme.AppGreen
import com.defitracker.app.ui.theme.AppRed
import com.defitracker.app.ui.theme.Geist
import com.defitracker.app.ui.theme.GeistTnum

@Composable
fun CryptoPairItem(
    pair: CryptoPair,
    sparkline: List<Double>,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val changeColor by animateColorAsState(
        targetValue = if (pair.isPositive) AppGreen else AppRed,
        animationSpec = tween(durationMillis = 180),
        label = "priceChangeColor"
    )
    // nota: banda $1-10, solo recién-cruzadas (sparkline toco sub-$1) muestran 4, consolidadas 2
    val priceText = remember(pair.price, sparkline) { stickyListPrice(pair.price, sparkline) }

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Coin Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF181A20))
                    .padding(4.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("https://assets.coincap.io/assets/icons/${pair.baseAsset.lowercase()}@2x.png")
                        .crossfade(false)
                        .memoryCacheKey("coin-${pair.baseAsset}")
                        .diskCacheKey("coin-${pair.baseAsset}")
                        .build(),
                    contentDescription = pair.baseAsset,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // una sola linea con ellipsis, nunca parte el simbolo en dos
                if (pair.source == "MEXC") {
                    // perps sin slash (ETHUSDT)
                    Text(
                        text = pair.baseAsset + pair.quoteAsset,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    // spot con slash chico en el mismo Text para que no salte de linea
                    Text(
                        text = buildAnnotatedString {
                            append(pair.baseAsset)
                            pushStyle(SpanStyle(fontSize = 11.sp, color = Color(0xFF777777), fontWeight = FontWeight.SemiBold))
                            append(" /${pair.quoteAsset}")
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = pair.symbol,
                    fontSize = 12.sp,
                    color = Color(0xFFAAAAAA),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // mini grafica 24h al medio
            Sparkline(values = sparkline, positive = pair.isPositive)

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 86.dp)) {
                Text(
                    text = priceText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    style = GeistTnum
                )
                Text(
                    text = "${if (pair.isPositive) "+" else ""}${pair.priceChangePercent}%",
                    color = changeColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    fontFamily = Geist
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFF777777),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// nota: banda $1-10 con 4 decimales del repo; si el sparkline (24h) va todo sobre $1, recorta a 2
private fun stickyListPrice(price: String, sparkline: List<Double>): String {
    val d = price.toDoubleOrNull() ?: return price
    if (d < 1.0 || d >= 10.0 || sparkline.size < 2) return price
    if (sparkline.all { it >= 1.0 }) return String.format(java.util.Locale.US, "%.2f", d)
    return price
}

@Composable
private fun Sparkline(values: List<Double>, positive: Boolean) {
    if (values.size < 2) {
        // placeholder pulsante mientras cargan los cierres
        val pulse = rememberInfiniteTransition(label = "sparkPulse").animateFloat(
            initialValue = 0.25f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse
            ),
            label = "sparkAlpha"
        )
        Box(
            modifier = Modifier
                .width(70.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Gray.copy(alpha = pulse.value))
        )
        return
    }
    val color = if (positive) AppGreen else AppRed
    Canvas(modifier = Modifier.width(70.dp).height(28.dp)) {
        val min = values.minOrNull() ?: return@Canvas
        val max = values.maxOrNull() ?: return@Canvas
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v ->
            Offset(
                i * stepX,
                size.height - ((v - min) / range * size.height).toFloat()
            )
        }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1..pts.lastIndex) lineTo(pts[i].x, pts[i].y)
        }
        drawPath(path, color, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, radius = 2.dp.toPx(), center = pts.last())
    }
}
