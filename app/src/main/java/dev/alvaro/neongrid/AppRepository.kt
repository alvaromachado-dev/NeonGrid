package dev.alvaro.neongrid

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
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
    /** Ícone monocromático (Android 13+), usado no modo de ícones neon. */
    val mono: ImageBitmap?,
    val searchKey: String,
)

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
    private val iconPx = (64 * context.resources.displayMetrics.density).toInt()

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
        return AppEntry(
            key = info.componentName.flattenToString(),
            label = label,
            packageName = info.componentName.packageName,
            component = info.componentName,
            user = user,
            icon = drawable.toBitmap(iconPx, iconPx).asImageBitmap(),
            mono = monochromeOf(drawable),
            searchKey = label.normalized(),
        )
    }

    private fun monochromeOf(drawable: Drawable): ImageBitmap? {
        if (Build.VERSION.SDK_INT < 33 || drawable !is AdaptiveIconDrawable) return null
        val mono = drawable.monochrome ?: return null
        return runCatching {
            AdaptiveIconDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT), mono)
                .toBitmap(iconPx, iconPx)
                .asImageBitmap()
        }.getOrNull()
    }

    fun launch(app: AppEntry) {
        launcherApps.startMainActivity(app.component, app.user, null, null)
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
