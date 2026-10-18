package world.euc.protocol.alarm

import world.euc.protocol.model.WheelTelemetry
import world.euc.protocol.model.WheelTemperatures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlarmEngineTest {

    private val config = AlarmConfig(
        speedAlarm1Kmh = 25.0,
        speedAlarm2Kmh = 30.0,
        speedAlarm3Kmh = 35.0,
        preAlarmMarginKmh = 2.0,
        speedLimitMarginKmh = 2.0,
        currentAlarmAmps = 40.0,
        temperatureAlarmCelsius = 70.0,
        batteryAlarmPercent = 15.0,
        safetyMarginAlarmPercent = 20.0,
    )

    @Test
    fun `velocidade abaixo do primeiro nível não dispara alarme`() {
        val events = AlarmEngine().evaluate(telemetry(speedKmh = 20.0), config, nowMillis = 0)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `velocidade no segundo nível dispara apenas o alarme correspondente`() {
        val events = AlarmEngine().evaluate(telemetry(speedKmh = 31.0), config, nowMillis = 0)

        assertEquals(listOf(AlarmType.SPEED_2), events.map { it.type })
        assertEquals(AlarmPriority.MEDIUM, events.first().priority)
    }

    @Test
    fun `velocidade no terceiro nível tem prioridade alta`() {
        val events = AlarmEngine().evaluate(telemetry(speedKmh = 36.0), config, nowMillis = 0)

        assertEquals(listOf(AlarmType.SPEED_3), events.map { it.type })
        assertEquals(AlarmPriority.HIGH, events.first().priority)
    }

    @Test
    fun `aproximação do primeiro nível dispara aviso antecipado`() {
        val events = AlarmEngine().evaluate(telemetry(speedKmh = 23.5), config, nowMillis = 0)

        assertEquals(listOf(AlarmType.SPEED_PRE_1), events.map { it.type })
    }

    @Test
    fun `aproximação do segundo nível tem precedência sobre o primeiro`() {
        // Com margem larga, o nível 2 fica próximo sem o nível 1 ter sido atingido.
        val wideMargin = config.copy(preAlarmMarginKmh = 6.0)

        val events = AlarmEngine().evaluate(telemetry(speedKmh = 24.5), wideMargin, nowMillis = 0)

        assertEquals(listOf(AlarmType.SPEED_PRE_2), events.map { it.type })
    }

    @Test
    fun `alarme de limite de velocidade usa o limite reportado pela roda`() {
        val events = AlarmEngine().evaluate(
            telemetry(speedKmh = 29.0, speedLimitKmh = 30.0),
            config,
            nowMillis = 0,
        )

        assertTrue(events.any { it.type == AlarmType.SPEED_LIMIT })
    }

    @Test
    fun `cooldown impede repetição e rearma quando a condição desaparece`() {
        val engine = AlarmEngine(cooldownMillis = 10_000)

        val first = engine.evaluate(telemetry(speedKmh = 31.0), config, nowMillis = 0)
        val repeated = engine.evaluate(telemetry(speedKmh = 31.0), config, nowMillis = 1_000)
        val afterClear = engine.evaluate(telemetry(speedKmh = 10.0), config, nowMillis = 2_000)
        val again = engine.evaluate(telemetry(speedKmh = 31.0), config, nowMillis = 3_000)

        assertEquals(1, first.size)
        assertTrue(repeated.isEmpty())
        assertTrue(afterClear.isEmpty())
        assertEquals(1, again.size)
    }

    @Test
    fun `temperatura alta dispara alarme com o valor do sensor mais quente`() {
        val events = AlarmEngine().evaluate(
            telemetry(temperatures = WheelTemperatures(motor = 72.0, board = 45.0)),
            config,
            nowMillis = 0,
        )

        val temperature = events.single { it.type == AlarmType.TEMPERATURE }
        assertEquals(72.0, temperature.value!!, 0.001)
    }

    @Test
    fun `bateria baixa e margem de segurança disparam alarmes`() {
        val events = AlarmEngine().evaluate(
            telemetry(batteryPercent = 12.0, safetyMarginPercent = 15.0),
            config,
            nowMillis = 0,
        )

        assertEquals(
            setOf(AlarmType.BATTERY, AlarmType.SAFETY_MARGIN),
            events.map { it.type }.toSet(),
        )
    }

    @Test
    fun `corrente de pico considera o valor absoluto`() {
        val events = AlarmEngine().evaluate(telemetry(currentAmps = -45.0), config, nowMillis = 0)

        assertEquals(listOf(AlarmType.CURRENT), events.map { it.type })
    }

    @Test
    fun `tensões só alarmam quando limites configurados`() {
        val desativado = AlarmEngine().evaluate(telemetry(voltageVolts = 40.0), config, nowMillis = 0)
        assertTrue(desativado.isEmpty())

        val ativado = AlarmEngine().evaluate(
            telemetry(voltageVolts = 40.0),
            config.copy(undervoltageVolts = 60.0),
            nowMillis = 0,
        )
        assertEquals(listOf(AlarmType.UNDERVOLTAGE), ativado.map { it.type })
    }

    @Test
    fun `alarmes desligados não produzem eventos`() {
        val events = AlarmEngine().evaluate(
            telemetry(speedKmh = 50.0, batteryPercent = 5.0),
            config.copy(enabled = false),
            nowMillis = 0,
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun `perda de conexão dispara após o tempo configurado e respeita cooldown`() {
        val engine = AlarmEngine(cooldownMillis = 10_000)

        val early = engine.evaluateConnection(lastTelemetryAtMillis = 0, config, nowMillis = 10_000)
        val late = engine.evaluateConnection(lastTelemetryAtMillis = 1_000, config, nowMillis = 8_000)
        val repeated = engine.evaluateConnection(lastTelemetryAtMillis = 1_000, config, nowMillis = 9_000)

        assertEquals(null, early)
        assertEquals(AlarmType.CONNECTION_LOST, late?.type)
        assertEquals(null, repeated)
    }

    private fun telemetry(
        speedKmh: Double? = null,
        speedLimitKmh: Double? = null,
        currentAmps: Double? = null,
        voltageVolts: Double? = null,
        batteryPercent: Double? = null,
        safetyMarginPercent: Double? = null,
        temperatures: WheelTemperatures? = null,
    ) = WheelTelemetry(
        speedKmh = speedKmh,
        speedLimitKmh = speedLimitKmh,
        currentAmps = currentAmps,
        voltageVolts = voltageVolts,
        batteryPercent = batteryPercent,
        safetyMarginPercent = safetyMarginPercent,
        temperatures = temperatures,
    )
}
