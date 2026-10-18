package world.euc.app.tour

import java.io.File
import java.util.Locale

/**
 * Guarda os passeios em arquivos de texto simples (`<id>.tour`) num
 * diretório — sem banco de dados: fácil de inspecionar, copiar e apagar.
 *
 * Formato (linhas `chave=valor` e, depois do cabeçalho de colunas, uma
 * linha CSV por ponto):
 * ```
 * euc-tour 1
 * id=2026-10-18-143012
 * name=Passeio de 18/10 14:30
 * started=1729265412000
 * ended=1729269012000
 * model=V11Y
 * distance=12.345
 * maxspeed=42.10
 * points=1200
 * time,lat,lon,speed,alt,battery,voltage,current,power,temp,distance
 * ```
 */
class TourStore(private val rootDir: File) {

    init {
        rootDir.mkdirs()
    }

    /** Passeios gravados, do mais recente para o mais antigo. */
    fun list(): List<TourSummary> =
        rootDir.listFiles { file -> file.extension == EXTENSION }
            ?.mapNotNull { runCatching { readSummary(it) }.getOrNull() }
            ?.sortedByDescending { it.startedAtMs }
            ?: emptyList()

    fun load(id: String): Tour? = runCatching { readTour(fileFor(id)) }.getOrNull()

    fun save(tour: Tour): File {
        val file = fileFor(tour.id)
        file.writeText(
            buildString {
                appendLine(MARKER)
                appendLine("$KEY_ID=${tour.id}")
                appendLine("$KEY_NAME=${tour.name}")
                appendLine("$KEY_STARTED=${tour.startedAtMs}")
                appendLine("$KEY_ENDED=${tour.endedAtMs}")
                appendLine("$KEY_MODEL=${tour.wheelModel.orEmpty()}")
                appendLine("$KEY_DISTANCE=${TourFormat.number(tour.distanceKm, 3)}")
                appendLine("$KEY_MAX_SPEED=${TourFormat.number(tour.maxSpeedKmh, 2)}")
                appendLine("$KEY_POINTS=${tour.points.size}")
                appendLine(COLUMNS_HEADER)
                tour.points.forEach { appendLine(formatPoint(it)) }
            },
        )
        return file
    }

    fun delete(id: String): Boolean = fileFor(id).delete()

    /** Exporta o passeio em CSV (para compartilhar/analisar no PC). */
    fun exportCsv(tour: Tour, target: File): File {
        target.writeText(
            buildString {
                appendLine("datetime,time,lat,lon,speed,alt,battery,voltage,current,power,temp,distance")
                tour.points.forEach { point ->
                    append(TourFormat.dateTime(point.timeMs)).append(',')
                    appendLine(formatPoint(point))
                }
            },
        )
        return target
    }

    /** Exporta o passeio em GPX (Strava, Garmin, Google Earth…). */
    fun exportGpx(tour: Tour, target: File): File {
        target.writeText(GpxWriter.write(tour))
        return target
    }

    fun fileFor(id: String): File = File(rootDir, "$id.$EXTENSION")

    // ------------------------------------------------------------- leitura

    private fun readSummary(file: File): TourSummary {
        val meta = mutableMapOf<String, String>()
        file.useLines { lines ->
            for (line in lines) {
                if (line == MARKER) continue
                if (line.startsWith("$COLUMN_TIME,")) break
                val separator = line.indexOf('=')
                if (separator > 0) meta[line.substring(0, separator)] = line.substring(separator + 1)
            }
        }
        return summaryOf(meta, file.nameWithoutExtension)
    }

    private fun readTour(file: File): Tour {
        val meta = mutableMapOf<String, String>()
        val points = mutableListOf<TourPoint>()
        var inPoints = false
        file.useLines { lines ->
            for (line in lines) {
                if (!inPoints) {
                    when {
                        line == MARKER -> Unit
                        line.startsWith("$COLUMN_TIME,") -> inPoints = true
                        else -> {
                            val separator = line.indexOf('=')
                            if (separator > 0) {
                                meta[line.substring(0, separator)] = line.substring(separator + 1)
                            }
                        }
                    }
                } else if (line.isNotBlank()) {
                    parsePoint(line)?.let(points::add)
                }
            }
        }
        return Tour(
            id = meta[KEY_ID] ?: file.nameWithoutExtension,
            name = meta[KEY_NAME] ?: file.nameWithoutExtension,
            startedAtMs = meta[KEY_STARTED]?.toLongOrNull() ?: 0L,
            endedAtMs = meta[KEY_ENDED]?.toLongOrNull() ?: 0L,
            wheelModel = meta[KEY_MODEL]?.takeIf { it.isNotBlank() },
            points = points,
        )
    }

    private fun summaryOf(meta: Map<String, String>, fallbackId: String): TourSummary {
        val started = meta[KEY_STARTED]?.toLongOrNull() ?: 0L
        val ended = meta[KEY_ENDED]?.toLongOrNull() ?: started
        return TourSummary(
            id = meta[KEY_ID] ?: fallbackId,
            name = meta[KEY_NAME] ?: fallbackId,
            startedAtMs = started,
            endedAtMs = ended,
            distanceKm = meta[KEY_DISTANCE]?.toDoubleOrNull() ?: 0.0,
            maxSpeedKmh = meta[KEY_MAX_SPEED]?.toDoubleOrNull() ?: 0.0,
            wheelModel = meta[KEY_MODEL]?.takeIf { it.isNotBlank() },
            pointCount = meta[KEY_POINTS]?.toIntOrNull() ?: 0,
        )
    }

    // ------------------------------------------------------------ formatação

    private fun formatPoint(point: TourPoint): String = buildString {
        append(point.timeMs).append(',')
        append(TourFormat.number(point.latitude, 7)).append(',')
        append(TourFormat.number(point.longitude, 7)).append(',')
        append(TourFormat.number(point.speedKmh, 2)).append(',')
        append(TourFormat.optional(point.altitudeM, 1)).append(',')
        append(point.batteryPercent ?: "").append(',')
        append(TourFormat.optional(point.voltage, 2)).append(',')
        append(TourFormat.optional(point.current, 2)).append(',')
        append(TourFormat.optional(point.powerW, 0)).append(',')
        append(TourFormat.optional(point.temperatureC, 1)).append(',')
        append(TourFormat.number(point.distanceKm, 3))
    }

    private fun parsePoint(line: String): TourPoint? {
        val fields = line.split(',')
        if (fields.size < 4) return null
        val timeMs = fields[0].toLongOrNull() ?: return null
        val latitude = fields[1].toDoubleOrNull() ?: return null
        val longitude = fields[2].toDoubleOrNull() ?: return null
        return TourPoint(
            timeMs = timeMs,
            latitude = latitude,
            longitude = longitude,
            speedKmh = fields.getOrNull(3)?.toDoubleOrNull() ?: 0.0,
            altitudeM = fields.getOrNull(4)?.toDoubleOrNull(),
            batteryPercent = fields.getOrNull(5)?.toIntOrNull(),
            voltage = fields.getOrNull(6)?.toDoubleOrNull(),
            current = fields.getOrNull(7)?.toDoubleOrNull(),
            powerW = fields.getOrNull(8)?.toDoubleOrNull(),
            temperatureC = fields.getOrNull(9)?.toDoubleOrNull(),
            distanceKm = fields.getOrNull(10)?.toDoubleOrNull() ?: 0.0,
        )
    }

    companion object {
        const val EXTENSION = "tour"

        private const val MARKER = "euc-tour 1"
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_STARTED = "started"
        private const val KEY_ENDED = "ended"
        private const val KEY_MODEL = "model"
        private const val KEY_DISTANCE = "distance"
        private const val KEY_MAX_SPEED = "maxspeed"
        private const val KEY_POINTS = "points"

        private const val COLUMN_TIME = "time"
        private const val COLUMNS_HEADER =
            "time,lat,lon,speed,alt,battery,voltage,current,power,temp,distance"
    }
}

/** Formatação numérica do arquivo (ponto decimal, independente do idioma). */
internal object TourFormat {

    fun number(value: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", value)

    fun optional(value: Double?, decimals: Int): String =
        if (value == null) "" else number(value, decimals)

    fun dateTime(timeMs: Long): String =
        String.format(Locale.US, "%1\$tY-%1\$tm-%1\$tdT%1\$tH:%1\$tM:%1\$tS", timeMs)
}
