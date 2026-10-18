package world.euc.app.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Cores de marca que não cabem no `ColorScheme` do Material: o gradiente do
 * arco (ciano → lima, como no logo), o brilho e as cores semânticas.
 */
data class EucAccents(
    val gaugeGradient: List<Color>,
    val glow: Color,
    val ok: Color,
    val warn: Color,
)

private val DarkAccents = EucAccents(
    gaugeGradient = listOf(EucPalette.Cyan, EucPalette.Lime),
    glow = EucPalette.Cyan.copy(alpha = 0.35f),
    ok = EucPalette.Ok,
    warn = EucPalette.Warn,
)

private val LightAccents = EucAccents(
    gaugeGradient = listOf(EucPalette.CyanDeep, EucPalette.LimeDeep),
    glow = EucPalette.CyanDeep.copy(alpha = 0.20f),
    ok = EucPalette.Ok,
    warn = EucPalette.Warn,
)

private val LocalEucAccents = staticCompositionLocalOf { DarkAccents }

/**
 * Tema do app: escuro por padrão (uso em movimento/à noite), com variante
 * AMOLED (preto puro) e tema claro.
 */
@Composable
fun EucTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(darkTheme, amoled) {
        when {
            amoled -> EucPalette.amoled()
            darkTheme -> EucPalette.dark()
            else -> EucPalette.light()
        }
    }
    val accents = if (darkTheme || amoled) DarkAccents else LightAccents

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EucTypography,
        shapes = EucShapes,
    ) {
        CompositionLocalProvider(
            LocalEucAccents provides accents,
            content = content,
        )
    }
}

/** Acesso às cores de marca: `EucTheme.accents.gaugeGradient`. */
object EucTheme {
    val accents: EucAccents
        @Composable get() = LocalEucAccents.current
}
