package com.defitracker.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.defitracker.app.presentation.crypto_detail.CryptoDetailScreen
import com.defitracker.app.presentation.crypto_list.CryptoListScreen
import androidx.compose.ui.res.painterResource
import com.defitracker.app.presentation.wallet.WalletScreen
import com.defitracker.app.presentation.transactions.TransactionsScreen
import com.defitracker.app.ui.theme.DeFiTrackerTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Pairs : Screen("crypto_list", "Pairs", Icons.Default.List)
    object Wallet : Screen("wallet", "Wallet", Icons.Default.Wallet)
    object Transactions : Screen("transactions", "History", Icons.Default.History)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // deep-link desde la noti push al grafico en ese TF
    var pendingAlert: Triple<String, String, String>? by androidx.compose.runtime.mutableStateOf(null)

    companion object {
        const val ACTION_OPEN_ALERT = "com.defitracker.app.OPEN_ALERT"
        const val EXTRA_ALERT_SYMBOL = "alert_symbol"
        const val EXTRA_ALERT_SOURCE = "alert_source"
        const val EXTRA_ALERT_INTERVAL = "alert_interval"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeAlertIntent(intent)
    }

    private fun consumeAlertIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_ALERT) {
            val symbol = intent.getStringExtra(EXTRA_ALERT_SYMBOL).orEmpty()
            val source = intent.getStringExtra(EXTRA_ALERT_SOURCE).orEmpty().ifEmpty { "MEXC" }
            val interval = intent.getStringExtra(EXTRA_ALERT_INTERVAL).orEmpty()
            if (symbol.isNotEmpty()) {
                pendingAlert = Triple(symbol, source, interval)
            }
            intent.action = null
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        consumeAlertIntent(intent)
        setContent {
            DeFiTrackerTheme {
                val navController = rememberNavController()
                val screens = listOf(Screen.Pairs, Screen.Wallet, Screen.Transactions)
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val showMainChrome = currentDestination?.route?.startsWith("crypto_detail/") != true

                // si llego por push, abre la grafica en ese TF al toque
                val activity = this@MainActivity
                LaunchedEffect(pendingAlert) {
                    val alert = pendingAlert
                    if (alert != null) {
                        val route = if (alert.third.isNotEmpty()) {
                            "crypto_detail/${alert.first}/${alert.second}?interval=${alert.third}"
                        } else {
                            "crypto_detail/${alert.first}/${alert.second}"
                        }
                        try {
                            navController.navigate(route)
                        } catch (_: Exception) {}
                        activity.pendingAlert = null
                    }
                }

                Scaffold(
                    topBar = {
                        if (showMainChrome) {
                            CenterAlignedTopAppBar(
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                                            contentDescription = "Logo",
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Octopus",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background
                                )
                            )
                        }
                    },
                    bottomBar = {
                        if (showMainChrome) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.background,
                                contentColor = Color.White,
                                tonalElevation = 0.dp
                            ) {
                                screens.forEach { screen ->
                                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                    NavigationBarItem(
                                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                                        label = { Text(screen.label) },
                                        selected = selected,
                                        onClick = {
                                            if (!selected) {
                                                navController.navigate(screen.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color(0xFF0ECB81),
                                            selectedTextColor = Color(0xFF0ECB81),
                                            unselectedIconColor = Color.Gray,
                                            unselectedTextColor = Color.Gray,
                                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Navigation(navController)
                    }
                }
            }
        }
    }
}

@Composable
fun Navigation(navController: androidx.navigation.NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Pairs.route,
        enterTransition = {
            fadeIn(animationSpec = tween(140)) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(220)
            )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(90)) + slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(220)
            )
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(140)) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(220)
            )
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(90)) + slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(220)
            )
        }
    ) {
        composable(Screen.Pairs.route) {
            CryptoListScreen(
                onNavigateToDetail = { symbol, source, interval ->
                    val route = if (interval.isNotEmpty()) {
                        "crypto_detail/$symbol/$source?interval=$interval"
                    } else {
                        "crypto_detail/$symbol/$source"
                    }
                    navController.navigate(route)
                }
            )
        }
        composable(Screen.Wallet.route) {
            WalletScreen()
        }
        composable(Screen.Transactions.route) {
            TransactionsScreen()
        }
        composable(
            route = "crypto_detail/{symbol}/{source}?interval={interval}",
            arguments = listOf(
                navArgument("symbol") { type = NavType.StringType },
                navArgument("source") { type = NavType.StringType },
                navArgument("interval") {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                }
            )
        ) {
            CryptoDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
