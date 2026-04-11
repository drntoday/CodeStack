package com.somnath.codestack

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.somnath.codestack.ui.components.DrawerContent
import com.somnath.codestack.ui.pages.DashboardPage
import com.somnath.codestack.ui.pages.SettingsPage
import com.somnath.codestack.ui.pages.TerminalPage
import com.somnath.codestack.ui.theme.CodeStackTheme
import com.somnath.codestack.ui.theme.DeepSlate
import com.somnath.codestack.ui.theme.Violet
import com.somnath.codestack.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Terminal : Screen("terminal?isProjectMode={isProjectMode}") {
        fun createRoute(isProjectMode: Boolean = false): String {
            return "terminal?isProjectMode=$isProjectMode"
        }
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
                CodeStackApp()
            }
        }
    }

    companion object {
        fun saveCodeToFile(context: Context, fileName: String, code: String) {
            try {
                val folder = File(context.getExternalFilesDir(null), "Projects")
                if (!folder.exists()) folder.mkdirs()
                val file = File(folder, fileName)
                file.writeText(code)
                Toast.makeText(context, "DEPLOYED TO VAULT: $fileName", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "DEPLOYMENT ERROR: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeStackApp() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryFlow.collectAsState(initial = null)
    val currentRoute = navBackStackEntry?.destination?.route

    val isDashboard = currentRoute == Screen.Dashboard.route
    val showBottomBar = currentRoute in listOf(
        Screen.Dashboard.route,
        Screen.Terminal.route,
        Screen.Vault.route
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
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
                when {
                    isDashboard -> {
                        TopAppBar(
                            title = {
                                Text(
                                    "CODESTACK",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 2.sp
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
                        )
                    }
                    currentRoute?.contains("editor") == true -> {
                        val fileName = navBackStackEntry?.arguments?.getString("fileName") ?: "Unknown"
                        TopAppBar(
                            title = {
                                Column {
                                    Text("EDITOR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(fileName, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { navController.navigateUp() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
                        )
                    }
                    else -> {
                        val title = when (currentRoute) {
                            Screen.Terminal.route -> "TERMINAL"
                            Screen.Vault.route -> "QUANTUM VAULT"
                            Screen.Settings.route -> "SETTINGS"
                            else -> "CODESTACK"
                        }
                        TopAppBar(
                            title = { Text(title, fontWeight = FontWeight.Bold, color = Color.White) },
                            navigationIcon = {
                                IconButton(onClick = { navController.navigateUp() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
                        )
                    }
                }
            },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = DeepSlate,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        NavigationBarItem(
                            selected = isDashboard,
                            onClick = { navController.navigate(Screen.Dashboard.route) { popUpTo(Screen.Dashboard.route) } },
                            label = { Text("DASHBOARD") },
                            icon = { Icon(Icons.Default.Menu, contentDescription = null) }
                        )
                        NavigationBarItem(
                            selected = currentRoute == Screen.Terminal.route,
                            onClick = { navController.navigate(Screen.Terminal.createRoute(false)) },
                            label = { Text("TERMINAL") },
                            icon = { Icon(Icons.Default.Chat, contentDescription = null) }
                        )
                        NavigationBarItem(
                            selected = currentRoute == Screen.Vault.route,
                            onClick = { navController.navigate(Screen.Vault.route) },
                            label = { Text("VAULT") },
                            icon = { Icon(Icons.Default.Code, contentDescription = null) }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize().background(DeepSlate)) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Dashboard.route
                ) {
                    composable(Screen.Dashboard.route) {
                        DashboardPage(navController)
                    }
                    composable(
                        route = Screen.Terminal.route,
                        arguments = listOf(
                            navArgument("isProjectMode") {
                                type = NavType.BoolType
                                defaultValue = false
                            }
                        )
                    ) { backStackEntry ->
                        val isProjectMode = backStackEntry.arguments?.getBoolean("isProjectMode") ?: false
                        val context = LocalContext.current
                        val viewModel: MainViewModel = viewModel()
                        TerminalPage(navController, viewModel, isProjectMode)
                    }
                    composable(Screen.Vault.route) {
                        VaultPage(navController)
                    }
                    composable(
                        route = Screen.Editor.route,
                        arguments = listOf(navArgument("fileName") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val fileName = backStackEntry.arguments?.getString("fileName") ?: ""
                        EditorPage(navController, fileName)
                    }
                    composable(Screen.Settings.route) {
                        SettingsPage(navController)
                    }
                }
            }
        }
    }
}

@Composable
fun VaultPage(navController: NavController) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("VAULT CONTENT", color = Color.White)
    }
}

@Composable
fun EditorPage(navController: NavController, fileName: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("EDITING: $fileName", color = Color.White)
    }
}
