package pt.isec.amov.safetysec.ui.screens.protected

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.isec.amov.safetysec.R // O R do teu projeto
import pt.isec.amov.safetysec.data.repository.FirestoreRepository
import pt.isec.amov.safetysec.managers.CameraManager
import pt.isec.amov.safetysec.managers.LocationManager
import pt.isec.amov.safetysec.managers.SensorManager
import pt.isec.amov.safetysec.viewmodel.AuthViewModel
import pt.isec.amov.safetysec.viewmodel.ProtegidoViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ProtectedDashboard(
    authViewModel: AuthViewModel,
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val context = LocalContext.current
    val user = authViewModel.currentUser
    val application = LocalContext.current.applicationContext as android.app.Application

    val protegidoViewModel: ProtegidoViewModel = viewModel(
        factory = ProtegidoViewModel.ProtegidoViewModelFactory(
            application,
            LocationManager(context),
            SensorManager(context),
            FirestoreRepository()
        )
    )

    // --- ESTADOS DA UI ---
    var showCancelDialog by remember { mutableStateOf(false) }
    var showMonitorsDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }

    // --- CÂMARA E VIDEO ---
    val cameraManager = remember { CameraManager(context) }
    var showCameraPreview by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var isCameraReady by remember { mutableStateOf(false) }

    var monitorParaRemover by remember { mutableStateOf<pt.isec.amov.safetysec.data.model.User?>(null) }

    // --- ESTADOS DO VIEWMODEL ---
    val isCounting = protegidoViewModel.isCountingDown
    val isAlertSent = protegidoViewModel.activeAlertId != null
    val isInCancelMode = isCounting || isAlertSent
    var showTimeWindowDialog by remember { mutableStateOf(false) }

    // --- CARREGAMENTO E PERMISSÕES ---
    LaunchedEffect(user) {
        if (user != null) {
            protegidoViewModel.startObservingRules(user.id)
            protegidoViewModel.startTimeWindowObservation(user.id) // Carregar janelas automaticamente
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

    LaunchedEffect(isAlertSent, isCounting) {
        if (isAlertSent && !isCounting && !showCameraPreview && !isUploading) {
            showCameraPreview = true
            isCameraReady = false
        }
    }

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Título Traduzido
            Text(stringResource(R.string.protected_area_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            // --- CABEÇALHO ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateToProfile) { Icon(Icons.Default.AccountCircle, stringResource(R.string.btn_profile)) }
                IconButton(onClick = { authViewModel.fetchAssociatedMonitors(); showMonitorsDialog = true }) { Icon(Icons.Default.Group, stringResource(R.string.btn_monitors)) }
                IconButton(onClick = { if (user != null) { protegidoViewModel.fetchAlertHistory(user.id); showHistoryDialog = true } }) { Icon(Icons.Default.History, stringResource(R.string.btn_history)) }
                IconButton(onClick = {
                    if (user != null) {
                        protegidoViewModel.startTimeWindowObservation(user.id)
                        showTimeWindowDialog = true
                    }
                }) {
                    Icon(Icons.Default.Schedule, stringResource(R.string.btn_schedules))
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { authViewModel.onLogoutClick { onLogout() } }) { Text(stringResource(R.string.btn_logout)) }
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
                    Text(stringResource(R.string.association_card_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    authViewModel.connectionCode?.let { code ->
                        Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.medium) {
                            Text(code, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { authViewModel.generateCode() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (authViewModel.connectionCode == null) stringResource(R.string.btn_generate_code) else stringResource(R.string.btn_generate_other_code))
                    }
                }
            }

            // LISTA DE REGRAS
            Text(stringResource(R.string.rules_list_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start).padding(top = 16.dp))

            if (protegidoViewModel.rules.isEmpty()) {
                Text(stringResource(R.string.no_rules_defined), style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
            }

            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
                items(protegidoViewModel.rules) { regra ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = if (regra.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(regra.type.name, fontWeight = FontWeight.Bold)
                                Text(regra.description, style = MaterialTheme.typography.bodyMedium)
                                if (regra.valueDouble != null) Text("${stringResource(R.string.rule_value_prefix)} ${regra.valueDouble}", style = MaterialTheme.typography.bodySmall)
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
                            text = when {
                                isCounting -> stringResource(R.string.sending_in, protegidoViewModel.countdownValue)
                                isAlertSent -> stringResource(R.string.cancel_alert_caps)
                                else -> stringResource(R.string.panic_button)
                            },
                            fontSize = if (isCounting) 24.sp else 22.sp, fontWeight = FontWeight.Black
                        )
                        if (isInCancelMode) Text(if (isCounting) stringResource(R.string.tap_to_abort) else stringResource(R.string.sos_mode_active), fontSize = 14.sp)
                    }
                }
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

        // =========================================================
        // CÂMARA OVERLAY
        // =========================================================
        if (showCameraPreview) {
            val lifecycleOwner = LocalLifecycleOwner.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .zIndex(3f)
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
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.recording_proof),
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { cameraManager.stopRecording() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text(stringResource(R.string.stop_send_now))
                    }
                }
            }
        }

        // =========================================================
        // LOADING OVERLAY
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
                    Text(stringResource(R.string.sending_video_monitor), color = Color.White, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.please_wait), color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }

    // =========================================================
    // DIÁLOGO DE HORÁRIOS
    // =========================================================
    if (showTimeWindowDialog) {
        var newName by remember { mutableStateOf("") }
        var startH by remember { mutableStateOf("09") }
        var endH by remember { mutableStateOf("18") }
        val days = remember { mutableStateListOf(false, true, true, true, true, true, false) }
        val dayNames = listOf("D", "S", "T", "Q", "Q", "S", "S")

        AlertDialog(
            onDismissRequest = { showTimeWindowDialog = false },
            title = { Text(stringResource(R.string.monitoring_windows_title)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.monitoring_windows_desc), fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                        items(protegidoViewModel.timeWindows) { window ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(window.name, fontWeight = FontWeight.Bold)
                                    Text("${window.startHour}:00 - ${window.endHour}:00", fontSize = 12.sp)
                                }
                                IconButton(onClick = { if (user != null) protegidoViewModel.deleteTimeWindow(user.id, window.id) }) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.btn_delete), tint = Color.Red)
                                }
                            }
                            HorizontalDivider()
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.add_new_schedule), fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.schedule_name_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        OutlinedTextField(value = startH, onValueChange = { startH = it }, label = { Text(stringResource(R.string.start_h)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(value = endH, onValueChange = { endH = it }, label = { Text(stringResource(R.string.end_h)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }

                    Text(stringResource(R.string.days_of_week), modifier = Modifier.padding(top=8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        dayNames.forEachIndexed { index, name ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(name, fontSize = 10.sp)
                                Checkbox(checked = days[index], onCheckedChange = { days[index] = it })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotEmpty() && user != null) {
                        val s = startH.toIntOrNull() ?: 9
                        val e = endH.toIntOrNull() ?: 18
                        val newWindow = pt.isec.amov.safetysec.data.model.TimeWindow(name = newName, startHour = s, endHour = e, activeDays = days.toList())
                        protegidoViewModel.addTimeWindow(user.id, newWindow)
                        newName = ""
                    }
                }) { Text(stringResource(R.string.btn_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimeWindowDialog = false }) { Text(stringResource(R.string.btn_close)) }
            }
        )
    }
}