package dev.alvaro.neongrid

import android.graphics.Paint as NativePaint
import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Neo-Tóquio à noite: cidade em três camadas de profundidade, letreiros neon que
 * piscam, chuva, gotas batendo no chão e o reflexo da cidade ondulando no asfalto molhado.
 *
 * Tudo o que é estático (céu + prédios + reflexo) é desenhado uma única vez em bitmap.
 * Por frame só entram: fatias do reflexo deslocadas, letreiros, chuva e respingos.
 */
@Composable
fun NeoTokyoBackground(animated: Boolean, modifier: Modifier = Modifier) {
    val neon = LocalNeon.current
    val time = remember { mutableFloatStateOf(17.3f) }
    // lido só na hora de desenhar, pra pausar/retomar sem reconstruir a cidade
    val animatedState = rememberUpdatedState(animated)

    if (animated) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    time.floatValue = (17.3 + ((now - start) / 1e9) % 3600.0).toFloat()
                }
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val scene = Scene.build(this, size, neon)
                onDrawBehind { scene.draw(this, time.floatValue, animatedState.value) }
            }
    )
}

/* ------------------------------------------------------------------------------------ */

private class Building(val x: Float, val w: Float, val top: Float)

private class Sign(
    val rect: Rect,
    val color: Color,
    val text: String,
    val vertical: Boolean,
    val seed: Float,
    val paint: NativePaint,
)

private fun frac(v: Float) = v - floor(v)
private fun hash(n: Float) = frac(sin(n * 12.9898f) * 43758.547f)

/** Brilho do letreiro: zumbido sutil + falhas rápidas de vez em quando, como neon de verdade. */
private fun flicker(seed: Float, t: Float): Float {
    val cycle = 6f + seed * 9f
    val local = (t + seed * 31f) % cycle
    if (local < 0.5f) {
        val r = hash(floor(local * 26f) + seed * 100f)
        return if (r < 0.55f) 0.2f + r * 0.6f else 1f
    }
    return 0.9f + 0.1f * sin(t * 8f + seed * 20f)
}

private class Scene(
    val w: Float,
    val h: Float,
    val horizonI: Int,
    val px: Float,
    val sky: ImageBitmap,
    val mirror: ImageBitmap,
    val signs: List<Sign>,
    val accent: Color,
    val alt: Color,
    val rainX: FloatArray,
    val rainPhase: FloatArray,
    val rainSpeed: FloatArray,
    val rainLen: FloatArray,
    val rainAlpha: FloatArray,
    val rainWidth: FloatArray,
    val rainTint: BooleanArray,
    val topScrim: Brush,
    val bottomScrim: Brush,
    val vignette: Brush,
) {
    private val horizon = horizonI.toFloat()

    fun draw(ds: DrawScope, t: Float, animated: Boolean) {
        with(ds) {
            // 1. Céu + cidade
            drawImage(sky)
            signs.forEach { drawSign(it, flicker(it.seed, t)) }

            // 2. Asfalto molhado: reflexo fatiado com ondulação
            drawRect(Color(0xFF040309), topLeft = Offset(0f, horizon), size = Size(w, h - horizon))
            val reflH = mirror.height
            val slice = max(3, (4f * px).toInt())
            var y = 0
            while (y < reflH) {
                val sh = min(slice, reflH - y)
                val depth = y.toFloat() / reflH
                val amp = px * (0.8f + 4.5f * depth)
                val dx = sin(y * 0.05f + t * 2.4f) * amp + sin(y * 0.017f - t * 1.1f) * amp * 0.7f
                drawImage(
                    image = mirror,
                    srcOffset = IntOffset(0, y),
                    srcSize = IntSize(mirror.width, sh),
                    dstOffset = IntOffset(dx.roundToInt(), horizonI + y),
                    dstSize = IntSize(mirror.width, sh),
                )
                y += sh
            }

            // Reflexo esticado dos letreiros (aquele borrão colorido de rua molhada)
            signs.forEach { s ->
                val a = flicker(s.seed, t)
                val start = horizon + (horizon - s.rect.bottom)
                val len = s.rect.height * 2.6f + h * 0.03f
                val wobble = sin(t * 1.7f + s.seed * 10f) * px * 2f
                drawRect(
                    Brush.verticalGradient(
                        listOf(s.color.copy(alpha = 0.30f * a), Color.Transparent),
                        startY = start,
                        endY = start + len,
                    ),
                    topLeft = Offset(s.rect.left + wobble, start),
                    size = Size(s.rect.width, len),
                )
            }

            // Luz da cidade vazando no chão logo abaixo do horizonte
            drawRect(
                Brush.verticalGradient(
                    listOf(alt.copy(alpha = 0.22f), Color.Transparent),
                    startY = horizon,
                    endY = horizon + h * 0.05f,
                ),
                topLeft = Offset(0f, horizon),
                size = Size(w, h * 0.05f),
            )

            // 3. Chuva
            val slant = 0.16f
            for (i in rainX.indices) {
                val len = rainLen[i]
                val travel = h + len
                val ry = ((rainPhase[i] * travel + t * rainSpeed[i]) % travel) - len
                val rx = rainX[i] * w * 1.25f - w * 0.12f + ry * slant
                val color = if (rainTint[i]) lerp(Color.White, accent, 0.55f) else Color.White
                drawLine(
                    color = color.copy(alpha = rainAlpha[i]),
                    start = Offset(rx, ry),
                    end = Offset(rx + len * slant, ry + len),
                    strokeWidth = rainWidth[i],
                    cap = StrokeCap.Round,
                )
            }

            // 4. Respingos no chão
            if (animated) {
                val reflHF = h - horizon
                for (k in 0 until 26) {
                    val period = 0.5f + hash(k * 3.1f) * 0.9f
                    val shifted = t + hash(k * 7.7f) * period
                    val cycleN = floor(shifted / period)
                    val p = (shifted - cycleN * period) / period
                    if (p > 0.35f) continue
                    val q = p / 0.35f
                    val depth = 0.06f + hash(cycleN + k * 13.3f) * 0.92f
                    val cx = hash(cycleN * 1.7f + k * 5.3f) * w
                    val cy = horizon + depth * reflHF
                    val r = px * (1.5f + 9f * q) * (0.4f + depth)
                    drawOval(
                        color = Color.White.copy(alpha = (1f - q) * 0.30f),
                        topLeft = Offset(cx - r * 1.3f, cy - r * 0.4f),
                        size = Size(r * 2.6f, r * 0.8f),
                        style = Stroke(width = px * 0.8f),
                    )
                }
            }

            // 5. Escurecimento pra UI ficar legível + vinheta
            drawRect(topScrim)
            drawRect(bottomScrim)
            drawRect(vignette)
        }
    }

    private fun DrawScope.drawSign(s: Sign, a: Float) {
        val cr = CornerRadius(3f * px, 3f * px)
        drawRoundRect(Color(0xFF07050D), topLeft = s.rect.topLeft, size = s.rect.size, cornerRadius = cr)
        drawRoundRect(s.color.copy(alpha = 0.10f * a), topLeft = s.rect.topLeft, size = s.rect.size, cornerRadius = cr)
        drawRoundRect(
            s.color.copy(alpha = 0.22f * a),
            topLeft = s.rect.topLeft,
            size = s.rect.size,
            cornerRadius = cr,
            style = Stroke(width = 4f * px),
        )
        drawRoundRect(
            s.color.copy(alpha = 0.95f * a),
            topLeft = s.rect.topLeft,
            size = s.rect.size,
            cornerRadius = cr,
            style = Stroke(width = 1f * px),
        )
        val p = s.paint
        val ts = p.textSize
        p.alpha = (255 * a).toInt().coerceIn(0, 255)
        p.setShadowLayer(ts * 0.6f, 0f, 0f, s.color.copy(alpha = a).toArgb())
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            if (s.vertical) {
                s.text.forEachIndexed { i, ch ->
                    nc.drawText(ch.toString(), s.rect.center.x, s.rect.top + ts * 0.25f + ts * 1.2f * (i + 1) - ts * 0.15f, p)
                }
            } else {
                nc.drawText(s.text, s.rect.center.x, s.rect.center.y + ts * 0.36f, p)
            }
        }
    }

    companion object {
        fun build(density: Density, size: Size, neon: NeonColors): Scene {
            val px = with(density) { 1.dp.toPx() }
            val wi = size.width.toInt().coerceAtLeast(2)
            val hi = size.height.toInt().coerceAtLeast(2)
            val w = wi.toFloat()
            val h = hi.toFloat()
            val horizonI = (hi * 0.60f).toInt().coerceIn(1, hi - 1)
            val horizon = horizonI.toFloat()
            val warm = Color(0xFFFFB547)
            val rnd = Random(2049)

            val far = mutableListOf<Building>()
            val mid = mutableListOf<Building>()
            val near = mutableListOf<Building>()

            /* ---------------- Céu e cidade (bitmap estático) ---------------- */
            val sky = ImageBitmap(wi, horizonI)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(sky), Size(w, horizon)) {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0xFF05030B),
                        0.55f to Color(0xFF110A24),
                        1f to Color(0xFF2B0F36),
                        startY = 0f,
                        endY = horizon,
                    )
                )
                // Poluição luminosa colorida
                val c1 = Offset(w * 0.72f, horizon)
                drawCircle(
                    Brush.radialGradient(listOf(neon.alt.copy(alpha = 0.38f), Color.Transparent), center = c1, radius = w * 0.95f),
                    radius = w * 0.95f,
                    center = c1,
                )
                val c2 = Offset(w * 0.12f, horizon * 0.92f)
                drawCircle(
                    Brush.radialGradient(listOf(neon.accent.copy(alpha = 0.20f), Color.Transparent), center = c2, radius = w * 0.6f),
                    radius = w * 0.6f,
                    center = c2,
                )

                val windowColors = listOf(warm, warm, warm, neon.accent, neon.alt)

                // Camada distante
                var x = -w * 0.03f
                while (x < w) {
                    val bw = w * (0.045f + rnd.nextFloat() * 0.07f)
                    val bh = h * (0.07f + rnd.nextFloat() * 0.17f)
                    far += Building(x, bw, horizon - bh)
                    x += bw * (0.85f + rnd.nextFloat() * 0.25f)
                }
                far.forEach { b ->
                    drawRect(Color(0xFF1C1535), topLeft = Offset(b.x, b.top), size = Size(b.w, horizon - b.top))
                    windows(b, horizon, rnd, 0.10f, 3.2f * px, Size(1.2f * px, 1.8f * px), 0.35f, windowColors)
                }

                // Névoa entre camadas
                val hazeTop = horizon - h * 0.22f
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, neon.alt.copy(alpha = 0.13f)), startY = hazeTop, endY = horizon),
                    topLeft = Offset(0f, hazeTop),
                    size = Size(w, horizon - hazeTop),
                )

                // Camada do meio
                x = -w * 0.02f
                while (x < w) {
                    val bw = w * (0.07f + rnd.nextFloat() * 0.09f)
                    val bh = h * (0.10f + rnd.nextFloat() * rnd.nextFloat() * 0.26f)
                    mid += Building(x, bw, horizon - bh)
                    x += bw + w * rnd.nextFloat() * 0.03f
                }
                mid.forEach { b ->
                    drawRect(Color(0xFF0E0A1C), topLeft = Offset(b.x, b.top), size = Size(b.w, horizon - b.top))
                    drawLine(neon.accent.copy(alpha = 0.22f), Offset(b.x, b.top), Offset(b.x, horizon), strokeWidth = px)
                    drawLine(neon.alt.copy(alpha = 0.25f), Offset(b.x, b.top), Offset(b.x + b.w, b.top), strokeWidth = px)
                    windows(b, horizon, rnd, 0.17f, 4.5f * px, Size(1.8f * px, 2.6f * px), 0.65f, windowColors)
                }

                // Torres próximas emoldurando a cena
                near += Building(-w * 0.02f, w * 0.17f, horizon - h * 0.30f)
                near += Building(w * 0.70f, w * 0.12f, horizon - h * 0.36f)
                near += Building(w * 0.80f, w * 0.24f, horizon - h * 0.50f)
                near.forEachIndexed { i, b ->
                    drawRect(Color(0xFF060410), topLeft = Offset(b.x, b.top), size = Size(b.w, horizon - b.top))
                    val rimX = if (i == 0) b.x + b.w else b.x
                    drawLine(neon.accent.copy(alpha = 0.35f), Offset(rimX, b.top), Offset(rimX, horizon), strokeWidth = 1.5f * px)
                    windows(b, horizon, rnd, 0.07f, 6f * px, Size(2.4f * px, 3.4f * px), 0.7f, windowColors)
                }

                // Brilho da rua no pé dos prédios
                val glowTop = horizon - h * 0.05f
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, neon.alt.copy(alpha = 0.22f)), startY = glowTop, endY = horizon),
                    topLeft = Offset(0f, glowTop),
                    size = Size(w, horizon - glowTop),
                )
            }

            /* ---------------- Letreiros ---------------- */
            fun paint(color: Color, ts: Float) = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                this.color = color.toArgb()
                textSize = ts
                textAlign = NativePaint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }

            val signs = mutableListOf<Sign>()

            fun verticalSign(b: Building, text: String, color: Color, ts: Float, offsetY: Float, seed: Float) {
                val sw = ts * 1.7f
                val sh = ts * 0.5f + text.length * ts * 1.2f
                val left = b.x + b.w * 0.2f
                val top = (b.top + offsetY).coerceAtMost(horizon - sh - px)
                signs += Sign(Rect(left, top, left + sw, top + sh), color, text, true, seed, paint(color, ts))
            }

            verticalSign(near[2], "ネオン", neon.alt, 15f * px, h * 0.06f, 0.13f)
            verticalSign(near[1], "東京", neon.accent, 12f * px, h * 0.05f, 0.57f)

            val labels = listOf("ラーメン", "BAR", "24H")
            mid.filter { it.x > w * 0.06f && it.x + it.w < w * 0.70f && it.w > w * 0.08f }
                .sortedBy { it.top }
                .take(labels.size)
                .forEachIndexed { i, b ->
                    val color = listOf(neon.alt, neon.accent, warm)[i % 3]
                    val ts = 9f * px
                    val p = paint(color, ts)
                    val tw = p.measureText(labels[i]) + ts * 1.2f
                    val cy = b.top + (horizon - b.top) * 0.22f
                    val cx = b.x + b.w / 2f
                    signs += Sign(Rect(cx - tw / 2f, cy - ts * 0.85f, cx + tw / 2f, cy + ts * 0.85f), color, labels[i], false, 0.21f + i * 0.31f, p)
                }

            near[0].let { b ->
                val ts = 9f * px
                val p = paint(warm, ts)
                val tw = p.measureText("ホテル") + ts * 1.2f
                val cx = b.x + b.w * 0.55f
                val cy = b.top + h * 0.05f
                signs += Sign(Rect(cx - tw / 2f, cy - ts * 0.85f, cx + tw / 2f, cy + ts * 0.85f), warm, "ホテル", false, 0.77f, p)
            }

            /* ---------------- Reflexo espelhado ---------------- */
            val reflH = (hi - horizonI).coerceAtLeast(1)
            val mirror = ImageBitmap(wi, reflH)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(mirror), Size(w, reflH.toFloat())) {
                scale(1f, -1f, pivot = Offset(w / 2f, reflH / 2f)) {
                    drawImage(sky, topLeft = Offset(0f, reflH - horizon))
                }
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.92f)),
                        startY = 0f,
                        endY = reflH.toFloat(),
                    )
                )
            }

            /* ---------------- Chuva ---------------- */
            val rr = Random(99)
            val n = 160
            val depth = FloatArray(n) { rr.nextFloat() }
            val rainX = FloatArray(n) { rr.nextFloat() }
            val rainPhase = FloatArray(n) { rr.nextFloat() }
            val rainSpeed = FloatArray(n) { h * (1.0f + depth[it] * 1.4f) }
            val rainLen = FloatArray(n) { h * (0.015f + depth[it] * 0.04f) }
            val rainAlpha = FloatArray(n) { 0.05f + depth[it] * depth[it] * 0.28f }
            val rainWidth = FloatArray(n) { px * (0.5f + depth[it] * 1.1f) }
            val rainTint = BooleanArray(n) { rr.nextFloat() < 0.3f }

            return Scene(
                w = w,
                h = h,
                horizonI = horizonI,
                px = px,
                sky = sky,
                mirror = mirror,
                signs = signs,
                accent = neon.accent,
                alt = neon.alt,
                rainX = rainX,
                rainPhase = rainPhase,
                rainSpeed = rainSpeed,
                rainLen = rainLen,
                rainAlpha = rainAlpha,
                rainWidth = rainWidth,
                rainTint = rainTint,
                topScrim = Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                    startY = 0f,
                    endY = h * 0.38f,
                ),
                bottomScrim = Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                    startY = h * 0.70f,
                    endY = h,
                ),
                vignette = Brush.radialGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    center = Offset(w / 2f, h * 0.5f),
                    radius = max(w, h) * 0.85f,
                ),
            )
        }

        private fun DrawScope.windows(
            b: Building,
            bottom: Float,
            rnd: Random,
            prob: Float,
            cell: Float,
            win: Size,
            alpha: Float,
            colors: List<Color>,
        ) {
            var wy = b.top + cell
            while (wy < bottom - cell) {
                var wx = b.x + cell * 0.6f
                while (wx < b.x + b.w - cell * 0.6f) {
                    if (rnd.nextFloat() < prob) {
                        val c = colors[rnd.nextInt(colors.size)]
                        drawRect(c.copy(alpha = alpha * (0.5f + rnd.nextFloat() * 0.5f)), topLeft = Offset(wx, wy), size = win)
                    }
                    wx += cell
                }
                wy += cell * 1.3f
            }
        }
    }
}
