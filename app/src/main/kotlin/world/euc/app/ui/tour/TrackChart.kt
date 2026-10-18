package world.euc.app.ui.tour

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import world.euc.app.tour.TourPoint
import java.util.Locale

/** Séries que o gráfico do passeio consegue mostrar. */
enum class ChartSeries(val label: String, val unit: String) {
    SPEED("Velocidade", "km/h"),
    CURRENT("Corrente", "A"),
    VOLTAGE("Tensão", "V"),
    POWER("Potência", "W"),
    TEMPERATURE("Temperatura", "°C"),
    ALTITUDE("Altitude", "m");

    fun valueOf(point: TourPoint): Double? = when (this) {
        SPEED -> point.speedKmh
        CURRENT -> point.current
        VOLTAGE -> point.voltage
        POWER -> point.powerW
        TEMPERATURE -> point.temperatureC
        ALTITUDE -> point.altitudeM
    }
}

/**
 * Gráfico de linha de uma série do passeio ao longo do tempo.
 * Série sem dados mostra um aviso no lugar do desenho.
 */
@Composable
fun TrackChart(
    points: List<TourPoint>,
    series: ChartSeries,
    modifier: Modifier = Modifier,
) {
    val samples = remember(points, series) {
        points.mapNotNull { point -> series.valueOf(point)?.let { point.timeMs to it } }
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val fillColor = lineColor.copy(alpha = 0.15f)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (samples.size < 2) {
            Text(
                "Sem dados de ${series.label.lowercase()} neste passeio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val values = samples.map { it.second }
        val minimum = values.min()
        val maximum = values.max()
        val average = values.average()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "mín ${format(minimum)} · méd ${format(average)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "máx ${format(maximum)} ${series.unit}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            val firstTime = samples.first().first
            val lastTime = samples.last().first
            val timeSpan = (lastTime - firstTime).coerceAtLeast(1L).toDouble()
            val range = (maximum - minimum).takeIf { it > 1e-9 } ?: 1.0

            // Linhas de referência.
            for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
                val y = size.height * fraction
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }

            fun yOf(value: Double): Float =
                (size.height - ((value - minimum) / range * size.height)).toFloat()

            val line = Path()
            val fill = Path()
            samples.forEachIndexed { index, (timeMs, value) ->
                val x = ((timeMs - firstTime) / timeSpan * size.width).toFloat()
                val y = yOf(value)
                if (index == 0) {
                    line.moveTo(x, y)
                    fill.moveTo(x, size.height)
                    fill.lineTo(x, y)
                } else {
                    line.lineTo(x, y)
                    fill.lineTo(x, y)
                }
            }
            fill.lineTo(size.width, size.height)
            fill.close()

            drawPath(path = fill, brush = Brush.verticalGradient(listOf(fillColor, Color.Transparent)))
            drawPath(path = line, color = lineColor, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

private fun format(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)
