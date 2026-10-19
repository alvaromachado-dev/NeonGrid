package dev.alvaro.neongrid

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LauncherRoot(
    repo: AppRepository,
    prefs: LauncherPrefs,
    homeSignal: Int,
    resumeSignal: Int,
    stopSignal: Int,
    isDefaultHome: Boolean,
    onRequestDefault: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
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

    // Zoom da home: avança quando um app abre e volta com mola quando você retorna (estilo iOS)
    val rootView = LocalView.current.rootView
    val scope = rememberCoroutineScope()
    val zoom = remember { Animatable(1f) }
    val neon = LocalNeon.current

    // Animação de abertura própria (o app que está abrindo e o progresso 0→1)
    var opening by remember { mutableStateOf<OpeningApp?>(null) }
    val openProgress = remember { Animatable(0f) }

    // Launcher saiu da tela (o app já cobriu tudo): limpa a animação de abertura
    LaunchedEffect(stopSignal) {
        if (stopSignal > 0) {
            opening = null
            openProgress.snapTo(0f)
        }
    }
    // Segurança: se o app não abrir, a animação some sozinha
    LaunchedEffect(opening) {
        if (opening != null) {
            delay(4_000)
            opening = null
            openProgress.snapTo(0f)
            zoom.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 320f))
        }
    }
    LaunchedEffect(resumeSignal) {
        if (resumeSignal > 0) {
            if (zoom.value < 1.01f) zoom.snapTo(1.10f)
            zoom.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 320f))
        }
    }

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
            zoom.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 320f))
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

    val fail: (AppEntry) -> Unit = { app ->
        Toast.makeText(context, "Falha ao abrir ${app.label}", Toast.LENGTH_SHORT).show()
    }
    val launch: (AppEntry) -> Unit = { app ->
        val source = LaunchSource.consume()
        if (settings.launchAnim == LaunchAnim.NEON && source != null && opening == null) {
            // 1) ícone cresce até a tela inteira  2) app abre por cima, sem animação do sistema
            opening = OpeningApp(app, source)
            scope.launch { zoom.animateTo(1.12f, tween(300, easing = FastOutSlowInEasing)) }
            scope.launch {
                openProgress.snapTo(0f)
                openProgress.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            }
            scope.launch {
                delay(190)
                runCatching { repo.launch(app, source, rootView, starter = context, instant = true) }
                    .onFailure {
                        opening = null
                        openProgress.snapTo(0f)
                        zoom.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 320f))
                        fail(app)
                    }
            }
        } else {
            runCatching { repo.launch(app, source, rootView) }
                .onSuccess { scope.launch { zoom.animateTo(1.12f, tween(320, easing = FastOutSlowInEasing)) } }
                .onFailure { fail(app) }
        }
    }

    Box(Modifier.fillMaxSize()) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val z = zoom.value
                scaleX = z
                scaleY = z
                alpha = (1f - (z - 1f) * 3f).coerceIn(0f, 1f)
            }
            .background(neon.bg)
    ) {
        NeoTokyoBackground(animated = settings.animatedBg && !drawerOpen)

        HomeScreen(
            homeApps = homeApps,
            dockApps = dockApps,
            settings = settings,
            isDefaultHome = isDefaultHome,
            onRequestDefault = onRequestDefault,
            onLaunch = launch,
            onLongPress = { menuApp = it },
            onOpenDrawer = { drawerOpen = true },
            onOpenNotifications = onOpenNotifications,
            onOpenSettings = { settingsOpen = true },
        )

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically(tween(280)) { it / 5 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(220)) { it / 5 } + fadeOut(tween(180)),
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
                isDefaultHome = isDefaultHome,
                onChange = { prefs.update(it) },
                onRequestDefault = {
                    settingsOpen = false
                    onRequestDefault()
                },
                onApplyWallpaper = { lock ->
                    Toast.makeText(context, "Aplicando papel de parede…", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        val ok = applyCityWallpaper(context, neon, lock)
                        Toast.makeText(
                            context,
                            if (ok) "Papel de parede aplicado" else "Não foi possível aplicar o papel de parede",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
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

    opening?.let { o -> LaunchOverlay(o) { openProgress.value } }
    }
}
