package dev.alvaro.neongrid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/*
 * Linha do tempo da animação de desbloqueio (p vai de 0 a 1 em ~1,2 s):
 *  0.00–0.18  "ACESSO LIBERADO" sendo digitado + linha neon crescendo no centro
 *  0.22–0.62  a linha se abre em duas e revela a home (monitor CRT ligando)
 *  0.25–0.80  glitch: a home treme e as bordas brilham
 *  0.30–1.00  elementos da home entram em sequência (ver [introOf])
 */

private fun clamp01(v: Float) = v.coerceIn(0f, 1f)
private fun easeOutCubic(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)

/** Progresso da entrada dos elementos da home (0 = escondidos, 1 = no lugar). */
fun introOf(p: Float) = clamp01((p - 0.30f) / 0.70f)

/** Intensidade do glitch na home (0 = parado). */
fun glitchOf(p: Float) = if (p >= 1f) 0f else 1f - clamp01((p - 0.25f) / 0.55f)

/** Deslocamento horizontal "tremido" do glitch, em px. */
fun glitchShift(p: Float, px: Float): Float {
    val g = glitchOf(p)
    if (g <= 0f) return 0f
    val step = (p * 40f).toInt()
    val r = sin(step * 91.7f) * 43758.547f
    val frac = r - kotlin.math.floor(r)
    return if (frac > 0.6f) (frac - 0.8f) * 30f * px * g else 0f
}

/** Faz um bloco da home entrar deslizando e aparecendo, na sua vez ([order]). */
fun Modifier.introStagger(intro: () -> Float, order: Int): Modifier = graphicsLayer {
    val t = clamp01((intro() - order * 0.11f) / 0.45f)
    val e = easeOutCubic(t)
    alpha = e
    translationY = (1f - e) * 36f * density
}

@Composable
fun UnlockOverlay(progress: () -> Float) {
    val neon = LocalNeon.current
    val measurer = rememberTextMeasurer()
    val title = "ACESSO LIBERADO"
    val sub = "ネオン東京 // SYS ONLINE"
    val titleStyle = TextStyle(color = neon.accent, fontFamily = Display, fontSize = 15.sp, letterSpacing = 5.sp)
    val subStyle = TextStyle(color = neon.dim, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 2.sp)

    Canvas(Modifier.fillMaxSize()) {
        val p = progress()
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val px = density

        val grow = easeOutCubic(clamp01(p / 0.18f))
        val open = easeOutCubic(clamp01((p - 0.22f) / 0.40f))
        val half = cy * open

        // Preto fora da "janela" que se abre
        if (half < cy) {
            drawRect(Color.Black, topLeft = Offset.Zero, size = Size(w, cy - half))
            drawRect(Color.Black, topLeft = Offset(0f, cy + half), size = Size(w, h - (cy + half)))
        }

        // Linhas neon nas bordas da janela
        val edge = 1f - clamp01((p - 0.55f) / 0.25f)
        if (edge > 0f) {
            val lw = w * grow
            val x0 = w / 2f - lw / 2f
            val x1 = w / 2f + lw / 2f
            listOf(cy - half, cy + half).forEach { y ->
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, neon.accent.copy(alpha = 0.35f * edge), Color.Transparent),
                        startY = y - 18f * px,
                        endY = y + 18f * px,
                    ),
                    topLeft = Offset(x0, y - 18f * px),
                    size = Size(lw, 36f * px),
                )
                drawLine(neon.accent.copy(alpha = edge), Offset(x0, y), Offset(x1, y), strokeWidth = 2.5f * px, cap = StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.8f * edge), Offset(w / 2f - lw * 0.3f, y), Offset(w / 2f + lw * 0.3f, y), strokeWidth = 1f * px)
            }
        }

        // Scanlines por cima da home enquanto abre
        val scan = 1f - clamp01((p - 0.35f) / 0.40f)
        if (scan > 0f && half > 0f) {
            var y = cy - half
            while (y < cy + half) {
                drawRect(Color.Black.copy(alpha = 0.28f * scan), topLeft = Offset(0f, y), size = Size(w, 1.2f * px))
                y += 4f * px
            }
        }

        // Texto digitado
        val textA = clamp01(p / 0.06f) * (1f - clamp01((p - 0.30f) / 0.12f))
        if (textA > 0f) {
            val n = (title.length * clamp01(p / 0.16f)).toInt().coerceIn(0, title.length)
            val cursor = if (((p * 30f).toInt() % 2) == 0) "_" else " "
            val shown = title.take(n) + if (n < title.length) cursor else ""
            val t = measurer.measure(shown, titleStyle.copy(color = neon.accent.copy(alpha = textA)))
            val full = measurer.measure(title, titleStyle)
            drawText(t, topLeft = Offset(w / 2f - full.size.width / 2f, cy - 38f * px - t.size.height))
            val s = measurer.measure(sub, subStyle.copy(color = neon.dim.copy(alpha = textA * clamp01((p - 0.10f) / 0.08f))))
            drawText(s, topLeft = Offset(w / 2f - s.size.width / 2f, cy + 22f * px))
        }
    }
}
