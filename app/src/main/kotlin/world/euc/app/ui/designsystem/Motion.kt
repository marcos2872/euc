package world.euc.app.ui.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Movimento: durações curtas e molas com pouca oscilação — o app é usado em
 * movimento, então nada de animações longas que atrasem a leitura do painel.
 */
object EucMotion {
    const val FAST = 120
    const val SHORT = 200
    const val MEDIUM = 320
    const val LONG = 480

    /** Entrada/saída suave de elementos. */
    val smooth = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

    /** Resposta de toque/estado (mais rápida). */
    val snappy = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
}
