package com.somnath.codestack.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.somnath.codestack.MainActivity
import com.somnath.codestack.model.*
import com.somnath.codestack.ui.components.*
import com.somnath.codestack.ui.theme.Emerald
import com.somnath.codestack.util.getApiKey
import com.somnath.codestack.util.saveApiKey
import com.somnath.codestack.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalPage(navController: NavController, viewModel: MainViewModel, isProjectMode: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // API State
    var apiKey by remember { mutableStateOf(getApiKey(context)) }
    var showApiKeyDialog by remember { mutableStateOf(apiKey.isEmpty()) }
    
    // UI States
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var isGenerating by remember { mutableStateOf(false) }

    // ViewModel State Collection
    val logs by viewModel.terminalLogs.collectAsState()
    val manifest by viewModel.manifestFiles.collectAsState()
    val currentStep by viewModel.workflowStep.collectAsState()
    val building by viewModel.isBuilding.collectAsState()
    val steps by viewModel.workflowSteps.collectAsState()
    val phase by viewModel.workflowPhase.collectAsState()
    val blueprint by viewModel.architectureBlueprint.collectAsState()

    val terminalListState = rememberLazyListState()

    // 1. Welcome Message Logic
    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            val text = if (isProjectMode) "ARCHITECT MODE: INITIALIZED. Describe your project requirements." 
                       else "TERMINAL ONLINE. Awaiting logic consultation."
            messages.add(ChatMessage(text, isUser = false))
        }
    }

    // 2. Auto-scroll Logic
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) terminalListState.animateScrollToItem(logs.size - 1) }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            onDismiss = { showApiKeyDialog = false },
            onConfirm = { key ->
                saveApiKey(context, key)
                apiKey = key
                showApiKeyDialog = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Upper Section: Chat/Blueprint
        val chatWeight = if (phase == WorkflowPhase.Discussion) 1f else 0.4f
        
        Column(modifier = Modifier.weight(chatWeight)) {
            LazyColumn(
                state = listState, 
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(msg, onSaveCode = { code ->
                        MainActivity.saveCodeToFile(context, "Code_${System.currentTimeMillis()}.kt", code)
                    })
                }
            }

            // Action Buttons for Blueprint Phase
            if (phase == WorkflowPhase.Blueprint && !building) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { viewModel.startAutonomousBuild("Project_${System.currentTimeMillis()}") },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("EXECUTE AUTONOMOUS BUILD", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Input Bar
            Surface(color = Color(0xFF1E293B), tonalElevation = 8.dp, modifier = Modifier.imePadding()) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Enter prompt...") },
                        enabled = !building && !isGenerating,
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Emerald,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    Spacer(Modifier.width(12.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isBlank()) return@IconButton
                            val userText = inputText
                            messages.add(ChatMessage(userText, true))
                            inputText = ""
                            
                            if (isProjectMode) {
                                scope.launch { viewModel.generateBlueprint(userText) }
                            } else {
                                // Standard Gemini Stream
                                isGenerating = true
                                scope.launch {
                                    try {
                                        val model = GenerativeModel("gemini-1.5-flash", apiKey)
                                        messages.add(ChatMessage("", false))
                                        model.generateContentStream(userText).collect { chunk ->
                                            val lastIdx = messages.lastIndex
                                            messages[lastIdx] = messages[lastIdx].copy(text = messages[lastIdx].text + (chunk.text ?: ""))
                                        }
                                    } catch (e: Exception) {
                                        messages.add(ChatMessage("Error: ${e.message}", false))
                                    } finally { isGenerating = false }
                                }
                            }
                        },
                        enabled = !building && !isGenerating,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Emerald)
                    ) {
                        Icon(Icons.Default.Send, null, tint = Color.Black)
                    }
                }
            }
        }

        // Lower Section: Architecture Panel
        if (phase != WorkflowPhase.Discussion) {
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
            ProjectPanel(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                phase = phase,
                currentStep = currentStep,
                steps = steps,
                logs = logs,
                manifest = manifest,
                terminalListState = terminalListState,
                blueprint = blueprint
            )
        }
    }
}
