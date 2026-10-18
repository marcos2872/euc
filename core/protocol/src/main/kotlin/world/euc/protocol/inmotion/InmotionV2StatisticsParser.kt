package world.euc.protocol.inmotion

import world.euc.protocol.model.WheelStatistics

/**
 * Interpreta os quadros de odômetro do protocolo Inmotion V2:
 *
 * | Quadro | Pedido    | Resposta | Campo             | Offset | Escala |
 * |--------|-----------|----------|-------------------|--------|--------|
 * | Viagem | `10 00 01`| `0x90`   | distância         | 23     | /100 km |
 * | Totais | `11`      | `0x91`   | distância         | 1      | /100 km |
 * | Totais | `11`      | `0x91`   | tempo de uso      | 5      | /3600 h |
 *
 * Offsets extraídos do EUC World 2.66.0 (adaptador Inmotion V2, `p364uh/C5937z.java`:
 * `getInt(23) * 0.01f` na resposta `0x90` e `getInt(1) * 0.01f` / `getInt(5) / 3600f`
 * na resposta `0x91`). Os inteiros são **sem sinal** (o app original usa um
 * conversor de u32 antes de escalar).
 *
 * Validar contra a tela/app oficial: o firmware pode não responder a algum
 * dos dois quadros, e o parser simplesmente devolve `null` nesse caso.
 */
class InmotionV2StatisticsParser {

    /** Extrai a distância da viagem de um quadro `0x90`. */
    fun parseTrip(frame: InmotionFrame): WheelStatistics? {
        val payload = frame.payload
        if (payload.size < OFFSET_TRIP_DISTANCE + 4) return null
        if (u8(payload, 0) != RESPONSE_TRIP) return null
        return WheelStatistics(
            tripKm = u32Le(payload, OFFSET_TRIP_DISTANCE)?.div(100.0),
        )
    }

    /** Extrai distância e tempo totais de um quadro `0x91`. */
    fun parseTotal(frame: InmotionFrame): WheelStatistics? {
        val payload = frame.payload
        if (payload.size < OFFSET_TOTAL_HOURS + 4) return null
        if (u8(payload, 0) != RESPONSE_TOTAL) return null
        return WheelStatistics(
            totalKm = u32Le(payload, OFFSET_TOTAL_DISTANCE)?.div(100.0),
            totalHours = u32Le(payload, OFFSET_TOTAL_HOURS)?.div(3600.0),
        )
    }

    /** Aceita qualquer um dos dois quadros de odômetro. */
    fun parse(frame: InmotionFrame): WheelStatistics? =
        parseTrip(frame) ?: parseTotal(frame)

    private fun u8(data: ByteArray, offset: Int): Int? =
        if (offset in data.indices) data[offset].toInt() and 0xFF else null

    /** Inteiro de 32 bits sem sinal, little-endian. */
    private fun u32Le(data: ByteArray, offset: Int): Long? {
        if (offset < 0 || offset + 4 > data.size) return null
        var value = 0L
        for (index in 0 until 4) {
            value = value or ((data[offset + index].toLong() and 0xFF) shl (8 * index))
        }
        return value
    }

    companion object {
        const val RESPONSE_TRIP = 0x90
        const val RESPONSE_TOTAL = 0x91

        const val OFFSET_TRIP_DISTANCE = 23
        const val OFFSET_TOTAL_DISTANCE = 1
        const val OFFSET_TOTAL_HOURS = 5
    }
}
