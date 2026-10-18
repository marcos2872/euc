package world.euc.protocol.inmotion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InmotionV2ResponsesTest {

    @Test
    fun `código de resposta é o comando com o bit alto ligado`() {
        assertEquals(0x84, InmotionV2Responses.expectedResponseCode(InmotionV2Commands.readValues()))
        assertEquals(0x90, InmotionV2Responses.expectedResponseCode(InmotionV2Commands.readTrip()))
        assertEquals(0x91, InmotionV2Responses.expectedResponseCode(InmotionV2Commands.readTotalStatistics()))
        assertEquals(0xA0, InmotionV2Responses.expectedResponseCode(InmotionV2Commands.readSettings()))
    }

    @Test
    fun `identificação de resposta considera o subcomando das consultas INITIAL`() {
        val modelResponse = decode(TestFrames.carType(modelByte = 6, variant = 2))
        val serialResponse = decode(TestFrames.serial("V11Y0000000000000001"))

        assertTrue(InmotionV2Responses.isResponseTo(modelResponse, InmotionV2Commands.readModel()))
        assertFalse(InmotionV2Responses.isResponseTo(modelResponse, InmotionV2Commands.readSerialNumber()))
        assertTrue(InmotionV2Responses.isResponseTo(serialResponse, InmotionV2Commands.readSerialNumber()))
    }

    @Test
    fun `resposta de telemetria casa com o poll de valores`() {
        val values = decode(TestFrames.values())

        assertTrue(InmotionV2Responses.isResponseTo(values, InmotionV2Commands.readValues()))
        assertFalse(InmotionV2Responses.isResponseTo(values, InmotionV2Commands.readSettings()))
    }

    @Test
    fun `interpreta modelo V11 com variante como V11Y`() {
        val model = InmotionV2Responses.parseModel(decode(TestFrames.carType(modelByte = 6, variant = 2)))

        assertNotNull(model)
        assertEquals(InmotionModel.V11Y, model.model)
        assertEquals("V11Y", model.displayName)
        assertEquals(20, model.batterySeries)
        assertEquals(4, model.batteryParallel)
    }

    @Test
    fun `interpreta V13 com 30 séries e 8 paralelos`() {
        val model = InmotionV2Responses.parseModel(decode(TestFrames.carType(modelByte = 8, variant = 0)))!!

        assertEquals(InmotionModel.V13, model.model)
        assertEquals("V13", model.displayName)
        assertEquals(30, model.batterySeries)
        assertEquals(8, model.batteryParallel)
    }

    @Test
    fun `modelo desconhecido cai no fallback sem quebrar`() {
        val model = InmotionV2Responses.parseModel(decode(TestFrames.carType(modelByte = 99, variant = 0)))!!

        assertEquals(InmotionModel.UNKNOWN, model.model)
    }

    @Test
    fun `extrai número de série ASCII`() {
        val serial = InmotionV2Responses.parseSerialNumber(decode(TestFrames.serial("V11Y0000000000000001")))

        assertEquals("V11Y0000000000000001", serial)
    }

    @Test
    fun `extrai versões de firmware e compara versões`() {
        val versions = InmotionV2Responses.parseFirmwareVersions(decode(TestFrames.firmware(1, 4, 0)))

        assertNotNull(versions)
        assertEquals("1.4.0", versions.main)
        assertTrue(versions.mainAtLeast("1.4.0"))
        assertTrue(versions.mainAtLeast("1.3.9"))
        assertFalse(versions.mainAtLeast("1.4.1"))
        assertFalse(versions.mainAtLeast("2.0.0"))
    }

    @Test
    fun `respostas de outros comandos não viram modelo nem serial`() {
        val values = decode(TestFrames.values())

        assertNull(InmotionV2Responses.parseModel(values))
        assertNull(InmotionV2Responses.parseSerialNumber(values))
        assertNull(InmotionV2Responses.parseFirmwareVersions(values))
    }

    private fun decode(bytes: ByteArray): InmotionFrame =
        requireNotNull(InmotionV2Framing.decode(bytes))
}
