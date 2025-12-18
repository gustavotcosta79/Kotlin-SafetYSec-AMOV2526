package pt.isec.amov.safetysec.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pt.isec.amov.safetysec.ui.screens.auth.LoginScreen
import pt.isec.amov.safetysec.ui.screens.auth.RegisterScreen
import pt.isec.amov.safetysec.ui.screens.MainDashboardScreen
import pt.isec.amov.safetysec.viewmodel.AuthViewModel
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()

    NavHost (
        navController = navController,
        startDestination = Screen.Login.route
    ){
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = authViewModel,
                onRegisterSuccess = { navController.popBackStack() },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // FICA APENAS COM ESTA ROTA
        composable(Screen.Dashboard.route) {
            MainDashboardScreen(authViewModel = authViewModel)
        }
    }
}