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
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import com.defitracker.app.domain.model.AvailableCryptoPair
import com.defitracker.app.domain.model.CryptoPair
import com.defitracker.app.presentation.alerts.AlertsBottomSheet
import com.defitracker.app.presentation.alerts.AlertsViewModel
import com.defitracker.app.presentation.crypto_detail.DetailSeed
import com.defitracker.app.presentation.crypto_detail.toDoubleValue
import com.defitracker.app.ui.theme.AppGold
import com.defitracker.app.ui.theme.AppGreen
import com.defitracker.app.ui.theme.AppRed
import com.defitracker.app.ui.theme.CardBg
import com.defitracker.app.presentation.crypto_list.components.CryptoPairItem
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.material.ExperimentalMaterialApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
fun CryptoListScreen(
    onNavigateToDetail: (String, String, String, DetailSeed?) -> Unit,
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
        else {
            val matches = (state.availableSymbols + tvIndexSymbols).filter {
                it.symbol.contains(query, ignoreCase = true) ||
                it.baseAsset.contains(query, ignoreCase = true) ||
                it.quoteAsset.contains(query, ignoreCase = true) ||
                it.displayName.contains(query, ignoreCase = true) ||
                it.keywords.contains(query, ignoreCase = true)
            }
            // intercalado por fuente: Binance ya no entierra a MEXC en el top 10
            val bySource = matches.groupBy { it.source ?: "Binance" }
            val out = mutableListOf<AvailableCryptoPair>()
            var i = 0
            while (out.size < 12) {
                var added = false
                for (list in bySource.values) {
                    list.getOrNull(i)?.let { out.add(it); added = true }
                }
                if (!added) break
                i++
            }
            out
        }
    }

    val showSymbolError = state.availableSymbols.isEmpty() && state.symbolsError.isNotEmpty()

    // todas las fuentes juntas, en orden manual guardado
    val pairOrder = viewModel.pairOrder.value
    val orderIndex = remember(pairOrder) {
        pairOrder.withIndex().associate { it.value to it.index }
    }
    val visiblePairs = remember(state.pairs, orderIndex) {
        state.pairs.sortedBy { orderIndex["${it.symbol}-${it.source}"] ?: Int.MAX_VALUE }
    }

    // drag con long-press, vive en copia local hasta soltar
    val listState = rememberLazyListState()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val dragKey = remember { mutableStateOf<String?>(null) }
    val dragOffsetY = remember { mutableStateOf(0f) }
    // Y del dedo en coords del viewport, manda sobre el offset acumulado
    val fingerY = remember { mutableStateOf(0f) }
    val localOrder = remember { mutableStateOf<List<CryptoPair>?>(null) }
    // un solo job de autoscroll: uno por evento se acumulaban y la lista saltaba
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    // soltar despues de arrastrar no abre el par sin querer
    var lastDropAt by remember { mutableStateOf(0L) }
    fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }
    val sortMode = viewModel.sortMode.value
    // sin leer .value aca: si se lee en el cuerpo, cualquier tick invalida la lista entera
    val sparklinesState = viewModel.sparklines
    val volumesState = viewModel.volumes
    val basePairs = localOrder.value ?: visiblePairs
    val displayPairs by remember(basePairs, sortMode) {
        derivedStateOf {
            when (sortMode) {
                SortMode.DEFAULT -> basePairs
                SortMode.VOL_DESC -> {
                    val vols = volumesState.value
                    basePairs.sortedByDescending { vols["${it.symbol}-${it.source}"] ?: 0.0 }
                }
                SortMode.CHG_DESC -> basePairs.sortedByDescending { it.priceChangePercent.toDoubleValue() }
            }
        }
    }
    val sortLabel = when (sortMode) {
        SortMode.DEFAULT -> "Ordenar por: Defecto"
        SortMode.VOL_DESC -> "Ordenar por Volumen 24h ↓"
        SortMode.CHG_DESC -> "Ordenar por Cambio 24h ↓"
    }
    var sortMenuOpen by remember { mutableStateOf(false) }
    // ref fresca del orden visible: el gesto no se reinicia con los ticks,
    // sembrar desde displayPairs directo usaba la lista vieja y el 2do move borraba el 1ro
    val displayPairsRef = rememberUpdatedState(displayPairs)
    // jalar-para-actualizar con el componente estandar: baja/crece/gira fluido,
    // dispara solo al soltar pasado el punto, reversa cancela con animacion
    val manualRefreshing = viewModel.manualRefreshing.value
    val pullRefreshState = rememberPullRefreshState(
        refreshing = manualRefreshing,
        onRefresh = { viewModel.manualRefresh() }
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LaunchedEffect(isSearchMode) {
            if (isSearchMode) {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState)
        ) {
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
                        // campana con burbuja numerica montada encima del icono
                        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                            IconButton(onClick = { showAlerts = true }) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Alertas",
                                    tint = Color.White
                                )
                            }
                            if (alertsState.unread > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(androidx.compose.ui.Alignment.TopEnd)
                                        .offset(x = (-4).dp, y = 4.dp)
                                        .sizeIn(minWidth = 22.dp, minHeight = 22.dp)
                                        .clip(CircleShape)
                                        .background(AppRed)
                                        .padding(horizontal = 5.dp, vertical = 2.dp),
                                    contentAlignment = androidx.compose.ui.Alignment.Center
                                ) {
                                    Text(
                                        text = if (alertsState.unread > 99) "99+" else alertsState.unread.toString(),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 12.sp
                                    )
                                }
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
                                cursorColor = AppGreen,
                                focusedBorderColor = AppGreen,
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
                                color = CardBg,
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
                                            Text("Retry", color = AppGreen, fontSize = 13.sp)
                                        }
                                    }
                                } else {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                    // si MEXC no cargo, se avisa en vez de esconderlo
                                    if (!state.mexcAvailable) {
                                        Text(
                                            text = "MEXC no disponible ahorita — jala la lista para reintentar",
                                            color = AppGold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                        )
                                    }
                                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                        items(
                                            items = filteredSymbols,
                                            key = { pair -> pair.symbol }
                                        ) { pair ->
                                            ListItem(
                                                headlineContent = {
                                                    Text(
                                                        // perps sin slash tambien al buscar
                                                        if (pair.source == "MEXC") pair.baseAsset + pair.quoteAsset
                                                        else pair.displayName,
                                                        color = Color.White,
                                                        fontSize = 14.sp
                                                    )
                                                },
                                                supportingContent = { Text("${pair.symbol} · ${pair.source ?: "Binance"}", color = Color.Gray, fontSize = 11.sp) },
                                                modifier = Modifier
                                                    .clickable {
                                                        viewModel.onAddPair(
                                                            symbol = pair.symbol,
                                                            baseAsset = pair.baseAsset,
                                                            quoteAsset = pair.quoteAsset,
                                                            source = pair.source ?: "Binance"
                                                        )
                                                        isSearchMode = false
                                                        searchQuery = ""
                                                    }
                                                    .background(Color.Transparent),
                                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                            )
                                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                        }
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
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
                        Column(modifier = Modifier.fillMaxSize()) {
                            // header estilo exchange, tap abre el desplegable de orden
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Box {
                                    Text(
                                        text = sortLabel,
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { sortMenuOpen = true }
                                    )
                                    DropdownMenu(
                                        expanded = sortMenuOpen,
                                        onDismissRequest = { sortMenuOpen = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Por defecto") },
                                            onClick = {
                                                viewModel.setSortMode(SortMode.DEFAULT)
                                                sortMenuOpen = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Mayor cambio 24h") },
                                            onClick = {
                                                viewModel.setSortMode(SortMode.CHG_DESC)
                                                sortMenuOpen = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Mayor volumen") },
                                            onClick = {
                                                viewModel.setSortMode(SortMode.VOL_DESC)
                                                sortMenuOpen = false
                                            }
                                        )
                                    }
                                }
                                Text(
                                    text = "Cambio 24h",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f)
                        ) {
                            items(
                                items = displayPairs,
                                key = { pair -> "${pair.symbol}-${pair.source}" }
                            ) { pair ->
                                val key = "${pair.symbol}-${pair.source}"
                                val dragging = dragKey.value == key
                                // fila con handle a la izquierda: unico punto de arrastre
                                Row(
                                    modifier = Modifier
                                        .animateItemPlacement()
                                        .graphicsLayer {
                                            translationY = if (dragging) dragOffsetY.value else 0f
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // handle chiquito siempre visible, con long-press para no mover sin querer
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .width(30.dp)
                                            .pointerInput(key, sortMode) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                                    // en ordenado se pasa a Por defecto con lo visible como base
                                                    if (sortMode != SortMode.DEFAULT) {
                                                        viewModel.setSortMode(SortMode.DEFAULT)
                                                    }
                                                    dragKey.value = key
                                                    localOrder.value = displayPairsRef.value
                                                    dragOffsetY.value = 0f
                                                    val vi = listState.layoutInfo.visibleItemsInfo
                                                        .find { it.key == key }
                                                    fingerY.value = (vi?.offset ?: 0) + (vi?.size ?: 0) / 2f
                                                },
                                                onDragEnd = {
                                                    stopAutoScroll()
                                                    lastDropAt = System.currentTimeMillis()
                                                    localOrder.value?.let { ordered ->
                                                        viewModel.savePairOrder(ordered.map { "${it.symbol}-${it.source}" })
                                                    }
                                                    dragKey.value = null
                                                    dragOffsetY.value = 0f
                                                    localOrder.value = null
                                                },
                                                onDragCancel = {
                                                    stopAutoScroll()
                                                    dragKey.value = null
                                                    dragOffsetY.value = 0f
                                                    localOrder.value = null
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    fingerY.value += dragAmount.y
                                                    val info = listState.layoutInfo
                                                    val slots = info.visibleItemsInfo.sortedBy { it.index }
                                                    if (slots.isEmpty()) return@detectDragGesturesAfterLongPress
                                                    // reancla al slot actual: compensa swaps y autoscroll
                                                    val slot = slots.find { it.key == dragKey.value }
                                                    if (slot != null) {
                                                        dragOffsetY.value = fingerY.value - (slot.offset + slot.size / 2f)
                                                    } else {
                                                        dragOffsetY.value += dragAmount.y
                                                    }
                                                    val current = localOrder.value?.toMutableList()
                                                        ?: return@detectDragGesturesAfterLongPress
                                                    val dragId = dragKey.value ?: return@detectDragGesturesAfterLongPress
                                                    val from = current.indexOfFirst { "${it.symbol}-${it.source}" == dragId }
                                                    if (from < 0) return@detectDragGesturesAfterLongPress
                                                    // destino por posicion real del dedo, sin asumir altos
                                                    var target = slots.last().index.coerceIn(0, current.size - 1)
                                                    for (vi in slots) {
                                                        if (fingerY.value < vi.offset + vi.size / 2f) {
                                                            target = vi.index.coerceIn(0, current.size - 1)
                                                            break
                                                        }
                                                    }
                                                    if (target != from) {
                                                        val lo = minOf(from, target)
                                                        val hi = maxOf(from, target)
                                                        var dist = 0f
                                                        for (vi in slots) {
                                                            if (vi.key != dragId && vi.index in (lo + 1)..hi) dist += vi.size
                                                        }
                                                        if (dist <= 0f) dist = kotlin.math.abs(target - from) * 180f
                                                        val item = current.removeAt(from)
                                                        current.add(target, item)
                                                        localOrder.value = current
                                                        // descuenta lo cruzado con altos reales
                                                        dragOffsetY.value += if (target > from) -dist else dist
                                                    }
                                                    // autoscroll en bordes con un solo job vivo
                                                    val vi = info.visibleItemsInfo.find { it.key == dragId }
                                                    val center = (vi?.offset ?: 0) + dragOffsetY.value + (vi?.size ?: 0) / 2
                                                    val viewportH = info.viewportEndOffset - info.viewportStartOffset
                                                    val dir = when {
                                                        center < 120 -> -1f
                                                        center > viewportH - 120 -> 1f
                                                        else -> 0f
                                                    }
                                                    if (dir == 0f) {
                                                        stopAutoScroll()
                                                    } else if (autoScrollJob?.isActive != true) {
                                                        autoScrollJob = scope.launch {
                                                            // con pausa de frame: sin delay saturaba el hilo y ANR
                                                            while (true) {
                                                                var moved = 0f
                                                                listState.scroll { moved = scrollBy(24f * dir) }
                                                                // la lista se movio sola: el item sigue al dedo
                                                                if (moved != 0f) dragOffsetY.value += moved
                                                                delay(16)
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DragHandle,
                                                contentDescription = "Reordenar",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    Box(modifier = Modifier.weight(1f)) {
                                        CryptoPairItem(
                                        pair = pair,
                                        sparkline = sparklinesState.value[key] ?: emptyList(),
                                        extended = viewModel.dayExt.value[key] ?: false,
                                        onClick = {
                                            if (System.currentTimeMillis() - lastDropAt < 300L) return@CryptoPairItem
                                            // semilla numerica (sin comas): el header del chart pinta al instante
                                            val seed = DetailSeed(
                                                price = pair.price.filter { it.isDigit() || it == '.' },
                                                pct = pair.priceChangePercent.filter { it.isDigit() || it == '.' || it == '-' },
                                                pos = pair.isPositive,
                                                vol = (volumesState.value[key] ?: 0.0).toString()
                                            )
                                            onNavigateToDetail(pair.symbol, pair.source, "", seed)
                                        },
                                        onDelete = { viewModel.onRemovePair(pair.symbol) }
                                        )
                                    }
                                }
                                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                            }
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
                        onNavigateToDetail(symbol, source, interval, null)
                    }
                )
            }
            // indicador estandar: baja con tu dedo, al soltar pasado el punto recarga
            PullRefreshIndicator(
                refreshing = manualRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter),
                backgroundColor = CardBg,
                contentColor = AppGreen
            )
        }
    }
}

// indices que TradingView publica como CRYPTOCAP:*; se muestran en el buscador
private val tvIndexSymbols = listOf(
    AvailableCryptoPair("BTC.D", "BTC.D", "", "TV", "btc dominance dominancia"),
    AvailableCryptoPair("ETH.D", "ETH.D", "", "TV", "eth dominance dominancia"),
    AvailableCryptoPair("USDT.D", "USDT.D", "", "TV", "usdt dominance dominancia tether"),
    AvailableCryptoPair("USDC.D", "USDC.D", "", "TV", "usdc dominance dominancia"),
    AvailableCryptoPair("OTHERS.D", "OTHERS.D", "", "TV", "others dominance dominancia other"),
    AvailableCryptoPair("BTC", "BTC", "", "TV", "btc bitcoin market cap"),
    AvailableCryptoPair("ETH", "ETH", "", "TV", "eth ethereum market cap"),
    AvailableCryptoPair("USDT", "USDT", "", "TV", "usdt tether stablecoin"),
    AvailableCryptoPair("USDC", "USDC", "", "TV", "usdc stablecoin"),
    AvailableCryptoPair("TOTAL", "TOTAL", "", "TV", "total market cap"),
    AvailableCryptoPair("TOTAL2", "TOTAL2", "", "TV", "total2 market cap"),
    AvailableCryptoPair("TOTAL3", "TOTAL3", "", "TV", "total3 market cap"),
    AvailableCryptoPair("OTHERS", "OTHERS", "", "TV", "others altcoins market cap")
)


