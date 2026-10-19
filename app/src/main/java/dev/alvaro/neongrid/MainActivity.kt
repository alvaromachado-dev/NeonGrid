package dev.alvaro.neongrid

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    private lateinit var repo: AppRepository
    private lateinit var prefs: LauncherPrefs
    private var homeSignal by mutableIntStateOf(0)
    private var isDefaultHome by mutableStateOf(true)
    private var roleRequestedAt = 0L

    /**
     * Resultado do diálogo "Definir NeonGrid como app de início?".
     * Se o sistema recusou na hora (sem mostrar diálogo), cai pras Configurações.
     */
    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefaultHome = checkDefaultHome()
        val instant = SystemClock.elapsedRealtime() - roleRequestedAt < 600
        if (!isDefaultHome && instant) openHomeSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        repo = AppRepository(applicationContext)
        prefs = LauncherPrefs(applicationContext)
        repo.start()

        setContent {
            val settings by prefs.settings.collectAsState()
            LaunchedEffect(settings.hideStatusBar) { applyStatusBar(settings.hideStatusBar) }
            NeonTheme(settings.accent) {
                LauncherRoot(
                    repo = repo,
                    prefs = prefs,
                    homeSignal = homeSignal,
                    isDefaultHome = isDefaultHome,
                    onRequestDefault = ::requestDefaultHome,
                    onOpenNotifications = ::expandNotifications,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isDefaultHome = checkDefaultHome()
        applyStatusBar(prefs.settings.value.hideStatusBar)
    }

    /** Esconde a barra de status só nesta tela; um deslize a partir da borda mostra ela por instantes. */
    private fun applyStatusBar(hide: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hide) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    /** Abre o painel de notificações (o mesmo truque usado por launchers como o Lawnchair). */
    @SuppressLint("WrongConstant", "PrivateApi")
    private fun expandNotifications() {
        runCatching {
            val service = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(service)
        }.onFailure {
            // Se o fabricante bloquear, ao menos mostra a barra pra puxar manualmente
            WindowCompat.getInsetsController(window, window.decorView)
                .show(WindowInsetsCompat.Type.statusBars())
        }
    }

    private fun checkDefaultHome(): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return rm.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        @Suppress("DEPRECATION")
        val res = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        return res?.activityInfo?.packageName == packageName
    }

    /** Abre o diálogo oficial do Android; se não der, abre a tela de app de início. */
    private fun requestDefaultHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                roleRequestedAt = SystemClock.elapsedRealtime()
                val launched = runCatching {
                    roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                }.isSuccess
                if (launched) return
            }
        }
        openHomeSettings()
    }

    private fun openHomeSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeSignal++
    }

    override fun onDestroy() {
        repo.stop()
        super.onDestroy()
    }
}
