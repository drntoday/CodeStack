package com.somnath.codestack

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // This is our "Memory Box"
        val sharedPref = getPreferences(Context.MODE_PRIVATE)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // These are "Variables" - they hold what you type
                    var apiKey by remember { mutableStateOf(sharedPref.getString("gemini_key", "") ?: "") }
                    var statusMessage by remember { mutableStateOf("Please enter your Gemini API Key") }

                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "CodeStack Setup", style = MaterialTheme.typography.headlineMedium)
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        // This is the input box for your key
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API Key") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // The Save Button
                        Button(onClick = {
                            with(sharedPref.edit()) {
                                putString("gemini_key", apiKey)
                                apply()
                            }
                            statusMessage = "Key Saved Successfully!"
                        }) {
                            Text("Save Key")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(text = statusMessage, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
