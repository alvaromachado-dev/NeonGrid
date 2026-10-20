package dev.alvaro.neongrid

import android.graphics.Paint as NativePaint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
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
 * Neo-Tóquio: cidade em três camadas de profundidade, letreiros neon que
 * piscam, chuva, gotas batendo no chão e o reflexo da cidade ondulando no asfalto molhado.
 *
 * Tudo o que é estático (céu + prédios + reflexo) é desenhado uma única vez em bitmap.
 * Por frame só entram: fatias do reflexo deslocadas, letreiros, chuva e respingos.
 */
@Composable
fun NeoTokyoBackground(animated: Boolean, skyHour: Float, weather: SceneWeather, modifier: Modifier = Modifier) {
    val neon = LocalNeon.current
    val time = remember { mutableFloatStateOf(17.3f) }
    val hourState = rememberUpdatedState(skyHour)
    val weatherState = rememberUpdatedState(weather)
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
                // ler a hora aqui faz a cidade ser redesenhada quando o céu muda
                val scene = Scene.build(this, size, neon, hourState.value, weatherState.value)
                onDrawBehind { scene.draw(this, time.floatValue, animatedState.value) }
            }
    )
}

/**
 * Hora usada pelo céu (0–24, com fração). Atualiza a cada 6 minutos; no modo
 * "sempre noite" fica fixa às 23h.
 */
@Composable
fun rememberSkyHour(followClock: Boolean): Float {
    var hour by remember { mutableFloatStateOf(currentSkyHour()) }
    LaunchedEffect(followClock) {
        if (!followClock) return@LaunchedEffect
        while (true) {
            hour = currentSkyHour()
            delay(60_000)
        }
    }
    return if (followClock) hour else 23f
}

fun currentSkyHour(): Float {
    val now = LocalTime.now()
    val h = now.hour + now.minute / 60f
    return (h * 10f).toInt() / 10f
}

/* ------------------------------ Dia e noite ------------------------------ */

/** Como o céu está numa certa hora. */
private class SkyLook(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val lights: Float, // quanto das janelas está aceso (0–1)
    val signs: Float,  // brilho dos letreiros
    val day: Float,    // 0 = noite, 1 = meio-dia
    val night: Float,  // estrelas e lua
    val sun: Float,    // sol synthwave no amanhecer/pôr do sol
)

private class SkyKey(val hour: Float, val look: SkyLook)

private fun key(h: Float, top: Long, mid: Long, bottom: Long, lights: Float, signs: Float, day: Float, night: Float, sun: Float) =
    SkyKey(h, SkyLook(Color(top), Color(mid), Color(bottom), lights, signs, day, night, sun))

private val SKY_KEYS = listOf(
    key(0f, 0xFF05030B, 0xFF110A24, 0xFF2B0F36, 0.80f, 1.00f, 0.0f, 1.0f, 0f),
    key(3f, 0xFF030208, 0xFF0A0716, 0xFF1C0A28, 0.40f, 0.85f, 0.0f, 1.0f, 0f),
    key(5.3f, 0xFF0A0A22, 0xFF24184A, 0xFF4A2050, 0.45f, 0.90f, 0.0f, 0.8f, 0f),
    key(6.4f, 0xFF1E2456, 0xFF6A3470, 0xFFFF7A5A, 0.30f, 0.60f, 0.3f, 0.2f, 1f),
    key(8f, 0xFF2F62A8, 0xFF6E9FD6, 0xFFE2CFC8, 0.12f, 0.35f, 0.8f, 0.0f, 0f),
    key(12.5f, 0xFF2A6CC2, 0xFF6EA8DF, 0xFFC8E2F4, 0.06f, 0.30f, 1.0f, 0.0f, 0f),
    key(16.8f, 0xFF2D5EA4, 0xFF8E9CC8, 0xFFF2BC8C, 0.12f, 0.40f, 0.75f, 0.0f, 0f),
    key(18.4f, 0xFF1A1846, 0xFF6A2C6C, 0xFFFF5A6A, 0.55f, 0.80f, 0.3f, 0.1f, 1f),
    key(19.6f, 0xFF0C0A26, 0xFF2C1446, 0xFF52204E, 0.85f, 1.00f, 0.0f, 0.7f, 0f),
    key(21f, 0xFF07040F, 0xFF140C2A, 0xFF331038, 0.95f, 1.00f, 0.0f, 1.0f, 0f),
    key(24f, 0xFF05030B, 0xFF110A24, 0xFF2B0F36, 0.80f, 1.00f, 0.0f, 1.0f, 0f),
)

private fun skyAt(hourIn: Float): SkyLook {
    val hour = ((hourIn % 24f) + 24f) % 24f
    val i = SKY_KEYS.indexOfLast { it.hour <= hour }.coerceIn(0, SKY_KEYS.size - 2)
    val a = SKY_KEYS[i]
    val b = SKY_KEYS[i + 1]
    val t = ((hour - a.hour) / (b.hour - a.hour)).coerceIn(0f, 1f)
    fun f(x: Float, y: Float) = x + (y - x) * t
    val la = a.look
    val lb = b.look
    return SkyLook(
        lerp(la.top, lb.top, t), lerp(la.mid, lb.mid, t), lerp(la.bottom, lb.bottom, t),
        f(la.lights, lb.lights), f(la.signs, lb.signs), f(la.day, lb.day), f(la.night, lb.night), f(la.sun, lb.sun),
    )
}

/** Altura do sol (0 = no horizonte, 1 = alto) no amanhecer/pôr do sol. */
private fun sunElevation(hour: Float): Float =
    (if (hour < 12f) (hour - 5.6f) / 1.8f else (19.4f - hour) / 1.8f).coerceIn(0f, 1f)

/**
 * Desenha a cidade num bitmap do tamanho da tela e aplica como papel de parede do sistema.
 * Assim, nos instantes em que o Android mostra o papel de parede (voltando pra home),
 * aparece a mesma cidade em vez do papel de parede antigo.
 */
suspend fun applyCityWallpaper(
    context: Context,
    neon: NeonColors,
    includeLock: Boolean,
    hour: Float,
    weather: SceneWeather = SceneWeather.ALWAYS_RAIN,
): Boolean =
    withContext(Dispatchers.Default) {
        runCatching {
            val wm = context.getSystemService(WindowManager::class.java)
            val (w, h) = if (Build.VERSION.SDK_INT >= 30) {
                val b = wm.maximumWindowMetrics.bounds
                b.width() to b.height()
            } else {
                @Suppress("DEPRECATION")
                val dm = android.util.DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
                dm.widthPixels to dm.heightPixels
            }
            val density = Density(context.resources.displayMetrics.density, 1f)
            val size = Size(w.toFloat(), h.toFloat())
            val scene = Scene.build(density, size, neon, hour, weather)
            val img = ImageBitmap(w, h)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), size) {
                scene.draw(this, 17.3f, false)
            }
            val flags = if (includeLock) {
                WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            } else {
                WallpaperManager.FLAG_SYSTEM
            }
            WallpaperManager.getInstance(context).setBitmap(img.asAndroidBitmap(), null, true, flags)
        }.isSuccess
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

private const val MIRROR_SCALE = 4

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
    val look: SkyLook,
    val weather: SceneWeather,
    val fog: Brush?,
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
            signs.forEach { drawSign(it, flicker(it.seed, t) * look.signs) }

            // 2. Asfalto molhado: reflexo fatiado com ondulação
            drawRect(Color(0xFF040309), topLeft = Offset(0f, horizon), size = Size(w, h - horizon))
            val mh = mirror.height
            val fullW = w.toInt()
            val rowsPerSlice = 2
            var y = 0
            while (y < mh) {
                val sh = min(rowsPerSlice, mh - y)
                val depth = y.toFloat() / mh
                val yFull = y * MIRROR_SCALE
                val amp = px * (0.4f + 3f * depth)
                val dx = sin(yFull * 0.035f + t * 2.2f) * amp + sin(yFull * 0.012f - t * 1.0f) * amp * 0.6f
                drawImage(
                    image = mirror,
                    srcOffset = IntOffset(0, y),
                    srcSize = IntSize(mirror.width, sh),
                    dstOffset = IntOffset(dx.roundToInt(), horizonI + yFull),
                    dstSize = IntSize(fullW, sh * MIRROR_SCALE),
                )
                y += sh
            }

            // Reflexo esticado dos letreiros (aquele borrão colorido de rua molhada)
            signs.forEach { s ->
                val a = flicker(s.seed, t) * look.signs
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
                    listOf(alt.copy(alpha = 0.22f * (1f - 0.6f * look.day)), Color.Transparent),
                    startY = horizon,
                    endY = horizon + h * 0.05f,
                ),
                topLeft = Offset(0f, horizon),
                size = Size(w, h * 0.05f),
            )

            // Neblina por cima da cidade
            fog?.let { drawRect(it) }

            // 3. Chuva (quantidade conforme o clima) ou neve
            val slant = 0.16f
            val drops = (rainX.size * weather.rain).toInt()
            if (weather.kind == WeatherKind.SNOW) {
                for (i in 0 until 120) {
                    val depth = rainAlpha[i] / 0.33f
                    val fy = ((rainPhase[i] * h + t * h * (0.03f + 0.05f * depth)) % h)
                    val fx = rainX[i] * w + sin(t * 0.7f + i) * 14f * px
                    drawCircle(Color.White.copy(alpha = 0.35f + 0.45f * depth), radius = px * (0.8f + 2f * depth), center = Offset(fx, fy))
                }
            }
            for (i in 0 until drops) {
                val len = rainLen[i]
                val travel = h + len
                val ry = ((rainPhase[i] * travel + t * rainSpeed[i]) % travel) - len
                val rx = rainX[i] * w * 1.25f - w * 0.12f + ry * slant
                val color = if (rainTint[i]) lerp(Color.White, accent, 0.55f) else Color.White
                drawLine(
                    color = color.copy(alpha = rainAlpha[i] * (1f - 0.4f * look.day)),
                    start = Offset(rx, ry),
                    end = Offset(rx + len * slant, ry + len),
                    strokeWidth = rainWidth[i],
                    cap = StrokeCap.Round,
                )
            }

            // Relâmpagos na tempestade
            if (weather.kind == WeatherKind.STORM && animated) {
                val cycle = 7f
                val n = floor(t / cycle)
                val at = 1.5f + hash(n * 3.7f) * 4f
                val dt = t - n * cycle - at
                val flash = when {
                    dt in 0f..0.08f -> 1f
                    dt in 0.16f..0.26f -> 0.6f
                    else -> 0f
                }
                if (flash > 0f) {
                    drawRect(Color(0xFFDDE6FF).copy(alpha = 0.38f * flash), size = Size(w, horizon))
                    drawRect(Color.White.copy(alpha = 0.10f * flash))
                }
            }

            // 4. Respingos no chão (só quando chove)
            if (animated && weather.rain > 0f) {
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
        fun build(density: Density, size: Size, neon: NeonColors, hour: Float, weather: SceneWeather): Scene {
            val base = skyAt(hour)
            // nuvens: céu acinzentado, menos estrelas/sol
            val overcast = when (weather.kind) {
                WeatherKind.CLEAR -> weather.clouds * 0.25f
                WeatherKind.FOG -> 0.85f
                WeatherKind.STORM -> 0.95f
                WeatherKind.RAIN, WeatherKind.SNOW -> 0.8f
                else -> weather.clouds * 0.75f
            }
            val gray = lerp(Color(0xFF1C1A28), Color(0xFF9AA4B2), base.day)
            val look = SkyLook(
                lerp(base.top, gray, overcast * 0.7f),
                lerp(base.mid, gray, overcast * 0.6f),
                lerp(base.bottom, gray, overcast * 0.5f),
                base.lights, base.signs, base.day,
                base.night * (1f - overcast * 0.9f),
                base.sun * (1f - overcast * 0.8f),
            )
            val day = look.day
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
                        0f to look.top,
                        0.55f to look.mid,
                        1f to look.bottom,
                        startY = 0f,
                        endY = horizon,
                    )
                )
                drawCelestial(look, hour, w, horizon, px)
                drawDaySun(look, hour, w, horizon, overcast)
                drawClouds(look, w, horizon, overcast)

                // Poluição luminosa colorida (some de dia)
                val glowK = 1f - 0.7f * day
                val c1 = Offset(w * 0.72f, horizon)
                drawCircle(
                    Brush.radialGradient(listOf(neon.alt.copy(alpha = 0.38f * glowK), Color.Transparent), center = c1, radius = w * 0.95f),
                    radius = w * 0.95f,
                    center = c1,
                )
                val c2 = Offset(w * 0.12f, horizon * 0.92f)
                drawCircle(
                    Brush.radialGradient(listOf(neon.accent.copy(alpha = 0.20f * glowK), Color.Transparent), center = c2, radius = w * 0.6f),
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
                    drawRect(lerp(Color(0xFF1C1535), Color(0xFF6E7A92), day * 0.8f), topLeft = Offset(b.x, b.top), size = Size(b.w, horizon - b.top))
                    windows(b, horizon, rnd, 0.10f, look.lights, 3.2f * px, Size(1.2f * px, 1.8f * px), 0.35f, windowColors)
                }

                // Névoa entre camadas
                val hazeTop = horizon - h * 0.22f
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, lerp(neon.alt.copy(alpha = 0.13f), Color(0xFFD8DEE8).copy(alpha = 0.30f), day)),
                        startY = hazeTop,
                        endY = horizon,
                    ),
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
                    drawRect(lerp(Color(0xFF0E0A1C), Color(0xFF2E3346), day * 0.8f), topLeft = Offset(b.x, b.top), size = Size(b.w, horizon - b.top))
                    drawLine(neon.accent.copy(alpha = 0.22f), Offset(b.x, b.top), Offset(b.x, horizon), strokeWidth = px)
                    drawLine(neon.alt.copy(alpha = 0.25f), Offset(b.x, b.top), Offset(b.x + b.w, b.top), strokeWidth = px)
                    windows(b, horizon, rnd, 0.17f, look.lights, 4.5f * px, Size(1.8f * px, 2.6f * px), 0.65f, windowColors)
                }

                // Torres próximas emoldurando a cena
                near += Building(-w * 0.02f, w * 0.17f, horizon - h * 0.19f)
                near += Building(w * 0.70f, w * 0.12f, horizon - h * 0.36f)
                near += Building(w * 0.80f, w * 0.24f, horizon - h * 0.50f)
                near.forEachIndexed { i, b ->
                    drawRect(lerp(Color(0xFF060410), Color(0xFF161822), day * 0.8f), topLeft = Offset(b.x, b.top), size = Size(b.w, horizon - b.top))
                    val rimX = if (i == 0) b.x + b.w else b.x
                    drawLine(neon.accent.copy(alpha = 0.35f), Offset(rimX, b.top), Offset(rimX, horizon), strokeWidth = 1.5f * px)
                    windows(b, horizon, rnd, 0.07f, look.lights, 6f * px, Size(2.4f * px, 3.4f * px), 0.7f, windowColors)
                }

                // Brilho da rua no pé dos prédios
                val glowTop = horizon - h * 0.05f
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, neon.alt.copy(alpha = 0.22f * (1f - 0.6f * day))), startY = glowTop, endY = horizon),
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
                val cy = b.top + h * 0.035f
                signs += Sign(Rect(cx - tw / 2f, cy - ts * 0.85f, cx + tw / 2f, cy + ts * 0.85f), warm, "ホテル", false, 0.77f, p)
            }

            /* ---------------- Reflexo espelhado ----------------
             * Feito em 1/4 da resolução: ao ser esticado na tela, a filtragem bilinear
             * borra o reflexo naturalmente, como em asfalto molhado. */
            val reflH = (hi - horizonI).coerceAtLeast(1)
            val srcH = min(reflH, horizonI)
            val mw = (wi / MIRROR_SCALE).coerceAtLeast(1)
            val mh = (reflH / MIRROR_SCALE).coerceAtLeast(1)
            val mirror = ImageBitmap(mw, mh)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(mirror), Size(mw.toFloat(), mh.toFloat())) {
                scale(1f, -1f, pivot = Offset(mw / 2f, mh / 2f)) {
                    drawImage(
                        image = sky,
                        srcOffset = IntOffset(0, horizonI - srcH),
                        srcSize = IntSize(wi, srcH),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(mw, mh * srcH / reflH),
                    )
                }
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.90f)),
                        startY = 0f,
                        endY = mh.toFloat(),
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
                look = look,
                weather = weather,
                fog = if (weather.kind == WeatherKind.FOG) {
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to lerp(Color(0xFF2A2638), Color(0xFFC9CED6), look.day).copy(alpha = 0.55f),
                        0.70f to lerp(Color(0xFF2A2638), Color(0xFFC9CED6), look.day).copy(alpha = 0.35f),
                        1f to Color.Transparent,
                    )
                } else null,
                rainX = rainX,
                rainPhase = rainPhase,
                rainSpeed = rainSpeed,
                rainLen = rainLen,
                rainAlpha = rainAlpha,
                rainWidth = rainWidth,
                rainTint = rainTint,
                topScrim = Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.6f - 0.22f * day), Color.Transparent),
                    startY = 0f,
                    endY = h * 0.38f,
                ),
                bottomScrim = Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                    startY = h * 0.70f,
                    endY = h,
                ),
                vignette = Brush.radialGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f - 0.30f * day)),
                    center = Offset(w / 2f, h * 0.5f),
                    radius = max(w, h) * 0.85f,
                ),
            )
        }

        /** Sol do dia: cruza o céu da manhã (esq.) até a tarde (dir.). */
        private fun DrawScope.drawDaySun(look: SkyLook, hour: Float, w: Float, horizon: Float, overcast: Float) {
            val a = look.day * (1f - overcast) * (1f - look.sun)
            if (a <= 0.02f) return
            val t = ((hour - 7f) / 11f).coerceIn(0f, 1f)
            val cx = w * (0.12f + 0.76f * t)
            val cy = horizon * (0.42f - 0.30f * sin(t * Math.PI.toFloat()))
            val r = w * 0.05f
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFFF6D8).copy(alpha = 0.55f * a), Color.Transparent), center = Offset(cx, cy), radius = r * 6f),
                radius = r * 6f,
                center = Offset(cx, cy),
            )
            drawCircle(Color(0xFFFFFBEA).copy(alpha = a), radius = r, center = Offset(cx, cy))
        }

        /** Nuvens macias (manchas com degradê), mais densas quanto mais nublado. */
        private fun DrawScope.drawClouds(look: SkyLook, w: Float, horizon: Float, overcast: Float) {
            if (overcast < 0.08f) return
            val cr = Random(31)
            val color = lerp(Color(0xFF2E2840), Color(0xFFF2F4F8), look.day)
            val count = (6 + overcast * 16).toInt()
            repeat(count) {
                val cx = cr.nextFloat() * w * 1.2f - w * 0.1f
                val cy = horizon * (0.08f + cr.nextFloat() * 0.55f)
                val rx = w * (0.18f + cr.nextFloat() * 0.25f)
                val ry = rx * (0.28f + cr.nextFloat() * 0.15f)
                val a = (0.18f + 0.35f * overcast) * (0.6f + cr.nextFloat() * 0.4f)
                drawOval(
                    Brush.radialGradient(
                        listOf(color.copy(alpha = a), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = rx,
                    ),
                    topLeft = Offset(cx - rx, cy - ry),
                    size = Size(rx * 2f, ry * 2f),
                )
            }
        }

        /** Estrelas e lua de noite; sol synthwave listrado no amanhecer e no pôr do sol. */
        private fun DrawScope.drawCelestial(look: SkyLook, hour: Float, w: Float, horizon: Float, px: Float) {
            if (look.night > 0.02f) {
                val sr = Random(7)
                repeat(110) {
                    val sx = sr.nextFloat() * w
                    val sy = sr.nextFloat() * horizon * 0.7f
                    val rad = px * (0.5f + sr.nextFloat() * 0.9f)
                    val a = look.night * (0.25f + sr.nextFloat() * 0.65f)
                    drawCircle(Color.White.copy(alpha = a), radius = rad, center = Offset(sx, sy))
                }
                // Lua crescente com halo
                val mc = Offset(w * 0.24f, horizon * 0.22f)
                val mr = w * 0.045f
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFE8E0FF).copy(alpha = 0.22f * look.night), Color.Transparent), center = mc, radius = mr * 4f),
                    radius = mr * 4f,
                    center = mc,
                )
                drawIntoCanvas { it.saveLayer(Rect(mc.x - mr * 2, mc.y - mr * 2, mc.x + mr * 2, mc.y + mr * 2), Paint()) }
                drawCircle(Color(0xFFF2ECFF).copy(alpha = look.night), radius = mr, center = mc)
                drawCircle(Color.Black, radius = mr * 0.9f, center = Offset(mc.x + mr * 0.45f, mc.y - mr * 0.2f), blendMode = BlendMode.Clear)
                drawIntoCanvas { it.restore() }
            }
            if (look.sun > 0.02f) {
                val r = w * 0.21f
                val cx = w * 0.44f
                val cy = horizon - r * (0.15f + 0.75f * sunElevation(hour))
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFFF6A7A).copy(alpha = 0.45f * look.sun), Color.Transparent), center = Offset(cx, cy), radius = r * 2.4f),
                    radius = r * 2.4f,
                    center = Offset(cx, cy),
                )
                drawIntoCanvas { it.saveLayer(Rect(cx - r, cy - r, cx + r, cy + r), Paint()) }
                drawCircle(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFE36A), Color(0xFFFF8A4C), Color(0xFFFF3D9A)),
                        startY = cy - r,
                        endY = cy + r,
                    ),
                    radius = r,
                    center = Offset(cx, cy),
                    alpha = look.sun,
                )
                // listras horizontais na metade de baixo (o clássico sol synthwave)
                var k = 0
                var sy = cy + r * 0.05f
                while (sy < cy + r) {
                    val gap = r * (0.025f + k * 0.018f)
                    drawRect(Color.Black, topLeft = Offset(cx - r, sy), size = Size(r * 2f, gap), blendMode = BlendMode.Clear)
                    sy += gap + r * 0.11f
                    k++
                }
                drawIntoCanvas { it.restore() }
            }
        }

        private fun DrawScope.windows(
            b: Building,
            bottom: Float,
            rnd: Random,
            prob: Float,
            lights: Float,
            cell: Float,
            win: Size,
            alpha: Float,
            colors: List<Color>,
        ) {
            var wy = b.top + cell
            while (wy < bottom - cell) {
                var wx = b.x + cell * 0.6f
                while (wx < b.x + b.w - cell * 0.6f) {
                    // sorteia sempre os 3 valores, mesmo se a janela ficar apagada
                    val r = rnd.nextFloat()
                    val c = colors[rnd.nextInt(colors.size)]
                    val a = alpha * (0.5f + rnd.nextFloat() * 0.5f)
                    if (r < prob * lights) {
                        drawRect(c.copy(alpha = a), topLeft = Offset(wx, wy), size = win)
                    }
                    wx += cell
                }
                wy += cell * 1.3f
            }
        }
    }
}
