package world.euc.app.ui.info

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import world.euc.app.BuildConfig
import world.euc.app.debug.ProtocolProbes
import world.euc.app.ui.components.EucCard
import world.euc.app.ui.components.LabelledValue
import world.euc.app.ui.designsystem.EucSpacing
import world.euc.app.wheel.WheelConnectionManager
import world.euc.app.wheel.WheelUiState
import world.euc.protocol.model.WheelBms
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tela de informações: identidade da roda, odômetros, BMS (tensões de célula
 * por pack) e a sonda de protocolo de debug.
 *
 * Fica separada dos Ajustes de propósito: é leitura (diagnóstico) e não
 * configuração — ver `docs/09-pendencias-inmotion.md` §3.1 e §4.1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(
    manager: WheelConnectionManager,
    state: WheelUiState,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Informações") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                        )
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
            if (state.model == null) {
                EucCard(spacing = EucSpacing.sm) {
                    Text("Sem roda conectada", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Conecte na roda para ver identidade, odômetros e as tensões de célula do BMS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.model?.let { WheelIdentityCard(state) }
            state.bms.takeIf { !it.isEmpty }?.let { BmsCard(it) }
            state.statistics.takeIf { !it.isEmpty || state.sessionTripKm > 0 }?.let { OdometersCard(state) }
            state.model?.let { TelemetrySnapshotCard(state) }

            if (BuildConfig.DEBUG) {
                EucCard(spacing = EucSpacing.md) {
                    Text("Sonda de protocolo (debug)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Envia quadros candidatos para descobrir os bytes que faltam no protocolo. " +
                            "Pedidos e respostas ficam no logcat (tag EucSession).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ProtocolProbes.ALL.forEach { probe ->
                        Column(verticalArrangement = Arrangement.spacedBy(EucSpacing.xs)) {
                            OutlinedButton(
                                onClick = { scope.launch { manager.probe(probe) } },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(probe.label)
                            }
                            Text(
                                probe.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(EucSpacing.sm))
        }
    }
}

/** Identidade da roda: modelo, serial, firmware e autenticação. */
@Composable
private fun WheelIdentityCard(state: WheelUiState) {
    EucCard(spacing = EucSpacing.md) {
        Text("Roda", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EucSpacing.xl),
        ) {
            LabelledValue("Modelo", state.model?.displayName ?: "—")
            state.model?.let { LabelledValue("Variante", it.variant.toString()) }
            LabelledValue("Bateria", state.model?.let { "${it.batterySeries}S${it.batteryParallel}P" } ?: "—")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EucSpacing.xl),
        ) {
            LabelledValue("Serial", state.serialNumber ?: "—")
            LabelledValue("Firmware", state.firmware?.main ?: "—")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EucSpacing.xl),
        ) {
            LabelledValue(
                "Limite de velocidade",
                "%.0f km/h".format(state.capabilities.maxSpeedLimitKmh),
            )
            LabelledValue(
                "Corrente de pico",
                "%.0f A".format(state.capabilities.maxCurrentAmps),
            )
        }
        LabelledValue(
            "Autenticação",
            when (state.authenticated) {
                true -> "confirmada pela roda"
                false -> "não confirmada"
                null -> "—"
            },
        )
    }
}

/** Odômetros da roda e da sessão. */
@Composable
private fun OdometersCard(state: WheelUiState) {
    EucCard(spacing = EucSpacing.md) {
        Text("Odômetros", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EucSpacing.xl),
        ) {
            LabelledValue("Viagem (sessão)", "%.2f km".format(state.sessionTripKm))
            state.statistics.tripKm?.let { LabelledValue("Viagem (roda)", "%.2f km".format(it)) }
            LabelledValue(
                if (state.statistics.totalKm != null) "Total (roda)" else "Total (app)",
                "%.2f km".format(state.statistics.totalKm ?: state.appOdometerKm),
            )
        }
        state.statistics.totalHours?.let { LabelledValue("Tempo ligada", "%.0f h".format(it)) }
    }
}

/** Retrato da telemetria no momento (tensão, corrente, limites e temperaturas). */
@Composable
private fun TelemetrySnapshotCard(state: WheelUiState) {
    val telemetry = state.telemetry

    EucCard(spacing = EucSpacing.md) {
        Text("Telemetria", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EucSpacing.xl),
        ) {
            LabelledValue("Tensão", telemetry.voltageVolts?.let { "%.1f V".format(it) } ?: "—")
            LabelledValue("Corrente", telemetry.currentAmps?.let { "%.1f A".format(it) } ?: "—")
            LabelledValue("Carga (PWM)", telemetry.loadPercent?.let { "%.0f %%".format(it) } ?: "—")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EucSpacing.xl),
        ) {
            LabelledValue("Limite de velocidade", telemetry.speedLimitKmh?.let { "%.0f km/h".format(it) } ?: "—")
            LabelledValue("Limite de corrente", telemetry.currentLimitAmps?.let { "%.0f A".format(it) } ?: "—")
        }
        LabelledValue(
            "Temperaturas (MOS / motor / bateria)",
            telemetry.temperatures?.let {
                listOfNotNull(it.mosfet, it.motor, it.battery).takeIf { temps -> temps.isNotEmpty() }
                    ?.joinToString(" / ") { temp -> "%.0f".format(temp) }
                    ?.plus(" °C")
            } ?: "—",
        )
    }
}

/**
 * BMS em detalhe: um bloco por pack com carga, tensão, desequilíbrio, as
 * **tensões de célula em mV** e as temperaturas.
 */
@Composable
private fun BmsCard(bms: WheelBms) {
    EucCard(spacing = EucSpacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bateria (BMS)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            bms.worstCellDeltaMv?.let { delta ->
                Text(
                    "Δ $delta mV",
                    style = MaterialTheme.typography.labelLarge,
                    color = cellColor(delta),
                )
            }
        }
        bms.lowestCellMv?.let {
            Text(
                "Menor célula: $it mV",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        bms.packs.forEach { pack ->
            Column(verticalArrangement = Arrangement.spacedBy(EucSpacing.xs)) {
                Text(
                    "Pack ${pack.index}" + (pack.boardId?.let { " (board $it)" } ?: ""),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EucSpacing.xl),
                ) {
                    LabelledValue("Carga", pack.socPercent?.let { "${it.roundToInt()} %" } ?: "—")
                    LabelledValue("Tensão", pack.voltageVolts?.let { "%.2f V".format(it) } ?: "—")
                    LabelledValue("Δ células", pack.cellDeltaMv?.let { "$it mV" } ?: "—")
                }
                if (pack.cellsMv.isNotEmpty()) {
                    val average = pack.cellsMv.average()
                    Text(
                        "Células (mV)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    pack.cellsMv.chunked(CELLS_PER_ROW).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(EucSpacing.sm),
                        ) {
                            row.forEach { millivolts ->
                                Text(
                                    millivolts.toString(),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cellColor(abs(millivolts - average).roundToInt()),
                                )
                            }
                        }
                    }
                }
                if (pack.temperaturesCelsius.isNotEmpty()) {
                    Text(
                        "Temperaturas ${pack.temperaturesCelsius.min().roundToInt()}–" +
                            "${pack.temperaturesCelsius.max().roundToInt()} °C",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Desequilíbrio: até 10 mV normal, até 30 mV atenção, acima disso crítico. */
@Composable
private fun cellColor(deltaMv: Int): Color = when {
    deltaMv <= 10 -> MaterialTheme.colorScheme.primary
    deltaMv <= 30 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

private const val CELLS_PER_ROW = 5
