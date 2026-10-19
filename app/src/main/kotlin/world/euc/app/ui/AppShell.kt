package world.euc.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.serialization.Serializable
import world.euc.app.EucApplication
import world.euc.app.ui.about.AboutScreen
import world.euc.app.ui.alarms.AlarmsScreen
import world.euc.app.ui.connect.ConnectScreen
import world.euc.app.ui.dashboard.DashboardScreen
import world.euc.app.ui.designsystem.EucMotion
import world.euc.app.ui.designsystem.EucSpacing
import world.euc.app.ui.designsystem.PillShape
import world.euc.app.ui.settings.SettingsScreen
import world.euc.app.ui.tour.TourDetailScreen
import world.euc.app.ui.tour.ToursScreen
import world.euc.app.wheel.ConnectionState

/** Rotas do app (chaves do Navigation 3). Serializáveis para a pilha sobreviver
 * a mudanças de configuração e à morte do processo. */
@Serializable
sealed interface EucRoute : NavKey {
    @Serializable
    data object Connect : EucRoute

    @Serializable
    data object Dashboard : EucRoute

    @Serializable
    data object Tours : EucRoute

    @Serializable
    data object Alarms : EucRoute

    @Serializable
    data object Settings : EucRoute

    @Serializable
    data object About : EucRoute

    @Serializable
    data class TourDetail(val tourId: String) : EucRoute
}

private data class ShellTab(val route: EucRoute, val label: String, val icon: ImageVector)

private val ConnectedTabs = listOf(
    ShellTab(EucRoute.Dashboard, "Painel", Icons.Default.Speed),
    ShellTab(EucRoute.Tours, "Passeios", Icons.Default.Timeline),
    ShellTab(EucRoute.Alarms, "Alarmes", Icons.Default.Notifications),
    ShellTab(EucRoute.Settings, "Ajustes", Icons.Default.Settings),
)

private val DisconnectedTabs = listOf(
    ShellTab(EucRoute.Connect, "Conectar", Icons.Default.Bluetooth),
    ShellTab(EucRoute.Tours, "Passeios", Icons.Default.Timeline),
    ShellTab(EucRoute.About, "Sobre", Icons.Default.Info),
)

/** Só em debug: `am start --es screen <tela>`. */
private fun routeForScreen(screen: String?): EucRoute? = when (screen?.lowercase()) {
    "connect" -> EucRoute.Connect
    "dashboard" -> EucRoute.Dashboard
    "tours" -> EucRoute.Tours
    "alarms" -> EucRoute.Alarms
    "settings" -> EucRoute.Settings
    "about" -> EucRoute.About
    else -> null
}

/**
 * Casca do app: barra inferior flutuante com desfoque, transições entre telas e
 * pilha de navegação (Navigation 3).
 *
 * Desconectado: Conectar · Passeios · Sobre. Conectado: Painel · Passeios ·
 * Alarmes · Ajustes (Sobre fica dentro de Ajustes).
 */
@Composable
fun EucApp(
    application: EucApplication,
    debugScreen: String? = null,
    debugTourId: String? = null,
) {
    val manager = application.wheelConnectionManager
    val state by manager.state.collectAsStateWithLifecycle()
    val connected = state.connection is ConnectionState.Connected
    val tabs = if (connected) ConnectedTabs else DisconnectedTabs

    val backStack = rememberNavBackStack(routeForScreen(debugScreen) ?: tabs.first().route)
    val hazeState = rememberHazeState()

    if (debugTourId != null) {
        LaunchedEffect(debugTourId) { backStack.add(EucRoute.TourDetail(debugTourId)) }
    }

    // Conectar/desconectar troca o conjunto de abas: mantém a aba atual válida.
    LaunchedEffect(connected) {
        if (backStack.size == 1 && tabs.none { it.route == backStack.last() }) {
            backStack[backStack.lastIndex] = tabs.first().route
        }
    }

    // Troca de aba: descarta telas de detalhe e substitui a aba do topo.
    fun openTab(route: EucRoute) {
        while (backStack.size > 1) backStack.removeLast()
        if (backStack.lastOrNull() != route) backStack[backStack.lastIndex] = route
    }

    // Voltar: desempilha; se já é a raiz, cai na aba padrão daquele estado.
    fun goBack(fallback: EucRoute) {
        if (backStack.size > 1) backStack.removeLastOrNull() else openTab(fallback)
    }

    Scaffold(
        bottomBar = {
            EucBottomBar(
                tabs = tabs,
                current = backStack.lastOrNull(),
                hazeState = hazeState,
                onSelect = { openTab(it) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .padding(innerPadding),
            transitionSpec = {
                (
                    fadeIn(tween(EucMotion.MEDIUM)) +
                        scaleIn(initialScale = 0.97f, animationSpec = tween(EucMotion.MEDIUM))
                    ) togetherWith fadeOut(tween(EucMotion.FAST))
            },
            popTransitionSpec = {
                fadeIn(tween(EucMotion.MEDIUM)) togetherWith fadeOut(tween(EucMotion.SHORT))
            },
            entryProvider = entryProvider {
                entry<EucRoute.Connect> { ConnectScreen(manager, state) }

                entry<EucRoute.Dashboard> {
                    DashboardScreen(manager = manager, state = state)
                }

                entry<EucRoute.Tours> {
                    ToursScreen(
                        recorder = application.tourRecorder,
                        store = application.tourStore,
                        onOpenTour = { backStack.add(EucRoute.TourDetail(it)) },
                    )
                }

                entry<EucRoute.Alarms> {
                    AlarmsScreen(
                        manager = manager,
                        state = state,
                        onBack = { goBack(EucRoute.Dashboard) },
                    )
                }

                entry<EucRoute.Settings> {
                    SettingsScreen(
                        manager = manager,
                        state = state,
                        onBack = { goBack(EucRoute.Dashboard) },
                        onOpenAbout = { backStack.add(EucRoute.About) },
                    )
                }

                entry<EucRoute.About> {
                    AboutScreen(
                        state = state,
                        csvLogger = application.csvLogger,
                        tourStore = application.tourStore,
                        onToggleCsvLogging = { manager.setCsvLogging(it) },
                        onBack = { goBack(if (connected) EucRoute.Dashboard else EucRoute.Connect) },
                    )
                }

                entry<EucRoute.TourDetail> { route ->
                    TourDetailScreen(
                        tourId = route.tourId,
                        store = application.tourStore,
                        onBack = { goBack(EucRoute.Tours) },
                    )
                }
            },
        )
    }
}

/**
 * Barra inferior flutuante: pílula translúcida com desfoque do conteúdo que
 * passa por trás (Haze), no lugar da barra sólida padrão.
 */
@Composable
private fun EucBottomBar(
    tabs: List<ShellTab>,
    current: NavKey?,
    hazeState: HazeState,
    onSelect: (EucRoute) -> Unit,
) {
    val style = HazeMaterials.thin(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = EucSpacing.lg, vertical = EucSpacing.sm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PillShape)
                .hazeBlur(input = HazeInput.Backdrop(hazeState), style = style)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PillShape)
                .padding(horizontal = EucSpacing.xs, vertical = EucSpacing.xs),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                EucTabItem(
                    tab = tab,
                    selected = tab.route == current,
                    onClick = { onSelect(tab.route) },
                )
            }
        }
    }
}

@Composable
private fun EucTabItem(tab: ShellTab, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .clip(PillShape)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                PillShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = EucSpacing.md, vertical = EucSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EucSpacing.xs),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}
