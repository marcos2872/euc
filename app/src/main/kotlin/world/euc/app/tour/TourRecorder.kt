package world.euc.app.tour

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import world.euc.app.wheel.WheelUiState
import java.util.Locale

/** Estado da gravação em andamento, para a UI e a notificação. */
data class RecordingUiState(
    val recording: Boolean = false,
    val tourId: String? = null,
    val startedAtMs: Long = 0L,
    val distanceKm: Double = 0.0,
    val durationMs: Long = 0L,
    val currentSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val pointCount: Int = 0,
    /** Pontos gravados até agora (para o mapa ao vivo). */
    val points: List<TourPoint> = emptyList(),
    val gpsEnabled: Boolean = true,
    /** Idade do último fix de GPS, em ms (null = nenhum fix ainda). */
    val lastFixAgeMs: Long? = null,
    val wheelModel: String? = null,
) {
    /** Sinal de GPS considerado perdido (sem fix há mais de 10 s). */
    val gpsLost: Boolean get() = !gpsEnabled || lastFixAgeMs == null || lastFixAgeMs > 10_000

    val avgSpeedKmh: Double get() = Tour.averageSpeed(distanceKm, durationMs)

    val ridingDurationMs: Long get() = ridingDurationMsOf(points)

    val avgRidingSpeedKmh: Double get() = Tour.averageSpeed(distanceKm, ridingDurationMs)
}

/**
 * Grava um passeio: junta o GPS (1 Hz) com a telemetria da roda e acumula
 * distância/estatísticas, salvando o arquivo em disco (com autosave, para
 * não perder o passeio se o app morrer).
 */
class TourRecorder(
    context: Context,
    private val gps: GpsTracker,
    private val store: TourStore,
    private val wheelState: StateFlow<WheelUiState>,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(RecordingUiState(gpsEnabled = gps.providerEnabled))
    val state: StateFlow<RecordingUiState> = _state.asStateFlow()

    private var builder = TourTrackBuilder()
    private var startedAtMs = 0L
    private var tourId: String? = null
    private var tickJob: Job? = null
    private var wheelJob: Job? = null
    private var autosaveJob: Job? = null

    @Volatile
    private var telemetry = TelemetrySnapshot()

    @Volatile
    private var wheelModel: String? = null

    @Volatile
    private var lastRecordedFixTimeMs = 0L

    val isRecording: Boolean get() = _state.value.recording

    /** Começa a gravar. Ignorado se já estiver gravando. */
    fun start() {
        if (_state.value.recording) return

        startedAtMs = System.currentTimeMillis()
        tourId = idFrom(startedAtMs)
        builder = TourTrackBuilder()
        lastRecordedFixTimeMs = 0L
        telemetry = snapshotOf(wheelState.value)
        wheelModel = wheelState.value.model?.displayName
        gps.reset()
        gps.acquire()

        _state.value = RecordingUiState(
            recording = true,
            tourId = tourId,
            startedAtMs = startedAtMs,
            gpsEnabled = gps.providerEnabled,
            wheelModel = wheelModel,
        )

        tickJob = scope.launch { tickLoop() }
        wheelJob = scope.launch {
            wheelState.collect { state ->
                telemetry = snapshotOf(state)
                wheelModel = state.model?.displayName ?: wheelModel
            }
        }
        autosaveJob = scope.launch {
            while (currentCoroutineContext().isActive) {
                delay(AUTOSAVE_INTERVAL_MS)
                saveCurrent()
            }
        }
    }

    /** Encerra a gravação, salva o passeio e devolve o que foi gravado. */
    fun stop(): Tour? {
        if (!_state.value.recording) return null
        val id = tourId
        cancelJobs()
        gps.release()

        val tour = buildTour(id, endedAtMs = System.currentTimeMillis())
        val saved = runCatching { store.save(tour) }.isSuccess
        _state.value = RecordingUiState(
            recording = false,
            gpsEnabled = gps.providerEnabled,
            wheelModel = wheelModel,
            pointCount = if (saved) tour.points.size else 0,
        )
        return tour.takeIf { saved }
    }

    /** Cancela a gravação em andamento e apaga o arquivo parcial. */
    fun discard() {
        val id = tourId
        cancelJobs()
        gps.release()
        id?.let { store.delete(it) }
        _state.value = RecordingUiState(gpsEnabled = gps.providerEnabled, wheelModel = wheelModel)
    }

    fun release() {
        cancelJobs()
        gps.release()
        scope.cancel()
    }

    // ------------------------------------------------------------ interno

    private suspend fun tickLoop() {
        while (currentCoroutineContext().isActive) {
            delay(TICK_INTERVAL_MS)
            val fix = gps.lastFix
            if (fix != null && fix.timeMs > lastRecordedFixTimeMs) {
                lastRecordedFixTimeMs = fix.timeMs
                builder.add(fix, telemetry)
            }
            publish()
        }
    }

    private fun publish() {
        val now = System.currentTimeMillis()
        val points = builder.snapshot()
        val fix = gps.lastFix
        _state.update { current ->
            current.copy(
                distanceKm = builder.currentDistanceKm,
                durationMs = now - startedAtMs,
                currentSpeedKmh = telemetry.speedKmh ?: fix?.speedKmh ?: 0.0,
                maxSpeedKmh = points.maxOfOrNull { it.speedKmh } ?: 0.0,
                pointCount = points.size,
                points = points,
                gpsEnabled = gps.providerEnabled,
                lastFixAgeMs = fix?.let { now - it.timeMs },
                wheelModel = wheelModel,
            )
        }
    }

    /** Salva o passeio parcial (autosave e fim da gravação). */
    private fun saveCurrent() {
        val id = tourId ?: return
        if (builder.isEmpty) return
        runCatching { store.save(buildTour(id, endedAtMs = System.currentTimeMillis())) }
    }

    private fun buildTour(id: String?, endedAtMs: Long): Tour = Tour(
        id = id ?: idFrom(startedAtMs),
        name = nameFrom(startedAtMs),
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
        wheelModel = wheelModel,
        points = builder.snapshot(),
    )

    private fun cancelJobs() {
        tickJob?.cancel()
        wheelJob?.cancel()
        autosaveJob?.cancel()
        tickJob = null
        wheelJob = null
        autosaveJob = null
    }

    private fun snapshotOf(state: WheelUiState): TelemetrySnapshot {
        val telemetry = state.telemetry
        val voltage = telemetry.voltageVolts
        val current = telemetry.currentAmps
        return TelemetrySnapshot(
            speedKmh = telemetry.speedKmh,
            batteryPercent = telemetry.batteryPercent?.toInt(),
            voltage = voltage,
            current = current,
            powerW = if (voltage != null && current != null) voltage * current else null,
            temperatureC = telemetry.temperatures?.mosfet ?: telemetry.temperatures?.board,
        )
    }

    companion object {
        private const val TICK_INTERVAL_MS = 1_000L
        private const val AUTOSAVE_INTERVAL_MS = 30_000L

        fun idFrom(timeMs: Long): String =
            String.format(Locale.US, "%1\$tY-%1\$tm-%1\$td-%1\$tH%1\$tM%1\$tS", timeMs)

        fun nameFrom(timeMs: Long): String =
            String.format(Locale.US, "Passeio de %1\$td/%1\$tm %1\$tH:%1\$tM", timeMs)
    }
}
