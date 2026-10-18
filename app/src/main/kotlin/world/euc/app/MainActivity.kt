package world.euc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import world.euc.app.ui.about.AboutScreen
import world.euc.app.ui.alarms.AlarmsScreen
import world.euc.app.ui.connect.ConnectScreen
import world.euc.app.ui.dashboard.DashboardScreen
import world.euc.app.ui.settings.SettingsScreen
import world.euc.app.ui.theme.EucTheme
import world.euc.app.ui.tour.TourDetailScreen
import world.euc.app.ui.tour.ToursScreen
import world.euc.app.wheel.ConnectionState

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = application as EucApplication

        setContent {
            EucTheme {
                EucApp(application)
            }
        }
    }
}

/** Destinos do app. Quais estão disponíveis depende de haver conexão. */
private enum class Tab(val label: String, val icon: ImageVector) {
    Connect("Conectar", Icons.Default.Bluetooth),
    Dashboard("Painel", Icons.Default.Speed),
    Tours("Passeios", Icons.Default.Timeline),
    Alarms("Alarmes", Icons.Default.Notifications),
    Settings("Ajustes", Icons.Default.Settings),
    About("Sobre", Icons.Default.Info),
}

/**
 * Navegação por estado: sem biblioteca de navegação enquanto os destinos são
 * poucos. Desconectado: Conectar · Passeios · Sobre. Conectado: Painel ·
 * Passeios · Alarmes · Ajustes (Sobre fica dentro de Ajustes).
 */
@Composable
private fun EucApp(application: EucApplication) {
    val manager = application.wheelConnectionManager
    val state by manager.state.collectAsStateWithLifecycle()
    var destination by remember { mutableStateOf(Tab.Connect) }
    var openTourId by remember { mutableStateOf<String?>(null) }

    openTourId?.let { tourId ->
        TourDetailScreen(
            tourId = tourId,
            store = application.tourStore,
            onBack = { openTourId = null },
        )
        return
    }

    val connected = state.connection is ConnectionState.Connected
    val tabs = if (connected) {
        listOf(Tab.Dashboard, Tab.Tours, Tab.Alarms, Tab.Settings)
    } else {
        listOf(Tab.Connect, Tab.Tours, Tab.About)
    }
    val selected = if (destination in tabs) destination else tabs.first()

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { destination = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selected) {
                Tab.Connect -> ConnectScreen(manager, state)

                Tab.Dashboard -> DashboardScreen(
                    manager = manager,
                    state = state,
                    onOpenSettings = { destination = Tab.Settings },
                    onOpenAlarms = { destination = Tab.Alarms },
                )

                Tab.Tours -> ToursScreen(
                    recorder = application.tourRecorder,
                    store = application.tourStore,
                    onOpenTour = { openTourId = it },
                )

                Tab.Alarms -> AlarmsScreen(
                    manager = manager,
                    state = state,
                    onBack = { destination = Tab.Dashboard },
                )

                Tab.Settings -> SettingsScreen(
                    manager = manager,
                    state = state,
                    onBack = { destination = Tab.Dashboard },
                    onOpenAbout = { destination = Tab.About },
                )

                Tab.About -> AboutScreen(
                    state = state,
                    csvLogger = application.csvLogger,
                    tourStore = application.tourStore,
                    onToggleCsvLogging = { manager.setCsvLogging(it) },
                    onBack = { destination = Tab.Connect },
                )
            }
        }
    }
}
