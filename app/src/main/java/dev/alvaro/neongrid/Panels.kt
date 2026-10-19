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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class MenuAction { HOME, DOCK, INFO, UNINSTALL }

/** Fundo escurecido + painel de vidro preso embaixo. Tocar fora fecha. */
@Composable
private fun BottomPanel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val neon = LocalNeon.current
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .noRippleClickable(onDismiss),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(12.dp)
                .fillMaxWidth()
                .clip(PanelShape)
                .background(Color(0xF2120E1F))
                .border(1.dp, neon.glassBorder, PanelShape)
                .noRippleClickable { } // toques dentro do painel não fecham
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(ChipShape)
                        .background(Brush.horizontalGradient(listOf(neon.accent, neon.alt)))
                )
            }
            Spacer(Modifier.height(16.dp))
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
            AppIconImage(app, neonIcons = false, modifier = Modifier.size(46.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                NeonText(app.label, color = neon.text, size = 18.sp, family = Sans, weight = FontWeight.SemiBold, maxLines = 1)
                NeonText(app.packageName, color = neon.dim, size = 11.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(14.dp))
        NeonDivider()
        Spacer(Modifier.height(4.dp))

        MenuItem(if (inHome) "Remover da tela inicial" else "Fixar na tela inicial") { onAction(MenuAction.HOME) }
        MenuItem(
            label = when {
                inDock -> "Remover do dock"
                dockFull -> "Dock cheio (máx. $MAX_DOCK)"
                else -> "Fixar no dock"
            },
            enabled = inDock || !dockFull,
        ) { onAction(MenuAction.DOCK) }
        MenuItem("Informações do app") { onAction(MenuAction.INFO) }
        MenuItem("Desinstalar", color = neon.danger) { onAction(MenuAction.UNINSTALL) }
    }
}

@Composable
fun SettingsPanel(
    settings: LauncherSettings,
    isDefaultHome: Boolean,
    onChange: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onRequestDefault: () -> Unit,
    onClose: () -> Unit,
) {
    val neon = LocalNeon.current
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }

    BottomPanel(onClose) {
        NeonText("Personalizar", color = neon.text, size = 22.sp, family = Sans, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        NeonText("ネオン東京 · NeonGrid v$version", color = neon.dim, size = 11.sp)

        Section("Cor do neon")
        ChipRow {
            Accent.entries.forEach { a ->
                NeonChip(a.label.lowercase().replaceFirstChar { it.uppercase() }, selected = settings.accent == a, dot = a.main) {
                    onChange { it.copy(accent = a) }
                }
            }
        }

        Section("Ícones")
        ChipRow {
            NeonChip("Originais", selected = !settings.neonIcons) { onChange { it.copy(neonIcons = false) } }
            NeonChip("Holograma", selected = settings.neonIcons) { onChange { it.copy(neonIcons = true) } }
        }

        Section("Colunas")
        ChipRow {
            NeonChip("4", selected = settings.columns == 4) { onChange { it.copy(columns = 4) } }
            NeonChip("5", selected = settings.columns == 5) { onChange { it.copy(columns = 5) } }
        }

        Section("Cidade")
        ChipRow {
            NeonChip("Chuva animada", selected = settings.animatedBg) { onChange { it.copy(animatedBg = true) } }
            NeonChip("Estática", selected = !settings.animatedBg) { onChange { it.copy(animatedBg = false) } }
        }

        Section("Barra de status")
        ChipRow {
            NeonChip("Oculta", selected = settings.hideStatusBar) { onChange { it.copy(hideStatusBar = true) } }
            NeonChip("Visível", selected = !settings.hideStatusBar) { onChange { it.copy(hideStatusBar = false) } }
        }

        Spacer(Modifier.height(18.dp))
        NeonDivider()
        Spacer(Modifier.height(4.dp))
        MenuItem(
            label = if (isDefaultHome) "Trocar launcher padrão" else "Definir como launcher padrão",
            color = if (isDefaultHome) neon.text else neon.accent,
        ) {
            if (isDefaultHome) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } else {
                onRequestDefault()
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    val neon = LocalNeon.current
    Spacer(Modifier.height(18.dp))
    NeonText(title.uppercase(), color = neon.dim, size = 11.sp, family = Sans, weight = FontWeight.Medium, spacing = 1.5.sp)
    Spacer(Modifier.height(10.dp))
}
