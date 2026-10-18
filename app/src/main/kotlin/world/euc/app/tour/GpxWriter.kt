package world.euc.app.tour

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Exporta um passeio em **GPX 1.1** — formato aceito por Strava, Garmin,
 * Google Earth, OsmAnd e afins.
 *
 * Além do que o GPX padroniza (posição, altitude e horário), cada ponto leva
 * os dados da roda numa **extensão própria** (`euc:`), que os outros apps
 * simplesmente ignoram:
 *
 * ```xml
 * <extensions>
 *   <euc:speed>25.3</euc:speed>
 *   <euc:battery>88</euc:battery>
 *   <euc:power>790</euc:power>
 *   <euc:current>10.5</euc:current>
 *   <euc:temperature>35.0</euc:temperature>
 * </extensions>
 * ```
 */
object GpxWriter {

    /** Namespace das extensões do app (dados da roda). */
    const val EXTENSION_NAMESPACE = "https://github.com/marcos2872/euc"

    fun write(tour: Tour): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine(
            """<gpx version="1.1" creator="euc" """ +
                """xmlns="http://www.topografix.com/GPX/1/1" """ +
                """xmlns:euc="$EXTENSION_NAMESPACE">""",
        )
        appendLine("  <metadata>")
        appendLine("    <name>${escape(tour.name)}</name>")
        appendLine("    <time>${isoTime(tour.startedAtMs)}</time>")
        appendLine("  </metadata>")
        appendLine("  <trk>")
        appendLine("    <name>${escape(tour.name)}</name>")
        tour.wheelModel?.let { appendLine("    <desc>${escape(it)}</desc>") }
        appendLine("    <trkseg>")
        tour.points.forEach { point ->
            appendLine(
                """      <trkpt lat="${decimal(point.latitude, 7)}" lon="${decimal(point.longitude, 7)}">""",
            )
            point.altitudeM?.let { appendLine("        <ele>${decimal(it, 1)}</ele>") }
            appendLine("        <time>${isoTime(point.timeMs)}</time>")
            val extensions = buildList {
                add("<euc:speed>${decimal(point.speedKmh, 2)}</euc:speed>")
                point.batteryPercent?.let { add("<euc:battery>$it</euc:battery>") }
                point.powerW?.let { add("<euc:power>${decimal(it, 0)}</euc:power>") }
                point.current?.let { add("<euc:current>${decimal(it, 2)}</euc:current>") }
                point.temperatureC?.let { add("<euc:temperature>${decimal(it, 1)}</euc:temperature>") }
            }
            appendLine("        <extensions>")
            extensions.forEach { appendLine("          $it") }
            appendLine("        </extensions>")
            appendLine("      </trkpt>")
        }
        appendLine("    </trkseg>")
        appendLine("  </trk>")
        appendLine("</gpx>")
    }

    /** Horário em UTC no formato do GPX (`2026-10-18T18:48:06Z`). */
    private fun isoTime(timeMs: Long): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        Locale.US,
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(timeMs))

    private fun decimal(value: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", value)

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
