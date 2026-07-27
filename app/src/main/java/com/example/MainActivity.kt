package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.data.database.AppDatabase
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.security.AppLockManager
import com.example.security.BiometricHelper
import com.example.security.LockGate
import com.example.security.SecurityViewModel
import com.example.ui.screens.AjustesScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.MovimientosScreen
import com.example.ui.screens.PortafolioScreen
import com.example.ui.screens.PresupuestoScreen
import com.example.ui.screens.RentaScreen
import com.example.ui.components.LocalAmountsHidden
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.Motion
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.security.LockScreen
import com.example.ui.security.SetupLockScreen
import androidx.compose.runtime.CompositionLocalProvider
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.FinanceViewModel
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ShowChart

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FLAG_SECURE: impide capturas de pantalla y oculta el contenido en el selector de
        // apps recientes, evitando exponer datos financieros.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        enableEdgeToEdge()
        // A4: precalienta la BD cifrada (loadLibrary + Keystore + Room.build) FUERA del hilo Main,
        // para que esos costes ya estén resueltos cuando se componga la rama UNLOCKED y no bloqueen
        // el primer frame (jank/ANR al desbloquear). getDatabase es un singleton idempotente, así que
        // el VM reutilizará la misma instancia. No expone datos: solo abre la BD; la passphrase sale
        // del Keystore (desacoplada del PIN), no se loguea nada ni se lee contenido aquí.
        lifecycleScope.launch(Dispatchers.IO) {
            // ARQ2-09: conservar el diagnóstico del primer fallo (antes se tragaba en silencio y
            // el primer síntoma era el constructor del FinanceViewModel). No se loguea ningún
            // detalle de la passphrase/Keystore, solo el stacktrace del error.
            runCatching { AppDatabase.getDatabase(applicationContext) }
                .onFailure { android.util.Log.e("FI", "DB warm-up failed", it) }
        }
        val activity = this
        setContent {
            MyApplicationTheme {
                AppRoot(activity)
            }
        }
    }
}

/**
 * Raíz de la app: aplica el App Lock antes de exponer cualquier dato financiero y gestiona el
 * bloqueo automático por inactividad observando el ciclo de vida.
 */
@Composable
fun AppRoot(activity: FragmentActivity) {
    val securityViewModel: SecurityViewModel = viewModel()
    val gate by securityViewModel.gate.collectAsState()

    // Bloqueo automático: registra el paso a segundo plano y evalúa al volver al frente.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> AppLockManager.onAppBackgrounded()
                Lifecycle.Event.ON_START -> AppLockManager.onAppForegrounded(securityViewModel.autoLockMillis())
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (gate) {
        // Aún no sabemos si hay PIN: splash neutro con la marca (candado), nunca datos financieros.
        LockGate.LOADING -> LoadingSplash()
        // Primer inicio: se exige configurar el PIN antes de mostrar cualquier dato financiero.
        LockGate.SETUP -> SetupLockScreen(securityViewModel = securityViewModel)
        LockGate.LOCKED -> LockScreen(
            securityViewModel = securityViewModel,
            onBiometricRequested = {
                // SEC2-04: el desbloqueo biométrico exige el binding criptográfico (CryptoObject
                // + token del gate); el éxito del prompt por sí solo ya no desbloquea.
                securityViewModel.requestBiometricUnlock(activity)
            }
        )
        LockGate.UNLOCKED -> {
            // Privacidad: expone globalmente si los montos deben enmascararse en la UI.
            val hideAmounts by securityViewModel.hideAmounts.collectAsState()
            CompositionLocalProvider(
                LocalAmountsHidden provides hideAmounts,
                LocalReducedMotion provides rememberReducedMotion(),
            ) {
                // Entrada animada al desbloquear (solo ENTRADA, sin estado de salida posible).
                // Vive dentro de la rama UNLOCKED: en un re-lock la rama se destruye y no queda
                // ningún "linger". No se anima ni envuelve el when(gate).
                UnlockedEntrance {
                    MainAppShell()
                }
            }
        }
    }
}

/**
 * UX-10: Splash del estado LOADING (mientras se resuelve si hay PIN). En lugar de una Surface en
 * blanco (que se percibe como cuelgue), muestra el icono de marca (candado) centrado con un fade-in
 * sutil. Usa un icono en vez de un spinner porque la transición a SETUP/LOCKED/UNLOCKED suele ser
 * casi instantánea y un spinner parpadearía. Respeta "reducir movimiento" (sin animación) y el color
 * de fondo del tema para funcionar en claro/oscuro. No expone ningún dato financiero.
 */
@Composable
private fun LoadingSplash() {
    val reduced = rememberReducedMotion()
    val alpha = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) alpha.animateTo(1f, Motion.medium())
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { this.alpha = alpha.value },
            )
        }
    }
}

/**
 * Contenedor de entrada para el subárbol desbloqueado. Anima una sola vez (alpha + leve
 * desplazamiento vertical) usando un [Animatable], nunca [androidx.compose.animation.AnimatedVisibility]:
 * así no existe estado de salida que pudiera mantener visible el contenido durante un re-lock.
 *
 * Si "reducir movimiento" está activo, salta directo al estado final (alpha=1, sin desplazamiento).
 */
@Composable
private fun UnlockedEntrance(content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    val liftPx = with(LocalDensity.current) { 14.dp.toPx() }
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            progress.animateTo(1f, Motion.medium())
        }
    }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.graphicsLayer {
            val a = progress.value
            alpha = a
            translationY = (1f - a) * liftPx
        }
    ) {
        content()
    }
}

sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector, val tag: String) {
    object Dashboard : BottomNavItem("dashboard", "Resumen", Icons.Default.Dashboard, "bottom_tab_dashboard")
    object Movimientos : BottomNavItem("movimientos", "Movimientos", Icons.AutoMirrored.Filled.ReceiptLong, "bottom_tab_movimientos")
    object Presupuesto : BottomNavItem("presupuesto", "Presupuesto", Icons.Default.TrackChanges, "bottom_tab_presupuesto")
    object Portafolio : BottomNavItem("portafolio", "Portafolio", Icons.AutoMirrored.Filled.ShowChart, "bottom_tab_portafolio")
    object Ajustes : BottomNavItem("ajustes", "Ajustes", Icons.Default.Settings, "bottom_tab_ajustes")
    object Renta : BottomNavItem("renta", "Renta", Icons.Default.Description, "bottom_tab_renta")
}

@Composable
fun MainAppShell() {
    val navController = rememberNavController()
    // A5: construir el FinanceViewModel mediante su Factory explícita en vez del default de
    // viewModel(), centralizando la creación y dejando margen para inyección/testabilidad.
    val application = LocalContext.current.applicationContext as android.app.Application
    val viewModel: FinanceViewModel = viewModel(
        factory = FinanceViewModel.provideFactory(application)
    )

    val navItems = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Movimientos,
        BottomNavItem.Presupuesto,
        BottomNavItem.Portafolio,
        BottomNavItem.Ajustes,
        BottomNavItem.Renta
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
          androidx.compose.foundation.layout.Column {
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavigationBar(
                modifier = Modifier.testTag("app_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val reducedMotion = LocalReducedMotion.current
                navItems.forEach { item ->
                    val selected = currentRoute == item.route
                    // Micro-escala del icono seleccionado (solo visual; no altera el área táctil).
                    val iconScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (selected) 1f else 0.92f,
                        animationSpec = Motion.fast(reducedMotion),
                        label = "navIconScale",
                    )
                    NavigationBarItem(
                        selected = selected,
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
                                contentDescription = item.title,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
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
        }
    ) { innerPadding ->
        val reducedMotionNav = LocalReducedMotion.current
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
            // Fade-through entre pestañas: leve fundido + escala de entrada; salida lineal y breve.
            enterTransition = {
                if (reducedMotionNav) {
                    EnterTransition.None
                } else {
                    fadeIn(
                        tween(
                            durationMillis = Motion.Medium,
                            delayMillis = 90,
                            easing = Motion.EmphasizedEasing,
                        )
                    ) + scaleIn(
                        initialScale = 0.97f,
                        animationSpec = tween(
                            durationMillis = Motion.Medium,
                            delayMillis = 90,
                            easing = Motion.EmphasizedEasing,
                        ),
                    )
                }
            },
            exitTransition = {
                if (reducedMotionNav) {
                    ExitTransition.None
                } else {
                    fadeOut(tween(durationMillis = 90, easing = LinearEasing))
                }
            },
            popEnterTransition = {
                if (reducedMotionNav) {
                    EnterTransition.None
                } else {
                    fadeIn(
                        tween(
                            durationMillis = Motion.Medium,
                            delayMillis = 90,
                            easing = Motion.EmphasizedEasing,
                        )
                    ) + scaleIn(
                        initialScale = 0.97f,
                        animationSpec = tween(
                            durationMillis = Motion.Medium,
                            delayMillis = 90,
                            easing = Motion.EmphasizedEasing,
                        ),
                    )
                }
            },
            popExitTransition = {
                if (reducedMotionNav) {
                    ExitTransition.None
                } else {
                    fadeOut(tween(durationMillis = 90, easing = LinearEasing))
                }
            },
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
            composable(BottomNavItem.Renta.route) {
                RentaScreen(viewModel = viewModel)
            }
        }
    }
}
