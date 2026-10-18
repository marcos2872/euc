package world.euc.protocol.link

import kotlinx.coroutines.flow.Flow

/**
 * Porta de transporte de bytes até a roda.
 *
 * É a fronteira entre o núcleo de protocolo (puro) e o mundo externo:
 * em produção é implementada pelo adaptador Bluetooth LE do Android; nos
 * testes, por um fake em memória.
 *
 * Contrato:
 * - [write] envia um quadro completo (já serializado) pela característica de escrita.
 * - [incoming] emite os bytes recebidos das notificações, em fragmentos
 *   arbitrários — a remontagem dos quadros é responsabilidade da camada de
 *   protocolo, não da porta.
 */
interface WheelLink {

    /** Envia bytes pela característica de escrita da roda. */
    suspend fun write(bytes: ByteArray)

    /** Fluxo de fragmentos recebidos das notificações. */
    val incoming: Flow<ByteArray>
}
