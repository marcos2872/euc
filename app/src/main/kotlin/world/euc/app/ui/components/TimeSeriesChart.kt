package world.euc.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Amostra de um gráfico no tempo. */
data class ChartSample(val timeMs: Long, val value: Double)

/**
 * Gráfico de linha de uma série no tempo, com faixa preenchida, linhas de
 * referência e os valores mínimos/médio/máximo acima do desenho.
 *
 * Sem pelo menos duas amostras mostra [emptyMessage] no lugar.
 */
@Composable
fun TimeSeriesChart(
    samples: List<ChartSample>,
    unit: String,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    emptyMessage: String = "Sem dados.",
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val fillColor = lineColor.copy(alpha = 0.15f)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (samples.size < 2) {
            Text(
                emptyMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val values = samples.map { it.value }
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
                "máx ${format(maximum)} $unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val firstTime = samples.first().timeMs
            val lastTime = samples.last().timeMs
            val timeSpan = (lastTime - firstTime).coerceAtLeast(1L).toDouble()
            val range = (maximum - minimum).takeIf { it > 1e-9 } ?: 1.0

            for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, size.height * fraction),
                    end = Offset(size.width, size.height * fraction),
                    strokeWidth = 1f,
                )
            }

            fun yOf(value: Double): Float =
                (size.height - ((value - minimum) / range * size.height)).toFloat()

            val line = Path()
            val fill = Path()
            samples.forEachIndexed { index, sample ->
                val x = ((sample.timeMs - firstTime) / timeSpan * size.width).toFloat()
                val y = yOf(sample.value)
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

            drawPath(
                path = fill,
                brush = Brush.verticalGradient(listOf(fillColor, Color.Transparent)),
            )
            drawPath(path = line, color = lineColor, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

private fun format(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)
