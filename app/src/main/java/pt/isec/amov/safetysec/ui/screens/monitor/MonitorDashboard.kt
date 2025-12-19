package pt.isec.amov.safetysec.ui.screens.monitor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pt.isec.amov.safetysec.viewmodel.AuthViewModel

//@Composable
//fun MonitorDashboard(viewModel: AuthViewModel) {
//    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
//        Text("Monitor Dashboard", style = MaterialTheme.typography.headlineMedium)
//
//        Spacer(modifier = Modifier.height(16.dp))
//
//        // Seção de Alertas Recentes [cite: 52]
//        Text("Recent Alerts", style = MaterialTheme.typography.titleMedium)
//        // Aqui usarias uma LazyColumn para listar os alertas vindos do FirestoreRepository
//
//        Spacer(modifier = Modifier.height(16.dp))
//
//        // Lista de Protegidos Associados [cite: 55]
//        Text("Protected Users", style = MaterialTheme.typography.titleMedium)
//
//        // Botão para associar novo Protegido via OTP [cite: 61, 18]
//        Button(onClick = { /* Abrir diálogo para inserir código OTP */ }) {
//            Text("Link New Protected User")
//        }
//    }
//}


@Composable
fun MonitorDashboard(viewModel: AuthViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Painel do Monitor", style = MaterialTheme.typography.headlineSmall)

        // MOSTRAR ERRO SE EXISTIR
        viewModel.errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // UI para introduzir o código OTP
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Adicionar Novo Protegido", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = viewModel.codeInput,
                    onValueChange = { viewModel.codeInput = it },
                    label = { Text("Introduza o código (ex: AJ39K2)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.onLinkSubmit { /* Aqui podes mostrar uma mensagem de sucesso */ } },
                    modifier = Modifier.align(Alignment.End),
                    enabled = !viewModel.isLoading
                ) {
                    Text("Associar")
                }
            }
        }

        Text("Meus Protegidos", style = MaterialTheme.typography.titleMedium)

        LazyColumn {
            items(viewModel.monitoredUsers) { protegido ->
                ListItem(
                    headlineContent = { Text(protegido.name) },
                    supportingContent = { Text(protegido.email) },
                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                    trailingContent = {
                        // Botão para ver mapa ou detalhes no futuro
                        IconButton(onClick = { /* Navegar para Detalhes */ }) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Ver Localização")
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}