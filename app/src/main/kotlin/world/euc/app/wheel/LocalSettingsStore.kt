package world.euc.app.wheel

import android.content.Context
import androidx.core.content.edit
import world.euc.protocol.model.WheelSettings

/**
 * Persiste o que a roda não guarda sozinha: as configurações locais
 * (farol, DRL, ventilador…) e o último estado conhecido das configurações
 * reportadas, para a tela abrir já com os seus valores.
 *
 * Valores ausentes usam sentinelas (`Float.NaN`, `Int.MIN_VALUE`) porque
 * SharedPreferences não guarda nulos.
 */
class LocalSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("wheel_local_settings", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- locais

    fun loadLocal(): LocalSettings = LocalSettings(
        headlightOn = prefs.getBoolean(KEY_HEADLIGHT, false),
        daytimeRunningLightsOn = prefs.getBoolean(KEY_DRL, false),
        headlightBrightness = prefs.getInt(KEY_BRIGHTNESS, 100),
        fanOn = prefs.getBoolean(KEY_FAN, false),
        fanQuietMode = prefs.getBoolean(KEY_FAN_QUIET, false),
        performanceMode = prefs.getBoolean(KEY_PERFORMANCE, false),
        locked = prefs.getBoolean(KEY_LOCKED, false),
        lowBatteryRiding = prefs.getBoolean(KEY_LOW_BATTERY, false),
        motorNoLoadDetection = prefs.getBoolean(KEY_NO_LOAD, false),
        transportationMode = prefs.getBoolean(KEY_TRANSPORT, false),
        handleButton = prefs.getBoolean(KEY_HANDLE_BUTTON, true),
    )

    fun saveLocal(settings: LocalSettings) {
        prefs.edit {
            putBoolean(KEY_HEADLIGHT, settings.headlightOn)
            putBoolean(KEY_DRL, settings.daytimeRunningLightsOn)
            putInt(KEY_BRIGHTNESS, settings.headlightBrightness)
            putBoolean(KEY_FAN, settings.fanOn)
            putBoolean(KEY_FAN_QUIET, settings.fanQuietMode)
            putBoolean(KEY_PERFORMANCE, settings.performanceMode)
            putBoolean(KEY_LOCKED, settings.locked)
            putBoolean(KEY_LOW_BATTERY, settings.lowBatteryRiding)
            putBoolean(KEY_NO_LOAD, settings.motorNoLoadDetection)
            putBoolean(KEY_TRANSPORT, settings.transportationMode)
            putBoolean(KEY_HANDLE_BUTTON, settings.handleButton)
        }
    }

    // ------------------------------------------------------- da roda (cache)

    fun loadWheelSettings(): WheelSettings = WheelSettings(
        speedLimitKmh = prefs.getFloat(KEY_SPEED_LIMIT, ABSENT_FLOAT).takeIf { !it.isNaN() }?.toDouble(),
        pedalTiltDegrees = prefs.getFloat(KEY_PEDAL_TILT, ABSENT_FLOAT).takeIf { !it.isNaN() }?.toDouble(),
        pedalSensitivityPercent = prefs.getInt(KEY_PEDAL_SENSITIVITY, ABSENT_INT).takeIf { it != ABSENT_INT },
        ridingMode = prefs.getInt(KEY_RIDING_MODE, ABSENT_INT).takeIf { it != ABSENT_INT },
        splitRidingModesEnabled = prefs.getBoolean(KEY_SPLIT_ENABLED, false),
        splitRidingAccelerationPercent = prefs.getInt(KEY_SPLIT_ACCELERATION, ABSENT_INT).takeIf { it != ABSENT_INT },
        splitRidingBrakingPercent = prefs.getInt(KEY_SPLIT_BRAKING, ABSENT_INT).takeIf { it != ABSENT_INT },
        speakerVolumePercent = prefs.getInt(KEY_VOLUME, ABSENT_INT).takeIf { it != ABSENT_INT },
        extendedLateralTiltLimit = prefs.getBoolean(KEY_EXTENDED_TILT, false),
    )

    fun saveWheelSettings(settings: WheelSettings) {
        prefs.edit {
            putFloat(KEY_SPEED_LIMIT, settings.speedLimitKmh?.toFloat() ?: ABSENT_FLOAT)
            putFloat(KEY_PEDAL_TILT, settings.pedalTiltDegrees?.toFloat() ?: ABSENT_FLOAT)
            putInt(KEY_PEDAL_SENSITIVITY, settings.pedalSensitivityPercent ?: ABSENT_INT)
            putInt(KEY_RIDING_MODE, settings.ridingMode ?: ABSENT_INT)
            putBoolean(KEY_SPLIT_ENABLED, settings.splitRidingModesEnabled ?: false)
            putInt(KEY_SPLIT_ACCELERATION, settings.splitRidingAccelerationPercent ?: ABSENT_INT)
            putInt(KEY_SPLIT_BRAKING, settings.splitRidingBrakingPercent ?: ABSENT_INT)
            putInt(KEY_VOLUME, settings.speakerVolumePercent ?: ABSENT_INT)
            putBoolean(KEY_EXTENDED_TILT, settings.extendedLateralTiltLimit ?: false)
        }
    }

    // --------------------------------------------------------------- odômetro

    /**
     * Odômetro acumulado **pelo app** para uma roda (km), usado quando o
     * firmware não reporta a quilometragem total. A chave é o número de série
     * (ou o endereço BLE, quando a série ainda não chegou).
     */
    fun odometerKm(wheelKey: String): Double =
        prefs.getFloat(odometerKey(wheelKey), 0f).toDouble()

    /** Soma a distância percorrida ao odômetro acumulado da roda. */
    fun addOdometerKm(wheelKey: String, km: Double) {
        if (km <= 0.0 || !km.isFinite()) return
        val key = odometerKey(wheelKey)
        prefs.edit { putFloat(key, prefs.getFloat(key, 0f) + km.toFloat()) }
    }

    private fun odometerKey(wheelKey: String) = "odometer_$wheelKey"

    // ------------------------------------------------------------- comportamento

    /** Reaplicar as configurações salvas na roda a cada conexão. */
    var reapplyOnConnect: Boolean
        get() = prefs.getBoolean(KEY_REAPPLY, false)
        set(value) = prefs.edit { putBoolean(KEY_REAPPLY, value) }

    /** Gravar um log CSV da telemetria a cada sessão de conexão. */
    var csvLogging: Boolean
        get() = prefs.getBoolean(KEY_CSV_LOGGING, false)
        set(value) = prefs.edit { putBoolean(KEY_CSV_LOGGING, value) }

    private companion object {
        const val ABSENT_FLOAT = Float.NaN
        const val ABSENT_INT = Int.MIN_VALUE

        const val KEY_HEADLIGHT = "headlight"
        const val KEY_DRL = "drl"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_FAN = "fan"
        const val KEY_FAN_QUIET = "fan_quiet"
        const val KEY_PERFORMANCE = "performance"
        const val KEY_LOCKED = "locked"
        const val KEY_LOW_BATTERY = "low_battery"
        const val KEY_NO_LOAD = "no_load"
        const val KEY_TRANSPORT = "transport"
        const val KEY_HANDLE_BUTTON = "handle_button"

        const val KEY_SPEED_LIMIT = "speed_limit"
        const val KEY_PEDAL_TILT = "pedal_tilt"
        const val KEY_PEDAL_SENSITIVITY = "pedal_sensitivity"
        const val KEY_RIDING_MODE = "riding_mode"
        const val KEY_SPLIT_ENABLED = "split_enabled"
        const val KEY_SPLIT_ACCELERATION = "split_acceleration"
        const val KEY_SPLIT_BRAKING = "split_braking"
        const val KEY_VOLUME = "volume"
        const val KEY_EXTENDED_TILT = "extended_tilt"

        const val KEY_REAPPLY = "reapply_on_connect"
        const val KEY_CSV_LOGGING = "csv_logging"
    }
}
