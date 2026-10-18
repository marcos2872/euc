package world.euc.app.ui.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Paleta da marca, tirada do logo: fundo naval quase preto, arco ciano → lima
 * com brilho.
 *
 * O tema **escuro é o padrão** (uso em movimento e à noite); o claro é uma
 * variação do mesmo sistema e o AMOLED usa preto puro para economizar bateria
 * em telas OLED.
 */
object EucPalette {

    // --- Marca -------------------------------------------------------------
    val Cyan = Color(0xFF35E0F5)
    val CyanDeep = Color(0xFF0B7C8A)
    val CyanSoft = Color(0xFFB9F2FA)
    val Lime = Color(0xFFA8E635)
    val LimeDeep = Color(0xFF4E7A0B)
    val LimeSoft = Color(0xFFDCF7A8)

    // --- Semânticas --------------------------------------------------------
    val Ok = Color(0xFF4ADE80)
    val Warn = Color(0xFFFBBF24)
    val Danger = Color(0xFFFF5A5F)

    // --- Neutros escuros ---------------------------------------------------
    private val Ink950 = Color(0xFF05080C)
    private val Ink900 = Color(0xFF070B10)
    private val Ink850 = Color(0xFF0A1017)
    private val Ink800 = Color(0xFF0D131B)
    private val Ink750 = Color(0xFF0F1620)
    private val Ink700 = Color(0xFF151E29)
    private val Ink650 = Color(0xFF1B2530)
    private val Ink600 = Color(0xFF26313D)
    private val TextDark = Color(0xFFE6EDF3)
    private val TextDimDark = Color(0xFF9AAAB8)

    // --- Neutros claros ----------------------------------------------------
    private val Paper0 = Color(0xFFFFFFFF)
    private val Paper50 = Color(0xFFF6F8FB)
    private val Paper100 = Color(0xFFEFF3F8)
    private val Paper200 = Color(0xFFE8EDF4)
    private val Paper300 = Color(0xFFE1E7EF)
    private val Paper400 = Color(0xFFC3CDD9)
    private val Paper500 = Color(0xFFDCE3EB)
    private val TextLight = Color(0xFF0F1720)
    private val TextDimLight = Color(0xFF44515F)

    /** Esquema escuro (padrão do app). */
    fun dark(): ColorScheme = darkColorScheme(
        primary = Cyan,
        onPrimary = Color(0xFF04252B),
        primaryContainer = Color(0xFF0B3B45),
        onPrimaryContainer = CyanSoft,
        secondary = Lime,
        onSecondary = Color(0xFF1A2A05),
        secondaryContainer = Color(0xFF2C4209),
        onSecondaryContainer = LimeSoft,
        tertiary = Color(0xFF7CC6FF),
        onTertiary = Color(0xFF04243D),
        tertiaryContainer = Color(0xFF123A57),
        onTertiaryContainer = Color(0xFFCDE7FF),
        error = Danger,
        onError = Color(0xFF3A0507),
        errorContainer = Color(0xFF5C1416),
        onErrorContainer = Color(0xFFFFDAD8),
        background = Ink900,
        onBackground = TextDark,
        surface = Ink800,
        onSurface = TextDark,
        surfaceVariant = Ink650,
        onSurfaceVariant = TextDimDark,
        surfaceContainerLowest = Ink950,
        surfaceContainerLow = Ink850,
        surfaceContainer = Ink750,
        surfaceContainerHigh = Ink700,
        surfaceContainerHighest = Ink650,
        outline = Ink600,
        outlineVariant = Color(0xFF1E2937),
        inverseSurface = TextDark,
        inverseOnSurface = Ink800,
        inversePrimary = CyanDeep,
        scrim = Color(0xFF000000),
    )

    /** Escuro com preto puro (telas AMOLED). */
    fun amoled(): ColorScheme = dark().copy(
        background = Color(0xFF000000),
        surface = Color(0xFF04070A),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF04070A),
        surfaceContainer = Color(0xFF0A0F15),
        surfaceContainerHigh = Color(0xFF101821),
        surfaceContainerHighest = Color(0xFF161F2A),
    )

    /** Esquema claro. */
    fun light(): ColorScheme = lightColorScheme(
        primary = CyanDeep,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFB6EBF3),
        onPrimaryContainer = Color(0xFF00363D),
        secondary = LimeDeep,
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD3F0A6),
        onSecondaryContainer = Color(0xFF15290A),
        tertiary = Color(0xFF1F6FA8),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFCFE7FF),
        onTertiaryContainer = Color(0xFF001E33),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        background = Paper50,
        onBackground = TextLight,
        surface = Paper0,
        onSurface = TextLight,
        surfaceVariant = Paper200,
        onSurfaceVariant = TextDimLight,
        surfaceContainerLowest = Paper0,
        surfaceContainerLow = Paper50,
        surfaceContainer = Paper100,
        surfaceContainerHigh = Paper200,
        surfaceContainerHighest = Paper300,
        outline = Paper400,
        outlineVariant = Paper500,
        inverseSurface = TextLight,
        inverseOnSurface = Paper50,
        inversePrimary = Cyan,
        scrim = Color(0xFF000000),
    )
}
