package com.vectorheart.asuna.ui.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * iOS 26 Liquid Glass Design System for Asuna VectorHeart.
 *
 * Implements physical glassmorphism, multi-layer depth, dual-tone Fresnel
 * specular rim refraction, internal gloss sheen, and blooming luminous glows.
 * Inspired by QWEA0/Liquid-Glass-Android and Kyant0/AndroidLiquidGlass.
 */
object LiquidGlassColors {
    // Glass Surface Dark Tints (Obsidian / Midnight Sapphire)
    val ObsidianDark = Color(0x381A0F1F)
    val ObsidianElevated = Color(0x4C231429)
    val ObsidianDeep = Color(0x751A0F1F)
    val FrostedSurface = Color(0x35261829)
    val FrostedSurfaceElevated = Color(0x4D3D2937)
    val CrystallineLight = Color(0x1FFFFFFF)

    // Specular Highlight Colors
    val SpecularHigh = Color(0xD9FFFFFF)    // 85% opacity intense incident light hit
    val SpecularMid = Color(0x33FFFFFF)     // 20% opacity translucent side
    val SpecularLow = Color(0x0AFFFFFF)     // 4% opacity dark transmission
    val SpecularBounce = Color(0x66FFFFFF)  // 40% opacity bottom Fresnel ground bounce

    // Luminous Accent Colors & Glass Tints
    val AsunaLavenderTint = Color(0x38B8A0FF)
    val AsunaLavenderGlow = Color(0xFFB8A0FF)
    val AsunaLavenderBorder = Color(0xFFD6C7FF)

    val WarmPeachTint = Color(0x3DFF9F66)
    val WarmPeachGlow = Color(0xFFFF9F66)
    val WarmPeachBorder = Color(0xFFFFD4BD)

    val CyberCyanTint = Color(0x3000E5FF)
    val CyberCyanGlow = Color(0xFF00E5FF)
    val CyberCyanBorder = Color(0xFF80F3FF)

    val NeonMintTint = Color(0x3000FF9D)
    val NeonMintGlow = Color(0xFF00FF9D)
    val NeonMintBorder = Color(0xFF80FFCE)

    val RubyErrorTint = Color(0x40FF5252)
    val RubyErrorGlow = Color(0xFFFF5252)
    val RubyErrorBorder = Color(0xFFFFB4AB)

    val AmberWarningTint = Color(0x35FFC107)
    val AmberWarningGlow = Color(0xFFFFC107)

    /**
     * Dual-tone physical Fresnel specular border brush.
     * Simulates physical incident light striking top-left edge of curved glass
     * with high opacity, fading around curves, and reflecting secondary bounce on bottom-right.
     */
    fun specularBorderBrush(
        intensity: Float = 1.0f,
        highlightColor: Color = Color.White,
        bounceColor: Color = Color.White
    ): Brush {
        val high = highlightColor.copy(alpha = (0.85f * intensity).coerceIn(0f, 1f))
        val mid = highlightColor.copy(alpha = (0.18f * intensity).coerceIn(0f, 1f))
        val low = highlightColor.copy(alpha = (0.05f * intensity).coerceIn(0f, 1f))
        val bounce = bounceColor.copy(alpha = (0.45f * intensity).coerceIn(0f, 1f))

        return Brush.linearGradient(
            0.00f to high,
            0.25f to mid,
            0.52f to low,
            0.75f to mid,
            1.00f to bounce,
            start = Offset(0f, 0f),
            end = Offset.Infinite
        )
    }

    /**
     * Refractive internal glass sheen brush (top-left gloss curvature).
     */
    fun glassSheenBrush(intensity: Float = 1.0f): Brush {
        return Brush.linearGradient(
            0.00f to Color.White.copy(alpha = (0.24f * intensity).coerceIn(0f, 1f)),
            0.20f to Color.White.copy(alpha = (0.07f * intensity).coerceIn(0f, 1f)),
            0.60f to Color.Transparent,
            1.00f to Color.White.copy(alpha = (0.09f * intensity).coerceIn(0f, 1f)),
            start = Offset(0f, 0f),
            end = Offset.Infinite
        )
    }
}

/**
 * Applies a liquid glass border with physical Fresnel dual-tone specular reflections.
 */
fun Modifier.liquidGlassBorder(
    shape: Shape = RoundedCornerShape(16.dp),
    strokeWidth: Dp = 1.dp,
    intensity: Float = 1.0f,
    highlightColor: Color = Color.White,
    bounceColor: Color = Color.White
): Modifier = this.border(
    width = strokeWidth,
    brush = LiquidGlassColors.specularBorderBrush(
        intensity = intensity,
        highlightColor = highlightColor,
        bounceColor = bounceColor
    ),
    shape = shape
)

/**
 * Applies multi-layer liquid glass surface styling to any Compose component:
 * 1. Ambient soft depth / glow underneath (if glowRadius > 0)
 * 2. Translucent tinted frosted base
 * 3. Directional refraction sheen
 * 4. Specular Fresnel edge refraction border
 * 5. Clean shape clipping for children
 */
fun Modifier.liquidGlassSurface(
    shape: Shape = RoundedCornerShape(20.dp),
    tintColor: Color = LiquidGlassColors.ObsidianElevated,
    frostedAlpha: Float = 0.65f,
    specularAlpha: Float = 0.85f,
    borderWidth: Dp = 1.dp,
    glowColor: Color = Color.Transparent,
    glowRadius: Dp = 0.dp,
    sheenIntensity: Float = 0.65f,
    highlightColor: Color = Color.White,
    bounceColor: Color = Color.White
): Modifier = this
    .drawBehind {
        val glowPx = glowRadius.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)

        // 1. Ambient glow / diffusion halo
        if (glowPx > 0f && glowColor.alpha > 0f) {
            drawOutline(
                outline = outline,
                brush = Brush.radialGradient(
                    0.0f to glowColor.copy(alpha = (glowColor.alpha * 0.70f).coerceIn(0f, 1f)),
                    0.65f to glowColor.copy(alpha = (glowColor.alpha * 0.18f).coerceIn(0f, 1f)),
                    1.0f to Color.Transparent,
                    center = center,
                    radius = (size.maxDimension / 2f) + glowPx
                )
            )
        }

        // 2. Translucent tinted frosted background
        drawOutline(
            outline = outline,
            brush = Brush.linearGradient(
                0.0f to tintColor.copy(alpha = (frostedAlpha * 1.15f).coerceIn(0f, 1f)),
                0.5f to tintColor.copy(alpha = frostedAlpha.coerceIn(0f, 1f)),
                1.0f to tintColor.copy(alpha = (frostedAlpha * 0.85f).coerceIn(0f, 1f)),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            )
        )

        // 3. Top-down internal refractive gloss sheen
        if (sheenIntensity > 0f) {
            drawOutline(
                outline = outline,
                brush = LiquidGlassColors.glassSheenBrush(sheenIntensity)
            )
        }
    }
    .clip(shape)
    .border(
        width = borderWidth,
        brush = LiquidGlassColors.specularBorderBrush(
            intensity = specularAlpha,
            highlightColor = highlightColor,
            bounceColor = bounceColor
        ),
        shape = shape
    )

/**
 * Convenience modifier for liquid glass capsules (floating docks, pills, buttons).
 */
fun Modifier.liquidGlassCapsule(
    tintColor: Color = LiquidGlassColors.ObsidianElevated,
    frostedAlpha: Float = 0.72f,
    specularAlpha: Float = 0.95f,
    borderWidth: Dp = 1.2.dp,
    glowColor: Color = Color.Transparent,
    glowRadius: Dp = 0.dp,
    sheenIntensity: Float = 0.75f,
    highlightColor: Color = Color.White,
    bounceColor: Color = Color.White
): Modifier = this.liquidGlassSurface(
    shape = CircleShape,
    tintColor = tintColor,
    frostedAlpha = frostedAlpha,
    specularAlpha = specularAlpha,
    borderWidth = borderWidth,
    glowColor = glowColor,
    glowRadius = glowRadius,
    sheenIntensity = sheenIntensity,
    highlightColor = highlightColor,
    bounceColor = bounceColor
)

/**
 * Draws an ambient luminous neon bloom glow behind any shape.
 * Supports pulsing / breathing animation for active listening/thinking states.
 */
fun Modifier.liquidGlassGlow(
    color: Color = LiquidGlassColors.WarmPeachGlow,
    radius: Dp = 16.dp,
    alpha: Float = 0.45f,
    shape: Shape = CircleShape,
    pulsing: Boolean = false
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidGlassGlowPulse")
    val animatedAlpha by if (pulsing) {
        infiniteTransition.animateFloat(
            initialValue = alpha * 0.45f,
            targetValue = alpha * 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "GlowAlpha"
        )
    } else {
        remember(alpha) { mutableStateOf(alpha) }
    }

    val animatedRadiusScale by if (pulsing) {
        infiniteTransition.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "GlowRadiusScale"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    this.drawBehind {
        val glowPx = radius.toPx() * animatedRadiusScale
        if (glowPx > 0f && animatedAlpha > 0f) {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(
                outline = outline,
                brush = Brush.radialGradient(
                    0.0f to color.copy(alpha = animatedAlpha.coerceIn(0f, 1f)),
                    0.60f to color.copy(alpha = (animatedAlpha * 0.35f).coerceIn(0f, 1f)),
                    1.0f to Color.Transparent,
                    center = center,
                    radius = (size.maxDimension / 2f) + glowPx
                )
            )
        }
    }
}

/**
 * Renders a 3D spherical liquid glass orb with caustic specular light reflections,
 * deep refraction core, bottom rim bounce, and optional animated pulse.
 */
fun Modifier.liquidGlassOrb(
    coreColor: Color = LiquidGlassColors.WarmPeachGlow,
    highlightColor: Color = Color.White,
    isPulsing: Boolean = false,
    specularIntensity: Float = 1.0f
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbPulse")
    val pulseScale by if (isPulsing) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "OrbScale"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    this
        .graphicsLayer {
            scaleX = pulseScale
            scaleY = pulseScale
        }
        .drawBehind {
            val radius = size.minDimension / 2f

            // 1. Deep Spherical Glass Radial Core
            drawCircle(
                brush = Brush.radialGradient(
                    0.00f to coreColor.copy(alpha = 0.95f),
                    0.45f to coreColor.copy(alpha = 0.80f),
                    0.80f to coreColor.copy(alpha = 0.50f),
                    1.00f to coreColor.copy(alpha = 0.25f),
                    center = Offset(size.width * 0.38f, size.height * 0.36f),
                    radius = radius * 1.15f
                ),
                radius = radius,
                center = center
            )

            // 2. Internal ambient glass shadow (dark rim)
            drawCircle(
                brush = Brush.radialGradient(
                    0.00f to Color.Transparent,
                    0.75f to Color(0x33000000),
                    1.00f to Color(0x77000000),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // 3. Top-left Caustic Specular Highlight Crescent
            val causticWidth = size.width * 0.50f
            val causticHeight = size.height * 0.28f
            drawOval(
                brush = Brush.radialGradient(
                    0.00f to highlightColor.copy(alpha = (0.90f * specularIntensity).coerceIn(0f, 1f)),
                    0.45f to highlightColor.copy(alpha = (0.50f * specularIntensity).coerceIn(0f, 1f)),
                    1.00f to Color.Transparent,
                    center = Offset(size.width * 0.35f, size.height * 0.26f),
                    radius = causticWidth * 0.65f
                ),
                topLeft = Offset(size.width * 0.16f, size.height * 0.12f),
                size = Size(causticWidth, causticHeight)
            )

            // 4. Secondary Bottom Ground Bounce Refraction
            val bounceWidth = size.width * 0.45f
            val bounceHeight = size.height * 0.18f
            drawOval(
                brush = Brush.radialGradient(
                    0.00f to highlightColor.copy(alpha = (0.45f * specularIntensity).coerceIn(0f, 1f)),
                    0.60f to highlightColor.copy(alpha = (0.15f * specularIntensity).coerceIn(0f, 1f)),
                    1.00f to Color.Transparent,
                    center = Offset(size.width * 0.50f, size.height * 0.85f),
                    radius = bounceWidth * 0.65f
                ),
                topLeft = Offset(size.width * 0.28f, size.height * 0.74f),
                size = Size(bounceWidth, bounceHeight)
            )

            // 5. Specular Dual-Tone Glass Rim Stroke
            drawCircle(
                brush = LiquidGlassColors.specularBorderBrush(
                    intensity = specularIntensity,
                    highlightColor = highlightColor,
                    bounceColor = coreColor.copy(alpha = 0.7f)
                ),
                radius = radius - 0.75.dp.toPx(),
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        .clip(CircleShape)
}

/**
 * Liquid Glass Card Composable.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    tintColor: Color = LiquidGlassColors.ObsidianElevated,
    frostedAlpha: Float = 0.65f,
    specularAlpha: Float = 0.85f,
    borderWidth: Dp = 1.dp,
    glowColor: Color = Color.Transparent,
    glowRadius: Dp = 0.dp,
    sheenIntensity: Float = 0.65f,
    highlightColor: Color = Color.White,
    bounceColor: Color = Color.White,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.liquidGlassSurface(
            shape = shape,
            tintColor = tintColor,
            frostedAlpha = frostedAlpha,
            specularAlpha = specularAlpha,
            borderWidth = borderWidth,
            glowColor = glowColor,
            glowRadius = glowRadius,
            sheenIntensity = sheenIntensity,
            highlightColor = highlightColor,
            bounceColor = bounceColor
        ),
        content = content
    )
}

/**
 * Liquid Glass Pill / Badge for status indicators and toggles.
 */
@Composable
fun GlassStatusBadge(
    text: String,
    stateColor: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
    label: String? = null
) {
    Row(
        modifier = modifier
            .liquidGlassCapsule(
                tintColor = LiquidGlassColors.ObsidianElevated,
                frostedAlpha = 0.80f,
                specularAlpha = 0.85f,
                borderWidth = 1.dp,
                glowColor = stateColor,
                glowRadius = if (pulsing) 8.dp else 2.dp,
                highlightColor = Color.White
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Glowing status dot
        Box(
            modifier = Modifier
                .size(7.dp)
                .liquidGlassGlow(
                    color = stateColor,
                    radius = 5.dp,
                    alpha = 0.9f,
                    pulsing = pulsing
                )
                .background(stateColor, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = stateColor,
                fontSize = 11.sp
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}
