package pt.isec.amov.safetysec.ui.screens.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import pt.isec.amov.safetysec.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.isec.amov.safetysec.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    var passwordVisible by remember { mutableStateOf(false) }

    // Controla se o diálogo de recuperação está visível
    var showRecoverDialog by remember { mutableStateOf(false) }
    var recoverEmail by remember { mutableStateOf("") }

    // --- ESTADOS PARA O MFA (Simulação) ---
    var mfaInput by remember { mutableStateOf("") }
    var mfaError by remember { mutableStateOf(false) }

    // DIÁLOGO DE MFA (Aparece quando o Login está correto)
    if (viewModel.isMfaVisible) {
        AlertDialog(
            onDismissRequest = { /* Não fecha ao clicar fora */ },
            title = { Text("Verificação de Segurança") },
            text = {
                Column {
                    Text("Foi enviado um código de verificação para a consola do Android Studio (Simulação de Email).")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = mfaInput,
                        onValueChange = {
                            if (it.length <= 6) mfaInput = it
                            mfaError = false
                        },
                        label = { Text("Código de 6 dígitos") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = mfaError
                    )
                    if (mfaError) {
                        Text("Código incorreto", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (viewModel.verifyMfaCode(mfaInput)) {
                        // CÓDIGO CERTO -> ENTRA NA APP
                        viewModel.isMfaVisible = false // Fecha dialog
                        onLoginSuccess() // Navega
                    } else {
                        mfaError = true
                    }
                }) {
                    Text("Verificar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.cancelMfa()
                    mfaInput = ""
                }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // MENSAGEM DE SUCESSO (Ex: Email enviado)
        if (viewModel.successMessage != null) {
            Text(
                text = viewModel.successMessage!!,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Campo Email
        OutlinedTextField(
            value = viewModel.email,
            onValueChange = { viewModel.email = it },
            label = { Text(stringResource(R.string.email_label)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Campo Password
        OutlinedTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            label = { Text(stringResource(R.string.password_label)) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = "Toggle Password")
                }
            },
            singleLine = true
        )

        // Link "Esqueci-me da password"
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = stringResource(R.string.forgot_password_link),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable {
                        recoverEmail = viewModel.email // Pré-preenche se já tiver escrito algo
                        showRecoverDialog = true
                    }
                    .padding(vertical = 8.dp)
            )
        }

        // Mensagem de Erro
        if (viewModel.errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = viewModel.errorMessage!!,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Botão Login
        if (viewModel.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    // MUDANÇA: Em vez de ir direto, chama o MFA
                    viewModel.onLoginClick {
                        // Login Firebase OK -> Inicia o MFA
                        viewModel.startMfaProcess()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(stringResource(R.string.login_button))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botão para ir para o Registo
        TextButton(onClick = onNavigateToRegister) {
            Text(stringResource(R.string.no_account_link))
        }
    }

    // DIÁLOGO DE RECUPERAÇÃO DE PASSWORD
    if (showRecoverDialog) {
        AlertDialog(
            onDismissRequest = { showRecoverDialog = false },
            title = { Text(stringResource(R.string.recover_password_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.recover_password_desc))
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = recoverEmail,
                        onValueChange = { recoverEmail = it },
                        label = { Text(stringResource(R.string.email_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.recoverPassword(recoverEmail) {
                            showRecoverDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.btn_send))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoverDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}