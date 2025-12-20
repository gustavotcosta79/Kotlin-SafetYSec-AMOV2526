package pt.isec.amov.safetysec.ui.screens.protected

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.isec.amov.safetysec.data.repository.FirestoreRepository
import pt.isec.amov.safetysec.managers.LocationManager
import pt.isec.amov.safetysec.viewmodel.AuthViewModel
import pt.isec.amov.safetysec.viewmodel.ProtegidoViewModel
import pt.isec.amov.safetysec.viewmodel.ProtegidoViewModelFactory



@Composable
fun ProtectedDashboard(
    authViewModel: AuthViewModel,
    onLogout: () -> Unit,onNavigateToProfile: () -> Unit
) {
    val context = LocalContext.current
    val user = authViewModel.currentUser




    // Factory do ViewModel
    val protegidoViewModel: ProtegidoViewModel = viewModel(
        factory = ProtegidoViewModelFactory(
            LocationManager(context),
            FirestoreRepository()
        )
    )

    // --- ESTADOS DA UI ---
    var showCancelDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }

    // --- ESTADOS DO VIEWMODEL (Lógica dos 10s) ---
    val isCounting = protegidoViewModel.isCountingDown
    val isAlertSent = protegidoViewModel.activeAlertId != null
    // Se estiver a contar OU se já enviou, o botão serve para cancelar
    val isInCancelMode = isCounting || isAlertSent

    // --- PERMISSÕES ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // 1. INICIAR A ESCUTA DAS REGRAS
    LaunchedEffect(user) {
        if (user != null) {
            protegidoViewModel.startObservingRules(user.id)
        }
    }


    // --- ESTRUTURA PRINCIPAL (BOX para o Overlay Manual) ---
    Box(modifier = Modifier.fillMaxSize()) {

        // 1. CAMADA DE FUNDO (A Aplicação Normal)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Área do Protegido", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                // Botão Perfil
                IconButton(onClick = onNavigateToProfile) {
                    Icon(Icons.Default.AccountCircle, "Perfil")
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { authViewModel.onLogoutClick { onLogout() } }) {
                    Text("Terminar Sessão")
                }
            }

            // MENSAGENS FEEDBACK
            if (protegidoViewModel.successMessage != null) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFD4EDDA))) {
                    Text(protegidoViewModel.successMessage!!, modifier = Modifier.padding(16.dp), color = Color(0xFF155724))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (protegidoViewModel.errorMessage != null) {
                Text(protegidoViewModel.errorMessage!!, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // OTP CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Associação com Monitor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    authViewModel.connectionCode?.let { code ->
                        Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.medium) {
                            Text(code, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { authViewModel.generateCode() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (authViewModel.connectionCode == null) "Gerar Novo Código" else "Gerar Outro Código")
                    }
                }
            }




            // =========================================================
            // NOVA SECÇÃO: GESTÃO DE REGRAS
            // =========================================================
            Text(
                text = "Minhas Regras de Segurança",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            if (protegidoViewModel.rules.isEmpty()) {
                Text(
                    "Nenhuma regra definida pelos monitores.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Usamos LazyColumn com weight para ocupar o espaço livre até ao botão de pânico
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                items(protegidoViewModel.rules) { regra ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        // Se estiver inativa, cor diferente para destacar que precisa de atenção?
                        colors = CardDefaults.cardColors(
                            containerColor = if (regra.isActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = regra.type.name, // Ou uma função para nome mais bonito
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = regra.description,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (regra.valueDouble != null) {
                                    Text(
                                        text = "Valor: ${regra.valueDouble}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            // SWITCH PARA ACEITAR / REVOGAR
                            Switch(
                                checked = regra.isActive,
                                onCheckedChange = { isChecked ->
                                    protegidoViewModel.toggleRule(regra.id, isChecked)
                                }
                            )
                        }
                    }
                }
            }


            Spacer(modifier = Modifier.weight(1f))

            // --- BOTÃO DE PÂNICO INTELIGENTE ---
            Button(
                onClick = {
                    if (isInCancelMode) {
                        // Se está a contar ou já enviou -> Abre diálogo para cancelar
                        pinInput = ""
                        showCancelDialog = true
                    } else {
                        // Se está normal -> Inicia a contagem de 10s
                        if (user != null) {
                            protegidoViewModel.startPanicProcess(user)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    // Laranja (Aviso 10s) | Vermelho Escuro (Enviado) | Vermelho Vivo (Normal)
                    containerColor = when {
                        isCounting -> Color(0xFFFF9800)
                        isAlertSent -> Color(0xFFD32F2F)
                        else -> Color.Red
                    }
                ),
                enabled = !protegidoViewModel.isLoading
            ) {
                if (protegidoViewModel.isLoading) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(32.dp))

                        // TEXTO DINÂMICO
                        Text(
                            text = when {
                                isCounting -> "A ENVIAR EM ${protegidoViewModel.countdownValue}s..."
                                isAlertSent -> "CANCELAR ALERTA"
                                else -> "BOTÃO DE PÂNICO"
                            },
                            fontSize = if (isCounting) 24.sp else 22.sp,
                            fontWeight = FontWeight.Black
                        )

                        if (isInCancelMode) {
                            Text(
                                text = if (isCounting) "Toque para abortar" else "Modo SOS Ativo",
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

        }

        // 2. CAMADA DE TOPO (O Overlay Manual do Diálogo)
        if (showCancelDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .zIndex(2f) // Garante prioridade visual
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showCancelDialog = false }, // Fechar ao clicar fora
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clickable(enabled = false) {}, // Impede clique no cartão de fechar
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEEEEEE)),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Cancelar Emergência", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.Black)
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Introduza o seu PIN para cancelar.", color = Color.Black)
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 6) pinInput = it },
                            label = { Text("PIN") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black
                            ),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showCancelDialog = false }) {
                                Text("Voltar")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (user != null) {
                                        // Chama a nova função inteligente do ViewModel
                                        protegidoViewModel.handleCancelRequest(pinInput, user.cancellationCode)
                                        showCancelDialog = false
                                    }
                                }
                            ) {
                                Text("Confirmar")
                            }
                        }
                    }
                }
            }
        }
    }
}