package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Sistema de color "dark premium" — Mi Panel Financiero
// ----------------------------------------------------------------------------
// Base casi-negra neutra con una sola familia de acento (azul eléctrico → cyan)
// y semánticos para positivo / negativo / alerta. El color se reserva para datos
// y acciones; nunca es decorativo. Ver handoff (sección 01 · Color).
// ============================================================================

// --- Superficies y texto ---
val Background       = Color(0xFF0E0F12) // Fondo de pantalla
val SurfaceContainer = Color(0xFF14161B) // Tarjetas
val SurfaceHigh      = Color(0xFF1A1D24) // Anidado / inputs
val SurfaceHighest   = Color(0xFF20242D) // Hover / chips / track
val OutlineVariant   = Color(0xFF23262F) // Bordes 1px (hairline)
val Outline          = Color(0xFF6C7283) // Terciario / labels
val OnSurface        = Color(0xFFF3F5F9) // Texto principal
val OnSurfaceVariant = Color(0xFFA2A9B8) // Texto secundario

// --- Acento y semánticos ---
val AccentBlue = Color(0xFF4D8DFF) // Acción primaria, marca
val AccentCyan = Color(0xFF36D6E7) // Acento secundario (degradado con AccentBlue)
val Success    = Color(0xFF34D399) // Ingresos, ganancia
val Negative   = Color(0xFFFB7185) // Gastos, pérdida, error
val Warning    = Color(0xFFF6C453) // Cerca del límite
val OnAccent   = Color(0xFF07101F) // Texto/iconos sobre el acento (alto contraste)

// ============================================================================
// Compatibilidad: los nombres "Excel*" existentes se conservan pero ahora apuntan
// al nuevo sistema premium. Así toda la UI que ya los usa adopta el rediseño sin
// reescribir cada pantalla y sin perder funcionalidad.
// ============================================================================
val ExcelDarkBlue = AccentBlue   // antes indigo; ahora acento azul eléctrico
val ExcelGreen = Success         // ingresos / ganancia
val ExcelRed = Negative          // gastos / pérdida / error
val ExcelMediumBlue = AccentBlue // gráficos / highlights / portafolio

// Fondos/superficies en modo claro y oscuro (se mantienen los nombres; el rediseño
// es dark premium, por lo que ambos esquemas usan la paleta oscura).
val LightBackground = Background
val LightSurface = SurfaceContainer
val DarkBackground = Background
val DarkSurface = SurfaceContainer

// Parámetros heredados del esquema dinámico de la plantilla (sin uso activo; se
// conservan para no romper referencias accidentales).
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
