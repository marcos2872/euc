package world.euc.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import world.euc.protocol.link.WheelLink

/**
 * Implementação de [WheelLink] sobre uma conexão Bluetooth LE já estabelecida.
 *
 * Recebe bytes das notificações e os publica em [incoming]; as escritas são
 * sequenciadas aguardando o callback do GATT (com timeout de segurança), para
 * não sobrecarregar a roda com comandos em voo.
 */
class AndroidWheelLink(
    private val gatt: BluetoothGatt,
    private val writeCharacteristic: BluetoothGattCharacteristic,
) : WheelLink {

    private val incomingFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    private val writeSignals = Channel<Unit>(Channel.CONFLATED)

    override val incoming: Flow<ByteArray> = incomingFlow.asSharedFlow()

    override suspend fun write(bytes: ByteArray) {
        @Suppress("DEPRECATION")
        writeCharacteristic.setValue(bytes)
        @Suppress("DEPRECATION")
        val started = gatt.writeCharacteristic(writeCharacteristic)
        if (!started) {
            throw IllegalStateException("A roda recusou a escrita BLE (operação em andamento)")
        }
        // Aguarda o callback; se a roda não responder, segue em frente após o timeout.
        withTimeoutOrNull(WRITE_TIMEOUT_MILLIS) { writeSignals.receive() }
    }

    /** Chamado pelo callback do GATT a cada notificação recebida. */
    internal fun onNotification(value: ByteArray) {
        incomingFlow.tryEmit(value)
    }

    /** Chamado pelo callback do GATT quando uma escrita é confirmada. */
    internal fun onWriteCompleted() {
        writeSignals.trySend(Unit)
    }

    /** Encerra a conexão GATT. */
    fun close() {
        gatt.close()
    }

    private companion object {
        const val WRITE_TIMEOUT_MILLIS = 2_000L
    }
}
