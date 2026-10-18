package world.euc.app.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Cantos generosos, no espírito das interfaces modernas: cartões de 22dp,
 * folhas de 28dp e "pílulas" para botões e chips.
 */
val EucShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Formato de pílula (botões, chips, barras). */
val PillShape = RoundedCornerShape(percent = 50)
