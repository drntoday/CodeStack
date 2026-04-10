package com.somnath.codestack

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// 2026 STABLE GOOGLE AI SDK
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

data class ChatMessage(val text: String, val isUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CodeStackApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeStackApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State management
    var apiKey by remember { mutableStateOf(getApiKey(context)) }
    var showApiKeyDialog by remember { mutableStateOf(apiKey.isEmpty()) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var isGenerating by remember { mutableStateOf(false) }

    // Auto-scroll on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // 1. API Key Setup Dialog
    if (showApiKeyDialog) {
        var tempKey by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { },
            title = { Text("CodeStack Setup") },
            text = {
                OutlinedTextField(
                    value = tempKey,
                    onValueChange = { tempKey = it },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("AIzaSy...") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (tempKey.isNotBlank()) {
                        saveApiKey(context, tempKey)
                        apiKey = tempKey
                        showApiKeyDialog = false
                    }
                }) { Text("Initialize Engine") }
            }
        )
    }

    // 2. The Core AI Engine Logic
    fun sendMessage() {
        if (inputText.isBlank() || isGenerating) return
        val userText = inputText.trim()
        
        messages.add(ChatMessage(userText, isUser = true))
        inputText = ""
        isGenerating = true
        
        val aiMessageIndex = messages.size
        messages.add(ChatMessage("Thinking...", isUser = false))

        scope.launch {
            try {
                // MODEL UPGRADE: Using Gemini 3.1 Flash-Lite for speed on Vivo Y30
                val model = GenerativeModel(
                    modelName = "gemini-3.1-flash-lite-preview",
                    apiKey = apiKey,
                    systemInstruction = content {
                        text("""
                            You are CodeStack AI, a Senior Software Engineer developed by Somnath Kurmi. 
                            You are specialized in Android development (Kotlin/Compose), Web, and GitHub Actions.
                            When asked to code, provide efficient, modular, and well-documented results.
                            Outline folder structures for multi-file projects.
                        """.trimIndent())
                    }
                )
                
                messages[aiMessageIndex] = ChatMessage("", isUser = false)
                
                model.generateContentStream(userText).collect { chunk ->
                    val currentText = messages[aiMessageIndex].text
                    messages[aiMessageIndex] = messages[aiMessageIndex].copy(text = currentText + (chunk.text ?: ""))
                }
            } catch (e: Exception) {
                messages[aiMessageIndex] = ChatMessage("Error: ${e.localizedMessage}", isUser = false)
            } finally {
                isGenerating = false
            }
        }
    }

    // 3. The Main UI Layout
    Scaffold(
        topBar = { 
            CenterAlignedTopAppBar(
                title = { Text("CODESTACK", style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        bottomBar = {
            Surface(tonalElevation = 12.dp, modifier = Modifier.imePadding()) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask Senior Engineer...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        enabled = !isGenerating
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = { sendMessage() },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Send, "Send")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { msg ->
                ChatBubble(msg)
            }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage) {
    val alignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (msg.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    
    // Basic logic to detect code blocks (simple version for Vivo Y30 performance)
    val isCode = msg.text.contains("```")

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp, 
                topEnd = 16.dp, 
                bottomStart = if (msg.isUser) 16.dp else 0.dp, 
                bottomEnd = if (msg.isUser) 0.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = msg.text.replace("```", ""), // Hiding backticks for now
                modifier = Modifier.padding(12.dp),
                color = textColor,
                fontSize = 15.sp,
                fontFamily = if (isCode) FontFamily.Monospace else FontFamily.Default
            )
        }
    }
}

// Data persistence
private fun saveApiKey(c: Context, k: String) = c.getSharedPreferences("cs_prefs", 0).edit().putString("api_key", k).apply()
private fun getApiKey(c: Context) = c.getSharedPreferences("cs_prefs", 0).getString("api_key", "") ?: ""
