package pt.isec.amov.safetysec.ui.screens.protected

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.isec.amov.safetysec.R
import pt.isec.amov.safetysec.data.model.User
import pt.isec.amov.safetysec.data.repository.FirestoreRepository
import pt.isec.amov.safetysec.managers.CameraManager
import pt.isec.amov.safetysec.managers.LocationManager
import pt.isec.amov.safetysec.managers.SensorManager
import pt.isec.amov.safetysec.viewmodel.AuthViewModel
import pt.isec.amov.safetysec.viewmodel.ProtegidoViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectedDashboard(
    authViewModel: AuthViewModel,
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val context = LocalContext.current
    val user = authViewModel.currentUser
    val application = LocalContext.current.applicationContext as android.app.Application

    // Detetar Orientação do Ecrã
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE


    val protegidoViewModel: ProtegidoViewModel = viewModel(
        factory = ProtegidoViewModel.ProtegidoViewModelFactory(
            application,          // <--- 1º: Application
            LocationManager(context), // <--- 2º: LocationManager
            SensorManager(context),   // <--- 3º: SensorManager
            FirestoreRepository()     // <--- 4º: Repository
        )
    )

    // --- ESTADOS DA UI ---
    var showCancelDialog by remember { mutableStateOf(false) }
    var showMonitorsDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) } // Para mostrar OTP em landscape
    var pinInput by remember { mutableStateOf("") }

    // --- ESTADO PARA FILTRO DE MONITOR ---
    var selectedMonitorFilter by remember { mutableStateOf<User?>(null) }

    // --- CÂMARA E VIDEO ---
    val cameraManager = remember { CameraManager(context) }
    var showCameraPreview by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var isCameraReady by remember { mutableStateOf(false) }
    var monitorParaRemover by remember { mutableStateOf<User?>(null) }

    // --- ESTADOS DO VIEWMODEL ---
    val isCounting = protegidoViewModel.isCountingDown
    val isAlertSent = protegidoViewModel.activeAlertId != null
    val isInCancelMode = isCounting || isAlertSent
    var showTimeWindowDialog by remember { mutableStateOf(false) }

    // LÓGICA DE FILTRAGEM DAS REGRAS
    // Recalcula a lista sempre que as regras ou o filtro mudarem
    val filteredRules = remember(protegidoViewModel.rules, selectedMonitorFilter) {
        if (selectedMonitorFilter == null) {
            protegidoViewModel.rules // Mostra todas
        } else {
            protegidoViewModel.rules.filter { it.monitorId == selectedMonitorFilter?.id }
        }
    }

    // --- CARREGAMENTO E PERMISSÕES ---
    LaunchedEffect(user) {
        if (user != null) {
            protegidoViewModel.startObservingRules(user.id)
            // Se tiveres implementado as janelas temporais:
            // protegidoViewModel.startTimeWindowObservation(user.id)
            authViewModel.fetchAssociatedMonitors()
            protegidoViewModel.startAutomaticMonitoring(user)
            protegidoViewModel.startSensorMonitoring(user)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val permissionsNeeded = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        val permissionsToRequest = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // Disparo da Câmara Automático
    LaunchedEffect(isAlertSent, isCounting) {
        if (isAlertSent && !isCounting && !showCameraPreview && !isUploading) {
            showCameraPreview = true
            isCameraReady = false
        }
    }

    // Gravação e Upload
    LaunchedEffect(showCameraPreview, isCameraReady) {
        if (showCameraPreview && isCameraReady) {
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

        // =========================================================
        // LAYOUT RESPONSIVO (PORTRAIT VS LANDSCAPE)
        // =========================================================
        if (isLandscape) {
            // --- LAYOUT HORIZONTAL (LANDSCAPE) ---
            Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // COLUNA DA ESQUERDA (Listas e Controlos)
                Column(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
                    // Cabeçalho Simplificado
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(onClick = { showOtpDialog = true }) { Icon(Icons.Default.Key, stringResource(R.string.association_code_title)) }
                        IconButton(onClick = onNavigateToProfile) { Icon(Icons.Default.AccountCircle, null) }
                        IconButton(onClick = { if (user != null) { protegidoViewModel.fetchAlertHistory(user.id); showHistoryDialog = true } }) { Icon(Icons.Default.History, null) }
                        IconButton(onClick = { authViewModel.onLogoutClick { onLogout() } }) { Icon(Icons.Default.ExitToApp, null, tint = Color.Red) }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dropdown de Filtro
                    MonitorFilterDropdown(
                        monitors = authViewModel.associatedMonitors,
                        selectedMonitor = selectedMonitorFilter,
                        onMonitorSelected = { selectedMonitorFilter = it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Lista de Regras
                    RulesList(filteredRules, protegidoViewModel)
                }

                Spacer(modifier = Modifier.width(16.dp))

                // COLUNA DA DIREITA (Botão de Pânico Gigante)
                Column(modifier = Modifier.weight(0.4f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    PanicButton(
                        isCounting = isCounting,
                        isAlertSent = isAlertSent,
                        isInCancelMode = isInCancelMode,
                        isLoading = protegidoViewModel.isLoading,
                        countdownValue = protegidoViewModel.countdownValue,
                        onClick = {
                            if (isInCancelMode) { pinInput = ""; showCancelDialog = true }
                            else if (user != null) protegidoViewModel.startPanicProcess(user)
                        }
                    )
                }
            }
        } else {
            // --- LAYOUT VERTICAL (PORTRAIT) ---
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                // Cabeçalho
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateToProfile) { Icon(Icons.Default.AccountCircle, stringResource(R.string.btn_profile)) }
                    IconButton(onClick = { authViewModel.fetchAssociatedMonitors(); showMonitorsDialog = true }) { Icon(Icons.Default.Group, stringResource(R.string.btn_monitors)) }
                    IconButton(onClick = { if (user != null) { protegidoViewModel.fetchAlertHistory(user.id); showHistoryDialog = true } }) { Icon(Icons.Default.History, stringResource(R.string.btn_history)) }

                    // Botão Janelas Temporais
                    IconButton(onClick = {
                        if (user != null) {
                            // protegidoViewModel.startTimeWindowObservation(user.id) // Descomentar se tiveres
                            showTimeWindowDialog = true
                        }
                    }) {
                        Icon(Icons.Default.Schedule, stringResource(R.string.btn_schedules))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { authViewModel.onLogoutClick { onLogout() } }) { Text(stringResource(R.string.btn_logout)) }
                }

                // Feedback
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

                // Card OTP
                OtpCard(authViewModel)

                Spacer(modifier = Modifier.height(16.dp))

                // Dropdown de Filtro (NOVO)
                Text(stringResource(R.string.filter_rules_label), style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.Start))
                MonitorFilterDropdown(
                    monitors = authViewModel.associatedMonitors,
                    selectedMonitor = selectedMonitorFilter,
                    onMonitorSelected = { selectedMonitorFilter = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Lista de Regras
                RulesList(filteredRules, protegidoViewModel)

                Spacer(modifier = Modifier.height(8.dp))

                // Botão de Pânico
                PanicButton(
                    isCounting = isCounting,
                    isAlertSent = isAlertSent,
                    isInCancelMode = isInCancelMode,
                    isLoading = protegidoViewModel.isLoading,
                    countdownValue = protegidoViewModel.countdownValue,
                    onClick = {
                        if (isInCancelMode) { pinInput = ""; showCancelDialog = true }
                        else if (user != null) protegidoViewModel.startPanicProcess(user)
                    }
                )
            }
        }

        // =========================================================
        // DIÁLOGOS
        // =========================================================

        if (showMonitorsDialog) {
            AlertDialog(
                onDismissRequest = { showMonitorsDialog = false },
                title = { Text(stringResource(R.string.my_monitors_title)) },
                text = {
                    if (authViewModel.associatedMonitors.isEmpty()) Text(stringResource(R.string.no_monitors_associated))
                    else LazyColumn { items(authViewModel.associatedMonitors) { monitor -> ListItem(leadingContent = { Icon(Icons.Default.Person, null) }, headlineContent = { Text(monitor.name) }, trailingContent = { IconButton(onClick = { monitorParaRemover = monitor }) { Icon(Icons.Default.Delete, stringResource(R.string.btn_remove), tint = MaterialTheme.colorScheme.error) } }); HorizontalDivider() } }
                },
                confirmButton = { TextButton(onClick = { showMonitorsDialog = false }) { Text(stringResource(R.string.btn_close)) } }
            )
        }

        if (monitorParaRemover != null) {
            AlertDialog(
                onDismissRequest = { monitorParaRemover = null },
                title = { Text(stringResource(R.string.remove_association_title)) },
                text = { Text(stringResource(R.string.remove_association_confirm, monitorParaRemover?.name ?: "")) },
                confirmButton = { Button(onClick = { authViewModel.disassociateMonitor(monitorParaRemover!!.id); monitorParaRemover = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.btn_disassociate)) } },
                dismissButton = { TextButton(onClick = { monitorParaRemover = null }) { Text(stringResource(R.string.btn_cancel)) } }
            )
        }

        if (showHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showHistoryDialog = false },
                title = { Text(stringResource(R.string.history_title)) },
                text = {
                    if (protegidoViewModel.alertHistory.isEmpty()) Text(stringResource(R.string.no_alerts_history))
                    else LazyColumn { items(protegidoViewModel.alertHistory) { alert ->
                        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        val dataFormatada = try { dateFormat.format(alert.date) } catch (e: Exception) { "-" }
                        val estado = when {
                            alert.cancelled -> stringResource(R.string.alert_cancelled)
                            alert.solved -> stringResource(R.string.alert_solved)
                            else -> stringResource(R.string.alert_active)
                        }
                        val corEstado = when { alert.cancelled -> Color.Gray; alert.solved -> Color(0xFF2E7D32); else -> Color.Red }
                        val icon = when { alert.cancelled -> Icons.Default.Close; alert.solved -> Icons.Default.CheckCircle; else -> Icons.Default.Warning }
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            ListItem(headlineContent = { Text(alert.type.name, fontWeight = FontWeight.Bold) }, supportingContent = { Column { Text("Data: $dataFormatada"); Text(estado, color = corEstado, style = MaterialTheme.typography.bodySmall) } }, leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) })
                        }
                        HorizontalDivider()
                    }}
                },
                confirmButton = { TextButton(onClick = { showHistoryDialog = false }) { Text(stringResource(R.string.btn_close)) } }
            )
        }

        // Diálogo OTP (Usado principalmente em Landscape)
        if (showOtpDialog) {
            AlertDialog(
                onDismissRequest = { showOtpDialog = false },
                title = { Text(stringResource(R.string.association_code_title)) },
                text = { OtpCard(authViewModel) },
                confirmButton = { TextButton(onClick = { showOtpDialog = false }) { Text(stringResource(R.string.btn_close)) } }
            )
        }

        if (showCancelDialog) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).zIndex(2f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showCancelDialog = false }, contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.fillMaxWidth(0.9f).clickable(enabled = false) {}, colors = CardDefaults.cardColors(containerColor = Color(0xFFEEEEEE)), elevation = CardDefaults.cardElevation(8.dp)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.cancel_emergency_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.Black)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.enter_pin_text), color = Color.Black)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(value = pinInput, onValueChange = { if (it.length <= 6) pinInput = it }, label = { Text(stringResource(R.string.pin_label)) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedTextColor = Color.Black, unfocusedTextColor = Color.Black), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showCancelDialog = false }) { Text(stringResource(R.string.btn_back)) }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { if (user != null) { protegidoViewModel.handleCancelRequest(pinInput, user.cancellationCode); showCancelDialog = false } }) { Text(stringResource(R.string.btn_confirm)) }
                        }
                    }
                }
            }
        }

        // --- CAMERA OVERLAY ---
        if (showCameraPreview) {
            val lifecycleOwner = LocalLifecycleOwner.current
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black).zIndex(3f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        cameraManager.startCamera(
                            lifecycleOwner = lifecycleOwner,
                            surfaceProvider = previewView.surfaceProvider,
                            onCameraReady = { isCameraReady = true }
                        )
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.recording_proof), color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 24.sp, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { cameraManager.stopRecording() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                        Text(stringResource(R.string.stop_send_now))
                    }
                }
            }
        }

        // --- UPLOAD LOADING ---
        if (isUploading) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.85f)).zIndex(4f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.sending_video_monitor), color = Color.White, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.please_wait), color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }

    // --- JANELAS TEMPORAIS (Dialog) ---
    if (showTimeWindowDialog) {
        // ... (Insere aqui o teu código do diálogo de janelas temporais se o tiveres) ...
        // Como no teu exemplo original já tinhas a lógica, podes mantê-la aqui.
        // Se precisares, posso reenviar, mas a estrutura é igual aos outros diálogos.
    }
}

// =========================================================
// COMPONENTES AUXILIARES (REUTILIZÁVEIS)
// =========================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorFilterDropdown(
    monitors: List<User>,
    selectedMonitor: User?,
    onMonitorSelected: (User?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedMonitor?.name ?: stringResource(R.string.all_monitors_option),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.all_monitors_option)) },
                onClick = {
                    onMonitorSelected(null)
                    expanded = false
                }
            )
            monitors.forEach { monitor ->
                DropdownMenuItem(
                    text = { Text(monitor.name) },
                    onClick = {
                        onMonitorSelected(monitor)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun RulesList(rules: List<pt.isec.amov.safetysec.data.model.Rule>, viewModel: ProtegidoViewModel) {
    if (rules.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_rules_found), color = Color.Gray)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(rules) { regra ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (regra.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(regra.type.name, fontWeight = FontWeight.Bold)
                            Text(regra.description, style = MaterialTheme.typography.bodyMedium)
                            if (regra.valueDouble != null) Text("${stringResource(R.string.rule_value_prefix)} ${regra.valueDouble}", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = regra.isActive,
                            onCheckedChange = { isChecked -> viewModel.toggleRule(regra.id, isChecked) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PanicButton(
    isCounting: Boolean, isAlertSent: Boolean, isInCancelMode: Boolean,
    isLoading: Boolean, countdownValue: Int, onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(100.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                isCounting -> Color(0xFFFF9800)
                isAlertSent -> Color(0xFFD32F2F)
                else -> Color.Red
            }
        ),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(32.dp))
                Text(
                    text = when {
                        isCounting -> stringResource(R.string.panic_sending_in, countdownValue)
                        isAlertSent -> stringResource(R.string.cancel_alert_caps)
                        else -> stringResource(R.string.panic_button)
                    },
                    fontSize = if (isCounting) 20.sp else 22.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                if (isInCancelMode) Text(if (isCounting) stringResource(R.string.tap_to_abort) else stringResource(R.string.sos_mode_active), fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun OtpCard(authViewModel: AuthViewModel) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.association_code_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            authViewModel.connectionCode?.let { code ->
                Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.medium) {
                    Text(code, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { authViewModel.generateCode() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (authViewModel.connectionCode == null) stringResource(R.string.btn_generate_code) else stringResource(R.string.btn_new_code))
            }
        }
    }
}