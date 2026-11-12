package com.defitracker.app.presentation.crypto_list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.defitracker.app.core.Brokers
import com.defitracker.app.domain.model.AvailableCryptoPair
import com.defitracker.app.ui.theme.BrokerColors
import com.defitracker.app.ui.theme.LineDiv

// un par con sus brokers; el orden de `entries` es el de los chips
internal data class SearchPairGroup(
    val baseAsset: String,
    val quoteAsset: String,
    val entries: List<AvailableCryptoPair>
) {
    val displayName: String = if (quoteAsset.isBlank()) baseAsset else "$baseAsset/$quoteAsset"
    val key: String = "$baseAsset|$quoteAsset"
}

internal const val MAX_SEARCH_GROUPS = 30
internal val SEARCH_ROW_HEIGHT = 44.dp

internal fun AvailableCryptoPair.matchesQuery(query: String): Boolean =
    symbol.contains(query, true) ||
        baseAsset.contains(query, true) ||
        quoteAsset.contains(query, true) ||
        displayName.contains(query, true) ||
        keywords.contains(query, true)

// USDT y USDC primero: al buscar "avax" el par liquido sale arriba
private fun quoteRank(quote: String): Int = when (quote.uppercase()) {
    "USDT" -> 0
    "USDC" -> 1
    "USD" -> 2
    else -> 3
}

/**
 * Agrupa los resultados por par (base+quote) para pintar una fila con los brokers como chips.
 * Orden: mas brokers primero, luego USDT/USDC, luego alfabetico. Los indices de TradingView
 * no son brokers y van al final, una fila cada uno.
 */
internal fun groupSearchPairs(matches: List<AvailableCryptoPair>): List<SearchPairGroup> {
    val brokerPairs = matches.filter { it.source != Brokers.TV && !it.source.isNullOrBlank() }
    val tvIndexes = matches.filter { it.source == Brokers.TV }
    val groups = brokerPairs
        .groupBy { it.baseAsset.uppercase() to it.quoteAsset.uppercase() }
        .map { (pair, entries) ->
            SearchPairGroup(
                baseAsset = pair.first,
                quoteAsset = pair.second,
                entries = entries.distinctBy { it.source }.sortedBy { Brokers.rank(it.source) }
            )
        }
        .sortedWith(
            compareByDescending<SearchPairGroup> { it.entries.size }
                .thenBy { quoteRank(it.quoteAsset) }
                .thenBy { it.displayName }
        )
    val tvRows = tvIndexes
        .map { SearchPairGroup(it.baseAsset, it.quoteAsset, listOf(it)) }
        .sortedBy { it.displayName }
    return (groups + tvRows).take(MAX_SEARCH_GROUPS)
}

@Composable
internal fun SearchPairRow(
    group: SearchPairGroup,
    trackedKeys: Set<String>,
    onPick: (AvailableCryptoPair) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SEARCH_ROW_HEIGHT)
            .padding(start = 16.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = group.displayName,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            group.entries.forEach { entry ->
                BrokerChip(
                    color = BrokerColors.of(entry.source),
                    label = if (entry.source == Brokers.TV) "TV" else entry.source.orEmpty(),
                    added = "${entry.symbol}-${entry.source}" in trackedKeys,
                    onClick = { onPick(entry) }
                )
            }
        }
    }
    Divider(color = LineDiv, thickness = 0.5.dp)
}

@Composable
private fun BrokerChip(color: Color, label: String, added: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (added) color.copy(alpha = 0.45f) else color,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = if (added) 0.08f else 0.18f))
            .clickable(enabled = !added) { onClick() }
            .padding(horizontal = 7.dp, vertical = 4.dp)
    )
}
