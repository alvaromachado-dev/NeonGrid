package dev.alvaro.neongrid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

/** O que está sendo arrastado. [fromIndex] = posição na lista exibida. */
class Dragged(
    val token: String,
    val fromDock: Boolean,
    val fromIndex: Int,
    val app: AppEntry?,
    val folderApps: List<AppEntry>?,
) {
    val isFolder get() = folderApps != null
}

/** Alvo em termos de posição na tela (índices da lista exibida). */
sealed interface DropSpot {
    data object None : DropSpot
    data object DockFull : DropSpot
    data class HomeInsert(val index: Int) : DropSpot
    data class HomeMerge(val index: Int) : DropSpot
    data class DockInsert(val index: Int) : DropSpot
}

/** O que desenhar enquanto arrasta: barra de inserção ou anel de "criar pasta". */
internal sealed interface Indicator {
    class Bar(val x: Float, val top: Float, val bottom: Float) : Indicator
    class Ring(val center: Offset, val radius: Float) : Indicator
}

/**
 * Estado do arrastar. As telas registram sua geometria (em px da janela) e o
 * controlador calcula onde o ícone cairia.
 */
class DragController {
    var dragged by mutableStateOf<Dragged?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)

    // geometria registrada pelas telas (não precisa ser state)
    var homeArea: Rect? = null
    var homeGrid: Rect? = null
    var homeCols: Int = 4
    var homeCount: Int = 0
    var dockRow: Rect? = null
    var dockCount: Int = 0
    var dockMax: Int = MAX_DOCK
    var slopPx: Float = 24f

    var onStart: () -> Unit = {}
    var onDrop: (Dragged, DropSpot) -> Unit = { _, _ -> }

    fun handleFor(make: () -> Dragged): TileDrag = object : TileDrag {
        override fun start(window: Offset) {
            pointer = window
            dragged = make()
            onStart()
        }
        override fun move(window: Offset) {
            pointer = window
        }
        override fun end() {
            val d = dragged ?: return
            val spot = resolve().first
            dragged = null
            onDrop(d, spot)
        }
        override fun cancel() {
            dragged = null
        }
    }

    internal fun resolve(): Pair<DropSpot, Indicator?> {
        val d = dragged ?: return DropSpot.None to null
        val p = pointer

        // Dock
        val dr = dockRow
        if (dr != null && dr.inflate(slopPx).contains(p)) {
            if (d.isFolder) return DropSpot.None to null
            if (!d.fromDock && dockCount >= dockMax) return DropSpot.DockFull to null
            val n = dockCount
            if (n == 0) return DropSpot.DockInsert(0) to Indicator.Bar(dr.center.x, dr.top, dr.bottom)
            val cw = dr.width / n
            val col = ((p.x - dr.left) / cw).toInt().coerceIn(0, n - 1)
            val cx = dr.left + (col + 0.5f) * cw
            val before = p.x < cx
            val idx = if (before) col else col + 1
            val x = if (before) dr.left + col * cw else dr.left + (col + 1) * cw
            return DropSpot.DockInsert(idx) to Indicator.Bar(x, dr.top, dr.bottom)
        }

        // Home
        val area = homeArea
        if (area != null && area.contains(p)) {
            val g = homeGrid
            val n = homeCount
            if (g == null || n == 0) return DropSpot.HomeInsert(n) to null
            val cols = homeCols
            val rows = ceil(n / cols.toFloat()).toInt().coerceAtLeast(1)
            val cw = g.width / cols
            val ch = g.height / rows
            if (p.y < g.top - ch * 0.3f) return endSpot(g, n, cols, cw, ch)
            val row = ((p.y - g.top) / ch).toInt().coerceIn(0, rows - 1)
            val col = ((p.x - g.left) / cw).toInt().coerceIn(0, cols - 1)
            val idx = row * cols + col
            if (idx >= n) return endSpot(g, n, cols, cw, ch)
            val center = Offset(g.left + (col + 0.5f) * cw, g.top + (row + 0.5f) * ch)
            val isOrigin = !d.fromDock && d.fromIndex == idx
            if (isOrigin) return DropSpot.None to null
            if (!d.isFolder && (p - center).getDistance() < cw * 0.30f) {
                return DropSpot.HomeMerge(idx) to Indicator.Ring(center, cw * 0.42f)
            }
            val before = p.x < center.x
            val x = if (before) g.left + col * cw else g.left + (col + 1) * cw
            val top = g.top + row * ch
            return DropSpot.HomeInsert(if (before) idx else idx + 1) to Indicator.Bar(x, top + ch * 0.1f, top + ch * 0.75f)
        }
        return DropSpot.None to null
    }

    private fun endSpot(g: Rect, n: Int, cols: Int, cw: Float, ch: Float): Pair<DropSpot, Indicator?> {
        val last = n - 1
        val row = last / cols
        val col = last % cols
        val x = g.left + (col + 1) * cw
        val top = g.top + row * ch
        return DropSpot.HomeInsert(n) to Indicator.Bar(x, top + ch * 0.1f, top + ch * 0.75f)
    }
}

/** Camada por cima de tudo: ícone flutuando no dedo + indicador de onde vai cair. */
@Composable
fun DragOverlay(controller: DragController, iconStyle: IconStyle) {
    val d = controller.dragged ?: return
    val neon = LocalNeon.current
    val sizePx = with(LocalDensity.current) { 60.dp.toPx() }
    val (_, indicator) = controller.resolve()

    Canvas(Modifier.fillMaxSize()) {
        when (indicator) {
            is Indicator.Bar -> {
                drawLine(neon.accent.copy(alpha = 0.35f), Offset(indicator.x, indicator.top), Offset(indicator.x, indicator.bottom), strokeWidth = 10f, cap = StrokeCap.Round)
                drawLine(neon.accent, Offset(indicator.x, indicator.top), Offset(indicator.x, indicator.bottom), strokeWidth = 4f, cap = StrokeCap.Round)
            }
            is Indicator.Ring -> {
                drawCircle(
                    Brush.radialGradient(listOf(neon.alt.copy(alpha = 0.35f), Color.Transparent), center = indicator.center, radius = indicator.radius * 1.3f),
                    radius = indicator.radius * 1.3f,
                    center = indicator.center,
                )
                drawCircle(neon.alt, radius = indicator.radius, center = indicator.center, style = Stroke(width = 3f))
            }
            null -> Unit
        }
    }

    val p = controller.pointer
    Box(
        Modifier
            .offset { IntOffset((p.x - sizePx / 2f).roundToInt(), (p.y - sizePx * 0.85f).roundToInt()) }
            .size(60.dp)
            .graphicsLayer {
                scaleX = 1.12f
                scaleY = 1.12f
            }
            .drawBehind {
                val glow = d.app?.glow ?: neon.accent
                drawCircle(
                    Brush.radialGradient(listOf(glow.copy(alpha = 0.55f), Color.Transparent), center = center, radius = size.minDimension * 0.8f),
                    radius = size.minDimension * 0.8f,
                )
            },
    ) {
        if (d.folderApps != null) {
            FolderIcon(d.folderApps, iconStyle, Modifier.fillMaxSize())
        } else if (d.app != null) {
            AppIconImage(d.app, iconStyle, Modifier.fillMaxSize())
        }
    }
}
