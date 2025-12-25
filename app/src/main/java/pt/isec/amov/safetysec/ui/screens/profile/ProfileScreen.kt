package pt.isec.amov.safetysec.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pt.isec.amov.safetysec.R

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import pt.isec.amov.safetysec.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit
) {
    // Carrega os dados para os campos quando o ecrã abre
    LaunchedEffect(Unit) {
        viewModel.initializeEditState()
    }

    val user = viewModel.currentUser ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((stringResource(R.string.edit_profile_title))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, (stringResource(R.string.btn_back)))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            // Nome
            OutlinedTextField(
                value = viewModel.editName,
                onValueChange = { viewModel.editName = it },
                label = { Text(stringResource(R.string.name_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Código de Cancelamento (APENAS PROTEGIDO)
            if (user.isProtected) {
                OutlinedTextField(
                    value = viewModel.editCancellationCode,
                    onValueChange = { if (it.length <= 6) viewModel.editCancellationCode = it },
                    label = { Text(stringResource(R.string.cancellation_code_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Text(
                    (stringResource(R.string.pin_help_text)),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            Text((stringResource(R.string.change_pass_title)), style = MaterialTheme.typography.titleSmall)

            // Password
            OutlinedTextField(
                value = viewModel.editPassword,
                onValueChange = { viewModel.editPassword = it },
                label = { Text(stringResource(R.string.new_pass_label))},
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            OutlinedTextField(
                value = viewModel.editConfirmPassword,
                onValueChange = { viewModel.editConfirmPassword = it },
                label = { Text(stringResource(R.string.confirm_pass_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Mensagens Erro/Sucesso
            if (viewModel.errorMessage != null) {
                Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.error)
            }
            if (viewModel.profileUpdateSuccess) {
                Text(stringResource(R.string.msg_profile_success), color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.onSaveChangesClick() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isLoading
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.btn_save_changes))
                }
            }
        }
    }
}