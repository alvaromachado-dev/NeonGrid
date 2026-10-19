package dev.alvaro.neongrid

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

enum class Accent(val label: String, val main: Color, val alt: Color) {
    CIANO("CIANO", Color(0xFF00F0FF), Color(0xFFFF2BD6)),
    MAGENTA("MAGENTA", Color(0xFFFF2BD6), Color(0xFF00F0FF)),
    ACIDO("ÁCIDO", Color(0xFFC6FF00), Color(0xFFFF2BD6)),
    AMBAR("ÂMBAR", Color(0xFFFFB000), Color(0xFF00F0FF)),
}

@Immutable
data class NeonColors(
    val accent: Color,
    val alt: Color,
    val bg: Color = Color(0xFF05060A),
    val panel: Color = Color(0xFF0B0E1A),
    val panelHi: Color = Color(0xFF151A33),
    val text: Color = Color(0xFFE6F7FF),
    val dim: Color = Color(0xFF6E7C9E),
    val danger: Color = Color(0xFFFF3B5C),
)

val LocalNeon = staticCompositionLocalOf { NeonColors(Accent.CIANO.main, Accent.CIANO.alt) }

val Mono = FontFamily.Monospace

/** Cantos cortados em diagonal: a assinatura visual do tema. */
val TileShape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)
val PanelShape = CutCornerShape(topStart = 20.dp, bottomEnd = 20.dp)
val ChipShape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)

@Composable
fun NeonTheme(accent: Accent, content: @Composable () -> Unit) {
    val colors = NeonColors(accent.main, accent.alt)
    val scheme = darkColorScheme(
        primary = colors.accent,
        secondary = colors.alt,
        background = colors.bg,
        surface = colors.panel,
        onBackground = colors.text,
        onSurface = colors.text,
    )
    CompositionLocalProvider(LocalNeon provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
