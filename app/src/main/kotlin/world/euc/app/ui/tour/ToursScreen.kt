package world.euc.app.ui.tour

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import world.euc.app.tour.RecordingUiState
import world.euc.app.tour.TourRecorder
import world.euc.app.tour.TourRecordingService
import world.euc.app.tour.TourStore
import world.euc.app.ui.designsystem.EucSpacing
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
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var confirmDeleteSelection by remember { mutableStateOf(false) }
    var shareMenuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = EucSpacing.screen, vertical = EucSpacing.md),
        verticalArrangement = Arrangement.spacedBy(EucSpacing.md),
    ) {
        if (recording.recording) {
            RecordingCard(
                state = recording,
                onOpenLocationSettings = { openLocationSettings(context) },
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
                fallbackCenter = recording.lastFix?.let { LatLon(it.latitude, it.longitude) },
            )
        } else {
            StartCard(
                locationEnabled = recording.locationEnabled,
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
                SelectionHeader(
                    selecting = selectionMode,
                    total = tours.size,
                    selectedCount = selectedIds.size,
                    onToggleAll = {
                        selectedIds = if (selectedIds.size == tours.size) {
                            emptySet()
                        } else {
                            tours.map { it.id }.toSet()
                        }
                    },
                    onShare = { shareMenuOpen = true },
                    onDelete = { confirmDeleteSelection = true },
                    onCancel = {
                        selectionMode = false
                        selectedIds = emptySet()
                    },
                    onStartSelecting = { selectionMode = true },
                )
                tours.forEachIndexed { index, tour ->
                    if (index > 0) HorizontalDivider()
                    TourRow(
                        tour = tour,
                        selecting = selectionMode,
                        selected = tour.id in selectedIds,
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (tour.id in selectedIds) {
                                    selectedIds - tour.id
                                } else {
                                    selectedIds + tour.id
                                }
                            } else {
                                onOpenTour(tour.id)
                            }
                        },
                        onLongClick = {
                            selectionMode = true
                            selectedIds = selectedIds + tour.id
                        },
                    )
                }
            }
        }
    }

    if (shareMenuOpen) {
        val ids = selectedIds
        DropdownMenu(expanded = true, onDismissRequest = { shareMenuOpen = false }) {
            TourShareFormat.entries.forEach { format ->
                DropdownMenuItem(
                    text = { Text("${format.label} — ${format.description}") },
                    onClick = {
                        shareMenuOpen = false
                        shareTours(context, scope, store, ids, format)
                    },
                )
            }
        }
    }

    if (confirmDeleteSelection) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelection = false },
            title = { Text("Apagar ${selectedIds.size} passeio(s)?") },
            text = { Text("Os arquivos serão apagados do aparelho. Não dá para desfazer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteSelection = false
                        val ids = selectedIds
                        scope.launch {
                            withContext(Dispatchers.IO) { ids.forEach { store.delete(it) } }
                            selectedIds = emptySet()
                            selectionMode = false
                            refreshKey++
                        }
                    },
                ) { Text("Apagar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelection = false }) { Text("Cancelar") }
            },
        )
    }
}

/**
 * Cabeçalho do histórico: fora da seleção mostra a contagem e o botão
 * "Selecionar"; em seleção mostra "Todos", quantos estão marcados e as ações.
 */
@Composable
private fun SelectionHeader(
    selecting: Boolean,
    total: Int,
    selectedCount: Int,
    onToggleAll: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onStartSelecting: () -> Unit,
) {
    if (!selecting) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$total passeio(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onStartSelecting) {
                Icon(Icons.Default.SelectAll, contentDescription = null)
                Text("  Selecionar")
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onToggleAll) {
            Text(if (selectedCount == total) "Nenhum" else "Todos")
        }
        Text(
            "$selectedCount de $total",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onShare, enabled = selectedCount > 0) {
            Icon(Icons.Default.Share, contentDescription = "Compartilhar selecionados")
        }
        TextButton(onClick = onDelete, enabled = selectedCount > 0) {
            Icon(Icons.Default.Delete, contentDescription = "Apagar selecionados")
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = "Sair da seleção")
        }
    }
}

@Composable
private fun StartCard(locationEnabled: Boolean, onStart: () -> Unit) {
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
            if (!locationEnabled) {
                Text(
                    "A localização do aparelho está desligada — ligue para registrar o percurso.",
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
    onOpenLocationSettings: () -> Unit,
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
                text = locationStatus(state),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.gpsLost || !state.locationEnabled) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (!state.locationEnabled) {
                OutlinedButton(onClick = onOpenLocationSettings) {
                    Text("Abrir ajustes de localização")
                }
            }

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TourRow(
    tour: TourSummary,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
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
        if (!selecting) {
            OutlinedButton(onClick = onClick) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Abrir passeio")
            }
        }
    }
}

/**
 * Texto de estado da localização, do mais grave ao normal: sem localização no
 * aparelho, sem fix nenhum, sinal fraco (rede ou GPS impreciso) e GPS bom.
 */
private fun locationStatus(state: RecordingUiState): String {
    val fix = state.lastFix
    return when {
        !state.locationEnabled ->
            "Localização desligada no aparelho — o percurso não está sendo registrado"
        fix == null ->
            "Procurando sinal de GPS… (em locais fechados o sinal não chega)"
        state.gpsLost ->
            "Sem sinal de GPS no momento"
        !state.hasAccurateSignal ->
            "Sinal fraco (±${fix.accuracyM?.toInt() ?: "?"} m) — o percurso precisa de céu aberto"
        else ->
            "GPS ativo (±${fix.accuracyM?.toInt() ?: "?"} m)"
    }
}

private fun openLocationSettings(context: Context) {
    val intent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun startRecording(context: Context, recorder: TourRecorder) {
    recorder.start()
    TourRecordingService.start(context)
}
