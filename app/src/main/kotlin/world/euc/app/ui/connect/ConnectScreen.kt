package world.euc.app.ui.connect

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import world.euc.app.wheel.ConnectionState
import world.euc.app.wheel.WheelConnectionManager
import world.euc.app.wheel.WheelUiState
import world.euc.ble.DiscoveredWheel

/** Permissões necessárias para escanear e conectar, conforme a versão do Android. */
private fun requiredBlePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun Context.hasBlePermissions(): Boolean =
    requiredBlePermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

/**
 * Tela de conexão: pede permissões, escaneia rodas próximas e conecta à
 * escolhida. Cada roda aparece com os protocolos candidatos detectados.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(manager: WheelConnectionManager, state: WheelUiState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasPermissions by remember { mutableStateOf(context.hasBlePermissions()) }
    var wheels by remember { mutableStateOf<List<DiscoveredWheel>>(emptyList()) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    val isScanning = scanJob?.isActive == true

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasPermissions = result.values.all { it }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Conectar à roda") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BluetoothStateHint(context)

            if (!hasPermissions) {
                Button(onClick = { permissionLauncher.launch(requiredBlePermissions()) }) {
                    Text("Conceder permissões de Bluetooth")
                }
            }

            Button(
                onClick = {
                    if (isScanning) {
                        scanJob?.cancel()
                        scanJob = null
                    } else {
                        wheels = emptyList()
                        scanJob = scope.launch {
                            manager.scan().collect { wheel ->
                                wheels = (wheels.filterNot { it.device.address == wheel.device.address } + wheel)
                                    .sortedByDescending { it.rssi }
                            }
                        }
                    }
                },
                enabled = hasPermissions,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isScanning) "Parar busca" else "Buscar rodas")
            }

            if (isScanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    Spacer(Modifier.padding(4.dp))
                    Text("Procurando...", style = MaterialTheme.typography.bodyMedium)
                }
            }

            ConnectionStatus(state)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(wheels, key = { it.device.address }) { wheel ->
                    WheelCard(wheel) {
                        scanJob?.cancel()
                        scanJob = null
                        scope.launch {
                            manager.connect(wheel, wheel.candidates.first().protocol)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BluetoothStateHint(context: Context) {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    if (adapter != null && !adapter.isEnabled) {
        Text(
            "Bluetooth está desligado — ligue para buscar a roda.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ConnectionStatus(state: WheelUiState) {
    when (val connection = state.connection) {
        is ConnectionState.Connecting -> Text("Conectando...")
        is ConnectionState.Failed -> Text(
            "Falha: ${connection.message}",
            color = MaterialTheme.colorScheme.error,
        )
        else -> Unit
    }
}

@Composable
private fun WheelCard(wheel: DiscoveredWheel, onConnect: () -> Unit) {
    Card(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                wheel.name ?: "(sem nome)",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                wheel.device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                wheel.candidates.joinToString(" · ") { "${it.protocol.displayName} (${it.confidence})" },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Sinal: ${wheel.rssi} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
