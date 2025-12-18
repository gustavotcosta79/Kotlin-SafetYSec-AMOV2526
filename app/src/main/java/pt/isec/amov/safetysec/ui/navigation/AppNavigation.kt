package pt.isec.amov.safetysec.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pt.isec.amov.safetysec.ui.screens.MainContainerScreen
import pt.isec.amov.safetysec.ui.screens.auth.LoginScreen
import pt.isec.amov.safetysec.ui.screens.auth.RegisterScreen
import pt.isec.amov.safetysec.viewmodel.AuthViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    val authViewModel: AuthViewModel = viewModel()

    NavHost (
        navController = navController,
        startDestination = Screen.Login.route
    ){
        //ecrã de login
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.MainApp.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        //ecrã de registo
        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = authViewModel,
                onRegisterSuccess = {
                    // voltamos ao login para ele entrar com as credencias que acabou de criar
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack() // Volta para trás (Login)
                }
            )
        }

        // --- 3. APLICAÇÃO PRINCIPAL (Monitor e/ou Protegido) ---
        composable(Screen.MainApp.route) {
            // Obtemos o utilizador atual guardado no ViewModel
            val user = authViewModel.currentUser

            // Verificação de segurança: Só mostramos o ecrã se houver user logado
            if (user != null) {
                MainContainerScreen(
                    user = user,
                    onLogout = {
                        authViewModel.onLogoutClick {
                            // Ao fazer logout, volta ao ecrã de Login e limpa tudo
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.MainApp.route) { inclusive = true }
                            }
                        }
                    }
                )
            } else {
                // Se por acaso o user for null (erro raro ou refresh do processo),
                // manda o utilizador de volta para o login.
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.MainApp.route) { inclusive = true }
                    }
                }
            }
        }
    }
}