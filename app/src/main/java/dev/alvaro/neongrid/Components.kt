package dev.alvaro.neongrid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Texto monoespaçado padrão do tema, com brilho opcional. */
@Composable
fun NeonText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalNeon.current.text,
    size: TextUnit = 12.sp,
    weight: FontWeight = FontWeight.Normal,
    spacing: TextUnit = 1.sp,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    glow: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = weight,
        fontFamily = Mono,
        letterSpacing = spacing,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
        style = if (glow) TextStyle(shadow = Shadow(color.copy(alpha = 0.85f), blurRadius = 18f)) else TextStyle.Default,
    )
}

fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}

/** Converte o ícone colorido em "holograma" de uma cor só, preservando o brilho. */
fun duotone(c: Color): ColorFilter {
    val k = 1.35f
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                c.red * 0.299f * k, c.red * 0.587f * k, c.red * 0.114f * k, 0f, 0f,
                c.green * 0.299f * k, c.green * 0.587f * k, c.green * 0.114f * k, 0f, 0f,
                c.blue * 0.299f * k, c.blue * 0.587f * k, c.blue * 0.114f * k, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
}

@Composable
fun AppIconImage(app: AppEntry, neonIcons: Boolean, modifier: Modifier = Modifier) {
    val neon = LocalNeon.current
    when {
        neonIcons && app.mono != null -> Image(
            bitmap = app.mono,
            contentDescription = null,
            modifier = modifier,
            colorFilter = ColorFilter.tint(neon.accent),
        )
        neonIcons -> {
            val filter = remember(neon.accent) { duotone(neon.accent) }
            Image(bitmap = app.icon, contentDescription = null, modifier = modifier, colorFilter = filter)
        }
        else -> Image(bitmap = app.icon, contentDescription = null, modifier = modifier)
    }
}

/** Ícone de app: moldura chanfrada com borda neon e brilho ao tocar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppEntry,
    neonIcons: Boolean,
    showLabel: Boolean,
    tileSize: Dp = 56.dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val neon = LocalNeon.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "tileScale")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(tileSize + 20.dp)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(tileSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = if (pressed) 16.dp else 6.dp,
                    shape = TileShape,
                    ambientColor = neon.accent,
                    spotColor = neon.accent,
                )
                .clip(TileShape)
                .background(Brush.linearGradient(listOf(neon.panel, neon.panelHi)))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            neon.accent.copy(alpha = if (pressed) 1f else 0.75f),
                            neon.alt.copy(alpha = 0.45f),
                        )
                    ),
                    shape = TileShape,
                ),
        ) {
            AppIconImage(app, neonIcons, Modifier.size(tileSize * 0.66f))
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            NeonText(
                text = app.label.uppercase(),
                size = 9.sp,
                spacing = 0.5.sp,
                maxLines = 1,
                align = TextAlign.Center,
                color = if (pressed) neon.accent else neon.text.copy(alpha = 0.85f),
            )
        }
    }
}

/** Botão estilo terminal usado nos painéis. */
@Composable
fun MenuItem(
    label: String,
    color: Color = LocalNeon.current.text,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val neon = LocalNeon.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ChipShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 13.dp),
    ) {
        NeonText("▸", color = if (enabled) neon.accent else neon.dim, size = 13.sp)
        Spacer(Modifier.width(10.dp))
        NeonText(
            label,
            color = if (enabled) color else neon.dim,
            size = 13.sp,
            weight = FontWeight.Bold,
            spacing = 1.5.sp,
        )
    }
}

@Composable
fun NeonChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val neon = LocalNeon.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(ChipShape)
            .background(if (selected) neon.accent else neon.panelHi)
            .border(1.dp, if (selected) neon.accent else neon.dim.copy(alpha = 0.5f), ChipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        NeonText(
            label,
            color = if (selected) neon.bg else neon.text,
            size = 11.sp,
            weight = FontWeight.Bold,
            spacing = 1.sp,
        )
    }
}

@Composable
fun ChipRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) { content() }
}

/** Linha fina degradê usada como divisor. */
@Composable
fun NeonDivider(modifier: Modifier = Modifier, fraction: Float = 1f) {
    val neon = LocalNeon.current
    Box(
        modifier
            .fillMaxWidth(fraction)
            .height(1.dp)
            .background(Brush.horizontalGradient(listOf(neon.accent, neon.alt.copy(alpha = 0.4f), Color.Transparent)))
    )
}
