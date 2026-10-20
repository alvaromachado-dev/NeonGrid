package dev.alvaro.neongrid

import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LauncherRoot(
    repo: AppRepository,
    prefs: LauncherPrefs,
    homeSignal: Int,
    resumeSignal: Int,
    stopSignal: Int,
    unlockSignal: Int,
    introSignal: Int,
    unlockArmed: Boolean,
    media: MediaRepository,
    weather: WeatherRepository,
    onEnableRealWeather: () -> Unit,
    musicEnabled: Boolean,
    lockEnabled: Boolean,
    isDefaultHome: Boolean,
    onRequestDefault: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val apps by repo.apps.collectAsState()
    val homeKeys by prefs.home.collectAsState()
    val dockKeys by prefs.dock.collectAsState()
    val folders by prefs.folders.collectAsState()
    val settings by prefs.settings.collectAsState()
    val usage by prefs.usage.collectAsState()
    val weatherNow by weather.state.collectAsState()
    val sceneWeather = when (settings.weatherMode) {
        WeatherMode.REAL -> weatherNow?.scene ?: SceneWeather.ALWAYS_RAIN
        WeatherMode.RAIN -> SceneWeather.ALWAYS_RAIN
        WeatherMode.CLEAR -> SceneWeather.ALWAYS_CLEAR
    }
    val weatherLabel = if (settings.weatherMode == WeatherMode.REAL) weatherNow?.let { "${it.tempC}° ${it.kind.label}" } else null
    val nowPlaying by media.state.collectAsState()
    val musicDebug by media.debug.collectAsState()

    val byKey = remember(apps) { apps.associateBy { it.key } }
    val homeEntries = remember(byKey, homeKeys, folders) {
        homeKeys.mapNotNull { t ->
            if (isFolderToken(t)) {
                folders[folderIdOf(t)]?.let { f ->
                    val fa = f.apps.mapNotNull { byKey[it] }
                    if (fa.isEmpty()) null else HomeFolder(t, f, fa)
                }
            } else {
                byKey[t]?.let { HomeApp(t, it) }
            }
        }
    }
    val dockApps = remember(byKey, dockKeys) { dockKeys.mapNotNull { byKey[it] } }

    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var drawerSearch by remember { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var menuFolderId by remember { mutableStateOf<String?>(null) }
    var openFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameFolder by remember { mutableStateOf(false) }

    val rootView = LocalView.current.rootView
    val scope = rememberCoroutineScope()
    val zoom = remember { Animatable(1f) }
    val neon = LocalNeon.current
    val skyHour = rememberSkyHour(settings.skyFollowsClock)

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    /* ------------------------------ Arrastar ------------------------------ */
    val drag = remember { DragController() }
    drag.slopPx = with(LocalDensity.current) { 20.dp.toPx() }
    SideEffect {
        drag.onStart = {
            menuApp = null
            menuFolderId = null
        }
        drag.onDrop = { d, spot ->
            val action = when (spot) {
                is DropSpot.HomeInsert -> DropAction.HomeBefore(homeEntries.getOrNull(spot.index)?.token)
                is DropSpot.HomeMerge -> homeEntries.getOrNull(spot.index)?.let { DropAction.HomeMerge(it.token) }
                is DropSpot.DockInsert -> DropAction.DockBefore(dockApps.getOrNull(spot.index)?.key)
                DropSpot.DockFull -> {
                    toast("Dock cheio (máx. $MAX_DOCK)")
                    null
                }
                DropSpot.None -> null
            }
            if (action != null) {
                val ok = prefs.applyDrop(d.token, d.fromDock, action) { repo.folderName(it) }
                if (ok) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (action is DropAction.HomeMerge && !isFolderToken(action.target)) toast("Pasta criada")
                }
            }
        }
    }

    // Limpa apps desinstalados de home, dock e pastas
    LaunchedEffect(byKey) {
        if (byKey.isNotEmpty()) prefs.prune(byKey.keys)
    }

    /* ------------------------- Animação de desbloqueio ------------------------- */
    val unlockP = remember { Animatable(1f) }
    var decodeSignal by remember { mutableStateOf(0) }
    var unlockCover by remember { mutableStateOf(true) }
    suspend fun playUnlock(cover: Boolean) {
        unlockCover = cover
            menuApp = null
            menuFolderId = null
            openFolderId = null
            settingsOpen = false
            drawerOpen = false
            zoom.snapTo(1f)
            unlockP.snapTo(0f)
            decodeSignal++
            unlockP.animateTo(1f, tween(1250, easing = LinearEasing))
    }
    // sem o serviço: o launcher desenha a animação inteira (só na home)
    LaunchedEffect(unlockSignal) { if (unlockSignal > 0) playUnlock(cover = true) }
    // com o serviço: ele desenha a capa por cima de tudo; aqui só a entrada dos elementos
    LaunchedEffect(introSignal) { if (introSignal > 0) playUnlock(cover = false) }
    val unlockVisible = unlockArmed || (unlockCover && unlockP.value < 1f)
    val px = LocalDensity.current.density

    /* ------------------------- Abertura e retorno ------------------------- */
    var opening by remember { mutableStateOf<OpeningApp?>(null) }
    val openProgress = remember { Animatable(0f) }

    LaunchedEffect(resumeSignal) {
        // voltando do bloqueio, quem anima é a animação de desbloqueio
        if (resumeSignal > 0 && !unlockArmed && unlockP.value >= 1f) {
            if (zoom.value < 1.01f) zoom.snapTo(1.10f)
            zoom.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 320f))
        }
    }
    LaunchedEffect(stopSignal) {
        if (stopSignal > 0) {
            opening = null
            openProgress.snapTo(0f)
            openFolderId = null
        }
    }
    LaunchedEffect(opening) {
        if (opening != null) {
            delay(4_000)
            opening = null
            openProgress.snapTo(0f)
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

    // Papel de parede do sistema acompanhando o céu (atualiza de hora em hora)
    LaunchedEffect(settings.wallpaperSync, skyHour.toInt(), settings.accent) {
        val sync = settings.wallpaperSync
        if (sync == WallpaperSync.OFF) return@LaunchedEffect
        val bucket = skyHour.toInt() * 10 + settings.accent.ordinal
        if (prefs.lastWallpaperHour == bucket) return@LaunchedEffect
        if (applyCityWallpaper(context, neon, sync == WallpaperSync.HOME_LOCK, skyHour, sceneWeather)) {
            prefs.lastWallpaperHour = bucket
        }
    }

    // Botão Home apertado com o launcher já aberto: volta pra tela inicial limpa
    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) {
            zoom.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 320f))
            menuApp = null
            menuFolderId = null
            openFolderId = null
            settingsOpen = false
            drawerOpen = false
        }
    }

    BackHandler(enabled = drawerOpen || settingsOpen || menuApp != null || menuFolderId != null || openFolderId != null) {
        when {
            menuApp != null -> menuApp = null
            menuFolderId != null -> menuFolderId = null
            openFolderId != null -> openFolderId = null
            settingsOpen -> settingsOpen = false
            else -> drawerOpen = false
        }
    }

    val fail: (AppEntry) -> Unit = { app -> toast("Falha ao abrir ${app.label}") }
    val launch: (AppEntry) -> Unit = { app ->
        prefs.recordLaunch(app.key)
        val source = LaunchSource.consume()
        if (settings.launchAnim == LaunchAnim.NEON && source != null && opening == null) {
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

    val actions = HomeActions(
        onRequestDefault = onRequestDefault,
        onLaunch = launch,
        onAppLongPress = { menuApp = it },
        onFolderOpen = { id ->
            renameFolder = false
            openFolderId = id
        },
        onFolderLongPress = { menuFolderId = it },
        onOpenDrawer = {
            drawerSearch = false
            drawerOpen = true
        },
        onOpenSearch = {
            drawerSearch = true
            drawerOpen = true
        },
        onOpenNotifications = onOpenNotifications,
        onOpenSettings = { settingsOpen = true },
        onDoubleTap = {
            if (!LockService.lock()) {
                toast("Ative o bloqueio por toque duplo em Personalizar → Gestos")
            }
        },
        onSwipeRight = {
            runCatching {
                context.startActivity(
                    Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { toast("Não achei o app de câmera") }
        },
        onMusicOpen = { media.open() },
        onMusicPrev = { media.previous() },
        onMusicPlayPause = { media.playPause() },
        onMusicNext = { media.next() },
    )

    // Caixa externa transparente: mostra o papel de parede do sistema atrás
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val z = zoom.value
                    scaleX = z
                    scaleY = z
                    alpha = (1f - (z - 1f) * 3f).coerceIn(0f, 1f)
                    translationX = glitchShift(unlockP.value, px)
                }
                .background(neon.bg)
        ) {
            NeoTokyoBackground(animated = settings.animatedBg && !drawerOpen, skyHour = skyHour, weather = sceneWeather)

            HomeScreen(
                entries = homeEntries,
                dockApps = dockApps,
                settings = settings,
                isDefaultHome = isDefaultHome,
                nowPlaying = nowPlaying,
                drag = drag,
                actions = actions,
                intro = { if (unlockArmed) 0f else introOf(unlockP.value) },
                weatherLabel = weatherLabel,
                decodeSignal = decodeSignal,
            )

            // Pasta aberta
            val openFolder = openFolderId?.let { folders[it] }
            AnimatedVisibility(
                visible = openFolder != null,
                enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.9f),
                exit = fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.92f),
            ) {
                val f = openFolder ?: return@AnimatedVisibility
                FolderPanel(
                    folder = f,
                    apps = f.apps.mapNotNull { byKey[it] },
                    iconStyle = settings.iconStyle,
                    startRenaming = renameFolder,
                    onLaunch = { app ->
                        launch(app)
                        openFolderId = null
                    },
                    onLongPress = { menuApp = it },
                    onRename = { prefs.renameFolder(f.id, it) },
                    onClose = { openFolderId = null },
                )
            }

            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInVertically(tween(280)) { it / 5 } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(220)) { it / 5 } + fadeOut(tween(180)),
            ) {
                AppDrawer(
                    apps = apps,
                    settings = settings,
                    focusSearch = drawerSearch,
                    usage = usage,
                    onWebSearch = { q ->
                        drawerOpen = false
                        SmartSearch.webSearch(context, q)
                    },
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
                    onApplyWallpaper = { mode ->
                        prefs.update { it.copy(wallpaperSync = mode) }
                        if (mode != WallpaperSync.OFF) {
                            toast("Aplicando papel de parede…")
                            scope.launch {
                                val ok = applyCityWallpaper(context, neon, mode == WallpaperSync.HOME_LOCK, skyHour, sceneWeather)
                                if (ok) prefs.lastWallpaperHour = skyHour.toInt() * 10 + settings.accent.ordinal
                                toast(if (ok) "Papel de parede aplicado" else "Não foi possível aplicar o papel de parede")
                            }
                        }
                    },
                    musicEnabled = musicEnabled,
                    musicDebug = musicDebug,
                    weatherLabel = weatherLabel,
                    onWeatherMode = { mode ->
                        if (mode == WeatherMode.REAL) onEnableRealWeather()
                        else prefs.update { it.copy(weatherMode = mode) }
                    },
                    lockEnabled = lockEnabled,
                    onEnableMusic = { runCatching { context.startActivity(media.accessIntent()) } },
                    onEnableLock = {
                        toast("Procure \"NeonGrid · bloqueio e animação de desbloqueio\" e ative")
                        runCatching { context.startActivity(LockService.settingsIntent()) }
                    },
                    onClose = { settingsOpen = false },
                )
            }

            menuFolderId?.let { id ->
                val f = folders[id]
                if (f == null) {
                    menuFolderId = null
                } else {
                    FolderMenu(
                        folder = f,
                        apps = f.apps.mapNotNull { byKey[it] },
                        iconStyle = settings.iconStyle,
                        onAction = { action ->
                            when (action) {
                                FolderAction.OPEN -> {
                                    renameFolder = false
                                    openFolderId = id
                                }
                                FolderAction.RENAME -> {
                                    renameFolder = true
                                    openFolderId = id
                                }
                                FolderAction.DISSOLVE -> prefs.dissolveFolder(id)
                                FolderAction.REMOVE -> prefs.removeFolder(id)
                            }
                            menuFolderId = null
                        },
                        onDismiss = { menuFolderId = null },
                    )
                }
            }

            menuApp?.let { app ->
                val inFolder = folders.values.firstOrNull { app.key in it.apps }
                AppMenu(
                    app = app,
                    inHome = app.key in homeKeys || inFolder != null,
                    inDock = app.key in dockKeys,
                    dockFull = dockKeys.size >= MAX_DOCK,
                    folderName = inFolder?.name,
                    onAction = { action ->
                        when (action) {
                            MenuAction.HOME -> prefs.toggleHome(app.key)
                            MenuAction.DOCK -> prefs.toggleDock(app.key)
                            MenuAction.INFO -> runCatching { repo.openInfo(app) }
                            MenuAction.UNINSTALL -> runCatching { repo.uninstall(app) }
                            MenuAction.OUT_OF_FOLDER -> inFolder?.let { prefs.removeFromFolder(it.id, app.key) }
                        }
                        menuApp = null
                    },
                    onDismiss = { menuApp = null },
                )
            }
        }

        DragOverlay(drag, settings.iconStyle)
        opening?.let { o -> LaunchOverlay(o) { openProgress.value } }
        if (unlockVisible) UnlockOverlay { if (unlockArmed) 0f else unlockP.value }
    }
}
