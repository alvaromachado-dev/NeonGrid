package dev.alvaro.neongrid

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

enum class WeatherKind(val label: String) {
    CLEAR("céu limpo"),
    CLOUDY("nublado"),
    FOG("neblina"),
    DRIZZLE("garoa"),
    RAIN("chuva"),
    STORM("tempestade"),
    SNOW("neve"),
}

/** Como o clima aparece na cidade: tipo + quanto de nuvem (0–1). */
data class SceneWeather(val kind: WeatherKind, val clouds: Float) {
    val rain: Float
        get() = when (kind) {
            WeatherKind.DRIZZLE -> 0.35f
            WeatherKind.RAIN, WeatherKind.STORM -> 1f
            else -> 0f
        }

    companion object {
        val ALWAYS_RAIN = SceneWeather(WeatherKind.RAIN, 0.45f)
        val ALWAYS_CLEAR = SceneWeather(WeatherKind.CLEAR, 0f)
    }
}

data class WeatherNow(val kind: WeatherKind, val clouds: Float, val tempC: Int, val fetchedAt: Long) {
    val scene get() = SceneWeather(kind, clouds)
}

/**
 * Clima real via Open-Meteo (gratuito, sem conta). Usa a localização aproximada,
 * arredondada pra ~1 km, e consulta no máximo a cada 30 minutos.
 */
class WeatherRepository(private val context: Context) {

    private val sp = context.getSharedPreferences("neongrid_weather", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(readCache())
    val state: StateFlow<WeatherNow?> = _state.asStateFlow()
    private var running = false

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun refreshAsync(force: Boolean = false) {
        if (running) return
        scope.launch {
            running = true
            runCatching { refresh(force) }
            running = false
        }
    }

    private suspend fun refresh(force: Boolean) {
        if (!hasPermission()) return
        val last = _state.value?.fetchedAt ?: 0L
        if (!force && System.currentTimeMillis() - last < 30 * 60_000L) return
        val loc = location() ?: return
        val w = withContext(Dispatchers.IO) { fetch(loc.latitude, loc.longitude) } ?: return
        _state.value = w
        sp.edit {
            putString("kind", w.kind.name)
            putFloat("clouds", w.clouds)
            putInt("temp", w.tempC)
            putLong("at", w.fetchedAt)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun location(): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val known = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        if (known != null && System.currentTimeMillis() - known.time < 6 * 3_600_000L) return known
        if (Build.VERSION.SDK_INT >= 30 && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            val fresh = withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    runCatching {
                        lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, context.mainExecutor) { cont.resume(it) }
                    }.onFailure { cont.resume(null) }
                }
            }
            if (fresh != null) return fresh
        }
        return known
    }

    private fun fetch(lat: Double, lon: Double): WeatherNow? = runCatching {
        // arredonda a posição (~1 km) antes de mandar
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,cloud_cover&timezone=auto",
                lat, lon,
            )
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val cur = JSONObject(body).getJSONObject("current")
        val code = cur.getInt("weather_code")
        val clouds = (cur.optDouble("cloud_cover", 50.0) / 100.0).toFloat().coerceIn(0f, 1f)
        WeatherNow(kindOf(code), clouds, Math.round(cur.getDouble("temperature_2m")).toInt(), System.currentTimeMillis())
    }.getOrNull()

    /** Códigos WMO usados pelo Open-Meteo. */
    private fun kindOf(code: Int) = when (code) {
        0, 1 -> WeatherKind.CLEAR
        2, 3 -> WeatherKind.CLOUDY
        45, 48 -> WeatherKind.FOG
        in 51..57 -> WeatherKind.DRIZZLE
        in 61..67, in 80..82 -> WeatherKind.RAIN
        in 71..77, 85, 86 -> WeatherKind.SNOW
        in 95..99 -> WeatherKind.STORM
        else -> WeatherKind.CLOUDY
    }

    private fun readCache(): WeatherNow? = runCatching {
        val kind = WeatherKind.valueOf(sp.getString("kind", null) ?: return null)
        WeatherNow(kind, sp.getFloat("clouds", 0.5f), sp.getInt("temp", 0), sp.getLong("at", 0L))
    }.getOrNull()
}
