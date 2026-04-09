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
// STABLE 2026 GOOGLE AI SDK IMPORTS
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

    // Dialog for API Key
    if (showApiKeyDialog) {
        var tempKey by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { },
            title = { Text("CodeStack AI Setup") },
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
                }) { Text("Save & Start") }
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
                // FIXED 2026 CONSTRUCTOR:
                // GenerativeModel(modelName, apiKey)
                val model = GenerativeModel(
                    modelName = "gemini-2.5-flash-lite", 
                    apiKey = apiKey
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
        topBar = { TopAppBar(title = { Text("CodeStack") }) },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask Gemini...") },
                        enabled = !isGenerating
                    )
                    IconButton(onClick = { sendMessage() }) {
                        Icon(Icons.Default.Send, "Send", tint = MaterialTheme.colorScheme.primary)
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
            items(messages) { ChatBubble(it) }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage) {
    val alignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val color = if (msg.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = color)) {
            Text(msg.text, Modifier.padding(12.dp))
        }
    }
}

private fun saveApiKey(c: Context, k: String) = c.getSharedPreferences("prefs", 0).edit().putString("key", k).apply()
private fun getApiKey(c: Context) = c.getSharedPreferences("prefs", 0).getString("key", "") ?: ""
