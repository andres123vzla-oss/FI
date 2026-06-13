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
// Sistema de color "claro" — Mi Panel Financiero (UX-01)
// ----------------------------------------------------------------------------
// Paleta clara real para cuando el sistema está en modo claro. Las superficies
// pasan a tonos casi-blancos y el texto a tonos oscuros; los acentos/semánticos
// se oscurecen lo necesario para mantener contraste WCAG AA (>=4.5:1) sobre
// superficies claras (un cyan/verde/rojo demasiado luminosos fallarían).
// ============================================================================

// --- Superficies y texto (claro) ---
val LightBg              = Color(0xFFF6F7F9) // Fondo de pantalla claro
val LightSurfaceContainer= Color(0xFFFFFFFF) // Tarjetas
val LightSurfaceHigh     = Color(0xFFEDEFF3) // Anidado / inputs
val LightSurfaceHighest  = Color(0xFFE2E6EC) // Hover / chips / track
val LightOutlineVariant  = Color(0xFFD8DCE3) // Bordes 1px (hairline)
val LightOutline         = Color(0xFF6C7283) // Terciario / labels
val LightOnSurface       = Color(0xFF14161B) // Texto principal
val LightOnSurfaceVariant= Color(0xFF565E6D) // Texto secundario

// --- Acento y semánticos (claro, oscurecidos para contraste sobre blanco) ---
val LightAccentBlue = Color(0xFF1F6FEB) // Acción primaria sobre claro
val LightAccentCyan = Color(0xFF0E7F8C) // Acento secundario sobre claro
val LightSuccess    = Color(0xFF0E8A5F) // Ingresos / ganancia sobre claro
val LightNegative   = Color(0xFFC4344B) // Gastos / pérdida / error sobre claro
val LightWarning    = Color(0xFF9A6B00) // Cerca del límite sobre claro
val LightOnAccent   = Color(0xFFFFFFFF) // Texto/iconos sobre el acento claro

// ============================================================================
// UX2-01: los alias estáticos Excel* (ExcelGreen/ExcelRed/ExcelMediumBlue/ExcelDarkBlue)
// fueron ELIMINADOS: apuntaban siempre a la paleta oscura y fallaban WCAG AA en modo
// claro (1.9–3.2:1 sobre tarjeta blanca). Las pantallas consumen los semánticos vía
// LocalFinanceColors.current (success/negative/warning/accentCyan) y
// MaterialTheme.colorScheme (primary/error), que sí cambian por tema.
// ============================================================================

// Fondos/superficies con nombres heredados. Ahora que existe un tema claro real
// (UX-01), los alias Light* apuntan a las superficies claras y los Dark* a las
// oscuras, para que cualquier referencia accidental use el color correcto por tema.
val LightBackground = LightBg
val LightSurface = LightSurfaceContainer
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
