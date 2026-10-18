package world.euc.app.tour

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Fix de GPS já normalizado (sem depender do Android). */
data class GpsFix(
    val timeMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float? = null,
    val altitudeM: Double? = null,
    val speedKmh: Double? = null,
    val bearingDegrees: Float? = null,
)

/** Estado da roda no instante de um fix, já reduzido ao que o passeio guarda. */
data class TelemetrySnapshot(
    val speedKmh: Double? = null,
    val batteryPercent: Int? = null,
    val voltage: Double? = null,
    val current: Double? = null,
    val powerW: Double? = null,
    val temperatureC: Double? = null,
)

/**
 * Acumula os pontos de um passeio: distância, velocidade e posições.
 *
 * Puro (sem Android) para poder ser testado. Fixes com precisão pior que
 * [maxAccuracyMeters] são descartados, como faz o EUC World (≥ 25 m);
 * deslocamentos menores que [minMovementMeters] não somam distância
 * (ruído do GPS com a roda parada).
 */
class TourTrackBuilder(
    private val maxAccuracyMeters: Double = 25.0,
    private val minMovementMeters: Double = 1.0,
) {
    private val points = mutableListOf<TourPoint>()
    private var distanceKm = 0.0

    val isEmpty: Boolean get() = points.isEmpty()

    val pointCount: Int get() = points.size

    val currentDistanceKm: Double get() = distanceKm

    /**
     * Adiciona um fix. Devolve o ponto gravado, ou `null` quando o fix foi
     * descartado por precisão ruim.
     */
    fun add(fix: GpsFix, telemetry: TelemetrySnapshot): TourPoint? {
        val accuracy = fix.accuracyM?.toDouble()
        if (accuracy != null && accuracy > maxAccuracyMeters) return null

        val previous = points.lastOrNull()
        if (previous != null) {
            val meters = haversineMeters(
                previous.latitude,
                previous.longitude,
                fix.latitude,
                fix.longitude,
            )
            if (meters >= minMovementMeters) distanceKm += meters / 1000.0
        }

        val point = TourPoint(
            timeMs = fix.timeMs,
            latitude = fix.latitude,
            longitude = fix.longitude,
            // A velocidade da roda é mais fiel que a do GPS num monociclo.
            speedKmh = telemetry.speedKmh ?: fix.speedKmh ?: 0.0,
            altitudeM = fix.altitudeM,
            batteryPercent = telemetry.batteryPercent,
            voltage = telemetry.voltage,
            current = telemetry.current,
            powerW = telemetry.powerW,
            temperatureC = telemetry.temperatureC,
            distanceKm = distanceKm,
        )
        points += point
        return point
    }

    fun snapshot(): List<TourPoint> = points.toList()

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        /** Distância em metros entre dois pontos (fórmula de haversine). */
        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val deltaLat = Math.toRadians(lat2 - lat1)
            val deltaLon = Math.toRadians(lon2 - lon1)
            val a = sin(deltaLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(deltaLon / 2).pow(2)
            return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
        }
    }
}
