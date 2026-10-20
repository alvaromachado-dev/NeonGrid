package dev.alvaro.neongrid

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

const val MAX_DOCK = 5

enum class IconStyle { NEON, ORIGINAL, HOLOGRAM }

enum class LaunchAnim { NEON, SYSTEM }

/** Clima da cidade: real (pela sua localização), sempre chovendo ou sempre limpo. */
enum class WeatherMode { REAL, RAIN, CLEAR }

/** Papel de parede do sistema acompanhando a cidade: não, só tela inicial, ou inicial + bloqueio. */
enum class WallpaperSync { OFF, HOME, HOME_LOCK }

const val FOLDER_PREFIX = "folder:"

fun isFolderToken(token: String) = token.startsWith(FOLDER_PREFIX)
fun folderIdOf(token: String) = token.removePrefix(FOLDER_PREFIX)

data class Folder(val id: String, val name: String, val apps: List<String>)

/** Onde um ícone arrastado vai cair, já em termos de "itens vizinhos" (não de posição na tela). */
sealed interface DropAction {
    /** Coloca na home antes de [anchor] (null = no fim). */
    data class HomeBefore(val anchor: String?) : DropAction
    /** Solta em cima de [target] na home: cria pasta ou entra na pasta. */
    data class HomeMerge(val target: String) : DropAction
    /** Coloca no dock antes de [anchor] (null = no fim). */
    data class DockBefore(val anchor: String?) : DropAction
}

data class LauncherSettings(
    val accent: Accent = Accent.CIANO,
    val iconStyle: IconStyle = IconStyle.NEON,
    val columns: Int = 4,
    val animatedBg: Boolean = true,
    val hideStatusBar: Boolean = true,
    val launchAnim: LaunchAnim = LaunchAnim.NEON,
    val unlockAnim: Boolean = true,
    val weatherMode: WeatherMode = WeatherMode.RAIN,
    val skyFollowsClock: Boolean = true,
    val wallpaperSync: WallpaperSync = WallpaperSync.OFF,
)

/** Guarda apps fixados, dock e configurações no próprio aparelho. */
class LauncherPrefs(context: Context) {

    private val sp = context.getSharedPreferences("neongrid", Context.MODE_PRIVATE)

    val home = MutableStateFlow(readList(KEY_HOME))
    val dock = MutableStateFlow(readList(KEY_DOCK))
    val settings = MutableStateFlow(readSettings())
    val folders = MutableStateFlow(readFolders())

    /** Quantas vezes cada app foi aberto pelo launcher (pra "Mais usados" e busca). */
    val usage = MutableStateFlow(readUsage())

    fun recordLaunch(key: String) {
        val m = usage.value.toMutableMap()
        m[key] = (m[key] ?: 0) + 1
        usage.value = m
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        sp.edit { putString(KEY_USAGE, o.toString()) }
    }

    private fun readUsage(): Map<String, Int> = runCatching {
        val o = JSONObject(sp.getString(KEY_USAGE, null) ?: "{}")
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

    /** Última hora em que o papel de parede do sistema foi atualizado (-1 = nunca). */
    var lastWallpaperHour: Int
        get() = sp.getInt(KEY_WP_HOUR, -1)
        set(v) = sp.edit { putInt(KEY_WP_HOUR, v) }

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

    fun setFolders(map: Map<String, Folder>) {
        folders.value = map
        val arr = JSONArray()
        map.values.forEach { f ->
            arr.put(JSONObject().put("id", f.id).put("name", f.name).put("apps", JSONArray(f.apps)))
        }
        sp.edit { putString(KEY_FOLDERS, arr.toString()) }
    }

    /** O app está na tela inicial (solto ou dentro de uma pasta)? */
    fun isOnHome(key: String): Boolean =
        key in home.value || folders.value.values.any { key in it.apps }

    fun folderOf(key: String): Folder? = folders.value.values.firstOrNull { key in it.apps }

    fun toggleHome(key: String) {
        val cur = home.value
        when {
            key in cur -> setHome(cur - key)
            folderOf(key) != null -> removeFromFolder(folderOf(key)!!.id, key, keepOnHome = false)
            else -> setHome(cur + key)
        }
    }

    /** Tira o app da pasta. Com [keepOnHome], ele vai pra home logo depois da pasta. */
    fun removeFromFolder(folderId: String, key: String, keepOnHome: Boolean = true) {
        val f = folders.value[folderId] ?: return
        val token = FOLDER_PREFIX + folderId
        val h = home.value.toMutableList()
        val fs = folders.value.toMutableMap()
        val rest = f.apps - key
        val at = h.indexOf(token)
        when {
            rest.isEmpty() -> { h.remove(token); fs.remove(folderId) }
            rest.size == 1 -> { if (at >= 0) h[at] = rest[0]; fs.remove(folderId) } // pasta de 1 app vira app
            else -> fs[folderId] = f.copy(apps = rest)
        }
        if (keepOnHome && key !in h) {
            val pos = h.indexOf(if (rest.size == 1) rest[0] else token)
            if (pos >= 0) h.add(pos + 1, key) else h.add(key)
        }
        setFolders(fs)
        setHome(h)
    }

    /** Desfaz a pasta: os apps voltam soltos pra home, no lugar dela. */
    fun dissolveFolder(folderId: String) {
        val f = folders.value[folderId] ?: return
        val h = home.value.toMutableList()
        val at = h.indexOf(FOLDER_PREFIX + folderId)
        val apps = f.apps.filter { it !in h }
        if (at >= 0) { h.removeAt(at); h.addAll(at, apps) } else h.addAll(apps)
        setFolders(folders.value - folderId)
        setHome(h)
    }

    /** Remove a pasta inteira (e os apps dela) da home. */
    fun removeFolder(folderId: String) {
        setHome(home.value - (FOLDER_PREFIX + folderId))
        setFolders(folders.value - folderId)
    }

    fun renameFolder(folderId: String, name: String) {
        val f = folders.value[folderId] ?: return
        val clean = name.trim().ifEmpty { f.name }
        setFolders(folders.value + (folderId to f.copy(name = clean)))
    }

    /**
     * Aplica um arrastar-e-soltar. [token] = app ou pasta arrastado, [fromDock] = de onde veio.
     * Retorna false se não deu (ex.: dock cheio, pasta indo pro dock).
     */
    fun applyDrop(token: String, fromDock: Boolean, action: DropAction, newFolderName: (List<String>) -> String): Boolean {
        val h = home.value.toMutableList()
        val d = dock.value.toMutableList()
        val fs = folders.value.toMutableMap()
        val isFolder = isFolderToken(token)

        fun removeSource() { if (fromDock) d.remove(token) else h.remove(token) }

        when (action) {
            is DropAction.HomeBefore -> {
                if (action.anchor == token) return false
                removeSource()
                h.remove(token) // evita duplicado se veio do dock e já estava na home
                val idx = action.anchor?.let { h.indexOf(it) }?.takeIf { it >= 0 } ?: h.size
                h.add(idx, token)
            }
            is DropAction.HomeMerge -> {
                if (isFolder || action.target == token) return false
                removeSource()
                h.remove(token)
                val at = h.indexOf(action.target)
                if (at < 0) return false
                if (isFolderToken(action.target)) {
                    val id = folderIdOf(action.target)
                    val f = fs[id] ?: return false
                    if (token !in f.apps) fs[id] = f.copy(apps = f.apps + token)
                } else {
                    val id = System.currentTimeMillis().toString(36)
                    val members = listOf(action.target, token)
                    fs[id] = Folder(id, newFolderName(members), members)
                    h[at] = FOLDER_PREFIX + id
                }
            }
            is DropAction.DockBefore -> {
                if (isFolder || action.anchor == token) return false
                if (!fromDock && token !in d && d.size >= MAX_DOCK) return false
                removeSource()
                d.remove(token)
                val idx = action.anchor?.let { d.indexOf(it) }?.takeIf { it >= 0 } ?: d.size
                d.add(idx, token)
            }
        }
        setFolders(fs)
        setHome(h)
        setDock(d)
        return true
    }

    /** Limpa apps desinstalados de home, dock e pastas. */
    fun prune(valid: Set<String>) {
        val fs = folders.value.mapValues { (_, f) -> f.copy(apps = f.apps.filter { it in valid }) }.toMutableMap()
        val h = mutableListOf<String>()
        home.value.forEach { t ->
            if (isFolderToken(t)) {
                val f = fs[folderIdOf(t)]
                when {
                    f == null || f.apps.isEmpty() -> fs.remove(folderIdOf(t))
                    f.apps.size == 1 -> { h += f.apps[0]; fs.remove(f.id) }
                    else -> h += t
                }
            } else if (t in valid) h += t
        }
        val d = dock.value.filter { it in valid }
        if (h != home.value) setHome(h)
        if (d != dock.value) setDock(d)
        if (fs != folders.value) setFolders(fs)
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
            putString(KEY_ICON_STYLE, s.iconStyle.name)
            putInt(KEY_COLUMNS, s.columns)
            putBoolean(KEY_ANIM, s.animatedBg)
            putBoolean(KEY_HIDE_BAR, s.hideStatusBar)
            putString(KEY_LAUNCH_ANIM, s.launchAnim.name)
            putBoolean(KEY_SKY_CLOCK, s.skyFollowsClock)
            putBoolean(KEY_UNLOCK_ANIM, s.unlockAnim)
            putString(KEY_WEATHER, s.weatherMode.name)
            putString(KEY_WP_SYNC, s.wallpaperSync.name)
        }
    }

    private fun readList(key: String): List<String> =
        sp.getString(key, null)?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()

    private fun readSettings(): LauncherSettings {
        val accent = runCatching { Accent.valueOf(sp.getString(KEY_ACCENT, null) ?: "") }
            .getOrDefault(Accent.CIANO)
        return LauncherSettings(
            accent = accent,
            iconStyle = runCatching { IconStyle.valueOf(sp.getString(KEY_ICON_STYLE, null) ?: "") }
                .getOrDefault(IconStyle.NEON),
            columns = sp.getInt(KEY_COLUMNS, 4).coerceIn(4, 5),
            animatedBg = sp.getBoolean(KEY_ANIM, true),
            hideStatusBar = sp.getBoolean(KEY_HIDE_BAR, true),
            launchAnim = runCatching { LaunchAnim.valueOf(sp.getString(KEY_LAUNCH_ANIM, null) ?: "") }
                .getOrDefault(LaunchAnim.NEON),
            skyFollowsClock = sp.getBoolean(KEY_SKY_CLOCK, true),
            unlockAnim = sp.getBoolean(KEY_UNLOCK_ANIM, true),
            weatherMode = runCatching { WeatherMode.valueOf(sp.getString(KEY_WEATHER, null) ?: "") }
                .getOrDefault(WeatherMode.RAIN),
            wallpaperSync = runCatching { WallpaperSync.valueOf(sp.getString(KEY_WP_SYNC, null) ?: "") }
                .getOrDefault(WallpaperSync.OFF),
        )
    }

    private fun readFolders(): Map<String, Folder> = runCatching {
        val arr = JSONArray(sp.getString(KEY_FOLDERS, null) ?: "[]")
        (0 until arr.length()).associate { i ->
            val o = arr.getJSONObject(i)
            val a = o.getJSONArray("apps")
            val id = o.getString("id")
            id to Folder(id, o.getString("name"), (0 until a.length()).map { a.getString(it) })
        }
    }.getOrDefault(emptyMap())

    private companion object {
        const val SEP = "|"
        const val KEY_HOME = "home"
        const val KEY_DOCK = "dock"
        const val KEY_INIT = "initialized"
        const val KEY_ACCENT = "accent"
        const val KEY_ICON_STYLE = "icon_style"
        const val KEY_COLUMNS = "columns"
        const val KEY_ANIM = "animated_bg"
        const val KEY_HIDE_BAR = "hide_status_bar"
        const val KEY_LAUNCH_ANIM = "launch_anim"
        const val KEY_SKY_CLOCK = "sky_follows_clock"
        const val KEY_WP_SYNC = "wallpaper_sync"
        const val KEY_WP_HOUR = "wallpaper_hour"
        const val KEY_FOLDERS = "folders"
        const val KEY_USAGE = "usage"
        const val KEY_UNLOCK_ANIM = "unlock_anim"
        const val KEY_WEATHER = "weather_mode"
    }
}
