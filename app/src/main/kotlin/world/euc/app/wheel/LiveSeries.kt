package world.euc.app.wheel

import world.euc.protocol.model.WheelTelemetry

/** Uma amostra de uma série temporal curta. */
data class LiveSample(val timeMs: Long, val value: Double)

/**
 * Série temporal curta (janela deslizante) para os gráficos ao vivo.
 *
 * Imutável: cada leitura devolve uma série nova, o que combina com o estado
 * observável da UI. A capacidade padrão (240 amostras ≈ 2 min a 2 Hz) mantém
 * o histórico recente sem crescer sem limite.
 */
data class LiveSeries(
    val samples: List<LiveSample> = emptyList(),
    val capacity: Int = DEFAULT_CAPACITY,
) {
    val latest: Double? get() = samples.lastOrNull()?.value

    val isEmpty: Boolean get() = samples.isEmpty()

    /** Acrescenta uma leitura; valor ausente ou inválido não entra na série. */
    fun plus(timeMs: Long, value: Double?): LiveSeries {
        if (value == null || !value.isFinite()) return this
        val updated = samples + LiveSample(timeMs, value)
        return if (updated.size <= capacity) {
            copy(samples = updated)
        } else {
            copy(samples = updated.takeLast(capacity))
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 240
    }
}

/** Séries ao vivo alimentadas pela telemetria, para os gráficos do painel. */
data class LiveHistory(
    val speed: LiveSeries = LiveSeries(),
    val current: LiveSeries = LiveSeries(),
    val voltage: LiveSeries = LiveSeries(),
    val power: LiveSeries = LiveSeries(),
    val load: LiveSeries = LiveSeries(),
    val temperature: LiveSeries = LiveSeries(),
) {
    fun plus(telemetry: WheelTelemetry, timeMs: Long): LiveHistory = LiveHistory(
        speed = speed.plus(timeMs, telemetry.speedKmh),
        current = current.plus(timeMs, telemetry.currentAmps),
        voltage = voltage.plus(timeMs, telemetry.voltageVolts),
        power = power.plus(
            timeMs,
            telemetry.voltageVolts?.let { volts ->
                telemetry.currentAmps?.let { amps -> volts * amps }
            },
        ),
        load = load.plus(timeMs, telemetry.loadPercent),
        temperature = temperature.plus(
            timeMs,
            telemetry.temperatures?.mosfet ?: telemetry.temperatures?.board,
        ),
    )
}

/** Séries que o painel sabe desenhar. */
enum class LiveSeriesKind(val label: String, val unit: String) {
    SPEED("Velocidade", "km/h"),
    CURRENT("Corrente", "A"),
    VOLTAGE("Tensão", "V"),
    POWER("Potência", "W"),
    LOAD("Carga (PWM)", "%"),
    TEMPERATURE("Temperatura", "°C");

    fun of(history: LiveHistory): LiveSeries = when (this) {
        SPEED -> history.speed
        CURRENT -> history.current
        VOLTAGE -> history.voltage
        POWER -> history.power
        LOAD -> history.load
        TEMPERATURE -> history.temperature
    }
}
