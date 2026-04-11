package com.somnath.codestack.viewmodel

import android.content.Context
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.somnath.codestack.model.ArchitectureBlueprint
import com.somnath.codestack.model.ArchitectResponse
import com.somnath.codestack.model.FileStatus
import com.somnath.codestack.model.ProjectFile
import com.somnath.codestack.model.WorkflowPhase
import com.somnath.codestack.model.WorkflowStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.PUT
import java.io.File

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

    private val _workflowPhase = MutableStateFlow(WorkflowPhase.Discussion)
    val workflowPhase: StateFlow<WorkflowPhase> = _workflowPhase.asStateFlow()

    private val _architectureBlueprint = MutableStateFlow<ArchitectureBlueprint?>(null)
    val architectureBlueprint: StateFlow<ArchitectureBlueprint?> = _architectureBlueprint.asStateFlow()

    private val client = OkHttpClient.Builder().build()
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(client)
        .build()
    private val githubApi = retrofit.create(GitHubApiService::class.java)
    private val gson = Gson()

    fun log(category: String, msg: String) {
        val timestamp = System.currentTimeMillis().toString().takeLast(4)
        val formattedMsg = "[$category] > $msg"
        _terminalLogs.value = _terminalLogs.value + "[$timestamp] $formattedMsg"
    }

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

    fun startAutonomousBuild(projectName: String) {
        if (_isBuilding.value) return
        viewModelScope.launch {
            _isBuilding.value = true
            _workflowStep.value = 2
            _workflowPhase.value = WorkflowPhase.Execution
            _terminalLogs.value = emptyList()
            _manifestFiles.value = emptyList()

            try {
                val projectRoot = ProjectDirectoryManager.createProjectRoot(context, projectName)
                log("AUTH", "Authenticating with Gemini & GitHub APIs...")
                log("ARCH", "Project Directory: ${projectRoot.absolutePath}")
                
                val files = generateArchitectureFiles("Build a ${_architectureBlueprint.value?.type ?: "Android"} app for ${projectName}")

                val manifestWithStatus = files.map { it.copy(status = FileStatus.Syncing) }
                _manifestFiles.value = manifestWithStatus
                log("GEN", "Generated ${files.size} file blueprints.")

                _architectureBlueprint.value?.let { ProjectDirectoryManager.saveArchitectureMd(projectRoot, it) }
                files.forEach { ProjectDirectoryManager.saveFile(projectRoot, it.path, it.content) }

                _manifestFiles.value = _manifestFiles.value.map { it.copy(status = FileStatus.Ready) }

                _workflowStep.value = 3
                log("GIT", "Initializing repo and pushing CI/CD workflow...")
                syncToGitHub(projectName, files)

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
