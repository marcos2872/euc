package world.euc.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import world.euc.app.ui.designsystem.EucSpacing
import world.euc.protocol.model.BmsPack
import world.euc.protocol.model.WheelBms
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Cartão do BMS: um bloco por pack com carga, tensão, desequilíbrio das células
 * e temperaturas, mais uma barra por célula (altura = tensão dentro do pack,
 * cor = desvio em relação à média).
 *
 * Só aparece quando a roda respondeu às rotas de BMS (`02 24`…`02 27`) — a V11y
 * responde com 2 packs de 20 células.
 */
@Composable
fun BmsCard(bms: WheelBms, modifier: Modifier = Modifier) {
    if (bms.isEmpty) return

    EucCard(modifier = modifier, spacing = EucSpacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bateria (BMS)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            bms.worstCellDeltaMv?.let { delta ->
                Text(
                    "Δ $delta mV",
                    style = MaterialTheme.typography.labelLarge,
                    color = deltaColor(delta),
                )
            }
        }
        bms.packs.forEach { pack -> PackBlock(pack) }
    }
}

@Composable
private fun PackBlock(pack: BmsPack) {
    Column(verticalArrangement = Arrangement.spacedBy(EucSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pack ${pack.index}", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text(
                pack.summary(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (pack.cellsMv.isNotEmpty()) {
            CellsBar(pack.cellsMv)
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

/** Uma barra por célula: altura pela tensão, cor pelo desvio da média do pack. */
@Composable
private fun CellsBar(cellsMv: List<Int>) {
    val lowest = cellsMv.min()
    val span = (cellsMv.max() - lowest).coerceAtLeast(1)
    val average = cellsMv.average()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CELL_BAR_MAX_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        cellsMv.forEach { millivolts ->
            val fraction = (millivolts - lowest).toFloat() / span
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(
                        CELL_BAR_MIN_HEIGHT + (CELL_BAR_MAX_HEIGHT - CELL_BAR_MIN_HEIGHT) * fraction,
                    )
                    .clip(RoundedCornerShape(2.dp))
                    .background(deltaColor(abs(millivolts - average).roundToInt())),
            )
        }
    }
}

private fun BmsPack.summary(): String = buildString {
    socPercent?.let { append("${it.roundToInt()}%") }
    voltageVolts?.let {
        if (isNotEmpty()) append(" · ")
        append("%.2f V".format(it))
    }
    cellDeltaMv?.let {
        if (isNotEmpty()) append(" · ")
        append("Δ $it mV")
    }
}

/** Desequilíbrio: até 10 mV normal, até 30 mV atenção, acima disso crítico. */
@Composable
private fun deltaColor(deltaMv: Int): Color = when {
    deltaMv <= 10 -> MaterialTheme.colorScheme.primary
    deltaMv <= 30 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

private val CELL_BAR_MIN_HEIGHT = 12.dp
private val CELL_BAR_MAX_HEIGHT = 34.dp
