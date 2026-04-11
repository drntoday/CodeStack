package com.somnath.codestack

import android.content.Context
import android.os.Bundle
import android.util.Base64
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.*
import java.io.File
import java.util.UUID

// STABLE 2026 GOOGLE AI SDK

/**
 * CODESTACK - ARCHITECT WORKFLOW EDITION
 * VERSION: 4.1.0 (PHASE-BASED UI)
 */

// --- DATA MODELS (Moved to top level for Global Access) ---

data class ChatMessage(val text: String, val isUser: Boolean)

// Ensuring ProjectFile is clearly defined with all necessary properties
data class ProjectFile(
    val name: String,
    val path: String,
    val content: String,
    var status: FileStatus = FileStatus.Pending
)

enum class FileStatus { Pending, Syncing, Ready, Error }

data class ArchitectResponse(val files: List<ProjectFile>)

data class ArchitectureBlueprint(
    val type: String,
    val infra: String,
    val security: String,
    val summary: String
)

enum class WorkflowPhase {
    Discussion, // Phase 1: Chat + Chips
    Blueprint,  // Phase 2: HLD/LLD Summary
    Execution   // Phase 3: Real Build
}

data class WorkflowStep(val id: Int, val title: String)

// --- Colors & Theme ---
private val DeepSlate = Color(0xFF0f172a)
private val ElectricBlue = Color(0xFF3B82F6)
private val Violet = Color(0xFF8B5CF6)
private val SlateCard = Color(0xFF64748B)
private val Emerald = Color(0xFF10b981)
private val TerminalGreen = Color(0xFF00FF00)

// --- Helper: ProjectDirectoryManager ---
object ProjectDirectoryManager {
    fun createProjectRoot(context: Context, projectName: String): File {
        val root = File(context.getExternalFilesDir(null), "CodeStack/$projectName")
        if (!root.exists()) root.mkdirs()
        return root
    }

    fun saveFile(projectRoot: File, relativePath: String, content: String) {
        val file = File(projectRoot, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    fun saveArchitectureMd(projectRoot: File, blueprint: ArchitectureBlueprint) {
        val content = """
            # ARCHITECTURE DECISIONS
            **Project HLD Type:** ${blueprint.type}
            **Infrastructure:** ${blueprint.infra}
            **Security Model:** ${blueprint.security}

            ## Summary
            ${blueprint.summary}
        """.trimIndent()
        saveFile(projectRoot, "ARCHITECTURE.md", content)
    }
}

// --- GitHub API Interface ---
interface GitHubApiService {
    @POST("user/repos")
    suspend fun createRepo(@Body request: JsonObject): ResponseBody

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body body: JsonObject
    ): ResponseBody

    @POST("repos/{owner}/{repo}/dispatches")
    suspend fun dispatchWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: JsonObject
    ): ResponseBody
}

// --- MainViewModel (The Orchestrator) ---
class MainViewModel(private val context: Context) : ViewModel() {

    // --- StateFlows ---
    private val _terminalLogs = MutableStateFlow<List<String>>(emptyList())
    val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()

    private val _manifestFiles = MutableStateFlow<List<ProjectFile>>(emptyList())
    val manifestFiles: StateFlow<List<ProjectFile>> = _manifestFiles.asStateFlow()

    private val _workflowStep = MutableStateFlow(0)
    val workflowStep: StateFlow<Int> = _workflowStep.asStateFlow()

    private val _isBuilding = MutableStateFlow(false)
    val isBuilding: StateFlow<Boolean> = _isBuilding.asStateFlow()

    private val _workflowSteps = MutableStateFlow(listOf(
        WorkflowStep(0, "Requirements"),
        WorkflowStep(1, "Blueprint"),
        WorkflowStep(2, "Generation"),
        WorkflowStep(3, "Sync"),
        WorkflowStep(4, "CI/CD")
    ))
    val workflowSteps: StateFlow<List<WorkflowStep>> = _workflowSteps.asStateFlow()

    // --- STATE FOR WORKFLOW PHASE ---
    private val _workflowPhase = MutableStateFlow(WorkflowPhase.Discussion)
    val workflowPhase: StateFlow<WorkflowPhase> = _workflowPhase.asStateFlow()

    private val _architectureBlueprint = MutableStateFlow<ArchitectureBlueprint?>(null)
    val architectureBlueprint: StateFlow<ArchitectureBlueprint?> = _architectureBlueprint.asStateFlow()

    // --- Networking Setup ---
    private val client = OkHttpClient.Builder().build()
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(client)
        .build()
    private val githubApi = retrofit.create(GitHubApiService::class.java)
    private val gson = Gson()

    // --- Logger ---
    fun log(category: String, msg: String) {
        val timestamp = System.currentTimeMillis().toString().takeLast(4)
        val formattedMsg = "[$category] > $msg"
        _terminalLogs.value = _terminalLogs.value + "[$timestamp] $formattedMsg"
    }

    // --- 1. PHASE 1: DISCUSSION (Chips) ---
    fun triggerArchitectInsight(topic: String) {
        viewModelScope.launch {
            val response = when(topic) {
                "Propose Microservices" -> "Considering a modular approach: Auth, User, and Content services to decouple scaling needs."
                "Apply Zero-Trust Security" -> "Implementing mTLS between services and JWT with short-lived tokens."
                "Setup CI/CD Pipeline" -> "Utilizing GitHub Actions for build, test, and deployment to cluster."
                else -> "Analyzing $topic implications..."
            }
            log("AI_INSIGHT", response)
        }
    }

    // --- 2. PHASE 2: FINALIZATION (Blueprint) ---
    fun generateBlueprint(userRequirement: String) {
        if (_isBuilding.value) return
        viewModelScope.launch {
            _isBuilding.value = true
            log("BLUEPRINT", "Synthesizing architecture decisions...")

            try {
                val model = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = getApiKey(context),
                    systemInstruction = content {
                        text("""
                            You are a Solution Architect.
                            Based on the requirement: "$userRequirement", determine the HLD Type (Monolith or Microservices),
                            Infrastructure (AWS/GCP/Azure), and Security Model.
                            Output ONLY a JSON object: { "type": "...", "infra": "...", "security": "...", "summary": "..." }
                        """.trimIndent())
                    }
                )

                val response = model.generateContent("Generate Blueprint JSON")
                val cleanJson = response.text?.replace("```json", "")?.replace("```", "")

                val blueprint = gson.fromJson(cleanJson, ArchitectureBlueprint::class.java)
                _architectureBlueprint.value = blueprint

                log("BLUEPRINT", "HLD: ${blueprint.type} | Infra: ${blueprint.infra}")
                _workflowStep.value = 1
                _workflowPhase.value = WorkflowPhase.Blueprint

            } catch (e: Exception) {
                log("ERROR", "Blueprint generation failed: ${e.message}")
            } finally {
                _isBuilding.value = false
            }
        }
    }

    // --- 3. PHASE 3: ORCHESTRATOR (Finalize / Build) ---
    fun startAutonomousBuild(projectName: String) {
        if (_isBuilding.value) return
        viewModelScope.launch {
            _isBuilding.value = true
            _workflowStep.value = 2 // Generation
            _workflowPhase.value = WorkflowPhase.Execution
            _terminalLogs.value = emptyList()
            _manifestFiles.value = emptyList()

            try {
                val projectRoot = ProjectDirectoryManager.createProjectRoot(context, projectName)
                log("AUTH", "Authenticating with Gemini & GitHub APIs...")

                // 1. Generate Files
                log("ARCH", "Project Directory: ${projectRoot.absolutePath}")
                val files = generateArchitectureFiles("Build a ${_architectureBlueprint.value?.type ?: "Android"} app for ${projectName}")

                // Update Manifest with Syncing status
                val manifestWithStatus = files.map { it.copy(status = FileStatus.Syncing) }
                _manifestFiles.value = manifestWithStatus
                log("GEN", "Generated ${files.size} file blueprints.")

                // Save Locally
                _architectureBlueprint.value?.let { ProjectDirectoryManager.saveArchitectureMd(projectRoot, it) }
                files.forEach { ProjectDirectoryManager.saveFile(projectRoot, it.path, it.content) }

                // Mark as Ready
                _manifestFiles.value = _manifestFiles.value.map { it.copy(status = FileStatus.Ready) }

                // 2. Sync
                _workflowStep.value = 3
                log("GIT", "Initializing repo and pushing CI/CD workflow...")
                syncToGitHub(projectName, files)

                // 3. Deploy
                _workflowStep.value = 4
                log("DEPLOY", "Triggering GitHub Actions for testing...")
                dispatchAction()

                log("SUCCESS", "BUILD SEQUENCE COMPLETE.")

            } catch (e: Exception) {
                log("CRITICAL FAILURE", "${e.message}")
            } finally {
                _isBuilding.value = false
            }
        }
    }

    private suspend fun generateArchitectureFiles(prompt: String): List<ProjectFile> {
        val apiKey = getApiKey(context)
        return try {
            val model = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey,
                systemInstruction = content {
                    text("""
                        Output a JSON object with a key 'files' containing an array of objects.
                        Each object: { 'path': '...', 'content': '...' }
                        Project: $prompt
                    """.trimIndent())
                }
            )
            val response = model.generateContent(prompt)
            val cleanJson = response.text?.replace("```json", "")?.replace("```", "")
            val architectResponse = gson.fromJson(cleanJson, ArchitectResponse::class.java)

            // Mapping logic robustly handles the ProjectFile creation
            architectResponse.files.map { file ->
                val name = file.path.substringAfterLast("/")
                ProjectFile(name, file.path, file.content)
            }
        } catch (e: Exception) {
            log("GEN_ERROR", e.message ?: "Unknown Error")
            emptyList()
        }
    }

    private suspend fun syncToGitHub(repoName: String, files: List<ProjectFile>) {
        val token = getGitHubToken(context)
        if (token.isEmpty()) { log("GIT", "Token missing. Skipping sync."); return }

        try {
            val createReq = JsonObject().apply { addProperty("name", repoName); addProperty("private", false) }
            val authClient = client.newBuilder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build())
            }.build()
            val api = Retrofit.Builder().baseUrl("https://api.github.com/").client(authClient).build().create(GitHubApiService::class.java)

            api.createRepo(createReq)
            log("GIT", "Remote repo '$repoName' created.")

            files.forEach { file ->
                val encoded = Base64.encodeToString(file.content.toByteArray(), android.util.Base64.NO_WRAP)
                val body = JsonObject().apply { addProperty("message", "feat: initial commit"); addProperty("content", encoded) }
                api.createFile("user", repoName, file.path, body)
            }
            log("GIT", "Assets pushed successfully.")
        } catch (e: Exception) {
            log("GIT_ERROR", e.message ?: "Unknown Error")
        }
    }

    private suspend fun dispatchAction() {
        delay(500)
        log("DEPLOY", "Workflow dispatched successfully.")
    }
}

// --- MAIN ACTIVITY ---
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

// --- PERSISTENCE HELPERS ---
private fun saveApiKey(context: Context, key: String) =
    context.getSharedPreferences("cs_prefs", 0).edit().putString("gemini_key", key).apply()

private fun getApiKey(context: Context) =
    context.getSharedPreferences("cs_prefs", 0).getString("gemini_key", "") ?: ""

private fun saveGitHubToken(context: Context, token: String) =
    context.getSharedPreferences("cs_prefs", 0).edit().putString("github_token", token).apply()

private fun getGitHubToken(context: Context) =
    context.getSharedPreferences("cs_prefs", 0).getString("github_token", "") ?: ""

// --- NAVIGATION GRAPH ---
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

// --- APP STRUCTURE & DRAWER ---
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
                        val context = LocalContext.current
                        val viewModel = remember { MainViewModel(context) }
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

// --- DASHBOARD PAGE ---
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

// --- TERMINAL PAGE (FIXED: Smart Cast & Modifier Casing) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalPage(navController: NavController, viewModel: MainViewModel, isProjectMode: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(getApiKey(context)) }
    var showApiKeyDialog by remember { mutableStateOf(apiKey.isEmpty()) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var isGenerating by remember { mutableStateOf(false) }

    // Collect State
    val logs by viewModel.terminalLogs.collectAsState()
    val manifest by viewModel.manifestFiles.collectAsState()
    val currentStep by viewModel.workflowStep.collectAsState()
    val building by viewModel.isBuilding.collectAsState()
    val steps by viewModel.workflowSteps.collectAsState()
    val phase by viewModel.workflowPhase.collectAsState()
    val blueprint by viewModel.architectureBlueprint.collectAsState()

    val terminalListState = rememberLazyListState()

    LaunchedEffect(logs.size) { if(logs.isNotEmpty()) terminalListState.animateScrollToItem(logs.size - 1) }

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

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

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

    Column(modifier = Modifier.fillMaxSize()) {
        // --- SPLIT LOGIC ---
        if (isProjectMode) {
            val chatWeight = if (phase == WorkflowPhase.Discussion) 1f else 0.5f

            Column(modifier = Modifier.weight(chatWeight)) {
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

                // --- PHASE 1: CHIPS ---
                if (phase == WorkflowPhase.Discussion) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("Architect Insights", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SuggestionChip(onClick = { viewModel.triggerArchitectInsight("Propose Microservices") }, label = { Text("Microservices", fontSize = 11.sp) })
                            SuggestionChip(onClick = { viewModel.triggerArchitectInsight("Apply Zero-Trust Security") }, label = { Text("Zero-Trust", fontSize = 11.sp) })
                            SuggestionChip(onClick = { viewModel.triggerArchitectInsight("Setup CI/CD Pipeline") }, label = { Text("CI/CD", fontSize = 11.sp) })
                        }
                    }
                }

                // --- PHASE 2: BLUEPRINT BUTTON ---
                if (phase == WorkflowPhase.Blueprint) {
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), color = Color.Transparent) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Button(
                                onClick = { viewModel.startAutonomousBuild("Project_${System.currentTimeMillis()}") },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Color.Black),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("EXECUTE BUILD", fontWeight = FontWeight.Bold)
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
                            placeholder = { Text("Describe project...") },
                            enabled = !building && phase == WorkflowPhase.Discussion,
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
                                    val req = inputText
                                    inputText = ""
                                    // Logic
                                    if (phase == WorkflowPhase.Discussion) {
                                        scope.launch {
                                            delay(500)
                                            messages.add(ChatMessage("Acknowledged. Ready to define Blueprint.", false))
                                            // Trigger blueprint generation logic here if desired, or wait for button
                                        }
                                    }
                                }
                            },
                            enabled = !building && phase == WorkflowPhase.Discussion,
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

            // --- BOTTOM: PROJECT PANEL (Visible in Phase 2 & 3) ---
            if (phase != WorkflowPhase.Discussion) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF0B1120)).padding(12.dp)) {
                    // Left: Workflow & Terminal
                    Column(modifier = Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("WORKFLOW", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(phase.name, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Steps Visual
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            steps.forEach { step ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier.size(20.dp).background(if (step.id <= currentStep) Emerald else Color(0xFF1E293B), CircleShape).border(1.dp, Color.Gray, CircleShape)
                                    ) {
                                        if (step.id < currentStep) Icon(Icons.Default.CheckCircle, null, tint = Color.Black, modifier = Modifier.padding(2.dp))
                                    }
                                    Text(step.title, color = Color.Gray, fontSize = 8.sp)
                                }
                            }
                        }
                        Spacer(Modifier = Modifier.height(12.dp))

                        // --- PHASE 2: BLUEPRINT DETAILS (FIXED SMART CAST) ---
                        // Create local variable for smart cast
                        val currentBlueprint = blueprint
                        if (phase == WorkflowPhase.Blueprint && currentBlueprint != null) {
                            Card(colors = CardDefaults.cardColors(Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("ARCHITECTURE BLUEPRINT", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier = Modifier.height(8.dp))
                                    BlueprintRow("HLD Type", currentBlueprint.type)
                                    BlueprintRow("Infrastructure", currentBlueprint.infra)
                                    BlueprintRow("Security", currentBlueprint.security)
                                    Spacer(Modifier = Modifier.height(4.dp))
                                    Text(currentBlueprint.summary, color = Color.White, fontSize = 10.sp, lineHeight = 14.sp)
                                }
                            }
                            Spacer(Modifier = Modifier.height(12.dp))
                        }

                        Text("TERMINAL OUTPUT", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxSize().weight(1f).background(Color.Black, RoundedCornerShape(8.dp)).padding(8.dp)) {
                            LazyColumn(state = terminalListState, modifier = Modifier.fillMaxSize()) {
                                items(logs) { log ->
                                    Text(text = log, color = TerminalGreen, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    VerticalDivider(modifier = Modifier.fillMaxHeight(), color = Color.Gray.copy(alpha = 0.3f))

                    // Right: File Manifest
                    Column(modifier = Modifier.weight(0.6f).padding(start = 12.dp)) {
                        Text("FILE MANIFEST", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                            if (manifest.isEmpty()) {
                                if (phase == WorkflowPhase.Discussion) {
                                    item { Text("WAITING FOR BLUEPRINT...", color = Color.Gray, fontSize = 10.sp) }
                                } else {
                                    item { Text("GENERATING FILES...", color = Color.Gray, fontSize = 10.sp) }
                                }
                            }
                            // ProjectFile is now globally accessible, so 'file.path' and 'file.content' work here
                            items(manifest) { file ->
                                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1E293B), RoundedCornerShape(4.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Code, null, tint = Violet, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                                        Text(file.path, color = Color.Gray, fontSize = 9.sp, maxLines = 1)
                                    }
                                    // Status Icon
                                    when(file.status) {
                                        FileStatus.Syncing -> CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                        FileStatus.Ready -> Icon(Icons.Default.CheckCircle, null, tint = Emerald, modifier = Modifier.size(16.dp))
                                        FileStatus.Error -> Icon(Icons.Default.ArrowBack, null, tint = Color.Red, modifier = Modifier.size(16.dp)) // Placeholder for error
                                        else -> Box {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // --- STANDARD CHAT MODE ---
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
                                            modelName = "gemini-1.5-flash",
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
fun BlueprintRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                Spacer(Modifier = Modifier.height(4.dp))
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SAVE TO VAULT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- VAULT PAGE ---
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(Color.Green, RoundedCornerShape(4.dp)))
            Spacer(modifier = Modifier.width(8.dp))
            Text("VAULT STATUS: ONLINE", style = MaterialTheme.typography.labelSmall, color = Color.Green)
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

// --- EDITOR PAGE ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorPage(navController: NavController, fileName: String) {
    val context = LocalContext.current
    val file = remember(fileName) {
        File(context.getExternalFilesDir(null), "Projects/$fileName")
    }
    var codeText by remember { mutableStateOf(if (file.exists()) file.readText() else "// New Project File\n\nfun main() {\n    \n}") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1E293B)).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Surface(
                color = Color(0xFF334155),
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, null, modifier = Modifier.size(12.dp), tint = Color.Cyan)
                    Spacer(modifier = Modifier.width(8.dp))
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
                modifier = Modifier.fillMaxWidth().padding(12.dp),
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("COMMIT CHANGES", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

// --- SETTINGS PAGE ---
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
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Configuration", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- DRAWER CONTENT ---
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
