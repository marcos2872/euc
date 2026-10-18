package world.euc.protocol.inmotion

/**
 * Tipo de quadro do protocolo Inmotion V2 (família V11+).
 *
 * O byte de tipo informa à roda a classe do payload:
 * - INITIAL: consultas de identificação (carType, serial, firmware)
 * - DEFAULT: leitura de estado e comandos de configuração
 * - EXTENDED: comandos sensíveis (V12+), com roteamento fixo `02 21`
 * - AUTH: handshake de autenticação (obrigatório para lock em V12+)
 *
 * Referência: análise do EUC World 2.66.0 (`p364uh/C5937z.java`, `m11214W`) e
 * documentação comunitária do eucplanet (MIT).
 */
enum class InmotionFrameType(val code: Int) {
    INITIAL(0x11),
    AUTH(0x13),
    DEFAULT(0x14),
    EXTENDED(0x16),
    ;

    companion object {
        fun fromCode(code: Int): InmotionFrameType? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Quadro Inmotion V2 decodificado.
 *
 * [type] é nulo quando o byte de tipo não pertence ao conjunto conhecido —
 * o quadro ainda é preservado para inspeção e depuração.
 */
class InmotionFrame(
    val type: InmotionFrameType?,
    val typeCode: Int,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is InmotionFrame &&
            other.typeCode == typeCode &&
            other.payload.contentEquals(payload)

    override fun hashCode(): Int = 31 * typeCode + payload.contentHashCode()

    override fun toString(): String =
        "InmotionFrame(type=$type, payload=${payload.joinToString(" ") { "%02X".format(it) }})"
}

/**
 * Codec de quadros Inmotion V2.
 *
 * Formato no fio (após o header, sujeito a escape):
 * ```
 * AA AA <tipo> <tamanho> <payload...> <xor>
 * ```
 * - `xor` = XOR de todos os bytes desde `AA AA` até o fim do payload.
 * - Byte stuffing: `0xAA` vira `A5 AA` e `0xA5` vira `A5 A5` (payload + xor).
 *
 * Fontes: EUC World (`m11214W` para TX, `m11155D0`/`m11185Z` para RX) e
 * eucplanet `docs/protocols/inmotion_v2.md`.
 */
object InmotionV2Framing {

    const val START_BYTE: Int = 0xAA
    const val ESCAPE_BYTE: Int = 0xA5

    private const val HEADER_SIZE = 4

    /** Monta o quadro completo pronto para enviar pela característica de escrita. */
    fun encode(type: InmotionFrameType, payload: ByteArray): ByteArray {
        val body = ByteArray(payload.size + 1)
        payload.copyInto(body)
        body[payload.size] = checksum(type, payload)

        val escapedBody = escape(body)
        val frame = ByteArray(HEADER_SIZE + escapedBody.size)
        frame[0] = START_BYTE.toByte()
        frame[1] = START_BYTE.toByte()
        frame[2] = type.code.toByte()
        frame[3] = payload.size.toByte()
        escapedBody.copyInto(frame, HEADER_SIZE)
        return frame
    }

    /**
     * Decodifica um quadro já sem escape. Retorna `null` se o header for
     * inválido, o tamanho não bater com os bytes disponíveis ou o checksum
     * divergir.
     */
    fun decode(frame: ByteArray): InmotionFrame? {
        if (frame.size < HEADER_SIZE + 1) return null
        if (frame[0] != START_BYTE.toByte() || frame[1] != START_BYTE.toByte()) return null

        val typeCode = frame[2].toInt() and 0xFF
        val payloadSize = frame[3].toInt() and 0xFF
        if (frame.size != HEADER_SIZE + payloadSize + 1) return null

        val expected = checksumBytes(frame, HEADER_SIZE + payloadSize)
        val actual = frame[HEADER_SIZE + payloadSize].toInt() and 0xFF
        if (expected != actual) return null

        val payload = frame.copyOfRange(HEADER_SIZE, HEADER_SIZE + payloadSize)
        return InmotionFrame(InmotionFrameType.fromCode(typeCode), typeCode, payload)
    }

    /** Remove o byte stuffing de um trecho recebido. */
    fun unescape(bytes: ByteArray): ByteArray {
        val out = ArrayList<Byte>(bytes.size)
        var escaped = false
        for (byte in bytes) {
            when {
                escaped -> {
                    out.add(byte)
                    escaped = false
                }
                byte.toInt() and 0xFF == ESCAPE_BYTE -> escaped = true
                else -> out.add(byte)
            }
        }
        return out.toByteArray()
    }

    /** Aplica o byte stuffing: `0xAA`/`0xA5` recebem o prefixo `0xA5`. */
    fun escape(bytes: ByteArray): ByteArray {
        val out = ArrayList<Byte>(bytes.size)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            if (value == START_BYTE || value == ESCAPE_BYTE) out.add(ESCAPE_BYTE.toByte())
            out.add(byte)
        }
        return out.toByteArray()
    }

    private fun checksum(type: InmotionFrameType, payload: ByteArray): Byte {
        var xor = START_BYTE xor START_BYTE xor type.code xor payload.size
        for (byte in payload) xor = xor xor (byte.toInt() and 0xFF)
        return xor.toByte()
    }

    private fun checksumBytes(frame: ByteArray, upToExclusive: Int): Int {
        var xor = 0
        for (index in 0 until upToExclusive) xor = xor xor (frame[index].toInt() and 0xFF)
        return xor
    }
}
