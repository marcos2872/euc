package world.euc.app.ui.designsystem

import androidx.compose.ui.unit.dp

/**
 * Escala de espaçamento — todos os `dp` da UI saem daqui, em vez de valores
 * soltos espalhados pelas telas.
 */
object EucSpacing {
    val none = 0.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** Padding lateral padrão das telas. */
    val screen = 20.dp

    /** Alvo de toque mínimo recomendado (acessibilidade). */
    val touchTarget = 48.dp
}
