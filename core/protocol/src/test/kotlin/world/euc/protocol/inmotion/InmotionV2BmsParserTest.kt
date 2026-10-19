package world.euc.protocol.inmotion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Quadros **reais** capturados numa V11y (fw 2.5.54) com a sonda de debug do
 * app (`docs/09-pendencias-inmotion.md` §4.1), em out/2026.
 */
class InmotionV2BmsParserTest {

    private val parser = InmotionV2BmsParser()

    @Test
    fun `le o quadro de info do pack 1`() {
        val pack = parser.parse(frame(INFO_PACK_1))!!

        assertEquals(1, pack.index)
        assertEquals(2, pack.boardId)
        assertEquals(81.30, pack.voltageVolts!!, 0.001)
        assertEquals(95.52, pack.socPercent!!, 0.001)
        assertEquals(4068, pack.maxCellMv)
        assertEquals(4062, pack.minCellMv)
        assertEquals(12, pack.temperaturesCelsius.size)
        assertEquals(28.0, pack.temperaturesCelsius.first())
        assertEquals(30.0, pack.temperaturesCelsius.last())
    }

    @Test
    fun `le o quadro de info do pack 2`() {
        val pack = parser.parse(frame(INFO_PACK_2))!!

        assertEquals(2, pack.index)
        assertEquals(3, pack.boardId)
        assertEquals(81.30, pack.voltageVolts!!, 0.001)
        assertEquals(95.52, pack.socPercent!!, 0.001)
    }

    @Test
    fun `le as 20 celulas e a soma bate com a tensao do pack`() {
        val pack = parser.parse(frame(CELLS_PACK_1))!!

        assertEquals(20, pack.cellsMv.size)
        assertEquals(4062, pack.minCellMv ?: pack.cellsMv.min())
        assertEquals(4068, pack.cellsMv.max())
        assertEquals(6, pack.cellDeltaMv)
        // A soma das células (81,302 V) reproduz a tensão do quadro de info
        // (81,30 V, arredondada a 0,01 V pelo firmware).
        assertEquals(81.30, pack.cellsMv.sum() / 1000.0, 0.01)
    }

    @Test
    fun `le as celulas do pack 2`() {
        val pack = parser.parse(frame(CELLS_PACK_2))!!

        assertEquals(2, pack.index)
        assertEquals(20, pack.cellsMv.size)
        assertEquals(6, pack.cellDeltaMv)
    }

    @Test
    fun `rotas de pack 3 e 4 usam o mesmo layout`() {
        // As rotas 3 e 4 não respondem na V11y (é dos modelos de 4 packs);
        // os quadros abaixo repetem o pack 1 com a rota trocada, só para
        // exercitar a faixa de rotas do parser.
        val pack3 = parser.parse(frame(INFO_PACK_3))!!
        val pack4 = parser.parse(frame(CELLS_PACK_4))!!

        assertEquals(3, pack3.index)
        assertEquals(4, pack4.index)
        assertTrue(pack4.cellsMv.isNotEmpty())
    }

    @Test
    fun `info e celulas se juntam num pack so`() {
        val info = parser.parse(frame(INFO_PACK_1))!!
        val cells = parser.parse(frame(CELLS_PACK_1))!!

        val merged = info.mergedWith(cells)

        assertEquals(81.30, merged.voltageVolts!!, 0.001)
        assertEquals(95.52, merged.socPercent!!, 0.001)
        assertEquals(20, merged.cellsMv.size)
        assertEquals(6, merged.cellDeltaMv)
    }

    @Test
    fun `quadro de outro tipo nao e BMS`() {
        assertNull(parser.parse(frame(INFO_PACK_1, typeCode = 0x14)))
    }

    @Test
    fun `quadro curto devolve nulo`() {
        assertNull(parser.parse(frame("24 02 81 01")))
    }

    @Test
    fun `subcomando desconhecido devolve nulo`() {
        assertNull(parser.parse(frame("24 02 83 00 00")))
    }

    private fun frame(hex: String, typeCode: Int = 0x16): InmotionFrame {
        val bytes = hex.trim().split(' ').map { it.toInt(16).toByte() }.toByteArray()
        return InmotionFrame(InmotionFrameType.fromCode(typeCode), typeCode, bytes)
    }

    private companion object {
        const val INFO_PACK_1 = "24 02 81 01 00 C3 20 34 06 C2 1F 00 00 02 00 50 25 A8 1F 08 00 " +
            "E4 0F DE 0F 55 00 CC CB CB CE C7 C7 C7 C7 CE CE CE CE 1E 55 FF FF 00 00 00 08 " +
            "00 00 00 00 00 00 00 00 00 00"
        const val INFO_PACK_2 = "25 02 81 01 00 C3 20 34 06 C2 1F 00 00 03 00 50 25 AD 1F 08 00 " +
            "E4 0F DE 0F 55 00 CB CB CB CE C6 C7 C6 C6 CE CE CE CE 1E 55 FF FF 00 00 00 08 " +
            "00 00 00 00 00 00 00 00 00 00"
        const val CELLS_PACK_1 = "24 02 82 E1 0F E1 0F DF 0F DE 0F E0 0F E1 0F E1 0F E0 0F DF 0F " +
            "DF 0F E3 0F E4 0F E4 0F E4 0F E2 0F E2 0F E2 0F E2 0F E0 0F E0 0F"
        const val CELLS_PACK_2 = "25 02 82 E0 0F E2 0F E0 0F E1 0F DE 0F E1 0F E4 0F E4 0F E2 0F " +
            "DF 0F E1 0F E3 0F E1 0F E2 0F E0 0F E3 0F E1 0F E3 0F E0 0F DF 0F"
        const val INFO_PACK_3 = "26 02 81 01 00 C3 20 34 06 C2 1F 00 00 02 00 50 25 A8 1F 08 00 " +
            "E4 0F DE 0F 55 00 CC CB CB CE C7 C7 C7 C7 CE CE CE CE 1E 55 FF FF 00 00 00 08 " +
            "00 00 00 00 00 00 00 00 00 00"
        const val CELLS_PACK_4 = "27 02 82 E1 0F E1 0F DF 0F DE 0F E0 0F E1 0F E1 0F E0 0F DF 0F " +
            "DF 0F E3 0F E4 0F E4 0F E4 0F E2 0F E2 0F E2 0F E2 0F E0 0F E0 0F"
    }
}
