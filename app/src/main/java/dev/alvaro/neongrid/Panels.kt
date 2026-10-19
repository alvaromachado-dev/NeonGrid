package dev.alvaro.neongrid

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class MenuAction { HOME, DOCK, INFO, UNINSTALL }

/** Fundo escurecido + painel neon preso embaixo. Tocar fora fecha. */
@Composable
private fun BottomPanel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val neon = LocalNeon.current
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .noRippleClickable(onDismiss),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .shadow(18.dp, PanelShape, ambientColor = neon.accent, spotColor = neon.accent)
                .clip(PanelShape)
                .background(neon.panel)
                .border(1.dp, Brush.linearGradient(listOf(neon.accent, neon.alt)), PanelShape)
                .noRippleClickable { } // toques dentro do painel não fecham
                .padding(18.dp),
        ) {
            content()
        }
    }
}

@Composable
fun AppMenu(
    app: AppEntry,
    inHome: Boolean,
    inDock: Boolean,
    dockFull: Boolean,
    onAction: (MenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val neon = LocalNeon.current
    BottomPanel(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconImage(app, neonIcons = false, modifier = Modifier.size(42.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                NeonText("// ALVO SELECIONADO", color = neon.dim, size = 10.sp, spacing = 2.sp)
                NeonText(
                    app.label.uppercase(),
                    color = neon.text,
                    size = 16.sp,
                    weight = FontWeight.Bold,
                    spacing = 1.sp,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        NeonDivider()
        Spacer(Modifier.height(6.dp))

        MenuItem(if (inHome) "REMOVER DA HOME" else "FIXAR NA HOME") { onAction(MenuAction.HOME) }
        MenuItem(
            label = when {
                inDock -> "REMOVER DO DOCK"
                dockFull -> "DOCK CHEIO (MÁX $MAX_DOCK)"
                else -> "FIXAR NO DOCK"
            },
            enabled = inDock || !dockFull,
        ) { onAction(MenuAction.DOCK) }
        MenuItem("INFO DO APP") { onAction(MenuAction.INFO) }
        MenuItem("DESINSTALAR", color = neon.danger) { onAction(MenuAction.UNINSTALL) }
    }
}

@Composable
fun SettingsPanel(
    settings: LauncherSettings,
    onChange: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onClose: () -> Unit,
) {
    val neon = LocalNeon.current
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }

    BottomPanel(onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonText("> CONFIG", color = neon.accent, size = 16.sp, weight = FontWeight.Bold, spacing = 3.sp, glow = true)
            Spacer(Modifier.weight(1f))
            NeonText("[ X ]", color = neon.accent, size = 13.sp, weight = FontWeight.Bold, modifier = Modifier.noRippleClickable(onClose).padding(4.dp))
        }
        Spacer(Modifier.height(12.dp))
        NeonDivider()

        Section("COR NEON")
        ChipRow {
            Accent.entries.forEach { a ->
                NeonChip(a.label, selected = settings.accent == a) { onChange { it.copy(accent = a) } }
            }
        }

        Section("ÍCONES")
        ChipRow {
            NeonChip("ORIGINAL", selected = !settings.neonIcons) { onChange { it.copy(neonIcons = false) } }
            NeonChip("HOLOGRAMA", selected = settings.neonIcons) { onChange { it.copy(neonIcons = true) } }
        }

        Section("COLUNAS")
        ChipRow {
            NeonChip("4", selected = settings.columns == 4) { onChange { it.copy(columns = 4) } }
            NeonChip("5", selected = settings.columns == 5) { onChange { it.copy(columns = 5) } }
        }

        Section("FUNDO")
        ChipRow {
            NeonChip("ANIMADO", selected = settings.animatedBg) { onChange { it.copy(animatedBg = true) } }
            NeonChip("ESTÁTICO", selected = !settings.animatedBg) { onChange { it.copy(animatedBg = false) } }
        }

        Spacer(Modifier.height(16.dp))
        NeonDivider()
        Spacer(Modifier.height(4.dp))
        MenuItem("DEFINIR COMO LAUNCHER PADRÃO") {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        Spacer(Modifier.height(6.dp))
        NeonText("NEONGRID v$version // build by ÁLVARO", color = neon.dim, size = 10.sp, spacing = 1.sp)
    }
}

@Composable
private fun Section(title: String) {
    val neon = LocalNeon.current
    Spacer(Modifier.height(16.dp))
    NeonText("// $title", color = neon.dim, size = 10.sp, spacing = 2.sp)
    Spacer(Modifier.height(8.dp))
}
