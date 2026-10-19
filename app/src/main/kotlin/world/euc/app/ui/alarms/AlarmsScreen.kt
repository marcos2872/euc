package world.euc.app.ui.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import world.euc.app.ui.components.SectionCard
import world.euc.app.ui.components.SliderRow
import world.euc.app.ui.components.SwitchRow
import world.euc.app.ui.components.format
import world.euc.app.ui.components.formatValue
import world.euc.app.ui.designsystem.EucSpacing
import world.euc.app.wheel.WheelConnectionManager
import world.euc.app.wheel.WheelUiState
import world.euc.protocol.alarm.AlarmConfig
import world.euc.protocol.alarm.AlarmLimits
import world.euc.protocol.alarm.AlarmType

/**
 * Configuração dos alarmes do app: cada nível tem seu próprio liga/desliga,
 * além do valor que o dispara. Com um nível desligado, o maior nível *ligado*
 * atingido é quem dispara (desligar o nível 2 não silencia o nível 1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsScreen(
    manager: WheelConnectionManager,
    state: WheelUiState,
    onBack: () -> Unit,
) {
    val config = state.alarmConfig
    val limits = alarmLimits(state)

    fun update(updated: AlarmConfig) = manager.updateAlarmConfig(updated)
    fun setEnabled(type: AlarmType, enabled: Boolean) =
        update(config.withTypeEnabled(type, enabled))

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
                .padding(horizontal = EucSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(EucSpacing.md),
        ) {
            SectionCard("Avisos") {
                SwitchRow(
                    label = "Alarmes ativos",
                    checked = config.enabled,
                    description = "Interruptor geral: desligado, nenhum alarme avisa.",
                ) {
                    update(config.copy(enabled = it))
                }
                SwitchRow(
                    label = "Voz (TTS)",
                    checked = config.voiceEnabled,
                    description = "Fala o aviso em voz alta pelo telefone.",
                ) {
                    update(config.copy(voiceEnabled = it))
                }
                SwitchRow(
                    label = "Vibração",
                    checked = config.vibrationEnabled,
                    description = "Vibra o telefone junto com o aviso.",
                ) {
                    update(config.copy(vibrationEnabled = it))
                }
                SwitchRow(
                    label = "Avisar se perder a conexão",
                    checked = config.isEnabled(AlarmType.CONNECTION_LOST),
                    description = "Avisa quando a telemetria para de chegar (roda desligada ou fora de alcance).",
                ) { enabled -> setEnabled(AlarmType.CONNECTION_LOST, enabled) }
                SliderRow(
                    label = "Tempo sem dados",
                    description = "Quanto tempo sem leitura dispara o aviso de conexão perdida.",
                    value = (config.connectionLostAfterMillis / 1000f),
                    valueRange = 3f..30f,
                    unit = " s",
                    onCommit = { update(config.copy(connectionLostAfterMillis = (it * 1000).toLong())) },
                )
            }

            SectionCard("Velocidade") {
                LimitsHint("Teto do modelo: ${limits.maxSpeedKmh.format(0)} km/h")
                AlarmLevel(
                    label = "Nível 1 (baixa prioridade)",
                    description = "Primeiro aviso — para você notar que está acelerando.",
                    enabled = config.isEnabled(AlarmType.SPEED_1),
                    onEnabledChange = { setEnabled(AlarmType.SPEED_1, it) },
                    value = config.speedAlarm1Kmh.toFloat(),
                    valueRange = 5f..limits.maxSpeedKmh.toFloat(),
                    unit = " km/h",
                    onCommit = { update(config.copy(speedAlarm1Kmh = it.toDouble())) },
                )
                AlarmLevel(
                    label = "Nível 2",
                    description = "Aviso intermediário, com mais urgência.",
                    enabled = config.isEnabled(AlarmType.SPEED_2),
                    onEnabledChange = { setEnabled(AlarmType.SPEED_2, it) },
                    value = config.speedAlarm2Kmh.toFloat(),
                    valueRange = 5f..limits.maxSpeedKmh.toFloat(),
                    unit = " km/h",
                    onCommit = { update(config.copy(speedAlarm2Kmh = it.toDouble())) },
                )
                AlarmLevel(
                    label = "Nível 3 (alta prioridade)",
                    description = "Aviso forte — perto do limite da roda.",
                    enabled = config.isEnabled(AlarmType.SPEED_3),
                    onEnabledChange = { setEnabled(AlarmType.SPEED_3, it) },
                    value = config.speedAlarm3Kmh.toFloat(),
                    valueRange = 5f..limits.maxSpeedKmh.toFloat(),
                    unit = " km/h",
                    onCommit = { update(config.copy(speedAlarm3Kmh = it.toDouble())) },
                )
                AlarmLevel(
                    label = "Aviso antecipado",
                    description = "Avisa alguns km/h antes do nível, para não ser pego de surpresa.",
                    enabled = config.isEnabled(AlarmType.SPEED_PRE_1),
                    onEnabledChange = { enabled ->
                        update(
                            config
                                .withTypeEnabled(AlarmType.SPEED_PRE_1, enabled)
                                .withTypeEnabled(AlarmType.SPEED_PRE_2, enabled),
                        )
                    },
                    value = config.preAlarmMarginKmh.toFloat(),
                    valueRange = 0f..10f,
                    unit = " km/h",
                    onCommit = { update(config.copy(preAlarmMarginKmh = it.toDouble())) },
                )
                SwitchRow(
                    label = "Alarme do limite da roda (tiltback)",
                    checked = config.speedLimitAlarmEnabled,
                    description = "Avisa quando você chega perto da velocidade em que a roda inclina os pedais.",
                ) {
                    update(config.copy(speedLimitAlarmEnabled = it))
                }
                SliderRow(
                    label = "Margem do limite da roda",
                    description = "Distância (km/h) do tiltback em que o aviso dispara.",
                    value = config.speedLimitMarginKmh.toFloat(),
                    valueRange = 0f..10f,
                    unit = " km/h",
                    onCommit = { update(config.copy(speedLimitMarginKmh = it.toDouble())) },
                )
            }

            SectionCard("Bateria e energia") {
                LimitsHint("Corrente de pico do modelo: ${limits.maxCurrentAmps.format(0)} A")
                AlarmLevel(
                    label = "Bateria baixa",
                    description = "Avisa quando a carga cair abaixo deste valor.",
                    enabled = config.isEnabled(AlarmType.BATTERY),
                    onEnabledChange = { setEnabled(AlarmType.BATTERY, it) },
                    value = config.batteryAlarmPercent.toFloat(),
                    valueRange = 0f..50f,
                    unit = " %",
                    onCommit = { update(config.copy(batteryAlarmPercent = it.toDouble())) },
                )
                AlarmLevel(
                    label = "Margem de segurança",
                    description = "Avisa quando a folga de potência ficar abaixo disso (motor perto do limite).",
                    enabled = config.isEnabled(AlarmType.SAFETY_MARGIN),
                    onEnabledChange = { setEnabled(AlarmType.SAFETY_MARGIN, it) },
                    value = config.safetyMarginAlarmPercent.toFloat(),
                    valueRange = 0f..50f,
                    unit = " %",
                    onCommit = { update(config.copy(safetyMarginAlarmPercent = it.toDouble())) },
                )
                AlarmLevel(
                    label = "Corrente de pico",
                    description = "Avisa em puxadas fortes ou subidas.",
                    enabled = config.isEnabled(AlarmType.CURRENT),
                    onEnabledChange = { enabled ->
                        update(
                            config
                                .withTypeEnabled(AlarmType.CURRENT, enabled)
                                // Ao ligar sem valor definido, assume um limite sensato.
                                .let {
                                    if (enabled && it.currentAlarmAmps <= 0) {
                                        it.copy(currentAlarmAmps = minOf(40.0, limits.maxCurrentAmps))
                                    } else {
                                        it
                                    }
                                },
                        )
                    },
                    value = config.currentAlarmAmps.toFloat(),
                    valueRange = 0f..limits.maxCurrentAmps.toFloat(),
                    unit = " A",
                    onCommit = { update(config.copy(currentAlarmAmps = it.toDouble())) },
                )
            }

            SectionCard("Temperatura e tensão") {
                LimitsHint(
                    "Bateria: ${limits.minVoltageVolts.format(0)}–" +
                        "${(limits.maxVoltageVolts - AlarmLimits.OVERVOLTAGE_HEADROOM_VOLTS).format(0)} V · " +
                        "até ${limits.maxTemperatureCelsius.format(0)} °C",
                )
                AlarmLevel(
                    label = "Temperatura máxima",
                    description = "Avisa quando qualquer sensor passar deste valor.",
                    enabled = config.isEnabled(AlarmType.TEMPERATURE),
                    onEnabledChange = { setEnabled(AlarmType.TEMPERATURE, it) },
                    value = config.temperatureAlarmCelsius.toFloat(),
                    valueRange = 30f..limits.maxTemperatureCelsius.toFloat(),
                    unit = " °C",
                    onCommit = { update(config.copy(temperatureAlarmCelsius = it.toDouble())) },
                )
                AlarmLevel(
                    label = "Sobretensão",
                    description = "Avisa se a tensão passar disso (carga ou regen anormal).",
                    enabled = config.isEnabled(AlarmType.OVERVOLTAGE),
                    onEnabledChange = { enabled ->
                        update(
                            config
                                .withTypeEnabled(AlarmType.OVERVOLTAGE, enabled)
                                .let {
                                    if (enabled && it.overvoltageVolts <= 0) {
                                        it.copy(
                                            overvoltageVolts = limits.maxVoltageVolts -
                                                AlarmLimits.OVERVOLTAGE_HEADROOM_VOLTS + 2.0,
                                        )
                                    } else {
                                        it
                                    }
                                },
                        )
                    },
                    value = config.overvoltageVolts.toFloat(),
                    valueRange = 0f..limits.maxVoltageVolts.toFloat(),
                    unit = " V",
                    onCommit = { update(config.copy(overvoltageVolts = it.toDouble())) },
                )
                AlarmLevel(
                    label = "Subtensão",
                    description = "Avisa se a tensão cair disso (bateria afundando).",
                    enabled = config.isEnabled(AlarmType.UNDERVOLTAGE),
                    onEnabledChange = { enabled ->
                        update(
                            config
                                .withTypeEnabled(AlarmType.UNDERVOLTAGE, enabled)
                                .let {
                                    if (enabled && it.undervoltageVolts <= 0) {
                                        it.copy(undervoltageVolts = limits.minVoltageVolts + 6.0)
                                    } else {
                                        it
                                    }
                                },
                        )
                    },
                    value = config.undervoltageVolts.toFloat(),
                    valueRange = 0f..limits.maxVoltageVolts.toFloat(),
                    unit = " V",
                    onCommit = { update(config.copy(undervoltageVolts = it.toDouble())) },
                )
            }
        }
    }
}

/**
 * Envelope da roda conectada: teto de velocidade/corrente/temperatura e faixa de
 * tensão da bateria (pela série de células que a roda reporta). Sem roda
 * conhecida, usa uma faixa ampla para não travar nada.
 */
private fun alarmLimits(state: WheelUiState): AlarmLimits {
    val base = state.model
        ?.let { AlarmLimits.of(state.capabilities, it.batterySeries) }
        ?: AlarmLimits.Unknown
    val reportedCurrent = state.telemetry.currentLimitAmps
    return if (reportedCurrent != null && reportedCurrent < base.maxCurrentAmps) {
        base.copy(maxCurrentAmps = reportedCurrent)
    } else {
        base
    }
}

/** Mostra qual envelope da roda está servindo de teto para os alarmes. */
@Composable
private fun LimitsHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Nível de alarme: interruptor à direita (desligado = não dispara), valor em
 * destaque e slider — que fica inativo enquanto o nível está desligado.
 */
@Composable
private fun AlarmLevel(
    label: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String,
    decimals: Int = 0,
    description: String? = null,
    onCommit: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                formatValue(local, decimals) + unit,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.size(EucSpacing.sm))
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = valueRange,
            enabled = enabled,
        )
    }
}
