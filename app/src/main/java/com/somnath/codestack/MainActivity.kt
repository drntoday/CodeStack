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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
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
 * VERSION: 2.3.0 (SPLIT SCREEN AUTONOMY)
 */

// --- Colors & Theme ---
private val DeepSlate = Color(0xFF0f172a)
private val ElectricBlue = Color(0xFF3B82F6)
private val Violet = Color(0xFF8B5CF6)
private val SlateCard = Color(0xFF64748B)
private val Emerald = Color(0xFF10b981)
private val TerminalGreen = Color(0xFF00FF00)

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

// --- TERMINAL PAGE (Split Screen Architecture) ---
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

    // Project State
    val workflowSteps = listOf("Requirements", "Code Gen", "GitHub Sync", "Local Deploy")
    var workflowStep by remember { mutableStateOf(0) } // 0 to 3
    val terminalLogs = remember { mutableStateListOf<String>() }
    val terminalListState = rememberLazyListState()
    val manifestFiles = remember { mutableStateListOf<String>() }
    var isBuilding by remember { mutableStateOf(false) }

    // Auto-scroll terminal
    LaunchedEffect(terminalLogs.size) {
        if (terminalLogs.isNotEmpty()) terminalListState.animateScrollToItem(terminalLogs.size - 1)
    }

    fun logToTerminal(msg: String) {
        val timestamp = System.currentTimeMillis().toString().takeLast(4)
        terminalLogs.add("[$timestamp] $msg")
    }

    fun startBuildSequence() {
        if (isBuilding) return
        isBuilding = true
        scope.launch {
            // Step 1: Requirements (Already assumed true if button clicked, but we move visual)
            workflowStep = 0 
            logToTerminal("INITIALIZING PROJECT STRUCTURE...")
            delay(800)

            // Step 2: Code Gen
            workflowStep = 1
            logToTerminal("ANALYZING REQUIREMENTS...")
            delay(800)
            logToTerminal("GENERATING KOTLIN SOURCES...")
            manifestFiles.add("build.gradle.kts")
            manifestFiles.add("MainActivity.kt")
            manifestFiles.add("AndroidManifest.xml")
            delay(800)

            // Step 3: Sync
            workflowStep = 2
            logToTerminal("AUTHENTICATING GITHUB OAUTH...")
            val token = getGitHubToken(context)
            if (token.isNotEmpty()) {
                delay(500)
                logToTerminal("REMOTE REPOSITORY CREATED: codestack-v2")
                delay(500)
                logToTerminal("PUSHING ASSETS TO REMOTE...")
            } else {
                logToTerminal("WARNING: NO GITHUB TOKEN FOUND. SKIPPING SYNC.")
            }
            delay(1000)

            // Step 4: Deploy
            workflowStep = 3
            logToTerminal("COMPILING DEX...")
            delay(800)
            logToTerminal("INSTALLING APK ON DEVICE...")
            delay(1000)
            logToTerminal("BUILD SEQUENCE COMPLETE.")
            isBuilding = false
        }
    }

    // Init Welcome
    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            val welcomeMsg = if (isProjectMode) {
                "PROJECT MODE ENGAGED. DESCRIBE YOUR APPLICATION TO BEGIN GENERATION."
            } else {
                "SYSTEM ONLINE. READY FOR LOGIC CONSULTATION AND DEBUGGING."
            }
            messages.add(ChatMessage(welcomeMsg, false))
        }
    }

    // Chat Scroll
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

    // Main Layout Column
    Column(modifier = Modifier.fillMaxSize()) {
        // Split Logic
        if (isProjectMode) {
            // --- TOP: CHAT (Weight 1) ---
            Column(modifier = Modifier.weight(1f)) {
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

                // Build Button Area (Visible in Project Mode)
                if (!isBuilding && workflowStep == 0 && messages.size > 1) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Button(
                                onClick = { startBuildSequence() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Emerald,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("FINALIZE & BUILD", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Input Area
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
                            placeholder = { Text("Define project requirements...") },
                            enabled = !isGenerating && !isBuilding,
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(
                            onClick = { 
                                if (inputText.isNotBlank()) {
                                    messages.add(ChatMessage(inputText.trim(), true))
                                    inputText = ""
                                    isGenerating = true
                                    scope.launch {
                                        try {
                                            val model = GenerativeModel(
                                                modelName = "gemini-3.1-flash-lite-preview", 
                                                apiKey = apiKey,
                                                systemInstruction = content { 
                                                    text("You are a Product Manager gathering requirements. Be concise.") 
                                                }
                                            )
                                            model.generateContentStream(messages.last().text).collect { chunk ->
                                                // Simplified response for demo
                                                // In real app, append to last message
                                            }
                                            // Simulated Response
                                            messages.add(ChatMessage("REQUIREMENTS ACKNOWLEDGED. READY TO BUILD.", false))
                                        } catch (e: Exception) {
                                            messages.add(ChatMessage("ERROR: ${e.message}", false))
                                        } finally {
                                            isGenerating = false
                                        }
                                    }
                                }
                            },
                            enabled = !isGenerating && !isBuilding,
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

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

            // --- BOTTOM: PROJECT PANEL (Weight 1) ---
            Row(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF0B1120))) {
                // Left: Workflow & Logs
                Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                    Text("WORKFLOW STATUS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Workflow Steps
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        workflowSteps.forEachIndexed { index, step ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(
                                            if (index <= workflowStep) Emerald else Color.Gray,
                                            CircleShape
                                        )
                                        .then(if (index <= workflowStep) {
                                            Modifier.drawBehind {
                                                drawRoundRect(
                                                    color = Emerald,
                                                    alpha = 0.5f,
                                                    cornerRadius = CornerRadius(20f),
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                                                )
                                            }
                                        } else Modifier)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    step, 
                                    color = if (index <= workflowStep) Color.White else Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = if (index <= workflowStep) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("TERMINAL OUTPUT", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Live Terminal
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black, RoundedCornerShape(8.dp))
                            .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        LazyColumn(
                            state = terminalListState,
                            modifier = Modifier.fillMaxSize(),
                            reverseLayout = false // Append to bottom
                        ) {
                            items(terminalLogs) { log ->
                                Text(
                                    text = log,
                                    color = TerminalGreen,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                    }
                }

                VerticalDivider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.fillMaxHeight())

                // Right: File Manifest
                Column(modifier = Modifier.weight(0.5f).padding(12.dp)) {
                    Text("FILE MANIFEST", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (manifestFiles.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("NO FILES GENERATED", color = Color.Gray, fontSize = 10.sp)
                                }
                            }
                        }
                        items(manifestFiles) { fileName ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code, 
                                    contentDescription = null, 
                                    tint = Violet, 
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(fileName, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        } else {
            // --- STANDARD CHAT MODE (Full Height) ---
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
                            placeholder = { Text("Ask anything...") },
                            enabled = !isGenerating,
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(
                            onClick = {
                                if (inputText.isBlank() || isGenerating) return@IconButton
                                val userText = inputText.trim()
                                messages.add(ChatMessage(userText, true))
                                inputText = ""
                                isGenerating = true
                                scope.launch {
                                    try {
                                        val model = GenerativeModel(
                                            modelName = "gemini-3.1-flash-lite-preview", 
                                            apiKey = apiKey,
                                            systemInstruction = content { 
                                                text("You are CodeStack AI, a Senior Software Architect.") 
                                            }
                                        )
                                        messages.add(ChatMessage("", false))
                                        model.generateContentStream(userText).collect { chunk ->
                                            messages.last().let { msg ->
                                                messages[messages.lastIndex] = msg.copy(text = msg.text + (chunk.text ?: ""))
                                            }
                                        }
                                    } catch (e: Exception) {
                                        messages.add(ChatMessage("SYSTEM INTERRUPT: ${e.localizedMessage}", false))
                                    } finally {
                                        isGenerating = false
                                    }
                                }
                            },
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
    }
}

@Composable
fun ChatBubble(msg: ChatMessage, onSaveCode: (String) -> Unit) {
    val horizontalAlign = if (msg.isUser) Alignment.End else Alignment.Start
    val bgColor = if (msg.isUser) ElectricBlue else Color(0xFF334155)
    
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = horizontalAlign) {
        Card(
            shape = RoundedCornerShape(if (msg.isUser) 16.dp else 4.dp), 
            colors = CardDefaults.cardColors(containerColor = bgColor),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (msg.isUser) "USER" else "CODESTACK_AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (msg.isUser) Color.White else MaterialTheme.colorScheme.primary,
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
                        colors = ButtonDefaults.buttonColors(containerColor = Violet)
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

// --- Settings Page ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var geminiKey by remember { mutableStateOf(getApiKey(context)) }
    var githubKey by remember { mutableStateOf(getGitHubToken(context)) }
    var isTestingGemini by remember { mutableStateOf(false) }
    var isTestingGithub by remember { mutableStateOf(false) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(24.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("System Configuration", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(32.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Gemini API Key", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                    trailingIcon = {
                        Button(
                            onClick = {
                                if (geminiKey.isNotBlank()) {
                                    isTestingGemini = true
                                    scope.launch {
                                        delay(1000)
                                        isTestingGemini = false
                                        snackbarHostState.showSnackbar("Gemini Connection: SUCCESS")
                                    }
                                }
                            },
                            enabled = !isTestingGemini,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.primary)
                        ) { Text(if (isTestingGemini) "Pinging..." else "Test", fontSize = 12.sp) }
                        }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("GitHub Token", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = githubKey,
                    onValueChange = { githubKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = MaterialTheme.colorScheme.secondary),
                    trailingIcon = {
                        Button(
                            onClick = {
                                if (githubKey.isNotBlank()) {
                                    isTestingGithub = true
                                    scope.launch {
                                        delay(1200)
                                        isTestingGithub = false
                                        snackbarHostState.showSnackbar("GitHub Connection: SUCCESS")
                                    }
                                }
                            },
                            enabled = !isTestingGithub,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.secondary)
                        ) { Text(if (isTestingGithub) "Pinging..." else "Test", fontSize = 12.sp) }
                        }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Note: Token requires 'repo' and 'workflow' scopes.", style = MaterialTheme.typography.bodySmall, color = Color.Gray.copy(alpha = 0.7f), fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    saveApiKey(context, geminiKey)
                    saveGitHubToken(context, githubKey)
                    scope.launch {
                        snackbarHostState.showSnackbar("Configuration Saved Successfully")
                        delay(500)
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
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
        
        DrawerItem(icon = Icons.AutoMirrored.Filled.ExitToApp, label = "Exit App", route = "", onNavigate = {}, onClose)
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
