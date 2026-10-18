package world.euc.protocol.inmotion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import world.euc.protocol.link.WheelLink
import world.euc.protocol.model.WheelTelemetry
import java.util.concurrent.atomic.AtomicReference

/**
 * Sessão de comunicação com uma roda Inmotion V2 (família V11+).
 *
 * Responsabilidades:
 * 1. Rodar a sequência de inicialização ao conectar (modelo → serial → firmware → settings).
 * 2. Consultar telemetria periodicamente (comando `0x04`).
 * 3. Enviar comandos de controle em fila, um por vez, com timeout e retry.
 * 4. Remontar notificações fragmentadas e publicar eventos de domínio.
 *
 * O transporte vem da porta [WheelLink]; esta classe não conhece Android nem
 * Bluetooth, o que a torna testável com tempo virtual.
 */
class InmotionV2Session(
    private val link: WheelLink,
    private val config: SessionConfig = SessionConfig(),
) {

    private val reader = InmotionV2FrameReader()
    private val telemetryParser = InmotionV2TelemetryParser()
    private val queue = Channel<QueuedCommand>(config.queueCapacity)
    private val responseWaiter = AtomicReference<ResponseWaiter?>(null)

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 64)

    /** Eventos de domínio: telemetria, identificação e falhas de comando. */
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    /**
     * Inicia a sessão: dispara os loops de recepção, de comandos e de polling,
     * e enfileira a sequência de inicialização. O job devolvido representa a
     * sessão inteira — cancelá-lo encerra tudo.
     */
    fun start(scope: CoroutineScope): Job = scope.launch {
        reader.reset()
        coroutineScope {
            launch { receiveLoop() }
            launch { commandLoop() }
            launch { pollLoop() }
            enqueueInitialization()
        }
    }

    /** Enfileira um comando de controle (sem resposta dedicada). */
    suspend fun send(command: InmotionCommand) {
        queue.send(QueuedCommand(command, expectsResponse = false))
    }

    /** Enfileira uma consulta que aguarda resposta, com retry automático. */
    suspend fun query(command: InmotionCommand) {
        queue.send(QueuedCommand(command, expectsResponse = true))
    }

    private suspend fun enqueueInitialization() {
        query(InmotionV2Commands.readModel())
        query(InmotionV2Commands.readSerialNumber())
        query(InmotionV2Commands.readFirmwareVersions())
        query(InmotionV2Commands.readSettings())
    }

    private suspend fun receiveLoop() {
        link.incoming.collect { chunk ->
            reader.offer(chunk).forEach(::handleFrame)
        }
    }

    private fun handleFrame(frame: InmotionFrame) {
        signalWaiter(frame)
        publishEvents(frame)
    }

    private fun signalWaiter(frame: InmotionFrame) {
        val waiter = responseWaiter.get() ?: return
        if (InmotionV2Responses.isResponseTo(frame, waiter.command)) {
            waiter.signal.trySend(Unit)
        }
    }

    private fun publishEvents(frame: InmotionFrame) {
        telemetryParser.parse(frame)?.let { telemetry ->
            _events.tryEmit(SessionEvent.TelemetryReceived(telemetry))
        }
        InmotionV2Responses.parseModel(frame)?.let { model ->
            _events.tryEmit(SessionEvent.ModelIdentified(model))
        }
        InmotionV2Responses.parseSerialNumber(frame)?.let { serial ->
            _events.tryEmit(SessionEvent.SerialNumberReceived(serial))
        }
        InmotionV2Responses.parseFirmwareVersions(frame)?.let { versions ->
            _events.tryEmit(SessionEvent.FirmwareReceived(versions))
        }
        if (isSettingsResponse(frame)) {
            _events.tryEmit(SessionEvent.SettingsReceived)
        }
    }

    private fun isSettingsResponse(frame: InmotionFrame): Boolean {
        val payload = frame.payload
        return payload.size >= 2 &&
            payload[0].toInt() and 0xFF == 0xA0 &&
            payload[1].toInt() and 0xFF == 0x20
    }

    private suspend fun commandLoop() {
        for (queued in queue) {
            if (queued.expectsResponse) {
                executeWithRetry(queued.command)
            } else {
                link.write(queued.command.toFrame())
                delay(config.controlSettleMillis)
            }
        }
    }

    private suspend fun executeWithRetry(command: InmotionCommand) {
        repeat(config.maxAttempts) { attempt ->
            val signal = Channel<Unit>(1)
            responseWaiter.set(ResponseWaiter(command, signal))
            link.write(command.toFrame())

            val acknowledged = withTimeoutOrNull(config.queryTimeoutMillis) { signal.receive() } != null
            responseWaiter.set(null)
            if (acknowledged) return

            if (attempt == config.maxAttempts - 1) {
                _events.tryEmit(SessionEvent.CommandTimedOut(command))
            }
        }
    }

    private suspend fun pollLoop() {
        while (currentCoroutineContext().isActive) {
            delay(config.pollIntervalMillis)
            queue.trySend(QueuedCommand(InmotionV2Commands.readValues(), expectsResponse = true))
        }
    }

    private class QueuedCommand(
        val command: InmotionCommand,
        val expectsResponse: Boolean,
    )

    private class ResponseWaiter(
        val command: InmotionCommand,
        val signal: Channel<Unit>,
    )
}

/** Ajustes de tempo e capacidade da sessão. */
data class SessionConfig(
    val queryTimeoutMillis: Long = 1_000,
    val controlSettleMillis: Long = 100,
    val maxAttempts: Int = 3,
    val pollIntervalMillis: Long = 500,
    val queueCapacity: Int = 32,
)

/** Eventos publicados pela sessão. */
sealed interface SessionEvent {

    /** Nova leitura de telemetria. */
    data class TelemetryReceived(val telemetry: WheelTelemetry) : SessionEvent

    /** Modelo identificado na inicialização. */
    data class ModelIdentified(val model: InmotionModelInfo) : SessionEvent

    /** Número de série recebido. */
    data class SerialNumberReceived(val serialNumber: String) : SessionEvent

    /** Versões de firmware recebidas. */
    data class FirmwareReceived(val versions: FirmwareVersions) : SessionEvent

    /** Configurações da roda recebidas. */
    data object SettingsReceived : SessionEvent

    /** Consulta esgotou as tentativas sem resposta. */
    data class CommandTimedOut(val command: InmotionCommand) : SessionEvent
}
