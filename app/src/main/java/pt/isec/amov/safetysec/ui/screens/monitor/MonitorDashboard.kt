package pt.isec.amov.safetysec.ui.screens.monitor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.LatLng
import pt.isec.amov.safetysec.data.model.Rule
import pt.isec.amov.safetysec.data.model.RuleType
import pt.isec.amov.safetysec.data.model.User
import pt.isec.amov.safetysec.viewmodel.AuthViewModel
import pt.isec.amov.safetysec.viewmodel.MonitorViewModel
import java.text.SimpleDateFormat
import java.util.Locale

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

    var showHistoryDialog by remember { mutableStateOf(false) }

    // Quando selecionamos um utilizador para gerir regras, carregamos as regras dele
    LaunchedEffect(userParaGerirRegras) {
        if (userParaGerirRegras != null) {
            monitorViewModel.fetchRulesForProtected(userParaGerirRegras!!.id)
        }
    }

    // =====================================================================
    // DIÁLOGO: HISTÓRICO GLOBAL DE ALERTAS (NOVO)
    // =====================================================================
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("Histórico Recente (Todos)") },
            text = {
                if (monitorViewModel.monitorAlertHistory.isEmpty()) {
                    Text("Não existem alertas registados nos seus protegidos.")
                } else {
                    LazyColumn {
                        items(monitorViewModel.monitorAlertHistory) { alert ->
                            val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                            val dataFormatada = try { dateFormat.format(alert.date) } catch (e: Exception) { "-" }

                            // Tentar descobrir o NOME do protegido através do ID ou Email
                            val nomeProtegido = authViewModel.monitoredUsers.find { it.id == alert.protectedId }?.name
                                ?: alert.userEmail // Fallback para email se não encontrar nome

                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                ListItem(
                                    headlineContent = {
                                        // AQUI MOSTRAMOS O NOME DA PESSOA
                                        Text("$nomeProtegido: ${alert.type.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    },
                                    supportingContent = {
                                        Column {
                                            Text("Data: $dataFormatada")

                                            if (alert.videoUrl.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

                                                Button(
                                                    onClick = { uriHandler.openUri(alert.videoUrl) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                                    modifier = Modifier.height(35.dp)
                                                ) {
                                                    Icon(Icons.Default.PlayCircle, null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Ver Vídeo de Prova", fontSize = 12.sp)
                                                }
                                            }

                                            val estado = when {
                                                alert.cancelled -> "Cancelado"
                                                alert.solved -> "Resolvido"
                                                else -> "ATIVO"
                                            }
                                            val cor = if(!alert.cancelled && !alert.solved) Color.Red else Color.Gray
                                            Text(estado, color = cor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    leadingContent = {
                                        Icon(
                                            if (alert.type == RuleType.BOTAO_PANICO) Icons.Default.Warning else Icons.Default.Info,
                                            contentDescription = null,
                                            tint = if (!alert.cancelled && !alert.solved) Color.Red else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) { Text("Fechar") }
            }
        )
    }

    // =====================================================================
    // DIÁLOGO PRINCIPAL: LISTA DE REGRAS DO UTILIZADOR
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
    // DIÁLOGO: CRIAR NOVA REGRA
    // =====================================================================
    if (showAddRuleDialog) {
        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text("Nova Regra") },
            text = {
                Column {
                    Text("Tipo de Regra:", style = MaterialTheme.typography.labelMedium)

                    // 1. Adicionado scroll horizontal para caberem todas as opções
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()), // Permite deslizar
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Opções existentes
                        FilterChip(
                            selected = monitorViewModel.selectedRuleType == RuleType.CONTROLO_VELOCIDADE,
                            onClick = { monitorViewModel.selectedRuleType = RuleType.CONTROLO_VELOCIDADE },
                            label = { Text("Velocidade") }
                        )
                        FilterChip(
                            selected = monitorViewModel.selectedRuleType == RuleType.INATIVIDADE,
                            onClick = { monitorViewModel.selectedRuleType = RuleType.INATIVIDADE },
                            label = { Text("Inatividade") }
                        )
                        FilterChip(
                            selected = monitorViewModel.selectedRuleType == RuleType.GEOFENCING,
                            onClick = { monitorViewModel.selectedRuleType = RuleType.GEOFENCING },
                            label = { Text("Area/Geo") }
                        )
                        // 2. Novas opções (Queda e Acidente)
                        FilterChip(
                            selected = monitorViewModel.selectedRuleType == RuleType.QUEDA,
                            onClick = { monitorViewModel.selectedRuleType = RuleType.QUEDA },
                            label = { Text("Queda") }
                        )
                        FilterChip(
                            selected = monitorViewModel.selectedRuleType == RuleType.ACIDENTE,
                            onClick = { monitorViewModel.selectedRuleType = RuleType.ACIDENTE },
                            label = { Text("Acidente") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Lógica para saber se o campo Valor deve estar ativo
                    // Apenas Velocidade, Inatividade e Geofencing precisam de valor numérico
                    val needsValue = when (monitorViewModel.selectedRuleType) {
                        RuleType.QUEDA, RuleType.ACIDENTE, RuleType.BOTAO_PANICO -> false
                        else -> true
                    }

                    // 4. Campo Valor (Ativo ou Inativo)
                    OutlinedTextField(
                        value = if (needsValue) monitorViewModel.ruleValueInput else "N/A", // Mostra N/A se inativo
                        onValueChange = {
                            if (needsValue) monitorViewModel.ruleValueInput = it
                        },
                        label = {
                            Text(if (needsValue) "Valor (Km/h, Min, Raio)" else "Sem parâmetro necessário")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = needsValue, // <--- Aqui bloqueamos o campo
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledTextColor = Color.Gray
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Campo Descrição (Sempre ativo)
                    OutlinedTextField(
                        value = monitorViewModel.ruleDescription,
                        onValueChange = { monitorViewModel.ruleDescription = it },
                        label = { Text("Descrição (Opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    // Verificação de segurança
                    if (userParaGerirRegras != null) {
                        monitorViewModel.submitRule(
                            monitorId = authViewModel.currentUser?.id ?: "",
                            targetUser = userParaGerirRegras!!, // <--- MUDANÇA: Passamos o user objeto
                            onFinished = { showAddRuleDialog = false }
                        )
                    }
                }) { Text("Criar") }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) { Text("Cancelar") }
            }
        )
    }
    // =====================================================================
    // DIÁLOGO: EDITAR REGRA EXISTENTE
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

                IconButton(onClick = {
                    // Passamos a lista de users que o monitor tem associados para buscar os alertas deles
                    monitorViewModel.fetchMonitorAlertHistory(authViewModel.monitoredUsers)
                    showHistoryDialog = true
                }) {
                    Icon(Icons.Default.History, "Histórico Recente")
                }
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