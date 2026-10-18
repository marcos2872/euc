package world.euc.protocol.alarm

/** Tipos de alarme do app (independentes de marca de roda). */
enum class AlarmType {
    /** Alarme de velocidade mais baixo (primeiro nível). */
    SPEED_1,
    SPEED_2,
    SPEED_3,

    /** Aviso antecipado: aproximando-se do nível sem ultrapassá-lo. */
    SPEED_PRE_1,
    SPEED_PRE_2,

    /** Aproximando-se do limite de velocidade (tiltback) da roda. */
    SPEED_LIMIT,

    /** Corrente alta (pico). */
    CURRENT,

    /** Temperatura alta em qualquer sensor. */
    TEMPERATURE,

    /** Tensão acima do seguro. */
    OVERVOLTAGE,

    /** Tensão abaixo do seguro. */
    UNDERVOLTAGE,

    /** Bateria baixa. */
    BATTERY,

    /** Margem de segurança baixa (carga do motor próxima do limite). */
    SAFETY_MARGIN,

    /** Sem telemetria por tempo demais. */
    CONNECTION_LOST,
}

/** Prioridade define som/vibração/urgência na UI. */
enum class AlarmPriority { LOW, MEDIUM, HIGH }

/**
 * Alarme disparado, com o valor que o provocou (a mensagem falada é montada
 * pela camada de apresentação, que conhece o idioma).
 */
data class AlarmEvent(
    val type: AlarmType,
    val priority: AlarmPriority,
    val value: Double? = null,
)

/**
 * Configuração dos alarmes. Valores em 0 desativam o alarme correspondente
 * quando isso faz sentido (corrente, tensões).
 */
data class AlarmConfig(
    val enabled: Boolean = true,
    val speedAlarm1Kmh: Double = 25.0,
    val speedAlarm2Kmh: Double = 30.0,
    val speedAlarm3Kmh: Double = 35.0,
    /** Distância (km/h) abaixo de um nível que dispara o aviso antecipado. */
    val preAlarmMarginKmh: Double = 2.0,
    val speedLimitAlarmEnabled: Boolean = true,
    /** Margem (km/h) antes do tiltback que dispara o alarme de limite. */
    val speedLimitMarginKmh: Double = 2.0,
    /** Corrente de pico que dispara o alarme (0 = desativado). */
    val currentAlarmAmps: Double = 0.0,
    val temperatureAlarmCelsius: Double = 70.0,
    /** Tensão acima da qual alarmar (0 = desativado). */
    val overvoltageVolts: Double = 0.0,
    /** Tensão abaixo da qual alarmar (0 = desativado). */
    val undervoltageVolts: Double = 0.0,
    val batteryAlarmPercent: Double = 15.0,
    val safetyMarginAlarmPercent: Double = 20.0,
    /** Tempo sem telemetria que dispara o alarme de conexão perdida. */
    val connectionLostAfterMillis: Long = 5_000L,
    /** Avisos falados (TTS). */
    val voiceEnabled: Boolean = true,
    /** Vibração do telefone. */
    val vibrationEnabled: Boolean = true,
)

/**
 * Avalia telemetria contra a configuração e produz alarmes.
 *
 * Regras:
 * - Apenas o **maior** nível de velocidade atingido dispara; avisos
 *   antecipados só ocorrem quando nenhum nível foi atingido.
 * - Cada tipo respeita um **cooldown** para não repetir o aviso
 *   continuamente; quando a condição desaparece, o tipo é rearmado.
 */
class AlarmEngine(private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS) {

    private val lastFiredAt = mutableMapOf<AlarmType, Long>()
    private var previousTypes: Set<AlarmType> = emptySet()

    /**
     * Avalia uma leitura. [nowMillis] é injetado para permitir testes
     * determinísticos e uso de tempo virtual.
     */
    fun evaluate(
        telemetry: world.euc.protocol.model.WheelTelemetry,
        config: AlarmConfig,
        nowMillis: Long,
    ): List<AlarmEvent> {
        val triggered = if (config.enabled) collect(telemetry, config) else emptyList()
        rearmClearedTypes(triggered.map { it.type }.toSet())

        val ready = triggered.filter { event -> !isCoolingDown(event.type, nowMillis) }
        ready.forEach { lastFiredAt[it.type] = nowMillis }
        previousTypes = triggered.map { it.type }.toSet()
        return ready
    }

    /** Avalia a perda de conexão com base no tempo da última leitura. */
    fun evaluateConnection(
        lastTelemetryAtMillis: Long,
        config: AlarmConfig,
        nowMillis: Long,
    ): AlarmEvent? {
        if (!config.enabled || lastTelemetryAtMillis == 0L) return null
        val silentFor = nowMillis - lastTelemetryAtMillis
        if (silentFor < config.connectionLostAfterMillis) return null
        if (isCoolingDown(AlarmType.CONNECTION_LOST, nowMillis)) return null
        lastFiredAt[AlarmType.CONNECTION_LOST] = nowMillis
        return AlarmEvent(AlarmType.CONNECTION_LOST, AlarmPriority.HIGH, silentFor / 1000.0)
    }

    private fun collect(
        telemetry: world.euc.protocol.model.WheelTelemetry,
        config: AlarmConfig,
    ): List<AlarmEvent> = buildList {
        collectSpeed(telemetry, config)
        collectCurrent(telemetry, config)
        collectTemperature(telemetry, config)
        collectVoltage(telemetry, config)
        collectBattery(telemetry, config)
        collectSafetyMargin(telemetry, config)
    }

    private fun MutableList<AlarmEvent>.collectSpeed(
        telemetry: world.euc.protocol.model.WheelTelemetry,
        config: AlarmConfig,
    ) {
        val speed = telemetry.speedKmh ?: return
        val levels = listOf(
            Triple(AlarmType.SPEED_1, config.speedAlarm1Kmh, AlarmPriority.LOW),
            Triple(AlarmType.SPEED_2, config.speedAlarm2Kmh, AlarmPriority.MEDIUM),
            Triple(AlarmType.SPEED_3, config.speedAlarm3Kmh, AlarmPriority.HIGH),
        )
        val reached = levels.lastOrNull { speed >= it.second }
        when {
            reached != null -> add(AlarmEvent(reached.first, reached.third, speed))
            speed >= config.speedAlarm2Kmh - config.preAlarmMarginKmh ->
                add(AlarmEvent(AlarmType.SPEED_PRE_2, AlarmPriority.LOW, speed))
            speed >= config.speedAlarm1Kmh - config.preAlarmMarginKmh ->
                add(AlarmEvent(AlarmType.SPEED_PRE_1, AlarmPriority.LOW, speed))
        }

        val limit = telemetry.speedLimitKmh
        if (config.speedLimitAlarmEnabled && limit != null && limit > 0 &&
            speed >= limit - config.speedLimitMarginKmh
        ) {
            add(AlarmEvent(AlarmType.SPEED_LIMIT, AlarmPriority.HIGH, speed))
        }
    }

    private fun MutableList<AlarmEvent>.collectCurrent(
        telemetry: world.euc.protocol.model.WheelTelemetry,
        config: AlarmConfig,
    ) {
        val current = telemetry.currentAmps?.let { kotlin.math.abs(it) } ?: return
        if (config.currentAlarmAmps > 0 && current >= config.currentAlarmAmps) {
            add(AlarmEvent(AlarmType.CURRENT, AlarmPriority.MEDIUM, current))
        }
    }

    private fun MutableList<AlarmEvent>.collectTemperature(
        telemetry: world.euc.protocol.model.WheelTelemetry,
        config: AlarmConfig,
    ) {
        val temperatures = telemetry.temperatures ?: return
        val hottest = listOfNotNull(
            temperatures.mosfet,
            temperatures.motor,
            temperatures.battery,
            temperatures.board,
            temperatures.cpu,
            temperatures.imu,
        ).maxOrNull() ?: return

        if (config.temperatureAlarmCelsius > 0 && hottest >= config.temperatureAlarmCelsius) {
            add(AlarmEvent(AlarmType.TEMPERATURE, AlarmPriority.MEDIUM, hottest))
        }
    }

    private fun MutableList<AlarmEvent>.collectVoltage(
        telemetry: world.euc.protocol.model.WheelTelemetry,
        config: AlarmConfig,
    ) {
        val voltage = telemetry.voltageVolts ?: return
        if (config.overvoltageVolts > 0 && voltage >= config.overvoltageVolts) {
            add(AlarmEvent(AlarmType.OVERVOLTAGE, AlarmPriority.MEDIUM, voltage))
        }
        if (config.undervoltageVolts > 0 && voltage <= config.undervoltageVolts) {
            add(AlarmEvent(AlarmType.UNDERVOLTAGE, AlarmPriority.HIGH, voltage))
        }
    }

    private fun MutableList<AlarmEvent>.collectBattery(
        telemetry: world.euc.protocol.model.WheelTelemetry,
        config: AlarmConfig,
    ) {
        val battery = telemetry.batteryPercent ?: return
        if (config.batteryAlarmPercent > 0 && battery <= config.batteryAlarmPercent) {
            add(AlarmEvent(AlarmType.BATTERY, AlarmPriority.MEDIUM, battery))
        }
    }

    private fun MutableList<AlarmEvent>.collectSafetyMargin(
        telemetry: world.euc.protocol.model.WheelTelemetry,
        config: AlarmConfig,
    ) {
        val margin = telemetry.safetyMarginPercent ?: return
        if (config.safetyMarginAlarmPercent > 0 && margin <= config.safetyMarginAlarmPercent) {
            add(AlarmEvent(AlarmType.SAFETY_MARGIN, AlarmPriority.HIGH, margin))
        }
    }

    private fun isCoolingDown(type: AlarmType, nowMillis: Long): Boolean {
        val last = lastFiredAt[type] ?: return false
        return nowMillis - last < cooldownMillis
    }

    /** Rearma tipos que deixaram de disparar, permitindo aviso imediato depois. */
    private fun rearmClearedTypes(currentTypes: Set<AlarmType>) {
        (previousTypes - currentTypes).forEach { lastFiredAt.remove(it) }
    }

    private companion object {
        const val DEFAULT_COOLDOWN_MILLIS = 10_000L
    }
}
