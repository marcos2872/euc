package world.euc.app.wheel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import world.euc.protocol.model.WheelTelemetry
import world.euc.protocol.model.WheelTemperatures

class LiveSeriesTest {

    @Test
    fun `acrescenta amostras e respeita a capacidade`() {
        var series = LiveSeries(capacity = 3)

        (1..5).forEach { index -> series = series.plus(index * 1_000L, index.toDouble()) }

        assertEquals(3, series.samples.size)
        assertEquals(listOf(3.0, 4.0, 5.0), series.samples.map { it.value })
        assertEquals(5_000L, series.samples.last().timeMs)
        assertEquals(5.0, series.latest)
    }

    @Test
    fun `valor ausente ou invalido nao entra na serie`() {
        var series = LiveSeries()

        series = series.plus(1_000L, null)
        series = series.plus(2_000L, Double.NaN)
        series = series.plus(3_000L, 10.0)

        assertEquals(1, series.samples.size)
        assertEquals(10.0, series.latest)
    }

    @Test
    fun `serie vazia nao tem ultimo valor`() {
        val series = LiveSeries()

        assertTrue(series.isEmpty)
        assertNull(series.latest)
    }

    @Test
    fun `historico guarda as series da telemetria`() {
        val history = LiveHistory().plus(
            WheelTelemetry(
                speedKmh = 25.0,
                currentAmps = 10.0,
                voltageVolts = 75.0,
                loadPercent = 40.0,
                temperatures = WheelTemperatures(mosfet = 42.0, motor = 50.0),
            ),
            timeMs = 1_000L,
        )

        assertEquals(25.0, history.speed.latest)
        assertEquals(10.0, history.current.latest)
        assertEquals(75.0, history.voltage.latest)
        assertEquals(750.0, history.power.latest, "potência = tensão × corrente")
        assertEquals(40.0, history.load.latest)
        assertEquals(42.0, history.temperature.latest, "usa a placa quando não há motor")
    }

    @Test
    fun `historico ignora campos que a roda nao reporta`() {
        val history = LiveHistory().plus(WheelTelemetry(speedKmh = 10.0), timeMs = 1_000L)

        assertEquals(10.0, history.speed.latest)
        assertNull(history.power.latest)
        assertTrue(history.voltage.isEmpty)
        assertTrue(history.temperature.isEmpty)
    }

    @Test
    fun `cada serie do enum vem do historico`() {
        val history = LiveHistory(
            speed = LiveSeries().plus(1_000L, 1.0),
            current = LiveSeries().plus(1_000L, 2.0),
            voltage = LiveSeries().plus(1_000L, 3.0),
            power = LiveSeries().plus(1_000L, 4.0),
            load = LiveSeries().plus(1_000L, 5.0),
            temperature = LiveSeries().plus(1_000L, 6.0),
        )

        assertEquals(1.0, LiveSeriesKind.SPEED.of(history).latest)
        assertEquals(2.0, LiveSeriesKind.CURRENT.of(history).latest)
        assertEquals(3.0, LiveSeriesKind.VOLTAGE.of(history).latest)
        assertEquals(4.0, LiveSeriesKind.POWER.of(history).latest)
        assertEquals(5.0, LiveSeriesKind.LOAD.of(history).latest)
        assertEquals(6.0, LiveSeriesKind.TEMPERATURE.of(history).latest)
    }
}
