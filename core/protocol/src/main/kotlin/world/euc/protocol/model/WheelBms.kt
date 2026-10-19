package world.euc.protocol.model

/**
 * Estado do BMS (gerenciador de bateria) reportado pela roda.
 *
 * Nem toda roda responde às rotas de BMS — [packs] fica vazio quando o firmware
 * não colabora. Cada pack corresponde a uma rota de consulta (`02 24`…`02 27`);
 * ver `docs/03-protocolo-inmotion.md` §7.
 */
data class WheelBms(
    val packs: List<BmsPack> = emptyList(),
) {
    val isEmpty: Boolean get() = packs.isEmpty()

    /** Tensão da célula mais baixa entre todos os packs, mV. */
    val lowestCellMv: Int? get() = packs.mapNotNull { it.minCellMv }.minOrNull()

    /** Maior desequilíbrio entre as células de um pack, mV. */
    val worstCellDeltaMv: Int? get() = packs.mapNotNull { it.cellDeltaMv }.maxOrNull()

    /** Carga média reportada pelos packs, %. */
    val averageSocPercent: Double?
        get() = packs.mapNotNull { it.socPercent }.takeIf { it.isNotEmpty() }?.average()

    /** Junta o que já se sabia com uma leitura nova (pack a pack, por índice). */
    fun mergedWith(other: WheelBms): WheelBms {
        if (other.packs.isEmpty()) return this
        val byIndex = packs.associateBy { it.index }.toMutableMap()
        other.packs.forEach { pack ->
            byIndex[pack.index] = byIndex[pack.index]?.mergedWith(pack) ?: pack
        }
        return WheelBms(byIndex.values.sortedBy { it.index })
    }
}

/**
 * Um pack de bateria (uma rota de BMS).
 *
 * Os campos chegam em **dois quadros** diferentes: o de *info* traz tensão,
 * carga, máx/mín de célula e temperaturas; o de *células* traz as tensões
 * individuais. Por isso tudo é opcional e [mergedWith] combina os dois.
 */
data class BmsPack(
    /** Índice da rota usada na consulta: 1 = `02 24` … 4 = `02 27`. */
    val index: Int,
    /** Id do board reportado pelo BMS (2 e 3 na V11y), quando reportado. */
    val boardId: Int? = null,
    /** Tensão do pack, V. */
    val voltageVolts: Double? = null,
    /** Carga do pack (SOC), %. */
    val socPercent: Double? = null,
    /** Tensão da célula mais alta, mV. */
    val maxCellMv: Int? = null,
    /** Tensão da célula mais baixa, mV. */
    val minCellMv: Int? = null,
    /** Tensões de célula, mV (20 na V11y). */
    val cellsMv: List<Int> = emptyList(),
    /** Temperaturas do BMS, °C (12 sensores na V11y). */
    val temperaturesCelsius: List<Double> = emptyList(),
) {
    /** Diferença entre a célula mais alta e a mais baixa, mV. */
    val cellDeltaMv: Int?
        get() = when {
            cellsMv.isNotEmpty() -> cellsMv.max() - cellsMv.min()
            maxCellMv != null && minCellMv != null -> maxCellMv - minCellMv
            else -> null
        }

    /** Junta o que já se sabia com uma leitura nova (lista vazia não apaga). */
    fun mergedWith(other: BmsPack): BmsPack = BmsPack(
        index = index,
        boardId = other.boardId ?: boardId,
        voltageVolts = other.voltageVolts ?: voltageVolts,
        socPercent = other.socPercent ?: socPercent,
        maxCellMv = other.maxCellMv ?: maxCellMv,
        minCellMv = other.minCellMv ?: minCellMv,
        cellsMv = other.cellsMv.ifEmpty { cellsMv },
        temperaturesCelsius = other.temperaturesCelsius.ifEmpty { temperaturesCelsius },
    )
}
