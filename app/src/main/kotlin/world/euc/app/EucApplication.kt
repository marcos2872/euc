package world.euc.app

import android.app.Application
import world.euc.app.wheel.WheelConnectionManager

/**
 * Aplicação: mantém o gerenciador de conexão com a roda durante todo o
 * ciclo de vida do processo (a conexão BLE não deve depender de uma tela).
 */
class EucApplication : Application() {

    val wheelConnectionManager: WheelConnectionManager by lazy {
        WheelConnectionManager(applicationContext)
    }
}
