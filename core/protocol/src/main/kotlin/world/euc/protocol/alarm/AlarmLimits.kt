package world.euc.protocol.alarm

import world.euc.protocol.model.WheelCapabilities

/**
 * Faixas válidas dos alarmes, derivadas do que a roda suporta.
 *
 * A ideia é simples: um alarme acima do envelope da roda nunca dispararia (ou
 * dispararia tarde demais), então o app nem deixa configurá-lo assim.
 */
data class AlarmLimits(
    /** Velocidade máxima da roda (top speed oficial do modelo). */
    val maxSpeedKmh: Double,
    /** Corrente de pico que o modelo suporta. */
    val maxCurrentAmps: Double,
    /** Tensão da bateria cheia (nº de células em série × 4,2 V) + folga de regen. */
    val maxVoltageVolts: Double,
    /** Tensão da bateria vazia (nº de células × 3,0 V) — piso do alarme. */
    val minVoltageVolts: Double,
    /** Temperatura máxima segura para alarme. */
    val maxTemperatureCelsius: Double,
) {
    companion object {
        /** Tensão de uma célula cheia (Li-ion). */
        const val CELL_FULL_VOLTS = 4.2

        /** Tensão de uma célula vazia (corte conservador). */
        const val CELL_EMPTY_VOLTS = 3.0

        /** Série assumida quando a roda não reporta (20S = V11/V11y). */
        const val DEFAULT_SERIES = 20

        /** Folga acima da bateria cheia para o alarme de sobretensão (regen). */
        const val OVERVOLTAGE_HEADROOM_VOLTS = 6.0

        /**
         * Limites do modelo. [batterySeries] vem da própria roda (ex.: 20 na
         * V11y) — quando ausente, assume [DEFAULT_SERIES].
         */
        fun of(capabilities: WheelCapabilities, batterySeries: Int? = null): AlarmLimits {
            val series = (batterySeries ?: DEFAULT_SERIES).coerceAtLeast(1)
            return AlarmLimits(
                maxSpeedKmh = capabilities.maxSpeedLimitKmh,
                maxCurrentAmps = capabilities.maxCurrentAmps,
                maxVoltageVolts = series * CELL_FULL_VOLTS + OVERVOLTAGE_HEADROOM_VOLTS,
                minVoltageVolts = series * CELL_EMPTY_VOLTS,
                maxTemperatureCelsius = capabilities.maxTemperatureCelsius,
            )
        }

        /** Sem roda conhecida: faixa ampla, sem travar nada. */
        val Unknown = AlarmLimits(
            maxSpeedKmh = 90.0,
            maxCurrentAmps = 120.0,
            maxVoltageVolts = 130.0,
            minVoltageVolts = 40.0,
            maxTemperatureCelsius = 100.0,
        )
    }
}

/**
 * Ajusta os alarmes ao envelope da roda: nenhum limite fica acima do que a roda
 * suporta. Usado quando o modelo é identificado (e quando a roda reporta um
 * limite de corrente menor que o presumido).
 */
fun AlarmConfig.clampedTo(limits: AlarmLimits): AlarmConfig = copy(
    speedAlarm1Kmh = speedAlarm1Kmh.coerceAtMost(limits.maxSpeedKmh),
    speedAlarm2Kmh = speedAlarm2Kmh.coerceAtMost(limits.maxSpeedKmh),
    speedAlarm3Kmh = speedAlarm3Kmh.coerceAtMost(limits.maxSpeedKmh),
    currentAlarmAmps = currentAlarmAmps.coerceAtMost(limits.maxCurrentAmps),
    overvoltageVolts = overvoltageVolts.coerceAtMost(limits.maxVoltageVolts),
    undervoltageVolts = undervoltageVolts.coerceAtMost(limits.maxVoltageVolts),
    temperatureAlarmCelsius = temperatureAlarmCelsius.coerceAtMost(limits.maxTemperatureCelsius),
)
