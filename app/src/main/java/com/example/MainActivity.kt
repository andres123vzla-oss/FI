package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.example.ui.screens.AjustesScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.MovimientosScreen
import com.example.ui.screens.PortafolioScreen
import com.example.ui.screens.PresupuestoScreen
import com.example.ui.theme.ExcelDarkBlue
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.FinanceViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppShell()
            }
        }
    }
}

sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector, val tag: String) {
    object Dashboard : BottomNavItem("dashboard", "Resumen", Icons.Default.Dashboard, "bottom_tab_dashboard")
    object Movimientos : BottomNavItem("movimientos", "Movimientos", Icons.Default.ReceiptLong, "bottom_tab_movimientos")
    object Presupuesto : BottomNavItem("presupuesto", "Presupuesto", Icons.Default.TrackChanges, "bottom_tab_presupuesto")
    object Portafolio : BottomNavItem("portafolio", "Portafolio", Icons.Default.ShowChart, "bottom_tab_portafolio")
    object Ajustes : BottomNavItem("ajustes", "Ajustes", Icons.Default.Settings, "bottom_tab_ajustes")
}

@Composable
fun MainAppShell() {
    val navController = rememberNavController()
    val viewModel: FinanceViewModel = viewModel()

    val navItems = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Movimientos,
        BottomNavItem.Presupuesto,
        BottomNavItem.Portafolio,
        BottomNavItem.Ajustes
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("app_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                navItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title
                            )
                        },
                        label = {
                            Text(
                                text = item.title,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.testTag(item.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Dashboard.route) {
                DashboardScreen(viewModel = viewModel)
            }
            composable(BottomNavItem.Movimientos.route) {
                MovimientosScreen(viewModel = viewModel)
            }
            composable(BottomNavItem.Presupuesto.route) {
                PresupuestoScreen(viewModel = viewModel)
            }
            composable(BottomNavItem.Portafolio.route) {
                PortafolioScreen(viewModel = viewModel)
            }
            composable(BottomNavItem.Ajustes.route) {
                AjustesScreen(viewModel = viewModel)
            }
        }
    }
}
