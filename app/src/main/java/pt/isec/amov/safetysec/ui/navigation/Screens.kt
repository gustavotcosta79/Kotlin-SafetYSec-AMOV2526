package pt.isec.amov.safetysec.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Dashboard : Screen("dashboard")
    object MainApp : Screen("main_app")

    // Futuros Dashboards (para onde vamos se o login correr bem)
    object MonitorDashboard : Screen("monitor_dashboard")
    object ProtectedDashboard : Screen("protected_dashboard")
    object Selector : Screen("selector") // Caso o user seja ambos
 }