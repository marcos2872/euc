package world.euc.protocol.inmotion

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InmotionV2FrameReaderTest {

    private val lockFrame = byteArrayOf(
        0xAA.toByte(), 0xAA.toByte(), 0x14, 0x03, 0x60, 0x31, 0x01, 0x47,
    )

    @Test
    fun `remonta um quadro entregue em pedaços`() {
        val reader = InmotionV2FrameReader()

        assertTrue(reader.offer(lockFrame.copyOfRange(0, 3)).isEmpty())
        assertTrue(reader.offer(lockFrame.copyOfRange(3, 6)).isEmpty())

        val frames = reader.offer(lockFrame.copyOfRange(6, lockFrame.size))

        assertEquals(1, frames.size)
        assertContentEquals(byteArrayOf(0x60, 0x31, 0x01), frames.first().payload)
    }

    @Test
    fun `remonta quadro com escape partido entre notificações`() {
        val frame = InmotionV2Framing.encode(InmotionFrameType.DEFAULT, byteArrayOf(0x60, 0xAA.toByte()))
        val reader = InmotionV2FrameReader()

        // Corta exatamente entre o prefixo de escape (A5) e o byte escapado (AA).
        val escapeIndex = frame.indexOf(0xA5.toByte())
        assertTrue(reader.offer(frame.copyOfRange(0, escapeIndex + 1)).isEmpty())

        val frames = reader.offer(frame.copyOfRange(escapeIndex + 1, frame.size))

        assertEquals(1, frames.size)
        assertContentEquals(byteArrayOf(0x60, 0xAA.toByte()), frames.first().payload)
    }

    @Test
    fun `descarta lixo antes do header e ainda entrega o quadro`() {
        val reader = InmotionV2FrameReader()
        val noisy = byteArrayOf(0x00, 0x12, 0x34) + lockFrame

        val frames = reader.offer(noisy)

        assertEquals(1, frames.size)
        assertContentEquals(byteArrayOf(0x60, 0x31, 0x01), frames.first().payload)
    }

    @Test
    fun `entrega múltiplos quadros de uma só notificação`() {
        val reader = InmotionV2FrameReader()

        val frames = reader.offer(lockFrame + lockFrame)

        assertEquals(2, frames.size)
    }

    @Test
    fun `ressincroniza após quadro corrompido`() {
        val reader = InmotionV2FrameReader()
        val corrupted = lockFrame.copyOf().apply { this[7] = 0x00 } // checksum inválido

        val frames = reader.offer(corrupted + lockFrame)

        assertEquals(1, frames.size)
        assertContentEquals(byteArrayOf(0x60, 0x31, 0x01), frames.first().payload)
    }

    @Test
    fun `reset limpa o estado acumulado`() {
        val reader = InmotionV2FrameReader()
        reader.offer(lockFrame.copyOfRange(0, 4))

        reader.reset()
        val frames = reader.offer(lockFrame)

        assertEquals(1, frames.size)
    }
}
