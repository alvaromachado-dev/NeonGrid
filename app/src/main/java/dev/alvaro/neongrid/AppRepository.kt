package dev.alvaro.neongrid

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.MediaStore
import android.view.View
import android.window.SplashScreen
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Normalizer

@Immutable
data class AppEntry(
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    /** Cor principal do ícone em versão neon; null se o ícone não tem cor marcante. */
    val glow: Color?,
    val searchKey: String,
) {
    /** Letra usada no índice A–Z da gaveta ('#' pra números e símbolos). */
    val indexLetter: Char
        get() = searchKey.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'
}

/** Guarda onde fica o ícone que acabou de ser tocado, pra animação de abertura sair dele. */
object LaunchSource {
    @Volatile private var rect: Rect? = null
    fun set(r: Rect?) { rect = r }
    fun consume(): Rect? = rect.also { rect = null }
}

/** Remove acentos e deixa minúsculo: "Câmera" -> "camera". */
fun String.normalized(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()

/**
 * Lê a lista de apps instalados via LauncherApps (a API oficial para launchers)
 * e se atualiza sozinha quando algo é instalado, removido ou atualizado.
 */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val iconPx = (60 * context.resources.displayMetrics.density).toInt()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private var loadJob: Job? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() {
        launcherApps.unregisterCallback(callback)
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = scope.launch { _apps.value = load() }
    }

    private fun load(): List<AppEntry> {
        val user = Process.myUserHandle()
        return launcherApps.getActivityList(null, user)
            .filter { it.componentName.packageName != context.packageName }
            .mapNotNull { info -> runCatching { toEntry(info, user) }.getOrNull() }
            .sortedBy { it.label.normalized() }
    }

    private fun toEntry(info: LauncherActivityInfo, user: UserHandle): AppEntry {
        val drawable = info.getIcon(0)
        val label = info.label.toString()
        val bitmap = drawable.toBitmap(iconPx, iconPx)
        return AppEntry(
            key = info.componentName.flattenToString(),
            label = label,
            packageName = info.componentName.packageName,
            component = info.componentName,
            user = user,
            icon = bitmap.asImageBitmap(),
            glow = neonColorOf(bitmap),
            searchKey = label.normalized().trim(),
        )
    }

    /** Pega a cor mais marcante do ícone e "acende": saturação alta e brilho máximo. */
    private fun neonColorOf(bitmap: Bitmap): Color? = runCatching {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val swatch = palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.dominantSwatch
            ?: return@runCatching null
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(swatch.rgb, hsv)
        if (hsv[1] < 0.2f) return@runCatching null // branco, preto ou cinza: usa a cor do tema
        hsv[1] = hsv[1].coerceAtLeast(0.75f)
        hsv[2] = 1f
        Color(android.graphics.Color.HSVToColor(hsv))
    }.getOrNull()

    /**
     * Abre o app "crescendo" a partir do ícone tocado (estilo iPhone).
     * [source] = posição do ícone na janela do launcher; [anchor] = view raiz do launcher.
     * No Android 13+ também pede a tela de abertura sem o ícone no meio (só a cor do app).
     */
    fun launch(app: AppEntry, source: Rect? = null, anchor: View? = null) {
        val opts = if (source != null && anchor != null && !source.isEmpty) {
            ActivityOptions.makeScaleUpAnimation(anchor, source.left, source.top, source.width(), source.height())
        } else {
            ActivityOptions.makeBasic()
        }
        if (Build.VERSION.SDK_INT >= 33) {
            opts.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR)
        }
        val screenBounds = source?.let { r ->
            val loc = IntArray(2)
            anchor?.getLocationOnScreen(loc)
            Rect(r).apply { offset(loc[0], loc[1]) }
        }
        launcherApps.startMainActivity(app.component, app.user, screenBounds, opts.toBundle())
    }

    fun openInfo(app: AppEntry) {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }

    fun uninstall(app: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Dock inicial: telefone, mensagens, câmera e navegador padrão do aparelho. */
    @Suppress("DEPRECATION")
    fun defaultDock(apps: List<AppEntry>): List<String> {
        val pm = context.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
        )
        return intents.mapNotNull { intent ->
            val pkg = runCatching {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            }.getOrNull()
            if (pkg == null || pkg == "android") null
            else apps.firstOrNull { it.packageName == pkg }?.key
        }.distinct()
    }
}
