package world.euc.app.alarm

import android.content.Context
import androidx.core.content.edit
import world.euc.protocol.alarm.AlarmConfig
import world.euc.protocol.alarm.AlarmType

/**
 * Persiste a configuração de alarmes em SharedPreferences.
 *
 * Escolha pragmática: são poucos valores primitivos e não exigem migração;
 * um armazenamento mais rico (DataStore) pode substituir sem mudar a API.
 */
class AlarmConfigStore(context: Context) {

    private val prefs = context.getSharedPreferences("alarm_config", Context.MODE_PRIVATE)

    fun load(): AlarmConfig {
        // Migração (uma única vez, na primeira abertura depois da atualização):
        // antes dos interruptores por nível, valor 0 significava "desligado".
        val stored = prefs.getStringSet(KEY_DISABLED_TYPES, null)
        val disabled = stored ?: buildSet {
            if (prefs.getFloat(KEY_CURRENT, 0f) <= 0f) add(AlarmType.CURRENT.name)
            if (prefs.getFloat(KEY_OVERVOLTAGE, 0f) <= 0f) add(AlarmType.OVERVOLTAGE.name)
            if (prefs.getFloat(KEY_UNDERVOLTAGE, 0f) <= 0f) add(AlarmType.UNDERVOLTAGE.name)
        }

        return AlarmConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            disabledTypes = disabled.mapNotNullTo(mutableSetOf()) { name ->
                runCatching { AlarmType.valueOf(name) }.getOrNull()
            },
            speedAlarm1Kmh = prefs.getFloat(KEY_SPEED_1, 25f).toDouble(),
            speedAlarm2Kmh = prefs.getFloat(KEY_SPEED_2, 30f).toDouble(),
            speedAlarm3Kmh = prefs.getFloat(KEY_SPEED_3, 35f).toDouble(),
            preAlarmMarginKmh = prefs.getFloat(KEY_PRE_MARGIN, 2f).toDouble(),
            speedLimitAlarmEnabled = prefs.getBoolean(KEY_SPEED_LIMIT, true),
            speedLimitMarginKmh = prefs.getFloat(KEY_SPEED_LIMIT_MARGIN, 2f).toDouble(),
            currentAlarmAmps = prefs.getFloat(KEY_CURRENT, 0f).toDouble(),
            temperatureAlarmCelsius = prefs.getFloat(KEY_TEMPERATURE, 70f).toDouble(),
            overvoltageVolts = prefs.getFloat(KEY_OVERVOLTAGE, 0f).toDouble(),
            undervoltageVolts = prefs.getFloat(KEY_UNDERVOLTAGE, 0f).toDouble(),
            batteryAlarmPercent = prefs.getFloat(KEY_BATTERY, 15f).toDouble(),
            safetyMarginAlarmPercent = prefs.getFloat(KEY_SAFETY_MARGIN, 20f).toDouble(),
            connectionLostAfterMillis = prefs.getLong(KEY_CONNECTION_LOST, 5_000L),
            voiceEnabled = prefs.getBoolean(KEY_VOICE, true),
            vibrationEnabled = prefs.getBoolean(KEY_VIBRATION, true),
        )
    }

    fun save(config: AlarmConfig) {
        prefs.edit {
            putBoolean(KEY_ENABLED, config.enabled)
            putStringSet(KEY_DISABLED_TYPES, config.disabledTypes.map { it.name }.toSet())
            putFloat(KEY_SPEED_1, config.speedAlarm1Kmh.toFloat())
            putFloat(KEY_SPEED_2, config.speedAlarm2Kmh.toFloat())
            putFloat(KEY_SPEED_3, config.speedAlarm3Kmh.toFloat())
            putFloat(KEY_PRE_MARGIN, config.preAlarmMarginKmh.toFloat())
            putBoolean(KEY_SPEED_LIMIT, config.speedLimitAlarmEnabled)
            putFloat(KEY_SPEED_LIMIT_MARGIN, config.speedLimitMarginKmh.toFloat())
            putFloat(KEY_CURRENT, config.currentAlarmAmps.toFloat())
            putFloat(KEY_TEMPERATURE, config.temperatureAlarmCelsius.toFloat())
            putFloat(KEY_OVERVOLTAGE, config.overvoltageVolts.toFloat())
            putFloat(KEY_UNDERVOLTAGE, config.undervoltageVolts.toFloat())
            putFloat(KEY_BATTERY, config.batteryAlarmPercent.toFloat())
            putFloat(KEY_SAFETY_MARGIN, config.safetyMarginAlarmPercent.toFloat())
            putLong(KEY_CONNECTION_LOST, config.connectionLostAfterMillis)
            putBoolean(KEY_VOICE, config.voiceEnabled)
            putBoolean(KEY_VIBRATION, config.vibrationEnabled)
        }
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_DISABLED_TYPES = "disabled_types"
        const val KEY_SPEED_1 = "speed_1"
        const val KEY_SPEED_2 = "speed_2"
        const val KEY_SPEED_3 = "speed_3"
        const val KEY_PRE_MARGIN = "pre_margin"
        const val KEY_SPEED_LIMIT = "speed_limit"
        const val KEY_SPEED_LIMIT_MARGIN = "speed_limit_margin"
        const val KEY_CURRENT = "current"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_OVERVOLTAGE = "overvoltage"
        const val KEY_UNDERVOLTAGE = "undervoltage"
        const val KEY_BATTERY = "battery"
        const val KEY_SAFETY_MARGIN = "safety_margin"
        const val KEY_CONNECTION_LOST = "connection_lost"
        const val KEY_VOICE = "voice"
        const val KEY_VIBRATION = "vibration"
    }
}
