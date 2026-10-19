package world.euc.app.ui.connect

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import world.euc.app.ui.components.EucCard
import world.euc.app.ui.designsystem.EucSpacing
import world.euc.app.ui.designsystem.EucTheme
import world.euc.app.ui.designsystem.PillShape
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
 * Tela de conexão: título grande, radar animado durante a busca e cartões das
 * rodas encontradas (com sinal e protocolos detectados).
 */
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

    fun toggleScan() {
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
    }


    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(
            start = EucSpacing.screen,
            end = EucSpacing.screen,
            top = EucSpacing.md,
            bottom = EucSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(EucSpacing.md),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(EucSpacing.xs)) {
                Text("Conectar", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Aproxime-se da roda e faça a busca por perto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { BluetoothStateHint(context) }

        if (!hasPermissions) {
            item {
                EucCard(spacing = EucSpacing.sm) {
                    Text("Permissões", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Para buscar e conectar, o app precisa das permissões de Bluetooth e localização.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { permissionLauncher.launch(requiredBlePermissions()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Conceder permissões")
                    }
                }
            }
        }

        item {
            ScanHeroCard(
                isScanning = isScanning,
                hasPermissions = hasPermissions,
                onToggle = { toggleScan() },
            )
        }

        connectionStatus(state)

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

@Composable
private fun BluetoothStateHint(context: Context) {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    if (adapter != null && !adapter.isEnabled) {
        EucCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
            Text(
                "Bluetooth está desligado — ligue para buscar a roda.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/**
 * Cartão principal da busca: radar animado (círculos que se expandem) enquanto
 * procura, e o botão de iniciar/parar.
 */
@Composable
private fun ScanHeroCard(
    isScanning: Boolean,
    hasPermissions: Boolean,
    onToggle: () -> Unit,
) {
    val accents = EucTheme.accents

    EucCard(spacing = EucSpacing.lg) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isScanning) {
                RadarAnimation()
            }
            Icon(
                imageVector = if (isScanning) Icons.Default.BluetoothSearching else Icons.Default.Bluetooth,
                contentDescription = null,
                tint = if (isScanning) accents.gaugeGradient.first() else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp),
            )
        }

        Text(
            text = if (isScanning) "Procurando rodas por perto..." else "Pronto para buscar",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
            color = if (isScanning) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = onToggle,
            enabled = hasPermissions,
            shape = PillShape,
            colors = if (isScanning) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(if (isScanning) "Parar busca" else "Buscar rodas")
        }
    }
}

/** Três círculos que se expandem continuamente, no gradiente da marca. */
@Composable
private fun RadarAnimation() {
    val accents = EucTheme.accents
    val transition = rememberInfiniteTransition(label = "radar")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "radar-progresso",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val maxRadius = size.minDimension / 2
        val ringColor = accents.gaugeGradient.first()
        repeat(3) { index ->
            val phase = (progress + index / 3f) % 1f
            val radius = maxRadius * (0.25f + 0.75f * phase)
            drawCircle(
                color = ringColor.copy(alpha = (1f - phase) * 0.5f),
                radius = radius,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

/** Estado da conexão, quando há algo a dizer (conectando ou falhou). */
private fun LazyListScope.connectionStatus(state: WheelUiState) {
    when (val connection = state.connection) {
        is ConnectionState.Connecting -> item {
            EucCard(spacing = EucSpacing.xs) {
                Text("Conectando...", style = MaterialTheme.typography.titleMedium)
            }
        }

        is ConnectionState.Failed -> item {
            EucCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    "Falha: ${connection.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        else -> Unit
    }
}

@Composable
private fun WheelCard(wheel: DiscoveredWheel, onConnect: () -> Unit) {
    EucCard(spacing = EucSpacing.sm) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EucSpacing.md),
        ) {
            SignalBars(rssi = wheel.rssi)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    wheel.name ?: "(sem nome)",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${wheel.device.address} · ${wheel.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(EucSpacing.sm)) {
            wheel.candidates.take(3).forEach { candidate ->
                FilterChip(
                    selected = false,
                    onClick = { },
                    enabled = false,
                    label = { Text("${candidate.protocol.displayName} ${candidate.confidence}") },
                )
            }
        }

        Button(
            onClick = onConnect,
            shape = PillShape,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Conectar")
        }
    }
}

/** Quatro barras de sinal, acesas conforme a potência recebida. */
@Composable
private fun SignalBars(rssi: Int) {
    val active = when {
        rssi >= -60 -> 4
        rssi >= -70 -> 3
        rssi >= -80 -> 2
        else -> 1
    }
    val accent = EucTheme.accents
    val color = when (active) {
        4, 3 -> accent.ok
        2 -> accent.warn
        else -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = EucSpacing.md, vertical = EucSpacing.md),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((8 + index * 5).dp)
                    .clip(CircleShape)
                    .background(
                        if (index < active) color else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
        Spacer(Modifier.size(EucSpacing.xs))
    }
}
