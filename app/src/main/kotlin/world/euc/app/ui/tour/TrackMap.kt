package world.euc.app.ui.tour

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import world.euc.app.tour.TourPoint
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/** Ponto geográfico simples. */
data class LatLon(val latitude: Double, val longitude: Double)

private const val TILE_SIZE = 256.0
private const val MIN_ZOOM = 3f
private const val MAX_ZOOM = 18f

/**
 * Mapa de trilha com tiles do OpenStreetMap, desenhado direto em Canvas
 * (sem biblioteca de mapas e sem chave de API).
 *
 * - arrastar move, pinça dá zoom
 * - [followLast] mantém o mapa no último ponto (gravação ao vivo)
 * - ao abrir com uma trilha, enquadra o percurso inteiro
 */
@Composable
fun TrackMap(
    points: List<TourPoint>,
    modifier: Modifier = Modifier,
    followLast: Boolean = false,
    showZoomButtons: Boolean = true,
    /** Posição para centralizar quando ainda não há pontos (fix aproximado). */
    fallbackCenter: LatLon? = null,
) {
    val context = LocalContext.current
    val tiles = remember { mutableStateMapOf<String, ImageBitmap>() }
    var zoom by remember { mutableFloatStateOf(15f) }
    var center by remember { mutableStateOf<LatLon?>(null) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var userMoved by remember { mutableStateOf(false) }

    // Cores lidas fora do Canvas (dentro do desenho não há contexto de composição).
    val emptyTileColor = MaterialTheme.colorScheme.surfaceVariant
    val trailColor = MaterialTheme.colorScheme.primary

    // Enquadramento inicial e acompanhamento do último ponto.
    LaunchedEffect(points.size, viewport, fallbackCenter) {
        if (viewport == IntSize.Zero) return@LaunchedEffect
        val last = points.lastOrNull()
        if (center == null) {
            val bounds = TrackBounds.of(points)
            if (bounds != null) {
                zoom = bounds.zoomFor(viewport, paddingPx = 48f)
            }
            val target = last?.let { LatLon(it.latitude, it.longitude) } ?: fallbackCenter
            if (target != null) center = target
        } else if (followLast && !userMoved && last != null) {
            center = LatLon(last.latitude, last.longitude)
        }
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                // Sem isto, os tiles desenhados fora do quadro "vazam" por cima
                // dos elementos vizinhos (ex.: os botões do cartão de gravação).
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .onSizeChanged { viewport = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        val current = center ?: return@detectTransformGestures
                        val scale = TILE_SIZE * 2.0.pow(zoom.toDouble())
                        val world = current.toWorld()
                        val newZoom = (zoom + log2(gestureZoom.toDouble())).toFloat()
                            .coerceIn(MIN_ZOOM, MAX_ZOOM)
                        val newWorldX = world.first - pan.x / scale
                        val newWorldY = world.second - pan.y / scale
                        zoom = newZoom
                        center = fromWorld(newWorldX, newWorldY)
                        userMoved = true
                    }
                },
        ) {
            val current = center ?: return@Canvas
            val scale = TILE_SIZE * 2.0.pow(zoom.toDouble())
            val world = current.toWorld()
            val originX = world.first * scale - size.width / 2.0
            val originY = world.second * scale - size.height / 2.0

            // Tiles visíveis.
            val firstTileX = floor(originX / TILE_SIZE).toInt()
            val lastTileX = floor((originX + size.width) / TILE_SIZE).toInt()
            val firstTileY = floor(originY / TILE_SIZE).toInt()
            val lastTileY = floor((originY + size.height) / TILE_SIZE).toInt()
            val tileZoom = zoom.toInt()

            for (tileX in firstTileX..lastTileX) {
                for (tileY in firstTileY..lastTileY) {
                    val key = "$tileZoom/$tileX/$tileY"
                    val bitmap = tiles[key]
                    if (bitmap != null) {
                        drawImage(
                            image = bitmap,
                            topLeft = Offset(
                                (tileX * TILE_SIZE - originX).toFloat(),
                                (tileY * TILE_SIZE - originY).toFloat(),
                            ),
                        )
                    } else {
                        drawRect(
                            color = emptyTileColor,
                            topLeft = Offset(
                                (tileX * TILE_SIZE - originX).toFloat(),
                                (tileY * TILE_SIZE - originY).toFloat(),
                            ),
                            size = Size(TILE_SIZE.toFloat(), TILE_SIZE.toFloat()),
                        )
                    }
                }
            }

            // Trilha.
            if (points.size >= 2) {
                val path = Path()
                points.forEachIndexed { index, point ->
                    val pointWorld = point.toWorld()
                    val x = (pointWorld.first * scale - originX).toFloat()
                    val y = (pointWorld.second * scale - originY).toFloat()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = trailColor,
                    style = Stroke(width = 4.dp.toPx()),
                )
            }

            // Ponto de partida e posição atual.
            points.firstOrNull()?.let { start ->
                drawCircle(
                    color = Color(0xFF2E7D32),
                    radius = 6.dp.toPx(),
                    center = start.screenPosition(scale, originX, originY),
                )
            }
            points.lastOrNull()?.let { last ->
                drawCircle(
                    color = Color(0xFFC62828),
                    radius = 6.dp.toPx(),
                    center = last.screenPosition(scale, originX, originY),
                )
            }
        }

        // Carrega os tiles que faltam (fora do desenho).
        MissingTilesLoader(
            tiles = tiles,
            context = context,
            zoom = zoom,
            center = center,
            viewport = viewport,
        )

        if (center == null) {
            Text(
                "Aguardando sinal de GPS para mostrar o mapa…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
            )
        }

        if (showZoomButtons) {
            MapControls(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                onZoomIn = { zoom = (zoom + 1).coerceAtMost(MAX_ZOOM) },
                onZoomOut = { zoom = (zoom - 1).coerceAtLeast(MIN_ZOOM) },
                onRecenter = {
                    userMoved = false
                    points.lastOrNull()?.let { center = LatLon(it.latitude, it.longitude) }
                },
            )
        }

        Text(
            text = "© OpenStreetMap",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp),
        )
    }
}

/** Dispara o carregamento dos tiles visíveis que ainda não estão em memória. */
@Composable
private fun MissingTilesLoader(
    tiles: MutableMap<String, ImageBitmap>,
    context: Context,
    zoom: Float,
    center: LatLon?,
    viewport: IntSize,
) {
    val current = center ?: return
    if (viewport == IntSize.Zero) return

    val tileZoom = zoom.toInt()
    val scale = TILE_SIZE * 2.0.pow(zoom.toDouble())
    val world = current.toWorld()
    val originX = world.first * scale - viewport.width / 2.0
    val originY = world.second * scale - viewport.height / 2.0
    val firstTileX = floor(originX / TILE_SIZE).toInt()
    val lastTileX = floor((originX + viewport.width) / TILE_SIZE).toInt()
    val firstTileY = floor(originY / TILE_SIZE).toInt()
    val lastTileY = floor((originY + viewport.height) / TILE_SIZE).toInt()
    val maxIndex = 2.0.pow(tileZoom).toInt()

    val wanted = buildList {
        for (tileX in firstTileX..lastTileX) {
            for (tileY in firstTileY..lastTileY) {
                if (tileX < 0 || tileY < 0 || tileX >= maxIndex || tileY >= maxIndex) continue
                val key = "$tileZoom/$tileX/$tileY"
                if (key !in tiles) add(key)
            }
        }
    }

    LaunchedEffect(wanted) {
        wanted.forEach { key ->
            if (tiles.containsKey(key)) return@forEach
            val parts = key.split('/')
            val bitmap = OsmTiles.load(
                context = context,
                zoom = parts[0].toInt(),
                tileX = parts[1].toInt(),
                tileY = parts[2].toInt(),
            ) ?: return@forEach
            tiles[key] = bitmap
        }
    }
}

/** Cache (memória + disco) e download dos tiles do OpenStreetMap. */
private object OsmTiles {

    private const val MAX_MEMORY_TILES = 64
    private const val USER_AGENT = "euc-app/0.1 (Android; uso pessoal)"

    private val mutex = Mutex()
    private val memory = LinkedHashMap<String, ImageBitmap>()

    suspend fun load(context: Context, zoom: Int, tileX: Int, tileY: Int): ImageBitmap? {
        val key = "$zoom/$tileX/$tileY"
        mutex.withLock { memory[key] }?.let { return it }

        val file = File(context.cacheDir, "osm/$zoom/$tileX/$tileY.png")
        val bitmap = withContext(Dispatchers.IO) {
            val bytes = if (file.isFile) {
                file.readBytes()
            } else {
                download(zoom, tileX, tileY)?.also { data ->
                    runCatching {
                        file.parentFile?.mkdirs()
                        file.writeBytes(data)
                    }
                }
            }
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        } ?: return null

        mutex.withLock {
            memory[key] = bitmap
            while (memory.size > MAX_MEMORY_TILES) {
                val oldest = memory.keys.firstOrNull() ?: break
                memory.remove(oldest)
            }
        }
        return bitmap
    }

    private fun download(zoom: Int, tileX: Int, tileY: Int): ByteArray? = runCatching {
        val connection = URL("https://tile.openstreetmap.org/$zoom/$tileX/$tileY.png")
            .openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.connect()
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

/** Limites geográficos de uma trilha. */
private data class TrackBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
) {
    fun zoomFor(viewport: IntSize, paddingPx: Float): Float {
        val minWorld = LatLon(minLat, minLon).toWorld()
        val maxWorld = LatLon(maxLat, maxLon).toWorld()
        val worldWidth = abs(maxWorld.first - minWorld.first).coerceAtLeast(1e-9)
        val worldHeight = abs(maxWorld.second - minWorld.second).coerceAtLeast(1e-9)
        val usableWidth = (viewport.width - 2 * paddingPx).coerceAtLeast(64f)
        val usableHeight = (viewport.height - 2 * paddingPx).coerceAtLeast(64f)
        val zoomX = log2(usableWidth / (worldWidth * TILE_SIZE))
        val zoomY = log2(usableHeight / (worldHeight * TILE_SIZE))
        return minOf(zoomX, zoomY).toFloat().coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    companion object {
        fun of(points: List<TourPoint>): TrackBounds? {
            if (points.isEmpty()) return null
            return TrackBounds(
                minLat = points.minOf { it.latitude },
                maxLat = points.maxOf { it.latitude },
                minLon = points.minOf { it.longitude },
                maxLon = points.maxOf { it.longitude },
            )
        }
    }
}

// ------------------------------------------------------- projeção Web Mercator

private fun TourPoint.toWorld(): Pair<Double, Double> = LatLon(latitude, longitude).toWorld()

private fun LatLon.toWorld(): Pair<Double, Double> {
    val worldX = (longitude + 180.0) / 360.0
    val clampedLat = latitude.coerceIn(-85.05112878, 85.05112878)
    val radians = Math.toRadians(clampedLat)
    val worldY = (1.0 - ln(tan(radians) + 1.0 / cos(radians)) / PI) / 2.0
    return worldX to worldY
}

private fun fromWorld(worldX: Double, worldY: Double): LatLon {
    val longitude = worldX * 360.0 - 180.0
    val latitude = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * worldY))))
    return LatLon(latitude, longitude)
}

private fun TourPoint.screenPosition(scale: Double, originX: Double, originY: Double): Offset {
    val world = toWorld()
    return Offset(
        (world.first * scale - originX).toFloat(),
        (world.second * scale - originY).toFloat(),
    )
}
