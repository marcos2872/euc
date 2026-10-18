package world.euc.protocol.discovery

/**
 * Protocolos de roda suportados (ou planejados).
 */
enum class WheelProtocol(val displayName: String) {
    INMOTION_V2("Inmotion (V11 ou posterior)"),
    INMOTION_V1("Inmotion (anterior à V11)"),
    KINGSONG("KingSong"),
    GOTWAY("Gotway/Begode"),
    VETERAN("Veteran"),
    NINEBOT_NEW("Ninebot (One Z)"),
    NINEBOT("Ninebot (One S2/A1)"),
    UNKNOWN("desconhecido"),
}

/** Grau de confiança da detecção — a UI deve pedir confirmação em MEDIUM/LOW. */
enum class Confidence { HIGH, MEDIUM, LOW }

/** Candidato de protocolo para um dispositivo descoberto. */
data class ProtocolCandidate(
    val protocol: WheelProtocol,
    val confidence: Confidence,
    val reason: String,
)

/** Dados de um dispositivo anunciado, relevantes para identificar o protocolo. */
data class BleDeviceProfile(
    val name: String?,
    val serviceUuids: Set<String>,
)

/**
 * Identifica o protocolo provável a partir dos serviços GATT e do nome do
 * dispositivo, replicando a tabela "BT definitions" do EUC World 2.66.0.
 *
 * Como vários protocolos compartilham serviços (`FFE0`, Nordic UART), a
 * detecção devolve uma lista ordenada de candidatos por confiança; a escolha
 * final deve ser confirmada pelo usuário e memorizada por endereço.
 */
object WheelProtocolDetector {

    // Serviços
    const val NORDIC_UART: String = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    const val FFE0: String = "0000ffe0-0000-1000-8000-00805f9b34fb"
    const val FFE5: String = "0000ffe5-0000-1000-8000-00805f9b34fb"
    const val FFF0: String = "0000fff0-0000-1000-8000-00805f9b34fb"
    const val DEVICE_INFORMATION: String = "0000180a-0000-1000-8000-00805f9b34fb"

    private val INMOTION_NAME = Regex("^(v\\d+[a-z]*|e20|p6|adventure|inmotion)", RegexOption.IGNORE_CASE)
    private val NINEBOT_NAME = Regex("(ninebot|\\bone\\b|^s2|^a1|^z\\d?)", RegexOption.IGNORE_CASE)
    private val KINGSONG_NAME = Regex("^(ks|kingsong|rockwheel|\\brw\\b)", RegexOption.IGNORE_CASE)
    private val GOTWAY_NAME = Regex("(gotway|begode|extreme ?bull|msuper|nikola|mten|^ex\\b|^rs\\b|^msp\\b)", RegexOption.IGNORE_CASE)
    private val VETERAN_NAME = Regex("(veteran|sherman|patton|lynx|abrams|leaperkim)", RegexOption.IGNORE_CASE)

    /** Devolve os candidatos em ordem decrescente de confiança. */
    fun detect(profile: BleDeviceProfile): List<ProtocolCandidate> {
        val services = profile.serviceUuids.map { it.lowercase() }.toSet()
        val name = profile.name.orEmpty()

        return buildList {
            if (NORDIC_UART in services) addNordicUartCandidates(name)
            if (FFE5 in services) {
                add(
                    ProtocolCandidate(
                        WheelProtocol.INMOTION_V1,
                        Confidence.HIGH,
                        "serviço FFE5 presente (transporte Inmotion pré-V11)",
                    ),
                )
            }
            if (FFE0 in services) addFfe0Candidates(name, services)
        }.distinctBy { it.protocol }
            .sortedBy { it.confidence.ordinal }
            .ifEmpty {
                listOf(ProtocolCandidate(WheelProtocol.UNKNOWN, Confidence.LOW, "nenhum serviço conhecido"))
            }
    }

    private fun MutableList<ProtocolCandidate>.addNordicUartCandidates(name: String) {
        when {
            INMOTION_NAME.containsMatchIn(name) -> add(
                ProtocolCandidate(WheelProtocol.INMOTION_V2, Confidence.HIGH, "Nordic UART + nome \"$name\""),
            )
            NINEBOT_NAME.containsMatchIn(name) -> add(
                ProtocolCandidate(WheelProtocol.NINEBOT_NEW, Confidence.HIGH, "Nordic UART + nome \"$name\""),
            )
            else -> {
                add(
                    ProtocolCandidate(
                        WheelProtocol.INMOTION_V2,
                        Confidence.MEDIUM,
                        "Nordic UART sem nome reconhecido",
                    ),
                )
                add(
                    ProtocolCandidate(
                        WheelProtocol.NINEBOT_NEW,
                        Confidence.LOW,
                        "Nordic UART sem nome reconhecido",
                    ),
                )
            }
        }
    }

    private fun MutableList<ProtocolCandidate>.addFfe0Candidates(name: String, services: Set<String>) {
        when {
            KINGSONG_NAME.containsMatchIn(name) -> add(
                ProtocolCandidate(WheelProtocol.KINGSONG, Confidence.HIGH, "nome \"$name\""),
            )
            GOTWAY_NAME.containsMatchIn(name) -> add(
                ProtocolCandidate(WheelProtocol.GOTWAY, Confidence.HIGH, "nome \"$name\""),
            )
            VETERAN_NAME.containsMatchIn(name) -> add(
                ProtocolCandidate(WheelProtocol.VETERAN, Confidence.HIGH, "nome \"$name\""),
            )
            FFF0 in services -> add(
                ProtocolCandidate(WheelProtocol.KINGSONG, Confidence.MEDIUM, "FFE0 + FFF0 (perfil KingSong)"),
            )
            DEVICE_INFORMATION in services -> add(
                ProtocolCandidate(WheelProtocol.GOTWAY, Confidence.MEDIUM, "FFE0 + serviço de informações"),
            )
            else -> {
                add(ProtocolCandidate(WheelProtocol.GOTWAY, Confidence.LOW, "apenas FFE0"))
                add(ProtocolCandidate(WheelProtocol.NINEBOT, Confidence.LOW, "apenas FFE0"))
                add(ProtocolCandidate(WheelProtocol.KINGSONG, Confidence.LOW, "apenas FFE0"))
            }
        }
    }
}
