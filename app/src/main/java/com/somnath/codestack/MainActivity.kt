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

/**
 * CODESTACK - ADVANCED AI DEVELOPMENT ENVIRONMENT
 * ARCHITECTURE BY SOMNATH KURMI
 * VERSION: 1.0.4 (QUANTUM VAULT ENABLED)
 */

data class ChatMessage(val text: String, val isUser: Boolean)

enum class Screen { Terminal, Vault, Editor }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00E5FF),
                    secondary = Color(0xFF7C4DFF),
                    tertiary = Color(0xFFFFAB40),
                    surface = Color(0xFF121212),
                    background = Color(0xFF0A0A0A)
                )
            ) {
                CodeStackApp()
            }
        }
    }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeStackApp() {
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    
    var currentScreen by remember { mutableStateOf(Screen.Terminal) }
    var editingFile by remember { mutableStateOf<File?>(null) }

    fun handleSave(codeContent: String) {
        editingFile?.let { file ->
            try {
                file.writeText(codeContent)
                Toast.makeText(context, "ASSET SYNCHRONIZED", Toast.LENGTH_SHORT).show()
                currentScreen = Screen.Vault
            } catch (e: Exception) {
                Toast.makeText(context, "SYNC ERROR: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun handleChatSave(codeContent: String) {
        mainActivity?.saveCodeToFile(context, "Source_${System.currentTimeMillis()}.kt", codeContent)
    }

    Scaffold(
        topBar = {
            if (currentScreen == Screen.Editor) {
                TopAppBar(
                    title = { 
                        Column {
                            Text("EDITOR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(editingFile?.name ?: "Unknown", style = MaterialTheme.typography.titleMedium)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { currentScreen = Screen.Vault }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
                )
            } else if (currentScreen == Screen.Vault) {
                 TopAppBar(
                    title = { Text("QUANTUM VAULT", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { 
                        Text("CODESTACK TERMINAL", 
                            style = MaterialTheme.typography.titleLarge, 
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ) 
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color(0xFF121212).copy(alpha = 0.95f)
                    )
                )
            }
        },
        bottomBar = {
            if (currentScreen != Screen.Editor) {
                NavigationBar(
                    containerColor = Color(0xFF121212),
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    NavigationBarItem(
                        selected = currentScreen == Screen.Terminal,
                        onClick = { currentScreen = Screen.Terminal },
                        label = { Text("TERMINAL") },
                        icon = { Icon(Icons.Default.Send, contentDescription = null) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.Vault,
                        onClick = { currentScreen = Screen.Vault },
                        label = { Text("VAULT") },
                        icon = { Icon(Icons.Default.Code, contentDescription = null) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize().background(Color(0xFF0A0A0A))) {
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
                            onSave = { handleSave(it) }
                        )
                    } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("ERROR: NO FILE SELECTED")
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

    fun sendMessage() {
        if (inputText.isBlank() || isGenerating) return
        val userText = inputText.trim()
        
        messages.add(ChatMessage(userText, true))
        inputText = ""
        isGenerating = true
        val aiIndex = messages.size
        messages.add(ChatMessage("ANALYZING SYSTEM PARAMETERS...", false))

        scope.launch {
            try {
                val model = GenerativeModel(
                    modelName = "gemini-3.1-flash-lite-preview", 
                    apiKey = apiKey,
                    systemInstruction = content {
                        text("""
                            You are CodeStack AI, a Senior Software Architect developed by Somnath Kurmi. 
                            Domain Expertise: Android (Kotlin/Compose), Full-stack, ML, and Cloud Infrastructure.
                            Persona: Highly technical, clean code advocate, providing industrial-grade solutions.
                            Response Format: Always provide code in markdown blocks with language identifiers.
                        """.trimIndent())
                    }
                )
                
                messages[aiIndex] = ChatMessage("", false)
                model.generateContentStream(userText).collect { chunk ->
                    val currentText = messages[aiIndex].text
                    messages[aiIndex] = messages[aiIndex].copy(text = currentText + (chunk.text ?: ""))
                }
            } catch (e: Exception) {
                messages[aiIndex] = ChatMessage("SYSTEM INTERRUPT: ${e.localizedMessage}", false)
            } finally {
                isGenerating = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState, 
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(messages) { ChatBubble(it, onSaveCode) }
        }

        Surface(
            tonalElevation = 12.dp, 
            color = Color(0xFF121212),
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
                    placeholder = { Text("Initialize command Sequence...") },
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(
                    onClick = { sendMessage() },
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

@Composable
fun ChatBubble(msg: ChatMessage, onSaveCode: (String) -> Unit) {
    val horizontalAlign = if (msg.isUser) Alignment.End else Alignment.Start
    val bgColor = if (msg.isUser) Color(0xFF1E1E1E) else Color(0xFF2D2D2D)
    val borderColor = if (msg.isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = horizontalAlign
    ) {
        Card(
            shape = RoundedCornerShape(if (msg.isUser) 16.dp else 4.dp), 
            colors = CardDefaults.cardColors(containerColor = bgColor),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .border(1.dp, borderColor, RoundedCornerShape(if (msg.isUser) 16.dp else 4.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (msg.isUser) "USER" else "CODESTACK_AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("DECRYPT & SAVE TO VAULT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
            if (!folder.exists()) folder.mkdirs()
            addAll(folder.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList())
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Color.Green, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(8.dp))
            Text("VAULT STATUS: ONLINE", style = MaterialTheme.typography.labelSmall, color = Color.Green)
        }
        
        Spacer(Modifier.height(24.dp))
        
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("VAULT EMPTY - AWAITING DATA INGESTION", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(files) { file ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onFileClick(file) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
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

@Composable
fun EditorScreen(fileName: String, initialContent: String, onSave: (String) -> Unit) {
    var codeText by remember { mutableStateOf(initialContent) }

    Column(Modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
        // IDE-style Tab Header
        Row(Modifier.fillMaxWidth().background(Color(0xFF1A1A1A)).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Surface(
                color = Color(0xFF2D2D2D),
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, null, modifier = Modifier.size(12.dp), tint = Color.Cyan)
                    Spacer(Modifier.width(8.dp))
                    Text(fileName, fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Code Editor Surface
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(4.dp)) {
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
        
        // Editor Controls
        Surface(
            tonalElevation = 16.dp, 
            color = Color(0xFF121212),
            modifier = Modifier.navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("UTF-8 | Kotlin Script", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Button(
                    onClick = { onSave(codeText) },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("COMMIT CHANGES", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun saveApiKey(c: Context, k: String) = c.getSharedPreferences("cs_prefs", 0).edit().putString("key", k).apply()
private fun getApiKey(c: Context) = c.getSharedPreferences("cs_prefs", 0).getString("key", "") ?: ""
