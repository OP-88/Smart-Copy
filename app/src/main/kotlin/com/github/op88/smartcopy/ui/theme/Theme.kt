package com.github.op88.smartcopy.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ──────────────────────────────────────────────────────────────────────────────
// Smart Copy Colour Palette
//
// Industrial dark-mode palette:
//   Primary   — Cyan 400  (#38BDF8) — action elements, selection highlights
//   Secondary — Slate 400 (#94A3B8) — secondary text, icons
//   Surface   — Slate 900 (#0F172A) — card / sheet backgrounds
//   Background— Near-black (#0A0A0F) — app background
// ──────────────────────────────────────────────────────────────────────────────

private val primaryCyan    = Color(0xFF38BDF8)
private val onPrimary      = Color(0xFF0A0A0F)
private val primaryContainer = Color(0xFF0C4A6E)

private val secondarySlate    = Color(0xFF94A3B8)
private val onSecondary       = Color(0xFF0F172A)
private val secondaryContainer = Color(0xFF1E293B)

private val backgroundDark  = Color(0xFF0A0A0F)
private val surfaceDark      = Color(0xFF0F172A)
private val surfaceVariant   = Color(0xFF1E293B)
private val onSurface        = Color(0xFFF1F5F9)
private val onSurfaceVariant = Color(0xFF94A3B8)

private val errorRed    = Color(0xFFEF4444)
private val onError     = Color(0xFF0A0A0F)

private val SmartCopyDarkColorScheme = darkColorScheme(
    primary            = primaryCyan,
    onPrimary          = onPrimary,
    primaryContainer   = primaryContainer,
    onPrimaryContainer = Color(0xFFBAE6FD),

    secondary            = secondarySlate,
    onSecondary          = onSecondary,
    secondaryContainer   = secondaryContainer,
    onSecondaryContainer = Color(0xFFCBD5E1),

    background    = backgroundDark,
    onBackground  = onSurface,
    surface       = surfaceDark,
    onSurface     = onSurface,
    surfaceVariant    = surfaceVariant,
    onSurfaceVariant  = onSurfaceVariant,

    error    = errorRed,
    onError  = onError,

    outline         = Color(0xFF334155),
    outlineVariant  = Color(0xFF1E293B),
)

@Composable
fun SmartCopyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SmartCopyDarkColorScheme,
        typography  = SmartCopyTypography,
        content     = content,
    )
}
