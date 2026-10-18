package world.euc.app

import android.app.Application
import world.euc.app.tour.CsvLogger
import world.euc.app.tour.GpsTracker
import world.euc.app.tour.TelemetryLogger
import world.euc.app.tour.TourRecorder
import world.euc.app.tour.TourStore
import world.euc.app.wheel.WheelConnectionManager
import java.io.File

/**
 * Aplicação: mantém viva, durante todo o processo, a conexão com a roda, o
 * GPS e a gravação (passeio e log CSV) — nada disso pode depender da tela.
 */
class EucApplication : Application() {

    val wheelConnectionManager: WheelConnectionManager by lazy {
        WheelConnectionManager(applicationContext)
    }

    val gpsTracker: GpsTracker by lazy { GpsTracker(applicationContext) }

    val tourStore: TourStore by lazy { TourStore(File(filesDir, TOURS_DIR)) }

    val tourRecorder: TourRecorder by lazy {
        TourRecorder(applicationContext, gpsTracker, tourStore, wheelConnectionManager.state)
    }

    val csvLogger: CsvLogger by lazy { CsvLogger(File(filesDir, LOGS_DIR)) }

    private val telemetryLogger: TelemetryLogger by lazy {
        TelemetryLogger(
            gps = gpsTracker,
            logger = csvLogger,
            wheelState = wheelConnectionManager.state,
            recorderState = tourRecorder.state,
            enabled = { wheelConnectionManager.state.value.csvLogging },
        )
    }

    override fun onCreate() {
        super.onCreate()
        telemetryLogger.start()
    }

    companion object {
        const val TOURS_DIR = "tours"
        const val LOGS_DIR = "logs"
    }
}
