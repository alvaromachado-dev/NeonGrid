package dev.alvaro.neongrid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Texto padrão do tema. Monoespaçado por padrão, com brilho ou sombra opcionais. */
@Composable
fun NeonText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalNeon.current.text,
    size: TextUnit = 12.sp,
    weight: FontWeight = FontWeight.Normal,
    spacing: TextUnit = 0.5.sp,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    family: FontFamily = Mono,
    glow: Boolean = false,
    shadow: Boolean = false,
) {
    val style = when {
        glow -> TextStyle(shadow = Shadow(color.copy(alpha = 0.85f), blurRadius = 18f))
        shadow -> TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.85f), offset = Offset(0f, 2f), blurRadius = 8f))
        else -> TextStyle.Default
    }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = weight,
        fontFamily = family,
        letterSpacing = spacing,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
        style = style,
    )
}

fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}

/** Superfície de "vidro" translúcido usada no dock, busca e painéis. */
fun Modifier.glass(neon: NeonColors, shape: Shape, alpha: Float = 1f): Modifier =
    this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.10f * alpha),
                    Color.White.copy(alpha = 0.04f * alpha),
                )
            )
        )
        .background(Color(0xFF0B0816).copy(alpha = 0.55f * alpha))
        .border(1.dp, neon.glassBorder, shape)

/**
 * Modo holograma: transforma o ícone colorido em tons da cor neon,
 * mantendo os detalhes (claro vira neon forte, escuro vira quase preto).
 */
fun hologram(c: Color): ColorFilter {
    // brilho mínimo de 22% (ícones escuros não somem) e teto em 92% (claros não estouram)
    val k = 0.70f
    val off = 0.22f * 255f
    fun row(ch: Float) = floatArrayOf(ch * 0.299f * k, ch * 0.587f * k, ch * 0.114f * k, 0f, ch * off)
    return ColorFilter.colorMatrix(
        ColorMatrix(
            row(c.red) + row(c.green) + row(c.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f)
        )
    )
}

@Composable
fun AppIconImage(app: AppEntry, neonIcons: Boolean, modifier: Modifier = Modifier) {
    val neon = LocalNeon.current
    if (neonIcons) {
        val filter = remember(neon.accent) { hologram(neon.accent) }
        Image(bitmap = app.icon, contentDescription = app.label, modifier = modifier, colorFilter = filter)
    } else {
        Image(bitmap = app.icon, contentDescription = app.label, modifier = modifier)
    }
}

/** Ícone de app sem moldura: só o ícone, com um halo neon que acende ao tocar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppEntry,
    neonIcons: Boolean,
    showLabel: Boolean,
    iconSize: Dp = 52.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val neon = LocalNeon.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, label = "tileScale")
    val glow by animateFloatAsState(if (pressed) 1f else 0f, label = "tileGlow")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(if (showLabel) iconSize + 26.dp else iconSize + 12.dp)
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
                .size(iconSize + 12.dp)
                .drawBehind {
                    // halo neon (aparece ao tocar) + sombra suave pra destacar do fundo
                    val r = size.minDimension / 2f
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                            center = center,
                            radius = r,
                        ),
                        radius = r,
                    )
                    if (glow > 0f) {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(neon.accent.copy(alpha = 0.55f * glow), Color.Transparent),
                                center = center,
                                radius = r * 1.1f,
                            ),
                            radius = r * 1.1f,
                        )
                    }
                },
        ) {
            AppIconImage(
                app,
                neonIcons,
                Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
        if (showLabel) {
            Spacer(Modifier.height(2.dp))
            NeonText(
                text = app.label,
                size = 11.sp,
                spacing = 0.sp,
                maxLines = 1,
                align = TextAlign.Center,
                family = Sans,
                color = if (pressed) neon.accent else neon.text.copy(alpha = 0.92f),
                shadow = true,
            )
        }
    }
}

/** Item de lista dos painéis (menu do app e configurações). */
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
            .clip(TileShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(ChipShape)
                .background(if (enabled) color else neon.dim)
        )
        Spacer(Modifier.width(14.dp))
        NeonText(
            label,
            color = if (enabled) color else neon.dim,
            size = 15.sp,
            weight = FontWeight.Medium,
            spacing = 0.2.sp,
            family = Sans,
        )
    }
}

@Composable
fun NeonChip(label: String, selected: Boolean, dot: Color? = null, onClick: () -> Unit) {
    val neon = LocalNeon.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(ChipShape)
            .background(if (selected) neon.accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (selected) neon.accent else neon.glassBorder, ChipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        if (dot != null) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(ChipShape)
                    .background(dot)
            )
            Spacer(Modifier.width(8.dp))
        }
        NeonText(
            label,
            color = if (selected) neon.accent else neon.text,
            size = 13.sp,
            weight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            spacing = 0.2.sp,
            family = Sans,
        )
    }
}

@Composable
fun ChipRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            .background(
                Brush.horizontalGradient(
                    listOf(neon.accent.copy(alpha = 0.7f), neon.alt.copy(alpha = 0.3f), Color.Transparent)
                )
            )
    )
}
