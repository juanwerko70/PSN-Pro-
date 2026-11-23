package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class AppThemeMode {
    DARK_GLASS,
    LIGHT_GLASS,
    SYSTEM_DYNAMIC
}

data class GlassConfig(
    val isDark: Boolean = true,
    val opacity: Float = 0.56f
)

val LocalGlassConfig = staticCompositionLocalOf { GlassConfig() }

private val DarkGlassColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF001E2E),
    primaryContainer = DeepSapphireContainer,
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = IridescentIndigo,
    onSecondary = Color(0xFF0F0B3A),
    secondaryContainer = Color(0xFF1E2252),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = VioletAccent,
    onTertiary = Color(0xFF1F0A4A),
    background = ObsidianBg,
    onBackground = SlateTextPrimary,
    surface = ObsidianSurface,
    onSurface = SlateTextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = SlateTextSecondary,
    outline = Color(0xFF2B3D63),
    error = CoralError,
    onError = Color.White
)

private val LightGlassColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0EFFF),
    onPrimaryContainer = Color(0xFF002744),
    secondary = IndigoSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color.White,
    background = IceBg,
    onBackground = IceTextPrimary,
    surface = IceSurface,
    onSurface = IceTextPrimary,
    surfaceVariant = IceSurfaceVariant,
    onSurfaceVariant = IceTextSecondary,
    outline = Color(0xFFCBD5E1),
    error = CoralError,
    onError = Color.White
)

val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PkgForgeTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK_GLASS,
    glassOpacity: Float = 0.56f,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        AppThemeMode.DARK_GLASS -> true
        AppThemeMode.LIGHT_GLASS -> false
        AppThemeMode.SYSTEM_DYNAMIC -> systemDark
    }

    val colorScheme = when {
        themeMode == AppThemeMode.SYSTEM_DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> DarkGlassColorScheme
        else -> LightGlassColorScheme
    }

    CompositionLocalProvider(LocalGlassConfig provides GlassConfig(isDark = useDark, opacity = glassOpacity)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
