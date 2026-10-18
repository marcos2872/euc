package world.euc.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeout
import world.euc.protocol.discovery.BleDeviceProfile
import world.euc.protocol.discovery.ProtocolCandidate
import world.euc.protocol.discovery.WheelProtocol
import world.euc.protocol.discovery.WheelProtocolDetector

/** Roda encontrada no escaneamento, já com candidatos de protocolo. */
data class DiscoveredWheel(
    val device: BluetoothDevice,
    val name: String?,
    val rssi: Int,
    val candidates: List<ProtocolCandidate>,
)

/**
 * Conecta a rodas por Bluetooth LE: escaneia, identifica o protocolo pelos
 * serviços anunciados e estabelece o [AndroidWheelLink] com os canais certos.
 *
 * Requer as permissões de runtime do Android 12+: `BLUETOOTH_SCAN`,
 * `BLUETOOTH_CONNECT` (ou `ACCESS_FINE_LOCATION` em versões anteriores).
 */
class BleWheelConnector(private val context: Context) {

    /**
     * Escaneia dispositivos que pareçam monociclos, emitindo cada descoberta.
     * O fluxo termina quando o escaneamento é cancelado ou o timeout expira.
     */
    fun scan(timeoutMillis: Long = DEFAULT_SCAN_MILLIS): Flow<DiscoveredWheel> = callbackFlow {
        requirePermission(Manifest.permission.BLUETOOTH_SCAN)
        val scanner = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
            ?.adapter
            ?.bluetoothLeScanner
            ?: throw IllegalStateException("Bluetooth LE indisponível neste dispositivo")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val profile = BleDeviceProfile(
                    name = result.device.safeName() ?: result.scanRecord?.deviceName,
                    serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString() }?.toSet().orEmpty(),
                )
                val candidates = WheelProtocolDetector.detect(profile)
                if (candidates.none { it.protocol == WheelProtocol.UNKNOWN }) {
                    trySend(
                        DiscoveredWheel(
                            device = result.device,
                            name = profile.name,
                            rssi = result.rssi,
                            candidates = candidates,
                        ),
                    )
                }
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(emptyList(), settings, callback)

        awaitClose { scanner.stopScan(callback) }
    }

    /**
     * Conecta ao dispositivo e devolve o link pronto para uso (serviços
     * descobertos e notificações habilitadas).
     */
    suspend fun connect(
        device: BluetoothDevice,
        protocol: WheelProtocol,
        timeoutMillis: Long = DEFAULT_CONNECT_MILLIS,
    ): AndroidWheelLink = withTimeout(timeoutMillis) {
        ConnectionAttempt(context, device).awaitLink(protocol)
    }

    private fun requirePermission(permission: String) {
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (!granted) throw SecurityException("Permissão $permission não concedida")
    }

    @Suppress("DEPRECATION")
    private fun BluetoothDevice.safeName(): String? =
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            name
        } else {
            null
        }

    private companion object {
        const val DEFAULT_SCAN_MILLIS = 15_000L
        const val DEFAULT_CONNECT_MILLIS = 20_000L
    }
}

/** Conduz as fases da conexão GATT até o link ficar utilizável. */
private class ConnectionAttempt(
    private val context: Context,
    private val device: BluetoothDevice,
) {

    private val connected = CompletableDeferred<Unit>()
    private val servicesDiscovered = CompletableDeferred<Unit>()
    private val notificationsEnabled = CompletableDeferred<Unit>()

    private var gatt: BluetoothGatt? = null
    private var link: AndroidWheelLink? = null

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when {
                newState == BluetoothProfile.STATE_CONNECTED -> {
                    connected.complete(Unit)
                    gatt.discoverServices()
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    val error = IllegalStateException("Conexão encerrada (status $status)")
                    connected.completeExceptionally(error)
                    servicesDiscovered.completeExceptionally(error)
                    notificationsEnabled.completeExceptionally(error)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                servicesDiscovered.complete(Unit)
            } else {
                servicesDiscovered.completeExceptionally(
                    IllegalStateException("Descoberta de serviços falhou (status $status)"),
                )
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                notificationsEnabled.complete(Unit)
            } else {
                notificationsEnabled.completeExceptionally(
                    IllegalStateException("Falha ao habilitar notificações (status $status)"),
                )
            }
        }

        @Deprecated("API 33 substituiu por onCharacteristicChanged(gatt, characteristic, value)")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            link?.onNotification(characteristic.value ?: return)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            link?.onWriteCompleted()
        }
    }

    suspend fun awaitLink(protocol: WheelProtocol): AndroidWheelLink {
        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw IllegalStateException("Não foi possível iniciar a conexão GATT")
        this.gatt = gatt

        connected.await()
        servicesDiscovered.await()

        val channels = resolveChannels(gatt, protocol)
        val established = AndroidWheelLink(gatt, channels.write)
        link = established

        enableNotifications(gatt, channels.notify)
        notificationsEnabled.await()

        return established
    }

    private fun resolveChannels(gatt: BluetoothGatt, protocol: WheelProtocol): GattChannels =
        when (protocol) {
            WheelProtocol.INMOTION_V2, WheelProtocol.NINEBOT_NEW -> GattChannels(
                write = requireCharacteristic(gatt, NUS_SERVICE, NUS_WRITE),
                notify = requireCharacteristic(gatt, NUS_SERVICE, NUS_NOTIFY),
            )
            WheelProtocol.INMOTION_V1 -> GattChannels(
                write = requireCharacteristic(gatt, FFE5_SERVICE, FFE9_CHARACTERISTIC),
                notify = requireCharacteristic(gatt, FFE0_SERVICE, FFE4_CHARACTERISTIC),
            )
            // KingSong, Gotway, Veteran e Ninebot compartilham FFE0/FFE1 para
            // escrita e notificação na mesma característica.
            WheelProtocol.KINGSONG,
            WheelProtocol.GOTWAY,
            WheelProtocol.VETERAN,
            WheelProtocol.NINEBOT,
            -> requireCharacteristic(gatt, FFE0_SERVICE, FFE1_CHARACTERISTIC).let { GattChannels(it, it) }

            WheelProtocol.UNKNOWN -> throw IllegalArgumentException("Protocolo não identificado")
        }

    private fun requireCharacteristic(
        gatt: BluetoothGatt,
        serviceUuid: String,
        characteristicUuid: String,
    ): BluetoothGattCharacteristic {
        val service = gatt.getService(UUID.fromString(serviceUuid))
            ?: throw IllegalStateException("Serviço $serviceUuid não encontrado")
        return service.getCharacteristic(UUID.fromString(characteristicUuid))
            ?: throw IllegalStateException("Característica $characteristicUuid não encontrada")
    }

    @Suppress("DEPRECATION")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(UUID.fromString(CCCD_DESCRIPTOR))
            ?: throw IllegalStateException("Descritor CCCD ausente na característica de notificação")
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(descriptor)
    }

    private data class GattChannels(
        val write: BluetoothGattCharacteristic,
        val notify: BluetoothGattCharacteristic,
    )

    private companion object {
        const val NUS_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
        const val NUS_WRITE = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
        const val NUS_NOTIFY = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
        const val FFE0_SERVICE = "0000ffe0-0000-1000-8000-00805f9b34fb"
        const val FFE1_CHARACTERISTIC = "0000ffe1-0000-1000-8000-00805f9b34fb"
        const val FFE4_CHARACTERISTIC = "0000ffe4-0000-1000-8000-00805f9b34fb"
        const val FFE5_SERVICE = "0000ffe5-0000-1000-8000-00805f9b34fb"
        const val FFE9_CHARACTERISTIC = "0000ffe9-0000-1000-8000-00805f9b34fb"
        const val CCCD_DESCRIPTOR = "00002902-0000-1000-8000-00805f9b34fb"
    }
}
