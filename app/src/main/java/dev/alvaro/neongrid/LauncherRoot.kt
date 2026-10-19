package dev.alvaro.neongrid

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun LauncherRoot(repo: AppRepository, prefs: LauncherPrefs, homeSignal: Int) {
    val context = LocalContext.current
    val apps by repo.apps.collectAsState()
    val homeKeys by prefs.home.collectAsState()
    val dockKeys by prefs.dock.collectAsState()
    val settings by prefs.settings.collectAsState()

    val byKey = remember(apps) { apps.associateBy { it.key } }
    val homeApps = remember(byKey, homeKeys) { homeKeys.mapNotNull { byKey[it] } }
    val dockApps = remember(byKey, dockKeys) { dockKeys.mapNotNull { byKey[it] } }

    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }

    // Primeira execução: monta o dock com os apps padrão do aparelho
    LaunchedEffect(apps) {
        if (apps.isNotEmpty() && !prefs.initialized) {
            prefs.setDock(repo.defaultDock(apps))
            prefs.markInitialized()
        }
    }

    // Botão Home apertado com o launcher já aberto: volta pra tela inicial limpa
    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) {
            menuApp = null
            settingsOpen = false
            drawerOpen = false
        }
    }

    BackHandler(enabled = drawerOpen || settingsOpen || menuApp != null) {
        when {
            menuApp != null -> menuApp = null
            settingsOpen -> settingsOpen = false
            else -> drawerOpen = false
        }
    }

    val launch: (AppEntry) -> Unit = { app ->
        runCatching { repo.launch(app) }
            .onFailure { Toast.makeText(context, "Falha ao abrir ${app.label}", Toast.LENGTH_SHORT).show() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(LocalNeon.current.bg)
    ) {
        CyberBackground(animated = settings.animatedBg)

        HomeScreen(
            homeApps = homeApps,
            dockApps = dockApps,
            settings = settings,
            onLaunch = launch,
            onLongPress = { menuApp = it },
            onOpenDrawer = { drawerOpen = true },
            onOpenSettings = { settingsOpen = true },
        )

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically(tween(260)) { it / 4 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(200)) { it / 4 } + fadeOut(tween(180)),
        ) {
            AppDrawer(
                apps = apps,
                settings = settings,
                onLaunch = { app ->
                    launch(app)
                    drawerOpen = false
                },
                onLongPress = { menuApp = it },
                onClose = { drawerOpen = false },
            )
        }

        AnimatedVisibility(visible = settingsOpen, enter = fadeIn(), exit = fadeOut()) {
            SettingsPanel(
                settings = settings,
                onChange = { prefs.update(it) },
                onClose = { settingsOpen = false },
            )
        }

        menuApp?.let { app ->
            AppMenu(
                app = app,
                inHome = app.key in homeKeys,
                inDock = app.key in dockKeys,
                dockFull = dockKeys.size >= MAX_DOCK,
                onAction = { action ->
                    when (action) {
                        MenuAction.HOME -> prefs.toggleHome(app.key)
                        MenuAction.DOCK -> prefs.toggleDock(app.key)
                        MenuAction.INFO -> runCatching { repo.openInfo(app) }
                        MenuAction.UNINSTALL -> runCatching { repo.uninstall(app) }
                    }
                    menuApp = null
                },
                onDismiss = { menuApp = null },
            )
        }
    }
}
