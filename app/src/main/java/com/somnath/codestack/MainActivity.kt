package com.somnath.codestack

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// CORRECT GOOGLE AI SDK IMPORTS (2026 Stable)
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

data class ChatMessage(val text: String, val isUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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
            title = { Text("Gemini AI Setup") },
            text = {
                OutlinedTextField(
                    value = tempKey,
                    onValueChange = { tempKey = it },
                    label = { Text("Enter API Key") },
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
                }) { Text("Start Chatting") }
            }
        )
    }

    fun sendMessage() {
        val userText = inputText.trim()
        if (userText.isEmpty() || isGenerating) return

        messages.add(ChatMessage(userText, isUser = true))
        inputText = ""
        isGenerating = true
        val aiIndex = messages.size
        messages.add(ChatMessage("Gemini is thinking...", isUser = false))

        scope.launch {
            try {
                // Initializing the Google AI Model directly
                val model = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = apiKey
                )
                
                messages[aiIndex] = ChatMessage("", isUser = false)

                // Streaming the response for a "typing" effect
                model.generateContentStream(userText).collect { chunk ->
                    val currentText = messages[aiIndex].text
                    messages[aiIndex] = messages[aiIndex].copy(text = currentText + (chunk.text ?: ""))
                }
            } catch (e: Exception) {
                messages[aiIndex] = ChatMessage("Error: ${e.localizedMessage}", isUser = false)
            } finally {
                isGenerating = false
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("CodeStack AI") }) },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Row(
                    modifier = Modifier.padding(8.dp).fillMaxWidth().imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask anything...") },
                        enabled = !isGenerating,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { sendMessage() }, enabled = !isGenerating && inputText.isNotBlank()) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState, 
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
    val color = if (msg.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = color),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(text = msg.text, modifier = Modifier.padding(12.dp), fontSize = 15.sp)
        }
    }
}

private fun saveApiKey(c: Context, k: String) = 
    c.getSharedPreferences("prefs", 0).edit().putString("key", k).apply()

private fun getApiKey(c: Context) = 
    c.getSharedPreferences("prefs", 0).getString("key", "") ?: ""
