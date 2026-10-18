package world.euc.app.alarm

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import world.euc.protocol.alarm.AlarmConfig
import world.euc.protocol.alarm.AlarmPriority
import world.euc.protocol.alarm.AlarmEvent
import world.euc.protocol.alarm.AlarmType
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Avisa o piloto sobre um alarme: vibração do telefone e mensagem falada
 * em português (TTS do sistema).
 *
 * O TTS inicializa de forma assíncrona; avisos pedidos antes do motor ficar
 * pronto são falados assim que ele inicializa.
 */
private fun formatValue(value: Double?): String =
    value?.let { it.roundToInt().toString() } ?: ""

class AlarmNotifier(context: Context) {

    private val appContext = context.applicationContext
    private val vibrator: Vibrator? = resolveVibrator(appContext)

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingMessage: String? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale("pt", "BR")
                pendingMessage?.let { speak(it) }
                pendingMessage = null
            }
        }
    }

    /** Dispara vibração e voz conforme a configuração. */
    fun notify(event: AlarmEvent, config: AlarmConfig) {
        if (config.vibrationEnabled) vibrate(event)
        if (config.voiceEnabled) speak(messageFor(event))
    }

    /** Libera o motor de voz (chamar quando o manager for descartado). */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private fun vibrate(event: AlarmEvent) {
        val vibrator = vibrator ?: return
        val pattern = when (event.priority) {
            AlarmPriority.LOW -> longArrayOf(0, 120)
            AlarmPriority.MEDIUM -> longArrayOf(0, 150, 120, 150)
            AlarmPriority.HIGH -> longArrayOf(0, 250, 120, 250, 120, 250)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun speak(message: String) {
        val engine = tts
        if (engine == null || !ttsReady) {
            pendingMessage = message
            return
        }
        engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    /** Mensagem falada em português para cada tipo de alarme. */
    internal fun messageFor(event: AlarmEvent): String {
        val value = event.value
        return when (event.type) {
            AlarmType.SPEED_1 -> "Velocidade ${formatValue(value)} quilômetros por hora"
            AlarmType.SPEED_2 -> "Atenção, velocidade ${formatValue(value)}"
            AlarmType.SPEED_3 -> "Perigo! Velocidade ${formatValue(value)}"
            AlarmType.SPEED_PRE_1 -> "Aproximando do alarme de velocidade"
            AlarmType.SPEED_PRE_2 -> "Cuidado, próximo do alarme"
            AlarmType.SPEED_LIMIT -> "Cuidado! Limite de velocidade da roda"
            AlarmType.CURRENT -> "Corrente alta"
            AlarmType.TEMPERATURE -> "Temperatura alta: ${formatValue(value)} graus"
            AlarmType.OVERVOLTAGE -> "Tensão alta"
            AlarmType.UNDERVOLTAGE -> "Tensão baixa"
            AlarmType.BATTERY -> "Bateria baixa: ${formatValue(value)} por cento"
            AlarmType.SAFETY_MARGIN -> "Margem de segurança baixa"
            AlarmType.CONNECTION_LOST -> "Conexão com a roda perdida"
        }
    }

    private companion object {
        const val UTTERANCE_ID = "euc-alarm"

        fun resolveVibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
    }
}
