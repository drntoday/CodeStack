package com.somnath.codestack.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

// --- SETTINGS COMPONENTS ---

@Composable
fun ApiKeyDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var tempKey by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "CORE INITIALIZATION") },
        text = {
            OutlinedTextField(
                value = tempKey,
                onValueChange = { tempKey = it },
                label = { Text(text = "Enter Gemini API Key") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                if (tempKey.isNotBlank()) {
                    onConfirm(tempKey)
                }
            }) { Text(text = "INITIALIZE") }
        }
    )
}
