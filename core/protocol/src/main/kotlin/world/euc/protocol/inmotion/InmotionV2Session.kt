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
import world.euc.protocol.model.WheelSettings
import world.euc.protocol.model.WheelStatistics
import world.euc.protocol.model.WheelTelemetry
import java.util.concurrent.atomic.AtomicReference

/**
 * Sessão de comunicação com uma roda Inmotion V2 (família V11+).
 *
 * Responsabilidades:
 * 1. Autenticar a sessão (handshake `0x13`) — necessário para lock em V12+.
 * 2. Rodar a inicialização ao conectar (modelo → serial → firmware → settings).
 * 3. Consultar telemetria periodicamente (comando `0x04`).
 * 4. Enviar comandos de controle em fila, um por vez, com timeout e retry.
 * 5. Remontar notificações fragmentadas e publicar eventos de domínio.
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
    private val settingsParser = InmotionV2SettingsParser()
    private val statisticsParser = InmotionV2StatisticsParser()
    private val queue = Channel<QueuedCommand>(config.queueCapacity)
    private val responseWaiter = AtomicReference<ResponseWaiter?>(null)
    private val authKeyChannel = AtomicReference<Channel<ByteArray>?>(null)
    private val authAckChannel = AtomicReference<Channel<Boolean>?>(null)

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 64)

    /** Eventos de domínio: telemetria, identificação, autenticação e falhas. */
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    /**
     * Inicia a sessão: dispara os loops de recepção, de comandos e de polling,
     * e enfileira autenticação + inicialização. O job devolvido representa a
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
        queue.send(QueuedCommand.Control(command))
    }

    /** Enfileira uma consulta que aguarda resposta, com retry automático. */
    suspend fun query(command: InmotionCommand) {
        queue.send(QueuedCommand.Query(command))
    }

    /**
     * Enfileira uma consulta **sem esperar resposta dedicada** (a resposta é
     * tratada ao chegar). Usada para releituras de cortesia, que não devem
     * prender a fila se o firmware não responder.
     */
    suspend fun poll(command: InmotionCommand) {
        queue.send(QueuedCommand.Poll(command))
    }

    private suspend fun enqueueInitialization() {
        queue.send(QueuedCommand.Authenticate)
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
        config.logger?.invoke("RX ${frame.toHex()}")
        captureAuthKey(frame)
        captureAuthAck(frame)
        signalWaiter(frame)
        publishEvents(frame)
    }

    private fun captureAuthKey(frame: InmotionFrame) {
        val channel = authKeyChannel.get() ?: return
        InmotionV2Responses.parseAuthKey(frame)?.let { key -> channel.trySend(key) }
    }

    private fun captureAuthAck(frame: InmotionFrame) {
        val channel = authAckChannel.get() ?: return
        if (InmotionV2Responses.isAuthSuccess(frame)) channel.trySend(true)
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
        statisticsParser.parse(frame)?.let { statistics ->
            config.logger?.invoke(
                "odômetro: viagem=${statistics.tripKm} km total=${statistics.totalKm} km " +
                    "horas=${statistics.totalHours}",
            )
            _events.tryEmit(SessionEvent.StatisticsReceived(statistics))
        }
        settingsParser.parse(frame)?.let { settings ->
            config.logger?.invoke(
                "settings parseados: limite=${settings.speedLimitKmh} modo=${settings.ridingMode} " +
                    "tilt=${settings.pedalTiltDegrees} " +
                    "sensibilidade=${settings.pedalSensitivityPercent} volume=${settings.speakerVolumePercent} " +
                    "split=${settings.splitRidingAccelerationPercent}/${settings.splitRidingBrakingPercent} " +
                    "splitAtivo=${settings.splitRidingModesEnabled} tiltEstendido=${settings.extendedLateralTiltLimit}",
            )
            _events.tryEmit(SessionEvent.SettingsReceived(settings))
        }
    }

    private suspend fun commandLoop() {
        for (queued in queue) {
            when (queued) {
                is QueuedCommand.Control -> {
                    writeFrame(queued.command)
                    delay(config.controlSettleMillis)
                }
                is QueuedCommand.Query -> executeWithRetry(queued.command)
                is QueuedCommand.Poll -> {
                    writeFrame(queued.command)
                    delay(config.controlSettleMillis)
                }
                QueuedCommand.Authenticate -> performAuthentication()
            }
        }
    }

    /**
     * Executa o handshake de autenticação: pede a chave, devolve o mesmo blob
     * e aguarda a confirmação. A roda aceita comandos sensíveis (lock) apenas
     * depois disso.
     */
    private suspend fun performAuthentication() {
        val keyChannel = Channel<ByteArray>(1)
        authKeyChannel.set(keyChannel)
        writeFrame(InmotionV2Commands.requestAuthKey())

        val key = withTimeoutOrNull(config.authTimeoutMillis) { keyChannel.receive() }
        authKeyChannel.set(null)
        if (key == null) {
            config.logger?.invoke("auth: sem chave da roda (timeout)")
            _events.tryEmit(SessionEvent.AuthenticationCompleted(success = false))
            return
        }

        val ackChannel = Channel<Boolean>(1)
        authAckChannel.set(ackChannel)
        writeFrame(InmotionV2Commands.verifyAuth(key))

        val confirmed = withTimeoutOrNull(config.authTimeoutMillis) { ackChannel.receive() } == true
        authAckChannel.set(null)
        config.logger?.invoke("auth: ${if (confirmed) "confirmada" else "sem confirmação da roda"}")
        _events.tryEmit(SessionEvent.AuthenticationCompleted(success = confirmed))
    }

    private suspend fun executeWithRetry(command: InmotionCommand) {
        repeat(config.maxAttempts) { attempt ->
            val signal = Channel<Unit>(1)
            responseWaiter.set(ResponseWaiter(command, signal))
            writeFrame(command)

            val acknowledged = withTimeoutOrNull(config.queryTimeoutMillis) { signal.receive() } != null
            responseWaiter.set(null)
            if (acknowledged) return

            if (attempt == config.maxAttempts - 1) {
                _events.tryEmit(SessionEvent.CommandTimedOut(command))
            }
        }
    }

    private suspend fun writeFrame(command: InmotionCommand) {
        config.logger?.invoke("TX ${command.toHex()}")
        link.write(command.toFrame())
    }

    private suspend fun pollLoop() {
        var tick = 0L
        while (currentCoroutineContext().isActive) {
            delay(config.pollIntervalMillis)
            queue.trySend(QueuedCommand.Query(InmotionV2Commands.readValues()))

            // Odômetros mudam devagar: viagem a cada poucos segundos, totais
            // bem mais raramente (o firmware pode nem responder aos quadros).
            if (tick % config.tripPollEveryTicks == 0L) {
                queue.trySend(QueuedCommand.Poll(InmotionV2Commands.readTrip()))
            }
            if (tick % config.totalPollEveryTicks == 0L) {
                queue.trySend(QueuedCommand.Poll(InmotionV2Commands.readTotalStatistics()))
            }
            tick += 1
        }
    }

    private sealed interface QueuedCommand {
        data class Control(val command: InmotionCommand) : QueuedCommand
        data class Query(val command: InmotionCommand) : QueuedCommand

        /**
         * Consulta periódica que **não espera** resposta dedicada: a resposta
         * é tratada ao chegar (evita timeouts empilhando na fila quando o
         * firmware não responde a algum quadro, como o de odômetro).
         */
        data class Poll(val command: InmotionCommand) : QueuedCommand

        data object Authenticate : QueuedCommand
    }

    private class ResponseWaiter(
        val command: InmotionCommand,
        val signal: Channel<Unit>,
    )

    private fun InmotionFrame.toHex(): String =
        payload.joinToString(" ") { "%02X".format(it) }.let { "tipo=${"%02X".format(typeCode)} $it" }

    private fun InmotionCommand.toHex(): String =
        payload.joinToString(" ") { "%02X".format(it) }.let { "tipo=${"%02X".format(frameType.code)} $it" }
}

/** Ajustes de tempo, capacidade e diagnóstico da sessão. */
data class SessionConfig(
    val queryTimeoutMillis: Long = 1_000,
    val controlSettleMillis: Long = 100,
    val authTimeoutMillis: Long = 2_000,
    val maxAttempts: Int = 3,
    val pollIntervalMillis: Long = 500,
    /** Consulta da viagem da roda: 1 a cada N ciclos de polling (4 = 2 s). */
    val tripPollEveryTicks: Long = 4,
    /** Consulta dos totais da roda: 1 a cada N ciclos de polling (20 = 10 s). */
    val totalPollEveryTicks: Long = 20,
    val queueCapacity: Int = 32,
    /** Recebe linhas de diagnóstico (hex de TX/RX e handshake). */
    val logger: ((String) -> Unit)? = null,
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

    /** Configurações da roda recebidas (apenas os campos reportados). */
    data class SettingsReceived(val settings: WheelSettings) : SessionEvent

    /** Odômetros (viagem/totais) recebidos — apenas os campos reportados. */
    data class StatisticsReceived(val statistics: WheelStatistics) : SessionEvent

    /** Handshake de autenticação concluído (ou não). */
    data class AuthenticationCompleted(val success: Boolean) : SessionEvent

    /** Consulta esgotou as tentativas sem resposta. */
    data class CommandTimedOut(val command: InmotionCommand) : SessionEvent
}
