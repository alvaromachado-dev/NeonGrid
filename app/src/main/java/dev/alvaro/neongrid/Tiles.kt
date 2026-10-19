package dev.alvaro.neongrid

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Callbacks de arrastar de um ícone. Posições em pixels da janela. */
interface TileDrag {
    fun start(window: Offset)
    fun move(window: Offset)
    fun end()
    fun cancel()
}

/**
 * Gestos de um ícone:
 * - toque → [onTap]
 * - segurar → [onLongPress] (abre o menu)
 * - segurar e arrastar → fecha o menu e arrasta (se [drag] não for null)
 * Um deslize rápido (sem segurar) passa pro fundo da home (abrir gaveta etc.).
 */
private fun Modifier.tileGestures(
    pressed: (Boolean) -> Unit,
    coords: () -> LayoutCoordinates?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    drag: () -> TileDrag?,
    dragEnabled: Boolean,
): Modifier = pointerInput(dragEnabled) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        pressed(true)
        var dragging = false
        var ended = false
        try {
            val long = awaitLongPressOrCancellation(down.id)
            if (long == null) {
                pressed(false)
                val up = currentEvent.changes.firstOrNull { it.id == down.id }
                if (up != null && !up.pressed && !up.isConsumed) {
                    up.consume()
                    onTap()
                }
                ended = true
                return@awaitEachGesture
            }
            long.consume()
            onLongPress()
            val d = if (dragEnabled) drag() else null
            val start = long.position
            val slop = viewConfiguration.touchSlop
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) {
                    ch.consume()
                    break
                }
                if (d != null) {
                    if (!dragging && (ch.position - start).getDistance() > slop) {
                        dragging = true
                        pressed(false)
                        coords()?.let { d.start(it.localToWindow(ch.position)) }
                    }
                    if (dragging) coords()?.let { d.move(it.localToWindow(ch.position)) }
                }
                ch.consume()
            }
            pressed(false)
            if (dragging) d?.end()
            ended = true
        } finally {
            if (!ended) {
                pressed(false)
                if (dragging) drag()?.cancel()
            }
        }
    }
}

/**
 * Base de qualquer ícone da home (app ou pasta): halo neon, rótulo, gestos e
 * registro da posição pra animação de abertura.
 */
@Composable
private fun LauncherTile(
    label: String,
    glowColor: Color,
    haloImage: ImageBitmap?,
    showHalo: Boolean,
    showLabel: Boolean,
    iconSize: Dp,
    ghost: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    drag: TileDrag?,
    modifier: Modifier = Modifier,
    icon: @Composable (Modifier) -> Unit,
) {
    val neon = LocalNeon.current
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "tileScale")
    val boost by animateFloatAsState(if (pressed) 1f else 0f, label = "tileGlow")
    val realBlur = Build.VERSION.SDK_INT >= 31
    val glowTint = remember(glowColor) { ColorFilter.tint(glowColor) }

    val bounds = remember { arrayOfNulls<android.graphics.Rect>(1) }
    val coordsRef = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val inset = with(LocalDensity.current) { 7.dp.roundToPx() }

    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLongPress)
    val dragState by rememberUpdatedState(drag)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(if (showLabel) iconSize + 26.dp else iconSize + 12.dp)
            .graphicsLayer { alpha = if (ghost) 0.25f else 1f }
            .onGloballyPositioned { coordsRef[0] = it }
            .tileGestures(
                pressed = { pressed = it },
                coords = { coordsRef[0] },
                onTap = {
                    LaunchSource.set(bounds[0])
                    tap()
                },
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    long()
                },
                drag = { dragState },
                dragEnabled = drag != null,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(iconSize + 14.dp)
                .onGloballyPositioned { c ->
                    val b = c.boundsInWindow()
                    bounds[0] = android.graphics.Rect(
                        b.left.toInt() + inset,
                        b.top.toInt() + inset,
                        b.right.toInt() - inset,
                        b.bottom.toInt() - inset,
                    )
                }
                .drawBehind {
                    val r = size.minDimension / 2f
                    if (!showHalo) {
                        drawCircle(
                            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), center = center, radius = r),
                            radius = r,
                        )
                        if (boost > 0f) {
                            drawCircle(
                                Brush.radialGradient(listOf(neon.accent.copy(alpha = 0.5f * boost), Color.Transparent), center = center, radius = r * 1.1f),
                                radius = r * 1.1f,
                            )
                        }
                    } else if (!realBlur || haloImage == null) {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(glowColor.copy(alpha = 0.45f + 0.35f * boost), Color.Transparent),
                                center = center,
                                radius = r * 1.15f,
                            ),
                            radius = r * 1.15f,
                        )
                    }
                },
        ) {
            if (showHalo && realBlur && haloImage != null) {
                // O blur só se espalha dentro da camada: cada camada ganha folga em volta.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .requiredSize(iconSize + 44.dp)
                        .graphicsLayer {
                            val s = scale * (1f + 0.08f * boost)
                            scaleX = s
                            scaleY = s
                            alpha = 0.75f + 0.25f * boost
                        }
                        .blur(12.dp, BlurredEdgeTreatment.Unbounded),
                ) {
                    Image(bitmap = haloImage, contentDescription = null, colorFilter = glowTint, modifier = Modifier.size(iconSize))
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .requiredSize(iconSize + 20.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            alpha = 0.95f
                        }
                        .blur(3.dp, BlurredEdgeTreatment.Unbounded),
                ) {
                    Image(bitmap = haloImage, contentDescription = null, colorFilter = glowTint, modifier = Modifier.size(iconSize + 3.dp))
                }
            }
            icon(
                Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
        if (showLabel) {
            Spacer(Modifier.height(2.dp))
            NeonText(
                text = label,
                size = 11.sp,
                spacing = 0.sp,
                maxLines = 1,
                align = TextAlign.Center,
                family = Sans,
                color = if (pressed) glowColor else neon.text.copy(alpha = 0.92f),
                shadow = true,
            )
        }
    }
}

/** Ícone de app sem moldura, com halo neon na cor do próprio app. */
@Composable
fun AppTile(
    app: AppEntry,
    iconStyle: IconStyle,
    showLabel: Boolean,
    iconSize: Dp = 52.dp,
    modifier: Modifier = Modifier,
    ghost: Boolean = false,
    drag: TileDrag? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val neon = LocalNeon.current
    val glowColor = if (iconStyle == IconStyle.HOLOGRAM) neon.accent else (app.glow ?: neon.accent)
    LauncherTile(
        label = app.label,
        glowColor = glowColor,
        haloImage = app.icon,
        showHalo = iconStyle != IconStyle.ORIGINAL,
        showLabel = showLabel,
        iconSize = iconSize,
        ghost = ghost,
        onTap = onClick,
        onLongPress = onLongClick,
        drag = drag,
        modifier = modifier,
    ) { m -> AppIconImage(app, iconStyle, m) }
}

/** Ícone de pasta: vidro com até 4 mini ícones. */
@Composable
fun FolderTile(
    name: String,
    apps: List<AppEntry>,
    iconStyle: IconStyle,
    iconSize: Dp = 52.dp,
    ghost: Boolean = false,
    drag: TileDrag? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val neon = LocalNeon.current
    LauncherTile(
        label = name,
        glowColor = neon.accent,
        haloImage = null,
        showHalo = iconStyle != IconStyle.ORIGINAL,
        showLabel = true,
        iconSize = iconSize,
        ghost = ghost,
        onTap = onClick,
        onLongPress = onLongClick,
        drag = drag,
    ) { m -> FolderIcon(apps, iconStyle, m) }
}

@Composable
fun FolderIcon(apps: List<AppEntry>, iconStyle: IconStyle, modifier: Modifier = Modifier) {
    val neon = LocalNeon.current
    val shape = RoundedCornerShape(28)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF15102A).copy(alpha = 0.85f))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f))))
            .border(1.dp, Brush.linearGradient(listOf(neon.accent.copy(alpha = 0.8f), neon.alt.copy(alpha = 0.5f))), shape)
            .padding(6.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            apps.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEach { a -> AppIconImage(a, iconStyle, Modifier.size(17.dp)) }
                    if (row.size == 1) Spacer(Modifier.size(17.dp))
                }
            }
        }
    }
}
