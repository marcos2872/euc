package world.euc.app.ui.about

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import world.euc.app.BuildConfig
import world.euc.app.tour.CsvLogger
import world.euc.app.tour.TourStore
import world.euc.app.ui.components.LabelledValue
import world.euc.app.ui.components.SectionCard
import world.euc.app.ui.components.SwitchRow
import world.euc.app.wheel.WheelUiState
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Sobre e diagnóstico: versão do app, estado da conexão, o que está
 * gravado no aparelho e os logs CSV (para compartilhar/apagar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    state: WheelUiState,
    csvLogger: CsvLogger,
    tourStore: TourStore,
    onToggleCsvLogging: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<File>>(emptyList()) }
    var tourCount by remember { mutableIntStateOf(0) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        val (loadedLogs, loadedTours) = withContext(Dispatchers.IO) {
            csvLogger.list() to tourStore.list()
        }
        logs = loadedLogs
        tourCount = loadedTours.size
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Sobre e diagnóstico") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("Aplicativo") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    LabelledValue("Versão", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    LabelledValue("Pacote", BuildConfig.APPLICATION_ID)
                }
                Text(
                    "App pessoal para a Inmotion V11y, com protocolo Inmotion V2 próprio. " +
                        "Sem conta, sem nuvem: passeios e logs ficam neste aparelho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("Roda") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    LabelledValue("Modelo", state.model?.displayName ?: "—")
                    LabelledValue("Firmware", state.firmware?.main ?: "—")
                    LabelledValue("Série", state.serialNumber?.takeLast(8) ?: "—")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    LabelledValue(
                        "Autenticação",
                        when (state.authenticated) {
                            true -> "ok"
                            false -> "falhou"
                            null -> "—"
                        },
                    )
                    LabelledValue(
                        "Última leitura",
                        if (state.lastTelemetryAtMillis == 0L) {
                            "—"
                        } else {
                            DateFormat.getTimeInstance().format(Date(state.lastTelemetryAtMillis))
                        },
                    )
                }
            }

            SectionCard("Dados no aparelho") {
                SwitchRow(
                    label = "Registrar log CSV",
                    checked = state.csvLogging,
                    description = "Uma linha por leitura de telemetria, no formato do EUC World. " +
                        "O arquivo é fechado quando a roda desconecta.",
                    onCheckedChange = onToggleCsvLogging,
                )
                Text(
                    "$tourCount passeio(s) gravado(s)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (logs.isEmpty()) {
                    Text(
                        "Nenhum log CSV ainda. Ligue a gravação acima e conecte a roda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    logs.forEachIndexed { index, file ->
                        if (index > 0) HorizontalDivider()
                        LogRow(
                            file = file,
                            isActive = file == csvLogger.activeFile,
                            onShare = { share(context, file) },
                            onDelete = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { csvLogger.delete(file) }
                                    refreshKey++
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(
    file: File,
    isActive: Boolean,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name + if (isActive) "  (gravando)" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                String.format(
                    Locale.getDefault(),
                    "%s · %.1f KB",
                    DateFormat.getDateTimeInstance().format(Date(file.lastModified())),
                    file.length() / 1024.0,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Default.Share, contentDescription = "Compartilhar log")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Apagar log")
        }
    }
}

private fun share(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartilhar log"))
}
