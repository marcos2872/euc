package world.euc.app.ui.tour

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Botões de zoom e recentralizar sobre o mapa. */
@Composable
fun MapControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalIconButton(onClick = onZoomIn, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Aproximar")
        }
        FilledTonalIconButton(onClick = onZoomOut, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Remove, contentDescription = "Afastar")
        }
        FilledTonalIconButton(onClick = onRecenter, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.MyLocation, contentDescription = "Centralizar")
        }
    }
}
