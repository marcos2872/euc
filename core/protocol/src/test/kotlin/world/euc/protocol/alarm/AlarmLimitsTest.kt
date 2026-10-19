package world.euc.protocol.alarm

import world.euc.protocol.model.WheelCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals

class AlarmLimitsTest {

    private val v11y = WheelCapabilities(
        settings = emptySet(),
        maxSpeedLimitKmh = 60.0,
        maxCurrentAmps = 58.0,
        maxTemperatureCelsius = 80.0,
    )

    @Test
    fun `faixa de tensão vem da série de células reportada pela roda`() {
        val limites = AlarmLimits.of(v11y, batterySeries = 20)

        assertEquals(60.0, limites.minVoltageVolts, "20 células × 3,0 V")
        assertEquals(90.0, limites.maxVoltageVolts, "20 × 4,2 V + folga de regen")
    }

    @Test
    fun `série diferente muda a faixa de tensão`() {
        val limites = AlarmLimits.of(v11y, batterySeries = 24)

        assertEquals(72.0, limites.minVoltageVolts)
        assertEquals(106.8, limites.maxVoltageVolts, 0.001)
    }

    @Test
    fun `sem série reportada assume 20 células`() {
        val limites = AlarmLimits.of(v11y, batterySeries = null)

        assertEquals(60.0, limites.minVoltageVolts)
    }

    @Test
    fun `alarmes acima do envelope da roda são derrubados`() {
        val exagerado = AlarmConfig(
            speedAlarm1Kmh = 70.0,
            speedAlarm2Kmh = 80.0,
            speedAlarm3Kmh = 90.0,
            currentAlarmAmps = 120.0,
            overvoltageVolts = 130.0,
            undervoltageVolts = 130.0,
            temperatureAlarmCelsius = 95.0,
        )

        val ajustado = exagerado.clampedTo(AlarmLimits.of(v11y, batterySeries = 20))

        assertEquals(60.0, ajustado.speedAlarm1Kmh)
        assertEquals(60.0, ajustado.speedAlarm2Kmh)
        assertEquals(60.0, ajustado.speedAlarm3Kmh)
        assertEquals(58.0, ajustado.currentAlarmAmps)
        assertEquals(90.0, ajustado.overvoltageVolts)
        assertEquals(90.0, ajustado.undervoltageVolts)
        assertEquals(80.0, ajustado.temperatureAlarmCelsius)
    }

    @Test
    fun `valores dentro do envelope ficam intocados`() {
        val sensato = AlarmConfig(
            speedAlarm1Kmh = 25.0,
            speedAlarm2Kmh = 30.0,
            speedAlarm3Kmh = 35.0,
            currentAlarmAmps = 40.0,
            overvoltageVolts = 86.0,
            undervoltageVolts = 66.0,
            temperatureAlarmCelsius = 70.0,
        )

        val ajustado = sensato.clampedTo(AlarmLimits.of(v11y, batterySeries = 20))

        assertEquals(sensato, ajustado)
    }

    @Test
    fun `sem roda conhecida a faixa é ampla e não trava nada`() {
        val doUsuario = AlarmConfig(
            speedAlarm1Kmh = 70.0,
            currentAlarmAmps = 110.0,
            temperatureAlarmCelsius = 95.0,
        )

        val ajustado = doUsuario.clampedTo(AlarmLimits.Unknown)

        assertEquals(doUsuario, ajustado)
    }

    @Test
    fun `outros ajustes não são afetados pelo clamp`() {
        val config = AlarmConfig(
            speedAlarm1Kmh = 99.0,
            preAlarmMarginKmh = 7.5,
            speedLimitMarginKmh = 4.0,
            batteryAlarmPercent = 20.0,
            safetyMarginAlarmPercent = 25.0,
            connectionLostAfterMillis = 9_000L,
            voiceEnabled = false,
            disabledTypes = setOf(AlarmType.SPEED_3),
        )

        val ajustado = config.clampedTo(AlarmLimits.of(v11y, batterySeries = 20))

        assertEquals(7.5, ajustado.preAlarmMarginKmh)
        assertEquals(4.0, ajustado.speedLimitMarginKmh)
        assertEquals(20.0, ajustado.batteryAlarmPercent)
        assertEquals(25.0, ajustado.safetyAlarmMargin())
        assertEquals(9_000L, ajustado.connectionLostAfterMillis)
        assertEquals(false, ajustado.voiceEnabled)
        assertEquals(setOf(AlarmType.SPEED_3), ajustado.disabledTypes)
    }

    private fun AlarmConfig.safetyAlarmMargin(): Double = safetyMarginAlarmPercent
}
