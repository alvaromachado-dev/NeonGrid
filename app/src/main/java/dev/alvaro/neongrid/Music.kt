package dev.alvaro.neongrid

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Serviço vazio: só existe porque o Android exige um "leitor de notificações"
 * pra que um app possa ver o que está tocando. O NeonGrid não lê nenhuma notificação.
 */
class MediaListenerService : NotificationListenerService()

@Immutable
data class NowPlaying(
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val color: Color?,
    val playing: Boolean,
    val packageName: String,
)

/** Acompanha o player ativo (Spotify, YouTube Music etc.) via MediaSession. */
class MediaRepository(private val context: Context) {

    private val component = ComponentName(context, MediaListenerService::class.java)
    private val msm = context.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state.asStateFlow()

    /** Diagnóstico mostrado em Personalizar → Música (o que o NeonGrid está enxergando). */
    private val _debug = MutableStateFlow("")
    val debug: StateFlow<String> = _debug.asStateFlow()

    private var controllers: List<MediaController> = emptyList()
    private var active: MediaController? = null
    private var started = false
    private var lastArtKey: String? = null
    private var lastArt: Pair<ImageBitmap?, Color?> = null to null

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        setControllers(list ?: emptyList())
    }

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Chamado ao voltar pra home: liga se a permissão foi dada. */
    fun start() {
        if (!hasAccess()) {
            _state.value = null
            _debug.value = "sem acesso às notificações"
            return
        }
        if (started) {
            // relê as sessões ao voltar pra home (algumas fabricantes não avisam mudanças)
            runCatching { setControllers(msm.getActiveSessions(component)) }
                .onFailure { _debug.value = "erro ao ler players: ${it.javaClass.simpleName}" }
            return
        }
        runCatching {
            msm.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            setControllers(msm.getActiveSessions(component))
            started = true
        }.onFailure { _debug.value = "erro ao ligar: ${it.javaClass.simpleName}: ${it.message?.take(80)}" }
    }

    fun stop() {
        if (!started) return
        runCatching { msm.removeOnActiveSessionsChangedListener(sessionsListener) }
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = emptyList()
        started = false
    }

    private fun setControllers(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = list
        _debug.value = if (list.isEmpty()) {
            "nenhum player ativo encontrado"
        } else {
            list.joinToString { c ->
                val st = when (c.playbackState?.state) {
                    PlaybackState.STATE_PLAYING -> "tocando"
                    PlaybackState.STATE_PAUSED -> "pausado"
                    null -> "sem estado"
                    else -> "estado ${c.playbackState?.state}"
                }
                "${c.packageName.substringAfterLast('.')} ($st${if (c.metadata == null) ", sem info da música" else ""})"
            }
        }
        list.forEach { runCatching { it.registerCallback(callback, handler) } }
        refresh()
    }

    private fun refresh() {
        val playing = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val c = playing ?: controllers.firstOrNull { it.metadata != null }
        active = c
        val md = c?.metadata
        if (c == null || md == null) {
            _state.value = null
            return
        }
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return run { _state.value = null }
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val artKey = "${c.packageName}|$title|$artist"
        if (artKey != lastArtKey) {
            lastArtKey = artKey
            val bmp: Bitmap? = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            lastArt = runCatching {
                if (bmp == null) null to null
                else {
                    val small = bmp.scale(160, 160)
                    val sw = Palette.from(small).generate().let { it.vibrantSwatch ?: it.lightVibrantSwatch ?: it.dominantSwatch }
                    small.asImageBitmap() to sw?.let { neonize(it.rgb) }
                }
            }.getOrDefault(null to null)
        }
        _state.value = NowPlaying(
            title = title,
            artist = artist,
            art = lastArt.first,
            color = lastArt.second,
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            packageName = c.packageName,
        )
    }

    private fun neonize(rgb: Int): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(rgb, hsv)
        hsv[1] = hsv[1].coerceAtLeast(0.6f)
        hsv[2] = 1f
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    fun playPause() {
        val c = active ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() = active?.transportControls?.skipToNext()
    fun previous() = active?.transportControls?.skipToPrevious()

    /** Abre o app que está tocando. */
    fun open() {
        val c = active ?: return
        runCatching {
            val pi = c.sessionActivity
            if (pi != null) pi.send()
            else context.packageManager.getLaunchIntentForPackage(c.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let { context.startActivity(it) }
        }
    }

    /** Tela do sistema pra liberar o acesso às notificações (só o NeonGrid, se possível). */
    fun accessIntent(): Intent =
        if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Card de vidro na home: capa, música, artista, equalizador e controles. */
@Composable
fun MusicCard(
    np: NowPlaying,
    onOpen: () -> Unit,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val neon = LocalNeon.current
    val glow = np.color ?: neon.accent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .glass(neon, TileShape)
            .background(Brush.horizontalGradient(listOf(glow.copy(alpha = 0.16f), Color.Transparent)))
            .noRippleClickable(onOpen)
            .padding(10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(glow.copy(alpha = 0.25f)),
        ) {
            if (np.art != null) {
                Image(np.art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(46.dp))
            } else {
                NeonText("♪", color = glow, size = 20.sp, family = Sans)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Equalizer(np.playing, glow)
                Spacer(Modifier.width(6.dp))
                NeonText(np.title, color = neon.text, size = 14.sp, family = Sans, weight = FontWeight.SemiBold, maxLines = 1)
            }
            if (np.artist.isNotEmpty()) {
                NeonText(np.artist, color = neon.dim, size = 12.sp, family = Sans, maxLines = 1)
            }
        }
        Spacer(Modifier.width(4.dp))
        MediaButton(onPrev) { drawPrevNext(next = false, color = neon.text) }
        MediaButton(onPlayPause, big = true, tint = glow) {
            if (np.playing) drawPause(neon.bg) else drawPlay(neon.bg)
        }
        MediaButton(onNext) { drawPrevNext(next = true, color = neon.text) }
    }
}

@Composable
private fun Equalizer(playing: Boolean, color: Color) {
    val t = rememberInfiniteTransition(label = "eq")
    val bars = List(3) { i ->
        t.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(380 + i * 140, easing = LinearEasing), RepeatMode.Reverse),
            label = "eq$i",
        )
    }
    Canvas(Modifier.size(width = 12.dp, height = 11.dp)) {
        val bw = size.width / 5f
        bars.forEachIndexed { i, b ->
            val hgt = if (playing) size.height * b.value else size.height * 0.25f
            drawRoundRect(
                color,
                topLeft = Offset(i * bw * 2f, size.height - hgt),
                size = Size(bw, hgt),
                cornerRadius = CornerRadius(bw / 2f, bw / 2f),
            )
        }
    }
}

@Composable
private fun MediaButton(
    onClick: () -> Unit,
    big: Boolean = false,
    tint: Color? = null,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    val s = if (big) 38.dp else 34.dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(s)
            .clip(ChipShape)
            .then(if (tint != null) Modifier.background(tint) else Modifier)
            .noRippleClickable(onClick),
    ) {
        Canvas(Modifier.size(if (big) 14.dp else 13.dp), onDraw = draw)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlay(color: Color) {
    val p = Path().apply {
        moveTo(size.width * 0.18f, 0f)
        lineTo(size.width, size.height / 2f)
        lineTo(size.width * 0.18f, size.height)
        close()
    }
    drawPath(p, color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPause(color: Color) {
    val bw = size.width * 0.32f
    drawRoundRect(color, topLeft = Offset(size.width * 0.08f, 0f), size = Size(bw, size.height), cornerRadius = CornerRadius(2f, 2f))
    drawRoundRect(color, topLeft = Offset(size.width * 0.60f, 0f), size = Size(bw, size.height), cornerRadius = CornerRadius(2f, 2f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPrevNext(next: Boolean, color: Color) {
    val w = size.width
    val h = size.height
    val p = Path().apply {
        if (next) {
            moveTo(0f, 0f); lineTo(w * 0.78f, h / 2f); lineTo(0f, h); close()
        } else {
            moveTo(w, 0f); lineTo(w * 0.22f, h / 2f); lineTo(w, h); close()
        }
    }
    drawPath(p, color)
    val bx = if (next) w * 0.82f else 0f
    drawRect(color, topLeft = Offset(bx, 0f), size = Size(w * 0.18f, h))
}
