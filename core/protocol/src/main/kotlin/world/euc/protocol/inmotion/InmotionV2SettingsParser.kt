package world.euc.protocol.inmotion

import world.euc.protocol.model.WheelSettings

/**
 * Interpreta a resposta de configurações (`A0 20`) do protocolo Inmotion V2,
 * no layout da família V11y/V13/V14.
 *
 * Offsets confirmados no EUC World 2.66.0 (parser `m11207L`, usado pela V11Y)
 * com as propriedades nomeadas do adaptador:
 *
 * | Offset | Campo                          | Escala |
 * |--------|--------------------------------|--------|
 * | 2      | Limite de velocidade           | /100   |
 * | 10     | Tilt dos pedais                | /100°  |
 * | 13     | Sensibilidade dos pedais       | %      |
 * | 15     | Split riding: aceleração       | %      |
 * | 16     | Split riding: frenagem         | %      |
 * | 17     | Volume do alto-falante         | %      |
 * | 36     | bit 2: split ligado · bit 5: tilt lateral estendido | — |
 *
 * Outras configurações (farol, DRL, ventilador, modos) não são reportadas
 * nesse quadro pelo firmware: o app mantém o último valor conhecido.
 */
class InmotionV2SettingsParser {

    /** Extrai as configurações de um quadro; `null` se não for essa resposta. */
    fun parse(frame: InmotionFrame): WheelSettings? {
        val payload = frame.payload
        if (payload.size < 2) return null
        if (payload[0].toInt() and 0xFF != RESPONSE_SETTINGS) return null
        if (payload[1].toInt() and 0xFF != SETTINGS_SUBCOMMAND) return null

        val data = payload.copyOfRange(2, payload.size)
        val flags = u8(data, OFFSET_FLAGS)

        return WheelSettings(
            speedLimitKmh = u16Le(data, OFFSET_SPEED_LIMIT)?.div(100.0),
            pedalTiltDegrees = i16Le(data, OFFSET_PEDAL_TILT)?.div(100.0),
            pedalSensitivityPercent = u8(data, OFFSET_PEDAL_SENSITIVITY),
            splitRidingAccelerationPercent = u8(data, OFFSET_SPLIT_ACCELERATION),
            splitRidingBrakingPercent = u8(data, OFFSET_SPLIT_BRAKING),
            speakerVolumePercent = u8(data, OFFSET_SPEAKER_VOLUME),
            splitRidingModesEnabled = flags?.let { (it and FLAG_SPLIT_RIDING) != 0 },
            extendedLateralTiltLimit = flags?.let { (it and FLAG_EXTENDED_TILT_LIMIT) != 0 },
        )
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

    private companion object {
        const val RESPONSE_SETTINGS = 0xA0
        const val SETTINGS_SUBCOMMAND = 0x20

        const val OFFSET_SPEED_LIMIT = 2
        const val OFFSET_PEDAL_TILT = 10
        const val OFFSET_PEDAL_SENSITIVITY = 13
        const val OFFSET_SPLIT_ACCELERATION = 15
        const val OFFSET_SPLIT_BRAKING = 16
        const val OFFSET_SPEAKER_VOLUME = 17
        const val OFFSET_FLAGS = 36

        const val FLAG_SPLIT_RIDING = 0x04
        const val FLAG_EXTENDED_TILT_LIMIT = 0x20
    }
}
