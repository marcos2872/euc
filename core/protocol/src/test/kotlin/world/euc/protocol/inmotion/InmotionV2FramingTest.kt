package world.euc.protocol.inmotion

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InmotionV2FramingTest {

    @Test
    fun `encode monta o quadro de lock com checksum XOR`() {
        // Vetor real: comando de lock do app oficial (payload 60 31 01, tipo 0x14).
        val frame = InmotionV2Framing.encode(
            InmotionFrameType.DEFAULT,
            byteArrayOf(0x60, 0x31, 0x01),
        )

        assertContentEquals(
            byteArrayOf(0xAA.toByte(), 0xAA.toByte(), 0x14, 0x03, 0x60, 0x31, 0x01, 0x47),
            frame,
        )
    }

    @Test
    fun `encode aplica escape em bytes reservados do payload e do checksum`() {
        // Payload 60 AA: o AA vira A5 AA; checksum 0xDC permanece cru.
        val frame = InmotionV2Framing.encode(InmotionFrameType.DEFAULT, byteArrayOf(0x60, 0xAA.toByte()))

        assertContentEquals(
            byteArrayOf(0xAA.toByte(), 0xAA.toByte(), 0x14, 0x02, 0x60, 0xA5.toByte(), 0xAA.toByte(), 0xDC.toByte()),
            frame,
        )
    }

    @Test
    fun `decode devolve tipo e payload de um quadro válido`() {
        val frame = byteArrayOf(0xAA.toByte(), 0xAA.toByte(), 0x14, 0x03, 0x60, 0x31, 0x01, 0x47)

        val decoded = InmotionV2Framing.decode(frame)

        assertNotNull(decoded)
        assertEquals(InmotionFrameType.DEFAULT, decoded.type)
        assertContentEquals(byteArrayOf(0x60, 0x31, 0x01), decoded.payload)
    }

    @Test
    fun `decode rejeita checksum inválido`() {
        val corrupted = byteArrayOf(0xAA.toByte(), 0xAA.toByte(), 0x14, 0x03, 0x60, 0x31, 0x01, 0x00)

        assertNull(InmotionV2Framing.decode(corrupted))
    }

    @Test
    fun `decode rejeita header inválido`() {
        val wrongHeader = byteArrayOf(0x55, 0xAA.toByte(), 0x14, 0x03, 0x60, 0x31, 0x01, 0x47)

        assertNull(InmotionV2Framing.decode(wrongHeader))
    }

    @Test
    fun `decode rejeita quadro truncado`() {
        val truncated = byteArrayOf(0xAA.toByte(), 0xAA.toByte(), 0x14, 0x03, 0x60)

        assertNull(InmotionV2Framing.decode(truncated))
    }

    @Test
    fun `decode preserva tipo desconhecido para inspeção`() {
        // Tipo 0x99 não pertence ao conjunto conhecido, mas o quadro é válido.
        val payload = byteArrayOf(0x01)
        val checksum = 0xAA xor 0xAA xor 0x99 xor 0x01 xor 0x01
        val frame = byteArrayOf(0xAA.toByte(), 0xAA.toByte(), 0x99.toByte(), 0x01, 0x01, checksum.toByte())

        val decoded = InmotionV2Framing.decode(frame)

        assertNotNull(decoded)
        assertNull(decoded.type)
        assertEquals(0x99, decoded.typeCode)
    }

    @Test
    fun `escape e unescape são inversos`() {
        val original = byteArrayOf(0x00, 0xAA.toByte(), 0xA5.toByte(), 0xFF.toByte(), 0x60)

        val restored = InmotionV2Framing.unescape(InmotionV2Framing.escape(original))

        assertContentEquals(original, restored)
    }
}
