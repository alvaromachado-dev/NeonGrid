package dev.alvaro.neongrid

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Serviço de acessibilidade usado SÓ pra bloquear a tela no toque duplo.
 * Não lê nada da tela (não pede eventos nem conteúdo de janelas).
 * É o jeito oficial de um launcher bloquear sem desativar a digital no próximo desbloqueio.
 */
class LockService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        @Volatile private var instance: LockService? = null

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
