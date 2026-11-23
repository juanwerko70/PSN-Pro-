package com.example.ui.components

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.IridescentIndigo
import com.example.ui.theme.LocalGlassConfig
import com.example.ui.theme.VioletAccent
import java.util.Locale

val LocalAppContext = staticCompositionLocalOf<Context> {
    error("LocalAppContext not provided")
}

@Composable
fun ProvideLocalizedAppContext(languageTag: String, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val localizedContext = remember(baseContext, languageTag) {
        if (languageTag == "system" || languageTag.isBlank()) {
            baseContext
        } else {
            val locale = Locale.forLanguageTag(languageTag)
            val config = Configuration(baseContext.resources.configuration)
            config.setLocale(locale)
            baseContext.createConfigurationContext(config)
        }
    }
    CompositionLocalProvider(LocalAppContext provides localizedContext) {
        content()
    }
}

@Composable
fun lStr(@StringRes id: Int, vararg args: Any): String {
    val ctx = LocalAppContext.current
    return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
}

@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalGlassConfig.current
    val bgColor = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val transition = rememberInfiniteTransition(label = "liquid_orbs")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_drift"
    )

    Box(modifier = modifier.fillMaxSize().background(bgColor)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val orbAlpha = if (glass.isDark) 0.22f else 0.16f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = orbAlpha), Color.Transparent),
                    center = Offset(w * (0.2f + 0.18f * drift), h * 0.16f),
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = Offset(w * (0.2f + 0.18f * drift), h * 0.16f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondary.copy(alpha = orbAlpha * 0.9f), Color.Transparent),
                    center = Offset(w * (0.85f - 0.2f * drift), h * 0.45f),
                    radius = w * 0.60f
                ),
                radius = w * 0.60f,
                center = Offset(w * (0.85f - 0.2f * drift), h * 0.45f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tertiary.copy(alpha = orbAlpha * 0.75f), Color.Transparent),
                    center = Offset(w * (0.3f + 0.25f * drift), h * 0.88f),
                    radius = w * 0.55f
                ),
                radius = w * 0.55f,
                center = Offset(w * (0.3f + 0.25f * drift), h * 0.88f)
            )
        }
        content()
    }
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = LocalGlassConfig.current
    val baseAlpha = glass.opacity.coerceIn(0.25f, 0.92f)
    val surfaceColor = if (glass.isDark) {
        MaterialTheme.colorScheme.surface.copy(alpha = baseAlpha)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = (baseAlpha + 0.22f).coerceAtMost(0.95f))
    }

    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (glass.isDark) 0.28f else 0.70f),
            accentColor.copy(alpha = 0.22f),
            Color.White.copy(alpha = if (glass.isDark) 0.06f else 0.20f)
        )
    )

    val cardModifier = modifier
        .clip(shape)
        .background(surfaceColor)
        .border(BorderStroke(1.dp, borderBrush), shape)
        .let { m ->
            if (onClick != null) m.clickable(onClick = onClick) else m
        }

    Box(modifier = cardModifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun GlassStatusPill(
    text: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun GlassMetricTile(
    label: String,
    value: String,
    accent: Color = ElectricCyan,
    modifier: Modifier = Modifier
) {
    LiquidGlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(12.dp),
        accentColor = accent
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PermissionLockedPanel(
    roleName: String,
    permissionName: String,
    modifier: Modifier = Modifier
) {
    LiquidGlassCard(
        modifier = modifier.fillMaxWidth(),
        accentColor = CoralError,
        contentPadding = PaddingValues(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(CoralError.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = lStr(R.string.perm_denied_title),
                    tint = CoralError,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = lStr(R.string.perm_denied_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = lStr(R.string.perm_denied_desc, roleName, permissionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            GlassStatusPill(text = permissionName, color = CoralError, icon = Icons.Rounded.Lock)
        }
    }
}

@Composable
fun SectionHeaderRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    ElectricCyan.copy(alpha = 0.22f),
                                    IridescentIndigo.copy(alpha = 0.22f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
