package com.somnath.codestack.ui.pages

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.somnath.codestack.MainActivity
import com.somnath.codestack.model.ChatMessage
import com.somnath.codestack.model.WorkflowPhase
import com.somnath.codestack.ui.components.ApiKeyDialog
import com.somnath.codestack.ui.components.ChatBubble
import com.somnath.codestack.ui.components.ProjectPanel
import com.somnath.codestack.ui.components.getApiKey
import com.somnath.codestack.ui.components.saveApiKey
import com.somnath.codestack.ui.theme.Emerald
import com.somnath.codestack.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    // Collecting State with shadow variables for null-safety
    val logs by viewModel.terminalLogs.collectAsState()
    val manifest by viewModel.manifestFiles.collectAsState()
    val currentStep by viewModel.workflowStep.collectAsState()
    val building by viewModel.isBuilding.collectAsState()
    val steps by viewModel.workflowSteps.collectAsState()
    val phase by viewModel.workflowPhase.collectAsState()

    // Smart cast fix
    val blueprintState by viewModel.architectureBlueprint.collectAsState()
    val bp = blueprintState

    val terminalListState = rememberLazyListState()

    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) terminalListState.animateScrollToItem(logs.size - 1) }

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
        ApiKeyDialog(
            onDismiss = { /* Cannot dismiss without key */ },
            onConfirm = { key ->
                saveApiKey(context, key)
                apiKey = key
                showApiKeyDialog = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isProjectMode) {
            val chatWeight = if (phase == WorkflowPhase.Discussion) 1f else 0.5f

            Column(modifier = Modifier.weight(chatWeight)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(messages) { ChatBubble(it, onSaveCode = { code ->
                        MainActivity.saveCodeToFile(context, "Source_${System.currentTimeMillis()}.kt", code)
                    }) }
                }

                if (phase == WorkflowPhase.Discussion) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = "Architect Insights",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SuggestionChip(onClick = { viewModel.triggerArchitectInsight("Propose Microservices") }, label = { Text(text = "Microservices", fontSize = 11.sp) })
                            SuggestionChip(onClick = { viewModel.triggerArchitectInsight("Apply Zero-Trust Security") }, label = { Text(text = "Zero-Trust", fontSize = 11.sp) })
                            SuggestionChip(onClick = { viewModel.triggerArchitectInsight("Setup CI/CD Pipeline") }, label = { Text(text = "CI/CD", fontSize = 11.sp) })
                        }
                    }
                }

                if (phase == WorkflowPhase.Blueprint) {
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color.Transparent
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Button(
                                onClick = { viewModel.startAutonomousBuild("Project_${System.currentTimeMillis()}") },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Color.Black),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "EXECUTE BUILD", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
                            placeholder = { Text(text = "Describe project...") },
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
                                    if (phase == WorkflowPhase.Discussion) {
                                        scope.launch {
                                            delay(500)
                                            messages.add(ChatMessage("Acknowledged. Ready to define Blueprint.", false))
                                            viewModel.generateBlueprint(req)
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

            if (phase != WorkflowPhase.Discussion) {
                ProjectPanel(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    phase = phase,
                    currentStep = currentStep,
                    steps = steps,
                    logs = logs,
                    manifest = manifest,
                    terminalListState = terminalListState,
                    blueprint = bp
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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
                            placeholder = { Text(text = "Ask anything...") },
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
                                                messages[messages.lastIndex] =
                                                    msg.copy(text = msg.text + (chunk.text ?: ""))
                                            }
                                        }
                                    } catch (e: Exception) {
                                        messages[messages.lastIndex] = ChatMessage("Error: ${e.message}", false)
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
                            Icon(Icons.Default.Send, "Send")
                        }
                    }
                }
            }
        }
    }
}
