package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val GrassDarkColorScheme = darkColorScheme(
  primary = EmeraldPhosphor,
  onPrimary = Color(0xFF032116),
  primaryContainer = EmeraldForest,
  onPrimaryContainer = MintGlow,
  secondary = GrassMint,
  onSecondary = Color(0xFF052E16),
  secondaryContainer = ObsidianSurfaceHigh,
  onSecondaryContainer = MintSoft,
  tertiary = AmberWarning,
  onTertiary = Color(0xFF2B1B00),
  background = ObsidianBg,
  onBackground = TextLightPrimary,
  surface = ObsidianSurface,
  onSurface = TextLightPrimary,
  surfaceVariant = ObsidianSurfaceVariant,
  onSurfaceVariant = TextLightSecondary,
  surfaceContainer = ObsidianSurface,
  surfaceContainerHigh = ObsidianSurfaceHigh,
  outline = ObsidianBorder,
  outlineVariant = Color(0xFF1E3227),
  error = CoralError,
  onError = Color(0xFF33060E),
)

private val GrassLightColorScheme = lightColorScheme(
  primary = EmeraldDeep,
  onPrimary = Color.White,
  primaryContainer = MintSoft,
  onPrimaryContainer = EmeraldForest,
  secondary = EmeraldBright,
  onSecondary = Color.White,
  secondaryContainer = LightSageVariant,
  onSecondaryContainer = TextDarkPrimary,
  tertiary = AmberDeep,
  onTertiary = Color.White,
  background = LightSageBg,
  onBackground = TextDarkPrimary,
  surface = LightSageSurface,
  onSurface = TextDarkPrimary,
  surfaceVariant = LightSageVariant,
  onSurfaceVariant = TextDarkSecondary,
  surfaceContainer = LightSageSurface,
  surfaceContainerHigh = LightSageVariant,
  outline = LightSageBorder,
  outlineVariant = Color(0xFFD5E7DC),
  error = CrimsonError,
  onError = Color.White,
)

private val GrassShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
)

@Composable
fun GrassSshTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) GrassDarkColorScheme else GrassLightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = GrassSshTypography,
    shapes = GrassShapes,
    content = content,
  )
}
