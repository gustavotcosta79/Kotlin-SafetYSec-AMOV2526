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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.LatLng
import pt.isec.amov.safetysec.R 
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
    LaunchedEffect(Unit) {
        authViewModel.startObservingAlerts()
    }

    // --- ESTADOS LOCAIS ---
    var userParaMapa by remember { mutableStateOf<Pair<User, LatLng?>?>(null) }
    var userParaRemover by remember { mutableStateOf<User?>(null) }

    // --- ESTADOS DE GESTÃO DE REGRAS ---
    var userParaGerirRegras by remember { mutableStateOf<User?>(null) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var ruleToEdit by remember { mutableStateOf<Rule?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userParaGerirRegras) {
        if (userParaGerirRegras != null) {
            // 1. Obtemos o ID do monitor que está logado
            val currentMonitorId = authViewModel.currentUser?.id ?: ""
            // 2. Passamos o ID do protegido E o ID do monitor
            if (currentMonitorId.isNotBlank()) {
                monitorViewModel.fetchRulesForProtected(userParaGerirRegras!!.id, currentMonitorId)
            }
        }
    }

    // =====================================================================
    // DIÁLOGO: HISTÓRICO GLOBAL
    // =====================================================================
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text(stringResource(R.string.history_all_title)) },
            text = {
                if (monitorViewModel.monitorAlertHistory.isEmpty()) {
                    Text(stringResource(R.string.no_alerts_monitor))
                } else {
                    LazyColumn {
                        items(monitorViewModel.monitorAlertHistory) { alert ->
                            val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                            val dataFormatada = try { dateFormat.format(alert.date) } catch (e: Exception) { "-" }

                            val nomeProtegido = authViewModel.monitoredUsers.find { it.id == alert.protectedId }?.name
                                ?: alert.userEmail

                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                ListItem(
                                    headlineContent = {
                                        // Nome + Tipo de Alerta
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
                                                    Text(stringResource(R.string.btn_view_video), fontSize = 12.sp)
                                                }
                                            }

                                            val estado = when {
                                                alert.cancelled -> stringResource(R.string.alert_cancelled)
                                                alert.solved -> stringResource(R.string.alert_solved)
                                                else -> stringResource(R.string.alert_active).uppercase()
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
                TextButton(onClick = { showHistoryDialog = false }) { Text(stringResource(R.string.btn_close)) }
            }
        )
    }

    // =====================================================================
    // DIÁLOGO: LISTA DE REGRAS
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
                    Text(stringResource(R.string.rules_user_title, targetUser.name))
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxSize()) {
                    Button(
                        onClick = { showAddRuleDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_new_rule))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (monitorViewModel.currentRules.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.no_rules_defined), color = Color.Gray)
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
                                            // Nome Traduzido do Tipo de Regra
                                            val nomeRegra = when(rule.type) {
                                                RuleType.CONTROLO_VELOCIDADE -> stringResource(R.string.rule_speed)
                                                RuleType.INATIVIDADE -> stringResource(R.string.rule_inactivity)
                                                RuleType.GEOFENCING -> stringResource(R.string.rule_geo)
                                                RuleType.QUEDA -> stringResource(R.string.rule_fall)
                                                RuleType.ACIDENTE -> stringResource(R.string.rule_accident)
                                                else -> rule.type.name
                                            }
                                            Text(nomeRegra, fontWeight = FontWeight.Bold)

                                            Row {
                                                IconButton(onClick = {
                                                    monitorViewModel.ruleValueInput = rule.valueDouble?.toString() ?: ""
                                                    monitorViewModel.ruleDescription = rule.description
                                                    ruleToEdit = rule
                                                }) {
                                                    Icon(Icons.Default.Edit, stringResource(R.string.btn_edit), tint = MaterialTheme.colorScheme.primary)
                                                }
                                                IconButton(onClick = {
                                                    // MUDANÇA: Adicionado o 3º argumento (ID do Monitor)
                                                    monitorViewModel.deleteRule(
                                                        rule.id,
                                                        targetUser.id,
                                                        authViewModel.currentUser?.id ?: ""
                                                    )
                                                }) {
                                                    Icon(Icons.Default.Delete, stringResource(R.string.btn_delete), tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                        Text("${stringResource(R.string.rule_value_prefix)} ${rule.valueDouble ?: "-"}", style = MaterialTheme.typography.bodyMedium)
                                        Text(rule.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                                        Text(
                                            if(rule.isActive) stringResource(R.string.rule_state_active) else stringResource(R.string.rule_state_inactive),
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
                TextButton(onClick = { userParaGerirRegras = null }) { Text(stringResource(R.string.btn_close)) }
            }
        )
    }

    // =====================================================================
    // DIÁLOGO: CRIAR NOVA REGRA
    // =====================================================================
    if (showAddRuleDialog) {
        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text(stringResource(R.string.new_rule_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.rule_type_label), style = MaterialTheme.typography.labelMedium)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = monitorViewModel.selectedRuleType == RuleType.CONTROLO_VELOCIDADE,
                            onClick = { monitorViewModel.selectedRuleType = RuleType.CONTROLO_VELOCIDADE },
                            label = { Text(stringResource(R.string.rule_speed)) }
                        )
                        FilterChip(
                            selected = monitorViewModel.selectedRuleType == RuleType.INATIVIDADE,
                            onClick = { monitorViewModel.selectedRuleType = RuleType.INATIVIDADE },
                            label = { Text(stringResource(R.string.rule_inactivity)) }
                        )
                        FilterChip(
                            selected = monitorViewModel.selectedRuleType == RuleType.GEOFENCING,
                            onClick = { monitorViewModel.selectedRuleType = RuleType.GEOFENCING },
                            label = { Text(stringResource(R.string.rule_geo)) }
                        )
                        FilterChip(
                            selected = monitorViewModel.selectedRuleType == RuleType.QUEDA,
                            onClick = { monitorViewModel.selectedRuleType = RuleType.QUEDA },
                            label = { Text(stringResource(R.string.rule_fall)) }
                        )
                        FilterChip(
                            selected = monitorViewModel.selectedRuleType == RuleType.ACIDENTE,
                            onClick = { monitorViewModel.selectedRuleType = RuleType.ACIDENTE },
                            label = { Text(stringResource(R.string.rule_accident)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val needsValue = when (monitorViewModel.selectedRuleType) {
                        RuleType.QUEDA, RuleType.ACIDENTE, RuleType.BOTAO_PANICO -> false
                        else -> true
                    }

                    OutlinedTextField(
                        value = if (needsValue) monitorViewModel.ruleValueInput else "N/A",
                        onValueChange = { if (needsValue) monitorViewModel.ruleValueInput = it },
                        label = {
                            Text(if (needsValue) stringResource(R.string.rule_value_hint) else stringResource(R.string.rule_no_param))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = needsValue,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledTextColor = Color.Gray
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = monitorViewModel.ruleDescription,
                        onValueChange = { monitorViewModel.ruleDescription = it },
                        label = { Text(stringResource(R.string.rule_desc_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (userParaGerirRegras != null) {
                        monitorViewModel.submitRule(
                            monitorId = authViewModel.currentUser?.id ?: "",
                            targetUser = userParaGerirRegras!!,
                            onFinished = { showAddRuleDialog = false }
                        )
                    }
                }) { Text(stringResource(R.string.btn_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    // =====================================================================
    // DIÁLOGO: EDITAR REGRA
    // =====================================================================
    if (ruleToEdit != null) {
        AlertDialog(
            onDismissRequest = { ruleToEdit = null },
            title = { Text(stringResource(R.string.edit_rule_title)) },
            text = {
                Column {
                    val nomeRegra = when(ruleToEdit!!.type) {
                        RuleType.CONTROLO_VELOCIDADE -> stringResource(R.string.rule_speed)
                        RuleType.INATIVIDADE -> stringResource(R.string.rule_inactivity)
                        RuleType.GEOFENCING -> stringResource(R.string.rule_geo)
                        RuleType.QUEDA -> stringResource(R.string.rule_fall)
                        RuleType.ACIDENTE -> stringResource(R.string.rule_accident)
                        else -> ruleToEdit!!.type.name
                    }
                    Text("${stringResource(R.string.rule_type_label)} $nomeRegra", fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = monitorViewModel.ruleValueInput,
                        onValueChange = { monitorViewModel.ruleValueInput = it },
                        label = { Text(stringResource(R.string.new_value_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = monitorViewModel.ruleDescription,
                        onValueChange = { monitorViewModel.ruleDescription = it },
                        label = { Text(stringResource(R.string.new_desc_label)) }
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

                        // MUDANÇA: Adicionada esta linha
                        monitorId = authViewModel.currentUser?.id ?: "",

                        onFinished = { ruleToEdit = null }
                    )
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { ruleToEdit = null }) { Text(stringResource(R.string.btn_cancel)) }
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
            confirmButton = { Button(onClick = { userParaMapa = null }) { Text(stringResource(R.string.btn_close)) } },
            text = { Box(modifier = Modifier.fillMaxSize()) { MapScreen(userParaMapa!!.second!!.latitude, userParaMapa!!.second!!.longitude, userParaMapa!!.first.name) } }
        )
    }

    if (userParaRemover != null) {
        AlertDialog(
            onDismissRequest = { userParaRemover = null },
            title = { Text(stringResource(R.string.remove_association_title)) },
            text = { Text(stringResource(R.string.remove_assoc_confirm_monitor, userParaRemover?.name ?: "")) },
            confirmButton = {
                Button(
                    onClick = {
                        authViewModel.disassociateProtected(userParaRemover!!.id)
                        userParaRemover = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.btn_disassociate)) }
            },
            dismissButton = { TextButton(onClick = { userParaRemover = null }) { Text(stringResource(R.string.btn_cancel)) } }
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
            Text(stringResource(R.string.monitor_panel_title), style = MaterialTheme.typography.headlineSmall)
            Row {
                IconButton(onClick = {
                    monitorViewModel.fetchMonitorAlertHistory(authViewModel.monitoredUsers)
                    showHistoryDialog = true
                }) {
                    Icon(Icons.Default.History, stringResource(R.string.recent_history))
                }
                IconButton(onClick = onNavigateToProfile) {
                    Icon(Icons.Default.AccountCircle, stringResource(R.string.btn_profile))
                }
                TextButton(onClick = { authViewModel.onLogoutClick { onLogout() } }) {
                    Text(stringResource(R.string.btn_logout))
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
                    label = { Text(stringResource(R.string.insert_otp_label)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { authViewModel.onLinkSubmit { /* Sucesso */ } },
                    enabled = !authViewModel.isLoading
                ) {
                    Text(stringResource(R.string.btn_link))
                }
            }
        }

        // LISTA PROTEGIDOS
        Text(stringResource(R.string.my_protected_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))

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
                    onClick = { userParaGerirRegras = protegido }
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(protegido.name, fontWeight = FontWeight.Bold) },
                        supportingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Badge(containerColor = if (temAlerta) Color.Red else Color(0xFF4CAF50))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (temAlerta) stringResource(R.string.status_emergency) else stringResource(R.string.status_safe))
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
                                IconButton(onClick = {
                                    val lat = alertaVinculado?.latitude ?: protegido.lastLatitude
                                    val lon = alertaVinculado?.longitude ?: protegido.lastLongitude
                                    if (lat != null && lon != null) {
                                        userParaMapa = Pair(protegido, LatLng(lat, lon))
                                    }
                                }) {
                                    Icon(Icons.Default.LocationOn, stringResource(R.string.btn_map), tint = if (temAlerta) Color.Red else MaterialTheme.colorScheme.outline)
                                }

                                IconButton(onClick = { userParaRemover = protegido }) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.btn_remove), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}