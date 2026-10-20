package dev.alvaro.neongrid

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import androidx.core.content.res.ResourcesCompat

/**
 * Versão "View" da animação de desbloqueio, desenhada numa janela por cima de
 * qualquer app (via serviço de acessibilidade). Mesma linha do tempo da versão
 * da home: texto digitado, linha neon, abertura estilo CRT, scanlines.
 */
@SuppressLint("ViewConstructor")
class UnlockOverlayView(
    context: Context,
    private val accent: Int,
    private val dim: Int,
    /** true = escaneamento transparente por cima de um app; false = capa preta (home). */
    private val scanMode: Boolean = false,
) : View(context) {

    private val duration = if (scanMode) SCAN_DURATION else DURATION

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

    private var startAt = 0L
    private var finished = false
    private var onEnd: (() -> Unit)? = null

    /**
     * Progresso calculado pelo relógio a cada quadro (não depende de animador do sistema,
     * que travava por cima de outros apps). Se algum quadro se perder, a animação segue.
     */
    fun play(onEnd: () -> Unit) {
        this.onEnd = onEnd
        startAt = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    fun finish() {
        if (finished) return
        finished = true
        onEnd?.invoke()
    }

    override fun onDraw(c: Canvas) {
        if (startAt != 0L) {
            p = ((SystemClock.uptimeMillis() - startAt) / duration.toFloat()).coerceIn(0f, 1f)
            if (p < 1f) postInvalidateOnAnimation() else post { finish() }
        }
        if (scanMode) {
            drawScan(c)
            return
        }
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

    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
    }

    /**
     * Escaneamento por cima do app (o app continua visível):
     * linha neon varrendo de cima a baixo com rastro e scanlines,
     * colchetes de HUD nos cantos e "ACESSO LIBERADO" no topo.
     */
    private fun drawScan(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sweep = easeInOut(clamp01(p / 0.75f))
        val y = -24f * d + (h + 48f * d) * sweep
        val fadeOut = 1f - clamp01((p - 0.75f) / 0.25f)

        // rastro: leve véu colorido + scanlines logo acima da linha
        val trail = h * 0.22f
        glowPaint.shader = LinearGradient(
            0f, y - trail, 0f, y,
            intArrayOf(0, withAlpha(accent, 0.16f * fadeOut)),
            null, Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, y - trail, w, y, glowPaint)
        scanPaint.color = withAlpha(0xFF000000.toInt(), 0.22f * fadeOut)
        var sy = (y - trail).coerceAtLeast(0f)
        while (sy < y) {
            c.drawRect(0f, sy, w, sy + 1.2f * d, scanPaint)
            sy += 4f * d
        }

        // a linha
        glowPaint.shader = LinearGradient(
            0f, y - 16f * d, 0f, y + 16f * d,
            intArrayOf(0, withAlpha(accent, 0.55f * fadeOut), 0),
            null, Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, y - 16f * d, w, y + 16f * d, glowPaint)
        linePaint.color = withAlpha(accent, fadeOut)
        linePaint.strokeWidth = 2.5f * d
        c.drawLine(0f, y, w, y, linePaint)
        corePaint.color = withAlpha(0xFFFFFFFF.toInt(), 0.85f * fadeOut)
        corePaint.strokeWidth = 1f * d
        c.drawLine(w * 0.15f, y, w * 0.85f, y, corePaint)

        // colchetes de HUD nos cantos (piscam no começo)
        val br = clamp01(p / 0.08f) * (1f - clamp01((p - 0.55f) / 0.30f))
        val blink = if (p < 0.2f && ((p * 40f).toInt() % 2 == 1)) 0.35f else 1f
        if (br > 0f) {
            bracketPaint.color = withAlpha(accent, br * blink)
            bracketPaint.strokeWidth = 3f * d
            bracketPaint.setShadowLayer(10f * d, 0f, 0f, withAlpha(accent, br))
            val m = 18f * d
            val len = 34f * d
            val top = 40f * d
            val bottom = h - 40f * d
            // sup. esq., sup. dir., inf. esq., inf. dir.
            c.drawLine(m, top, m + len, top, bracketPaint); c.drawLine(m, top, m, top + len, bracketPaint)
            c.drawLine(w - m, top, w - m - len, top, bracketPaint); c.drawLine(w - m, top, w - m, top + len, bracketPaint)
            c.drawLine(m, bottom, m + len, bottom, bracketPaint); c.drawLine(m, bottom, m, bottom - len, bracketPaint)
            c.drawLine(w - m, bottom, w - m - len, bottom, bracketPaint); c.drawLine(w - m, bottom, w - m, bottom - len, bracketPaint)
        }

        // texto no topo
        val ta = clamp01(p / 0.08f) * (1f - clamp01((p - 0.60f) / 0.25f))
        if (ta > 0f) {
            val n = (title.length * clamp01(p / 0.30f)).toInt().coerceIn(0, title.length)
            titlePaint.color = withAlpha(accent, ta)
            titlePaint.setShadowLayer(12f * d, 0f, 0f, withAlpha(accent, ta))
            c.drawText(title.take(n), w / 2f, 92f * d, titlePaint)
        }
    }

    private fun easeInOut(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f

    companion object {
        const val DURATION = 1250L
        const val SCAN_DURATION = 1000L
    }
}
