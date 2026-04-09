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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
// UPDATED 2026 IMPORTS
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content

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
                }) { Text("Join Chat") }
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
        messages.add(ChatMessage("Analyzing...", isUser = false))

        scope.launch {
            try {
                // NEW 2026 INITIALIZATION
                // Use the Firebase AI Logic SDK with the Vertex AI backend
                val model = Firebase.ai(
                    backend = GenerativeBackend.vertexAI(apiKey = apiKey)
                ).generativeModel(modelName = "gemini-1.5-flash")
                
                messages[aiIndex] = ChatMessage("", isUser = false)

                model.generateContentStream(userText).collect { chunk ->
                    val currentText = messages[aiIndex].text
                    messages[aiIndex] = messages[aiIndex].copy(text = currentText + (chunk.text ?: ""))
                }
            } catch (e: Exception) {
                messages[aiIndex] = ChatMessage("System Error: ${e.localizedMessage}", isUser = false)
            } finally {
                isGenerating = false
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("CodeStack AI") }) },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask Gemini...") },
                        enabled = !isGenerating,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendMessage() })
                    )
                    IconButton(onClick = { sendMessage() }, enabled = !isGenerating && inputText.isNotBlank()) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState, 
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
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

private fun saveApiKey(c: Context, k: String) = c.getSharedPreferences("prefs", 0).edit().putString("key", k).apply()
private fun getApiKey(c: Context) = c.getSharedPreferences("prefs", 0).getString("key", "") ?: ""
