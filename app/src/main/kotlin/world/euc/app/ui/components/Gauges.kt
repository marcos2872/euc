package world.euc.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import world.euc.app.ui.designsystem.EucMotion
import world.euc.app.ui.designsystem.EucTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Ângulo inicial e abertura do arco do velocímetro (como num conta-giros). */
private const val GAUGE_START_ANGLE = 150f
private const val GAUGE_SWEEP_ANGLE = 240f

/**
 * Velocímetro circular: arco em gradiente ciano → lima (o arco do logo), marcas
 * a cada 5 km/h e a marca do limite (tiltback) da roda.
 *
 * O número central usa figuras tabulares, então não "pula" de largura a cada
 * leitura.
 */
@Composable
fun SpeedGauge(
    speedKmh: Double,
    maxKmh: Double,
    limitKmh: Double?,
    modifier: Modifier = Modifier,
    unit: String = "km/h",
    caption: String? = null,
) {
    val accents = EucTheme.accents
    val animated by animateFloatAsState(
        targetValue = speedKmh.toFloat().coerceIn(0f, maxKmh.toFloat()),
        animationSpec = EucMotion.smooth,
        label = "velocidade",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val tickColor = MaterialTheme.colorScheme.outline
    val limitColor = MaterialTheme.colorScheme.secondary

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2 + 8.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val gradient = Brush.linearGradient(
                colors = accents.gaugeGradient,
                start = Offset(0f, size.height),
                end = Offset(size.width, 0f),
            )

            // trilha
            drawArc(
                color = trackColor,
                startAngle = GAUGE_START_ANGLE,
                sweepAngle = GAUGE_SWEEP_ANGLE,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // marcas a cada 5 km/h
            val steps = (maxKmh / 5.0).toInt().coerceAtLeast(1)
            repeat(steps + 1) { index ->
                val angle = Math.toRadians(
                    (GAUGE_START_ANGLE + GAUGE_SWEEP_ANGLE * index / steps).toDouble(),
                )
                val center = Offset(size.width / 2, size.height / 2)
                val outer = arcSize.width / 2 + stroke / 2
                val inner = outer - if (index % 2 == 0) 9.dp.toPx() else 5.dp.toPx()
                drawLine(
                    color = tickColor,
                    start = center + Offset((cos(angle) * inner).toFloat(), (sin(angle) * inner).toFloat()),
                    end = center + Offset((cos(angle) * outer).toFloat(), (sin(angle) * outer).toFloat()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            val fraction = (animated / maxKmh.toFloat()).coerceIn(0f, 1f)
            val progressSweep = GAUGE_SWEEP_ANGLE * fraction

            // brilho por trás do progresso
            drawArc(
                brush = gradient,
                startAngle = GAUGE_START_ANGLE,
                sweepAngle = progressSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                alpha = 0.22f,
                style = Stroke(width = stroke * 2.1f, cap = StrokeCap.Round),
            )

            // progresso
            drawArc(
                brush = gradient,
                startAngle = GAUGE_START_ANGLE,
                sweepAngle = progressSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // marca do limite (tiltback)
            limitKmh?.let { limit ->
                val limitFraction = (limit / maxKmh).toFloat().coerceIn(0f, 1f)
                drawLimitMarker(
                    angleDegrees = GAUGE_START_ANGLE + GAUGE_SWEEP_ANGLE * limitFraction,
                    radius = arcSize.width / 2 + stroke / 2,
                    color = limitColor,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = formatValue(animated, 1),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            caption?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun DrawScope.drawLimitMarker(angleDegrees: Float, radius: Float, color: Color) {
    val angle = Math.toRadians(angleDegrees.toDouble())
    val center = Offset(size.width / 2, size.height / 2)
    val direction = Offset((cos(angle)).toFloat(), (sin(angle)).toFloat())
    drawLine(
        color = color,
        start = center + direction * (radius * 0.74f),
        end = center + direction * (radius * 0.94f),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

/**
 * Anel compacto para um valor secundário (bateria, carga do motor, temperatura),
 * com o valor no centro e o rótulo embaixo.
 */
@Composable
fun RingGauge(
    fraction: Float,
    valueText: String,
    label: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 96.dp,
    color: Color? = null,
    caption: String? = null,
) {
    val accent = color ?: MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = EucMotion.smooth,
        label = label,
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(diameter), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                val inset = stroke / 2 + 2.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)

                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    alpha = 0.25f,
                    style = Stroke(width = stroke * 1.8f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                caption?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Deslocamento por ângulo e raio, usado nos marcadores do arco. */
private operator fun Offset.times(scale: Float): Offset = Offset(x * scale, y * scale)
