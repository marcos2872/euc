package world.euc.protocol.inmotion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InmotionV2StatisticsParserTest {

    private val parser = InmotionV2StatisticsParser()

    @Test
    fun `le a distancia da viagem no quadro 0x90`() {
        val frame = tripFrame(distanceRaw = 123_456) // 1234,56 km

        val statistics = parser.parseTrip(frame)!!

        assertEquals(1234.56, statistics.tripKm!!, 0.001)
        assertNull(statistics.totalKm)
    }

    @Test
    fun `le distancia e horas totais no quadro 0x91`() {
        val frame = totalFrame(distanceRaw = 987_654, secondsRaw = 7_200) // 9876,54 km / 2 h

        val statistics = parser.parseTotal(frame)!!

        assertEquals(9876.54, statistics.totalKm!!, 0.001)
        assertEquals(2.0, statistics.totalHours!!, 0.001)
    }

    @Test
    fun `inteiro sem sinal com bit alto nao vira negativo`() {
        // 0xFFFFFF00 = 4294967040 -> 42.949.670,40 km (valor absurdo, mas não negativo)
        val frame = tripFrame(distanceRaw = 0xFFFFFF00.toInt())

        assertEquals(42_949_670.4, parser.parseTrip(frame)!!.tripKm!!, 0.1)
    }

    @Test
    fun `quadro curto devolve nulo`() {
        val short = InmotionV2Framing.decode(
            InmotionV2Framing.encode(InmotionFrameType.DEFAULT, byteArrayOf(0x90.toByte(), 0, 0))!!,
        )!!

        assertNull(parser.parseTrip(short))
        assertNull(parser.parseTotal(short))
    }

    @Test
    fun `ignora quadros de outros tipos`() {
        val telemetry = InmotionV2Framing.decode(TestFrames.values())!!
        val settings = InmotionV2Framing.decode(TestFrames.settings())!!

        assertNull(parser.parse(telemetry))
        assertNull(parser.parse(settings))
    }

    @Test
    fun `parse aceita os dois quadros de odometro`() {
        assertEquals(10.0, parser.parse(tripFrame(1_000))!!.tripKm!!, 0.001)
        assertEquals(20.0, parser.parse(totalFrame(2_000, 3_600))!!.totalKm!!, 0.001)
    }

    private fun tripFrame(distanceRaw: Int): InmotionFrame {
        val payload = ByteArray(32)
        payload[0] = 0x90.toByte()
        putU32(payload, InmotionV2StatisticsParser.OFFSET_TRIP_DISTANCE, distanceRaw)
        return InmotionV2Framing.decode(
            InmotionV2Framing.encode(InmotionFrameType.DEFAULT, payload)!!,
        )!!
    }

    private fun totalFrame(distanceRaw: Int, secondsRaw: Int): InmotionFrame {
        val payload = ByteArray(16)
        payload[0] = 0x91.toByte()
        putU32(payload, InmotionV2StatisticsParser.OFFSET_TOTAL_DISTANCE, distanceRaw)
        putU32(payload, InmotionV2StatisticsParser.OFFSET_TOTAL_HOURS, secondsRaw)
        return InmotionV2Framing.decode(
            InmotionV2Framing.encode(InmotionFrameType.DEFAULT, payload)!!,
        )!!
    }

    private fun putU32(data: ByteArray, offset: Int, value: Int) {
        for (index in 0 until 4) {
            data[offset + index] = ((value shr (8 * index)) and 0xFF).toByte()
        }
    }
}
