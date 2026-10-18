package world.euc.app.ui.tour

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import world.euc.app.tour.TourStore
import java.io.File

/** Formatos de exportação oferecidos ao compartilhar um passeio. */
enum class TourShareFormat(
    val label: String,
    val description: String,
    val extension: String,
    val mimeType: String,
) {
    CSV("CSV", "planilha (Excel, análise)", "csv", "text/csv"),
    GPX("GPX", "mapa, Strava, Garmin", "gpx", "application/gpx+xml"),
}

/**
 * Exporta os passeios escolhidos no formato pedido e abre o compartilhamento
 * do Android. Com vários passeios, todos vão como anexos de uma vez.
 */
fun shareTours(
    context: Context,
    scope: CoroutineScope,
    store: TourStore,
    ids: Set<String>,
    format: TourShareFormat,
) {
    if (ids.isEmpty()) return
    scope.launch {
        val files = withContext(Dispatchers.IO) {
            ids.mapNotNull { id ->
                store.load(id)?.let { tour ->
                    val target = File(context.cacheDir, "${tour.id}.${format.extension}")
                    runCatching {
                        when (format) {
                            TourShareFormat.CSV -> store.exportCsv(tour, target)
                            TourShareFormat.GPX -> store.exportGpx(tour, target)
                        }
                    }.getOrNull()
                }
            }
        }
        if (files.isEmpty()) return@launch

        val uris = ArrayList(
            files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) },
        )
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, uris)
            }
        }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        context.startActivity(
            Intent.createChooser(intent, "Compartilhar passeios (${format.label})"),
        )
    }
}
