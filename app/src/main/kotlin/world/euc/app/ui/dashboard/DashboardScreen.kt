package world.euc.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import world.euc.app.ui.components.ActionButton
import world.euc.app.ui.components.ChartSample
import world.euc.app.ui.components.ChoiceFlowRow
import world.euc.app.ui.components.EucCard
import world.euc.app.ui.components.LabelledValue
import world.euc.app.ui.components.RingGauge
import world.euc.app.ui.components.SpeedGauge
import world.euc.app.ui.components.TimeSeriesChart
import world.euc.app.ui.components.format
import world.euc.app.ui.designsystem.EucSpacing
import world.euc.app.ui.designsystem.EucTheme
import world.euc.app.wheel.ConnectionState
import world.euc.app.wheel.LiveHistory
import world.euc.app.wheel.LiveSeriesKind
import world.euc.app.wheel.WheelConnectionManager
import world.euc.app.wheel.WheelUiState
import world.euc.protocol.model.WheelTelemetry
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Painel principal: velocímetro em arco, anéis de bateria/carga/temperatura,
 * dados da roda, gráficos ao vivo e ações rápidas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    manager: WheelConnectionManager,
    state: WheelUiState,
) {
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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
                .padding(horizontal = EucSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(EucSpacing.md),
        ) {
            state.activeAlarm?.let { alarm ->
                AlarmBanner(alarmLabel(alarm.type.name), alarm.value)
            }

            SpeedHeroCard(state)
            RingsCard(state.telemetry)
            DistanceCard(state)
            TelemetryCard(state.telemetry)
            LiveChartsCard(state.history)
            QuickActionsCard(
                lightOn = state.local.headlightOn,
                onHorn = { scope.launch { manager.horn() } },
                onToggleLight = { on -> scope.launch { manager.setHeadlight(on) } },
            )
            FreshnessHint(state)
            Spacer(Modifier.height(EucSpacing.sm))
        }
    }
}

/** Aviso de alarme: cartão em gradiente de erro, com o valor que o disparou. */
@Composable
private fun AlarmBanner(label: String, value: Double?) {
    EucCard(
        containerColor = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            "⚠  $label${value?.let { " · ${it.roundToInt()}" } ?: ""}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
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

/**
 * Cartão principal: o velocímetro em arco (gradiente ciano → lima, como no
 * logo), com a marca do limite da roda e os números de apoio.
 */
@Composable
private fun SpeedHeroCard(state: WheelUiState) {
    val telemetry = state.telemetry
    val maxKmh = state.capabilities.maxSpeedLimitKmh.takeIf { it > 0 } ?: 60.0
    val limit = telemetry.speedLimitKmh
    val ridingMode = state.settings.ridingMode

    EucCard(spacing = EucSpacing.md) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Altura ajustada ao arco (240°): o medidor ocupa a largura do
                // cartão sem deixar vão até a linha de limite/modo/margem.
                .aspectRatio(1.32f),
            contentAlignment = Alignment.Center,
        ) {
            SpeedGauge(
                speedKmh = telemetry.speedKmh ?: 0.0,
                maxKmh = maxKmh,
                limitKmh = limit,
                modifier = Modifier.fillMaxSize(),
                caption = telemetry.safetyMarginPercent?.let { "margem ${it.format(0)}%" },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EucSpacing.xl),
        ) {
            LabelledValue(
                label = "Limite (tiltback)",
                value = limit.let { "${it.format(0)} km/h" },
            )
            LabelledValue(
                label = "Modo",
                value = when (ridingMode) {
                    0 -> "Comfort"
                    1 -> "Sport"
                    else -> "—"
                },
            )
            LabelledValue(
                label = "Margem",
                value = telemetry.safetyMarginPercent.let { "${it.format(0)} %" },
            )
        }
    }
}

/** Anéis compactos: bateria, carga do motor (PWM) e temperatura. */
@Composable
private fun RingsCard(telemetry: WheelTelemetry) {
    val accents = EucTheme.accents
    val battery = telemetry.batteryPercent
    val load = telemetry.loadPercent
    val motorTemperature = telemetry.temperatures?.motor ?: telemetry.temperatures?.board

    EucCard(spacing = EucSpacing.lg) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RingGauge(
                fraction = ((battery ?: 0.0) / 100.0).toFloat(),
                valueText = battery.let { "${it.format(0)}" },
                caption = "%",
                label = "Bateria",
                color = levelColor(battery, ok = 30.0, warn = 15.0) ?: MaterialTheme.colorScheme.primary,
            )
            RingGauge(
                fraction = ((load ?: 0.0) / 100.0).toFloat(),
                valueText = load.let { "${it.format(0)}" },
                caption = "%",
                label = "Carga (PWM)",
                color = highIsBadColor(load, warn = 70.0, danger = 90.0) ?: accents.ok,
            )
            RingGauge(
                fraction = ((motorTemperature ?: 0.0) / 90.0).toFloat(),
                valueText = motorTemperature.let { "${it.format(0)}" },
                caption = "°C",
                label = "Temperatura",
                color = highIsBadColor(motorTemperature, warn = 55.0, danger = 70.0) ?: accents.ok,
            )
        }
    }
}

/** Verde quando o valor é alto (bateria), amarelo/vermelho quando baixo. */
private fun levelColor(value: Double?, ok: Double, warn: Double): Color? = when {
    value == null -> null
    value >= ok -> null
    value >= warn -> Color(0xFFFBBF24)
    else -> Color(0xFFFF5A5F)
}

/** Verde quando o valor é baixo (carga/temperatura), amarelo/vermelho quando alto. */
private fun highIsBadColor(value: Double?, warn: Double, danger: Double): Color? = when {
    value == null -> null
    value < warn -> null
    value < danger -> Color(0xFFFBBF24)
    else -> Color(0xFFFF5A5F)
}

/**
 * Distâncias: viagem da sessão (zera a cada conexão) e quilometragem total.
 *
 * O total vem do odômetro da própria roda quando o firmware reporta; senão,
 * do acumulado pelo app (somado a cada desconexão).
 */
@Composable
private fun DistanceCard(state: WheelUiState) {
    val reportedTotal = state.statistics.totalKm
    val total = reportedTotal ?: state.appOdometerKm

    EucCard(spacing = EucSpacing.md) {
        Text("Distância", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EucSpacing.xl),
        ) {
            LabelledValue(
                label = "Viagem (sessão)",
                value = String.format(Locale.getDefault(), "%.2f km", state.sessionTripKm),
            )
            LabelledValue(
                label = if (reportedTotal != null) "Total (roda)" else "Total (app)",
                value = String.format(Locale.getDefault(), "%.2f km", total),
            )
            state.statistics.totalHours?.let { hours ->
                LabelledValue(
                    label = "Tempo ligada",
                    value = String.format(Locale.getDefault(), "%.0f h", hours),
                )
            }
        }
    }
}

/** Dados elétricos e de atitude, em grade compacta. */
@Composable
private fun TelemetryCard(telemetry: WheelTelemetry) {
    EucCard(spacing = EucSpacing.md) {
        Text("Roda", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EucSpacing.xl),
        ) {
            LabelledValue("Tensão", telemetry.voltageVolts.let { "${it.format(1)} V" })
            LabelledValue("Corrente", telemetry.currentAmps.let { "${it.format(1)} A" })
            LabelledValue("Limite de corrente", telemetry.currentLimitAmps.let { "${it.format(0)} A" })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EucSpacing.xl),
        ) {
            LabelledValue("Pitch", telemetry.pitchDegrees.let { "${it.format(1)}°" })
            LabelledValue("Roll", telemetry.rollDegrees.let { "${it.format(1)}°" })
            LabelledValue(
                "Temperatura placa",
                telemetry.temperatures?.board.let { "${it.format(0)} °C" },
            )
        }
    }
}

/**
 * Gráficos ao vivo da telemetria: janela deslizante com os últimos ~2 min,
 * com escolha da série (como no EUC World, mas em cartão próprio).
 */
@Composable
private fun LiveChartsCard(history: LiveHistory) {
    var kind by remember { mutableStateOf(LiveSeriesKind.SPEED) }

    EucCard(spacing = EucSpacing.sm) {
        Text("Gráficos ao vivo", style = MaterialTheme.typography.titleMedium)
        ChoiceFlowRow(
            label = "Série",
            options = LiveSeriesKind.entries.map { it to it.label },
            selected = kind,
            onSelect = { kind = it },
        )
        val series = kind.of(history)
        TimeSeriesChart(
            samples = series.samples.map { ChartSample(it.timeMs, it.value) },
            unit = kind.unit,
            emptyMessage = "Sem dados de ${kind.label.lowercase()} ainda.",
        )
    }
}

@Composable
private fun QuickActionsCard(
    lightOn: Boolean?,
    onHorn: () -> Unit,
    onToggleLight: (Boolean) -> Unit,
) {
    EucCard(spacing = EucSpacing.md) {
        Text("Ações rápidas", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EucSpacing.md),
        ) {
            ActionButton(
                label = "Buzina",
                icon = { Icon(Icons.Default.VolumeUp, contentDescription = null) },
                onClick = onHorn,
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = if (lightOn == true) "Farol: ligado" else "Farol: desligado",
                icon = { Icon(Icons.Default.Lightbulb, contentDescription = null) },
                onClick = { onToggleLight(lightOn != true) },
                active = lightOn == true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FreshnessHint(state: WheelUiState) {
    val ageMillis = System.currentTimeMillis() - state.lastTelemetryAtMillis
    val stale = state.lastTelemetryAtMillis == 0L || ageMillis > STALE_AFTER_MILLIS
    val message = when {
        state.lastTelemetryAtMillis == 0L -> "Aguardando telemetria..."
        ageMillis > STALE_AFTER_MILLIS -> "Sem dados há ${ageMillis / 1000} s — verifique a conexão"
        else -> "Atualizado há ${ageMillis / 1000} s"
    }

    Row(
        modifier = Modifier.padding(start = EucSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EucSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (stale) MaterialTheme.colorScheme.error else EucTheme.accents.ok,
                ),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = if (stale) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private const val STALE_AFTER_MILLIS = 3_000L
