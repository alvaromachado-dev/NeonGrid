package dev.alvaro.neongrid

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow

const val MAX_DOCK = 5

data class LauncherSettings(
    val accent: Accent = Accent.CIANO,
    val neonIcons: Boolean = false,
    val columns: Int = 4,
    val animatedBg: Boolean = true,
    val hideStatusBar: Boolean = true,
)

/** Guarda apps fixados, dock e configurações no próprio aparelho. */
class LauncherPrefs(context: Context) {

    private val sp = context.getSharedPreferences("neongrid", Context.MODE_PRIVATE)

    val home = MutableStateFlow(readList(KEY_HOME))
    val dock = MutableStateFlow(readList(KEY_DOCK))
    val settings = MutableStateFlow(readSettings())

    val initialized: Boolean get() = sp.getBoolean(KEY_INIT, false)

    fun markInitialized() = sp.edit { putBoolean(KEY_INIT, true) }

    fun setHome(keys: List<String>) {
        home.value = keys
        sp.edit { putString(KEY_HOME, keys.joinToString(SEP)) }
    }

    fun setDock(keys: List<String>) {
        val trimmed = keys.take(MAX_DOCK)
        dock.value = trimmed
        sp.edit { putString(KEY_DOCK, trimmed.joinToString(SEP)) }
    }

    fun toggleHome(key: String) {
        val cur = home.value
        setHome(if (key in cur) cur - key else cur + key)
    }

    /** Retorna false se o dock já está cheio. */
    fun toggleDock(key: String): Boolean {
        val cur = dock.value
        if (key in cur) {
            setDock(cur - key)
            return true
        }
        if (cur.size >= MAX_DOCK) return false
        setDock(cur + key)
        return true
    }

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        val s = transform(settings.value)
        settings.value = s
        sp.edit {
            putString(KEY_ACCENT, s.accent.name)
            putBoolean(KEY_NEON_ICONS, s.neonIcons)
            putInt(KEY_COLUMNS, s.columns)
            putBoolean(KEY_ANIM, s.animatedBg)
            putBoolean(KEY_HIDE_BAR, s.hideStatusBar)
        }
    }

    private fun readList(key: String): List<String> =
        sp.getString(key, null)?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()

    private fun readSettings(): LauncherSettings {
        val accent = runCatching { Accent.valueOf(sp.getString(KEY_ACCENT, null) ?: "") }
            .getOrDefault(Accent.CIANO)
        return LauncherSettings(
            accent = accent,
            neonIcons = sp.getBoolean(KEY_NEON_ICONS, false),
            columns = sp.getInt(KEY_COLUMNS, 4).coerceIn(4, 5),
            animatedBg = sp.getBoolean(KEY_ANIM, true),
            hideStatusBar = sp.getBoolean(KEY_HIDE_BAR, true),
        )
    }

    private companion object {
        const val SEP = "|"
        const val KEY_HOME = "home"
        const val KEY_DOCK = "dock"
        const val KEY_INIT = "initialized"
        const val KEY_ACCENT = "accent"
        const val KEY_NEON_ICONS = "neon_icons"
        const val KEY_COLUMNS = "columns"
        const val KEY_ANIM = "animated_bg"
        const val KEY_HIDE_BAR = "hide_status_bar"
    }
}
