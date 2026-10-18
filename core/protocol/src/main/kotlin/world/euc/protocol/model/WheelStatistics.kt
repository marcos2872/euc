package world.euc.protocol.model

/**
 * Odômetros reportados pela roda (quando o firmware reporta).
 *
 * Todos os campos são opcionais: nem todo modelo responde aos quadros de
 * viagem (`10 00 01`) e de totais (`11`).
 */
data class WheelStatistics(
    /** Distância da viagem atual da roda (zera no reset do odômetro), km. */
    val tripKm: Double? = null,
    /** Distância total do monociclo (odômetro permanente), km. */
    val totalKm: Double? = null,
    /** Tempo total de uso, horas. */
    val totalHours: Double? = null,
) {
    val isEmpty: Boolean get() = tripKm == null && totalKm == null && totalHours == null

    /** Junta o que já se sabia com uma leitura nova (campo nulo não apaga). */
    fun mergedWith(other: WheelStatistics): WheelStatistics = WheelStatistics(
        tripKm = other.tripKm ?: tripKm,
        totalKm = other.totalKm ?: totalKm,
        totalHours = other.totalHours ?: totalHours,
    )
}
