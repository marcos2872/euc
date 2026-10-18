package world.euc.protocol.inmotion

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class InmotionV2CommandsTest {

    @Test
    fun `lock monta payload de controle com subcódigo 0x31`() {
        val command = InmotionV2Commands.lock(locked = true)

        assertEquals(InmotionFrameType.DEFAULT, command.frameType)
        assertContentEquals(byteArrayOf(0x60, 0x31, 0x01), command.payload)
    }

    @Test
    fun `unlock envia zero`() {
        assertContentEquals(byteArrayOf(0x60, 0x31, 0x00), InmotionV2Commands.lock(locked = false).payload)
    }

    @Test
    fun `farol usa opcode moderno por padrão e legado quando pedido`() {
        assertContentEquals(byteArrayOf(0x60, 0x50, 0x01), InmotionV2Commands.headlight(on = true).payload)
        assertContentEquals(
            byteArrayOf(0x60, 0x40, 0x01),
            InmotionV2Commands.headlight(on = true, legacyHeadlightOpcode = true).payload,
        )
    }

    @Test
    fun `buzina usa som 0x18 e volume 1 por padrão`() {
        assertContentEquals(byteArrayOf(0x60, 0x51, 0x18, 0x01), InmotionV2Commands.horn().payload)
    }

    @Test
    fun `buzina da V11y usa playBeep e evita o tom contínuo`() {
        // A V11y pertence à família V13/V14: playBeep (0x02, 100).
        // O som 0x18 (playSound) na V11y é um tom contínuo — não usar.
        assertContentEquals(
            byteArrayOf(0x60, 0x51, 0x02, 0x64),
            InmotionV2Commands.hornFor(InmotionModel.V11Y).payload,
        )
        assertContentEquals(
            byteArrayOf(0x60, 0x51, 0x02, 0x64),
            InmotionV2Commands.hornFor(InmotionModel.V14).payload,
        )
        assertContentEquals(
            byteArrayOf(0x60, 0x51, 0x02, 0x64),
            InmotionV2Commands.hornFor(InmotionModel.V13).payload,
        )
    }

    @Test
    fun `buzina da V12 e da V11 usa playSound`() {
        assertContentEquals(
            byteArrayOf(0x60, 0x51, 0x18, 0x01),
            InmotionV2Commands.hornFor(InmotionModel.V12).payload,
        )
        assertContentEquals(
            byteArrayOf(0x60, 0x51, 0x18, 0x01),
            InmotionV2Commands.hornFor(InmotionModel.V11).payload,
        )
    }

    @Test
    fun `lock estendido adiciona roteamento e usa quadro EXTENDED`() {
        val command = InmotionV2Commands.lock(locked = true, extended = true)

        assertEquals(InmotionFrameType.EXTENDED, command.frameType)
        assertContentEquals(byteArrayOf(0x02, 0x21, 0x60, 0x31, 0x01), command.payload)

        // Vetor do app oficial (V14 lock): AA AA 16 05 02 21 60 31 01 60
        assertContentEquals(
            byteArrayOf(
                0xAA.toByte(), 0xAA.toByte(), 0x16, 0x05, 0x02, 0x21, 0x60, 0x31, 0x01, 0x60,
            ),
            command.toFrame(),
        )
    }

    @Test
    fun `tiltback converte km-h para u16 little-endian com escala 100`() {
        // 30 km/h -> 3000 = 0x0BB8 -> LE: B8 0B
        assertContentEquals(byteArrayOf(0x60, 0x21, 0xB8.toByte(), 0x0B), InmotionV2Commands.speedTiltback(30.0).payload)
    }

    @Test
    fun `tilt dos pedais converte graus com escala 100`() {
        // 1.5° -> 150 = 0x0096 -> LE: 96 00
        assertContentEquals(byteArrayOf(0x60, 0x22, 0x96.toByte(), 0x00), InmotionV2Commands.pedalTilt(1.5).payload)
    }

    @Test
    fun `standby timer envia segundos como u16 little-endian`() {
        // 900 s = 0x0384 -> LE: 84 03
        assertContentEquals(byteArrayOf(0x60, 0x28, 0x84.toByte(), 0x03), InmotionV2Commands.standbyTimer(900).payload)
    }

    @Test
    fun `sensibilidade dos pedais envia valor e teto 100`() {
        assertContentEquals(byteArrayOf(0x60, 0x25, 0x32, 0x64), InmotionV2Commands.pedalSensitivity(50).payload)
    }

    @Test
    fun `volume limita percentual ao intervalo 0-100`() {
        assertContentEquals(byteArrayOf(0x60, 0x26, 0x64), InmotionV2Commands.speakerVolume(150).payload)
    }

    @Test
    fun `split riding modes usa opcode da V11Y por padrão`() {
        assertContentEquals(byteArrayOf(0x60, 0x3E, 0x01), InmotionV2Commands.splitRidingModes(true).payload)
        assertContentEquals(
            byteArrayOf(0x60, 0x42, 0x01),
            InmotionV2Commands.splitRidingModes(true, legacyOpcode = true).payload,
        )
    }

    @Test
    fun `split riding modes settings envia aceleração e frenagem`() {
        assertContentEquals(
            byteArrayOf(0x60, 0x3F, 0x32, 0x3C),
            InmotionV2Commands.splitRidingModesSettings(acceleration = 50, braking = 60).payload,
        )
    }

    @Test
    fun `calibração de yaw e de pedais usa payloads distintos`() {
        assertContentEquals(
            byteArrayOf(0x60, 0x52, 0x01, 0x00, 0x01),
            InmotionV2Commands.calibration(yaw = true).payload,
        )
        assertContentEquals(
            byteArrayOf(0x60, 0x52, 0x01, 0x01, 0x00),
            InmotionV2Commands.calibration(yaw = false).payload,
        )
    }

    @Test
    fun `consultas de identificação usam o tipo INITIAL`() {
        val model = InmotionV2Commands.readModel()
        assertEquals(InmotionFrameType.INITIAL, model.frameType)
        assertContentEquals(byteArrayOf(0x02, 0x01), model.payload)

        assertContentEquals(byteArrayOf(0x02, 0x02), InmotionV2Commands.readSerialNumber().payload)
        assertContentEquals(byteArrayOf(0x02, 0x06), InmotionV2Commands.readFirmwareVersions().payload)
    }

    @Test
    fun `poll de telemetria usa comando 0x04 no tipo DEFAULT`() {
        val poll = InmotionV2Commands.readValues()

        assertEquals(InmotionFrameType.DEFAULT, poll.frameType)
        assertContentEquals(byteArrayOf(0x04), poll.payload)
    }

    @Test
    fun `toFrame serializa o comando com framing completo`() {
        val frame = InmotionV2Commands.lock(locked = true).toFrame()

        assertContentEquals(
            byteArrayOf(0xAA.toByte(), 0xAA.toByte(), 0x14, 0x03, 0x60, 0x31, 0x01, 0x47),
            frame,
        )
    }
}
