package pt.isec.amov.safetysec.ui.screens.monitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.maps.model.LatLng
import pt.isec.amov.safetysec.data.model.User
import pt.isec.amov.safetysec.viewmodel.AuthViewModel

@Composable
fun MonitorDashboard(authViewModel: AuthViewModel, onLogout: () -> Unit,onNavigateToProfile: () -> Unit) {

    // --- ESTADOS PARA O MAPA ---
    var userParaMapa by remember { mutableStateOf<Pair<User, LatLng?>?>(null) }

    // --- LÓGICA DO DIÁLOGO DO MAPA ---
    if (userParaMapa != null && userParaMapa?.second != null) {
        AlertDialog(
            onDismissRequest = { userParaMapa = null },
            properties = DialogProperties(usePlatformDefaultWidth = false), // Permite mapa maior
            modifier = Modifier.fillMaxSize().padding(16.dp),
            confirmButton = {
                Button(onClick = { userParaMapa = null }) { Text("Fechar") }
            },
            text = {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Chama o MapScreen que criaste (precisa de estar no projeto)
                    MapScreen(
                        latitude = userParaMapa!!.second!!.latitude,
                        longitude = userParaMapa!!.second!!.longitude,
                        title = userParaMapa!!.first.name
                    )
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // --- CABEÇALHO COM LOGOUT ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text("Painel do Monitor", style = MaterialTheme.typography.headlineSmall)

            //icon p ver o perfil
            IconButton(onClick = onNavigateToProfile) {
                Icon(Icons.Default.AccountCircle, "Perfil")
            }
            //dar logout
            TextButton(onClick = {
                authViewModel.onLogoutClick { onLogout() }
            }) {
                Text("Terminar Sessão")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. SECÇÃO DE ADICIONAR PROTEGIDO (Compacta)
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = authViewModel.codeInput,
                    onValueChange = { authViewModel.codeInput = it },
                    label = { Text("Inserir Código OTP") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { authViewModel.onLinkSubmit { /* Feedback sucesso */ } },
                    enabled = !authViewModel.isLoading
                ) {
                    Text("Ligar")
                }
            }
        }

        // 2. LISTA DE UTILIZADORES
        Text(
            "Meus Protegidos",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(authViewModel.monitoredUsers) { protegido ->
                // Verifica se este protegido tem um alerta ativo
                val alertaVinculado = authViewModel.activeAlerts.find { it.userEmail == protegido.email }
                val temAlerta = alertaVinculado != null

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (temAlerta) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(protegido.name, fontWeight = FontWeight.Bold) },
                        supportingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Badge(containerColor = if (temAlerta) Color.Red else Color(0xFF4CAF50))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (temAlerta) "EM EMERGÊNCIA" else "Seguro")
                            }
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = if (temAlerta) Color.Red else MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            // BOTÃO DE LOCALIZAÇÃO QUE ABRE O MAPA INTERNO
                            IconButton(onClick = {
                                // Prioridade: Localização do alerta. Alternativa: Última conhecida do user.
                                val lat = alertaVinculado?.latitude ?: protegido.lastLatitude
                                val lon = alertaVinculado?.longitude ?: protegido.lastLongitude

                                if (lat != null && lon != null) {
                                    userParaMapa = Pair(protegido, LatLng(lat, lon))
                                } else {
                                    // Podes mostrar um Toast ou erro no ViewModel aqui
                                }
                            }) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = "Ver no Mapa",
                                    tint = if (temAlerta) Color.Red else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}