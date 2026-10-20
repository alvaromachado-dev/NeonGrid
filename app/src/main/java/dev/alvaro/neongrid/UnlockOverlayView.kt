package dev.alvaro.neongrid

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.res.ResourcesCompat

/**
 * Versão "View" da animação de desbloqueio, desenhada numa janela por cima de
 * qualquer app (via serviço de acessibilidade). Mesma linha do tempo da versão
 * da home: texto digitado, linha neon, abertura estilo CRT, scanlines.
 */
@SuppressLint("ViewConstructor")
class UnlockOverlayView(context: Context, private val accent: Int, private val dim: Int) : View(context) {

    private var p = 0f
    private val d = resources.displayMetrics.density
    private val title = "ACESSO LIBERADO"
    private val sub = "ネオン東京 // SYS ONLINE"

    private val black = Paint().apply { color = 0xFF000000.toInt() }
    private val scanPaint = Paint()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 15f * resources.displayMetrics.scaledDensity
        letterSpacing = 0.33f
        typeface = runCatching { ResourcesCompat.getFont(context, R.font.orbitron) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * resources.displayMetrics.scaledDensity
        letterSpacing = 0.18f
        typeface = runCatching { ResourcesCompat.getFont(context, R.font.share_tech_mono) }.getOrNull() ?: Typeface.MONOSPACE
    }

    private fun clamp01(v: Float) = v.coerceIn(0f, 1f)
    private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)
    private fun withAlpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((255 * clamp01(a)).toInt() shl 24)

    fun play(onEnd: () -> Unit) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1250
            interpolator = LinearInterpolator()
            addUpdateListener {
                p = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd()
            })
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cy = h / 2f
        val grow = easeOut(clamp01(p / 0.18f))
        val open = easeOut(clamp01((p - 0.22f) / 0.40f))
        val half = cy * open

        // preto fora da janela que se abre
        if (half < cy) {
            c.drawRect(0f, 0f, w, cy - half, black)
            c.drawRect(0f, cy + half, w, h, black)
        }

        // scanlines dentro da janela
        val scan = 1f - clamp01((p - 0.35f) / 0.40f)
        if (scan > 0f && half > 0f) {
            scanPaint.color = withAlpha(0xFF000000.toInt(), 0.28f * scan)
            var y = cy - half
            while (y < cy + half) {
                c.drawRect(0f, y, w, y + 1.2f * d, scanPaint)
                y += 4f * d
            }
        }

        // linhas neon nas bordas
        val edge = 1f - clamp01((p - 0.55f) / 0.25f)
        if (edge > 0f) {
            val lw = w * grow
            val x0 = w / 2f - lw / 2f
            val x1 = w / 2f + lw / 2f
            for (y in floatArrayOf(cy - half, cy + half)) {
                glowPaint.shader = LinearGradient(
                    0f, y - 18f * d, 0f, y + 18f * d,
                    intArrayOf(0, withAlpha(accent, 0.35f * edge), 0),
                    null, Shader.TileMode.CLAMP,
                )
                c.drawRect(x0, y - 18f * d, x1, y + 18f * d, glowPaint)
                linePaint.color = withAlpha(accent, edge)
                linePaint.strokeWidth = 2.5f * d
                c.drawLine(x0, y, x1, y, linePaint)
                corePaint.color = withAlpha(0xFFFFFFFF.toInt(), 0.8f * edge)
                corePaint.strokeWidth = 1f * d
                c.drawLine(w / 2f - lw * 0.3f, y, w / 2f + lw * 0.3f, y, corePaint)
            }
        }

        // texto digitado
        val textA = clamp01(p / 0.06f) * (1f - clamp01((p - 0.30f) / 0.12f))
        if (textA > 0f) {
            val n = (title.length * clamp01(p / 0.16f)).toInt().coerceIn(0, title.length)
            val cursor = if (((p * 30f).toInt() % 2) == 0) "_" else " "
            val shown = title.take(n) + if (n < title.length) cursor else ""
            titlePaint.color = withAlpha(accent, textA)
            titlePaint.setShadowLayer(12f * d, 0f, 0f, withAlpha(accent, textA))
            c.drawText(shown, w / 2f, cy - 38f * d, titlePaint)
            subPaint.color = withAlpha(dim, textA * clamp01((p - 0.10f) / 0.08f))
            c.drawText(sub, w / 2f, cy + 34f * d, subPaint)
        }
    }
}
