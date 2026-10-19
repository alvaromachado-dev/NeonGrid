package dev.alvaro.neongrid

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.max
import kotlin.random.Random

/**
 * Fundo cyberpunk: skyline com janelas acesas, horizonte em neon,
 * grid em perspectiva andando em direção à tela, scanlines e vinheta.
 * A parte estática é desenhada uma vez só em bitmap; por frame só o grid é redesenhado.
 */
@Composable
fun CyberBackground(animated: Boolean, modifier: Modifier = Modifier) {
    val neon = LocalNeon.current
    if (animated) {
        val transition = rememberInfiniteTransition(label = "grid")
        val phase = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Restart),
            label = "phase",
        )
        CyberLayer(neon, { phase.value }, modifier)
    } else {
        CyberLayer(neon, { 0.35f }, modifier)
    }
}

@Composable
private fun CyberLayer(neon: NeonColors, phase: () -> Float, modifier: Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val horizon = h * 0.64f

                val back = ImageBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
                CanvasDrawScope().draw(this, layoutDirection, Canvas(back), size) {
                    drawBackdrop(horizon, neon)
                }

                val scan = ImageBitmap(1, 6)
                Canvas(scan).drawRect(0f, 0f, 1f, 2f, Paint().apply { color = Color.Black.copy(alpha = 0.22f) })
                val scanBrush = ShaderBrush(ImageShader(scan, TileMode.Repeated, TileMode.Repeated))

                val vignette = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                    center = Offset(w / 2f, h * 0.45f),
                    radius = max(w, h) * 0.8f,
                )

                onDrawBehind {
                    drawImage(back)
                    drawGrid(horizon, phase(), neon)
                    drawRect(scanBrush)
                    drawRect(vignette)
                }
            }
    )
}

private fun DrawScope.drawBackdrop(horizon: Float, neon: NeonColors) {
    val w = size.width
    val h = size.height

    // Céu
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFF04050A), Color(0xFF090616), Color(0xFF170A2E)),
            endY = horizon,
        )
    )
    // Chão
    drawRect(Color(0xFF05040C), topLeft = Offset(0f, horizon), size = Size(w, h - horizon))

    // Névoa colorida no horizonte
    val hazeTop = horizon - h * 0.24f
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, neon.alt.copy(alpha = 0.22f)),
            startY = hazeTop,
            endY = horizon,
        ),
        topLeft = Offset(0f, hazeTop),
        size = Size(w, horizon - hazeTop),
    )

    // Skyline (seed fixa: a cidade é sempre a mesma)
    val rnd = Random(1984)
    val unit = w / 120f
    var x = -w * 0.02f
    while (x < w) {
        val bw = w * (0.035f + rnd.nextFloat() * 0.07f)
        val bh = h * (0.03f + rnd.nextFloat() * rnd.nextFloat() * 0.24f)
        val top = horizon - bh
        val realW = bw - unit * 0.6f

        drawRect(Color(0xFF080612), topLeft = Offset(x, top), size = Size(realW, bh))
        drawLine(neon.accent.copy(alpha = 0.28f), Offset(x, top), Offset(x + realW, top), strokeWidth = 1.2f)

        if (rnd.nextFloat() < 0.25f) {
            val ax = x + realW * 0.5f
            val tipY = top - h * 0.022f
            drawLine(neon.accent.copy(alpha = 0.35f), Offset(ax, top), Offset(ax, tipY), strokeWidth = 1.5f)
            drawCircle(neon.alt, radius = unit * 0.5f, center = Offset(ax, tipY))
        }

        val step = unit * 2.2f
        var wy = top + step
        while (wy < horizon - step) {
            var wx = x + step * 0.7f
            while (wx < x + realW - step) {
                if (rnd.nextFloat() < 0.10f) {
                    val c = if (rnd.nextBoolean()) neon.accent else neon.alt
                    drawRect(c.copy(alpha = 0.55f), topLeft = Offset(wx, wy), size = Size(unit * 0.7f, unit * 1.1f))
                }
                wx += step
            }
            wy += step * 1.4f
        }
        x += bw
    }

    // Linha do horizonte com brilho
    drawLine(neon.alt.copy(alpha = 0.30f), Offset(0f, horizon), Offset(w, horizon), strokeWidth = 10f)
    drawLine(neon.alt, Offset(0f, horizon), Offset(w, horizon), strokeWidth = 2f)
}

private fun DrawScope.drawGrid(horizon: Float, phase: Float, neon: NeonColors) {
    val w = size.width
    val h = size.height
    val depth = h - horizon
    val cx = w / 2f

    for (i in -12..12) {
        val top = cx + i * w * 0.035f
        val bottom = cx + i * w * 0.32f
        drawLine(neon.accent.copy(alpha = 0.20f), Offset(top, horizon), Offset(bottom, h), strokeWidth = 1.5f)
    }

    val rows = 10
    for (k in 0 until rows) {
        val t = (k + phase) / rows
        val y = horizon + depth * t * t
        drawLine(
            neon.accent.copy(alpha = 0.04f + 0.30f * t),
            Offset(0f, y),
            Offset(w, y),
            strokeWidth = 1f + 1.5f * t,
        )
    }
}
