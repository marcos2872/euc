package world.euc.app.ui.tour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import world.euc.app.tour.TourPoint
import world.euc.app.ui.components.ChartSample
import world.euc.app.ui.components.TimeSeriesChart

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

/** Gráfico de uma série do passeio ao longo do tempo. */
@Composable
fun TrackChart(
    points: List<TourPoint>,
    series: ChartSeries,
    modifier: Modifier = Modifier,
) {
    val samples = remember(points, series) {
        points.mapNotNull { point ->
            series.valueOf(point)?.let { ChartSample(point.timeMs, it) }
        }
    }

    TimeSeriesChart(
        samples = samples,
        unit = series.unit,
        modifier = modifier,
        // Passeios podem ter milhares de pontos: permite arrastar e ampliar.
        interactive = true,
        emptyMessage = "Sem dados de ${series.label.lowercase()} neste passeio.",
    )
}
