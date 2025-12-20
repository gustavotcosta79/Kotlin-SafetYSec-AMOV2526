package pt.isec.amov.safetysec.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import pt.isec.amov.safetysec.ui.screens.monitor.MonitorDashboard
import pt.isec.amov.safetysec.ui.screens.protected.ProtectedDashboard
import pt.isec.amov.safetysec.viewmodel.AuthViewModel

@Composable
fun MainDashboardScreen(authViewModel: AuthViewModel,onNavigateToLogin: () -> Unit,onNavigateToProfile: () -> Unit) {
    val user = authViewModel.currentUser

    // 1. Lógica de Carregamento (tua abordagem)
    if (user == null) {

        LaunchedEffect(Unit) {
            authViewModel.fetchCurrentUser()
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // 2. Inicialização da Tab como o teu colega fez:
    // Se for Monitor, começa no Monitor. Se não, vai para o outro.
    var isMonitorTabSelected by remember { mutableStateOf(user.isMonitor) }

    LaunchedEffect(isMonitorTabSelected) {
        if (isMonitorTabSelected && user.isMonitor) {
            authViewModel.fetchMonitoredUsers()
        }
    }

    val showBottomBar = user.isMonitor && user.isProtected

    Scaffold(
        bottomBar = {
            // Só mostra a barra se tiver ambos os perfis
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = isMonitorTabSelected,
                        onClick = { isMonitorTabSelected = true },
                        label = { Text("Monitor") },
                        icon = { Icon(Icons.Default.Visibility, contentDescription = "Monitor") }
                    )
                    NavigationBarItem(
                        selected = !isMonitorTabSelected,
                        onClick = { isMonitorTabSelected = false },
                        label = { Text("Protegido") },
                        icon = { Icon(Icons.Default.HealthAndSafety, contentDescription = "Protegido") }
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // 3. Decisão de ecrã baseada na seleção da tab (Fusão)
            if (isMonitorTabSelected && user.isMonitor) {
                MonitorDashboard(authViewModel,
                    onLogout = onNavigateToLogin,
                    onNavigateToProfile = onNavigateToProfile)
            } else if (user.isProtected) {
                ProtectedDashboard(authViewModel,
                    onLogout = onNavigateToLogin,
                    onNavigateToProfile = onNavigateToProfile)
            }
        }
    }
}