package world.euc.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val writeMutex = Mutex()

    override val incoming: Flow<ByteArray> = incomingFlow.asSharedFlow()

    override suspend fun write(bytes: ByteArray) {
        // Uma escrita por vez: a pilha do Android recusa uma segunda operação em
        // voo e, sem esta trava, um comando do usuário concorrendo com o polling
        // derrubava o app (SecurityException/IllegalStateException não tratada).
        writeMutex.withLock {
            var attempts = 0
            while (true) {
                if (startWrite(bytes)) break
                attempts++
                if (attempts >= WRITE_ATTEMPTS) {
                    Log.w(TAG, "GATT recusou a escrita $attempts vezes — comando descartado")
                    return
                }
                delay(WRITE_RETRY_MILLIS)
            }
            // Aguarda o callback; se a roda não responder, segue em frente após o timeout.
            withTimeoutOrNull(WRITE_TIMEOUT_MILLIS) { writeSignals.receive() }
        }
    }

    private fun startWrite(bytes: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                writeCharacteristic,
                bytes,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            writeCharacteristic.setValue(bytes)
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(writeCharacteristic)
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
        const val TAG = "EucBle"
        const val WRITE_TIMEOUT_MILLIS = 2_000L
        const val WRITE_RETRY_MILLIS = 60L
        const val WRITE_ATTEMPTS = 5
    }
}
