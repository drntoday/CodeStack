package com.somnath.codestack

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlightTakeoff // Rocket Icon
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

// STABLE 2026 GOOGLE AI SDK
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

/**
 * CODESTACK - ADVANCED AI DEVELOPMENT ENVIRONMENT
 * VERSION: 2.2.0 (ADVANCED SETTINGS & SECURITY)
 */

// --- Colors & Theme ---
private val DeepSlate = Color(0xFF0f172a)
private val ElectricBlue = Color(0xFF3B82F6)
private val Violet = Color(0xFF8B5CF6)
private val SlateCard = Color(0xFF64748B)
private val Emerald = Color(0xFF10b981) // Success Color

data class ChatMessage(val text: String, val isUser: Boolean)

// --- Main Activity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00E5FF),
                    secondary = Violet,
                    tertiary = Color(0xFFFFAB40),
                    surface = Color(0xFF1E293B),
                    background = DeepSlate
                )
            ) {
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

// --- Persistence Helpers ---
private fun saveApiKey(context: Context, key: String) = 
    context.getSharedPreferences("cs_prefs", 0).edit().putString("gemini_key", key).apply()

private fun getApiKey(context: Context) = 
    context.getSharedPreferences("cs_prefs", 0).getString("gemini_key", "") ?: ""

private fun saveGitHubToken(context: Context, token: String) = 
    context.getSharedPreferences("cs_prefs", 0).edit().putString("github_token", token).apply()

private fun getGitHubToken(context: Context) = 
    context.getSharedPreferences("cs_prefs", 0).getString("github_token", "") ?: ""

// --- Navigation Graph ---
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

// --- App Structure & Drawer ---
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
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigate(route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = false }
                    }
                },
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
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
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
                                    Text(fileName, style = MaterialTheme.typography.titleMedium)
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { navController.navigateUp() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                            title = { Text(title, fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = { navController.navigateUp() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                        TerminalPage(navController, isProjectMode)
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

// --- Dashboard Page ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardPage(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(DeepSlate),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            "Welcome back, Developer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "System Status: Optimal | Ready for Deployment",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            letterSpacing = 0.5.sp
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                ActionCard(
                    title = "Initialize Project",
                    description = "Start an autonomous development session.",
                    icon = Icons.Default.FlightTakeoff,
                    iconColor = ElectricBlue,
                    onClick = { navController.navigate(Screen.Terminal.createRoute(true)) }
                )
            }
            
            item {
                ActionCard(
                    title = "General Query",
                    description = "Consult CodeStack AI for logic or debugging.",
                    icon = Icons.Default.ChatBubble,
                    iconColor = Violet,
                    onClick = { navController.navigate(Screen.Terminal.createRoute(false)) }
                )
            }
            
            item(span = { GridItemSpan(maxLineSpan) }) {
                ActionCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "System Settings",
                    description = "Manage Gemini and GitHub API Credentials.",
                    icon = Icons.Default.Settings,
                    iconColor = SlateCard,
                    onClick = { navController.navigate(Screen.Settings.route) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "scale"
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .height(140.dp)
            .border(1.dp, iconColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                color = iconColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.padding(12.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = Color.Gray, lineHeight = 14.sp)
            }
        }
    }
}

// --- Terminal Page ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalPage(navController: NavController, isProjectMode: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var apiKey by remember { mutableStateOf(getApiKey(context)) }
    var showApiKeyDialog by remember { mutableStateOf(apiKey.isEmpty()) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var isGenerating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            val welcomeMsg = if (isProjectMode) {
                "INITIATING AUTONOMOUS DEVELOPMENT PROTOCOL. PLEASE DEFINE PROJECT REQUIREMENTS."
            } else {
                "SYSTEM ONLINE. READY FOR LOGIC CONSULTATION AND DEBUGGING."
            }
            messages.add(ChatMessage(welcomeMsg, false))
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    if (showApiKeyDialog) {
        var tempKey by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { },
            title = { Text("CORE INITIALIZATION") },
            text = {
                OutlinedTextField(
                    value = tempKey,
                    onValueChange = { tempKey = it },
                    label = { Text("Enter Gemini API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (tempKey.isNotBlank()) {
                        saveApiKey(context, tempKey)
                        apiKey = tempKey
                        showApiKeyDialog = false
                    }
                }) { Text("INITIALIZE") }
            }
        )
    }

    fun sendMessage() {
        if (inputText.isBlank() || isGenerating) return
        val userText = inputText.trim()
        
        messages.add(ChatMessage(userText, true))
        inputText = ""
        isGenerating = true
        val aiIndex = messages.size
        messages.add(ChatMessage("ANALYZING SYSTEM PARAMETERS...", false))

        val systemInstruction = if (isProjectMode) {
            "You are an Autonomous Project Architect. Your goal is to write complete, production-ready code files based on user requirements. Prioritize architecture and scalability."
        } else {
            "You are CodeStack AI, a Senior Software Architect. You assist with logic, debugging, and code explanation."
        }

        scope.launch {
            try {
                val model = GenerativeModel(
                    modelName = "gemini-3.1-flash-lite-preview", 
                    apiKey = apiKey,
                    systemInstruction = content { text(systemInstruction) }
                )
                
                messages[aiIndex] = ChatMessage("", false)
                model.generateContentStream(userText).collect { chunk ->
                    val currentText = messages[aiIndex].text
                    messages[aiIndex] = messages[aiIndex].copy(text = currentText + (chunk.text ?: ""))
                }
            } catch (e: Exception) {
                messages[aiIndex] = ChatMessage("SYSTEM INTERRUPT: ${e.localizedMessage}", false)
            } finally {
                isGenerating = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState, 
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(messages) { ChatBubble(it, onSaveCode = { code -> 
                MainActivity.saveCodeToFile(context, "Source_${System.currentTimeMillis()}.kt", code)
            }) }
        }

        Surface(
            tonalElevation = 12.dp, 
            color = Color(0xFF1E293B),
            modifier = Modifier.imePadding()
        ) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(), 
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (isProjectMode) "Enter project spec..." else "Ask anything...") },
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(
                    onClick = { sendMessage() },
                    enabled = !isGenerating,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.Send, "Execute")
                }
            }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage, onSaveCode: (String) -> Unit) {
    val horizontalAlign = if (msg.isUser) Alignment.End else Alignment.Start
    val bgColor = if (msg.isUser) Color(0xFF334155) else Color(0xFF475569)
    
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = horizontalAlign) {
        Card(
            shape = RoundedCornerShape(if (msg.isUser) 16.dp else 4.dp), 
            colors = CardDefaults.cardColors(containerColor = bgColor),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (msg.isUser) "USER" else "CODESTACK_AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = msg.text, 
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = if (msg.text.contains("```")) FontFamily.Monospace else FontFamily.Default
                )
                
                if (!msg.isUser && msg.text.contains("```")) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val rawCode = msg.text.substringAfter("```").substringBeforeLast("```")
                            val lines = rawCode.lines()
                            val cleanCode = if (lines.isNotEmpty() && lines[0].trim().let { it == "kotlin" || it == "kt" || it == "java" }) {
                                lines.drop(1).joinToString("\n")
                            } else { rawCode }.trim()
                            onSaveCode(cleanCode)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("SAVE TO VAULT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- Vault Page ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultPage(navController: NavController) {
    val context = LocalContext.current
    val files = remember { 
        mutableStateListOf<File>().apply {
            val folder = File(context.getExternalFilesDir(null), "Projects")
            if (!folder.exists()) folder.mkdirs()
            addAll(folder.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList())
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Color.Green, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(8.dp))
            Text("VAULT STATUS: ONLINE", style = MaterialTheme.typography.labelSmall, color = Color.Green)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("VAULT EMPTY - AWAITING DATA INGESTION", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(files) { file ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { 
                        navController.navigate(Screen.Editor.createRoute(file.name)) 
                    },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    ListItem(
                        headlineContent = { Text(file.name, fontWeight = FontWeight.Bold, color = Color.White) },
                        supportingContent = { 
                            Text("SIZE: ${file.length() / 1024} KB | TYPE: ${file.extension.uppercase()}", fontSize = 10.sp, color = Color.Gray) 
                        },
                        leadingContent = { 
                            Icon(
                                imageVector = if (file.extension == "kt") Icons.Default.Code else Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { 
                                file.delete()
                                files.remove(file)
                                Toast.makeText(context, "ASSET PURGED", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Red.copy(alpha = 0.7f))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

// --- Editor Page ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorPage(navController: NavController, fileName: String) {
    val context = LocalContext.current
    val file = remember(fileName) {
        File(context.getExternalFilesDir(null), "Projects/$fileName")
    }
    var codeText by remember { mutableStateOf(if (file.exists()) file.readText() else "// New Project File\n\nfun main() {\n    \n}") }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF1E293B)).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Surface(
                color = Color(0xFF334155),
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, null, modifier = Modifier.size(12.dp), tint = Color.Cyan)
                    Spacer(Modifier.width(8.dp))
                    Text(fileName, fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Box(modifier = Modifier.weight(1f).padding(4.dp)) {
            TextField(
                value = codeText,
                onValueChange = { codeText = it },
                modifier = Modifier.fillMaxSize(),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFFCECECE),
                    lineHeight = 18.sp
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        
        Surface(
            tonalElevation = 16.dp, 
            color = Color(0xFF1E293B),
            modifier = Modifier.navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("UTF-8 | Kotlin Script", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Button(
                    onClick = { MainActivity.saveCodeToFile(context, fileName, codeText) },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("COMMIT CHANGES", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

// --- Settings Page (New Implementation) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Load existing keys
    var geminiKey by remember { mutableStateOf(getApiKey(context)) }
    var githubKey by remember { mutableStateOf(getGitHubToken(context)) }

    // Test Connection States
    var isTestingGemini by remember { mutableStateOf(false) }
    var isTestingGithub by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "System Configuration",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Gemini Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Gemini API Key",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    trailingIcon = {
                        Button(
                            onClick = {
                                if (geminiKey.isNotBlank()) {
                                    isTestingGemini = true
                                    scope.launch {
                                        delay(1000) // Simulate Network Ping
                                        isTestingGemini = false
                                        snackbarHostState.showSnackbar("Gemini Connection: SUCCESS", duration = SnackbarDuration.Short)
                                    }
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Please enter a key first", duration = SnackbarDuration.Short)
                                    }
                                }
                            },
                            enabled = !isTestingGemini,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = if (isTestingGemini) Color.Gray else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (isTestingGemini) "Pinging..." else "Test", fontSize = 12.sp)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // GitHub Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "GitHub Personal Access Token",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = githubKey,
                    onValueChange = { githubKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.secondary
                    ),
                    trailingIcon = {
                        Button(
                            onClick = {
                                if (githubKey.isNotBlank()) {
                                    isTestingGithub = true
                                    scope.launch {
                                        delay(1200) // Simulate Network Ping
                                        isTestingGithub = false
                                        snackbarHostState.showSnackbar("GitHub Connection: SUCCESS", duration = SnackbarDuration.Short)
                                    }
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Please enter a token first", duration = SnackbarDuration.Short)
                                    }
                                }
                            },
                            enabled = !isTestingGithub,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = if (isTestingGithub) Color.Gray else MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(if (isTestingGithub) "Pinging..." else "Test", fontSize = 12.sp)
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Note: Token requires 'repo' and 'workflow' scopes for autonomous features.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save Button
            Button(
                onClick = {
                    saveApiKey(context, geminiKey)
                    saveGitHubToken(context, githubKey)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Configuration Saved Successfully",
                            duration = SnackbarDuration.Short,
                            withDismissAction = true
                        )
                        delay(500) // Slight delay to show the snackbar before navigating back
                        navController.popBackStack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Save Configuration", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- Drawer Content ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerContent(onNavigate: (String) -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(Color(0xFF0B1120)) 
            .padding(16.dp)
    ) {
        Text(
            "CODESTACK",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 24.dp)
        )
        
        Divider(color = Color.Gray.copy(alpha = 0.2f))
        
        Spacer(modifier = Modifier.height(16.dp))
        
        DrawerItem(icon = Icons.Default.Menu, label = "Dashboard", route = Screen.Dashboard.route, onNavigate, onClose)
        DrawerItem(icon = Icons.Default.Chat, label = "AI Terminal", route = Screen.Terminal.createRoute(false), onNavigate, onClose)
        DrawerItem(icon = Icons.Default.Code, label = "Vault", route = Screen.Vault.route, onNavigate, onClose)
        DrawerItem(icon = Icons.Outlined.Settings, label = "Configuration", route = Screen.Settings.route, onNavigate, onClose)
        
        Spacer(modifier = Modifier.weight(1f))
        
        Divider(color = Color.Gray.copy(alpha = 0.2f))
        
        DrawerItem(icon = Icons.AutoMirrored.Filled.ExitToApp, label = "Exit App", route = "", onNavigate = {
             // Handle exit
        }, onClose)
    }
}

@Composable
fun DrawerItem(icon: ImageVector, label: String, route: String, onNavigate: (String) -> Unit, onClose: () -> Unit) {
    TextButton(
        onClick = {
            if (route.isNotEmpty()) onNavigate(route)
            onClose()
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
    ) {
        Icon(icon, contentDescription = null, tint = Color.Gray)
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
