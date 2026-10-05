package com.vectorheart.asuna.ui.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vectorheart.asuna.ui.theme.LiquidGlassColors
import com.vectorheart.asuna.ui.theme.liquidGlassBorder
import com.vectorheart.asuna.ui.theme.liquidGlassCapsule
import com.vectorheart.asuna.ui.theme.liquidGlassGlow
import com.vectorheart.asuna.ui.theme.liquidGlassOrb
import com.vectorheart.asuna.ui.theme.liquidGlassSurface

/**
 * Chat message model shared across chat components and sheets.
 */
data class ChatMessage(
    val id: Long,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "" // "" / "sending" / "error" / "tool"
)

/**
 * Floating iOS 26 Liquid Glass Capsule Dock BottomBar.
 *
 * Encapsulates navigation and voice interaction inside a smooth glass capsule dock
 * with dual-tone Fresnel specular edge refraction, luminous active icons,
 * and a spherical liquid glass PTT microphone orb.
 */
@Composable
fun BottomBar(
    currentModel: String,
    onPttDown: () -> Unit,
    onPttUp: () -> Unit,
    onSettingsClick: () -> Unit,
    onModelClick: () -> Unit,
    onChatClick: () -> Unit,
    onLocalModelsClick: () -> Unit,
    isPickupMode: Boolean = false,
    onTogglePickupMode: () -> Unit = {},
    pttState: PttState = PttState.Idle,
    unreadCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating Liquid Glass Capsule Dock
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassSurface(
                    shape = RoundedCornerShape(36.dp),
                    tintColor = LiquidGlassColors.ObsidianElevated,
                    frostedAlpha = 0.74f,
                    specularAlpha = 0.95f,
                    borderWidth = 1.2.dp,
                    glowColor = Color(0x35000000),
                    glowRadius = 16.dp,
                    sheenIntensity = 0.75f,
                    highlightColor = Color.White,
                    bounceColor = LiquidGlassColors.WarmPeachBorder.copy(alpha = 0.35f)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassNavButton(
                    icon = Icons.Default.Chat,
                    onClick = onChatClick,
                    contentDesc = "История чата",
                    badgeCount = unreadCount
                )
                LiquidGlassNavButton(
                    icon = Icons.Default.SwapHoriz,
                    onClick = onModelClick,
                    contentDesc = "Сменить аватар"
                )
                // Centerpiece: Glowing Spherical Liquid Glass PTT Orb
                PttButton(
                    onDown = onPttDown,
                    onUp = onPttUp,
                    pttState = pttState
                )
                LiquidGlassNavButton(
                    icon = Icons.Default.Memory,
                    onClick = onLocalModelsClick,
                    contentDesc = "Локальные ИИ модели"
                )
                LiquidGlassNavButton(
                    icon = Icons.Default.Settings,
                    onClick = onSettingsClick,
                    contentDesc = "Настройки"
                )
            }
        }
    }
}

/**
 * Standard NavButton wrapper delegating to LiquidGlassNavButton.
 */
@Composable
fun NavButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDesc: String,
    size: Int = 42,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    badgeCount: Int = 0,
    isActive: Boolean = false
) {
    LiquidGlassNavButton(
        icon = icon,
        onClick = onClick,
        contentDesc = contentDesc,
        size = size,
        tint = tint,
        badgeCount = badgeCount,
        isActive = isActive
    )
}

/**
 * iOS 26 Liquid Glass Navigation Button with tactile bounce physics and glowing disc.
 */
@Composable
fun LiquidGlassNavButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDesc: String,
    size: Int = 44,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    badgeCount: Int = 0,
    isActive: Boolean = false
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "NavButtonScale"
    )

    Box(
        modifier = Modifier
            .size(size.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .liquidGlassSurface(
                shape = CircleShape,
                tintColor = if (isActive) LiquidGlassColors.WarmPeachTint else Color(0x22FFFFFF),
                frostedAlpha = if (isActive) 0.60f else 0.25f,
                specularAlpha = if (isActive) 1.0f else 0.70f,
                borderWidth = 1.dp,
                glowColor = if (isActive) LiquidGlassColors.WarmPeachGlow else Color.Transparent,
                glowRadius = if (isActive) 8.dp else 0.dp,
                sheenIntensity = 0.65f,
                highlightColor = if (isActive) Color.White else Color(0x99FFFFFF),
                bounceColor = if (isActive) LiquidGlassColors.WarmPeachBorder else Color(0x33FFFFFF)
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        val change = down.changes.firstOrNull()
                        if (change != null && change.pressed && !down.changes.any { it.isConsumed }) {
                            down.changes.forEach { it.consume() }
                            isPressed = true
                            onClick()
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.changes.firstOrNull()?.pressed == false) {
                                    ev.changes.forEach { it.consume() }
                                    isPressed = false
                                    break
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = if (isActive) LiquidGlassColors.WarmPeachGlow else tint,
            modifier = Modifier.size((size * 0.52f).dp)
        )
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(16.dp)
                    .liquidGlassGlow(LiquidGlassColors.WarmPeachGlow, radius = 4.dp, alpha = 0.9f)
                    .background(LiquidGlassColors.WarmPeachGlow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else "$badgeCount",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A0F1F)
                )
            }
        }
    }
}

private data class PttVisualConfig(
    val orbColor: Color,
    val glowColor: Color,
    val isPulsing: Boolean,
    val micGlyph: String
)

/**
 * Spherical Liquid Glass Push-to-Talk (PTT) Microphone Button.
 *
 * Implements physical caustic reflections, radial glass sphere core,
 * specular rim refraction, and state-driven luminous halos.
 */
@Composable
fun PttButton(
    onDown: () -> Unit,
    onUp: () -> Unit,
    pttState: PttState = PttState.Idle
) {
    var isPressed by remember { mutableStateOf(false) }

    val config = when (pttState) {
        PttState.Idle -> PttVisualConfig(
            LiquidGlassColors.WarmPeachGlow,
            LiquidGlassColors.WarmPeachGlow,
            false,
            "🎤"
        )
        PttState.Listening -> PttVisualConfig(
            LiquidGlassColors.CyberCyanGlow,
            LiquidGlassColors.CyberCyanGlow,
            true,
            "🎙"
        )
        PttState.Thinking -> PttVisualConfig(
            LiquidGlassColors.AsunaLavenderGlow,
            LiquidGlassColors.AsunaLavenderGlow,
            true,
            "💭"
        )
        PttState.Speaking -> PttVisualConfig(
            Color(0xFFFFB300),
            Color(0xFFFFB300),
            true,
            "🔊"
        )
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PttButtonScale"
    )

    Box(
        modifier = Modifier
            .size(68.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .liquidGlassGlow(
                color = config.glowColor,
                radius = if (config.isPulsing || isPressed) 20.dp else 10.dp,
                alpha = if (config.isPulsing || isPressed) 0.75f else 0.35f,
                pulsing = config.isPulsing
            )
            .liquidGlassOrb(
                coreColor = config.orbColor,
                isPulsing = config.isPulsing,
                specularIntensity = 1.0f
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        if (down.changes.firstOrNull()?.pressed == true) {
                            isPressed = true
                            onDown()
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.changes.firstOrNull()?.pressed == false) {
                                    isPressed = false
                                    onUp()
                                    break
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = config.micGlyph,
            fontSize = 28.sp,
            color = Color.White
        )
    }
}

private data class BubbleGlassStyle(
    val tintColor: Color,
    val glowColor: Color,
    val highlightBorder: Color,
    val badgeLabel: String,
    val badgeColor: Color
)

/**
 * Translucent Refractive Liquid Glass Message Bubble.
 *
 * Renders user and Asuna chat messages as physical glass cards with specular rim
 * highlights, internal glass sheen, and soft luminous glows.
 */
@Composable
fun ChatMessageBubble(msg: ChatMessage) {
    val isUser = msg.isUser
    val isError = msg.status == "error"
    val isTool = msg.status == "tool"

    val style = when {
        isError -> BubbleGlassStyle(
            LiquidGlassColors.RubyErrorTint,
            LiquidGlassColors.RubyErrorGlow,
            LiquidGlassColors.RubyErrorBorder,
            "⚠️ Внимание",
            Color(0xFFFF5252)
        )
        isTool -> BubbleGlassStyle(
            LiquidGlassColors.CyberCyanTint,
            LiquidGlassColors.CyberCyanGlow,
            LiquidGlassColors.CyberCyanBorder,
            "⚙️ Инструмент",
            Color(0xFF00E5FF)
        )
        isUser -> BubbleGlassStyle(
            LiquidGlassColors.WarmPeachTint,
            LiquidGlassColors.WarmPeachGlow,
            LiquidGlassColors.WarmPeachBorder,
            "👤 Ты",
            LiquidGlassColors.WarmPeachGlow
        )
        else -> BubbleGlassStyle(
            LiquidGlassColors.AsunaLavenderTint,
            LiquidGlassColors.AsunaLavenderGlow,
            LiquidGlassColors.AsunaLavenderBorder,
            "🤖 Асуна",
            LiquidGlassColors.AsunaLavenderGlow
        )
    }

    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .liquidGlassSurface(
                    shape = bubbleShape,
                    tintColor = style.tintColor,
                    frostedAlpha = 0.72f,
                    specularAlpha = 0.92f,
                    borderWidth = 1.1.dp,
                    glowColor = style.glowColor,
                    glowRadius = 6.dp,
                    sheenIntensity = 0.65f,
                    highlightColor = Color.White,
                    bounceColor = style.highlightBorder.copy(alpha = 0.45f)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Header: Glass Pill with glowing indicator dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(style.badgeColor, CircleShape)
                        .liquidGlassGlow(style.badgeColor, radius = 4.dp, alpha = 0.9f)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = style.badgeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = style.badgeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                if (msg.status == "sending") {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "• генерирует...",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 10.sp
                    )
                }
            }

            // Body text with razor-sharp contrast
            Text(
                text = msg.text,
                style = if (isTool) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        else MaterialTheme.typography.bodyMedium,
                color = Color(0xFFF9EFE7),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

/**
 * Backward-compatible chip toggle delegating to GlassChipToggle.
 */
@Composable
fun ChipToggle(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassChipToggle(
        selected = selected,
        label = label,
        onClick = onClick,
        modifier = modifier
    )
}

/**
 * iOS 26 Liquid Glass Chip Toggle with active glow state and specular rim.
 */
@Composable
fun GlassChipToggle(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .liquidGlassSurface(
                shape = RoundedCornerShape(12.dp),
                tintColor = if (selected) LiquidGlassColors.WarmPeachTint else LiquidGlassColors.ObsidianElevated,
                frostedAlpha = if (selected) 0.85f else 0.52f,
                specularAlpha = if (selected) 1.0f else 0.45f,
                borderWidth = if (selected) 1.2.dp else 0.8.dp,
                glowColor = if (selected) LiquidGlassColors.WarmPeachGlow else Color.Transparent,
                glowRadius = if (selected) 8.dp else 0.dp,
                sheenIntensity = if (selected) 0.75f else 0.35f,
                highlightColor = if (selected) Color.White else Color(0x66FFFFFF),
                bounceColor = if (selected) LiquidGlassColors.WarmPeachBorder else Color(0x33FFFFFF)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(LiquidGlassColors.WarmPeachGlow, CircleShape)
                        .liquidGlassGlow(LiquidGlassColors.WarmPeachGlow, radius = 4.dp, alpha = 0.9f)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = if (selected) Color(0xFFFFD4BD) else Color(0xFFE2D6CA),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * Liquid Glass Pill for status badges.
 */
@Composable
fun GlassStatusPill(
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
                specularAlpha = 0.88f,
                borderWidth = 1.dp,
                glowColor = stateColor,
                glowRadius = if (pulsing) 8.dp else 2.dp,
                highlightColor = Color.White,
                bounceColor = stateColor.copy(alpha = 0.4f)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
            color = Color(0xFFF5EBE0),
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}
