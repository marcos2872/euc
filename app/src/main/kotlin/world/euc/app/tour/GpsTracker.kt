package world.euc.app.tour

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper

/**
 * Fonte de posição por GPS (1 Hz), como o EUC World: só o provedor GPS,
 * sem Fused/Google Play Services.
 *
 * Compartilhada por quem precisa de posição (gravação de passeio e log CSV)
 * através de contagem de uso: o GPS só fica ligado enquanto há interessados.
 */
class GpsTracker(private val context: Context) {

    private val manager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var users = 0

    /** Último fix válido recebido (null enquanto não houver sinal). */
    @Volatile
    var lastFix: GpsFix? = null
        private set

    /** O GPS do aparelho está ligado? */
    val providerEnabled: Boolean
        get() = runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)

    private val listener = LocationListener { location -> accept(location) }

    fun acquire() {
        users += 1
        if (users == 1) startUpdates()
    }

    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) stopUpdates()
    }

    /** Esquece o último fix (ao começar um passeio novo). */
    fun reset() {
        lastFix = null
    }

    @SuppressLint("MissingPermission")
    private fun startUpdates() {
        if (!providerEnabled) return
        runCatching {
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MS,
                0f,
                listener,
                Looper.getMainLooper(),
            )
            // Aproveita a última posição conhecida, se ainda for recente.
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { location ->
                if (System.currentTimeMillis() - location.time <= LAST_KNOWN_MAX_AGE_MS) {
                    accept(location)
                }
            }
        }
    }

    private fun stopUpdates() {
        runCatching { manager.removeUpdates(listener) }
    }

    private fun accept(location: Location) {
        lastFix = GpsFix(
            timeMs = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyM = location.accuracy.takeIf { location.hasAccuracy() },
            altitudeM = location.altitude.takeIf { location.hasAltitude() },
            speedKmh = (location.speed * 3.6).takeIf { location.hasSpeed() },
            bearingDegrees = location.bearing.takeIf { location.hasBearing() },
        )
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
        const val LAST_KNOWN_MAX_AGE_MS = 30_000L
    }
}
