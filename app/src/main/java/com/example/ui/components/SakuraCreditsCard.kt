package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SakuraSoftPink = Color(0xFFFFB7C5)
private val SakuraDeepPink = Color(0xFFFF6B95)
private val SakuraPalePetal = Color(0xFFFFDCE5)
private val SakuraBranchColor = Color(0xFF4A2535)

private data class SakuraPetalSeed(
    val xFraction: Float,
    val yOffset: Float,
    val speed: Float,
    val sizeDp: Float,
    val swayAmplitude: Float,
    val swayFreq: Float,
    val spinSpeed: Float,
    val alpha: Float,
    val tint: Color
)

@Composable
fun SakuraCreditsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val transition = rememberInfiniteTransition(label = "sakura_credits")
    val fallPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sakura_fall_phase"
    )
    val branchSway by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sakura_branch_sway"
    )

    val petals = remember {
        listOf(
            SakuraPetalSeed(0.08f, 0.05f, 1.0f, 9f, 18f, 1.5f, 180f, 0.88f, SakuraSoftPink),
            SakuraPetalSeed(0.18f, 0.42f, 0.85f, 7f, 14f, 2.1f, -150f, 0.72f, SakuraPalePetal),
            SakuraPetalSeed(0.28f, 0.78f, 1.15f, 10f, 22f, 1.3f, 220f, 0.85f, SakuraDeepPink),
            SakuraPetalSeed(0.36f, 0.22f, 0.9f, 8f, 16f, 1.8f, -200f, 0.78f, SakuraSoftPink),
            SakuraPetalSeed(0.47f, 0.61f, 1.2f, 11f, 20f, 1.2f, 160f, 0.90f, SakuraPalePetal),
            SakuraPetalSeed(0.58f, 0.14f, 0.8f, 7f, 12f, 2.0f, -140f, 0.68f, SakuraSoftPink),
            SakuraPetalSeed(0.67f, 0.52f, 1.05f, 9f, 18f, 1.6f, 210f, 0.85f, SakuraDeepPink),
            SakuraPetalSeed(0.75f, 0.86f, 0.95f, 8f, 15f, 1.9f, -190f, 0.76f, SakuraSoftPink),
            SakuraPetalSeed(0.84f, 0.31f, 1.1f, 10f, 21f, 1.4f, 175f, 0.88f, SakuraPalePetal),
            SakuraPetalSeed(0.92f, 0.69f, 0.85f, 8f, 13f, 2.2f, -165f, 0.74f, SakuraDeepPink),
            SakuraPetalSeed(0.24f, 0.19f, 1.25f, 6f, 10f, 2.4f, 240f, 0.62f, SakuraPalePetal),
            SakuraPetalSeed(0.52f, 0.89f, 0.9f, 7f, 15f, 1.7f, -170f, 0.70f, SakuraSoftPink),
            SakuraPetalSeed(0.79f, 0.08f, 1.15f, 9f, 17f, 1.5f, 195f, 0.82f, SakuraSoftPink),
            SakuraPetalSeed(0.62f, 0.37f, 0.82f, 6f, 11f, 2.3f, -210f, 0.65f, SakuraPalePetal)
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sakura_credits_card"),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = BorderStroke(
            1.2.dp,
            Brush.linearGradient(
                listOf(
                    SakuraSoftPink.copy(alpha = 0.75f),
                    SakuraDeepPink.copy(alpha = 0.45f),
                    ElectricCyan.copy(alpha = 0.35f)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF1A0E1B),
                            Color(0xFF281227),
                            Color(0xFF131324)
                        )
                    )
                )
        ) {
            // Animated Sakura petals & cherry blossom branch
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(24.dp))
            ) {
                val w = size.width
                val h = size.height

                // Soft sakura ambient glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(SakuraDeepPink.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(w * 0.85f, h * 0.22f),
                        radius = w * 0.55f
                    ),
                    radius = w * 0.55f,
                    center = Offset(w * 0.85f, h * 0.22f)
                )

                // Stylized Sakura branch in top-right corner with gentle sway
                val swayPx = branchSway * 5.dp.toPx()
                val branchPath = Path().apply {
                    moveTo(w + 8f, h * 0.08f)
                    quadraticTo(w * 0.82f, h * 0.16f + swayPx * 0.4f, w * 0.66f, h * 0.11f + swayPx)
                }
                drawPath(
                    path = branchPath,
                    color = SakuraBranchColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
                // Blossom clusters on branch
                drawSakuraBlossom(Offset(w * 0.70f, h * 0.11f + swayPx), 9.dp.toPx())
                drawSakuraBlossom(Offset(w * 0.83f, h * 0.15f + swayPx * 0.5f), 11.dp.toPx())
                drawSakuraBlossom(Offset(w * 0.92f, h * 0.10f + swayPx * 0.2f), 8.dp.toPx())

                // Falling animated Sakura petals
                petals.forEach { p ->
                    val yNorm = (p.yOffset + fallPhase * p.speed) % 1.15f - 0.08f
                    val yPos = yNorm * h
                    val angleRad = (fallPhase * p.swayFreq + p.yOffset) * 2.0 * PI
                    val xPos = (p.xFraction * w) + (sin(angleRad).toFloat() * p.swayAmplitude.dp.toPx())
                    val rot = (fallPhase * p.spinSpeed) + (p.yOffset * 360f)
                    val pxSize = p.sizeDp.dp.toPx()

                    rotate(degrees = rot, pivot = Offset(xPos, yPos)) {
                        drawSakuraPetal(
                            center = Offset(xPos, yPos),
                            petalSize = pxSize,
                            color = p.tint.copy(alpha = p.alpha)
                        )
                    }
                }
            }

            // Foreground Developer Credits Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassStatusPill(
                        text = lStr(R.string.credits_sakura_badge),
                        color = SakuraSoftPink
                    )
                    GlassStatusPill(
                        text = "PKG FORGE",
                        color = ElectricCyan
                    )
                }

                // JuanDev Row with Custom Hacker Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        ElectricCyan.copy(alpha = 0.24f),
                                        SakuraDeepPink.copy(alpha = 0.24f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_hacker_hood),
                            contentDescription = "Hacker JuanDev",
                            tint = ElectricCyan,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "JuanDev",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.testTag("credits_juandev_name")
                        )
                        Text(
                            text = lStr(R.string.credits_role_subtitle),
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelMedium,
                            color = SakuraSoftPink
                        )
                    }
                }

                // Instagram Credit Row with IG Icon
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/xjp177ez"))
                                )
                            }
                        }
                        .testTag("credits_instagram_btn"),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, SakuraSoftPink.copy(alpha = 0.40f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFFF58529),
                                                Color(0xFFDD2A7B),
                                                Color(0xFF8134AF),
                                                Color(0xFF515BD4)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_instagram),
                                    contentDescription = "Instagram",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Instagram: xjp177ez",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "@xjp177ez",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldSuccess
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = "Open Instagram",
                            tint = SakuraSoftPink,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSakuraPetal(center: Offset, petalSize: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + petalSize * 0.45f)
        cubicTo(
            center.x - petalSize * 0.55f, center.y + petalSize * 0.15f,
            center.x - petalSize * 0.50f, center.y - petalSize * 0.55f,
            center.x - petalSize * 0.12f, center.y - petalSize * 0.42f
        )
        lineTo(center.x, center.y - petalSize * 0.25f)
        lineTo(center.x + petalSize * 0.12f, center.y - petalSize * 0.42f)
        cubicTo(
            center.x + petalSize * 0.50f, center.y - petalSize * 0.55f,
            center.x + petalSize * 0.55f, center.y + petalSize * 0.15f,
            center.x, center.y + petalSize * 0.45f
        )
        close()
    }
    drawPath(path = path, color = color)
}

private fun DrawScope.drawSakuraBlossom(center: Offset, radius: Float) {
    for (i in 0 until 5) {
        val deg = i * 72f
        val rad = deg * PI / 180.0
        val petalCenter = Offset(
            center.x + (cos(rad) * radius * 0.55f).toFloat(),
            center.y + (sin(rad) * radius * 0.55f).toFloat()
        )
        rotate(degrees = deg + 90f, pivot = petalCenter) {
            drawSakuraPetal(
                center = petalCenter,
                petalSize = radius * 0.85f,
                color = if (i % 2 == 0) SakuraSoftPink.copy(alpha = 0.88f) else SakuraPalePetal.copy(alpha = 0.85f)
            )
        }
    }
    drawCircle(
        color = Color(0xFFFFD166).copy(alpha = 0.9f),
        radius = radius * 0.20f,
        center = center
    )
}
