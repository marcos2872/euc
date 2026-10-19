package world.euc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import world.euc.app.ui.EucApp
import world.euc.app.ui.designsystem.EucTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val application = application as EucApplication

        // Modo debug: permite abrir uma tela específica por ADB, contornando o
        // bloqueio de toques do MIUI:
        //   am start -S -n world.euc.app/.MainActivity --es screen tours [--es tour <id>]
        val debugScreen = if (BuildConfig.DEBUG) intent?.getStringExtra("screen") else null
        val debugTourId = if (BuildConfig.DEBUG) intent?.getStringExtra("tour") else null
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("demo", false) == true) {
            application.wheelConnectionManager.startDemoTelemetry()
        }

        setContent {
            EucTheme {
                EucApp(
                    application = application,
                    debugScreen = debugScreen,
                    debugTourId = debugTourId,
                )
            }
        }
    }
}
