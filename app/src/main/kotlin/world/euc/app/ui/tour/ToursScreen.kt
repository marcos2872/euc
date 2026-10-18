package world.euc.app.ui.tour

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import world.euc.app.tour.RecordingUiState
import world.euc.app.tour.TourRecorder
import world.euc.app.tour.TourRecordingService
import world.euc.app.tour.TourStore
import world.euc.app.tour.TourSummary
import world.euc.app.tour.formatDuration
import world.euc.app.ui.components.SectionCard
import java.util.Locale

/**
 * Passeios: gravação (com mapa ao vivo) e histórico.
 *
 * Funciona com ou sem a roda conectada — a telemetria entra quando existe.
 */
@Composable
fun ToursScreen(
    recorder: TourRecorder,
    store: TourStore,
    onOpenTour: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val recording by recorder.state.collectAsStateWithLifecycle()
    var tours by remember { mutableStateOf<List<TourSummary>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey, recording.recording) {
        tours = withContext(Dispatchers.IO) { store.list() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            message = null
            startRecording(context, recorder)
        } else {
            message = "Sem permissão de localização não é possível gravar o passeio."
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (recording.recording) {
            RecordingCard(
                state = recording,
                onStop = {
                    recorder.stop()
                    TourRecordingService.stop(context)
                    refreshKey++
                },
                onDiscard = {
                    recorder.discard()
                    TourRecordingService.stop(context)
                    refreshKey++
                },
            )
            TrackMap(
                points = recording.points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                followLast = true,
            )
        } else {
            StartCard(
                gpsEnabled = recording.gpsEnabled,
                onStart = {
                    if (hasLocationPermission(context)) {
                        startRecording(context, recorder)
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ),
                        )
                    }
                },
            )
        }

        message?.let { text ->
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        SectionCard("Histórico") {
            if (tours.isEmpty()) {
                Text(
                    "Nenhum passeio gravado ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                tours.forEachIndexed { index, tour ->
                    if (index > 0) HorizontalDivider()
                    TourRow(tour = tour, onClick = { onOpenTour(tour.id) })
                }
            }
        }
    }
}

@Composable
private fun StartCard(gpsEnabled: Boolean, onStart: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Gravar passeio", style = MaterialTheme.typography.titleMedium)
            Text(
                "Registra posição (GPS, 1 Hz) e telemetria da roda, salvando em um arquivo no " +
                    "aparelho. Funciona com a tela apagada.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!gpsEnabled) {
                Text(
                    "O GPS do aparelho está desligado — ligue para registrar o percurso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(onClick = onStart) {
                Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                Text("  Iniciar gravação")
            }
        }
    }
}

@Composable
private fun RecordingCard(
    state: RecordingUiState,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text("  Gravando passeio", style = MaterialTheme.typography.titleMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LiveStat("Distância", String.format(Locale.getDefault(), "%.2f km", state.distanceKm))
                LiveStat("Tempo", formatDuration(state.durationMs))
                LiveStat(
                    "Velocidade",
                    String.format(Locale.getDefault(), "%.1f km/h", state.currentSpeedKmh),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LiveStat(
                    "Máxima",
                    String.format(Locale.getDefault(), "%.1f km/h", state.maxSpeedKmh),
                )
                LiveStat(
                    "Média",
                    String.format(Locale.getDefault(), "%.1f km/h", state.avgSpeedKmh),
                )
                LiveStat("Pontos", state.pointCount.toString())
            }

            Text(
                text = when {
                    !state.gpsEnabled -> "GPS desligado — o percurso não está sendo registrado"
                    state.gpsLost -> "Sem sinal de GPS no momento"
                    else -> "GPS ativo"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state.gpsLost) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Text("  Parar e salvar")
                }
                OutlinedButton(onClick = onDiscard) { Text("Descartar") }
            }
        }
    }
}

@Composable
private fun LiveStat(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TourRow(tour: TourSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tour.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                buildString {
                    append(String.format(Locale.getDefault(), "%.2f km", tour.distanceKm))
                    append(" · ")
                    append(formatDuration(tour.durationMs))
                    append(" · média ")
                    append(String.format(Locale.getDefault(), "%.1f km/h", tour.avgSpeedKmh))
                    append(" · máx ")
                    append(String.format(Locale.getDefault(), "%.1f km/h", tour.maxSpeedKmh))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            tour.wheelModel?.let { model ->
                Text(
                    model,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(onClick = onClick) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Abrir passeio")
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun startRecording(context: Context, recorder: TourRecorder) {
    recorder.start()
    TourRecordingService.start(context)
}
