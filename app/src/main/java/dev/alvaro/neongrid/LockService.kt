package dev.alvaro.neongrid

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat

/**
 * Serviço de acessibilidade do NeonGrid. Faz só duas coisas:
 * 1. bloquear a tela no toque duplo da home;
 * 2. desenhar a animação de desbloqueio por cima de qualquer tela (home ou app).
 * Não lê nada do que aparece na tela.
 */
class LockService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var armed = false
    private var overlay: UnlockOverlayView? = null

    private fun keyguardLocked() =
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    /** Com a tela acesa, confere o bloqueio a cada 30 ms até desbloquear. */
    private val poll = object : Runnable {
        override fun run() {
            if (!armed) return
            if (keyguardLocked()) {
                handler.postDelayed(this, 30)
                return
            }
            armed = false
            showUnlockAnimation()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    handler.removeCallbacks(poll)
                    armed = LauncherPrefs(applicationContext).settings.value.unlockAnim
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    handler.removeCallbacks(poll)
                    if (armed) handler.post(poll)
                }
            }
        }
    }

    override fun onServiceConnected() {
        instance = this
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun showUnlockAnimation() {
        if (overlay != null) return
        val wm = getSystemService(WindowManager::class.java) ?: return
        val accent = LauncherPrefs(applicationContext).settings.value.accent
        val view = UnlockOverlayView(this, accent.main.toArgb(), 0xFF8C87A8.toInt())
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        val added = runCatching { wm.addView(view, params) }.isSuccess
        if (!added) return
        overlay = view
        unlockListener?.invoke()
        val remove = {
            if (overlay === view) {
                runCatching { wm.removeView(view) }
                overlay = null
            }
        }
        view.play { remove() }
        // trava de segurança: nunca deixa a tela presa no preto
        handler.postDelayed({
            view.finish()
            remove()
        }, UnlockOverlayView.DURATION + 450)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        overlay?.let { v -> runCatching { getSystemService(WindowManager::class.java)?.removeView(v) } }
        overlay = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        @Volatile private var instance: LockService? = null

        /** Avisado quando a animação de desbloqueio começa (o launcher anima a entrada da home). */
        @Volatile var unlockListener: (() -> Unit)? = null

        fun isRunning(): Boolean = instance != null

        /** Bloqueia a tela. Retorna false se o serviço não estiver ativado. */
        fun lock(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = "${context.packageName}/${LockService::class.java.name}"
            val meShort = "${context.packageName}/.LockService"
            return enabled.split(':').any { it.equals(me, true) || it.equals(meShort, true) }
        }

        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
