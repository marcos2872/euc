package world.euc.app.wheel

import world.euc.protocol.inmotion.FirmwareVersions
import world.euc.protocol.inmotion.InmotionModelInfo
import world.euc.protocol.model.WheelTelemetry

/** Estado observável da conexão e da telemetria para a UI. */
data class WheelUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val model: InmotionModelInfo? = null,
    val serialNumber: String? = null,
    val firmware: FirmwareVersions? = null,
    val telemetry: WheelTelemetry = WheelTelemetry(),
    /** Relógio da última leitura recebida (0 = nenhuma). */
    val lastTelemetryAtMillis: Long = 0L,
    /** Último estado de farol comandado pelo app (otimista). */
    val lightOn: Boolean? = null,
)

/** Estados do ciclo de conexão. */
sealed interface ConnectionState {

    data object Disconnected : ConnectionState

    data object Connecting : ConnectionState

    data class Connected(val deviceName: String?) : ConnectionState

    data class Failed(val message: String) : ConnectionState
}
