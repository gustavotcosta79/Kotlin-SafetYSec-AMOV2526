package pt.isec.amov.safetysec.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
                    // TODO: Aqui vamos decidir para onde ir (Monitor ou Protegido)
                    // Por enquanto, vamos assumir que vai para um ecrã temporário ou imprime no Log
                    println("Navegar para Dashboard!")
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

        //FALTA IMPLEMENTAR OS RESTANTES ECRÃS SEGUINDO A MESMA LÓGICA
    }
}