package com.somnath.codestack

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import java.io.File

// STABLE 2026 GOOGLE AI SDK
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

data class ChatMessage(val text: String, val isUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // FIX: Pushes the app content away from system navigation and status bars
        enableEdgeToEdge()
        
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CodeStackApp()
            }
        }
    }

    // Add this function inside your MainActivity class
    private fun saveCodeToFile(context: Context, fileName: String, code: String) {
        try {
            // Creates a "CodeStack" folder in your phone's documents
            val folder = File(context.getExternalFilesDir(null), "Projects")
            if (!folder.exists()) folder.mkdirs()

            val file = File(folder, fileName)
            file.writeText(code)
            
            Toast.makeText(context, "Saved to /Projects/$fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeStackApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var apiKey by remember { mutableStateOf(getApiKey(context)) }
    var showApiKeyDialog by remember { mutableStateOf(apiKey.isEmpty()) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var isGenerating by remember { mutableStateOf(false) }

    // Auto-scroll logic
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    if (showApiKeyDialog) {
        var tempKey by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { },
            title = { Text("CodeStack Setup") },
            text = {
                OutlinedTextField(
                    value = tempKey,
                    onValueChange = { tempKey = it },
                    label = { Text("Enter Gemini API Key") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (tempKey.isNotBlank()) {
                        saveApiKey(context, tempKey)
                        apiKey = tempKey
                        showApiKeyDialog = false
                    }
                }) { Text("Deploy Engine") }
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
        messages.add(ChatMessage("Thinking...", false))

        scope.launch {
            try {
                // Using 3.1 Flash-Lite for optimized performance on Vivo Y30
                val model = GenerativeModel(
                    modelName = "gemini-3.1-flash-lite-preview", 
                    apiKey = apiKey,
                    systemInstruction = content {
                        text("""
                            You are CodeStack AI, a Senior Software Engineer developed by Somnath Kurmi. 
                            You specialize in full-stack development and Android.
                            Provide clean, documented code and project structures.
                        """.trimIndent())
                    }
                )
                
                messages[aiIndex] = ChatMessage("", false)
                model.generateContentStream(userText).collect { chunk ->
                    val currentText = messages[aiIndex].text
                    messages[aiIndex] = messages[aiIndex].copy(text = currentText + (chunk.text ?: ""))
                }
            } catch (e: Exception) {
                messages[aiIndex] = ChatMessage("Error: ${e.localizedMessage}", false)
            } finally {
                isGenerating = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { 
            CenterAlignedTopAppBar(
                title = { Text("CODESTACK", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            )
        },
        bottomBar = {
            // FIX: Added navigationBarsPadding() and imePadding() to prevent overlap
            Surface(
                tonalElevation = 8.dp, 
                modifier = Modifier
                    .imePadding() 
                    .navigationBarsPadding() 
            ) {
                Row(
                    Modifier
                        .padding(12.dp)
                        .fillMaxWidth(), 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Build something great...") },
                        enabled = !isGenerating,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { sendMessage() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Send, "Send", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState, 
            modifier = Modifier
                .padding(padding) // Scaffold padding prevents content from going under bars
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { ChatBubble(it) }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage) {
    val horizontalAlign = if (msg.isUser) Alignment.End else Alignment.Start
    val bgColor = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (msg.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = horizontalAlign
    ) {
        Card(
            shape = RoundedCornerShape(16.dp), 
            colors = CardDefaults.cardColors(containerColor = bgColor),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = msg.text, 
                modifier = Modifier.padding(12.dp),
                color = textColor,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontFamily = if (msg.text.contains("```")) FontFamily.Monospace else FontFamily.Default
            )
        }
    }
}

private fun saveApiKey(c: Context, k: String) = c.getSharedPreferences("cs_prefs", 0).edit().putString("key", k).apply()
private fun getApiKey(c: Context) = c.getSharedPreferences("cs_prefs", 0).getString("key", "") ?: ""
