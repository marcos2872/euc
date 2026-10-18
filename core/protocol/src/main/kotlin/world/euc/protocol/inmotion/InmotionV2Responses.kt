package world.euc.protocol.inmotion

/**
 * Interpreta os quadros de resposta do protocolo Inmotion V2.
 *
 * Convenção de resposta: o primeiro byte é `0x80 | comando`. Para as consultas
 * de identificação (comando `0x02`), o segundo byte repete o subcomando
 * (ex.: carType responde `82 01`, serial responde `82 02`).
 */
object InmotionV2Responses {

    private const val RESPONSE_FLAG = 0x80

    /** Código de resposta esperado para um comando enviado. */
    fun expectedResponseCode(command: InmotionCommand): Int =
        (command.payload.first().toInt() and 0xFF) or RESPONSE_FLAG

    /** Verifica se o quadro é a resposta do comando informado. */
    fun isResponseTo(frame: InmotionFrame, command: InmotionCommand): Boolean {
        val payload = frame.payload
        if (payload.isEmpty()) return false
        val commandCode = command.payload.first().toInt() and 0xFF
        if (payload[0].toInt() and 0xFF != commandCode or RESPONSE_FLAG) return false
        // Consultas de identificação ecoam o subcomando no segundo byte.
        if (commandCode == 0x02 && command.payload.size > 1) {
            val subCommand = command.payload[1].toInt() and 0xFF
            return payload.size > 1 && payload[1].toInt() and 0xFF == subCommand
        }
        return true
    }

    /**
     * Extrai a identificação do modelo da resposta de carType (`82 01 …`).
     *
     * O byte de modelo fica na posição 3 do payload e o de variante na 4
     * (layout do EUC World, caso INIT_ID).
     */
    fun parseModel(frame: InmotionFrame): InmotionModelInfo? {
        val payload = frame.payload
        if (payload.size < 5) return null
        if (payload[0].toInt() and 0xFF != 0x82 || payload[1].toInt() and 0xFF != 0x01) return null

        val modelByte = payload[3].toInt() and 0xFF
        val variant = payload[4].toInt() and 0xFF
        return InmotionModelInfo.from(modelByte, variant)
    }

    /**
     * Extrai o número de série da resposta `82 02 …`: 21 bytes ASCII.
     */
    fun parseSerialNumber(frame: InmotionFrame): String? {
        val payload = frame.payload
        if (payload.size < 23) return null
        if (payload[0].toInt() and 0xFF != 0x82 || payload[1].toInt() and 0xFF != 0x02) return null

        val bytes = payload.copyOfRange(2, 23)
        return String(bytes, Charsets.US_ASCII).trimEnd('\u0000', ' ')
    }

    /**
     * Extrai as versões de firmware da resposta `82 06 …`.
     *
     * Cada versão é montada como `maior.menor.patch`, com os bytes em
     * [offset+3].[offset+2] e o patch como u16 little-endian em [offset].
     */
    fun parseFirmwareVersions(frame: InmotionFrame): FirmwareVersions? {
        val payload = frame.payload
        if (payload.size < 24) return null
        if (payload[0].toInt() and 0xFF != 0x82 || payload[1].toInt() and 0xFF != 0x06) return null

        return FirmwareVersions(
            main = versionAt(payload, 12),
            secondary = versionAt(payload, 21),
            tertiary = versionAt(payload, 3),
        )
    }

    private fun versionAt(payload: ByteArray, offset: Int): String? {
        if (offset + 3 >= payload.size) return null
        val major = payload[offset + 3].toInt() and 0xFF
        val minor = payload[offset + 2].toInt() and 0xFF
        val patch = (payload[offset].toInt() and 0xFF) or ((payload[offset + 1].toInt() and 0xFF) shl 8)
        return "$major.$minor.$patch"
    }
}

/** Identificação de modelo com configuração de bateria. */
data class InmotionModelInfo(
    val model: InmotionModel,
    /** Variante reportada pela roda (ex.: 2 distingue a V11Y da V11). */
    val variant: Int,
    val batterySeries: Int,
    val batteryParallel: Int,
) {
    /** Nome comercial exato (ex.: "V11Y"). */
    val displayName: String
        get() = if (model == InmotionModel.V11 && variant == 2) "V11Y" else model.displayName

    companion object {
        /**
         * Mapeia o byte de modelo reportado pela roda (layout do EUC World).
         * Modelo desconhecido assume a configuração da V12, como o app oficial.
         */
        fun from(modelByte: Int, variant: Int): InmotionModelInfo = when (modelByte) {
            6 -> InmotionModelInfo(InmotionModel.V11, variant, 20, 4)
            7 -> InmotionModelInfo(InmotionModel.V12, variant, 20, 4)
            8 -> InmotionModelInfo(InmotionModel.V13, variant, 30, 8)
            9 -> InmotionModelInfo(InmotionModel.V14, variant, 32, 4)
            10 -> InmotionModelInfo(InmotionModel.E20, variant, 20, 4)
            11 -> InmotionModelInfo(InmotionModel.V12S, variant, 20, 4)
            12 -> InmotionModelInfo(InmotionModel.V9, variant, 20, 4)
            13 -> InmotionModelInfo(InmotionModel.P6, variant, 56, 4)
            else -> InmotionModelInfo(InmotionModel.UNKNOWN, variant, 20, 4)
        }
    }
}

/** Modelos da família Inmotion V2. */
enum class InmotionModel(val displayName: String) {
    V11("V11"),
    V12("V12"),
    V13("V13"),
    V14("V14"),
    V12S("V12S"),
    V9("V9"),
    E20("E20"),
    P6("P6"),
    UNKNOWN("desconhecido"),
}

/** Versões de firmware reportadas pela roda. */
data class FirmwareVersions(
    val main: String?,
    val secondary: String? = null,
    val tertiary: String? = null,
) {
    /** Compara a versão principal com outra no formato `maior.menor.patch`. */
    fun mainAtLeast(version: String): Boolean {
        val current = main?.split('.')?.mapNotNull { it.toIntOrNull() } ?: return false
        val reference = version.split('.').mapNotNull { it.toIntOrNull() }
        if (current.size < 3 || reference.size < 3) return false
        for (index in 0..2) {
            if (current[index] != reference[index]) return current[index] > reference[index]
        }
        return true
    }
}
