package world.euc.protocol.model

/**
 * Identificadores das configurações que uma roda pode suportar.
 *
 * A UI deve mostrar apenas o que o modelo reporta como suportado — evita
 * botões que não fazem nada em certos firmwares.
 */
enum class WheelSettingId {
    SPEED_LIMIT,
    PEDAL_TILT,
    PEDAL_SENSITIVITY,
    RIDING_MODE,
    SPLIT_RIDING_MODES,
    PERFORMANCE_MODE,
    LOW_BATTERY_RIDING,
    MOTOR_NO_LOAD_DETECTION,
    TRANSPORTATION_MODE,
    HANDLE_BUTTON,
    HEADLIGHT,
    HEADLIGHT_BRIGHTNESS,
    DAYTIME_RUNNING_LIGHTS,
    SPEAKER_VOLUME,
    COOLING_FAN,
    COOLING_FAN_QUIET_MODE,
    LOCK,
    EXTENDED_LATERAL_TILT_LIMIT,
    SPEED_ALARMS,
    CALIBRATION,
}

/** Conjunto de configurações suportadas por um modelo. */
data class WheelCapabilities(
    val settings: Set<WheelSettingId>,
) {
    operator fun contains(setting: WheelSettingId): Boolean = setting in settings
}
