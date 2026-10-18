package world.euc.protocol.inmotion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InmotionV2SettingsParserTest {

    private val parser = InmotionV2SettingsParser()

    @Test
    fun `extrai as configurações reportadas pela V11y`() {
        val frame = settingsFrame(
            speedLimitRaw = 2500,          // 25.00 km/h
            pedalTiltRaw = -150,           // -1.50°
            pedalSensitivity = 100,
            splitAcceleration = 50,
            splitBraking = 60,
            speakerVolume = 20,
            flags = 0x24,                  // split ligado (0x04) + tilt estendido (0x20)
        )

        val settings = parser.parse(frame)!!

        assertEquals(25.0, settings.speedLimitKmh!!, 0.001)
        assertEquals(-1.5, settings.pedalTiltDegrees!!, 0.001)
        assertEquals(100, settings.pedalSensitivityPercent)
        assertEquals(50, settings.splitRidingAccelerationPercent)
        assertEquals(60, settings.splitRidingBrakingPercent)
        assertEquals(20, settings.speakerVolumePercent)
        assertEquals(true, settings.splitRidingModesEnabled)
        assertEquals(true, settings.extendedLateralTiltLimit)
    }

    @Test
    fun `flags zeradas desligam as opções booleanas`() {
        val settings = parser.parse(settingsFrame(flags = 0x00))!!

        assertEquals(false, settings.splitRidingModesEnabled)
        assertEquals(false, settings.extendedLateralTiltLimit)
    }

    @Test
    fun `quadro curto devolve apenas os campos presentes`() {
        // Resposta com 14 bytes de dados: cobre velocidade e tilt, não o volume.
        val payload = ByteArray(2 + 14)
        payload[0] = 0xA0.toByte()
        payload[1] = 0x20
        payload[2] = 0x64
        payload[3] = 0x0A  // 2660 = 26.60 km/h
        val frame = InmotionV2Framing.decode(InmotionV2Framing.encode(InmotionFrameType.DEFAULT, payload))!!

        val settings = parser.parse(frame)!!

        assertEquals(26.6, settings.speedLimitKmh!!, 0.001)
        assertNull(settings.speakerVolumePercent)
        assertNull(settings.splitRidingModesEnabled)
    }

    @Test
    fun `ignora quadros que não são de configurações`() {
        val telemetry = InmotionV2Framing.decode(TestFrames.values())!!

        assertNull(parser.parse(telemetry))
    }

    private fun settingsFrame(
        speedLimitRaw: Int = 0,
        pedalTiltRaw: Int = 0,
        pedalSensitivity: Int = 0,
        splitAcceleration: Int = 0,
        splitBraking: Int = 0,
        speakerVolume: Int = 0,
        flags: Int = 0,
    ): InmotionFrame {
        val data = ByteArray(40)
        putU16(data, 0, speedLimitRaw)
        putU16(data, 8, pedalTiltRaw)
        data[11] = pedalSensitivity.toByte()
        data[13] = splitAcceleration.toByte()
        data[14] = splitBraking.toByte()
        data[15] = speakerVolume.toByte()
        data[34] = flags.toByte()

        val payload = byteArrayOf(0xA0.toByte(), 0x20) + data
        return InmotionV2Framing.decode(InmotionV2Framing.encode(InmotionFrameType.DEFAULT, payload))!!
    }

    private fun putU16(data: ByteArray, offset: Int, value: Int) {
        val unsigned = value and 0xFFFF
        data[offset] = (unsigned and 0xFF).toByte()
        data[offset + 1] = ((unsigned shr 8) and 0xFF).toByte()
    }
}
