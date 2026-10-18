package world.euc.app.wheel

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import world.euc.ble.AndroidWheelLink
import world.euc.ble.BleWheelConnector
import world.euc.ble.DiscoveredWheel
import world.euc.protocol.discovery.WheelProtocol
import world.euc.protocol.inmotion.InmotionCommand
import world.euc.protocol.inmotion.InmotionV2Commands
import world.euc.protocol.inmotion.InmotionV2Session
import world.euc.protocol.inmotion.SessionEvent

/**
 * Orquestra a conexão com a roda: escaneamento, sessão de protocolo e estado
 * para a UI. Vive no processo (criado pela [world.euc.app.EucApplication]) para
 * sobreviver à navegação entre telas.
 */
class WheelConnectionManager(context: Context) {

    private val connector = BleWheelConnector(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(WheelUiState())
    val state: StateFlow<WheelUiState> = _state.asStateFlow()

    private var link: AndroidWheelLink? = null
    private var session: InmotionV2Session? = null
    private var sessionJob: Job? = null
    private var eventsJob: Job? = null

    /** Escaneia rodas próximas, emitindo cada descoberta. */
    fun scan(timeoutMillis: Long = DEFAULT_SCAN_MILLIS): Flow<DiscoveredWheel> =
        connector.scan(timeoutMillis)

    /** Conecta à roda escolhida e inicia a sessão do protocolo. */
    suspend fun connect(wheel: DiscoveredWheel, protocol: WheelProtocol) {
        disconnect()
        _state.update { it.copy(connection = ConnectionState.Connecting) }

        runCatching {
            require(protocol == WheelProtocol.INMOTION_V2) {
                "Protocolo ainda não suportado: ${protocol.displayName}"
            }
            val established = connector.connect(wheel.device, protocol)
            link = established

            val newSession = InmotionV2Session(established)
            session = newSession
            eventsJob = scope.launch { newSession.events.collect(::onSessionEvent) }
            sessionJob = newSession.start(scope)
        }.onSuccess {
            _state.update { it.copy(connection = ConnectionState.Connected(wheel.name)) }
        }.onFailure { error ->
            disconnect()
            _state.update {
                it.copy(connection = ConnectionState.Failed(error.message ?: "falha ao conectar"))
            }
        }
    }

    /** Encerra a sessão e a conexão GATT. */
    fun disconnect() {
        eventsJob?.cancel()
        sessionJob?.cancel()
        link?.close()
        eventsJob = null
        sessionJob = null
        session = null
        link = null
        _state.update {
            WheelUiState(connection = ConnectionState.Disconnected)
        }
    }

    /** Aciona a buzina da roda. */
    suspend fun horn() {
        send(InmotionV2Commands.horn())
    }

    /** Liga/desliga o farol (estado otimista até a roda confirmar). */
    suspend fun setLight(on: Boolean) {
        send(InmotionV2Commands.headlight(on))
        _state.update { it.copy(lightOn = on) }
    }

    private suspend fun send(command: InmotionCommand) {
        session?.send(command)
    }

    private fun onSessionEvent(event: SessionEvent) {
        when (event) {
            is SessionEvent.TelemetryReceived -> _state.update {
                it.copy(telemetry = event.telemetry, lastTelemetryAtMillis = System.currentTimeMillis())
            }
            is SessionEvent.ModelIdentified -> _state.update { it.copy(model = event.model) }
            is SessionEvent.SerialNumberReceived -> _state.update { it.copy(serialNumber = event.serialNumber) }
            is SessionEvent.FirmwareReceived -> _state.update { it.copy(firmware = event.versions) }
            SessionEvent.SettingsReceived -> Unit // estado da roda refletido na telemetria
            is SessionEvent.CommandTimedOut -> Unit // observabilidade futura (log de depuração)
        }
    }

    private companion object {
        const val DEFAULT_SCAN_MILLIS = 15_000L
    }
}
