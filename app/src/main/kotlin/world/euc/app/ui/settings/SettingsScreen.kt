package world.euc.app.ui.settings

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import world.euc.app.wheel.WheelConnectionManager
import world.euc.app.wheel.WheelUiState
import kotlin.math.roundToInt

/**
 * Configurações da roda (layout da V11y).
 *
 * Valores reportados pela roda vêm de [WheelUiState.settings]; os que o
 * firmware não devolve ficam em [WheelUiState.local] com atualização otimista.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    manager: WheelConnectionManager,
    state: WheelUiState,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settings = state.settings
    val local = state.local
    var calibrationTarget by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações da roda") },
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
            SectionCard("Condução") {
                SliderRow(
                    label = "Limite de velocidade",
                    value = (settings.speedLimitKmh ?: DEFAULT_SPEED_LIMIT).toFloat(),
                    valueRange = 5f..50f,
                    unit = " km/h",
                    onCommit = { scope.launch { manager.setSpeedLimit(it.toDouble()) } },
                )
                SliderRow(
                    label = "Tilt dos pedais",
                    value = (settings.pedalTiltDegrees ?: 0.0).toFloat(),
                    valueRange = -10f..10f,
                    unit = "°",
                    decimals = 1,
                    onCommit = { scope.launch { manager.setPedalTilt(it.toDouble()) } },
                )
                SliderRow(
                    label = "Sensibilidade dos pedais",
                    value = (settings.pedalSensitivityPercent ?: 100).toFloat(),
                    valueRange = 0f..100f,
                    unit = " %",
                    onCommit = { scope.launch { manager.setPedalSensitivity(it.roundToInt()) } },
                )
                SwitchRow("Modo performance", local.performanceMode) {
                    scope.launch { manager.setPerformanceMode(it) }
                }
                SwitchRow("Rodar com bateria baixa", local.lowBatteryRiding) {
                    scope.launch { manager.setLowBatteryRiding(it) }
                }
                SwitchRow("Detecção de motor sem carga", local.motorNoLoadDetection) {
                    scope.launch { manager.setMotorNoLoadDetection(it) }
                }
                SwitchRow(
                    label = "Modo transporte",
                    checked = local.transportationMode,
                    description = "Impede o motor de engatar se a roda for ligada sem querer durante o transporte.",
                ) {
                    scope.launch { manager.setTransportationMode(it) }
                }
                SwitchRow(
                    label = "Botão da alça",
                    checked = local.handleButton,
                    description = "Sensor da alça: ao levantar a roda pelo botão, o motor desliga.",
                ) {
                    scope.launch { manager.setHandleButton(it) }
                }
            }

            SectionCard("Modos separados (aceleração/frenagem)") {
                SwitchRow("Ativar", settings.splitRidingModesEnabled ?: false) {
                    scope.launch { manager.setSplitRidingModes(it) }
                }
                SliderRow(
                    label = "Aceleração",
                    value = (settings.splitRidingAccelerationPercent ?: 50).toFloat(),
                    valueRange = 0f..100f,
                    unit = " %",
                    onCommit = {
                        scope.launch {
                            manager.setSplitRidingSettings(
                                acceleration = it.roundToInt(),
                                braking = settings.splitRidingBrakingPercent ?: 50,
                            )
                        }
                    },
                )
                SliderRow(
                    label = "Frenagem",
                    value = (settings.splitRidingBrakingPercent ?: 50).toFloat(),
                    valueRange = 0f..100f,
                    unit = " %",
                    onCommit = {
                        scope.launch {
                            manager.setSplitRidingSettings(
                                acceleration = settings.splitRidingAccelerationPercent ?: 50,
                                braking = it.roundToInt(),
                            )
                        }
                    },
                )
            }

            SectionCard("Iluminação") {
                SwitchRow("Luzes diurnas (DRL)", local.daytimeRunningLightsOn) {
                    scope.launch { manager.setDaytimeRunningLights(it) }
                }
                SwitchRow("Farol", local.headlightOn) {
                    scope.launch { manager.setHeadlight(it) }
                }
                SliderRow(
                    label = "Brilho do farol",
                    value = local.headlightBrightness.toFloat(),
                    valueRange = 0f..100f,
                    unit = " %",
                    onCommit = { scope.launch { manager.setHeadlightBrightness(it.roundToInt()) } },
                )
            }

            SectionCard("Som e ventilador") {
                SliderRow(
                    label = "Volume do alto-falante",
                    value = (settings.speakerVolumePercent ?: 20).toFloat(),
                    valueRange = 0f..100f,
                    unit = " %",
                    onCommit = { scope.launch { manager.setSpeakerVolume(it.roundToInt()) } },
                )
                SwitchRow("Ventilador", local.fanOn) {
                    scope.launch { manager.setCoolingFan(it) }
                }
                SwitchRow("Ventilador silencioso", local.fanQuietMode) {
                    scope.launch { manager.setCoolingFanQuietMode(it) }
                }
            }

            SectionCard("Segurança") {
                state.authenticated?.let { authenticated ->
                    Text(
                        if (authenticated) {
                            "Sessão autenticada com a roda — comandos sensíveis liberados."
                        } else {
                            "A roda não confirmou a autenticação: bloqueio e comandos sensíveis podem ser ignorados."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (authenticated) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                SwitchRow(
                    label = "Bloqueio da roda",
                    checked = local.locked,
                    description = "Desliga o motor e ativa alarmes de movimento (proteção contra furto). " +
                        "Use apenas com a roda parada e apoiada.",
                ) {
                    scope.launch { manager.setLock(it) }
                }
                SwitchRow(
                    label = "Tilt lateral estendido",
                    checked = settings.extendedLateralTiltLimit ?: false,
                    description = "Aumenta o limite de inclinação lateral em que o motor se desliga. " +
                        "Útil em curvas acentuadas; deixe desligado enquanto aprende.",
                ) {
                    scope.launch { manager.setExtendedLateralTiltLimit(it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { calibrationTarget = "giroscópio" },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Calibrar giroscópio")
                    }
                    OutlinedButton(
                        onClick = { calibrationTarget = "pedais" },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Zerar pedais")
                    }
                }
            }
        }
    }

    calibrationTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { calibrationTarget = null },
            title = { Text("Calibrar $target?") },
            text = {
                Text(
                    "Coloque a roda na posição correta e nivelada antes de continuar. " +
                        "A calibração não deve ser interrompida.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { manager.calibrate(yaw = target == "giroscópio") }
                    calibrationTarget = null
                }) {
                    Text("Calibrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { calibrationTarget = null }) { Text("Cancelar") }
            },
        )
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
private fun SwitchRow(
    label: String,
    checked: Boolean,
    description: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String,
    decimals: Int = 0,
    onCommit: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                formatValue(local, decimals) + unit,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = valueRange,
        )
    }
}

private fun formatValue(value: Float, decimals: Int): String =
    if (decimals == 0) value.roundToInt().toString() else String.format("%.${decimals}f", value)

private const val DEFAULT_SPEED_LIMIT = 25.0
