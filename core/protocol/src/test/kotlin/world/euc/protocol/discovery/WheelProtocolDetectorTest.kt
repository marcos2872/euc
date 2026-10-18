package world.euc.protocol.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WheelProtocolDetectorTest {

    @Test
    fun `Nordic UART com nome V11Y detecta Inmotion V2 com alta confiança`() {
        val candidates = WheelProtocolDetector.detect(
            BleDeviceProfile(name = "V11Y-12345678", serviceUuids = setOf(WheelProtocolDetector.NORDIC_UART)),
        )

        assertEquals(WheelProtocol.INMOTION_V2, candidates.first().protocol)
        assertEquals(Confidence.HIGH, candidates.first().confidence)
    }

    @Test
    fun `Nordic UART com nome de Ninebot detecta One Z`() {
        val candidates = WheelProtocolDetector.detect(
            BleDeviceProfile(name = "Ninebot One Z", serviceUuids = setOf(WheelProtocolDetector.NORDIC_UART)),
        )

        assertEquals(WheelProtocol.NINEBOT_NEW, candidates.first().protocol)
        assertEquals(Confidence.HIGH, candidates.first().confidence)
    }

    @Test
    fun `Nordic UART sem nome reconhecido sugere Inmotion V2 e Ninebot`() {
        val candidates = WheelProtocolDetector.detect(
            BleDeviceProfile(name = null, serviceUuids = setOf(WheelProtocolDetector.NORDIC_UART)),
        )

        assertEquals(WheelProtocol.INMOTION_V2, candidates.first().protocol)
        assertEquals(Confidence.MEDIUM, candidates.first().confidence)
        assertTrue(candidates.any { it.protocol == WheelProtocol.NINEBOT_NEW })
    }

    @Test
    fun `serviço FFE5 detecta Inmotion pré-V11`() {
        val candidates = WheelProtocolDetector.detect(
            BleDeviceProfile(
                name = "V10F-1234",
                serviceUuids = setOf(WheelProtocolDetector.FFE0, WheelProtocolDetector.FFE5),
            ),
        )

        assertEquals(WheelProtocol.INMOTION_V1, candidates.first().protocol)
        assertEquals(Confidence.HIGH, candidates.first().confidence)
    }

    @Test
    fun `nome KingSong com FFE0 tem prioridade sobre heurística de serviços`() {
        val candidates = WheelProtocolDetector.detect(
            BleDeviceProfile(
                name = "KS-16X",
                serviceUuids = setOf(WheelProtocolDetector.FFE0, WheelProtocolDetector.FFF0),
            ),
        )

        assertEquals(WheelProtocol.KINGSONG, candidates.first().protocol)
        assertEquals(Confidence.HIGH, candidates.first().confidence)
    }

    @Test
    fun `nome de Veteran detecta Veteran`() {
        val candidates = WheelProtocolDetector.detect(
            BleDeviceProfile(name = "Sherman", serviceUuids = setOf(WheelProtocolDetector.FFE0)),
        )

        assertEquals(WheelProtocol.VETERAN, candidates.first().protocol)
    }

    @Test
    fun `FFE0 com FFF0 sem nome sugere KingSong`() {
        val candidates = WheelProtocolDetector.detect(
            BleDeviceProfile(
                name = null,
                serviceUuids = setOf(WheelProtocolDetector.FFE0, WheelProtocolDetector.FFF0),
            ),
        )

        assertEquals(WheelProtocol.KINGSONG, candidates.first().protocol)
        assertEquals(Confidence.MEDIUM, candidates.first().confidence)
    }

    @Test
    fun `FFE0 com informações do dispositivo sem nome sugere Gotway`() {
        val candidates = WheelProtocolDetector.detect(
            BleDeviceProfile(
                name = null,
                serviceUuids = setOf(WheelProtocolDetector.FFE0, WheelProtocolDetector.DEVICE_INFORMATION),
            ),
        )

        assertEquals(WheelProtocol.GOTWAY, candidates.first().protocol)
    }

    @Test
    fun `serviços desconhecidos devolvem UNKNOWN`() {
        val candidates = WheelProtocolDetector.detect(
            BleDeviceProfile(name = "fone de ouvido", serviceUuids = setOf("0000180f-0000-1000-8000-00805f9b34fb")),
        )

        assertEquals(1, candidates.size)
        assertEquals(WheelProtocol.UNKNOWN, candidates.first().protocol)
    }

    @Test
    fun `candidatos são únicos por protocolo e ordenados por confiança`() {
        val candidates = WheelProtocolDetector.detect(
            BleDeviceProfile(
                name = null,
                serviceUuids = setOf(WheelProtocolDetector.FFE0, WheelProtocolDetector.NORDIC_UART),
            ),
        )

        assertEquals(candidates.size, candidates.distinctBy { it.protocol }.size)
        assertEquals(candidates.sortedBy { it.confidence.ordinal }, candidates)
    }
}
