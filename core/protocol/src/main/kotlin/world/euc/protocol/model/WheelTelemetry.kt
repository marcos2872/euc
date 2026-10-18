package world.euc.protocol.model

/**
 * Leitura instantânea de telemetria de um monociclo.
 *
 * Todos os campos são opcionais: cada protocolo/modelo reporta um subconjunto
 * diferente, e um valor ausente significa "não reportado", nunca zero.
 */
data class WheelTelemetry(
    val voltageVolts: Double? = null,
    val currentAmps: Double? = null,
    val speedKmh: Double? = null,
    /** Carga do motor (PWM) em %, como reportada pela roda. */
    val loadPercent: Double? = null,
    /** Margem de segurança em % (derivada da carga: 100 - carga). */
    val safetyMarginPercent: Double? = null,
    val pitchDegrees: Double? = null,
    val rollDegrees: Double? = null,
    val batteryPercent: Double? = null,
    val speedLimitKmh: Double? = null,
    val currentLimitAmps: Double? = null,
    val temperatures: WheelTemperatures? = null,
    val ridingMode: RidingMode? = null,
)

/** Temperaturas em °C por sensor. Ausente = sensor não presente neste modelo. */
data class WheelTemperatures(
    val mosfet: Double? = null,
    val motor: Double? = null,
    val battery: Double? = null,
    val board: Double? = null,
    val cpu: Double? = null,
    val imu: Double? = null,
)

/** Modo de operação reportado pela roda (bits baixos do byte de estado). */
enum class RidingMode {
    LOCKED,
    DRIVE,
    SHUTDOWN,
    IDLE,
    UNKNOWN,
}
