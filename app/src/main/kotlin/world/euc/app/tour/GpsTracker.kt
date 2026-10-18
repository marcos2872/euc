package world.euc.app.tour

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper

/**
 * Fonte de posição para a gravação (1 Hz).
 *
 * Usa **os dois provedores**: GPS (preciso, precisa de céu aberto) e rede
 * (aproximado, funciona dentro de casa). O fix de GPS tem prioridade —
 * enquanto ele estiver fresco, o de rede é ignorado, para não piorar a
 * trilha. É o GPS que interessa para o percurso; o de rede serve para o app
 * não ficar "sem sinal" em locais fechados e para posicionar o mapa.
 *
 * O horário do fix é o **do relógio do aparelho** no recebimento: o horário
 * que o chip de GPS informa pode estar errado antes de sincronizar (e isso
 * fazia um fix válido parecer velho).
 */
class GpsTracker(private val context: Context) {

    private val manager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var users = 0
    private var registered = false

    /** Último fix válido recebido (null enquanto não houver sinal). */
    @Volatile
    var lastFix: GpsFix? = null
        private set

    /** Localização ligada no aparelho (qualquer provedor)? */
    val locationEnabled: Boolean get() = gpsEnabled || networkEnabled

    /** O GPS do aparelho está ligado? */
    val gpsEnabled: Boolean get() = isProviderEnabled(LocationManager.GPS_PROVIDER)

    private val networkEnabled: Boolean get() = isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    /** Há interessados com os provedores registrados? */
    val isRegistered: Boolean get() = registered

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

    /**
     * Registra os provedores se ainda não estiverem (o usuário pode ter
     * ligado a localização depois de começar a gravar).
     */
    fun ensureRegistered() {
        if (users > 0 && !registered && locationEnabled) startUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startUpdates() {
        runCatching {
            if (gpsEnabled) {
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    UPDATE_INTERVAL_MS,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            }
            if (networkEnabled) {
                manager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    UPDATE_INTERVAL_MS,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            }
            registered = gpsEnabled || networkEnabled

            // Último fix conhecido, se ainda for recente (rede primeiro: o GPS
            // sobrescreve logo abaixo por ter prioridade).
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).forEach { provider ->
                if (!isProviderEnabled(provider)) return@forEach
                manager.getLastKnownLocation(provider)?.let { location ->
                    if (System.currentTimeMillis() - location.time <= LAST_KNOWN_MAX_AGE_MS) {
                        accept(location)
                    }
                }
            }
        }
    }

    private fun stopUpdates() {
        runCatching { manager.removeUpdates(listener) }
        registered = false
    }

    private fun accept(location: Location) {
        val fromGps = location.provider == LocationManager.GPS_PROVIDER
        val current = lastFix
        // Fix de rede não substitui um fix de GPS fresco.
        if (!fromGps && current != null && current.fromGps && !current.isStale()) return

        lastFix = GpsFix(
            timeMs = System.currentTimeMillis(),
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyM = location.accuracy.takeIf { location.hasAccuracy() },
            altitudeM = location.altitude.takeIf { location.hasAltitude() },
            speedKmh = (location.speed * 3.6).takeIf { location.hasSpeed() },
            bearingDegrees = location.bearing.takeIf { location.hasBearing() },
            fromGps = fromGps,
        )
    }

    private fun isProviderEnabled(provider: String): Boolean =
        runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)

    private companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
        const val LAST_KNOWN_MAX_AGE_MS = 30_000L
    }
}
