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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.util.Locale
import kotlin.random.Random
import java.time.format.TextStyle as JTextStyle

@Composable
fun HomeScreen(
    homeApps: List<AppEntry>,
    dockApps: List<AppEntry>,
    settings: LauncherSettings,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val threshold = with(LocalDensity.current) { 70.dp.toPx() }
    val openDrawer by rememberUpdatedState(onOpenDrawer)

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -threshold) openDrawer() },
                    onVerticalDrag = { change, amount ->
                        total += amount
                        change.consume()
                    },
                )
            }
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        HudHeader(appCount = homeApps.size + dockApps.size, onOpenSettings = onOpenSettings)

        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        ) {
            if (homeApps.isEmpty()) {
                EmptyHint()
            } else {
                HomeGrid(homeApps, settings, onLaunch, onLongPress)
            }
        }

        SwipeHint(onClick = onOpenDrawer)
        Spacer(Modifier.height(10.dp))
        Dock(dockApps, settings, onLaunch, onLongPress)
        Spacer(Modifier.height(14.dp))
    }
}

/* ---------------------------------- HUD ---------------------------------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HudHeader(appCount: Int, onOpenSettings: () -> Unit) {
    val neon = LocalNeon.current
    val now by rememberNow()
    val battery by rememberBattery()

    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonText("◢ NEONGRID", color = neon.accent, size = 11.sp, weight = FontWeight.Bold, spacing = 3.sp, glow = true)
            NeonText(" // SYS.ONLINE", color = neon.dim, size = 11.sp, spacing = 1.sp)
            Spacer(Modifier.weight(1f))
            val (level, charging) = battery
            val batColor = when {
                charging -> neon.accent
                level <= 15 -> neon.danger
                else -> neon.text
            }
            NeonText(
                "PWR ${level}%" + if (charging) " ⚡" else "",
                color = batColor,
                size = 11.sp,
                weight = FontWeight.Bold,
                spacing = 1.sp,
            )
            Spacer(Modifier.width(10.dp))
            NeonText(
                "[≡]",
                color = neon.accent,
                size = 13.sp,
                weight = FontWeight.Bold,
                modifier = Modifier
                    .clip(ChipShape)
                    .noRippleClickable(onOpenSettings)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }

        Spacer(Modifier.height(30.dp))

        GlitchClock(
            now = now,
            modifier = Modifier.combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = onOpenSettings,
            ),
        )

        val dow = now.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault())
            .uppercase().trimEnd('.')
        NeonText(
            "%s // %02d.%02d.%d".format(dow, now.dayOfMonth, now.monthValue, now.year),
            color = neon.text.copy(alpha = 0.8f),
            size = 14.sp,
            spacing = 4.sp,
        )

        Spacer(Modifier.height(14.dp))
        NeonDivider(fraction = 0.6f)
        Spacer(Modifier.height(10.dp))

        val greeting = when (now.hour) {
            in 5..11 -> "BOM DIA"
            in 12..17 -> "BOA TARDE"
            else -> "BOA NOITE"
        }
        val cursor = if (now.second % 2 == 0) "_" else " "
        NeonText("> $greeting, OPERADOR$cursor", color = neon.accent.copy(alpha = 0.9f), size = 12.sp, spacing = 1.5.sp)
        NeonText("> $appCount módulos fixados · link estável", color = neon.dim, size = 11.sp, spacing = 1.sp)
    }
}

/** Relógio com aberração cromática e um "glitch" aleatório de vez em quando. */
@Composable
private fun GlitchClock(now: LocalDateTime, modifier: Modifier = Modifier) {
    val neon = LocalNeon.current
    var glitch by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(4_000, 9_000))
            glitch = true; delay(90)
            glitch = false; delay(60)
            glitch = true; delay(70)
            glitch = false
        }
    }

    val colon = if (now.second % 2 == 0) ":" else " "
    val text = "%02d%s%02d".format(now.hour, colon, now.minute)
    val style = TextStyle(
        fontFamily = Mono,
        fontSize = 78.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-2).sp,
    )
    val split = if (glitch) 7.dp else 2.dp
    val jitter = if (glitch) 3.dp else 0.dp

    Box(modifier) {
        Text(text, style = style, color = neon.alt.copy(alpha = 0.7f), modifier = Modifier.offset(x = -split, y = jitter))
        Text(text, style = style, color = neon.accent.copy(alpha = 0.7f), modifier = Modifier.offset(x = split, y = -jitter))
        Text(
            text,
            style = style.copy(shadow = Shadow(neon.accent, blurRadius = 36f)),
            color = Color.White,
            modifier = Modifier.offset(x = if (glitch) 2.dp else 0.dp),
        )
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

/* -------------------------------- Grid / Dock ------------------------------ */

@Composable
private fun HomeGrid(
    apps: List<AppEntry>,
    settings: LauncherSettings,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
) {
    val columns = settings.columns
    val tile = if (columns == 5) 50.dp else 56.dp
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        apps.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { app ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        AppTile(
                            app = app,
                            neonIcons = settings.neonIcons,
                            showLabel = true,
                            tileSize = tile,
                            onClick = { onLaunch(app) },
                            onLongClick = { onLongPress(app) },
                        )
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
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
) {
    val neon = LocalNeon.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, PanelShape, ambientColor = neon.accent, spotColor = neon.accent)
            .clip(PanelShape)
            .background(neon.panel.copy(alpha = 0.9f))
            .border(1.dp, Brush.horizontalGradient(listOf(neon.accent, neon.alt)), PanelShape)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    ) {
        if (apps.isEmpty()) {
            NeonText(
                "DOCK VAZIO // SEGURE UM APP PRA FIXAR",
                color = neon.dim,
                size = 11.sp,
                spacing = 1.sp,
                modifier = Modifier.padding(vertical = 18.dp),
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                apps.forEach { app ->
                    AppTile(
                        app = app,
                        neonIcons = settings.neonIcons,
                        showLabel = false,
                        tileSize = 50.dp,
                        onClick = { onLaunch(app) },
                        onLongClick = { onLongPress(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeHint(onClick: () -> Unit) {
    val neon = LocalNeon.current
    val pulse = rememberInfiniteTransition(label = "hint")
    val alpha = pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "hintAlpha",
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        NeonText(
            "︽  APPS  ︽",
            color = neon.accent,
            size = 11.sp,
            weight = FontWeight.Bold,
            spacing = 3.sp,
            glow = true,
            modifier = Modifier
                .graphicsLayer { this.alpha = alpha.value }
                .noRippleClickable(onClick)
                .padding(horizontal = 24.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun EmptyHint() {
    val neon = LocalNeon.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(neon.panel.copy(alpha = 0.6f))
            .border(1.dp, neon.dim.copy(alpha = 0.4f), TileShape)
            .padding(16.dp),
    ) {
        NeonText("> nenhum app fixado na home.", color = neon.text, size = 12.sp)
        NeonText("> deslize pra cima, segure um app", color = neon.dim, size = 12.sp)
        NeonText("  e escolha FIXAR NA HOME_", color = neon.dim, size = 12.sp)
    }
}
