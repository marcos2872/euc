package world.euc.app.debug

import world.euc.protocol.inmotion.InmotionCommand
import world.euc.protocol.inmotion.InmotionFrameType

/**
 * Sonda de captura do protocolo Inmotion V2 — **somente build de debug**.
 *
 * Envia quadros **candidatos** tirados da documentação do app oficial
 * (`docs/protocolos/inmotion.md`) para descobrir os bytes que ainda faltam:
 * BMS por célula, comandos de identificação e a inversão do mute.
 *
 * A sessão já registra todo TX/RX em hex no logcat (tag `EucSession`), então o
 * uso é: conectar na roda, tocar o botão em Ajustes e ler o log. O plano de
 * captura está em `docs/09-pendencias-inmotion.md` §4.1.
 */
class ProtocolProbe(
    /** Rótulo do botão na UI de debug. */
    val label: String,
    /** O que a sequência tenta descobrir (mostrado na UI). */
    val description: String,
    /** Quadros enviados em ordem, com pausa entre eles. */
    val commands: List<InmotionCommand>,
)

object ProtocolProbes {

    /** Rotas de pack/célula do doc 03 §7 (`02 24`…`02 27`, tipo `0x16`). */
    val BMS = ProtocolProbe(
        label = "Bateria (células)",
        description = "Consulta as rotas de pack do app oficial (02 24…02 27, packs 1 e 2).",
        commands = buildList {
            for (route in 0x24..0x27) {
                for (pack in 1..2) add(extended(0x02, route, pack))
            }
        },
    )

    /**
     * Leituras vizinhas das conhecidas (`02 01` modelo, `02 02` serial,
     * `02 06` firmware): candidatas a SN do motor, HW e data de ativação.
     */
    val IDENTIFICATION = ProtocolProbe(
        label = "Identificação",
        description = "Tenta 02 03, 02 04, 02 05 e 02 07 (motor/bateria SN, HW, ativação).",
        commands = listOf(0x03, 0x04, 0x05, 0x07).map { initial(0x02, it) },
    )

    /**
     * Inversão do mute (`0x2C`): manda `1`, toca o bipe, manda `0` e toca de
     * novo — o bipe que sumir revela qual valor é o "mutado".
     */
    val MUTE = ProtocolProbe(
        label = "Som/mute + bipe",
        description = "Mute 1 → bipe → mute 0 → bipe: o bipe que sumir revela a inversão.",
        commands = listOf(control(0x2C, 0x01), horn(), control(0x2C, 0x00), horn()),
    )

    /** Todas as sondas, na ordem sugerida de uso. */
    val ALL = listOf(BMS, IDENTIFICATION, MUTE)

    private fun initial(vararg bytes: Int) = command(InmotionFrameType.INITIAL, bytes)
    private fun extended(vararg bytes: Int) = command(InmotionFrameType.EXTENDED, bytes)
    private fun control(vararg bytes: Int) = command(InmotionFrameType.DEFAULT, bytes)

    private fun command(type: InmotionFrameType, bytes: IntArray) =
        InmotionCommand(type, ByteArray(bytes.size) { bytes[it].toByte() })

    /** Bipe curto da V11y (`playBeep`) — só para dar feedback sonoro à sonda. */
    private fun horn() = command(InmotionFrameType.DEFAULT, intArrayOf(0x51, 0x02, 0x64))
}
