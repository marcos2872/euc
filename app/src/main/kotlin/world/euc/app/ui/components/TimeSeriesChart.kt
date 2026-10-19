package world.euc.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Amostra de um gráfico no tempo. */
data class ChartSample(val timeMs: Long, val value: Double)

/**
 * Gráfico de linha da série no tempo (Vico): linha na cor de destaque, área com
 * gradiente por baixo, eixos com rótulos de hora e de valor, e — quando
 * [interactive] — arrastar/zoom para explorar passeios longos.
 *
 * Sem pelo menos duas amostras mostra [emptyMessage] no lugar.
 */
@Composable
fun TimeSeriesChart(
    samples: List<ChartSample>,
    unit: String,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    interactive: Boolean = false,
    emptyMessage: String = "Sem dados.",
) {
    val accent = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (samples.size < 2) {
            Text(
                emptyMessage,
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
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
                color = labelColor,
            )
            Text(
                "máx ${format(maximum)} $unit",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
        }

        val modelProducer = remember { CartesianChartModelProducer() }
        LaunchedEffect(samples) {
            modelProducer.runTransaction {
                lineModel {
                    series(
                        x = samples.map { it.timeMs.toDouble() },
                        y = samples.map { it.value },
                    )
                }
            }
        }

        val line = remember(accent) {
            LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(accent)),
                stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp),
                areaFill = LineCartesianLayer.AreaFill.single(
                    Fill(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0f)),
                        ),
                    ),
                ),
            )
        }
        // Janela curta (gráfico ao vivo) precisa de segundos; passeios longos, não.
        val timePattern = if (samples.last().timeMs - samples.first().timeMs < 10 * 60_000L) {
            "mm:ss"
        } else {
            "HH:mm"
        }
        val valueFormatter = remember(unit) {
            CartesianValueFormatter { _, value, _ ->
                if (unit.isEmpty()) format(value) else "${format(value)} $unit"
            }
        }
        val spanMillis = (samples.last().timeMs - samples.first().timeMs).coerceAtLeast(1L)
        // O Vico posiciona os rótulos em passos de x (aqui, milissegundos desde
        // a primeira amostra): ~5 rótulos por janela, deslocados meio passo para
        // não encostarem nas bordas. Sem o recuo automático do primeiro rótulo,
        // a linha encosta na borda esquerda do gráfico.
        val labelStepMillis = remember(spanMillis) { (spanMillis / 5).coerceAtLeast(1_000L).toInt() }
        val itemPlacer = remember(labelStepMillis) {
            HorizontalAxis.ItemPlacer.aligned(spacing = { labelStepMillis })
        }
        val scrollState = rememberVicoScrollState(scrollEnabled = interactive)


        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(line),
                ),
                startAxis = VerticalAxis.rememberStart(valueFormatter = valueFormatter),
                bottomAxis = HorizontalAxis.rememberBottom(
                    // Os rótulos de hora são desenhados por nós, logo abaixo do
                    // gráfico, alinhados às bordas (o Vico centraliza o primeiro
                    // no início, deixando-o fora da coluna dos rótulos verticais
                    // — e deslocar os extremos fazia uns sobreporem os outros).
                    label = null,
                    itemPlacer = itemPlacer,
                ),
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            scrollState = scrollState,
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                timeLabel(samples.first().timeMs, timePattern),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
            Text(
                timeLabel((samples.first().timeMs + samples.last().timeMs) / 2, timePattern),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                textAlign = TextAlign.Center,
            )
            Text(
                timeLabel(samples.last().timeMs, timePattern),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** Rótulo de hora (fuso local) para o eixo horizontal. */
private fun timeLabel(timeMs: Long, pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timeMs))

private fun format(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)
