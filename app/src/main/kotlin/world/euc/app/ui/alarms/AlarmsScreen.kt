package world.euc.app.ui.alarms

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import world.euc.app.wheel.WheelConnectionManager
import world.euc.app.wheel.WheelUiState
import world.euc.protocol.alarm.AlarmConfig
import kotlin.math.roundToInt

/**
 * Configuração dos alarmes do app: níveis de velocidade, temperatura,
 * bateria, margem de segurança, tensões e avisos (voz/vibração).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsScreen(
    manager: WheelConnectionManager,
    state: WheelUiState,
    onBack: () -> Unit,
) {
    val config = state.alarmConfig

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alarmes") },
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
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("Avisos") {
                SwitchRow("Alarmes ativos", config.enabled) {
                    manager.updateAlarmConfig(config.copy(enabled = it))
                }
                SwitchRow("Voz (TTS)", config.voiceEnabled) {
                    manager.updateAlarmConfig(config.copy(voiceEnabled = it))
                }
                SwitchRow("Vibração", config.vibrationEnabled) {
                    manager.updateAlarmConfig(config.copy(vibrationEnabled = it))
                }
            }

            SectionCard("Velocidade") {
                SliderRow(
                    label = "Nível 1 (baixa prioridade)",
                    value = config.speedAlarm1Kmh.toFloat(),
                    range = 5f..60f,
                    unit = " km/h",
                    onCommit = { manager.updateAlarmConfig(config.copy(speedAlarm1Kmh = it.toDouble())) },
                )
                SliderRow(
                    label = "Nível 2",
                    value = config.speedAlarm2Kmh.toFloat(),
                    range = 5f..70f,
                    unit = " km/h",
                    onCommit = { manager.updateAlarmConfig(config.copy(speedAlarm2Kmh = it.toDouble())) },
                )
                SliderRow(
                    label = "Nível 3 (alta prioridade)",
                    value = config.speedAlarm3Kmh.toFloat(),
                    range = 5f..80f,
                    unit = " km/h",
                    onCommit = { manager.updateAlarmConfig(config.copy(speedAlarm3Kmh = it.toDouble())) },
                )
                SliderRow(
                    label = "Margem do aviso antecipado",
                    value = config.preAlarmMarginKmh.toFloat(),
                    range = 0f..10f,
                    unit = " km/h",
                    onCommit = { manager.updateAlarmConfig(config.copy(preAlarmMarginKmh = it.toDouble())) },
                )
                SwitchRow("Alarme do limite da roda (tiltback)", config.speedLimitAlarmEnabled) {
                    manager.updateAlarmConfig(config.copy(speedLimitAlarmEnabled = it))
                }
                SliderRow(
                    label = "Margem do limite da roda",
                    value = config.speedLimitMarginKmh.toFloat(),
                    range = 0f..10f,
                    unit = " km/h",
                    onCommit = { manager.updateAlarmConfig(config.copy(speedLimitMarginKmh = it.toDouble())) },
                )
            }

            SectionCard("Bateria e energia") {
                SliderRow(
                    label = "Bateria baixa",
                    value = config.batteryAlarmPercent.toFloat(),
                    range = 0f..50f,
                    unit = " %",
                    onCommit = { manager.updateAlarmConfig(config.copy(batteryAlarmPercent = it.toDouble())) },
                )
                SliderRow(
                    label = "Margem de segurança",
                    value = config.safetyMarginAlarmPercent.toFloat(),
                    range = 0f..50f,
                    unit = " %",
                    onCommit = { manager.updateAlarmConfig(config.copy(safetyMarginAlarmPercent = it.toDouble())) },
                )
                SliderRow(
                    label = "Corrente de pico (0 = desligado)",
                    value = config.currentAlarmAmps.toFloat(),
                    range = 0f..120f,
                    unit = " A",
                    onCommit = { manager.updateAlarmConfig(config.copy(currentAlarmAmps = it.toDouble())) },
                )
            }

            SectionCard("Temperatura e tensão") {
                SliderRow(
                    label = "Temperatura máxima",
                    value = config.temperatureAlarmCelsius.toFloat(),
                    range = 30f..100f,
                    unit = " °C",
                    onCommit = { manager.updateAlarmConfig(config.copy(temperatureAlarmCelsius = it.toDouble())) },
                )
                SliderRow(
                    label = "Sobretensão (0 = desligado)",
                    value = config.overvoltageVolts.toFloat(),
                    range = 0f..130f,
                    unit = " V",
                    onCommit = { manager.updateAlarmConfig(config.copy(overvoltageVolts = it.toDouble())) },
                )
                SliderRow(
                    label = "Subtensão (0 = desligado)",
                    value = config.undervoltageVolts.toFloat(),
                    range = 0f..120f,
                    unit = " V",
                    onCommit = { manager.updateAlarmConfig(config.copy(undervoltageVolts = it.toDouble())) },
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onCommit: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                "${local.roundToInt()}$unit",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = range,
        )
    }
}
