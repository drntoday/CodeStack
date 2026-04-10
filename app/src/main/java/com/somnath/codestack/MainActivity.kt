package com.somnath.codestack

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File

// STABLE 2026 GOOGLE AI SDK
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

// --- DATA MODELS ---
data class ChatMessage(val text: String, val isUser: Boolean)
enum class Screen { Terminal, Vault, Editor }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CodeStackApp()
            }
        }
    }

    // Helper to save files to the "Projects" directory
    fun saveCodeToFile(context: Context, fileName: String, code: String) {
        try {
            val folder = File(context.getExternalFilesDir(null), "Projects")
            if (!folder.exists()) folder.mkdirs()
            val file = File(folder, fileName)
            file.writeText(code)
            Toast.makeText(context, "Asset Secured in Vault", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Vault Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeStackApp() {
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    
    var currentScreen by remember { mutableStateOf(Screen.Terminal) }
    var editingFile by remember { mutableStateOf<File?>(null) }

    // Navigation and Action Handlers
    fun handleEditorSave(codeContent: String) {
        editingFile?.let { file ->
            try {
                file.writeText(codeContent)
                Toast.makeText(context, "Asset Updated", Toast.LENGTH_SHORT).show()
                currentScreen = Screen.Vault 
            } catch (e: Exception) {
                Toast.makeText(context, "Editor Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun handleChatSave(codeContent: String) {
        mainActivity?.saveCodeToFile(context, "Snippet_${System.currentTimeMillis()}.kt", codeContent)
    }

    Scaffold(
        topBar = {
            if (currentScreen == Screen.Editor) {
                TopAppBar(
                    title = { Text("EDITOR: ${editingFile?.name?.uppercase()}") },
                    navigationIcon = {
                        IconButton(onClick = { currentScreen = Screen.Vault }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { 
                        Text(
                            text = if (currentScreen == Screen.Vault) "QUANTUM VAULT" else "CODESTACK AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        ) 
                    }
                )
            }
        },
        bottomBar = {
            if (currentScreen != Screen.Editor) {
                NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                    NavigationBarItem(
                        selected = currentScreen == Screen.Terminal,
                        onClick = { currentScreen = Screen.Terminal },
                        label = { Text("Terminal") },
                        icon = { Icon(Icons.Default.Send, contentDescription = null) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.Vault,
                        onClick = { currentScreen = Screen.Vault },
                        label = { Text("Vault") },
                        icon = { Icon(Icons.Default.Code, contentDescription = null) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentScreen) {
                Screen.Terminal -> TerminalScreen(onSaveCode = { handleChatSave(it) })
                Screen.Vault -> VaultScreen(onFileClick = { file ->
                    editingFile = file
                    currentScreen = Screen.Editor
                })
                Screen.Editor -> {
                    editingFile?.let { file ->
                        EditorScreen(
                            fileName = file.name,
                            initialContent = file.readText(),
                            onSave = { handleEditorSave(it) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(onSaveCode: (String) -> Unit) {
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
            title = { Text("System Authentication") },
            text = {
                OutlinedTextField(
                    value = tempKey,
                    onValueChange = { tempKey = it },
                    label = { Text("Enter Gemini API Key") },
                    singleLine = true
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
        messages.add(ChatMessage("Processing system query...", false))

        scope.launch {
            try {
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
                messages[aiIndex] = ChatMessage("Critical Error: ${e.localizedMessage}", false)
            } finally {
                isGenerating = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState, 
            modifier = Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { ChatBubble(it, onSaveCode) }
        }

        Surface(tonalElevation = 8.dp, modifier = Modifier.imePadding().navigationBarsPadding()) {
            Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Input command...") },
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { sendMessage() },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(Icons.Default.Send, "Send", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage, onSaveCode: (String) -> Unit) {
    val horizontalAlign = if (msg.isUser) Alignment.End else Alignment.Start
    val bgColor = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (msg.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = horizontalAlign) {
        Card(
            shape = RoundedCornerShape(16.dp), 
            colors = CardDefaults.cardColors(containerColor = bgColor),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = msg.text, 
                    color = textColor,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontFamily = if (msg.text.contains("```")) FontFamily.Monospace else FontFamily.Default
                )
                
                if (!msg.isUser && msg.text.contains("```")) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val rawCode = msg.text.substringAfter("```").substringBeforeLast("```")
                            val lines = rawCode.lines()
                            val cleanCode = if (lines.isNotEmpty() && lines[0].trim().let { it == "kotlin" || it == "kt" || it == "java" }) {
                                lines.drop(1).joinToString("\n")
                            } else { rawCode }.trim()
                            onSaveCode(cleanCode)
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        // FIXED: Using .dp here to satisfy the compiler
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save to Vault", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun VaultScreen(onFileClick: (File) -> Unit) {
    val context = LocalContext.current
    val files = remember { 
        mutableStateListOf<File>().apply {
            val folder = File(context.getExternalFilesDir(null), "Projects")
            addAll(folder.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList())
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("ACTIVE ASSETS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No assets in vault.", color = Color.Gray)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(files) { file ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onFileClick(file) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    ListItem(
                        headlineContent = { Text(file.name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("${file.length() / 1024} KB • ${file.extension.uppercase()}", fontSize = 12.sp) },
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
                            }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EditorScreen(fileName: String, initialContent: String, onSave: (String) -> Unit) {
    var codeText by remember { mutableStateOf(initialContent) }

    Column(Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        Surface(color = Color(0xFF2D2D2D), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = fileName,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp).border(1.dp, Color.White.copy(alpha = 0.1f))) {
            TextField(
                value = codeText,
                onValueChange = { codeText = it },
                modifier = Modifier.fillMaxSize(),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFFD4D4D4)
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
        
        Surface(tonalElevation = 4.dp, modifier = Modifier.navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                Button(onClick = { onSave(codeText) }, shape = RoundedCornerShape(8.dp)) {
                    // FIXED: Using .dp here
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sync Changes")
                }
            }
        }
    }
}

private fun saveApiKey(c: Context, k: String) = c.getSharedPreferences("cs_prefs", 0).edit().putString("key", k).apply()
private fun getApiKey(c: Context) = c.getSharedPreferences("cs_prefs", 0).getString("key", "") ?: ""
