package dev.alvaro.neongrid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/** Um app sendo aberto: qual é e de onde (posição do ícone na janela, em px). */
class OpeningApp(val app: AppEntry, val from: android.graphics.Rect)

/**
 * Animação de abertura desenhada pelo próprio launcher (o One UI ignora a do sistema):
 * o ícone vira um cartão que cresce até ocupar a tela, com o ícone ampliado no centro
 * e brilho neon na cor do app. Quando termina, o app aparece por cima, sem animação.
 */
@Composable
fun LaunchOverlay(opening: OpeningApp, progress: () -> Float) {
    val neon = LocalNeon.current
    val glow = opening.app.glow ?: neon.accent
    Canvas(Modifier.fillMaxSize()) {
        val p = progress().coerceIn(0f, 1f)
        val f = opening.from
        val left = lerp(f.left.toFloat(), 0f, p)
        val top = lerp(f.top.toFloat(), 0f, p)
        val right = lerp(f.right.toFloat(), size.width, p)
        val bottom = lerp(f.bottom.toFloat(), size.height, p)
        val w = right - left
        val h = bottom - top
        val startRadius = f.width() * 0.24f
        val radius = lerp(startRadius, 30.dp.toPx(), p)
        val cr = CornerRadius(radius, radius)

        // cartão escuro que cresce a partir do ícone
        drawRoundRect(
            color = lerp(Color(0xFF0B0914), glow, 0.10f * (1f - p)),
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = cr,
        )
        // brilho da cor do app no centro do cartão
        val c = Offset(left + w / 2f, top + h / 2f)
        val gr = maxOf(w, h) * 0.6f
        drawRoundRect(
            brush = Brush.radialGradient(listOf(glow.copy(alpha = 0.35f), Color.Transparent), center = c, radius = gr),
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = cr,
        )
        // contorno neon que some conforme cresce
        drawRoundRect(
            color = glow.copy(alpha = 0.8f * (1f - p)),
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = cr,
            style = Stroke(width = 2.dp.toPx()),
        )
        // ícone no centro, crescendo um pouco (vira a "tela de abertura")
        val iconStart = f.width().toFloat()
        val iconSize = lerp(iconStart, iconStart * 1.45f, p)
        drawImage(
            image = opening.app.icon,
            dstOffset = IntOffset((c.x - iconSize / 2f).roundToInt(), (c.y - iconSize / 2f).roundToInt()),
            dstSize = IntSize(iconSize.roundToInt(), iconSize.roundToInt()),
        )
    }
}
