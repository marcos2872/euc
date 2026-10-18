package world.euc.app.tour

import java.io.BufferedWriter
import java.io.File
import java.util.Locale

/** Uma linha do log de telemetria. Campos ausentes ficam vazios no CSV. */
data class LogSample(
    val timeMs: Long,
    val speedKmh: Double? = null,
    val speedAvgKmh: Double? = null,
    val speedAvgRidingKmh: Double? = null,
    val speedMaxKmh: Double? = null,
    val speedLimitKmh: Double? = null,
    val voltageVolts: Double? = null,
    val currentAmps: Double? = null,
    val powerW: Double? = null,
    val batteryPercent: Double? = null,
    val temperatureC: Double? = null,
    val temperatureMotorC: Double? = null,
    val tiltDegrees: Double? = null,
    val rollDegrees: Double? = null,
    val fanOn: Boolean? = null,
    val alarmBits: Int? = null,
    val distanceKm: Double? = null,
    val durationMs: Long? = null,
    val durationRidingMs: Long? = null,
    val gps: GpsFix? = null,
)

/**
 * Grava a telemetria em CSV com as **mesmas 46 colunas do EUC World**
 * (cabeçalho, ordem e formatos), para os arquivos poderem ser analisados
 * com as ferramentas que já existem para aquele formato.
 *
 * Separador vírgula e fim de linha CRLF, como no original.
 */
class CsvLogger(private val rootDir: File) {

    private var writer: BufferedWriter? = null
    private var lastFlushMs = 0L

    var activeFile: File? = null
        private set

    private var startedAtMs = 0L

    /** Abre um arquivo novo (fecha o anterior, se houver). */
    fun start(): File? {
        stop()
        rootDir.mkdirs()
        val target = File(rootDir, "euc-log-${fileStamp()}.csv")
        return runCatching {
            val output = target.bufferedWriter()
            output.write(COLUMNS.joinToString(","))
            output.write(EOL)
            writer = output
            activeFile = target
            startedAtMs = System.currentTimeMillis()
            lastFlushMs = startedAtMs
            target
        }.getOrNull()
    }

    fun append(sample: LogSample) {
        val output = writer ?: return
        runCatching {
            output.write(formatRow(sample, startedAtMs))
            output.write(EOL)
            if (sample.timeMs - lastFlushMs >= FLUSH_INTERVAL_MS) {
                output.flush()
                lastFlushMs = sample.timeMs
            }
        }
    }

    fun stop() {
        runCatching {
            writer?.flush()
            writer?.close()
        }
        writer = null
        activeFile = null
    }

    /** Logs já gravados, do mais recente para o mais antigo. */
    fun list(): List<File> =
        rootDir.listFiles { file -> file.extension == "csv" }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    fun delete(file: File): Boolean {
        if (file == activeFile) stop()
        return file.delete()
    }

    private fun fileStamp(): String =
        String.format(
            Locale.US,
            "%1\$tY%1\$tm%1\$td-%1\$tH%1\$tM%1\$tS",
            System.currentTimeMillis(),
        )

    companion object {
        const val EOL = "\r\n"

        private const val FLUSH_INTERVAL_MS = 5_000L

        /** Cabeçalho do EUC World, na ordem exata. */
        val COLUMNS: List<String> = listOf(
            "datetime", "duration", "duration_riding", "distance", "distance_total",
            "speed", "speed_avg", "speed_avg_riding", "speed_max", "speed_limit",
            "acceleration", "voltage", "current", "current_phase", "power",
            "energy_consumption", "energy_consumed", "battery", "battery_corrected_voltage",
            "temp", "temp_motor", "temp_batt", "temp_cpu", "temp_imu", "safety_margin",
            "cpu_load", "tilt", "roll", "fan", "alert", "alarm", "gps_datetime",
            "gps_duration", "gps_duration_riding", "gps_distance", "gps_lat", "gps_lon",
            "gps_speed", "gps_speed_avg", "gps_speed_avg_riding", "gps_speed_max", "gps_alt",
            "gps_bearing", "gps_acc", "hr", "extra",
        )

        /** Monta a linha CSV (46 campos, na ordem do cabeçalho). */
        fun formatRow(sample: LogSample, startedAtMs: Long): String {
            val elapsedMs = (sample.timeMs - startedAtMs).coerceAtLeast(0L)
            val gps = sample.gps
            val fields = listOf(
                TourFormat.dateTime(sample.timeMs),
                TourFormat.number(elapsedMs / 1000.0, 3),
                TourFormat.optional(sample.durationRidingMs?.let { it / 1000.0 }, 3),
                TourFormat.optional(sample.distanceKm, 3),
                "",
                TourFormat.optional(sample.speedKmh, 2),
                TourFormat.optional(sample.speedAvgKmh, 2),
                TourFormat.optional(sample.speedAvgRidingKmh, 2),
                TourFormat.optional(sample.speedMaxKmh, 2),
                TourFormat.optional(sample.speedLimitKmh, 2),
                "",
                TourFormat.optional(sample.voltageVolts, 2),
                TourFormat.optional(sample.currentAmps, 2),
                "",
                TourFormat.optional(sample.powerW, 0),
                "",
                "",
                TourFormat.optional(sample.batteryPercent, 0),
                "",
                TourFormat.optional(sample.temperatureC, 0),
                TourFormat.optional(sample.temperatureMotorC, 0),
                "",
                "",
                "",
                "",
                "",
                TourFormat.optional(sample.tiltDegrees, 1),
                TourFormat.optional(sample.rollDegrees, 1),
                sample.fanOn?.toString().orEmpty(),
                "",
                sample.alarmBits?.toString().orEmpty(),
                gps?.let { TourFormat.dateTime(it.timeMs) }.orEmpty(),
                gps?.let { TourFormat.number((sample.timeMs - it.timeMs) / 1000.0, 3) }.orEmpty(),
                "",
                "",
                TourFormat.optional(gps?.latitude, 7),
                TourFormat.optional(gps?.longitude, 7),
                TourFormat.optional(gps?.speedKmh, 2),
                "",
                "",
                "",
                TourFormat.optional(gps?.altitudeM, 1),
                TourFormat.optional(gps?.bearingDegrees?.toDouble(), 1),
                TourFormat.optional(gps?.accuracyM?.toDouble(), 1),
                "",
                "",
            )
            check(fields.size == COLUMNS.size) { "linha do log com ${fields.size} campos" }
            return fields.joinToString(",")
        }
    }
}
