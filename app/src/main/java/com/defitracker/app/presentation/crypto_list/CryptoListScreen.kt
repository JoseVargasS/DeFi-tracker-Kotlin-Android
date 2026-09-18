package com.defitracker.app.presentation.crypto_list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.defitracker.app.alerts.DivScanService
import com.defitracker.app.domain.model.CryptoPair
import com.defitracker.app.presentation.alerts.AlertsBottomSheet
import com.defitracker.app.presentation.alerts.AlertsViewModel
import com.defitracker.app.presentation.crypto_list.components.CryptoPairItem
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CryptoListScreen(
    onNavigateToDetail: (String, String, String) -> Unit,
    viewModel: CryptoListViewModel = hiltViewModel(),
    alertsViewModel: AlertsViewModel = hiltViewModel()
) {
    val state = viewModel.state.value
    val alertsState = alertsViewModel.state.value
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showAlerts by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // permiso de notis + arranque del monitoreo de futuros
    val notiPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> DivScanService.start(context) }
    LaunchedEffect(Unit) {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notiPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            DivScanService.start(context)
        }
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val filteredSymbols = remember(searchQuery, state.availableSymbols) {
        val query = searchQuery.trim()
        if (query.isEmpty()) emptyList()
        else state.availableSymbols.filter {
            it.symbol.contains(query, ignoreCase = true) ||
            it.baseAsset.contains(query, ignoreCase = true) ||
            it.quoteAsset.contains(query, ignoreCase = true) ||
            it.displayName.contains(query, ignoreCase = true)
        }.take(10)
    }

    val showSymbolError = state.availableSymbols.isEmpty() && state.symbolsError.isNotEmpty()

    // cada fuente ve solo sus pares, en orden manual guardado
    val pairOrder = viewModel.pairOrder.value
    val orderIndex = remember(pairOrder) {
        pairOrder.withIndex().associate { it.value to it.index }
    }
    val visiblePairs = remember(state.pairs, state.selectedSource, orderIndex) {
        state.pairs.filter { it.source == state.selectedSource }
            .sortedBy { orderIndex["${it.symbol}-${it.source}"] ?: Int.MAX_VALUE }
    }
    val sources = listOf("Binance", "MEXC")

    // drag con long-press, vive en copia local hasta soltar
    val listState = rememberLazyListState()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val dragKey = remember { mutableStateOf<String?>(null) }
    val dragOffsetY = remember { mutableStateOf(0f) }
    val localOrder = remember { mutableStateOf<List<CryptoPair>?>(null) }
    val displayPairs = localOrder.value ?: visiblePairs

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LaunchedEffect(isSearchMode) {
            if (isSearchMode) {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Pairs",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        // campana con badge, abre el bottom sheet de alertas
                        BadgedBox(
                            badge = {
                                if (alertsState.unread > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF6465D))
                                    )
                                }
                            }
                        ) {
                            IconButton(onClick = { showAlerts = true }) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Alertas",
                                    tint = Color.White
                                )
                            }
                        }
                        IconButton(onClick = {
                            isSearchMode = !isSearchMode
                            if (isSearchMode.not()) {
                                searchQuery = ""
                            }
                        }) {
                            Icon(
                                imageVector = if (isSearchMode) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White
                            )
                        }
                    }
                }

                // segmentado compacto centrado, solo el ancho necesario
                Row(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A1D23))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    sources.forEach { source ->
                        val selected = state.selectedSource == source
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) Color(0xFF1ECB81).copy(alpha = 0.25f)
                                    else Color.Transparent
                                )
                                .clickable { viewModel.selectSource(source) }
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = source,
                                    color = if (selected) Color.White else Color.Gray,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = if (source == "Binance") "Spot" else "Futuros",
                                    color = if (selected) Color(0xFF1ECB81) else Color.Gray.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isSearchMode,
                    enter = fadeIn() + expandVertically(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .animateContentSize()
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search symbol (e.g. ETH/BTC)...", color = Color.Gray, fontSize = 14.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .focusRequester(focusRequester),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFF1ECB81),
                                focusedBorderColor = Color(0xFF1ECB81),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )

                        AnimatedVisibility(
                            visible = searchQuery.isNotBlank(),
                            enter = fadeIn() + expandVertically(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp),
                                color = Color(0xFF1A1D23),
                                shape = RoundedCornerShape(8.dp),
                                tonalElevation = 2.dp
                            ) {
                                if (showSymbolError) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(state.symbolsError, color = Color.Gray, fontSize = 13.sp)
                                        TextButton(onClick = { viewModel.retryLoadSymbols() }) {
                                            Text("Retry", color = Color(0xFF1ECB81), fontSize = 13.sp)
                                        }
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                        items(
                                            items = filteredSymbols,
                                            key = { pair -> pair.symbol }
                                        ) { pair ->
                                            ListItem(
                                                headlineContent = {
                                                    Text(
                                                        // perps sin slash tambien al buscar
                                                        if (state.selectedSource == "MEXC") pair.baseAsset + pair.quoteAsset
                                                        else pair.displayName,
                                                        color = Color.White,
                                                        fontSize = 14.sp
                                                    )
                                                },
                                                supportingContent = { Text("${pair.symbol} · ${state.selectedSource}", color = Color.Gray, fontSize = 11.sp) },
                                                modifier = Modifier
                                                    .clickable {
                                                        viewModel.onAddPair(
                                                            symbol = pair.symbol,
                                                            baseAsset = pair.baseAsset,
                                                            quoteAsset = pair.quoteAsset,
                                                            source = state.selectedSource
                                                        )
                                                        isSearchMode = false
                                                        searchQuery = ""
                                                    }
                                                    .background(Color.Transparent),
                                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                            )
                                            Divider(color = Color.Gray.copy(alpha = 0.1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .animateContentSize()
                ) {
                    if (visiblePairs.isEmpty() && !isSearchMode) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search, 
                                contentDescription = null, 
                                modifier = Modifier.size(48.dp), 
                                tint = Color.Gray.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("No pairs tracked yet", color = Color.Gray)
                            Text("Use search to add your first pair", color = Color.Gray, fontSize = 12.sp)
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = displayPairs,
                                key = { pair -> "${pair.symbol}-${pair.source}" }
                            ) { pair ->
                                val key = "${pair.symbol}-${pair.source}"
                                val dragging = dragKey.value == key
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer {
                                            translationY = if (dragging) dragOffsetY.value else 0f
                                        }
                                        .pointerInput(key) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                                    dragKey.value = key
                                                    localOrder.value = displayPairs
                                                    dragOffsetY.value = 0f
                                                },
                                                onDragEnd = {
                                                    localOrder.value?.let { ordered ->
                                                        viewModel.savePairOrder(ordered.map { "${it.symbol}-${it.source}" })
                                                    }
                                                    dragKey.value = null
                                                    dragOffsetY.value = 0f
                                                    localOrder.value = null
                                                },
                                                onDragCancel = {
                                                    dragKey.value = null
                                                    dragOffsetY.value = 0f
                                                    localOrder.value = null
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetY.value += dragAmount.y
                                                    val current = localOrder.value?.toMutableList()
                                                        ?: return@detectDragGesturesAfterLongPress
                                                    val dragId = dragKey.value ?: return@detectDragGesturesAfterLongPress
                                                    val from = current.indexOfFirst { "${it.symbol}-${it.source}" == dragId }
                                                    if (from < 0) return@detectDragGesturesAfterLongPress
                                                    // por key, el indice de Lazy cambia con cada reorder
                                                    val itemH = listState.layoutInfo.visibleItemsInfo
                                                        .find { it.key == dragId }?.size ?: 180
                                                    val target = (from + (dragOffsetY.value / itemH).roundToInt())
                                                        .coerceIn(0, current.size - 1)
                                                    if (target != from) {
                                                        val item = current.removeAt(from)
                                                        current.add(target, item)
                                                        localOrder.value = current
                                                        // descuenta lo ya movido o el offset acumulado salta de mas
                                                        dragOffsetY.value -= (target - from) * itemH
                                                    }
                                                    // autoscroll en bordes
                                                    val info = listState.layoutInfo
                                                    val vi = info.visibleItemsInfo.find { it.key == dragId }
                                                    val center = (vi?.offset ?: 0) + dragOffsetY.value + (vi?.size ?: 0) / 2
                                                    val viewportH = info.viewportEndOffset - info.viewportStartOffset
                                                    if (center < 120) {
                                                        scope.launch { listState.scroll { scrollBy(-60f) } }
                                                    } else if (center > viewportH - 120) {
                                                        scope.launch { listState.scroll { scrollBy(60f) } }
                                                    }
                                                }
                                            )
                                        }
                                ) {
                                    CryptoPairItem(
                                        pair = pair,
                                        onClick = { onNavigateToDetail(pair.symbol, pair.source, "") },
                                        onDelete = { viewModel.onRemovePair(pair.symbol) }
                                    )
                                }
                                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
            // modal bottom con las notis, tap abre la grafica en ese TF
            if (showAlerts) {
                AlertsBottomSheet(
                    onDismiss = { showAlerts = false },
                    onOpenAlert = { symbol, source, interval ->
                        showAlerts = false
                        onNavigateToDetail(symbol, source, interval)
                    }
                )
            }
        }
    }
}

