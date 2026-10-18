package world.euc.app.tour

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import world.euc.app.wheel.ConnectionState
import world.euc.app.wheel.WheelUiState

/**
 * Grava um log CSV por sessão de conexão (uma linha por quadro de
 * telemetria), no formato do EUC World.
 *
 * O arquivo é aberto quando a roda conecta (se a gravação estiver ligada)
 * e fechado quando ela desconecta — assim cada arquivo corresponde a uma
 * sessão, como no app original.
 */
class TelemetryLogger(
    private val gps: GpsTracker,
    private val logger: CsvLogger,
    private val wheelState: StateFlow<WheelUiState>,
    private val recorderState: StateFlow<RecordingUiState>,
    private val enabled: () -> Boolean,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var lastSampleTimeMs = 0L
    private var gpsAcquired = false

    fun start() {
        job?.cancel()
        job = scope.launch {
            wheelState.collect { state ->
                if (!enabled() || state.connection !is ConnectionState.Connected) {
                    close()
                    return@collect
                }
                val timeMs = state.lastTelemetryAtMillis
                if (timeMs == 0L || timeMs <= lastSampleTimeMs) return@collect
                lastSampleTimeMs = timeMs
                open()
                logger.append(sampleOf(state, timeMs))
            }
        }
    }

    fun release() {
        job?.cancel()
        job = null
        close()
        scope.cancel()
    }

    private fun open() {
        if (logger.activeFile != null) return
        if (!gpsAcquired) {
            gps.acquire()
            gpsAcquired = true
        }
        logger.start()
    }

    private fun close() {
        if (logger.activeFile != null) logger.stop()
        if (gpsAcquired) {
            gps.release()
            gpsAcquired = false
        }
        lastSampleTimeMs = 0L
    }

    private fun sampleOf(state: WheelUiState, timeMs: Long): LogSample {
        val telemetry = state.telemetry
        val voltage = telemetry.voltageVolts
        val current = telemetry.currentAmps
        val recording = recorderState.value.takeIf { it.recording }
        return LogSample(
            timeMs = timeMs,
            speedKmh = telemetry.speedKmh,
            speedAvgKmh = recording?.avgSpeedKmh,
            speedAvgRidingKmh = recording?.avgRidingSpeedKmh,
            speedMaxKmh = recording?.maxSpeedKmh,
            speedLimitKmh = telemetry.speedLimitKmh,
            voltageVolts = voltage,
            currentAmps = current,
            powerW = if (voltage != null && current != null) voltage * current else null,
            batteryPercent = telemetry.batteryPercent,
            temperatureC = telemetry.temperatures?.mosfet ?: telemetry.temperatures?.board,
            temperatureMotorC = telemetry.temperatures?.motor,
            tiltDegrees = telemetry.pitchDegrees,
            rollDegrees = telemetry.rollDegrees,
            fanOn = state.local.fanOn,
            distanceKm = recording?.distanceKm,
            durationMs = recording?.durationMs,
            durationRidingMs = recording?.ridingDurationMs,
            gps = gps.lastFix,
        )
    }
}
