package world.euc.app.wheel

import world.euc.protocol.alarm.AlarmConfig
import world.euc.protocol.alarm.AlarmEvent
import world.euc.protocol.inmotion.FirmwareVersions
import world.euc.protocol.inmotion.InmotionModelInfo
import world.euc.protocol.model.WheelCapabilities
import world.euc.protocol.model.WheelSettings
import world.euc.protocol.model.WheelStatistics
import world.euc.protocol.model.WheelTelemetry

/** Estado observável da conexão e da telemetria para a UI. */
data class WheelUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val model: InmotionModelInfo? = null,
    val serialNumber: String? = null,
    val firmware: FirmwareVersions? = null,
    val telemetry: WheelTelemetry = WheelTelemetry(),
    /** Configurações reportadas pela roda (parcial, conforme o firmware). */
    val settings: WheelSettings = WheelSettings(),
    /** Configurações que a roda não reporta — mantidas pelo app. */
    val local: LocalSettings = LocalSettings(),
    /** Odômetros reportados pela roda (viagem/totais), quando o firmware reporta. */
    val statistics: WheelStatistics = WheelStatistics(),
    /** Distância desde a conexão atual, em km (viagem da sessão). */
    val sessionTripKm: Double = 0.0,
    /** Odômetro acumulado pelo app para esta roda, em km (fallback do total). */
    val appOdometerKm: Double = 0.0,
    /** Relógio da última leitura recebida (0 = nenhuma). */
    val lastTelemetryAtMillis: Long = 0L,
    /** Configuração dos alarmes (persistida no aparelho). */
    val alarmConfig: AlarmConfig = AlarmConfig(),
    /** Alarme ativo no momento (para o aviso na tela). */
    val activeAlarm: AlarmEvent? = null,
    /** Resultado do handshake de autenticação (null = em andamento). */
    val authenticated: Boolean? = null,
    /** O que o modelo conectado suporta (a UI mostra apenas isso). */
    val capabilities: WheelCapabilities = WheelCapabilities(emptySet()),
    /** Reaplicar as configurações salvas na roda a cada conexão. */
    val reapplyOnConnect: Boolean = false,
    /** Gravar um log CSV da telemetria durante a sessão. */
    val csvLogging: Boolean = false,
)

/**
 * Valores que o firmware da V11y não devolve no quadro de configurações.
 * O app mantém o último valor comandado (comportamento do EUC World).
 */
data class LocalSettings(
    val headlightOn: Boolean = false,
    val daytimeRunningLightsOn: Boolean = false,
    val headlightBrightness: Int = 100,
    val fanOn: Boolean = false,
    val fanQuietMode: Boolean = false,
    val performanceMode: Boolean = false,
    val locked: Boolean = false,
    val lowBatteryRiding: Boolean = false,
    val motorNoLoadDetection: Boolean = false,
    val transportationMode: Boolean = false,
    val handleButton: Boolean = true,
)

/** Estados do ciclo de conexão. */
sealed interface ConnectionState {

    data object Disconnected : ConnectionState

    data object Connecting : ConnectionState

    data class Connected(val deviceName: String?) : ConnectionState

    data class Failed(val message: String) : ConnectionState
}
