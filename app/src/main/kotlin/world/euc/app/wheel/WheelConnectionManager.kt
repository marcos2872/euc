package world.euc.app.wheel

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import world.euc.ble.AndroidWheelLink
import world.euc.ble.BleWheelConnector
import world.euc.ble.DiscoveredWheel
import world.euc.app.alarm.AlarmConfigStore
import world.euc.app.alarm.AlarmNotifier
import world.euc.protocol.alarm.AlarmConfig
import world.euc.protocol.alarm.AlarmEngine
import world.euc.protocol.discovery.WheelProtocol
import world.euc.protocol.inmotion.InmotionCommand
import world.euc.protocol.inmotion.InmotionV2Commands
import world.euc.protocol.inmotion.InmotionModel
import world.euc.protocol.inmotion.InmotionV2Capabilities
import world.euc.protocol.inmotion.InmotionV2Session
import world.euc.protocol.inmotion.SessionConfig
import world.euc.protocol.inmotion.SessionEvent
import world.euc.protocol.model.WheelSettings
import world.euc.protocol.model.WheelTelemetry

/**
 * Orquestra a conexão com a roda: escaneamento, sessão de protocolo, comandos
 * e estado para a UI. Vive no processo (criado pela
 * [world.euc.app.EucApplication]) para sobreviver à navegação entre telas.
 */
class WheelConnectionManager(context: Context) {

    private val connector = BleWheelConnector(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val alarmConfigStore = AlarmConfigStore(context.applicationContext)
    private val localSettingsStore = LocalSettingsStore(context.applicationContext)
    private val alarmEngine = AlarmEngine()
    private val alarmNotifier = AlarmNotifier(context.applicationContext)
    private var alarmConfig = alarmConfigStore.load()

    private val _state = MutableStateFlow(
        WheelUiState(
            alarmConfig = alarmConfig,
            settings = localSettingsStore.loadWheelSettings(),
            local = localSettingsStore.loadLocal(),
            reapplyOnConnect = localSettingsStore.reapplyOnConnect,
        ),
    )
    val state: StateFlow<WheelUiState> = _state.asStateFlow()

    private var link: AndroidWheelLink? = null
    private var session: InmotionV2Session? = null
    private var sessionJob: Job? = null
    private var eventsJob: Job? = null
    private var connectionWatchJob: Job? = null
    private var reappliedThisConnection = false

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

            val newSession = InmotionV2Session(
                link = established,
                config = SessionConfig(logger = { line -> Log.d(LOG_TAG, line) }),
            )
            session = newSession
            reappliedThisConnection = false
            eventsJob = scope.launch { newSession.events.collect(::onSessionEvent) }
            sessionJob = newSession.start(scope)
            startConnectionWatch()
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
        connectionWatchJob?.cancel()
        link?.close()
        eventsJob = null
        sessionJob = null
        connectionWatchJob = null
        session = null
        link = null
        _state.value = WheelUiState(
            alarmConfig = alarmConfig,
            settings = localSettingsStore.loadWheelSettings(),
            local = localSettingsStore.loadLocal(),
            reapplyOnConnect = localSettingsStore.reapplyOnConnect,
        )
    }

    // ------------------------------------------------------------ ações rápidas

    /** Aciona a buzina da roda com o opcode correto para o modelo conectado. */
    suspend fun horn() {
        send(InmotionV2Commands.hornFor(_state.value.model?.model))
    }

    /** Liga/desliga o farol. */
    suspend fun setHeadlight(on: Boolean) {
        send(InmotionV2Commands.headlight(on))
        updateLocal { it.copy(headlightOn = on) }
    }

    /** Liga/desliga as luzes diurnas (DRL). */
    suspend fun setDaytimeRunningLights(on: Boolean) {
        send(InmotionV2Commands.daytimeRunningLights(on))
        updateLocal { it.copy(daytimeRunningLightsOn = on) }
    }

    /** Ajusta o brilho do farol (0-100). */
    suspend fun setHeadlightBrightness(percent: Int) {
        send(InmotionV2Commands.headlightBrightness(percent))
        updateLocal { it.copy(headlightBrightness = percent.coerceIn(0, 100)) }
    }

    // ------------------------------------------------------------ condução

    /** Define o limite de velocidade (tiltback), em km/h. */
    suspend fun setSpeedLimit(kmh: Double) {
        send(InmotionV2Commands.speedTiltback(kmh))
        updateSettings { it.copy(speedLimitKmh = kmh) }
    }

    /** Define a inclinação dos pedais, em graus. */
    suspend fun setPedalTilt(degrees: Double) {
        send(InmotionV2Commands.pedalTilt(degrees))
        updateSettings { it.copy(pedalTiltDegrees = degrees) }
    }

    /** Define a sensibilidade dos pedais (0-100). */
    suspend fun setPedalSensitivity(percent: Int) {
        send(InmotionV2Commands.pedalSensitivity(percent))
        updateSettings { it.copy(pedalSensitivityPercent = percent.coerceIn(0, 100)) }
    }

    /** Define o modo de condução (0 = comfort, 1 = classic). */
    suspend fun setRidingMode(mode: Int) {
        send(InmotionV2Commands.ridingMode(mode))
        updateSettings { it.copy(ridingMode = mode) }
    }

    /** Liga/desliga os modos de condução separados. */
    suspend fun setSplitRidingModes(enabled: Boolean) {
        send(InmotionV2Commands.splitRidingModes(enabled))
        updateSettings { it.copy(splitRidingModesEnabled = enabled) }
    }

    /** Ajusta aceleração e frenagem dos modos separados (0-100). */
    suspend fun setSplitRidingSettings(acceleration: Int, braking: Int) {
        send(
            InmotionV2Commands.splitRidingModesSettings(
                acceleration = acceleration.coerceIn(0, 100),
                braking = braking.coerceIn(0, 100),
            ),
        )
        updateSettings {
            it.copy(
                splitRidingAccelerationPercent = acceleration.coerceIn(0, 100),
                splitRidingBrakingPercent = braking.coerceIn(0, 100),
            )
        }
    }

    /** Liga/desliga o modo performance. */
    suspend fun setPerformanceMode(on: Boolean) {
        send(InmotionV2Commands.performanceMode(on))
        updateLocal { it.copy(performanceMode = on) }
    }

    /** Permite rodar com bateria baixa (modo "go home"). */
    suspend fun setLowBatteryRiding(on: Boolean) {
        send(InmotionV2Commands.lowBatteryRiding(on))
        updateLocal { it.copy(lowBatteryRiding = on) }
    }

    /** Liga/desliga a detecção de motor sem carga. */
    suspend fun setMotorNoLoadDetection(on: Boolean) {
        send(InmotionV2Commands.motorNoLoadDetection(on))
        updateLocal { it.copy(motorNoLoadDetection = on) }
    }

    /** Liga/desliga o modo transporte. */
    suspend fun setTransportationMode(on: Boolean) {
        send(InmotionV2Commands.transportationMode(on))
        updateLocal { it.copy(transportationMode = on) }
    }

    /** Habilita/desabilita o botão da alça. */
    suspend fun setHandleButton(enabled: Boolean) {
        send(InmotionV2Commands.handleButton(enabled))
        updateLocal { it.copy(handleButton = enabled) }
    }

    /**
     * Bloqueia/desbloqueia a roda.
     *
     * A família V11y/V12/V13/V14 exige framing estendido; modelos antigos
     * aceitam a forma simples.
     */
    suspend fun setLock(locked: Boolean) {
        val model = _state.value.model?.model
        val needsExtended = model == null || when (model) {
            InmotionModel.V9, InmotionModel.V11, InmotionModel.V12, InmotionModel.V12S -> false
            else -> true
        }
        send(InmotionV2Commands.lock(locked, extended = needsExtended))
        updateLocal { it.copy(locked = locked) }
    }

    /** Liga/desliga o limite de inclinação lateral estendido. */
    suspend fun setExtendedLateralTiltLimit(on: Boolean) {
        send(InmotionV2Commands.extendedLateralTiltLimit(on))
        updateSettings { it.copy(extendedLateralTiltLimit = on) }
    }

    // ------------------------------------------------------------ som e ventilador

    /** Ajusta o volume do alto-falante (0-100). */
    suspend fun setSpeakerVolume(percent: Int) {
        send(InmotionV2Commands.speakerVolume(percent))
        updateSettings { it.copy(speakerVolumePercent = percent.coerceIn(0, 100)) }
    }

    /** Liga/desliga o ventilador. */
    suspend fun setCoolingFan(on: Boolean) {
        send(InmotionV2Commands.coolingFan(on))
        updateLocal { it.copy(fanOn = on) }
    }

    /** Liga/desliga o modo silencioso do ventilador. */
    suspend fun setCoolingFanQuietMode(on: Boolean) {
        send(InmotionV2Commands.coolingFan(on = true, quietMode = on))
        updateLocal { it.copy(fanQuietMode = on) }
    }

    /** Inicia a calibração (giroscópio ou ponto zero dos pedais). */
    suspend fun calibrate(yaw: Boolean) {
        send(InmotionV2Commands.calibration(yaw))
    }

    // ------------------------------------------------------------ internos

    private suspend fun send(command: InmotionCommand) {
        session?.send(command)
    }

    private fun updateSettings(transform: (WheelSettings) -> WheelSettings) {
        _state.update { it.copy(settings = transform(it.settings)) }
        localSettingsStore.saveWheelSettings(_state.value.settings)
    }

    private fun updateLocal(transform: (LocalSettings) -> LocalSettings) {
        _state.update { it.copy(local = transform(it.local)) }
        localSettingsStore.saveLocal(_state.value.local)
    }

    /** Liga/desliga a reaplicação das configurações salvas ao conectar. */
    fun setReapplyOnConnect(enabled: Boolean) {
        localSettingsStore.reapplyOnConnect = enabled
        _state.update { it.copy(reapplyOnConnect = enabled) }
    }

    private fun onSessionEvent(event: SessionEvent) {
        when (event) {
            is SessionEvent.TelemetryReceived -> {
                _state.update {
                    it.copy(telemetry = event.telemetry, lastTelemetryAtMillis = System.currentTimeMillis())
                }
                evaluateAlarms(event.telemetry)
            }
            is SessionEvent.ModelIdentified -> _state.update {
                it.copy(
                    model = event.model,
                    capabilities = InmotionV2Capabilities.forModel(event.model.model),
                )
            }
            is SessionEvent.SerialNumberReceived -> _state.update { it.copy(serialNumber = event.serialNumber) }
            is SessionEvent.FirmwareReceived -> _state.update { it.copy(firmware = event.versions) }
            is SessionEvent.SettingsReceived -> {
                _state.update { it.copy(settings = it.settings.mergedWith(event.settings)) }
                localSettingsStore.saveWheelSettings(_state.value.settings)
                scope.launch { maybeReapplySettings() }
            }
            is SessionEvent.AuthenticationCompleted -> _state.update { it.copy(authenticated = event.success) }
            is SessionEvent.CommandTimedOut -> Unit // observabilidade futura (log de depuração)
        }
    }

    /** Mantém valores já conhecidos quando a roda reporta apenas parte deles. */
    private fun WheelSettings.mergedWith(reported: WheelSettings): WheelSettings = WheelSettings(
        speedLimitKmh = reported.speedLimitKmh ?: speedLimitKmh,
        pedalTiltDegrees = reported.pedalTiltDegrees ?: pedalTiltDegrees,
        pedalSensitivityPercent = reported.pedalSensitivityPercent ?: pedalSensitivityPercent,
        ridingMode = reported.ridingMode ?: ridingMode,
        splitRidingModesEnabled = reported.splitRidingModesEnabled ?: splitRidingModesEnabled,
        splitRidingAccelerationPercent = reported.splitRidingAccelerationPercent ?: splitRidingAccelerationPercent,
        splitRidingBrakingPercent = reported.splitRidingBrakingPercent ?: splitRidingBrakingPercent,
        speakerVolumePercent = reported.speakerVolumePercent ?: speakerVolumePercent,
        extendedLateralTiltLimit = reported.extendedLateralTiltLimit ?: extendedLateralTiltLimit,
    )

    /**
     * Reaplica na roda as configurações salvas, uma única vez por conexão.
     *
     * Não reaplica o bloqueio (travar sozinho ao conectar seria perigoso) nem
     * a calibração.
     */
    private suspend fun maybeReapplySettings() {
        if (reappliedThisConnection || !_state.value.reapplyOnConnect) return
        reappliedThisConnection = true

        val settings = _state.value.settings
        val local = _state.value.local

        settings.speedLimitKmh?.let { setSpeedLimit(it) }
        settings.pedalTiltDegrees?.let { setPedalTilt(it) }
        settings.pedalSensitivityPercent?.let { setPedalSensitivity(it) }
        settings.ridingMode?.let { setRidingMode(it) }
        settings.splitRidingModesEnabled?.let { setSplitRidingModes(it) }
        val acceleration = settings.splitRidingAccelerationPercent
        val braking = settings.splitRidingBrakingPercent
        if (acceleration != null && braking != null) {
            setSplitRidingSettings(acceleration, braking)
        }
        settings.speakerVolumePercent?.let { setSpeakerVolume(it) }
        settings.extendedLateralTiltLimit?.let { setExtendedLateralTiltLimit(it) }

        setDaytimeRunningLights(local.daytimeRunningLightsOn)
        setHeadlight(local.headlightOn)
        setHeadlightBrightness(local.headlightBrightness)
        setCoolingFan(local.fanOn)
        if (local.fanQuietMode) setCoolingFanQuietMode(true)
        setPerformanceMode(local.performanceMode)
        setLowBatteryRiding(local.lowBatteryRiding)
        setMotorNoLoadDetection(local.motorNoLoadDetection)
        setTransportationMode(local.transportationMode)
        setHandleButton(local.handleButton)
    }

    // ------------------------------------------------------------ alarmes

    /** Atualiza e persiste a configuração de alarmes. */
    fun updateAlarmConfig(config: AlarmConfig) {
        alarmConfig = config
        alarmConfigStore.save(config)
        _state.update { it.copy(alarmConfig = config) }
    }

    private fun evaluateAlarms(telemetry: WheelTelemetry) {
        val events = alarmEngine.evaluate(telemetry, alarmConfig, System.currentTimeMillis())
        events.forEach { event ->
            alarmNotifier.notify(event, alarmConfig)
            _state.update { it.copy(activeAlarm = event) }
        }
        if (events.isNotEmpty()) scheduleAlarmClear()
    }

    private fun scheduleAlarmClear() {
        scope.launch {
            delay(ALARM_BANNER_MILLIS)
            _state.update { it.copy(activeAlarm = null) }
        }
    }

    /** Vigia a perda de telemetria e avisa uma vez por ocorrência. */
    private fun startConnectionWatch() {
        connectionWatchJob = scope.launch {
            while (isActive) {
                delay(1_000)
                val current = _state.value
                val event = alarmEngine.evaluateConnection(
                    lastTelemetryAtMillis = current.lastTelemetryAtMillis,
                    config = alarmConfig,
                    nowMillis = System.currentTimeMillis(),
                )
                if (event != null) {
                    alarmNotifier.notify(event, alarmConfig)
                    _state.update { it.copy(activeAlarm = event) }
                    scheduleAlarmClear()
                }
            }
        }
    }

    /** Libera o motor de voz do aparelho. */
    fun release() {
        disconnect()
        alarmNotifier.shutdown()
    }

    private companion object {
        const val LOG_TAG = "EucSession"
        const val DEFAULT_SCAN_MILLIS = 15_000L
        const val ALARM_BANNER_MILLIS = 6_000L
    }
}
