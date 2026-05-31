package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// Tema "dark premium" — Mi Panel Financiero
// ----------------------------------------------------------------------------
// Tokens del handoff mapeados a ColorScheme / Typography / Shapes. Los semánticos
// que Material 3 no cubre (success / warning / cyan) viajan por CompositionLocal
// en [FinanceColors]. El rediseño es exclusivamente oscuro: ambos modos del
// sistema usan este esquema para mantener la identidad premium y el contraste.
//
// Nota de mapeo: el código existente usa `colorScheme.surface` para tarjetas y
// `colorScheme.background` para el fondo de pantalla. Por eso `surface` apunta a
// la superficie de tarjeta (#14161B) y `background` al fondo (#0E0F12); así las
// tarjetas se separan del fondo sin reescribir cada pantalla.
// ============================================================================

private val FinanceDarkScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = OnAccent,
    primaryContainer = SurfaceHigh,
    onPrimaryContainer = OnSurface,
    secondary = AccentCyan,
    onSecondary = OnAccent,
    tertiary = Success,
    onTertiary = OnAccent,
    background = Background,
    onBackground = OnSurface,
    surface = SurfaceContainer,
    onSurface = OnSurface,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = Background,
    surfaceContainerLow = SurfaceContainer,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Negative,
    onError = OnAccent,
)

/** Colores semánticos fuera del estándar Material 3 (success / warning / cyan). */
@Immutable
data class FinanceColors(
    val success: Color = Success,
    val warning: Color = Warning,
    val accentCyan: Color = AccentCyan,
)

val LocalFinanceColors = staticCompositionLocalOf { FinanceColors() }

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    // Identidad dark premium: el esquema oscuro se aplica siempre.
    MaterialTheme(
        colorScheme = FinanceDarkScheme,
        typography = Typography,
        shapes = FinanceShapes,
        content = {
            CompositionLocalProvider(
                LocalFinanceColors provides FinanceColors(),
                content = content,
            )
        },
    )
}
