package com.defitracker.app.presentation.crypto_list.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.defitracker.app.domain.model.CryptoPair
import com.defitracker.app.ui.theme.Rajdhani

@Composable
fun CryptoPairItem(
    pair: CryptoPair,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val changeColor by animateColorAsState(
        targetValue = if (pair.isPositive) Color(0xFF1ECB81) else Color(0xFFE74C4C),
        animationSpec = tween(durationMillis = 180),
        label = "priceChangeColor"
    )
    val changeBackground by animateColorAsState(
        targetValue = if (pair.isPositive) Color(0xFF132B22) else Color(0xFF2B171A),
        animationSpec = tween(durationMillis = 180),
        label = "priceChangeBackground"
    )

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
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
                // ponytail: perps sin slash (ETHUSDT), spot con slash
                if (pair.source == "MEXC") {
                    Text(
                        text = pair.baseAsset + pair.quoteAsset,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = pair.baseAsset,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = " /${pair.quoteAsset}",
                            fontSize = 12.sp,
                            color = Color(0xFF777777),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
                Text(
                    text = pair.symbol,
                    fontSize = 12.sp,
                    color = Color(0xFFAAAAAA),
                    fontWeight = FontWeight.Medium
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = pair.price,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = Rajdhani
                )
                
                Surface(
                    color = changeBackground,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "${if (pair.isPositive) "+" else ""}${pair.priceChangePercent}%",
                        color = changeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = Rajdhani,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

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
