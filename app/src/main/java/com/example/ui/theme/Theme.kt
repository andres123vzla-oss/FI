package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
// en [FinanceColors]. Hay tema claro y oscuro reales (UX-01): se elige el esquema
// según `darkTheme`, que por defecto sigue al sistema (isSystemInDarkTheme).
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

// Esquema claro real (UX-01): superficies casi-blancas y acentos/semánticos
// oscurecidos para contraste AA sobre fondo claro.
private val FinanceLightScheme = lightColorScheme(
    primary = LightAccentBlue,
    onPrimary = LightOnAccent,
    primaryContainer = LightSurfaceHigh,
    onPrimaryContainer = LightOnSurface,
    secondary = LightAccentCyan,
    onSecondary = LightOnAccent,
    tertiary = LightSuccess,
    onTertiary = LightOnAccent,
    background = LightBg,
    onBackground = LightOnSurface,
    surface = LightSurfaceContainer,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightBg,
    surfaceContainerLow = LightSurfaceContainer,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightNegative,
    onError = LightOnAccent,
)

/** Colores semánticos fuera del estándar Material 3 (success / warning / cyan). */
@Immutable
data class FinanceColors(
    val success: Color = Success,
    val warning: Color = Warning,
    val accentCyan: Color = AccentCyan,
)

// Variantes por tema: el oscuro usa los semánticos premium; el claro usa los
// oscurecidos para mantener contraste cuando se pintan sobre superficies claras.
private val DarkFinanceColors = FinanceColors(
    success = Success,
    warning = Warning,
    accentCyan = AccentCyan,
)
private val LightFinanceColors = FinanceColors(
    success = LightSuccess,
    warning = LightWarning,
    accentCyan = LightAccentCyan,
)

val LocalFinanceColors = staticCompositionLocalOf { FinanceColors() }

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Se elige el esquema según el modo del sistema (o el valor forzado por el caller).
    val colorScheme = if (darkTheme) FinanceDarkScheme else FinanceLightScheme
    val financeColors = if (darkTheme) DarkFinanceColors else LightFinanceColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = FinanceShapes,
        content = {
            CompositionLocalProvider(
                LocalFinanceColors provides financeColors,
                content = content,
            )
        },
    )
}
