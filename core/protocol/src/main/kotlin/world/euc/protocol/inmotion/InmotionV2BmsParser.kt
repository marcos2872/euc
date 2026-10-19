package world.euc.protocol.inmotion

import world.euc.protocol.model.BmsPack

/**
 * Interpreta os quadros de BMS do protocolo Inmotion V2.
 *
 * Consulta (tipo `0x16`): `02 <rota> <sub>`, com `rota` = `0x24`…`0x27`
 * (um pack por rota: 1 = `02 24`) e `sub` = `01` info geral / `02` células.
 * A roda responde `<rota> 02 8<sub> …` no mesmo tipo.
 *
 * **Offsets nos dados** (payload sem os 3 bytes `<rota> 02 8<sub>`),
 * little-endian:
 *
 * | Quadro | Offset | Campo | Escala |
 * |---|---|---|---|
 * | info | 6 | Tensão do pack | /100 V |
 * | info | 10 | Id do board (2 e 3 na V11y) | — |
 * | info | 12 | Carga do pack (SOC) | /100 % |
 * | info | 18 | Tensão da célula mais alta | mV |
 * | info | 20 | Tensão da célula mais baixa | mV |
 * | info | 24-35 | Temperaturas (12 sensores) | byte − 176 °C |
 * | células | 0-39 | 20 tensões de célula | u16 LE mV |
 *
 * Validado numa V11y real (fw 2.5.54): a soma das 20 células bate com a tensão
 * do pack (81,30 V) e o máx/mín do quadro de info batem com o de células.
 * As rotas `02 26`/`02 27` (packs 3 e 4) não respondem na V11y — são dos
 * modelos de 4 packs (V12/V13/V14).
 */
class InmotionV2BmsParser {

    /** Extrai um pack de BMS do quadro; `null` se o quadro não for de BMS. */
    fun parse(frame: InmotionFrame): BmsPack? {
        if (frame.typeCode != FRAME_TYPE) return null
        val payload = frame.payload
        if (payload.size < 4) return null

        val route = payload[0].toInt() and 0xFF
        val index = route - ROUTE_BASE + 1
        if (route !in ROUTE_BASE..ROUTE_LAST) return null
        if (payload[1].toInt() and 0xFF != ROUTE_ECHO) return null

        return when (payload[2].toInt() and 0xFF) {
            SUB_INFO -> parseInfo(index, payload)
            SUB_CELLS -> parseCells(index, payload)
            else -> null
        }
    }

    private fun parseInfo(index: Int, payload: ByteArray): BmsPack? {
        // Dados após `<rota> 02 81`.
        val data = payload.copyOfRange(3, payload.size)
        // Sem bytes suficientes para os campos essenciais não é um quadro de info.
        if (data.size <= OFFSET_INFO_MIN_CELL + 1) return null
        val temperatures = data
            .takeIf { it.size >= OFFSET_INFO_TEMPERATURES + TEMPERATURE_COUNT }
            ?.let { bytes ->
                (OFFSET_INFO_TEMPERATURES until OFFSET_INFO_TEMPERATURES + TEMPERATURE_COUNT)
                    .map { (bytes[it].toInt() and 0xFF) - TEMPERATURE_OFFSET_CELSIUS }
                    .map { it.toDouble() }
            }
            .orEmpty()

        return BmsPack(
            index = index,
            boardId = u16Le(data, OFFSET_INFO_BOARD),
            voltageVolts = u16Le(data, OFFSET_INFO_VOLTAGE)?.div(100.0),
            socPercent = u16Le(data, OFFSET_INFO_SOC)?.div(100.0),
            maxCellMv = u16Le(data, OFFSET_INFO_MAX_CELL),
            minCellMv = u16Le(data, OFFSET_INFO_MIN_CELL),
            temperaturesCelsius = temperatures,
        )
    }

    private fun parseCells(index: Int, payload: ByteArray): BmsPack? {
        val cells = (0 until CELL_COUNT).mapNotNull { u16Le(payload, 3 + it * 2) }
        // Quadro truncado (ou de outro tamanho de pack) não vale como leitura.
        if (cells.size < MIN_CELLS) return null
        return BmsPack(index = index, cellsMv = cells)
    }

    private fun u16Le(data: ByteArray, offset: Int): Int? {
        if (offset + 1 >= data.size) return null
        return (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    private companion object {
        /** Tipo de quadro das rotas de BMS (estendido). */
        const val FRAME_TYPE = 0x16

        /** Primeira rota (`0x24` = pack 1) e última (`0x27` = pack 4). */
        const val ROUTE_BASE = 0x24
        const val ROUTE_LAST = 0x27

        /** Segundo byte das respostas (`<rota> 02 …`). */
        const val ROUTE_ECHO = 0x02

        /** Subcomandos: info geral e tensões de célula. */
        const val SUB_INFO = 0x81
        const val SUB_CELLS = 0x82

        const val OFFSET_INFO_VOLTAGE = 6
        const val OFFSET_INFO_BOARD = 10
        const val OFFSET_INFO_SOC = 12
        const val OFFSET_INFO_MAX_CELL = 18
        const val OFFSET_INFO_MIN_CELL = 20
        const val OFFSET_INFO_TEMPERATURES = 24
        const val TEMPERATURE_COUNT = 12
        const val TEMPERATURE_OFFSET_CELSIUS = 176

        /** Células por pack (20 na V11y, 20S). */
        const val CELL_COUNT = 20

        /** Mínimo de células para o quadro valer como leitura (protege de truncado). */
        const val MIN_CELLS = 8
    }
}
