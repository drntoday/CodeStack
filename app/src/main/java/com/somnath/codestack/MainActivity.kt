package com.somnath.codestack

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.somnath.codestack.ui.components.DrawerContent
import com.somnath.codestack.ui.pages.DashboardPage
import com.somnath.codestack.ui.pages.SettingsPage
import com.somnath.codestack.ui.pages.TerminalPage
import com.somnath.codestack.ui.theme.CodeStackTheme
import com.somnath.codestack.ui.theme.DeepSlate
import com.somnath.codestack.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File

// 1. Defined outside to ensure they are ready before the UI starts
sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Terminal : Screen("terminal?isProjectMode={isProjectMode}") {
        fun createRoute(isProjectMode: Boolean = false): String = "terminal?isProjectMode=$isProjectMode"
    }
    data object Vault : Screen("vault")
    data object Settings : Screen("settings")
    data object Editor : Screen("editor/{fileName}") {
        fun createRoute(fileName: String) = "editor/$fileName"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            CodeStackTheme {
                // Initialize ViewModel with a 'try-catch' safety net inside the composable
                val mainViewModel: MainViewModel = viewModel()
                CodeStackApp(mainViewModel)
            }
        }
    }

    companion object {
        fun saveCodeToFile(context: Context, fileName: String, code: String) {
            try {
                val folder = File(context.getExternalFilesDir(null), "Projects")
                if (!folder.exists()) folder.mkdirs()
                File(folder, fileName).writeText(code)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeStackApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentRoute == Screen.Dashboard.route,
        drawerContent = {
            DrawerContent(
                navController = navController,
                drawerState = drawerState,
                onClose = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("CODESTACK", fontWeight = FontWeight.Black) },
                    navigationIcon = {
                        if (currentRoute == Screen.Dashboard.route) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = null, tint = Color.White)
                            }
                        } else {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate, titleContentColor = Color.White)
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize().background(DeepSlate)) {
                NavHost(
                    navController = navController, 
                    startDestination = Screen.Dashboard.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Screen.Dashboard.route) { DashboardPage(navController) }
                    composable(Screen.Settings.route) { SettingsPage(navController) }
                    composable(Screen.Vault.route) { VaultPage(navController) }
                    composable(
                        route = Screen.Terminal.route,
                        arguments = listOf(navArgument("isProjectMode") { type = NavType.BoolType; defaultValue = false })
                    ) { backStack ->
                        val isProject = backStack.arguments?.getBoolean("isProjectMode") ?: false
                        TerminalPage(navController, viewModel, isProject)
                    }
                    composable(
                        route = Screen.Editor.route,
                        arguments = listOf(navArgument("fileName") { type = NavType.StringType })
                    ) { backStack ->
                        val fileName = backStack.arguments?.getString("fileName") ?: ""
                        EditorPage(navController, fileName)
                    }
                }
            }
        }
    }
}

@Composable
fun VaultPage(navController: androidx.navigation.NavController) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("VAULT", color = Color.White)
    }
}

@Composable
fun EditorPage(navController: androidx.navigation.NavController, fileName: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("EDITOR: $fileName", color = Color.White)
    }
}
