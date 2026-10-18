package world.euc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import world.euc.app.ui.connect.ConnectScreen
import world.euc.app.ui.dashboard.DashboardScreen
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

@Composable
private fun EucApp(manager: WheelConnectionManager) {
    val state by manager.state.collectAsStateWithLifecycle()

    when (state.connection) {
        is ConnectionState.Connected -> DashboardScreen(manager, state)
        else -> ConnectScreen(manager, state)
    }
}
