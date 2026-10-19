package world.euc.app.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
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
            state.statistics
                .takeIf { it.totalKm != null || it.totalHours != null || state.appOdometerKm > 0 }
                ?.let { OdometersCard(state) }

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

/** Odômetro total da roda (ou do app) e tempo ligada. */
@Composable
private fun OdometersCard(state: WheelUiState) {
    EucCard(spacing = EucSpacing.md) {
        Text("Odômetro", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EucSpacing.xl),
        ) {
            LabelledValue(
                if (state.statistics.totalKm != null) "Total (roda)" else "Total (app)",
                "%.2f km".format(state.statistics.totalKm ?: state.appOdometerKm),
            )
            state.statistics.totalHours?.let { LabelledValue("Tempo ligada", "%.0f h".format(it)) }
        }
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
                    val lowest = pack.cellsMv.min()
                    val highest = pack.cellsMv.max()
                    Text(
                        "Células (mV)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    pack.cellsMv.chunked(CELLS_PER_ROW).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(EucSpacing.xs),
                        ) {
                            row.forEach { millivolts ->
                                CellChip(
                                    millivolts = millivolts,
                                    lowest = lowest,
                                    highest = highest,
                                    deviationMv = abs(millivolts - average).roundToInt(),
                                    modifier = Modifier.weight(1f),
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

/**
 * Mini card de uma célula: o valor em mV e um filete de carga proporcional à
 * tensão dentro do pack — um "desenho" das células, com a cor pelo desequilíbrio.
 */
@Composable
private fun CellChip(
    millivolts: Int,
    lowest: Int,
    highest: Int,
    deviationMv: Int,
    modifier: Modifier = Modifier,
) {
    val span = (highest - lowest).coerceAtLeast(1)
    val level = (millivolts - lowest).toFloat() / span
    val accent = cellColor(deviationMv)

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(horizontal = EucSpacing.xs, vertical = EucSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EucSpacing.xs),
    ) {
        Text(
            text = millivolts.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = accent,
            maxLines = 1,
            softWrap = false,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.25f + 0.75f * level)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(accent),
            )
        }
    }
}

private const val CELLS_PER_ROW = 5
