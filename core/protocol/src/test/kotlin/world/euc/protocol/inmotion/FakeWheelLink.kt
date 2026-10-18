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

    /** Quando true, responde ao handshake de autenticação automaticamente. */
    var answerAuthentication: Boolean = false

    /** Blob devolvido na resposta de chave (16 bytes). */
    val authKey: ByteArray = ByteArray(16) { (it + 1).toByte() }

    override suspend fun write(bytes: ByteArray) {
        writtenFrames += bytes.copyOf()
        if (!answerAuthentication) return
        val command = InmotionV2Framing.decode(bytes) ?: return
        when {
            command.payload.contentEquals(byteArrayOf(0x02, 0x00, 0x00, 0x02)) -> {
                // resposta de requestAuthKey: prefixo 02 80 02 + 16 bytes
                incomingFlow.tryEmit(
                    InmotionV2Framing.encode(
                        InmotionFrameType.AUTH,
                        byteArrayOf(0x02, 0x80.toByte(), 0x02) + authKey,
                    ),
                )
            }
            command.payload.size == 20 && command.payload[3] == 0x82.toByte() -> {
                // confirmação de verifyAuth
                incomingFlow.tryEmit(
                    InmotionV2Framing.encode(
                        InmotionFrameType.AUTH,
                        byteArrayOf(0x02, 0x80.toByte(), 0x82.toByte(), 0x01),
                    ),
                )
            }
        }
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

    /** Payloads ignorando o handshake de autenticação (para asserções de protocolo). */
    fun writtenProtocolPayloads(): List<ByteArray> =
        writtenFrames
            .mapNotNull { InmotionV2Framing.decode(it) }
            .filter { it.type != InmotionFrameType.AUTH }
            .map { it.payload }
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

    fun settings(speedLimitRaw: Int = 2500, speakerVolume: Int = 20): ByteArray {
        val data = ByteArray(40)
        val unsigned = speedLimitRaw and 0xFFFF
        data[0] = (unsigned and 0xFF).toByte()
        data[1] = ((unsigned shr 8) and 0xFF).toByte()
        data[15] = speakerVolume.toByte()
        return frame(byteArrayOf(0xA0.toByte(), 0x20) + data)
    }

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
