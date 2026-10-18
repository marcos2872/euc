package world.euc.protocol.inmotion

/**
 * Remonta quadros Inmotion V2 a partir de notificações BLE fragmentadas.
 *
 * Notificações chegam em pedaços arbitrários (20 bytes por padrão), que podem
 * cortar um quadro ao meio ou até separar o par de escape `A5 XX`. Este leitor
 * acumula os bytes, remove o stuffing e emite apenas quadros completos e com
 * checksum válido; bytes inválidos são descartados com ressincronização pelo
 * próximo header `AA AA`.
 */
class InmotionV2FrameReader {

    private val buffer = ArrayList<Byte>()
    private var escapePending = false

    /** Consome um pedaço recebido e devolve os quadros completos que surgiram. */
    fun offer(chunk: ByteArray): List<InmotionFrame> {
        appendUnescaped(chunk)
        return extractFrames()
    }

    /** Limpa o estado (usar ao reconectar). */
    fun reset() {
        buffer.clear()
        escapePending = false
    }

    private fun appendUnescaped(chunk: ByteArray) {
        for (byte in chunk) {
            val value = byte.toInt() and 0xFF
            when {
                escapePending -> {
                    buffer.add(byte)
                    escapePending = false
                }
                value == InmotionV2Framing.ESCAPE_BYTE -> escapePending = true
                else -> buffer.add(byte)
            }
        }
    }

    private fun extractFrames(): List<InmotionFrame> {
        val frames = ArrayList<InmotionFrame>()
        while (true) {
            discardLeadingGarbage()
            if (buffer.size < HEADER_SIZE + 1) break

            val payloadSize = buffer[3].toInt() and 0xFF
            val frameSize = HEADER_SIZE + payloadSize + 1
            if (buffer.size < frameSize) break

            val candidate = ByteArray(frameSize) { buffer[it] }
            val frame = InmotionV2Framing.decode(candidate)
            if (frame == null) {
                // Não é um quadro válido: descarta um byte e tenta ressincronizar.
                buffer.removeAt(0)
            } else {
                frames.add(frame)
                repeat(frameSize) { buffer.removeAt(0) }
            }
        }
        return frames
    }

    /** Descarta bytes que não podem iniciar um quadro (`AA AA`). */
    private fun discardLeadingGarbage() {
        while (buffer.size >= 2 && !isHeaderAt(0)) {
            buffer.removeAt(0)
        }
    }

    private fun isHeaderAt(index: Int): Boolean =
        buffer[index].toInt() and 0xFF == InmotionV2Framing.START_BYTE &&
            buffer[index + 1].toInt() and 0xFF == InmotionV2Framing.START_BYTE

    private companion object {
        const val HEADER_SIZE = 4
    }
}
