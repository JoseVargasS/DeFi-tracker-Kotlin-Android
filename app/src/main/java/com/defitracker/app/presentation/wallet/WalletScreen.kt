package com.defitracker.app.presentation.wallet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.defitracker.app.ui.theme.AppGold
import com.defitracker.app.ui.theme.AppGreen
import com.defitracker.app.ui.theme.AppRed
import com.defitracker.app.ui.theme.Geist
import com.defitracker.app.ui.theme.GeistTnum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    viewModel: WalletViewModel = hiltViewModel()
) {
    val state = viewModel.state.value
    var addressInput by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    val allAssets = remember(state.balances) { state.balances.values.flatten() }
    val totalWorth = remember(allAssets) {
        allAssets.sumOf { (it.amount ?: 0.0) * (it.price ?: 0.0) }
    }
    val selectedWallet = remember(state.wallets, state.selectedAddress) {
        state.wallets.find { it.address == state.selectedAddress }
    }
    val selectedLabel = selectedWallet?.name?.takeIf { it.isNotBlank() } ?: "Selected wallet"
    val shortAddress = state.selectedAddress
        .takeIf { it.isNotBlank() }
        ?.let { "${it.take(6)}...${it.takeLast(4)}" }
        ?: "No wallet selected"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Wallet Explorer",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = shortAddress,
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                IconButton(
                    onClick = { viewModel.fetchBalances(state.selectedAddress) },
                    enabled = state.selectedAddress.isNotBlank() && !state.isLoading,
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

        // Wallet Selection / Input
        item {
            var walletNameInput by remember { mutableStateOf("") }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .animateContentSize()
                ) {
                    if (state.wallets.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        Text("Active wallet", color = AppGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { expanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = selectedLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                            Text(
                                                text = shortAddress,
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    state.wallets.forEach { wallet ->
                                        DropdownMenuItem(
                                            text = { 
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Column {
                                                        if (wallet.name.isNotEmpty()) {
                                                            Text(
                                                                text = wallet.name, 
                                                                color = Color.White, 
                                                                fontSize = 15.sp, // Larger font
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        Text(
                                                            text = wallet.address, // More characters visible
                                                            color = Color.Gray, 
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                viewModel.onAddressSelected(wallet.address)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { 
                                        if (selectedWallet != null) {
                                            addressInput = selectedWallet.address
                                            walletNameInput = selectedWallet.name
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { clipboardManager.setText(AnnotatedString(state.selectedAddress)) },
                                    enabled = state.selectedAddress.isNotBlank(),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.deleteWallet(state.selectedAddress) },
                                    enabled = state.selectedAddress.isNotBlank(),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AppRed.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    } else {
                        Text("No saved wallets", color = AppGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Add a public wallet address to view balances by chain.", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text("Add or edit wallet", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    
                    OutlinedTextField(
                        value = walletNameInput,
                        onValueChange = { walletNameInput = it },
                        placeholder = { Text("Wallet Name (Optional)", color = Color.Gray, fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = AppGreen,
                            focusedBorderColor = AppGreen,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = addressInput,
                            onValueChange = { addressInput = it },
                            placeholder = { Text("0x... address", color = Color.Gray, fontSize = 14.sp) },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = AppGreen,
                                focusedBorderColor = AppGreen,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { 
                                if (addressInput.isNotEmpty()) {
                                    viewModel.onAddressSelected(addressInput)
                                    viewModel.saveWallet(addressInput, walletNameInput)
                                    addressInput = ""
                                    walletNameInput = ""
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = AppGreen),
                            modifier = Modifier.size(48.dp)
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Total Worth Summary
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total Worth", color = AppGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${allAssets.size} assets", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        text = "$${String.format("%,.2f", totalWorth)}",
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Geist
                    )
                    AnimatedVisibility(visible = state.isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            color = AppGreen,
                            trackColor = MaterialTheme.colorScheme.background
                        )
                    }
                    AnimatedVisibility(visible = state.error != null) {
                        Text(
                            text = state.error ?: "",
                            color = AppRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text(text = "Assets: ", color = AppGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(text = "$${String.format("%,.2f", totalWorth)}", color = AppGold, fontSize = 14.sp)
                        Text(text = "  |  ", color = Color.Gray, fontSize = 14.sp)
                        Text(text = "DeFi: ", color = AppGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(text = "$0", color = AppGold, fontSize = 14.sp)
                    }
                }
            }
        }

        if (!state.isLoading && state.selectedAddress.isNotBlank() && state.error == null && state.balances.isEmpty()) {
            item {
                EmptyBalancesCard()
            }
        }

        // Multi-Chain Cards
        state.balances.forEach { (chainName, assets) ->
            item(key = "chain-$chainName") {
                ChainCard(chainName, assets)
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun EmptyBalancesCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF181A20)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("No balances found", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Try refresh, check the address, or use another saved wallet.", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ChainCard(chainName: String, assets: List<com.defitracker.app.data.remote.dto.CoinStatsBalanceDto>) {
    val chainTotal = remember(assets) {
        assets.sumOf { (it.amount ?: 0.0) * (it.price ?: 0.0) }
    }
    val chainColor = when (chainName) {
        "Ether" -> Color(0xFF627EEA)
        "BSC" -> Color(0xFFF3BA2F)
        "Base" -> Color(0xFF0052FF)
        "Polygon" -> Color(0xFF8247E5)
        else -> Color.Gray
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(chainColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = chainName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "Total $chainName: $${String.format("%,.2f", chainTotal)}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Geist
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Name", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.weight(1.2f))
                Text(text = "Amount", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(text = "Price", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(text = "Total", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }

            // Asset Rows
            assets.forEach { asset ->
                AssetRow(asset)
            }
        }
    }
}

@Composable
fun AssetRow(asset: com.defitracker.app.data.remote.dto.CoinStatsBalanceDto) {
    val total = (asset.amount ?: 0.0) * (asset.price ?: 0.0)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Name & Icon
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1.2f)) {
            AsyncImage(
                model = asset.imgUrl ?: "https://cdn-icons-png.flaticon.com/512/4214/4214500.png",
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = asset.symbol ?: "", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        // Amount
        Text(
            text = String.format("%.4f", asset.amount ?: 0.0),
            color = Color.White,
            fontSize = 12.sp,
            style = GeistTnum,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        // Price
        Text(
            text = "$${if ((asset.price ?: 0.0) < 1.0) String.format("%.4f", asset.price) else String.format("%,.2f", asset.price)}",
            color = Color.White,
            fontSize = 12.sp,
            style = GeistTnum,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        // Total
        Text(
            text = "$${String.format("%,.2f", total)}",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            style = GeistTnum,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
