package world.euc.protocol.inmotion

import world.euc.protocol.model.RidingMode
import world.euc.protocol.model.WheelTelemetry
import world.euc.protocol.model.WheelTemperatures

/**
 * Interpreta os quadros de resposta de telemetria do protocolo Inmotion V2
 * (família V11+, incluindo a V11y).
 *
 * Layout dos dados (offsets relativos ao início do payload menos o byte de
 * resposta), little-endian, confirmado no EUC World 2.66.0 (parser `m11168M`
 * para V11Y) e na documentação comunitária do WheelLog/eucplanet:
 *
 * | Offset | Campo                          | Escala      |
 * |--------|--------------------------------|-------------|
 * | 0      | Tensão                         | /100 V      |
 * | 2      | Corrente                       | /100 A      |
 * | 8      | Velocidade                     | /100 km/h   |
 * | 14     | Carga do motor (PWM)           | /100 %      |
 * | 20     | Pitch (tilt)                   | /100 °      |
 * | 22     | Roll                           | /100 °      |
 * | 34     | Bateria                        | /100 %      |
 * | 40     | Limite de velocidade           | /100 km/h   |
 * | 48     | Limite de corrente             | /100 A      |
 * | 58-63  | Temperaturas (MOS, motor, bateria, placa, CPU, IMU) | byte − 176 °C |
 * | 74     | Estado (bits 0-2 = modo)       | —           |
 *
 * Campos ausentes no quadro (respostas curtas de firmware antigo) ficam nulos.
 */
class InmotionV2TelemetryParser {

    /** Extrai a telemetria de um quadro de resposta; `null` se não for telemetria. */
    fun parse(frame: InmotionFrame): WheelTelemetry? {
        val payload = frame.payload
        if (payload.isEmpty()) return null
        if (payload[0].toInt() and 0xFF != RESPONSE_VALUES) return null

        val data = payload.copyOfRange(1, payload.size)
        val load = absolute(i16Le(data, OFFSET_LOAD))?.div(100.0)

        return WheelTelemetry(
            voltageVolts = u16Le(data, OFFSET_VOLTAGE)?.div(100.0),
            currentAmps = i16Le(data, OFFSET_CURRENT)?.div(100.0),
            speedKmh = absolute(i16Le(data, OFFSET_SPEED))?.div(100.0),
            loadPercent = load,
            safetyMarginPercent = load?.let { 100.0 - it },
            pitchDegrees = i16Le(data, OFFSET_PITCH)?.div(100.0),
            rollDegrees = i16Le(data, OFFSET_ROLL)?.div(100.0),
            batteryPercent = absolute(i16Le(data, OFFSET_BATTERY))?.div(100.0),
            speedLimitKmh = absolute(i16Le(data, OFFSET_SPEED_LIMIT))?.div(100.0),
            currentLimitAmps = i16Le(data, OFFSET_CURRENT_LIMIT)?.div(100.0),
            temperatures = parseTemperatures(data),
            ridingMode = parseRidingMode(data),
        )
    }

    private fun parseTemperatures(data: ByteArray): WheelTemperatures? {
        val temperatures = WheelTemperatures(
            mosfet = temperature(data, OFFSET_TEMP_MOSFET),
            motor = temperature(data, OFFSET_TEMP_MOTOR),
            battery = temperature(data, OFFSET_TEMP_BATTERY),
            board = temperature(data, OFFSET_TEMP_BOARD),
            cpu = temperature(data, OFFSET_TEMP_CPU),
            imu = temperature(data, OFFSET_TEMP_IMU),
        )
        val hasAnySensor = temperatures.let {
            it.mosfet != null || it.motor != null || it.battery != null ||
                it.board != null || it.cpu != null || it.imu != null
        }
        return temperatures.takeIf { hasAnySensor }
    }

    private fun parseRidingMode(data: ByteArray): RidingMode? {
        val status = u8(data, OFFSET_STATUS) ?: return null
        return when (status and 0x07) {
            0 -> RidingMode.LOCKED
            1 -> RidingMode.DRIVE
            2 -> RidingMode.SHUTDOWN
            3 -> RidingMode.IDLE
            else -> RidingMode.UNKNOWN
        }
    }

    /**
     * Temperaturas chegam como byte sem sinal deslocado: `byte − 176`.
     * Bytes fora da faixa (0x00-0x7F) produzem valores implausíveis e são
     * tratados como sensor ausente, mesma proteção do app oficial.
     */
    private fun temperature(data: ByteArray, offset: Int): Double? {
        val raw = u8(data, offset) ?: return null
        if (raw < 0x80) return null
        return (raw - 176).toDouble()
    }

    private fun u8(data: ByteArray, offset: Int): Int? =
        if (offset < data.size) data[offset].toInt() and 0xFF else null

    private fun u16Le(data: ByteArray, offset: Int): Int? {
        if (offset + 1 >= data.size) return null
        return (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun i16Le(data: ByteArray, offset: Int): Int? {
        val value = u16Le(data, offset) ?: return null
        return if (value >= 0x8000) value - 0x10000 else value
    }

    private fun absolute(value: Int?): Int? = value?.let { if (it < 0) -it else it }

    private companion object {
        const val RESPONSE_VALUES = 0x84

        const val OFFSET_VOLTAGE = 0
        const val OFFSET_CURRENT = 2
        const val OFFSET_SPEED = 8
        const val OFFSET_LOAD = 14
        const val OFFSET_PITCH = 20
        const val OFFSET_ROLL = 22
        const val OFFSET_BATTERY = 34
        const val OFFSET_SPEED_LIMIT = 40
        const val OFFSET_CURRENT_LIMIT = 48
        const val OFFSET_TEMP_MOSFET = 58
        const val OFFSET_TEMP_MOTOR = 59
        const val OFFSET_TEMP_BATTERY = 60
        const val OFFSET_TEMP_BOARD = 61
        const val OFFSET_TEMP_CPU = 62
        const val OFFSET_TEMP_IMU = 63
        const val OFFSET_STATUS = 74
    }
}
