package world.euc.app.tour

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TourTrackBuilderTest {

    @Test
    fun `acumula distancia entre pontos consecutivos`() {
        val builder = TourTrackBuilder()

        builder.add(fix(latitude = -23.5000000, timeMs = 1_000), TelemetrySnapshot())
        builder.add(fix(latitude = -23.5010000, timeMs = 2_000), TelemetrySnapshot())

        // 0,001° de latitude ≈ 111,2 m
        assertEquals(0.111, builder.currentDistanceKm, 0.002)
        assertEquals(2, builder.pointCount)
    }

    @Test
    fun `descarta fix com precisao pior que o limite`() {
        val builder = TourTrackBuilder()

        val discarded = builder.add(
            fix(latitude = -23.5, timeMs = 1_000, accuracyM = 40f),
            TelemetrySnapshot(),
        )

        assertNull(discarded)
        assertEquals(0, builder.pointCount)
    }

    @Test
    fun `movimento menor que o minimo nao soma distancia`() {
        val builder = TourTrackBuilder()

        builder.add(fix(latitude = -23.5000000, timeMs = 1_000), TelemetrySnapshot())
        // ~0,5 m depois: ruído de GPS com a roda parada
        builder.add(fix(latitude = -23.5000045, timeMs = 2_000), TelemetrySnapshot())

        assertEquals(0.0, builder.currentDistanceKm)
        assertEquals(2, builder.pointCount, "o ponto é gravado, só não soma distância")
    }

    @Test
    fun `parado nao soma a oscilacao do GPS`() {
        val builder = TourTrackBuilder()

        builder.add(fix(latitude = -23.5000000, timeMs = 1_000, speedKmh = 0.0), TelemetrySnapshot(speedKmh = 0.0))
        // ~11 m de oscilação em um segundo, mas a roda está parada
        builder.add(fix(latitude = -23.5001000, timeMs = 2_000, speedKmh = 0.0), TelemetrySnapshot(speedKmh = 0.0))

        assertEquals(0.0, builder.currentDistanceKm)
        assertEquals(2, builder.pointCount)
    }

    @Test
    fun `usa a velocidade da roda em vez da do GPS`() {
        val builder = TourTrackBuilder()

        val point = builder.add(
            fix(latitude = -23.5, timeMs = 1_000, speedKmh = 25.0),
            TelemetrySnapshot(speedKmh = 20.0),
        )

        assertNotNull(point)
        assertEquals(20.0, point.speedKmh)
    }

    @Test
    fun `sem velocidade da roda cai para a do GPS`() {
        val builder = TourTrackBuilder()

        val point = builder.add(
            fix(latitude = -23.5, timeMs = 1_000, speedKmh = 25.0),
            TelemetrySnapshot(),
        )

        assertEquals(25.0, point?.speedKmh)
    }

    @Test
    fun `ponto carrega a telemetria do instante`() {
        val builder = TourTrackBuilder()

        val point = builder.add(
            fix(latitude = -23.5, timeMs = 1_000),
            TelemetrySnapshot(
                speedKmh = 30.0,
                batteryPercent = 77,
                voltage = 75.5,
                current = 12.5,
                powerW = 943.75,
                temperatureC = 42.0,
            ),
        )

        assertNotNull(point)
        assertEquals(77, point.batteryPercent)
        assertEquals(75.5, point.voltage)
        assertEquals(12.5, point.current)
        assertEquals(943.75, point.powerW)
        assertEquals(42.0, point.temperatureC)
    }

    @Test
    fun `distancia do ponto e a acumulada`() {
        val builder = TourTrackBuilder()

        builder.add(fix(latitude = -23.5000000, timeMs = 1_000), TelemetrySnapshot())
        val second = builder.add(fix(latitude = -23.5010000, timeMs = 2_000), TelemetrySnapshot())

        assertEquals(builder.currentDistanceKm, second?.distanceKm)
    }

    private fun fix(
        latitude: Double,
        timeMs: Long,
        longitude: Double = -46.5,
        accuracyM: Float? = 5f,
        speedKmh: Double? = null,
    ) = GpsFix(
        timeMs = timeMs,
        latitude = latitude,
        longitude = longitude,
        accuracyM = accuracyM,
        speedKmh = speedKmh,
    )
}
