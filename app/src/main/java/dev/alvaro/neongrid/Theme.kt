package dev.alvaro.neongrid

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Paleta Neo-Tóquio. Os nomes internos (CIANO, MAGENTA, AMBAR, ACIDO) ficam iguais
 * pra não perder a cor que já estava salva no aparelho.
 */
enum class Accent(val label: String, val main: Color, val alt: Color) {
    CIANO("CIANO", Color(0xFF36F3FF), Color(0xFFFF3D9A)),
    MAGENTA("ROSA", Color(0xFFFF3D9A), Color(0xFF36F3FF)),
    AMBAR("ÂMBAR", Color(0xFFFFB547), Color(0xFFFF3D9A)),
    ACIDO("VIOLETA", Color(0xFFA48BFF), Color(0xFF36F3FF)),
}

@Immutable
data class NeonColors(
    val accent: Color,
    val alt: Color,
    val bg: Color = Color(0xFF06050C),
    val panel: Color = Color(0xFF120F1E),
    val panelHi: Color = Color(0xFF1C1830),
    val glass: Color = Color(0x1AFFFFFF),
    val glassBorder: Color = Color(0x26FFFFFF),
    val text: Color = Color(0xFFF2F0FF),
    val dim: Color = Color(0xFF8C87A8),
    val danger: Color = Color(0xFFFF4D6A),
)

val LocalNeon = staticCompositionLocalOf { NeonColors(Accent.CIANO.main, Accent.CIANO.alt) }

val Mono = FontFamily.Monospace
val Sans = FontFamily.SansSerif

val TileShape = RoundedCornerShape(18.dp)
val PanelShape = RoundedCornerShape(28.dp)
val ChipShape = RoundedCornerShape(50)

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
