package com.defitracker.app.presentation.crypto_list.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.defitracker.app.ui.theme.Geist
import com.defitracker.app.ui.theme.MonogramColors

// Icono del par: primero el logo real (CoinCap, Binance) y, si ninguna fuente lo tiene
// (commodities como XAU, indices de TradingView), un badge con monograma en vez de una
// letra pelada. El 1000PEPE prueba tambien el nombre pelado: PEPE tiene el mismo logo.
@Composable
fun PairIcon(
    baseAsset: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val context = LocalContext.current
    val urls = remember(baseAsset) { pairIconUrls(baseAsset) }
    var iconIdx by remember(baseAsset) { mutableIntStateOf(0) }
    var iconFailed by remember(baseAsset) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MonogramColors.surface)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (iconFailed) {
            MonogramBadge(baseAsset)
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(urls[iconIdx])
                    .crossfade(false)
                    .memoryCacheKey("coin-$baseAsset-$iconIdx")
                    .diskCacheKey("coin-$baseAsset-$iconIdx")
                    .build(),
                contentDescription = baseAsset,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onError = {
                    if (iconIdx < urls.lastIndex) iconIdx++ else iconFailed = true
                }
            )
        }
    }
}

@Composable
private fun MonogramBadge(baseAsset: String) {
    val accent = monogramAccent(baseAsset)
    val monogram = pairMonogram(baseAsset)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.42f), MonogramColors.surface)))
            .border(1.dp, accent.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = monogram,
            color = MonogramColors.text,
            fontSize = if (monogram.length == 1) 16.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Geist
        )
    }
}

// URLs del logo, en orden: CoinCap (listados viejos), Binance (casi todo lo demas).
internal fun pairIconUrls(baseAsset: String): List<String> {
    val bases = listOf(baseAsset) +
        listOfNotNull(baseAsset.removePrefix("1000").takeIf { it != baseAsset })
    return bases.flatMap { base ->
        listOf(
            "https://assets.coincap.io/assets/icons/${base.lowercase()}@2x.png",
            "https://bin.bnbstatic.com/static/assets/logos/${base.uppercase()}.png"
        )
    }
}

// Monograma para los pares sin logo en ninguna fuente: commodities e indices de TradingView.
// Solo ASCII: la fuente de la app no trae simbolos raros.
internal fun pairMonogram(baseAsset: String): String = when (baseAsset) {
    "XAU" -> "Au"
    "USOIL" -> "Oil"
    "BTC.D" -> "B%"
    "ETH.D" -> "E%"
    "USDT.D" -> "U%"
    "USDC.D" -> "C%"
    "OTHERS.D" -> "O%"
    "TOTAL" -> "T"
    "TOTAL2" -> "T2"
    "TOTAL3" -> "T3"
    "OTHERS" -> "OT"
    else -> {
        val clean = baseAsset.removePrefix("1000").ifEmpty { baseAsset }
        clean.take(2).uppercase()
    }
}

// Acento del badge: commodities en dorado, indices de TradingView en violeta, el resto por hash.
internal fun monogramAccent(baseAsset: String): Color = when {
    baseAsset == "XAU" || baseAsset == "USOIL" -> MonogramColors.gold
    baseAsset.endsWith(".D") -> MonogramColors.violet
    baseAsset.startsWith("TOTAL") || baseAsset == "OTHERS" -> MonogramColors.cyan
    else -> MonogramColors.accents[Math.abs(baseAsset.hashCode()) % MonogramColors.accents.size]
}
