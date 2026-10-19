package world.euc.protocol.inmotion

import world.euc.protocol.model.WheelCapabilities
import world.euc.protocol.model.WheelSettingId

/**
 * Capacidades por modelo da família Inmotion V2.
 *
 * Baseadas nas telas de configuração do EUC World 2.66.0 (o que o app expõe
 * por modelo = o que funciona na prática) e na referência comunitária do
 * eucplanet para lock e handshake.
 *
 * Observações:
 * - **Tilt lateral estendido** só é exposto para V13/V14 — a V11y não suporta.
 * - **Lock** não aparece nas telas do EUC World (é ação rápida), mas é
 *   suportado na V11y/V13/V14 via framing estendido + autenticação.
 * - V13/V14 usam farol automático no lugar de DRL/brilho/ventilador.
 * - **Modos separados (split riding) não são expostos**: o opcode do comando
 *   (`0x3E`) é o mesmo dos alarmes de velocidade no app oficial e o comando
 *   real ainda não foi confirmado em captura — ver
 *   `docs/09-pendencias-inmotion.md` §1.1. O quadro de settings continua sendo
 *   lido (offsets 13/14 e flag em 34), mas nada é escrito na roda.
 */
object InmotionV2Capabilities {

    private val COMUM = setOf(
        WheelSettingId.SPEED_LIMIT,
        WheelSettingId.PEDAL_TILT,
        WheelSettingId.PEDAL_SENSITIVITY,
        WheelSettingId.TRANSPORTATION_MODE,
        WheelSettingId.CALIBRATION,
    )

    private val V11Y = COMUM + setOf(
        WheelSettingId.RIDING_MODE,
        WheelSettingId.PERFORMANCE_MODE,
        WheelSettingId.LOW_BATTERY_RIDING,
        WheelSettingId.MOTOR_NO_LOAD_DETECTION,
        WheelSettingId.HANDLE_BUTTON,
        WheelSettingId.HEADLIGHT,
        WheelSettingId.HEADLIGHT_BRIGHTNESS,
        WheelSettingId.DAYTIME_RUNNING_LIGHTS,
        WheelSettingId.SPEAKER_VOLUME,
        WheelSettingId.COOLING_FAN,
        WheelSettingId.COOLING_FAN_QUIET_MODE,
        WheelSettingId.LOCK,
    )

    private val V11 = V11Y

    private val V13_V14 = COMUM + setOf(
        WheelSettingId.SPEAKER_VOLUME,
        WheelSettingId.LOCK,
        WheelSettingId.EXTENDED_LATERAL_TILT_LIMIT,
    )

    private val V12 = COMUM + setOf(
        WheelSettingId.RIDING_MODE,
        WheelSettingId.SPEED_ALARMS,
        WheelSettingId.PERFORMANCE_MODE,
        WheelSettingId.LOW_BATTERY_RIDING,
        WheelSettingId.HANDLE_BUTTON,
        WheelSettingId.HEADLIGHT,
        WheelSettingId.HEADLIGHT_BRIGHTNESS,
        WheelSettingId.SPEAKER_VOLUME,
        WheelSettingId.LOCK,
    )

    private val V9_V12S = COMUM + setOf(
        WheelSettingId.RIDING_MODE,
        WheelSettingId.SPEED_ALARMS,
        WheelSettingId.LOW_BATTERY_RIDING,
        WheelSettingId.MOTOR_NO_LOAD_DETECTION,
        WheelSettingId.HANDLE_BUTTON,
        WheelSettingId.HEADLIGHT,
        WheelSettingId.HEADLIGHT_BRIGHTNESS,
        WheelSettingId.DAYTIME_RUNNING_LIGHTS,
        WheelSettingId.SPEAKER_VOLUME,
        WheelSettingId.LOCK,
    )

    private val P6 = COMUM + setOf(
        WheelSettingId.RIDING_MODE,
        WheelSettingId.SPEED_ALARMS,
        WheelSettingId.LOW_BATTERY_RIDING,
        WheelSettingId.MOTOR_NO_LOAD_DETECTION,
        WheelSettingId.HANDLE_BUTTON,
        WheelSettingId.HEADLIGHT,
        WheelSettingId.DAYTIME_RUNNING_LIGHTS,
        WheelSettingId.SPEAKER_VOLUME,
        WheelSettingId.LOCK,
    )

    /**
     * Velocidade máxima configurável por modelo (top speed oficial):
     * V11y 60 km/h, V11 50, V12 60, V13/V14 90, V9/V12S/E20/P6 menores.
     */
    private fun maxSpeedFor(model: InmotionModel?): Double = when (model) {
        InmotionModel.V11Y -> 60.0
        InmotionModel.V11 -> 50.0
        InmotionModel.V12 -> 60.0
        InmotionModel.V12S -> 50.0
        InmotionModel.V13, InmotionModel.V14 -> 90.0
        InmotionModel.V9 -> 30.0
        InmotionModel.E20, InmotionModel.P6 -> 25.0
        InmotionModel.UNKNOWN, null -> 50.0
    }

    /**
     * Framing estendido para ajustes protegidos (limite de velocidade e
     * bloqueio): confirmado na V11y — o mesmo comando em framing padrão é
     * ignorado sem aviso (o EUC World tem esse defeito na V11y).
     */
    private fun usesExtendedFraming(model: InmotionModel?): Boolean = when (model) {
        InmotionModel.V9, InmotionModel.V11, InmotionModel.E20, InmotionModel.P6 -> false
        else -> true
    }

    /**
     * Corrente de pico que o modelo suporta (teto dos alarmes). Valores
     * conservadores — a roda reporta o limite real na telemetria, que o app usa
     * quando for menor.
     */
    private fun maxCurrentFor(model: InmotionModel?): Double = when (model) {
        InmotionModel.V11Y, InmotionModel.V11 -> 58.0
        InmotionModel.V12 -> 60.0
        InmotionModel.V12S -> 50.0
        InmotionModel.V13, InmotionModel.V14 -> 100.0
        InmotionModel.V9 -> 30.0
        InmotionModel.E20, InmotionModel.P6 -> 30.0
        InmotionModel.UNKNOWN, null -> WheelCapabilities.DEFAULT_MAX_CURRENT
    }

    /** Capacidades do modelo; sem modelo detectado assume o conjunto da V11y. */
    fun forModel(model: InmotionModel?): WheelCapabilities = WheelCapabilities(
        maxSpeedLimitKmh = maxSpeedFor(model),
        maxCurrentAmps = maxCurrentFor(model),
        requiresExtendedFraming = usesExtendedFraming(model),
        settings = when (model) {
            InmotionModel.V11 -> V11
            InmotionModel.V11Y -> V11Y
            InmotionModel.V12 -> V12
            InmotionModel.V12S -> V9_V12S
            InmotionModel.V13, InmotionModel.V14 -> V13_V14
            InmotionModel.V9 -> V9_V12S
            InmotionModel.P6 -> P6
            InmotionModel.E20 -> COMUM + setOf(
                WheelSettingId.RIDING_MODE,
                WheelSettingId.SPEED_ALARMS,
                WheelSettingId.MOTOR_NO_LOAD_DETECTION,
                WheelSettingId.HEADLIGHT,
                WheelSettingId.HEADLIGHT_BRIGHTNESS,
            )
            InmotionModel.UNKNOWN, null -> V11Y
        },
    )
}
