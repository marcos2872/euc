package world.euc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import world.euc.app.ui.connect.ConnectScreen
import world.euc.app.ui.dashboard.DashboardScreen
import world.euc.app.ui.settings.SettingsScreen
import world.euc.app.ui.theme.EucTheme
import world.euc.app.wheel.ConnectionState
import world.euc.app.wheel.WheelConnectionManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = (application as EucApplication).wheelConnectionManager

        setContent {
            EucTheme {
                EucApp(manager)
            }
        }
    }
}

/**
 * Navegação por estado: conectar → painel → configurações.
 * Sem biblioteca de navegação enquanto são só três destinos.
 */
@Composable
private fun EucApp(manager: WheelConnectionManager) {
    val state by manager.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    when {
        state.connection !is ConnectionState.Connected -> {
            showSettings = false
            ConnectScreen(manager, state)
        }
        showSettings -> SettingsScreen(manager, state, onBack = { showSettings = false })
        else -> DashboardScreen(manager, state, onOpenSettings = { showSettings = true })
    }
}
