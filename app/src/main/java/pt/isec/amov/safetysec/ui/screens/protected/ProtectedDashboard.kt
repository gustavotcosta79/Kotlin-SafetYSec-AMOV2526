package pt.isec.amov.safetysec.ui.screens.protected

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.isec.amov.safetysec.data.repository.FirestoreRepository
import pt.isec.amov.safetysec.managers.LocationManager
import pt.isec.amov.safetysec.viewmodel.AuthViewModel
import pt.isec.amov.safetysec.viewmodel.ProtegidoViewModel
import pt.isec.amov.safetysec.viewmodel.ProtegidoViewModelFactory

@Composable
fun ProtectedDashboard(
    authViewModel: AuthViewModel, // Usado para saber QUEM é o user e gerir o OTP
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val user = authViewModel.currentUser

    // 1. Instanciar o ViewModel do Protegido usando a Factory (Slide 80)
    // Isto permite injetar o LocationManager e o Repository
    val protegidoViewModel: ProtegidoViewModel = viewModel(
        factory = ProtegidoViewModelFactory(
            LocationManager(context),
            FirestoreRepository()
        )
    )

    // --- LÓGICA DE PERMISSÕES
    // 1. Criamos o "lançador" que vai receber a resposta do utilizador (Sim/Não)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Aqui vemos se o user aceitou
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (!granted) {
            // Opcional: Mostrar aviso que a app precisa disto para funcionar
        }
    }

    // 2. Quando o ecrã abre (LaunchedEffect), verificamos se já temos permissão
    LaunchedEffect(Unit) {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Se não tivermos permissão, PEDIMOS AGORA
        if (!hasFineLocation && !hasCoarseLocation) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Área do Protegido",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- MENSAGENS DE FEEDBACK DO PÂNICO ---
        if (protegidoViewModel.successMessage != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFD4EDDA))) {
                Text(
                    text = protegidoViewModel.successMessage!!,
                    modifier = Modifier.padding(16.dp),
                    color = Color(0xFF155724)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (protegidoViewModel.errorMessage != null) {
            Text(
                text = protegidoViewModel.errorMessage!!,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // --- SECÇÃO DO CÓDIGO DE ASSOCIAÇÃO (OTP) - Mantida no AuthViewModel ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Associação com Monitor",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Partilhe este código com o seu Monitor para ele o poder acompanhar.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Código Gerado
                authViewModel.connectionCode?.let { code ->
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = code,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            letterSpacing = 4.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Código ativo", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { authViewModel.generateCode() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !authViewModel.isLoading
                ) {
                    if (authViewModel.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text(if (authViewModel.connectionCode == null) "Gerar Novo Código" else "Gerar Outro Código")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // --- BOTÃO DE PÂNICO (Lógica no ProtegidoViewModel) ---
        Button(
            onClick = {
                if (user != null) {
                    protegidoViewModel.sendPanicAlert(user)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = MaterialTheme.shapes.large,
            enabled = !protegidoViewModel.isLoading // Evita cliques duplos
        ) {
            if (protegidoViewModel.isLoading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Text(
                    text = "BOTÃO DE PÂNICO",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { authViewModel.onLogoutClick {onLogout()} }) {
            Text("Terminar Sessão")
        }
    }
}