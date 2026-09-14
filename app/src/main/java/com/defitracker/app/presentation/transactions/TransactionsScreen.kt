package com.defitracker.app.presentation.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.defitracker.app.data.remote.dto.EtherscanTransactionDto
import com.defitracker.app.ui.theme.PlexMono
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state = viewModel.state.value
    val groupedTransactions = remember(state.transactions) {
        state.transactions
            .groupBy { it.network ?: "Unknown" }
            .toSortedMap(compareBy { networkOrder(it) })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TransactionsHeader(
                selectedAddress = state.selectedAddress,
                totalTransactions = state.transactions.size,
                networkCount = groupedTransactions.size,
                isLoading = state.isLoading,
                onRefresh = { viewModel.fetchTransactions(state.selectedAddress) }
            )
        }

        item {
            WalletSelector(
                selectedAddress = state.selectedAddress,
                wallets = state.wallets,
                onWalletSelected = viewModel::onAddressSelected
            )
        }

        if (state.error != null) {
            item {
                MessageCard(
                    title = "Could not load transactions",
                    message = state.error
                )
            }
        }

        if (state.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF0ECB81))
                }
            }
        } else if (state.transactions.isEmpty() && state.error == null) {
            item {
                MessageCard(
                    title = "No transactions found",
                    message = "Select a saved wallet or refresh this address."
                )
            }
        } else {
            groupedTransactions.forEach { (network, transactions) ->
                item(key = "header-$network") {
                    NetworkSectionHeader(network, transactions)
                }
                items(
                    items = transactions,
                    key = { tx -> "${tx.network}-${tx.hash}-${tx.tokenSymbol}-${tx.value}" }
                ) { tx ->
                    TransactionRow(tx = tx, userAddress = state.selectedAddress)
                }
            }

            if (state.canLoadMore) {
                item(key = "load-more-transactions") {
                    OutlinedButton(
                        onClick = viewModel::loadMoreTransactions,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant)
                        )
                    ) {
                        Text("View 5 more", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionsHeader(
    selectedAddress: String,
    totalTransactions: Int,
    networkCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Transaction History",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = selectedAddress.shortAddress(),
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryChip("$totalTransactions txs")
                SummaryChip("$networkCount networks")
            }
        }

        IconButton(
            onClick = onRefresh,
            enabled = selectedAddress.isNotBlank() && !isLoading,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = Color.White,
                disabledContentColor = Color.Gray
            )
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
        }
    }
}

@Composable
private fun WalletSelector(
    selectedAddress: String,
    wallets: List<com.defitracker.app.data.local.WalletEntity>,
    onWalletSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    if (wallets.isEmpty()) {
        MessageCard(
            title = "No saved wallet",
            message = "Save a wallet in Wallet Explorer to inspect its history."
        )
        return
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant)
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Selected wallet", color = Color.Gray, fontSize = 11.sp)
                    Text(
                        text = wallets.find { it.address == selectedAddress }?.name?.takeIf { it.isNotBlank() }
                            ?: selectedAddress.shortAddress(),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.Default.ExpandMore, contentDescription = null, tint = Color.Gray)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            wallets.forEach { wallet ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = wallet.name.takeIf { it.isNotBlank() } ?: "Wallet",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(wallet.address.shortAddress(), color = Color.Gray, fontSize = 11.sp)
                        }
                    },
                    onClick = {
                        onWalletSelected(wallet.address)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun NetworkSectionHeader(
    network: String,
    transactions: List<EtherscanTransactionDto>
) {
    val incoming = transactions.count { tx ->
        tx.value.toAmount(tx.tokenDecimal).signum() > 0
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(networkColor(network))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(network, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = "$incoming transfers",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TransactionRow(tx: EtherscanTransactionDto, userAddress: String) {
    val isSent = tx.from.equals(userAddress, ignoreCase = true)
    val direction = if (isSent) "Sent" else "Received"
    val directionColor = if (isSent) Color(0xFFF6465D) else Color(0xFF0ECB81)
    val directionIcon = if (isSent) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
    val amount = remember(tx.value, tx.tokenDecimal) { tx.value.toAmount(tx.tokenDecimal) }
    val timestamp = remember(tx.timeStamp) { tx.timeStamp.formatTimestamp() }
    val tokenSymbol = tx.tokenSymbol ?: tx.symbol ?: "Token"
    val counterparty = if (isSent) tx.to else tx.from

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF20232B)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(directionColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(directionIcon, contentDescription = null, tint = directionColor, modifier = Modifier.size(21.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(direction, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = networkColor(tx.network ?: "").copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = tx.network ?: "Unknown",
                                color = networkColor(tx.network ?: ""),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(timestamp, color = Color.Gray, fontSize = 12.sp)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (isSent) "-" else "+"}${amount.formatTokenAmount()}",
                        color = directionColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = PlexMono,
                        textAlign = TextAlign.End
                    )
                    Text(tokenSymbol, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TransactionMeta("To/From", counterparty.shortAddress())
                TransactionMeta("Hash", tx.hash.shortHash())
            }
        }
    }
}

@Composable
private fun TransactionMeta(label: String, value: String) {
    Column {
        Text(label, color = Color.Gray, fontSize = 10.sp)
        Text(value, color = Color(0xFFADB1B8), fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MessageCard(title: String, message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(message, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun SummaryChip(text: String) {
    AssistChip(
        onClick = {},
        label = { Text(text, fontSize = 11.sp) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = Color(0xFFADB1B8)
        ),
        border = null
    )
}

private fun String.toAmount(decimals: String?): BigDecimal {
    val rawValue = runCatching { BigDecimal(this) }.getOrDefault(BigDecimal.ZERO)
    val scale = decimals?.toIntOrNull()?.coerceIn(0, 36) ?: 18
    return rawValue.divide(BigDecimal.TEN.pow(scale), 18, RoundingMode.DOWN).stripTrailingZeros()
}

private fun BigDecimal.formatTokenAmount(): String {
    if (signum() == 0) return "0"
    val abs = abs()
    return when {
        abs < BigDecimal("0.000001") -> "<0.000001"
        abs < BigDecimal.ONE -> setScale(8, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
        abs < BigDecimal("1000") -> setScale(6, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
        else -> setScale(4, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
    }
}

private fun String.formatTimestamp(): String {
    val seconds = toLongOrNull() ?: return "Unknown date"
    val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    return formatter.format(Date(seconds * 1000))
}

private fun String.shortAddress(): String {
    if (isBlank()) return "No wallet selected"
    return if (length <= 14) this else "${take(6)}...${takeLast(6)}"
}

private fun String.shortHash(): String = if (length <= 14) this else "${take(8)}...${takeLast(6)}"

private fun networkColor(network: String): Color {
    return when (network) {
        "Ethereum" -> Color(0xFF627EEA)
        "BSC" -> Color(0xFFF3BA2F)
        "Base" -> Color(0xFF4C8DFF)
        "Optimism" -> Color(0xFFFF4F4F)
        "Polygon" -> Color(0xFF8247E5)
        "Arbitrum" -> Color(0xFF28A0F0)
        "Solana" -> Color(0xFF14F195)
        else -> Color(0xFFADB1B8)
    }
}

private fun networkOrder(network: String): Int {
    return when (network) {
        "Base" -> 0
        "Ethereum" -> 1
        "BSC" -> 2
        "Polygon" -> 3
        "Arbitrum" -> 4
        "Optimism" -> 5
        "Solana" -> 6
        else -> 99
    }
}
