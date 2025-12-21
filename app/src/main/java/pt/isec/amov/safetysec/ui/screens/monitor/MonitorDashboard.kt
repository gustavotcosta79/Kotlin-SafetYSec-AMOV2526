package pt.isec.amov.safetysec.ui.screens.monitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.LatLng
import pt.isec.amov.safetysec.data.model.RuleType
import pt.isec.amov.safetysec.data.model.User
import pt.isec.amov.safetysec.viewmodel.AuthViewModel
import pt.isec.amov.safetysec.viewmodel.MonitorViewModel

@Composable
fun MonitorDashboard(
    authViewModel: AuthViewModel,
    monitorViewModel: MonitorViewModel = viewModel(),
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    // Inicia a escuta de alertas em tempo real ao abrir o ecrã
    LaunchedEffect(Unit) {
        authViewModel.startObservingAlerts()
    }

    // --- ESTADOS LOCAIS ---
    var userParaMapa by remember { mutableStateOf<Pair<User, LatLng?>?>(null) }
    var userParaRegra by remember { mutableStateOf<User?>(null) }

    // NOVO: Estado para saber quem vamos apagar (se null, o diálogo fecha)
    var userParaRemover by remember { mutableStateOf<User?>(null) }

    // =====================================================================
    // DIÁLOGO DO MAPA
    // =====================================================================
    if (userParaMapa != null && userParaMapa?.second != null) {
        AlertDialog(
            onDismissRequest = { userParaMapa = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxSize().padding(16.dp),
            confirmButton = {
                Button(onClick = { userParaMapa = null }) { Text("Fechar") }
            },
            text = {
                Box(modifier = Modifier.fillMaxSize()) {
                    MapScreen(
                        latitude = userParaMapa!!.second!!.latitude,
                        longitude = userParaMapa!!.second!!.longitude,
                        title = userParaMapa!!.first.name
                    )
                }
            }
        )
    }

    // =====================================================================
    // DIÁLOGO PARA CRIAR REGRA
    // =====================================================================
    if (userParaRegra != null) {
        val targetUser = userParaRegra!!

        AlertDialog(
            onDismissRequest = { userParaRegra = null },
            title = { Text("Propor Regra a ${targetUser.name}") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Escolha o tipo de regra:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        FilterChip(
                            selected = monitorViewModel.selectedRuleType == RuleType.CONTROLO_VELOCIDADE,
                            onClick = { monitorViewModel.selectedRuleType = RuleType.CONTROLO_VELOCIDADE },
                            label = { Text("Veloc.") }
                        )
                        FilterChip(
                            selected = monitorViewModel.selectedRuleType == RuleType.INATIVIDADE,
                            onClick = { monitorViewModel.selectedRuleType = RuleType.INATIVIDADE },
                            label = { Text("Inativ.") }
                        )
                        FilterChip(
                            selected = monitorViewModel.selectedRuleType == RuleType.GEOFENCING,
                            onClick = { monitorViewModel.selectedRuleType = RuleType.GEOFENCING },
                            label = { Text("Area") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = monitorViewModel.ruleValueInput,
                        onValueChange = { monitorViewModel.ruleValueInput = it },
                        label = { Text("Valor (Km/h, Minutos ou Raio)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = monitorViewModel.ruleDescription,
                        onValueChange = { monitorViewModel.ruleDescription = it },
                        label = { Text("Descrição curta") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val monitorId = authViewModel.currentUser?.id ?: ""
                        monitorViewModel.submitRule(
                            monitorId = monitorId,
                            protectedId = targetUser.id,
                            onFinished = { userParaRegra = null }
                        )
                    },
                    enabled = !monitorViewModel.isLoading
                ) {
                    Text(if (monitorViewModel.isLoading) "A guardar..." else "Propor Regra")
                }
            },
            dismissButton = {
                TextButton(onClick = { userParaRegra = null }) { Text("Cancelar") }
            }
        )
    }

    // =====================================================================
    //       DIÁLOGO DE CONFIRMAÇÃO DE REMOÇÃO
    // =====================================================================
    if (userParaRemover != null) {
        AlertDialog(
            onDismissRequest = { userParaRemover = null },
            title = { Text("Remover Associação") },
            text = { Text("Tem a certeza que deseja deixar de monitorizar ${userParaRemover?.name}? Esta ação não pode ser desfeita.") },
            confirmButton = {
                Button(
                    onClick = {
                        // Chama o ViewModel para apagar
                        authViewModel.removeAssociation(userParaRemover!!.id)
                        userParaRemover = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remover")
                }
            },
            dismissButton = {
                TextButton(onClick = { userParaRemover = null }) { Text("Cancelar") }
            }
        )
    }

    // =====================================================================
    // CONTEÚDO PRINCIPAL
    // =====================================================================
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // --- CABEÇALHO ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Painel do Monitor", style = MaterialTheme.typography.headlineSmall)
            Row {
                IconButton(onClick = onNavigateToProfile) {
                    Icon(Icons.Default.AccountCircle, "Perfil")
                }
                TextButton(onClick = { authViewModel.onLogoutClick { onLogout() } }) {
                    Text("Sair")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. SECÇÃO DE ADICIONAR PROTEGIDO
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
                    onClick = { authViewModel.onLinkSubmit { /* Sucesso */ } },
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
                val alertaVinculado = authViewModel.activeAlerts.find { it.userEmail == protegido.email }
                val temAlerta = alertaVinculado != null

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (temAlerta) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surface
                    ),
                    // Clique no cartão abre menu de regras (Opcional, mas útil)
                    onClick = { userParaRegra = protegido }
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
                            // AQUI ESTÁ A MUDANÇA: Uma Row com 2 Botões
                            Row {
                                // 1. Botão Mapa
                                IconButton(onClick = {
                                    val lat = alertaVinculado?.latitude ?: protegido.lastLatitude
                                    val lon = alertaVinculado?.longitude ?: protegido.lastLongitude
                                    if (lat != null && lon != null) {
                                        userParaMapa = Pair(protegido, LatLng(lat, lon))
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = "Mapa",
                                        tint = if (temAlerta) Color.Red else MaterialTheme.colorScheme.outline
                                    )
                                }

                                // 2. Botão Remover (Lixo)
                                IconButton(onClick = { userParaRemover = protegido }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remover",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}