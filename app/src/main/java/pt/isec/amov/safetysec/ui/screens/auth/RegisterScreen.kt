package pt.isec.amov.safetysec.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.isec.amov.safetysec.viewmodel.AuthViewModel

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ){
        Text(
            text = "Criar Conta",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Campo Nome
        OutlinedTextField(
            value = viewModel.name,
            onValueChange = { viewModel.name = it },
            label = { Text("Nome Completo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Campo Email
        OutlinedTextField(
            value = viewModel.email,
            onValueChange = { viewModel.email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Campo Password
        OutlinedTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(), // Esconde a password
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Selecione o seu perfil:", style = MaterialTheme.typography.labelLarge)

        // Checkbox Monitor
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = viewModel.isMonitor,
                onCheckedChange = { viewModel.isMonitor = it }
            )
            Text(text = "Monitor (Cuidador)")
        }

        // Checkbox Protegido
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = viewModel.isProtected,
                onCheckedChange = { viewModel.isProtected = it }
            )
            Text(text = "Protegido (Utilizador)")
        }

        //parte do codigo para cancelar avisos
        if (viewModel.isProtected) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = viewModel.cancellationCode,
                onValueChange = {
                    // Impedir que escreva mais de 4-6 números
                    if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                        viewModel.cancellationCode = it
                    }
                },
                label = { Text("Código Cancelamento (PIN)") },
                placeholder = { Text("Ex: 1234") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword, //meter p aparecer o numpad
                    imeAction = ImeAction.Done
                ),
                visualTransformation = PasswordVisualTransformation()
            )
            Text(
                text = "* Necessário para cancelar alertas falsos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Mensagem de Erro
        if (viewModel.errorMessage != null) {
            Text(
                text = viewModel.errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Botão de Registar
        if (viewModel.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { viewModel.onRegisterClick(onRegisterSuccess) },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Criar Conta")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Voltar ao Login
        TextButton(onClick = onNavigateBack) {
            Text("Já tem conta? Voltar ao Login")
        }
    }
}