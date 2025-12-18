package pt.isec.amov.safetysec.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import pt.isec.amov.safetysec.data.model.User
import pt.isec.amov.safetysec.ui.screens.monitor.MonitorDashboardScreen
import pt.isec.amov.safetysec.ui.screens.protegido.ProtectedDashboardScreen

@Composable
fun MainContainerScreen (
    user: User,
    onLogout: () -> Unit
){
    var isMonitorTabSelected by remember { mutableStateOf(user.isMonitor) }

    val showBottomBar = user.isMonitor && user.isProtected
    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    // Botão MONITOR
                    NavigationBarItem(
                        selected = isMonitorTabSelected, // Se for true, está selecionado
                        onClick = { isMonitorTabSelected = true },
                        label = { Text("Monitor") },
                        icon = { Icon(Icons.Default.Visibility, contentDescription = "Monitor") }
                    )

                    // Botão PROTEGIDO
                    NavigationBarItem(
                        selected = !isMonitorTabSelected, // Se for false, está selecionado
                        onClick = { isMonitorTabSelected = false },
                        label = { Text("Protegido") },
                        icon = { Icon(Icons.Default.HealthAndSafety, contentDescription = "Protegido") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding)) {
            // DECISÃO SIMPLES:
            if (isMonitorTabSelected) {
                // Proteção para não mostrar ecrã se o user não tiver permissão
                if (user.isMonitor) MonitorDashboardScreen(onLogout)
            } else {
                if (user.isProtected) ProtectedDashboardScreen(onLogout)
            }
        }
    }
}