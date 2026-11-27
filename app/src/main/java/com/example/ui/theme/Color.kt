package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Verdant Terminal Craft Palette
val EmeraldPhosphor = Color(0xFF34D399)
val EmeraldBright = Color(0xFF10B981)
val EmeraldDeep = Color(0xFF047857)
val EmeraldForest = Color(0xFF064E3B)
val MintGlow = Color(0xFFA7F3D0)
val MintSoft = Color(0xFFD1FAE5)
val GrassMint = Color(0xFF86EFAC)

val ObsidianBg = Color(0xFF0A110D)
val ObsidianSurface = Color(0xFF101A14)
val ObsidianSurfaceVariant = Color(0xFF18261E)
val ObsidianSurfaceHigh = Color(0xFF1F3127)
val ObsidianBorder = Color(0xFF284032)

val LightSageBg = Color(0xFFF3F8F5)
val LightSageSurface = Color(0xFFFFFFFF)
val LightSageVariant = Color(0xFFE3EFE8)
val LightSageBorder = Color(0xFFC5DDD0)

val AmberWarning = Color(0xFFFBBF24)
val AmberDeep = Color(0xFFB45309)
val CoralError = Color(0xFFFB7185)
val CrimsonError = Color(0xFFBE123C)
val TextLightPrimary = Color(0xFFE8F5EE)
val TextLightSecondary = Color(0xFF9AB8A6)
val TextDarkPrimary = Color(0xFF0D1F15)
val TextDarkSecondary = Color(0xFF476654)

enum class TerminalThemePreset(
  val displayName: String,
  val background: Color,
  val surface: Color,
  val foreground: Color,
  val accent: Color,
  val cursor: Color,
) {
  GRASS_PHOSPHOR(
    displayName = "Grass Phosphor",
    background = Color(0xFF07100B),
    surface = Color(0xFF0E1B13),
    foreground = Color(0xFFD8F7E6),
    accent = Color(0xFF34D399),
    cursor = Color(0xFF4ADE80),
  ),
  OBSIDIAN_EMERALD(
    displayName = "Obsidian Pro",
    background = Color(0xFF0C0F14),
    surface = Color(0xFF151B24),
    foreground = Color(0xFFE2E8F0),
    accent = Color(0xFF38BDF8),
    cursor = Color(0xFF34D399),
  ),
  SOLARIZED_DARK(
    displayName = "Solarized Deep",
    background = Color(0xFF002B36),
    surface = Color(0xFF073642),
    foreground = Color(0xFFEEE8D5),
    accent = Color(0xFF2AA198),
    cursor = Color(0xFF859900),
  ),
  RETRO_AMBER(
    displayName = "Amber CRT",
    background = Color(0xFF120C04),
    surface = Color(0xFF1E1408),
    foreground = Color(0xFFFDE68A),
    accent = Color(0xFFF59E0B),
    cursor = Color(0xFFFBBF24),
  ),
}
