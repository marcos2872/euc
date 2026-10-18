package world.euc.protocol.inmotion

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import world.euc.protocol.link.WheelLink

/**
 * Link em memória para testes: registra o que foi escrito e permite injetar
 * notificações como se viessem da roda.
 */
class FakeWheelLink : WheelLink {

    private val incomingFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    private val writtenFrames = mutableListOf<ByteArray>()

    override val incoming: Flow<ByteArray> = incomingFlow.asSharedFlow()

    override suspend fun write(bytes: ByteArray) {
        writtenFrames += bytes.copyOf()
    }

    /** Injeta bytes recebidos (como uma notificação BLE). */
    suspend fun emit(bytes: ByteArray) {
        incomingFlow.emit(bytes)
    }

    /** Payloads de todos os quadros escritos, na ordem. */
    fun writtenPayloads(): List<ByteArray> =
        writtenFrames.map { requireNotNull(InmotionV2Framing.decode(it)).payload }

    /** Quantas vezes um payload específico foi escrito. */
    fun writeCount(payload: ByteArray): Int =
        writtenPayloads().count { it.contentEquals(payload) }

    /** Total de quadros escritos. */
    fun writeTotal(): Int = writtenFrames.size
}

/** Fábrica de quadros de resposta para os testes. */
object TestFrames {

    fun carType(modelByte: Int, variant: Int): ByteArray = frame(
        byteArrayOf(0x82.toByte(), 0x01, 0x00, modelByte.toByte(), variant.toByte()),
    )

    fun serial(value: String): ByteArray {
        val ascii = value.toByteArray(Charsets.US_ASCII)
        val payload = ByteArray(2 + 21)
        payload[0] = 0x82.toByte()
        payload[1] = 0x02
        ascii.copyInto(payload, destinationOffset = 2, endIndex = minOf(ascii.size, 21))
        return frame(payload)
    }

    fun firmware(major: Int, minor: Int, patch: Int): ByteArray {
        val payload = ByteArray(24)
        payload[0] = 0x82.toByte()
        payload[1] = 0x06
        payload[12] = (patch and 0xFF).toByte()
        payload[13] = ((patch shr 8) and 0xFF).toByte()
        payload[14] = minor.toByte()
        payload[15] = major.toByte()
        return frame(payload)
    }

    fun settings(): ByteArray = frame(byteArrayOf(0xA0.toByte(), 0x20, 0x00, 0x00))

    fun values(
        voltageRaw: Int = 8420,
        speedRaw: Int = 2500,
        batteryRaw: Int = 7800,
        loadRaw: Int = 3500,
    ): ByteArray {
        val data = ByteArray(80)
        putU16(data, 0, voltageRaw)
        putU16(data, 8, speedRaw)
        putU16(data, 14, loadRaw)
        putU16(data, 34, batteryRaw)
        return frame(byteArrayOf(0x84.toByte()) + data)
    }

    fun frame(payload: ByteArray): ByteArray = InmotionV2Framing.encode(InmotionFrameType.DEFAULT, payload)

    private fun putU16(data: ByteArray, offset: Int, value: Int) {
        val unsigned = value and 0xFFFF
        data[offset] = (unsigned and 0xFF).toByte()
        data[offset + 1] = ((unsigned shr 8) and 0xFF).toByte()
    }
}
