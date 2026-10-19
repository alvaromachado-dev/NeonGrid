package dev.alvaro.neongrid

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    private lateinit var repo: AppRepository
    private lateinit var prefs: LauncherPrefs
    private lateinit var media: MediaRepository
    private var lockEnabled by mutableStateOf(false)
    private var musicEnabled by mutableStateOf(false)
    private var homeSignal by mutableIntStateOf(0)
    private var resumeSignal by mutableIntStateOf(0)
    private var stopSignal by mutableIntStateOf(0)

    // Animação de desbloqueio: "arma" quando a tela apaga e dispara ao desbloquear
    private var unlockSignal by mutableIntStateOf(0)
    private var unlockArmed by mutableStateOf(false)
    private var isResumedNow = false
    private var unlockedAt = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    unlockArmed = prefs.settings.value.unlockAnim
                    unlockedAt = 0L
                }
                Intent.ACTION_USER_PRESENT -> {
                    unlockedAt = SystemClock.elapsedRealtime()
                    if (isResumedNow) tryPlayUnlock()
                }
            }
        }
    }

    private fun tryPlayUnlock() {
        if (!unlockArmed) return
        val km = getSystemService(KeyguardManager::class.java)
        if (km?.isKeyguardLocked == true) return // ainda na tela de bloqueio: espera
        // se desbloqueou dentro de outro app e só voltou pra home bem depois, não anima
        val recent = unlockedAt == 0L || SystemClock.elapsedRealtime() - unlockedAt < 2_500
        unlockArmed = false
        if (recent) unlockSignal++
    }
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
        media = MediaRepository(applicationContext)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            val settings by prefs.settings.collectAsState()
            LaunchedEffect(settings.hideStatusBar) { applyStatusBar(settings.hideStatusBar) }
            NeonTheme(settings.accent) {
                LauncherRoot(
                    repo = repo,
                    prefs = prefs,
                    homeSignal = homeSignal,
                    resumeSignal = resumeSignal,
                    stopSignal = stopSignal,
                    unlockSignal = unlockSignal,
                    unlockArmed = unlockArmed,
                    media = media,
                    musicEnabled = musicEnabled,
                    lockEnabled = lockEnabled,
                    isDefaultHome = isDefaultHome,
                    onRequestDefault = ::requestDefaultHome,
                    onOpenNotifications = ::expandNotifications,
                )
            }
        }
    }

    /** Chamado quando o launcher volta a aparecer depois de um app (dispara o zoom de retorno). */
    override fun onPause() {
        super.onPause()
        isResumedNow = false
    }

    override fun onStop() {
        super.onStop()
        stopSignal++
        media.stop()
    }

    override fun onRestart() {
        super.onRestart()
        resumeSignal++
    }

    override fun onResume() {
        super.onResume()
        isDefaultHome = checkDefaultHome()
        applyStatusBar(prefs.settings.value.hideStatusBar)
        isResumedNow = true
        tryPlayUnlock()
        lockEnabled = LockService.isEnabled(this)
        musicEnabled = media.hasAccess()
        media.start()
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
        runCatching { unregisterReceiver(screenReceiver) }
        repo.stop()
        super.onDestroy()
    }
}
