package pt.isec.amov.safetysec.ui.screens.monitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import pt.isec.amov.safetysec.data.model.Rule
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
    var userParaRemover by remember { mutableStateOf<User?>(null) }

    // --- ESTADOS DE GESTÃO DE REGRAS ---
    var userParaGerirRegras by remember { mutableStateOf<User?>(null) } // Abre a lista de regras
    var showAddRuleDialog by remember { mutableStateOf(false) }         // Abre o form de criar
    var ruleToEdit by remember { mutableStateOf<Rule?>(null) }          // Abre o form de editar

    // Quando selecionamos um utilizador para gerir regras, carregamos as regras dele
    LaunchedEffect(userParaGerirRegras) {
        if (userParaGerirRegras != null) {
            monitorViewModel.fetchRulesForProtected(userParaGerirRegras!!.id)
        }
    }

    // =====================================================================
    // 1. DIÁLOGO PRINCIPAL: LISTA DE REGRAS DO UTILIZADOR
    // =====================================================================
    if (userParaGerirRegras != null && !showAddRuleDialog && ruleToEdit == null) {
        val targetUser = userParaGerirRegras!!

        AlertDialog(
            onDismissRequest = { userParaGerirRegras = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.8f),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Regras: ${targetUser.name}")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Botão Nova Regra
                    Button(
                        onClick = { showAddRuleDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Nova Regra")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (monitorViewModel.currentRules.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Sem regras definidas.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn {
                            items(monitorViewModel.currentRules) { rule ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(rule.type.name, fontWeight = FontWeight.Bold)
                                            // Ícones de Ação (Editar / Apagar)
                                            Row {
                                                IconButton(onClick = {
                                                    // Carrega dados para edição
                                                    monitorViewModel.ruleValueInput = rule.valueDouble?.toString() ?: ""
                                                    monitorViewModel.ruleDescription = rule.description
                                                    ruleToEdit = rule
                                                }) {
                                                    Icon(Icons.Default.Edit, "Editar", tint = MaterialTheme.colorScheme.primary)
                                                }
                                                IconButton(onClick = { monitorViewModel.deleteRule(rule.id, targetUser.id) }) {
                                                    Icon(Icons.Default.Delete, "Apagar", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                        Text("Valor: ${rule.valueDouble ?: "-"}", style = MaterialTheme.typography.bodyMedium)
                                        Text(rule.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                                        // Estado da Regra
                                        Text(
                                            if(rule.isActive) "Estado: ATIVA" else "Estado: INATIVA (A aguardar protegido)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if(rule.isActive) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { userParaGerirRegras = null }) { Text("Fechar") }
            }
        )
    }

    // =====================================================================
    // 2. DIÁLOGO: CRIAR NOVA REGRA
    // =====================================================================
    if (showAddRuleDialog) {
        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text("Nova Regra") },
            text = {
                Column {
                    Text("Tipo:", style = MaterialTheme.typography.labelMedium)
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
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = monitorViewModel.ruleValueInput,
                        onValueChange = { monitorViewModel.ruleValueInput = it },
                        label = { Text("Valor (Km/h, Minutos ou Raio)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = monitorViewModel.ruleDescription,
                        onValueChange = { monitorViewModel.ruleDescription = it },
                        label = { Text("Descrição") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    monitorViewModel.submitRule(
                        monitorId = authViewModel.currentUser?.id ?: "",
                        protectedId = userParaGerirRegras!!.id,
                        onFinished = { showAddRuleDialog = false }
                    )
                }) { Text("Criar") }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // =====================================================================
    // 3. DIÁLOGO: EDITAR REGRA EXISTENTE
    // =====================================================================
    if (ruleToEdit != null) {
        AlertDialog(
            onDismissRequest = { ruleToEdit = null },
            title = { Text("Editar Regra") },
            text = {
                Column {
                    Text("Tipo: ${ruleToEdit!!.type.name}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = monitorViewModel.ruleValueInput,
                        onValueChange = { monitorViewModel.ruleValueInput = it },
                        label = { Text("Novo Valor") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = monitorViewModel.ruleDescription,
                        onValueChange = { monitorViewModel.ruleDescription = it },
                        label = { Text("Nova Descrição") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    monitorViewModel.updateRule(
                        ruleId = ruleToEdit!!.id,
                        newValue = monitorViewModel.ruleValueInput,
                        newDesc = monitorViewModel.ruleDescription,
                        protectedId = userParaGerirRegras!!.id,
                        onFinished = { ruleToEdit = null }
                    )
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { ruleToEdit = null }) { Text("Cancelar") }
            }
        )
    }

    // =====================================================================
    // DIÁLOGOS DE MAPA E REMOÇÃO
    // =====================================================================
    if (userParaMapa != null && userParaMapa?.second != null) {
        AlertDialog(
            onDismissRequest = { userParaMapa = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxSize().padding(16.dp),
            confirmButton = { Button(onClick = { userParaMapa = null }) { Text("Fechar") } },
            text = { Box(modifier = Modifier.fillMaxSize()) { MapScreen(userParaMapa!!.second!!.latitude, userParaMapa!!.second!!.longitude, userParaMapa!!.first.name) } }
        )
    }

    if (userParaRemover != null) {
        AlertDialog(
            onDismissRequest = { userParaRemover = null },
            title = { Text("Remover Associação") },
            text = { Text("Tem a certeza que deseja desassociar ${userParaRemover?.name}? Esta ação não pode ser desfeita.") },
            confirmButton = {
                Button(
                    onClick = {
                        // Nota: Usa removeAssociation conforme definido no AuthViewModel anterior
                        authViewModel.disassociateProtected(userParaRemover!!.id)
                        userParaRemover = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Desassociar") }
            },
            dismissButton = { TextButton(onClick = { userParaRemover = null }) { Text("Cancelar") } }
        )
    }

    // =====================================================================
    // CONTEÚDO PRINCIPAL
    // =====================================================================
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // CABEÇALHO
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

        // ADICIONAR PROTEGIDO
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

        // LISTA PROTEGIDOS
        Text("Meus Protegidos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
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
                    // MUDANÇA IMPORTANTE: Clicar no cartão agora abre a lista de regras
                    onClick = { userParaGerirRegras = protegido }
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
                            Row {
                                // 1. Botão Mapa
                                IconButton(onClick = {
                                    val lat = alertaVinculado?.latitude ?: protegido.lastLatitude
                                    val lon = alertaVinculado?.longitude ?: protegido.lastLongitude
                                    if (lat != null && lon != null) {
                                        userParaMapa = Pair(protegido, LatLng(lat, lon))
                                    }
                                }) {
                                    Icon(Icons.Default.LocationOn, "Mapa", tint = if (temAlerta) Color.Red else MaterialTheme.colorScheme.outline)
                                }

                                // 2. Botão Remover (Lixo)
                                IconButton(onClick = { userParaRemover = protegido }) {
                                    Icon(Icons.Default.Delete, "Remover", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}