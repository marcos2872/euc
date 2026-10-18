package world.euc.protocol.model

/**
 * Configurações da roda reportadas pelo próprio veículo.
 *
 * Cada campo é opcional: nem todo modelo/firmware reporta tudo, e ausência
 * significa "não reportado" (a UI deve manter o último valor conhecido).
 */
data class WheelSettings(
    /** Limite de velocidade (tiltback), em km/h. */
    val speedLimitKmh: Double? = null,
    /** Inclinação dos pedais, em graus (positivo = ponta para cima). */
    val pedalTiltDegrees: Double? = null,
    /** Sensibilidade dos pedais, em %. */
    val pedalSensitivityPercent: Int? = null,
    /** Modo de condução (0 = comfort, 1 = classic), quando reportado. */
    val ridingMode: Int? = null,
    /** Modos de condução separados (aceleração/frenagem independentes). */
    val splitRidingModesEnabled: Boolean? = null,
    val splitRidingAccelerationPercent: Int? = null,
    val splitRidingBrakingPercent: Int? = null,
    /** Volume do alto-falante, em %. */
    val speakerVolumePercent: Int? = null,
    /** Limite de inclinação lateral estendido (curvas acentuadas). */
    val extendedLateralTiltLimit: Boolean? = null,
)
