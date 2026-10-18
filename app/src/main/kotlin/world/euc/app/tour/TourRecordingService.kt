package world.euc.app.tour

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import world.euc.app.EucApplication
import world.euc.app.MainActivity
import world.euc.app.R
import java.util.Locale

/**
 * Serviço em primeiro plano que mantém a gravação viva com o app em
 * segundo plano (tela apagada, bolso), com uma notificação de acompanhamento
 * e um botão para parar.
 */
class TourRecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var recorder: TourRecorder

    override fun onCreate() {
        super.onCreate()
        recorder = (application as EucApplication).tourRecorder
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            recorder.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!recorder.isRecording) {
            stopSelf()
            return START_NOT_STICKY
        }

        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(recorder.state.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        }.isSuccess
        if (!started) {
            // Sistema recusou o serviço em primeiro plano (permissão de localização,
            // por exemplo): a gravação continua enquanto o app estiver aberto.
            stopSelf()
            return START_NOT_STICKY
        }

        scope.launch {
            recorder.state.collect { state ->
                if (!state.recording) {
                    stopSelf()
                } else {
                    notify(notification(state))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------------- notificação

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Gravação de passeio",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Aviso permanente enquanto um passeio está sendo gravado"
                setShowBadge(false)
            },
        )
    }

    private fun notification(state: RecordingUiState): Notification {
        val summary = if (state.pointCount == 0) {
            if (state.gpsEnabled) "Aguardando sinal de GPS…" else "GPS desligado"
        } else {
            buildString {
                append(String.format(Locale.getDefault(), "%.2f km", state.distanceKm))
                append(" · ")
                append(formatDuration(state.durationMs))
                append(" · ")
                append(String.format(Locale.getDefault(), "%.1f km/h", state.currentSpeedKmh))
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tour)
            .setContentTitle("Gravando passeio")
            .setContentText(summary)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent())
            .addAction(0, "Parar", stopIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun notify(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, TourRecordingService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val CHANNEL_ID = "tour-recording"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "world.euc.app.action.STOP_TOUR_RECORDING"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TourRecordingService::class.java),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TourRecordingService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}

/** Duração em `hh:mm:ss` (ou `mm:ss` abaixo de uma hora). */
internal fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
