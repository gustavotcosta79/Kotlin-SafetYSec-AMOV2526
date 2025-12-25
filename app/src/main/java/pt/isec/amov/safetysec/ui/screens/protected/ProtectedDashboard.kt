package pt.isec.amov.safetysec.ui.screens.protected

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView // NOVO IMPORT
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView // NOVO IMPORT
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.isec.amov.safetysec.data.repository.FirestoreRepository
import pt.isec.amov.safetysec.managers.CameraManager // NOVO IMPORT
import pt.isec.amov.safetysec.managers.LocationManager
import pt.isec.amov.safetysec.managers.SensorManager
import pt.isec.amov.safetysec.viewmodel.AuthViewModel
import pt.isec.amov.safetysec.viewmodel.ProtegidoViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun ProtectedDashboard(
    authViewModel: AuthViewModel,
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val context = LocalContext.current
    val user = authViewModel.currentUser

    val protegidoViewModel: ProtegidoViewModel = viewModel(
        factory = ProtegidoViewModel.ProtegidoViewModelFactory(
            LocationManager(context),
            SensorManager(context),
            FirestoreRepository()
        )
    )

    // --- ESTADOS DA UI ---
    var showCancelDialog by remember { mutableStateOf(false) } // Cancelar Pânico
    var showMonitorsDialog by remember { mutableStateOf(false) } // Gerir Monitores
    var showHistoryDialog by remember { mutableStateOf(false) } // Histórico de Alertas
    var pinInput by remember { mutableStateOf("") }

    // --- CÂMARA E VIDEO (NOVOS ESTADOS) ---
    // Inicializamos o CameraManager aqui
    val cameraManager = remember { CameraManager(context) }
    var showCameraPreview by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var isCameraReady by remember { mutableStateOf(false) }

    // Estado para saber qual monitor remover (para confirmação)
    var monitorParaRemover by remember { mutableStateOf<pt.isec.amov.safetysec.data.model.User?>(null) }

    // --- ESTADOS DO VIEWMODEL ---
    val isCounting = protegidoViewModel.isCountingDown
    val isAlertSent = protegidoViewModel.activeAlertId != null
    val isInCancelMode = isCounting || isAlertSent

    // --- CARREGAR DADOS INICIAIS E MONITORIZAÇÃO ---
    LaunchedEffect(user) {
        if (user != null) {
            // 1. Carregar dados do Firestore
            protegidoViewModel.startObservingRules(user.id)
            authViewModel.fetchAssociatedMonitors()

            // 2. Ligar o Motor de Monitorização (GPS + Sensores)
            protegidoViewModel.startAutomaticMonitoring(user)
            protegidoViewModel.startSensorMonitoring(user)
        }
    }

    // --- PERMISSÕES (ATUALIZADO COM A TUA LÓGICA) ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        // 1. Definimos tudo o que a app precisa
        val permissionsNeeded = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,       // NOVO
            Manifest.permission.RECORD_AUDIO  // NOVO
        )

        // 2. Filtramos apenas as que AINDA NÃO TEMOS (Mantendo a tua lógica inteligente)
        val permissionsToRequest = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        // 3. Se a lista de "faltas" não estiver vazia, pedimos ao utilizador
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    //Gatilho para ABRIR A CÂMARA (apenas visualização)
    LaunchedEffect(isAlertSent, isCounting) {
        if (isAlertSent && !isCounting && !showCameraPreview && !isUploading) {
            showCameraPreview = true
            isCameraReady = false // Reset ao estado
        }
    }

    // Gatilho para COMEÇAR A GRAVAR (só quando a câmara estiver pronta)
    LaunchedEffect(showCameraPreview, isCameraReady) {
        if (showCameraPreview && isCameraReady) { // <--- Só entra aqui se estiver pronta

            // Pequeno delay de segurança para garantir que o preview já tem imagem
            kotlinx.coroutines.delay(500)

            cameraManager.recordVideo { videoFile ->
                showCameraPreview = false
                isUploading = true
                protegidoViewModel.uploadAndLinkVideo(videoFile, cameraManager) {
                    isUploading = false
                }
            }
        }
    }

    // --- ESTRUTURA PRINCIPAL ---
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

            // --- CABEÇALHO ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Botão Perfil
                IconButton(onClick = onNavigateToProfile) { Icon(Icons.Default.AccountCircle, "Perfil") }

                // Botão Gerir Monitores
                IconButton(onClick = { authViewModel.fetchAssociatedMonitors(); showMonitorsDialog = true }) { Icon(Icons.Default.Group, "Monitores") }

                // Botão Histórico
                IconButton(onClick = { if (user != null) { protegidoViewModel.fetchAlertHistory(user.id); showHistoryDialog = true } }) { Icon(Icons.Default.History, "Histórico") }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { authViewModel.onLogoutClick { onLogout() } }) { Text("Sair") }
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
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
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

            // LISTA DE REGRAS
            Text("Regras de Segurança", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start).padding(top = 16.dp))

            if (protegidoViewModel.rules.isEmpty()) {
                Text("Nenhuma regra definida.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
            }

            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
                items(protegidoViewModel.rules) { regra ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = if (regra.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(regra.type.name, fontWeight = FontWeight.Bold)
                                Text(regra.description, style = MaterialTheme.typography.bodyMedium)
                                if (regra.valueDouble != null) Text("Valor: ${regra.valueDouble}", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = regra.isActive, onCheckedChange = { isChecked -> protegidoViewModel.toggleRule(regra.id, isChecked) })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // BOTÃO DE PÂNICO
            Button(
                onClick = {
                    if (isInCancelMode) {
                        pinInput = ""; showCancelDialog = true
                    } else {
                        if (user != null) protegidoViewModel.startPanicProcess(user)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = when { isCounting -> Color(0xFFFF9800); isAlertSent -> Color(0xFFD32F2F); else -> Color.Red }),
                enabled = !protegidoViewModel.isLoading
            ) {
                if (protegidoViewModel.isLoading) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(32.dp))
                        Text(
                            text = when { isCounting -> "A ENVIAR EM ${protegidoViewModel.countdownValue}s..."; isAlertSent -> "CANCELAR ALERTA"; else -> "BOTÃO DE PÂNICO" },
                            fontSize = if (isCounting) 24.sp else 22.sp, fontWeight = FontWeight.Black
                        )
                        if (isInCancelMode) Text(if (isCounting) "Toque para abortar" else "Modo SOS Ativo", fontSize = 14.sp)
                    }
                }
            }
        }

        // =========================================================
        // DIÁLOGOS EXISTENTES (Monitores, Remover, Histórico, PIN)
        // =========================================================

        if (showMonitorsDialog) {
            AlertDialog(
                onDismissRequest = { showMonitorsDialog = false },
                title = { Text("Meus Monitores") },
                text = {
                    if (authViewModel.associatedMonitors.isEmpty()) Text("Não está associado a nenhum monitor.")
                    else LazyColumn { items(authViewModel.associatedMonitors) { monitor -> ListItem(leadingContent = { Icon(Icons.Default.Person, null) }, headlineContent = { Text(monitor.name) }, trailingContent = { IconButton(onClick = { monitorParaRemover = monitor }) { Icon(Icons.Default.Delete, "Remover", tint = MaterialTheme.colorScheme.error) } }); HorizontalDivider() } }
                },
                confirmButton = { TextButton(onClick = { showMonitorsDialog = false }) { Text("Fechar") } }
            )
        }

        if (monitorParaRemover != null) {
            AlertDialog(
                onDismissRequest = { monitorParaRemover = null },
                title = { Text("Remover associação?") },
                text = { Text("Tem a certeza que deseja se desassociar de ${monitorParaRemover?.name}?") },
                confirmButton = { Button(onClick = { authViewModel.disassociateMonitor(monitorParaRemover!!.id); monitorParaRemover = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Desassociar") } },
                dismissButton = { TextButton(onClick = { monitorParaRemover = null }) { Text("Cancelar") } }
            )
        }

        if (showHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showHistoryDialog = false },
                title = { Text("Histórico de Alertas") },
                text = {
                    if (protegidoViewModel.alertHistory.isEmpty()) Text("Ainda não existem alertas registados.")
                    else LazyColumn { items(protegidoViewModel.alertHistory) { alert ->
                        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        val dataFormatada = try { dateFormat.format(alert.date) } catch (e: Exception) { "Data desconhecida" }
                        val estado = when { alert.cancelled -> "Cancelado"; alert.solved -> "Resolvido"; else -> "Ativo / Pendente" }
                        val corEstado = when { alert.cancelled -> Color.Gray; alert.solved -> Color(0xFF2E7D32); else -> Color.Red }
                        val icon = when { alert.cancelled -> Icons.Default.Close; alert.solved -> Icons.Default.CheckCircle; else -> Icons.Default.Warning }
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            ListItem(headlineContent = { Text(alert.type.name, fontWeight = FontWeight.Bold) }, supportingContent = { Column { Text("Data: $dataFormatada"); Text(estado, color = corEstado, style = MaterialTheme.typography.bodySmall) } }, leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) })
                        }
                        HorizontalDivider()
                    }}
                },
                confirmButton = { TextButton(onClick = { showHistoryDialog = false }) { Text("Fechar") } }
            )
        }

        if (showCancelDialog) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).zIndex(2f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showCancelDialog = false }, contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.fillMaxWidth(0.9f).clickable(enabled = false) {}, colors = CardDefaults.cardColors(containerColor = Color(0xFFEEEEEE)), elevation = CardDefaults.cardElevation(8.dp)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Cancelar Emergência", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.Black)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Introduza o seu PIN para cancelar.", color = Color.Black)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(value = pinInput, onValueChange = { if (it.length <= 6) pinInput = it }, label = { Text("PIN") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedTextColor = Color.Black, unfocusedTextColor = Color.Black), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showCancelDialog = false }) { Text("Voltar") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { if (user != null) { protegidoViewModel.handleCancelRequest(pinInput, user.cancellationCode); showCancelDialog = false } }) { Text("Confirmar") }
                        }
                    }
                }
            }
        }

        // =========================================================
        // 6. CÂMARA OVERLAY (Aparece automaticamente) - NOVO
        // =========================================================
        if (showCameraPreview) {
            val lifecycleOwner = LocalLifecycleOwner.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .zIndex(3f) // Fica por cima de tudo
            ) {
                // Componente que mostra a imagem da câmara
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        cameraManager.startCamera(
                            lifecycleOwner = lifecycleOwner,
                            surfaceProvider = previewView.surfaceProvider,
                            onCameraReady = {
                                isCameraReady = true
                            }
                        )
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Interface sobreposta (Botão Parar)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "A GRAVAR PROVAS...",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { cameraManager.stopRecording() }, // Parar manual (o automático também funciona a 30s)
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Parar e Enviar Agora")
                    }
                }
            }
        }

        // =========================================================
        // 7. LOADING OVERLAY (Enquanto faz upload) - NOVO
        // =========================================================
        if (isUploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha=0.85f))
                    .zIndex(4f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("A enviar vídeo para o Monitor...", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Por favor aguarde.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}