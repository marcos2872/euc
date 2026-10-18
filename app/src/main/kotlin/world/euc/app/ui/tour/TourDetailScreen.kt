package world.euc.app.ui.tour

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import world.euc.app.tour.Tour
import world.euc.app.tour.TourStore
import world.euc.app.tour.formatDuration
import world.euc.app.ui.components.ChoiceRow
import world.euc.app.ui.components.LabelledValue
import world.euc.app.ui.components.SectionCard
import world.euc.app.ui.components.format
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Detalhe de um passeio: mapa da trilha, estatísticas e gráfico. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TourDetailScreen(
    tourId: String,
    store: TourStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tour by remember(tourId) { mutableStateOf<Tour?>(null) }
    var loading by remember(tourId) { mutableStateOf(true) }
    var series by remember { mutableStateOf(ChartSeries.SPEED) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(tourId) {
        tour = withContext(Dispatchers.IO) { store.load(tourId) }
        loading = false
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(tour?.name ?: "Passeio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        val current = tour
        if (loading) {
            Text("Carregando…", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        if (current == null) {
            Text(
                "Passeio não encontrado.",
                modifier = Modifier.padding(padding).padding(16.dp),
                color = MaterialTheme.colorScheme.error,
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                DateFormat.getDateTimeInstance().format(Date(current.startedAtMs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TrackMap(
                points = current.points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
            )

            SectionCard("Resumo") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    LabelledValue("Distância", String.format(Locale.getDefault(), "%.2f km", current.distanceKm))
                    LabelledValue("Duração", formatDuration(current.durationMs))
                    LabelledValue("Em movimento", formatDuration(current.ridingDurationMs))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    LabelledValue("Vel. média", current.avgSpeedKmh.format(1) + " km/h")
                    LabelledValue("Média mov.", current.avgRidingSpeedKmh.format(1) + " km/h")
                    LabelledValue("Vel. máxima", current.maxSpeedKmh.format(1) + " km/h")
                }
            }

            SectionCard("Roda") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    LabelledValue(
                        "Bateria",
                        if (current.startBatteryPercent != null && current.endBatteryPercent != null) {
                            "${current.startBatteryPercent}% → ${current.endBatteryPercent}%"
                        } else {
                            "—"
                        },
                    )
                    LabelledValue("Potência máx", current.maxPowerW.format(0) + " W")
                    LabelledValue("Corrente máx", current.maxCurrentAmps.format(1) + " A")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    LabelledValue("Temp. máx", current.maxTemperatureC.format(0) + " °C")
                    LabelledValue("Altitude", altitudeLabel(current))
                    LabelledValue("Modelo", current.wheelModel ?: "—")
                }
            }

            SectionCard("Gráfico") {
                ChoiceRow(
                    label = "Série",
                    options = ChartSeries.entries.map { it to it.label },
                    selected = series,
                    onSelect = { series = it },
                )
                TrackChart(
                    points = current.points,
                    series = series,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                store.exportCsv(
                                    current,
                                    File(context.cacheDir, "${current.id}.csv"),
                                )
                            }
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.files",
                                file,
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, current.name)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(intent, "Compartilhar passeio"),
                            )
                        }
                    },
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text("  Compartilhar CSV")
                }
                OutlinedButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("  Excluir")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir passeio?") },
            text = { Text("O arquivo do passeio será apagado do aparelho.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        scope.launch {
                            withContext(Dispatchers.IO) { store.delete(tourId) }
                            onBack()
                        }
                    },
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}

private fun altitudeLabel(tour: Tour): String {
    val altitudes = tour.points.mapNotNull { it.altitudeM }
    if (altitudes.isEmpty()) return "—"
    return String.format(
        Locale.getDefault(),
        "%.0f–%.0f m",
        altitudes.min(),
        altitudes.max(),
    )
}
