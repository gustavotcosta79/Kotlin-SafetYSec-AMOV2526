package pt.isec.amov.safetysec.ui.screens.protected

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
    onLogout: () -> Unit
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

    // ESTADOS
    var showCancelDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    val isInPanicMode = protegidoViewModel.activeAlertId != null

    // PERMISSÕES
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

    // --- ESTRUTURA PRINCIPAL (BOX para permitir sobreposição) ---
    // Usamos Box para podermos meter coisas "umas em cima das outras"
    Box(modifier = Modifier.fillMaxSize()) {

        // 1. O CONTEÚDO NORMAL DA APP (Fica por baixo)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Área do Protegido", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(24.dp))

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

            Spacer(modifier = Modifier.weight(1f))

            // BOTÃO DE PÂNICO
            Button(
                onClick = {
                    if (isInPanicMode) {
                        pinInput = ""
                        showCancelDialog = true // ISTO VAI ATIVAR O ITEM Nº 2 ABAIXO
                    } else {
                        if (user != null) protegidoViewModel.sendPanicAlert(user)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isInPanicMode) Color(0xFFFFA000) else MaterialTheme.colorScheme.error
                ),
                enabled = !protegidoViewModel.isLoading
            ) {
                if (protegidoViewModel.isLoading) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(32.dp))
                        Text(
                            text = if (isInPanicMode) "CANCELAR ALERTA" else "BOTÃO DE PÂNICO",
                            fontSize = 22.sp, fontWeight = FontWeight.Black
                        )
                        if (isInPanicMode) Text("Modo SOS Ativo", fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { authViewModel.onLogoutClick { onLogout() } }) {
                Text("Terminar Sessão")
            }
        }

        // 2. O OVERLAY MANUAL (DIÁLOGO FEITO À MÃO)
        // Este bloco só é desenhado se showCancelDialog for true.
        // Como está no fim da Box, fica POR CIMA de tudo (z-index maior).
        if (showCancelDialog) {
            // Fundo escuro transparente que ocupa o ecrã todo
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)) // Efeito de escurecer o fundo
                    .zIndex(2f) // Garante que fica no topo
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Clicar fora fecha o diálogo
                        showCancelDialog = false
                    },
                contentAlignment = Alignment.Center
            ) {
                // A "Janela" do diálogo (Cartão branco)
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f) // Ocupa 90% da largura
                        .clickable(enabled = false) {}, // Impede que cliques no cartão fechem o diálogo
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEEEEEE)),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Cancelar Emergência", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.Black)
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Introduza o seu PIN para cancelar o alerta.", color = Color.Black)
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
                                        protegidoViewModel.cancelPanicAlert(pinInput, user.cancellationCode)
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