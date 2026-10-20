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

enum class MenuAction { HOME, DOCK, INFO, UNINSTALL, OUT_OF_FOLDER }

enum class FolderAction { OPEN, RENAME, DISSOLVE, REMOVE }

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
    folderName: String? = null,
    onAction: (MenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val neon = LocalNeon.current
    BottomPanel(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconImage(app, IconStyle.ORIGINAL, modifier = Modifier.size(46.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                NeonText(app.label, color = neon.text, size = 18.sp, family = Sans, weight = FontWeight.SemiBold, maxLines = 1)
                NeonText(app.packageName, color = neon.dim, size = 11.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(14.dp))
        NeonDivider()
        Spacer(Modifier.height(4.dp))

        if (folderName != null) {
            MenuItem("Tirar da pasta \"$folderName\"") { onAction(MenuAction.OUT_OF_FOLDER) }
        }
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
fun FolderMenu(
    folder: Folder,
    apps: List<AppEntry>,
    iconStyle: IconStyle,
    onAction: (FolderAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val neon = LocalNeon.current
    BottomPanel(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FolderIcon(apps, iconStyle, Modifier.size(46.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                NeonText(folder.name, color = neon.text, size = 18.sp, family = Sans, weight = FontWeight.SemiBold, maxLines = 1)
                NeonText("${apps.size} apps", color = neon.dim, size = 11.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(14.dp))
        NeonDivider()
        Spacer(Modifier.height(4.dp))
        MenuItem("Abrir") { onAction(FolderAction.OPEN) }
        MenuItem("Renomear") { onAction(FolderAction.RENAME) }
        MenuItem("Desfazer pasta (apps voltam pra tela inicial)") { onAction(FolderAction.DISSOLVE) }
        MenuItem("Remover da tela inicial", color = neon.danger) { onAction(FolderAction.REMOVE) }
    }
}

@Composable
fun SettingsPanel(
    settings: LauncherSettings,
    isDefaultHome: Boolean,
    onChange: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onRequestDefault: () -> Unit,
    onApplyWallpaper: (WallpaperSync) -> Unit,
    musicEnabled: Boolean,
    musicDebug: String,
    weatherLabel: String?,
    onWeatherMode: (WeatherMode) -> Unit,
    lockEnabled: Boolean,
    onEnableMusic: () -> Unit,
    onEnableLock: () -> Unit,
    onClose: () -> Unit,
) {
    val neon = LocalNeon.current
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }

    BottomPanel(onClose) {
        NeonText("PERSONALIZAR", color = neon.text, size = 18.sp, family = Display, weight = FontWeight.SemiBold, spacing = 2.sp)
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
            NeonChip("Halo neon", selected = settings.iconStyle == IconStyle.NEON) { onChange { it.copy(iconStyle = IconStyle.NEON) } }
            NeonChip("Originais", selected = settings.iconStyle == IconStyle.ORIGINAL) { onChange { it.copy(iconStyle = IconStyle.ORIGINAL) } }
            NeonChip("Holograma", selected = settings.iconStyle == IconStyle.HOLOGRAM) { onChange { it.copy(iconStyle = IconStyle.HOLOGRAM) } }
        }

        Section("Colunas")
        ChipRow {
            NeonChip("4", selected = settings.columns == 4) { onChange { it.copy(columns = 4) } }
            NeonChip("5", selected = settings.columns == 5) { onChange { it.copy(columns = 5) } }
        }

        Section("Cidade")
        ChipRow {
            NeonChip("Animada", selected = settings.animatedBg) { onChange { it.copy(animatedBg = true) } }
            NeonChip("Estática", selected = !settings.animatedBg) { onChange { it.copy(animatedBg = false) } }
        }
        Spacer(Modifier.height(8.dp))
        ChipRow {
            NeonChip("Céu acompanha o horário", selected = settings.skyFollowsClock) { onChange { it.copy(skyFollowsClock = true) } }
            NeonChip("Sempre noite", selected = !settings.skyFollowsClock) { onChange { it.copy(skyFollowsClock = false) } }
        }

        Section("Clima")
        ChipRow {
            NeonChip("Real (sua cidade)", selected = settings.weatherMode == WeatherMode.REAL) { onWeatherMode(WeatherMode.REAL) }
            NeonChip("Sempre chuva", selected = settings.weatherMode == WeatherMode.RAIN) { onWeatherMode(WeatherMode.RAIN) }
            NeonChip("Sem chuva", selected = settings.weatherMode == WeatherMode.CLEAR) { onWeatherMode(WeatherMode.CLEAR) }
        }
        if (settings.weatherMode == WeatherMode.REAL) {
            Spacer(Modifier.height(6.dp))
            NeonText(
                weatherLabel?.let { "Agora: $it · atualiza a cada 30 min (Open-Meteo)" } ?: "Buscando o clima…",
                color = neon.dim,
                size = 12.sp,
                family = Sans,
            )
        }

        Section("Gestos")
        NeonText(
            "↑ apps · ↓ notificações · → câmera · ← buscar apps\nSegurar num espaço vazio: Personalizar\nToque duplo num espaço vazio: bloquear a tela",
            color = neon.dim,
            size = 12.sp,
            family = Sans,
        )
        Spacer(Modifier.height(4.dp))
        if (lockEnabled) {
            NeonText("✓ Bloqueio com toque duplo ativado", color = neon.accent, size = 12.sp, family = Sans)
        } else {
            MenuItem("Ativar bloqueio com toque duplo", color = neon.accent) { onEnableLock() }
        }

        Section("Música na tela inicial")
        if (musicEnabled) {
            NeonText("✓ Ativado. O card aparece quando algo estiver tocando.", color = neon.accent, size = 12.sp, family = Sans)
            if (musicDebug.isNotEmpty()) {
                NeonText("players: $musicDebug", color = neon.dim, size = 11.sp)
            }
        } else {
            NeonText(
                "Mostra capa, música e controles do Spotify, YouTube Music etc. Precisa do acesso às notificações (o NeonGrid só lê o player, nada mais).",
                color = neon.dim,
                size = 12.sp,
                family = Sans,
            )
            MenuItem("Ativar música na tela inicial", color = neon.accent) { onEnableMusic() }
        }

        Section("Abrir apps")
        ChipRow {
            NeonChip("Zoom neon", selected = settings.launchAnim == LaunchAnim.NEON) { onChange { it.copy(launchAnim = LaunchAnim.NEON) } }
            NeonChip("Padrão do sistema", selected = settings.launchAnim == LaunchAnim.SYSTEM) { onChange { it.copy(launchAnim = LaunchAnim.SYSTEM) } }
        }

        Section("Papel de parede do sistema")
        NeonText(
            "Usa a cidade como papel de parede do celular e atualiza de hora em hora, acompanhando o céu. Evita o fundo antigo piscando ao voltar pra home.",
            color = neon.dim,
            size = 12.sp,
            family = Sans,
        )
        Spacer(Modifier.height(10.dp))
        ChipRow {
            NeonChip("Desligado", selected = settings.wallpaperSync == WallpaperSync.OFF) { onApplyWallpaper(WallpaperSync.OFF) }
            NeonChip("Tela inicial", selected = settings.wallpaperSync == WallpaperSync.HOME) { onApplyWallpaper(WallpaperSync.HOME) }
            NeonChip("Inicial + bloqueio", selected = settings.wallpaperSync == WallpaperSync.HOME_LOCK) { onApplyWallpaper(WallpaperSync.HOME_LOCK) }
        }

        Section("Animação ao desbloquear")
        ChipRow {
            NeonChip("Ligada", selected = settings.unlockAnim) { onChange { it.copy(unlockAnim = true) } }
            NeonChip("Desligada", selected = !settings.unlockAnim) { onChange { it.copy(unlockAnim = false) } }
        }
        if (settings.unlockAnim && !lockEnabled) {
            Spacer(Modifier.height(6.dp))
            NeonText(
                "Hoje ela só aparece quando você desbloqueia direto na home. Pra aparecer também por cima dos apps, ative o serviço do NeonGrid em Acessibilidade.",
                color = neon.dim,
                size = 12.sp,
                family = Sans,
            )
            MenuItem("Ativar animação em qualquer tela", color = neon.accent) { onEnableLock() }
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
