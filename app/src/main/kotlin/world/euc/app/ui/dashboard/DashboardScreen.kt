package world.euc.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import world.euc.app.wheel.ConnectionState
import world.euc.app.wheel.WheelConnectionManager
import world.euc.app.ui.components.LabelledValue
import world.euc.app.ui.components.format
import world.euc.app.wheel.WheelUiState
import world.euc.protocol.model.WheelTelemetry
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Painel principal: telemetria ao vivo da roda e ações rápidas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    manager: WheelConnectionManager,
    state: WheelUiState,
    onOpenSettings: () -> Unit,
    onOpenAlarms: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.model?.displayName ?: "Roda conectada")
                        val subtitle = listOfNotNull(
                            state.firmware?.main?.let { "fw $it" },
                            state.serialNumber,
                            (state.connection as? ConnectionState.Connected)?.deviceName,
                        ).joinToString(" · ")
                        if (subtitle.isNotEmpty()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAlarms) {
                        Icon(Icons.Default.Notifications, contentDescription = "Alarmes")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações da roda")
                    }
                    IconButton(onClick = { manager.disconnect() }) {
                        Icon(Icons.Default.LinkOff, contentDescription = "Desconectar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.activeAlarm?.let { alarm ->
                AlarmBanner(alarm.type.name, alarm.value)
            }
            SpeedCard(state.telemetry)
            BatteryCard(state.telemetry)
            StatsCard(state.telemetry)
            QuickActionsCard(
                lightOn = state.local.headlightOn,
                onHorn = { scope.launch { manager.horn() } },
                onToggleLight = { on -> scope.launch { manager.setHeadlight(on) } },
            )
            FreshnessHint(state)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AlarmBanner(typeName: String, value: Double?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            "⚠ ${alarmLabel(typeName)}${value?.let { " (${it.roundToInt()})" } ?: ""}",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun alarmLabel(typeName: String): String = when (typeName) {
    "SPEED_1", "SPEED_2", "SPEED_3" -> "Alarme de velocidade"
    "SPEED_PRE_1", "SPEED_PRE_2" -> "Aproximando do alarme"
    "SPEED_LIMIT" -> "Limite da roda"
    "CURRENT" -> "Corrente alta"
    "TEMPERATURE" -> "Temperatura alta"
    "OVERVOLTAGE" -> "Tensão alta"
    "UNDERVOLTAGE" -> "Tensão baixa"
    "BATTERY" -> "Bateria baixa"
    "SAFETY_MARGIN" -> "Margem de segurança baixa"
    "CONNECTION_LOST" -> "Conexão perdida"
    else -> typeName
}

@Composable
private fun SpeedCard(telemetry: WheelTelemetry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Velocidade", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                Text(
                    telemetry.speedKmh.format(1),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(" km/h", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LabelledValue("Limite", telemetry.speedLimitKmh.let { "${it.format(0)} km/h" })
                LabelledValue("Margem", telemetry.safetyMarginPercent.let { "${it.format(0)} %" })
            }
        }
    }
}

@Composable
private fun BatteryCard(telemetry: WheelTelemetry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Bateria", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                telemetry.batteryPercent.let { "${it.format(0)} %" },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { ((telemetry.batteryPercent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatsCard(telemetry: WheelTelemetry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LabelledValue("Tensão", telemetry.voltageVolts.let { "${it.format(1)} V" })
                LabelledValue("Corrente", telemetry.currentAmps.let { "${it.format(1)} A" })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LabelledValue("Carga (PWM)", telemetry.loadPercent.let { "${it.format(0)} %" })
                LabelledValue("Limite de corrente", telemetry.currentLimitAmps.let { "${it.format(0)} A" })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LabelledValue("Pitch", telemetry.pitchDegrees.let { "${it.format(1)}°" })
                LabelledValue("Roll", telemetry.rollDegrees.let { "${it.format(1)}°" })
            }
            telemetry.temperatures?.let { temperatures ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    LabelledValue("Motor", temperatures.motor.let { "${it.format(0)}°C" })
                    LabelledValue("Placa", temperatures.board.let { "${it.format(0)}°C" })
                    LabelledValue("Bateria", temperatures.battery.let { "${it.format(0)}°C" })
                }
            }
        }
    }
}

@Composable
private fun QuickActionsCard(
    lightOn: Boolean?,
    onHorn: () -> Unit,
    onToggleLight: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onHorn, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.VolumeUp, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("Buzina")
            }
            Button(onClick = { onToggleLight(lightOn != true) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Lightbulb, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(if (lightOn == true) "Farol: on" else "Farol: off")
            }
        }
    }
}

@Composable
private fun FreshnessHint(state: WheelUiState) {
    val ageMillis = System.currentTimeMillis() - state.lastTelemetryAtMillis
    val message = when {
        state.lastTelemetryAtMillis == 0L -> "Aguardando telemetria..."
        ageMillis > STALE_AFTER_MILLIS -> "Sem dados há ${ageMillis / 1000} s — verifique a conexão"
        else -> "Atualizado há ${ageMillis / 1000} s"
    }
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = if (state.lastTelemetryAtMillis == 0L || ageMillis > STALE_AFTER_MILLIS) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

private const val STALE_AFTER_MILLIS = 3_000L
