package com.example.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Tokens de movimiento del sistema "dark premium". Centralizan duraciones y curvas para que
 * todas las transiciones sean cortas, sutiles y consistentes (sin valores mágicos dispersos).
 *
 * Guía: nada llama la atención. Se animan opacidad, escala y posición; nunca el tamaño del
 * texto de los montos.
 */
object Motion {
    /** Microinteracciones (reveal del ojo, toques). */
    const val Fast = 150

    /** Entrada/salida de ítems, cambios de contenido. */
    const val Medium = 250

    /** Barras de progreso, expansiones. */
    const val Long = 300

    /** Curva estándar de Material: acelera y desacelera suavemente. */
    val StandardEasing: Easing = FastOutSlowInEasing

    /** Curva "emphasized" de Material 3 para movimientos algo más expresivos pero contenidos. */
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> fast(): FiniteAnimationSpec<T> = tween(durationMillis = Fast, easing = StandardEasing)

    fun <T> medium(): FiniteAnimationSpec<T> = tween(durationMillis = Medium, easing = EmphasizedEasing)

    fun <T> long(): FiniteAnimationSpec<T> = tween(durationMillis = Long, easing = EmphasizedEasing)

    // Variantes que respetan "reducir movimiento": saltan al estado final (snap) cuando está activo.
    fun <T> fast(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else fast()

    fun <T> medium(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else medium()

    fun <T> long(reduced: Boolean): FiniteAnimationSpec<T> = if (reduced) snap() else long()
}

/**
 * Preferencia del sistema "Quitar animaciones" (Ajustes → Accesibilidad). Cuando está activa,
 * `ANIMATOR_DURATION_SCALE` vale 0 y las animaciones deben saltar a su estado final.
 *
 * Se provee dentro de la rama desbloqueada (junto a [LocalAmountsHidden]); las pantallas de
 * seguridad lo leen directo con [rememberReducedMotion].
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

private fun readReducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f

/**
 * Lee la preferencia de "reducir movimiento" y se re-evalúa si cambia en runtime (vía
 * [ContentObserver] sobre `ANIMATOR_DURATION_SCALE`). Lectura barata, en el hilo principal.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val state = produceState(initialValue = readReducedMotion(context), context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { value = readReducedMotion(context) }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        resolver.registerContentObserver(uri, false, observer)
        awaitDispose { resolver.unregisterContentObserver(observer) }
    }
    val reduced by state
    return reduced
}
