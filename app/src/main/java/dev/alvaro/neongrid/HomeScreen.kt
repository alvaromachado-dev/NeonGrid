package dev.alvaro.neongrid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

/** Item da tela inicial: app solto ou pasta. [token] = id salvo nas preferências. */
sealed interface HomeEntry {
    val token: String
}

data class HomeApp(override val token: String, val app: AppEntry) : HomeEntry

data class HomeFolder(override val token: String, val folder: Folder, val apps: List<AppEntry>) : HomeEntry

/** Tudo o que a tela inicial pode pedir pro launcher fazer. */
class HomeActions(
    val onRequestDefault: () -> Unit,
    val onLaunch: (AppEntry) -> Unit,
    val onAppLongPress: (AppEntry) -> Unit,
    val onFolderOpen: (String) -> Unit,
    val onFolderLongPress: (String) -> Unit,
    val onOpenDrawer: () -> Unit,
    val onOpenSearch: () -> Unit,
    val onOpenNotifications: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onDoubleTap: () -> Unit,
    val onSwipeRight: () -> Unit,
    val onMusicOpen: () -> Unit,
    val onMusicPrev: () -> Unit,
    val onMusicPlayPause: () -> Unit,
    val onMusicNext: () -> Unit,
)

@Composable
fun HomeScreen(
    entries: List<HomeEntry>,
    dockApps: List<AppEntry>,
    settings: LauncherSettings,
    isDefaultHome: Boolean,
    nowPlaying: NowPlaying?,
    drag: DragController,
    actions: HomeActions,
    intro: () -> Float = { 1f },
    decodeSignal: Int = 0,
) {
    val density = LocalDensity.current
    val vThreshold = with(density) { 70.dp.toPx() }
    val hThreshold = with(density) { 90.dp.toPx() }
    val act by rememberUpdatedState(actions)

    Column(
        Modifier
            .fillMaxSize()
            // Gestos no fundo: ↑ apps · ↓ notificações · → câmera · ← busca
            .pointerInput(Unit) {
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { total = Offset.Zero },
                    onDragEnd = {
                        val (dx, dy) = total
                        if (abs(dy) >= abs(dx)) {
                            when {
                                dy < -vThreshold -> act.onOpenDrawer()
                                dy > vThreshold -> act.onOpenNotifications()
                            }
                        } else {
                            when {
                                dx > hThreshold -> act.onSwipeRight()
                                dx < -hThreshold -> act.onOpenSearch()
                            }
                        }
                    },
                    onDrag = { change, amount ->
                        total += amount
                        change.consume()
                    },
                )
            }
            // toque duplo bloqueia · segurar no vazio abre Personalizar
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { act.onDoubleTap() },
                    onLongPress = { act.onOpenSettings() },
                )
            }
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 22.dp),
    ) {
        Box(Modifier.introStagger(intro, 0)) { TopBar(actions.onOpenSettings) }
        Spacer(Modifier.height(26.dp))
        Box(Modifier.introStagger(intro, 1)) { ClockBlock(onLongPress = actions.onOpenSettings, decodeSignal = decodeSignal) }

        if (!isDefaultHome) {
            Spacer(Modifier.height(18.dp))
            DefaultHomeBanner(actions.onRequestDefault)
        }

        if (nowPlaying != null) {
            Spacer(Modifier.height(18.dp))
            Box(Modifier.introStagger(intro, 2)) {
            MusicCard(
                np = nowPlaying,
                onOpen = actions.onMusicOpen,
                onPrev = actions.onMusicPrev,
                onPlayPause = actions.onMusicPlayPause,
                onNext = actions.onMusicNext,
            )
            }
        }

        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { drag.homeArea = it.boundsInWindow() }
                .introStagger(intro, 3)
                .padding(bottom = 10.dp),
        ) {
            if (entries.isEmpty()) {
                SideEffect {
                    drag.homeGrid = null
                    drag.homeCount = 0
                }
                EmptyHint()
            } else {
                HomeGrid(entries, settings, drag, actions)
            }
        }

        Box(Modifier.introStagger(intro, 4)) { DrawerHandle(onClick = actions.onOpenDrawer) }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.introStagger(intro, 5)) { Dock(dockApps, settings, drag, actions) }
        Spacer(Modifier.height(10.dp))
    }
}

/* -------------------------------- Topo -------------------------------- */

@Composable
private fun TopBar(onOpenSettings: () -> Unit) {
    val neon = LocalNeon.current
    val battery by rememberBattery()
    val (level, charging) = battery

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(ChipShape)
                .background(neon.alt)
        )
        Spacer(Modifier.width(8.dp))
        NeonText("ネオン東京", color = neon.text.copy(alpha = 0.85f), size = 12.sp, family = Sans, weight = FontWeight.Medium, spacing = 2.sp, shadow = true)
        Spacer(Modifier.weight(1f))
        BatteryMeter(level, charging)
        Spacer(Modifier.width(12.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .glass(neon, ChipShape)
                .noRippleClickable(onOpenSettings),
        ) {
            Canvas(Modifier.size(14.dp)) {
                val r = size.minDimension / 9f
                for (i in 0..2) {
                    drawCircle(neon.text, radius = r, center = Offset(size.width / 2f, size.height * (0.15f + i * 0.35f)))
                }
            }
        }
    }
}

@Composable
private fun BatteryMeter(level: Int, charging: Boolean) {
    val neon = LocalNeon.current
    val color = when {
        charging -> neon.accent
        level <= 15 -> neon.danger
        else -> neon.text
    }
    val filled = ((level + 10) / 20).coerceIn(0, 5)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(5) { i ->
                Box(
                    Modifier
                        .width(4.dp)
                        .height(11.dp)
                        .clip(ChipShape)
                        .background(if (i < filled) color else Color.White.copy(alpha = 0.18f))
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        NeonText(
            (if (charging) "⚡" else "") + "$level%",
            color = color,
            size = 12.sp,
            family = Sans,
            weight = FontWeight.Medium,
            shadow = true,
        )
    }
}

/** Relógio em "tubo de neon": núcleo branco fino + brilho colorido, com falhas raras. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClockBlock(onLongPress: () -> Unit, decodeSignal: Int) {
    val neon = LocalNeon.current
    val now by rememberNow()
    var power by remember { mutableFloatStateOf(1f) }
    var scramble by remember { mutableStateOf<String?>(null) }

    // Ao desbloquear: os dígitos embaralham e vão "travando" um a um na hora certa
    LaunchedEffect(decodeSignal) {
        if (decodeSignal <= 0) return@LaunchedEffect
        val glyphs = "0123456789#%&@$?"
        val start = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - start
            val real = "%02d:%02d".format(LocalDateTime.now().hour, LocalDateTime.now().minute)
            if (elapsed > 260 + real.length * 110) break
            scramble = real.mapIndexed { i, ch ->
                if (ch == ':' || elapsed > 260 + i * 110) ch else glyphs[Random.nextInt(glyphs.length)]
            }.joinToString("")
            delay(45)
        }
        scramble = null
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(9_000, 16_000))
            for (v in floatArrayOf(0.35f, 1f, 0.55f, 0.9f, 0.3f, 1f)) {
                power = v
                delay(Random.nextLong(40, 110))
            }
        }
    }

    val time = scramble ?: "%02d:%02d".format(now.hour, now.minute)
    val base = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        fontSize = 70.sp,
        letterSpacing = 1.sp,
    )

    Column(
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
            onLongClick = onLongPress,
        )
    ) {
        Box {
            Text(
                time,
                style = base.copy(shadow = Shadow(neon.accent, blurRadius = 60f)),
                color = neon.accent.copy(alpha = 0.55f * power),
            )
            Text(
                time,
                style = base.copy(shadow = Shadow(neon.accent.copy(alpha = 0.9f), blurRadius = 16f)),
                color = Color.White.copy(alpha = 0.35f + 0.65f * power),
            )
        }

        val locale = Locale.getDefault()
        val pattern = if (locale.language == "pt") "EEEE, d 'de' MMMM" else "EEEE, MMMM d"
        val date = now.format(DateTimeFormatter.ofPattern(pattern, locale))
        NeonText(date, color = neon.text.copy(alpha = 0.9f), size = 17.sp, family = Sans, weight = FontWeight.Light, spacing = 0.3.sp, shadow = true)

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(22.dp)
                    .height(2.dp)
                    .background(Brush.horizontalGradient(listOf(neon.accent, neon.alt)))
            )
            Spacer(Modifier.width(10.dp))
            val greeting = when (now.hour) {
                in 5..11 -> "おはよう · bom dia"
                in 12..17 -> "こんにちは · boa tarde"
                else -> "こんばんは · boa noite"
            }
            NeonText(greeting, color = neon.accent, size = 12.sp, family = Sans, spacing = 1.sp, glow = true)
        }
    }
}

@Composable
private fun rememberNow(): State<LocalDateTime> {
    val state = remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            state.value = LocalDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    return state
}

/** Nível de bateria (0–100) e se está carregando. */
@Composable
private fun rememberBattery(): State<Pair<Int, Boolean>> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(0 to false) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                state.value = (level * 100 / scale) to charging
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sticky?.let { receiver.onReceive(context, it) }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/* -------------------------------- Apps -------------------------------- */

@Composable
private fun HomeGrid(
    entries: List<HomeEntry>,
    settings: LauncherSettings,
    drag: DragController,
    actions: HomeActions,
) {
    val columns = settings.columns
    val icon = if (columns == 5) 46.dp else 52.dp
    val dragged = drag.dragged
    SideEffect {
        drag.homeCols = columns
        drag.homeCount = entries.size
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.onGloballyPositioned { drag.homeGrid = it.boundsInWindow() },
    ) {
        entries.chunked(columns).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { c, entry ->
                    val index = r * columns + c
                    val ghost = dragged != null && !dragged.fromDock && dragged.fromIndex == index
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        when (entry) {
                            is HomeApp -> AppTile(
                                app = entry.app,
                                iconStyle = settings.iconStyle,
                                showLabel = true,
                                iconSize = icon,
                                ghost = ghost,
                                drag = drag.handleFor { Dragged(entry.token, false, index, entry.app, null) },
                                onClick = { actions.onLaunch(entry.app) },
                                onLongClick = { actions.onAppLongPress(entry.app) },
                            )
                            is HomeFolder -> FolderTile(
                                name = entry.folder.name,
                                apps = entry.apps,
                                iconStyle = settings.iconStyle,
                                iconSize = icon,
                                ghost = ghost,
                                drag = drag.handleFor { Dragged(entry.token, false, index, null, entry.apps) },
                                onClick = { actions.onFolderOpen(entry.folder.id) },
                                onLongClick = { actions.onFolderLongPress(entry.folder.id) },
                            )
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Dock(
    apps: List<AppEntry>,
    settings: LauncherSettings,
    drag: DragController,
    actions: HomeActions,
) {
    val neon = LocalNeon.current
    val dragged = drag.dragged
    SideEffect { drag.dockCount = apps.size }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .glass(neon, PanelShape)
            .drawBehind {
                // fio de neon no topo do vidro
                drawLine(
                    Brush.horizontalGradient(listOf(Color.Transparent, neon.accent, neon.alt, Color.Transparent)),
                    start = Offset(size.width * 0.1f, 0.5f),
                    end = Offset(size.width * 0.9f, 0.5f),
                    strokeWidth = 2f,
                )
            }
            .then(if (apps.isEmpty()) Modifier.onGloballyPositioned { drag.dockRow = it.boundsInWindow() } else Modifier)
            .padding(vertical = 8.dp, horizontal = 6.dp),
    ) {
        if (apps.isEmpty()) {
            NeonText(
                "Arraste um app até aqui pra fixar no dock",
                color = neon.dim,
                size = 13.sp,
                family = Sans,
                modifier = Modifier.padding(vertical = 18.dp),
            )
        } else {
            // cada ícone ganha uma fatia igual da largura, então 5 apps sempre cabem
            val icon = if (apps.size >= 5) 44.dp else 50.dp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { drag.dockRow = it.boundsInWindow() },
            ) {
                apps.forEachIndexed { index, app ->
                    val ghost = dragged != null && dragged.fromDock && dragged.fromIndex == index
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        AppTile(
                            app = app,
                            iconStyle = settings.iconStyle,
                            showLabel = false,
                            iconSize = icon,
                            ghost = ghost,
                            drag = drag.handleFor { Dragged(app.key, true, index, app, null) },
                            onClick = { actions.onLaunch(app) },
                            onLongClick = { actions.onAppLongPress(app) },
                        )
                    }
                }
            }
        }
    }
}

/** Setinha pulsando que indica a gaveta. */
@Composable
private fun DrawerHandle(onClick: () -> Unit) {
    val neon = LocalNeon.current
    val pulse = rememberInfiniteTransition(label = "handle")
    val t = pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "handleT",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .noRippleClickable(onClick),
    ) {
        Canvas(
            Modifier
                .width(34.dp)
                .height(12.dp)
                .graphicsLayer {
                    alpha = 0.45f + 0.55f * t.value
                    translationY = -4f * t.value
                }
        ) {
            val path = Path().apply {
                moveTo(0f, size.height)
                lineTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
            }
            drawPath(path, neon.accent.copy(alpha = 0.35f), style = Stroke(width = 8f, cap = StrokeCap.Round))
            drawPath(path, neon.accent, style = Stroke(width = 3f, cap = StrokeCap.Round))
        }
    }
}

/** Aviso que aparece enquanto o NeonGrid não é o launcher padrão. */
@Composable
private fun DefaultHomeBanner(onActivate: () -> Unit) {
    val neon = LocalNeon.current
    val pulse = rememberInfiniteTransition(label = "banner")
    val dot = pulse.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "bannerDot",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .glass(neon, TileShape)
            .noRippleClickable(onActivate)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .graphicsLayer { alpha = dot.value }
                .clip(ChipShape)
                .background(neon.danger)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NeonText("Não é o launcher padrão", color = neon.text, size = 14.sp, family = Sans, weight = FontWeight.Medium)
            NeonText("O botão Home ainda abre o launcher antigo", color = neon.dim, size = 12.sp, family = Sans)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .clip(ChipShape)
                .background(neon.accent)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            NeonText("Ativar", color = neon.bg, size = 13.sp, family = Sans, weight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyHint() {
    val neon = LocalNeon.current
    Column(
        Modifier
            .fillMaxWidth()
            .glass(neon, TileShape, alpha = 0.8f)
            .border(1.dp, Color.Transparent, TileShape)
            .padding(16.dp),
    ) {
        NeonText("Sua tela inicial está vazia", color = neon.text, size = 14.sp, family = Sans, weight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        NeonText(
            "Deslize pra cima pra ver os apps e segure um ícone pra fixar aqui.",
            color = neon.dim,
            size = 12.sp,
            family = Sans,
        )
    }
}
