package world.euc.app.tour

/** Velocidade acima da qual o tempo conta como "em movimento". */
private const val RIDING_SPEED_THRESHOLD_KMH = 1.0

/** Uma amostra gravada do passeio: posição do GPS + estado da roda no instante. */
data class TourPoint(
    val timeMs: Long,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double,
    val altitudeM: Double? = null,
    val batteryPercent: Int? = null,
    val voltage: Double? = null,
    val current: Double? = null,
    val powerW: Double? = null,
    val temperatureC: Double? = null,
    /** Distância acumulada desde o início do passeio, em km. */
    val distanceKm: Double = 0.0,
)

/** Passeio completo, com todos os pontos gravados. */
data class Tour(
    val id: String,
    val name: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val wheelModel: String?,
    val points: List<TourPoint>,
) {
    val distanceKm: Double get() = points.lastOrNull()?.distanceKm ?: 0.0

    val durationMs: Long get() = (endedAtMs - startedAtMs).coerceAtLeast(0L)

    val maxSpeedKmh: Double get() = points.maxOfOrNull { it.speedKmh } ?: 0.0

    val avgSpeedKmh: Double get() = averageSpeed(distanceKm, durationMs)

    /** Tempo em que a roda esteve de fato se movendo. */
    val ridingDurationMs: Long get() = ridingDurationMsOf(points)

    val avgRidingSpeedKmh: Double get() = averageSpeed(distanceKm, ridingDurationMs)

    val startBatteryPercent: Int? get() = points.firstNotNullOfOrNull { it.batteryPercent }

    val endBatteryPercent: Int? get() = points.asReversed().firstNotNullOfOrNull { it.batteryPercent }

    val avgPowerW: Double? get() = points.mapNotNull { it.powerW }.averageOrNull()

    val maxPowerW: Double? get() = points.mapNotNull { it.powerW }.maxOrNull()

    val maxCurrentAmps: Double? get() = points.mapNotNull { it.current }.maxOrNull()

    val maxTemperatureC: Double? get() = points.mapNotNull { it.temperatureC }.maxOrNull()

    /** Amplitude vertical (máx − mín) das altitudes gravadas. */
    val altitudeRangeM: Double?
        get() {
            val altitudes = points.mapNotNull { it.altitudeM }
            if (altitudes.isEmpty()) return null
            return altitudes.max() - altitudes.min()
        }

    /** Resumo sem os pontos, para listagens. */
    fun summary(): TourSummary = TourSummary(
        id = id,
        name = name,
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
        distanceKm = distanceKm,
        maxSpeedKmh = maxSpeedKmh,
        wheelModel = wheelModel,
        pointCount = points.size,
    )

    companion object {
        fun averageSpeed(distanceKm: Double, durationMs: Long): Double =
            if (durationMs <= 0) 0.0 else distanceKm * 3_600_000.0 / durationMs
    }
}

/** Cabeçalho de um passeio gravado — o suficiente para a lista, sem os pontos. */
data class TourSummary(
    val id: String,
    val name: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val distanceKm: Double,
    val maxSpeedKmh: Double,
    val wheelModel: String?,
    val pointCount: Int,
) {
    val durationMs: Long get() = (endedAtMs - startedAtMs).coerceAtLeast(0L)

    val avgSpeedKmh: Double get() = Tour.averageSpeed(distanceKm, durationMs)
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

/**
 * Tempo em que a roda esteve de fato se movendo: soma os intervalos entre
 * pontos consecutivos em que a velocidade passou de [RIDING_SPEED_THRESHOLD_KMH].
 */
internal fun ridingDurationMsOf(points: List<TourPoint>): Long {
    var total = 0L
    for (index in 1 until points.size) {
        if (points[index - 1].speedKmh > RIDING_SPEED_THRESHOLD_KMH) {
            total += points[index].timeMs - points[index - 1].timeMs
        }
    }
    return total
}
